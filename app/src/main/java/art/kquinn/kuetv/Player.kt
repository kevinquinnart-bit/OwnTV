package art.kquinn.kuetv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** One version of a channel: its quality as the user reads it ("FHD") and its address. */
data class Stream(
    val label: String,
    val url: String,
    /** Sent with every request for it, when the place it plays from asks for them. */
    val headers: Map<String, String> = emptyMap(),
    /** English subtitles kept in a file of their own, for a stream that may carry none. */
    val subtitle: String? = null,
)

/** The last thing tried for a film when add-ons are in use: the copy the provider itself lists. */
const val PROVIDER_COPY = "Your provider's copy"

/** A film that hasn't started after this long on one stream moves to the next. A torrent is given longer. */
private const val SLOW_START_MS = 25_000L
private const val SLOW_TORRENT_MS = 80_000L

/** Sends the player's reading to the torrent engine for a torrent's address, and to the internet for any other. */
@OptIn(UnstableApi::class)
private class Either(private val web: DataSource, private val torrent: DataSource) : DataSource {
    private var using: DataSource = web

    override fun addTransferListener(transferListener: TransferListener) {
        web.addTransferListener(transferListener)
        torrent.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        using = if (dataSpec.uri.scheme == "magnet") torrent else web
        return using.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = using.read(buffer, offset, length)
    override fun getUri(): android.net.Uri? = using.uri
    override fun getResponseHeaders(): Map<String, List<String>> = using.responseHeaders
    override fun close() = using.close()
}

/**
 * One thing the player can show: a channel or a film. A channel the provider lists in several
 * versions carries them in [streams], best picture first, and starts on [startStream].
 */
data class Playable(
    val name: String,
    val url: String,
    val streams: List<Stream> = emptyList(),
    val startStream: Int = 0,
    /** Sent with every request for it; a recording in Google Drive needs its pass. */
    val headers: Map<String, String> = emptyMap(),
    /** For a live channel: asks the provider what is on it, for the now-and-next lines in the name box. */
    val guide: (suspend () -> List<Programme>)? = null,
    /**
     * For a film or episode when the user has add-ons: asks them where it can be played from. The
     * player asks when it gets to this item, tries what comes back in order, and keeps [url] for last.
     */
    val find: (suspend () -> Search)? = null,
)

/** Recording needs this much room to start. */
private const val ROOM_TO_RECORD = 500L * 1024 * 1024

/** Sits between the player and the internet and shows [recorder] everything the player is sent. */
@OptIn(UnstableApi::class)
private class Tap(private val upstream: DataSource, private val recorder: Recorder) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        recorder.newConnection()
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = upstream.read(buffer, offset, length)
        if (count > 0) recorder.take(buffer, offset, count)
        return count
    }

    override fun getUri(): android.net.Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() = upstream.close()
}

private const val MAX_TRIES = 6

/** A picture that stays stopped this long moves down to the next version. */
private const val LONG_STOP_MS = 12_000L

/** This many short stops inside [STOPS_WINDOW_MS] also moves down. */
private const val STOPS_TO_MOVE = 3
private const val STOPS_WINDOW_MS = 90_000L

/** How long a channel stays on the version it last played well on, before the best is tried again. */
private const val SETTLED_MS = 30 * 60_000L

/** The version each channel last played on, and when; kept only while the app is open. */
private object Settled {
    private val last = HashMap<String, Pair<Int, Long>>()

    fun startFor(item: Playable, count: Int = item.streams.size): Int {
        val kept = last[item.url] ?: return item.startStream
        return if (System.currentTimeMillis() - kept.second < SETTLED_MS && kept.first in 0 until count) kept.first else item.startStream
    }

    fun played(item: Playable, stream: Int, count: Int) {
        if (count > 1) last[item.url] = stream to System.currentTimeMillis()
    }
}

/**
 * The full-screen player.
 *
 * Live: ▲ / CH+ is the next channel, ▼ / CH− the one before, OK shows the channel's name, Back closes.
 * On a phone: swipe up for the next channel, down for the one before, tap for the name.
 * A film or an episode: the player's own bar handles play, pause and skipping; Back closes. When an
 * episode ends, the next one in the list starts.
 *
 * English audio and English subtitles are chosen whenever the stream carries them. When a stream
 * drops, the player reconnects by itself and says so; after [MAX_TRIES] failures in a row it says the
 * stream isn't playing instead of leaving a black screen.
 *
 * A channel with several versions starts on its best that is not 4K. If the picture stays stopped for
 * [LONG_STOP_MS], or stops [STOPS_TO_MOVE] times in [STOPS_WINDOW_MS], it moves down one version and
 * says so. If a version won't play at all, the next is tried. It never moves back up by itself, because
 * each move interrupts the picture. ◀ is a better version, ▶ a lighter one; on a phone, swipe sideways.
 *
 * A film or episode with add-ons in use: the add-ons are asked where it can be played from, and what
 * they give is tried in order, best first, with the provider's own copy last. One that won't start is
 * left for the next, and the screen says so. Once one has started playing it is kept: a drop part way
 * through reconnects to the same one, at the same place.
 *
 * Live: the ≡ (menu) button starts recording the channel and ends it; on a phone, a button in the name
 * box does. Changing channel or closing the player also ends it. The recording is a copy of what the
 * player is being sent, so it uses no second connection to the provider.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(items: List<Playable>, startIndex: Int, live: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var stream by remember { mutableIntStateOf(items.getOrNull(index)?.let { Settled.startFor(it) } ?: 0) }
    var note by remember { mutableStateOf<String?>(null) }
    // Why the version was changed for the user, shown with the channel's name for a few seconds.
    var moved by remember { mutableStateOf<String?>(null) }
    var stopped by remember { mutableStateOf(false) }
    val stops = remember { ArrayList<Long>() }
    val hasPlayed = remember { booleanArrayOf(false) }
    // What the add-ons gave for each film or episode asked about so far, with the provider's copy at the end.
    val found = remember { HashMap<Int, List<Stream>>() }
    val streamsAt: (Int) -> List<Stream> = { i -> found[i] ?: items.getOrNull(i)?.streams.orEmpty() }
    // True once this stream has shown a picture. Until then a failure means "try the next", after it "reconnect".
    val started = remember { booleanArrayOf(false) }
    // Streams whose separate subtitle file has been left off, because they wouldn't play with it.
    val withoutSubtitles = remember { HashSet<String>() }
    var reload by remember { mutableIntStateOf(0) }
    val nextStream: (List<Stream>, Int) -> String = { all, next ->
        if (all[next].label == PROVIDER_COPY) "That stream isn't playing. Trying your provider's copy."
        else "That stream isn't playing. Trying the next one (" + (next + 1) + " of " + all.size + ")."
    }
    var showName by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }

    var recording by remember { mutableStateOf(false) }
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    /** Says [text] on screen for a few seconds, with the channel's name. */
    val say: (String) -> Unit = { text -> moved = text; showName += 1 }
    /** Ends the recording, if there is one. The file stays in Recordings and goes to Drive from there. */
    val recorderHolder = remember { arrayOfNulls<Recorder>(1) }
    val finishRecording: () -> Boolean = {
        val was = recording
        val made = recorderHolder[0]?.stop()
        if (was) Report.note("Recording", "ended: " + (made?.let { it.name + ", " + it.length() / (1024 * 1024) + " MB" } ?: "no file"))
        Recordings.recordingNow = null
        recording = false
        if (was) Recordings.changed()
        was
    }
    val recorder = remember {
        // A problem writing the file arrives from the player's own thread; the screen is told on the main one.
        Recorder(onProblem = { why -> main.post { finishRecording(); say(why) } }).also { recorderHolder[0] = it }
    }
    val http = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(Provider.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
    }
    val subtitlesWanted = remember { Choices.subtitles(context) }
    // What this stream has by way of subtitles: 0 not known yet, 1 showing, 2 none at all, 3 some, but none in English.
    var subtitles by remember { mutableIntStateOf(0) }
    val player = remember {
        val load = DefaultLoadControl.Builder()
            // Holds more picture in hand than the default before and during play, to ride out short stalls.
            .setBufferDurationsMs(20_000, 60_000, 2_500, 6_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DataSource.Factory { Either(Tap(DefaultDataSource.Factory(context, http).createDataSource(), recorder), TorrentSource(context.applicationContext)) },
                ),
            )
            .setLoadControl(load)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage("en")
                    .setPreferredTextLanguage("en")
                    .setSelectUndeterminedTextLanguage(true)
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesWanted)
                    .build()
                playWhenReady = true
            }
    }

    /** Another channel, film or episode: start it on the version it should start on. */
    val go: (Int) -> Unit = { next ->
        val wasRecording = finishRecording()
        index = next
        stream = items.getOrNull(next)?.let { Settled.startFor(it) } ?: 0
        tries = 0
        note = null
        moved = if (wasRecording) "Recording saved." else null
    }
    val toggleRecording: () -> Unit = {
        val item = items.getOrNull(index) ?: Playable("", "")
        val url = item.streams.getOrNull(stream)?.url ?: item.url
        when {
            url.isEmpty() -> Unit
            recording -> { finishRecording(); say("Recording saved.") }
            // Only a stream that arrives as one unbroken run can be copied as it plays.
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> say("This channel can't be recorded.")
            Recordings.folder(context).usableSpace < ROOM_TO_RECORD -> say("Not enough storage here to record.")
            else -> try {
                val file = Recordings.newFile(context, item.name)
                recorder.start(file)
                Recordings.recordingNow = file
                recording = true
                Report.note("Recording", "started: " + item.name)
                say(if (phone) "Recording. Tap the picture, then Stop, to finish." else "Recording. Press ≡ again to finish.")
            } catch (e: java.io.IOException) {
                say("Couldn't start recording.")
            }
        }
    }
    /** Another version of the same channel. [why] is said on screen; null when the user chose it. */
    val change: (Int, String?) -> Unit = { next, why ->
        if (why != null) Report.note("Player", items.getOrNull(index)?.name.orEmpty() + ": " + why)
        moved = why
        note = null
        stream = next
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                stopped = state == Player.STATE_BUFFERING
                val item = items.getOrNull(index)
                val versions = streamsAt(index)
                if (state == Player.STATE_READY) {
                    tries = 0
                    note = null
                    hasPlayed[0] = true
                    started[0] = true
                    if (item != null) Settled.played(item, stream, versions.size)
                }
                // The picture was playing and has stopped to catch up. Too many of those: move down a version.
                if (state == Player.STATE_BUFFERING && live && hasPlayed[0] && stream < versions.size - 1) {
                    val now = System.currentTimeMillis()
                    stops.removeAll { now - it > STOPS_WINDOW_MS }
                    stops += now
                    if (stops.size >= STOPS_TO_MOVE) {
                        change(stream + 1, "Kept stopping on " + versions[stream].label + ". Now on " + versions[stream + 1].label + ".")
                    }
                }
                // An episode has finished and another follows it in the list: play that one.
                if (state == Player.STATE_ENDED && !live && index < items.size - 1) go(index + 1)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val text = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                subtitles = when {
                    text.any { it.isSelected } -> 1
                    text.isEmpty() -> 2
                    else -> 3
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlayed[0] = false
                val versions = streamsAt(index)
                val kind = when {
                    versions.getOrNull(stream)?.url?.startsWith("magnet:") == true -> "torrent"
                    live -> "live channel"
                    else -> "film or recording"
                }
                Report.note(
                    "Player",
                    items.getOrNull(index)?.name.orEmpty() + " (" + kind + ", stream " + (stream + 1) + " of " + versions.size.coerceAtLeast(1) + "): " +
                        error.errorCodeName + " · " + (error.cause?.message ?: error.message ?: "no detail"),
                )
                if (!live && versions.size > 1 && !started[0]) {
                    // A film's stream that never started. First without its separate subtitles, in case they are the fault.
                    val now = versions.getOrNull(stream)
                    when {
                        now?.subtitle != null && withoutSubtitles.add(now.url) -> reload += 1
                        stream < versions.size - 1 -> change(stream + 1, nextStream(versions, stream + 1))
                        else -> note = "This isn't playing right now."
                    }
                    return
                }
                tries += 1
                if (tries >= maxOf(MAX_TRIES, versions.size * 2)) {
                    note = if (live) "This channel isn't playing right now." else "This film isn't playing right now."
                } else if (live && versions.size > 1 && tries % 2 == 0) {
                    // Two failures in a row on this version: try the next one, going round to the first after the last.
                    val next = (stream + 1) % versions.size
                    change(next, versions[stream].label + " isn't playing. Trying " + versions[next].label + ".")
                } else {
                    note = "Reconnecting…"
                    retryTick += 1
                }
            }
        }
        player.addListener(listener)
        // Copying recordings to Google Drive waits while anything is playing.
        Recordings.playing = true
        onDispose {
            player.removeListener(listener)
            player.release()
            finishRecording()
            Recordings.playing = false
            Recordings.wake(context)
            // Any torrent that was playing is stopped and what it fetched is removed. That can take a moment.
            val app = context.applicationContext
            Thread({ Torrents.close(app) }, "kuetv-torrent-close").start()
        }
    }

    // A new channel (or film), or another version of this channel: start it clean.
    LaunchedEffect(index, stream, reload) {
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        val search = item.find
        if (search != null && index !in found) {
            // The add-ons haven't been asked about this one yet.
            player.stop()
            note = "Finding the best stream…"
            val result = try {
                search()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val theirs = result?.streams.orEmpty()
            Report.note(
                "Add-ons",
                item.name + ": " + if (result == null) "asking them failed" else theirs.size.toString() + " streams, " + theirs.count { it.url.startsWith("magnet:") } + " of them torrents",
            )
            found[index] = theirs + Stream(PROVIDER_COPY, item.url)
            note = null
            if (theirs.isEmpty()) moved = "Nothing from your add-ons for this one. Playing your provider's copy."
            val start = Settled.startFor(item, theirs.size + 1)
            if (start != stream) {
                stream = start
                return@LaunchedEffect
            }
        }
        val now = streamsAt(index).getOrNull(stream)
        stops.clear()
        hasPlayed[0] = false
        started[0] = false
        subtitles = 0
        val torrent = (now?.url ?: item.url).startsWith("magnet:")
        if (torrent && moved == null) moved = "This one is a torrent. It can take a minute to start."
        if (!torrent) Thread({ Torrents.stopCurrent() }, "kuetv-torrent-stop").start()
        http.setDefaultRequestProperties(now?.headers?.takeIf { it.isNotEmpty() } ?: item.headers)
        val media = MediaItem.Builder().setUri(now?.url ?: item.url)
        val subtitle = now?.subtitle?.takeIf { now.url !in withoutSubtitles }
        if (subtitle != null) {
            val kind = if (subtitle.substringBefore('?').endsWith(".vtt", ignoreCase = true)) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            media.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle))
                        .setMimeType(kind).setLanguage("en").setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build(),
                ),
            )
        }
        player.setMediaItem(media.build())
        player.prepare()
        showName += 1
    }
    // The picture has stayed stopped too long, at the start or part way through: move down a version.
    LaunchedEffect(stopped, index, stream, reload) {
        if (!stopped) return@LaunchedEffect
        val versions = streamsAt(index)
        if (live) {
            delay(LONG_STOP_MS)
            if (stream < versions.size - 1) {
                change(stream + 1, "Kept stopping on " + versions[stream].label + ". Now on " + versions[stream + 1].label + ".")
            }
        } else if (versions.size > 1 && !started[0]) {
            // A film's stream that is taking too long to start: the next one may be quicker.
            delay(if (versions.getOrNull(stream)?.url?.startsWith("magnet:") == true) SLOW_TORRENT_MS else SLOW_START_MS)
            if (!started[0] && stream < versions.size - 1) change(stream + 1, nextStream(versions, stream + 1))
        }
    }
    // After a drop: wait a moment, then pick the stream up again.
    LaunchedEffect(retryTick) {
        if (retryTick == 0) return@LaunchedEffect
        delay(1_500)
        if (live) player.seekToDefaultPosition()
        player.prepare()
    }
    // What is on each channel watched so far, asked for once the channel has been stayed on for a moment.
    val guides = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<Programme>>() }
    LaunchedEffect(index) {
        val ask = items.getOrNull(index)?.guide ?: return@LaunchedEffect
        if (!live || index in guides) return@LaunchedEffect
        delay(700)
        val listing = try {
            ask()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        guides[index] = listing
        // If the name box is still up, keep it up long enough to read what has just arrived.
        if (listing.isNotEmpty()) showName += 1
    }
    var nameVisible by remember { mutableStateOf(false) }
    LaunchedEffect(showName) {
        if (showName == 0) return@LaunchedEffect
        nameVisible = true
        // Longer when there is something to read: why the stream changed, or what the programme is about.
        delay(if (moved != null || (live && index in guides)) 9_000 else 4_000)
        nameVisible = false
        moved = null
    }

    BackHandler { onClose() }

    // KueTV has left the screen (Home was pressed, or another app opened): stop the picture and sound,
    // unless a recording is being made from it. Back on screen: carry on, a live channel from now.
    val leftScreen = remember { booleanArrayOf(false) }
    val visible = Screen.visible
    LaunchedEffect(visible) {
        if (!visible && !recording) {
            leftScreen[0] = true
            player.pause()
        } else if (visible && leftScreen[0]) {
            leftScreen[0] = false
            if (live) { player.seekToDefaultPosition(); player.prepare() }
            player.play()
        }
    }

    // Phone: the picture takes the whole screen while playing. A swipe from the edge brings the bars back.
    if (phone) {
        DisposableEffect(Unit) {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    window.insetsController?.let {
                        it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        it.hide(android.view.WindowInsets.Type.systemBars())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                }
            }
            onDispose {
                if (window != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        window.insetsController?.show(android.view.WindowInsets.Type.navigationBars())
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = 0
                    }
                }
            }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (live) runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!live) Modifier else Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionUp, Key.ChannelUp -> { if (items.size > 1) go((index + 1) % items.size); true }
                            Key.DirectionDown, Key.ChannelDown -> { if (items.size > 1) go((index - 1 + items.size) % items.size); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showName += 1; true }
                            Key.Menu, Key.MediaRecord -> { toggleRecording(); true }
                            // A channel with several versions: left is a better one, right a lighter one. At either end the name box shows again.
                            Key.DirectionLeft, Key.DirectionRight -> {
                                val count = items.getOrNull(index)?.streams?.size ?: 0
                                val next = if (e.key == Key.DirectionLeft) stream - 1 else stream + 1
                                if (count > 1) { if (next in 0 until count) change(next, null) else showName += 1 }
                                true
                            }
                            else -> false
                        }
                    },
            ),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = !live
                    keepScreenOn = true
                    // A film's bar has a subtitles button, to pick a language or turn them off.
                    setShowSubtitleButton(true)
                    // Subtitles: large white letters on a dark band, the same on every channel and film.
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE, 0xCC000000.toInt(), android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
                            ),
                        )
                        setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.4f)
                    }
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    if (!live) {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                    }
                }
            },
        )
        if (phone && live) {
            // A clear sheet over the picture that takes the touches: swipe to change channel, tap for the name.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(items.size) {
                        var up = 0f
                        var across = 0f
                        detectDragGestures(
                            onDragStart = { up = 0f; across = 0f },
                            onDragEnd = {
                                val far = 60.dp.toPx()
                                val versions = items.getOrNull(index)?.streams?.size ?: 0
                                if (kotlin.math.abs(up) >= kotlin.math.abs(across)) {
                                    // Up and down: the next channel, or the one before.
                                    if (items.size > 1 && up <= -far) go((index + 1) % items.size)
                                    else if (items.size > 1 && up >= far) go((index - 1 + items.size) % items.size)
                                } else {
                                    // Sideways: right for a better version, left for a lighter one.
                                    if (across >= far && stream > 0) change(stream - 1, null)
                                    else if (across <= -far && stream < versions - 1) change(stream + 1, null)
                                }
                            },
                        ) { _, by -> up += by.y; across += by.x }
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { showName += 1 }) },
            )
        }
        val title = items.getOrNull(index)?.name.orEmpty()
        val versions = items.getOrNull(index)?.streams.orEmpty()
        if ((nameVisible && (live || moved != null)) || note != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (phone) 16.dp else 36.dp)
                    .widthIn(max = 980.dp)
                    .background(Color(0xCC0E1626), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                val at = System.currentTimeMillis()
                Text(
                    if (live) title + "  ·  " + clock.format(java.util.Date(at)) else title,
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                val listing = if (live) guides[index].orEmpty() else emptyList()
                val onNow = listing.firstOrNull { it.startMs <= at && it.stopMs > at }
                val next = listing.firstOrNull { it.startMs >= (onNow?.stopMs ?: at) }
                if (onNow != null) {
                    Text(
                        "Now · " + clock.format(java.util.Date(onNow.startMs)) + " to " + clock.format(java.util.Date(onNow.stopMs)) + " · " + onNow.title.ifBlank { "No title" },
                        color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    // The whole description, not cut short.
                    if (onNow.description.isNotBlank()) Text(onNow.description, color = Color(0xFFB9C2D3), fontSize = 18.sp)
                }
                if (next != null) {
                    Text(
                        "Next · " + clock.format(java.util.Date(next.startMs)) + " · " + next.title.ifBlank { "No title" },
                        color = Color(0xFFB9C2D3), fontSize = 18.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (live && versions.size > 1 && stream in versions.indices) {
                    Text(
                        versions[stream].label + " · version " + (stream + 1) + " of " + versions.size +
                            if (phone) " · swipe sideways to change" else " · ◀ ▶ to change",
                        color = Color(0xFFB9C2D3), fontSize = 18.sp, maxLines = 1,
                    )
                }
                if (live && subtitles != 0) {
                    Text(
                        when {
                            !subtitlesWanted -> "Subtitles are turned off in Settings"
                            subtitles == 1 -> "Subtitles: on"
                            subtitles == 3 -> "No English subtitles on this channel"
                            else -> "No subtitles on this channel"
                        },
                        color = Color(0xFFB9C2D3), fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                moved?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp) }
                note?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp) }
                if (phone && live && items.size > 1) Text("Swipe up or down to change channel", color = Color(0xFFB9C2D3), fontSize = 16.sp)
                if (live && !phone) Text(if (recording) "● Recording · ≡ to finish" else "≡ records this channel", color = Color(0xFFB9C2D3), fontSize = 16.sp)
                if (live && phone) {
                    Text(
                        if (recording) "■ Stop recording" else "● Record",
                        color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .background(Color(0xFFFFD23F), RoundedCornerShape(22.dp))
                            .clickable { toggleRecording() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
        if (recording) {
            Text(
                "● REC", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (phone) 16.dp else 36.dp)
                    .background(Color(0xCCB3261E), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * The small TV: a live channel playing in a corner while the lists are browsed. It has its own simple
 * player, which is closed when the full-screen one opens. It stops when KueTV leaves the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniTv(item: Playable, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var retry by remember(item.url) { mutableIntStateOf(0) }
    val player = remember(item.url) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Provider.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredAudioLanguage("en").build()
                setMediaItem(MediaItem.fromUri(item.url))
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { retry += 1 }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val visible = Screen.visible
    LaunchedEffect(player, visible, retry) {
        if (!visible) { player.stop(); return@LaunchedEffect }
        // A few tries after a drop, a little apart, then it is left quiet rather than hammering the provider.
        if (retry > 5) return@LaunchedEffect
        if (retry > 0) delay(3_000)
        player.seekToDefaultPosition()
        player.prepare()
    }
    Column(
        modifier
            .width(380.dp)
            .background(Color(0xFF16213A), RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { ctx -> PlayerView(ctx).apply { useController = false; isFocusable = false } },
            update = { it.player = player },
        )
        Text(item.name, color = Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
