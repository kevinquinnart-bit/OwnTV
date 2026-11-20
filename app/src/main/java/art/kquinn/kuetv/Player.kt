package art.kquinn.kuetv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import art.kquinn.kuetv.TrText as Text
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
private class Either(private val web: DataSource, private val torrent: DataSource, private val shift: DataSource) : DataSource {
    private var using: DataSource = web

    override fun addTransferListener(transferListener: TransferListener) {
        web.addTransferListener(transferListener)
        torrent.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // A torrent, the copy kept for pause and rewind, or the internet.
        using = when (dataSpec.uri.scheme) {
            "magnet" -> torrent
            "shift" -> shift
            else -> web
        }
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
    /** For a live channel the provider keeps for catch-up: the programme on now, played from its start. */
    val fromStart: ((Programme) -> Playable)? = null,
    /** For a film or episode: its picture (empty if it has none). Set only on those, which go on Home's "Carry on watching". */
    val poster: String? = null,
    /** For catch-up: what plays once this has finished, the next programme on the channel; null when there is none yet. */
    val then: (suspend () -> Playable?)? = null,
    /** For catch-up: when it first went out, so the player can say what time it was on air at each moment. */
    val airedFromMs: Long? = null,
) {
    /** What it is known by when its place is kept: its address, or, with no address of its own (add-on only), its name. */
    val key: String get() = url.ifEmpty { NO_ADDRESS + name }

    companion object {
        const val NO_ADDRESS = "kue:"
    }
}

/** One button along the bottom of the live name box. */
private class Control(
    /** Said under the button. */
    val label: String,
    val icon: Icon,
    /** -1 or 1 for the buttons that wind back or on; holding one down winds faster. */
    val seek: Int = 0,
    /** Written inside the button, for the picture's quality. */
    val badge: String = "",
    /** How things stand for this button, written just above it: "Paused", "No subtitles on this channel". */
    val status: String = "",
    val act: () -> Unit,
)

/** The pictures drawn on the player's buttons. */
private enum class Icon { BACK, PLAY, PAUSE, ON, FROM_START, RECORD, STOP, PICTURE, SUBS_ON, SUBS_OFF, OPTIONS, LIST, MULTI, PREVIOUS, LIVE }

/** How much one key-repeat of a held wind button adds: faster the longer it is held. Public for the tests. */
fun windSpeed(repeats: Int): Int = when {
    repeats < 15 -> 1
    repeats < 35 -> 2
    repeats < 70 -> 4
    else -> 8
}

/** "2 min 30 s", "45 s". */
fun windWords(ms: Long): String {
    val sec = kotlin.math.abs(ms) / 1000
    return if (sec < 60) "$sec s" else (sec / 60).toString() + " min" + if (sec % 60 == 0L) "" else " " + sec % 60 + " s"
}

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
        val kept = last[item.key] ?: return item.startStream
        return if (System.currentTimeMillis() - kept.second < SETTLED_MS && kept.first in 0 until count) kept.first else item.startStream
    }

    fun played(item: Playable, stream: Int, count: Int) {
        if (count > 1) last[item.key] = stream to System.currentTimeMillis()
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
@OptIn(UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    firstItems: List<Playable>,
    startIndex: Int,
    live: Boolean,
    onClose: () -> Unit,
    /** Plays the programme on now from its start, through catch-up, in place of the live channel. */
    onFromStart: ((Playable) -> Unit)? = null,
    /** Live: opens Multiview on these channels, starting with the one playing. */
    onMultiview: ((List<Playable>, Int) -> Unit)? = null,
    /** Live: other lists of channels to pick from in the list over the picture (Favourites, Recently watched, All). */
    lists: List<Pair<String, () -> List<Playable>>> = emptyList(),
    /** Plays these with VLC instead, from this one. */
    onVlc: ((List<Playable>, Int) -> Unit)? = null,
    /** Live: ◀ leaves full screen for the Live TV list, on this channel. */
    onChannels: ((Playable) -> Unit)? = null,
) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    // What is to be played: those given, and for catch-up, each next programme added as the one before it ends.
    val items = remember { androidx.compose.runtime.mutableStateListOf<Playable>().apply { addAll(firstItems) } }
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
    // Whether a torrent has been played in this sitting: only then is there a torrent to stop or tidy away.
    val usedTorrent = remember { booleanArrayOf(false) }
    // Films and episodes already carried on from where they were left, so a change of stream doesn't do it twice over.
    val carriedOn = remember { HashSet<Int>() }
    val nextStream: (List<Stream>, Int) -> String = { all, next ->
        if (all[next].label == PROVIDER_COPY) "That stream isn't playing. Trying your provider's copy."
        else "That stream isn't playing. Trying the next one (" + (next + 1) + " of " + all.size + ")."
    }
    var showName by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }

    var recording by remember { mutableStateOf(false) }
    // The next episode (or programme), counting down to start: its place in the list, or -1 with none waiting.
    var upNext by remember { mutableIntStateOf(-1) }
    var upNextIn by remember { mutableIntStateOf(0) }
    // The Options panel: the sound, the picture's shape and the sound sync. Which row the remote is on.
    var options by remember { mutableStateOf(false) }
    var optionRow by remember { mutableIntStateOf(0) }
    var sounds by remember { mutableStateOf<List<SoundChoice>>(emptyList()) }
    var shape by remember { mutableIntStateOf(PictureShape.saved(context)) }
    var syncMs by remember { mutableIntStateOf(0) }
    // Live: the list of channels over the picture (◀ opens it), and the row the remote is on in it.
    var channelList by remember { mutableStateOf(false) }
    var listPick by remember { mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Which list the list over the picture shows: -1 the one playing from, or one of [lists].
    var shownList by remember { mutableIntStateOf(-1) }
    val shownItems: List<Playable> = if (shownList < 0) items else remember(shownList) { runCatching { lists[shownList].second() }.getOrDefault(emptyList()) }
    // Above the channels, a row for each list to switch to.
    val headerRows = if (lists.isEmpty()) 0 else lists.size + 1
    // A channel number being typed on the remote's number keys; it is gone to a moment after the last key.
    var typed by remember { mutableStateOf("") }
    // Live: the channel watched before this one, for 0 (or the Last channel button) to go back to.
    var previous by remember { mutableIntStateOf(-1) }
    // Sound only: the picture turned off, the sound kept (for a match's commentary, say).
    var soundOnly by remember { mutableStateOf(false) }
    // The control buttons in the name box: brought up by OK on a TV (◀ ▶ then move along them), always there on a phone.
    var controls by remember { mutableStateOf(false) }
    var pick by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    // A wind button held down: how far it has wound so far (back is negative), and how fast.
    var held by remember { mutableLongStateOf(0L) }
    // The channel and version the box was last brought up for.
    val shownFor = remember { intArrayOf(-1, -1) }
    var heldSpeed by remember { mutableIntStateOf(1) }
    val heldKey = remember { arrayOfNulls<Key>(1) }
    val windScope = rememberCoroutineScope()
    // Paused at some point since this channel started, so the picture is behind live.
    var behind by remember { mutableStateOf(false) }
    // Pause and rewind: the copy of this channel being kept on the device, if it is playing through one.
    val shiftWanted = remember { Choices.timeShift(context) }
    val shiftHolder = remember { arrayOfNulls<ShiftBuffer>(1) }
    val shiftUrl = remember { arrayOfNulls<String>(1) }
    var shifting by remember { mutableStateOf(false) }
    // When the place the player was last started at in the copy arrived from the provider.
    var shiftBase by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }
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
    // Through the DNS server chosen in Settings, when there is one.
    val http = remember { KueDns.playerHttp() }
    val subtitlesWanted = remember { Choices.subtitles(context) }
    // Subtitles for this sitting, as set in Settings to begin with; the Subtitles button changes it here only.
    var subsOn by remember { mutableStateOf(subtitlesWanted) }
    val lang = remember { Langs.of(Choices.subtitleLang(context)) }
    // Films already said to have no subtitles, so it is said once for each stream.
    val toldNoSubs = remember { HashSet<String>() }
    // What this stream has by way of subtitles: 0 not known yet, 1 showing, 2 none at all, 3 some, but none in English.
    var subtitles by remember { mutableIntStateOf(0) }
    val player = remember {
        val load = DefaultLoadControl.Builder()
            // Holds more picture in hand than the default before and during play, to ride out short stalls.
            .setBufferDurationsMs(20_000, 60_000, 2_500, 6_000)
            .build()
        ExoPlayer.Builder(context, SyncedRenderers(context))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DataSource.Factory { Either(Tap(DefaultDataSource.Factory(context, http).createDataSource(), recorder), TorrentSource(context.applicationContext), ShiftSource()) },
                ).setDrmSessionManagerProvider(Drm.provider(http)),
            )
            .setLoadControl(load)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(SoundLanguage.get(context))
                    .setPreferredTextLanguage(lang.code)
                    .setSelectUndeterminedTextLanguage(true)
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesWanted)
                    // The most detail chosen in Settings; and with stereo only, two-channel sound when the stream has it.
                    .apply { PictureQuality.size(PictureQuality.get(context)).let { (w, h) -> setMaxVideoSize(w, h) } }
                    .setMaxAudioChannelCount(if (Sound.stereoOnly(context)) 2 else Int.MAX_VALUE)
                    .build()
                playWhenReady = true
            }
    }

    /** Notes how far into a film the player has got, so it can carry on from there next time. */
    val keepPlace: () -> Unit = {
        val item = items.getOrNull(index)
        if (!live && item != null && started[0] && player.duration > 0) Positions.keep(context, item.key, player.currentPosition, player.duration, item.name, item.poster)
    }
    /** Another channel, film or episode: start it on the version it should start on. */
    val go: (Int) -> Unit = { next ->
        keepPlace()
        val wasRecording = finishRecording()
        upNext = -1
        channelList = false
        if (live && next != index) previous = index
        // A sound picked for the last one doesn't carry over: the next starts on its own first choice.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO).build()
        index = next
        stream = items.getOrNull(next)?.let { Settled.startFor(it) } ?: 0
        controls = false
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
            url.substringBefore('?').let { it.endsWith(".m3u8", ignoreCase = true) || it.endsWith(".mpd", ignoreCase = true) } || Drm.of(url) != null -> say("This channel can't be recorded.")
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

    // The TV switched to the picture's own frame rate, when that is chosen; back to its usual one after.
    DisposableEffect(player) {
        val rate = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val fps = player.videoFormat?.frameRate ?: return
                if (fps > 0f) FrameRate.match(context, fps)
            }
        }
        player.addListener(rate)
        onDispose {
            player.removeListener(rate)
            FrameRate.release(context)
        }
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
                    if (!live && !started[0] && item != null) {
                        // A film that was left part way through carries on from there, on whichever stream is playing it.
                        val place = Positions.of(context, item.key)
                        if (place > 0 && player.duration > 0 && place < player.duration && carriedOn.add(index)) {
                            player.seekTo(place)
                            say("Carrying on from " + place / 60_000 + " minutes in. Rewind to start again.")
                        } else if (place > 0 && index in carriedOn) {
                            // Another stream of the same film, after the first gave up: back to the same place.
                            player.seekTo(place)
                        }
                    }
                    // The first time this film, episode, catch-up or recording plays: it goes on History.
                    if (!live && !started[0] && item != null) History.add(context, if (item.poster != null) History.FILM else History.OTHER, item.name, item.key, item.poster)
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
                if (state == Player.STATE_ENDED && !live && item != null) {
                    Positions.forget(context, item.key)
                    // A film or episode played to the end is watched (not catch-up or a recording, which have no picture).
                    if (item.poster != null) Watched.set(context, item.key, true)
                }
                // An episode has finished and another follows it in the list: count down to it. Catch-up: the
                // programme that followed, once it has finished airing.
                if (state == Player.STATE_ENDED && !live && upNext < 0) {
                    if (index < items.size - 1) {
                        upNext = index + 1
                    } else {
                        val more = items.getOrNull(index)?.then
                        if (more != null) windScope.launch {
                            val following = try {
                                more()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            if (following != null) {
                                items.add(following)
                                upNext = items.size - 1
                            }
                        }
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                sounds = soundChoices(tracks)
                val text = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                subtitles = when {
                    text.any { it.isSelected } -> 1
                    text.isEmpty() -> 2
                    else -> 3
                }
                // A film whose stream turns out to have none in the wanted language: say so, once.
                if (!live && subsOn && subtitles != 1 && !tracks.isEmpty && toldNoSubs.add("$index:$stream")) {
                    say("No " + lang.name + " subtitles found for this one.")
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
            keepPlace()
            player.removeListener(listener)
            player.release()
            shiftHolder[0]?.close()
            shiftHolder[0] = null
            finishRecording()
            Recordings.playing = false
            Recordings.wake(context)
            // Any torrent that was playing is stopped and what it fetched is removed. That can take a moment.
            if (usedTorrent[0]) {
                val app = context.applicationContext
                Thread({ Torrents.close(app) }, "kuetv-torrent-close").start()
            }
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
            // Something with no copy at the provider plays from the add-ons alone.
            found[index] = if (item.url.isEmpty()) theirs else theirs + Stream(PROVIDER_COPY, item.url)
            note = null
            if (theirs.isEmpty() && item.url.isEmpty()) {
                note = if (result == null) "Couldn't ask your add-ons. Check the internet and try again." else "Your add-ons have nothing to play this from."
                return@LaunchedEffect
            }
            if (theirs.isEmpty()) moved = "Nothing from your add-ons for this one. Playing your provider's copy."
            val start = Settled.startFor(item, found[index].orEmpty().size)
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
        if (torrent) usedTorrent[0] = true
        else if (usedTorrent[0]) Thread({ Torrents.stopCurrent() }, "kuetv-torrent-stop").start()
        http.setDefaultRequestProperties(now?.headers?.takeIf { it.isNotEmpty() } ?: item.headers)
        // Live, with pause and rewind on: the channel is fetched into a copy here and played from that.
        // A stream that comes in many small files (.m3u8) can't be copied this way, so it plays as before.
        var address = now?.url ?: item.url
        if (Stalker.isLink(address)) {
            // A portal's channel: the portal gives a fresh link to play it from.
            val link = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Stalker.resolve(address) } }
            address = link.getOrElse {
                note = it.message ?: "The portal didn't give a link for this channel."
                return@LaunchedEffect
            }
        }
        var playFrom = address
        // Streams in many small files (.m3u8, .mpd), and protected ones, can't be copied as they play.
        val pieces = address.substringBefore('?').let { it.endsWith(".m3u8", ignoreCase = true) || it.endsWith(".mpd", ignoreCase = true) } || Drm.of(address) != null
        if (live && shiftWanted && !torrent && !pieces) {
            var copy = shiftHolder[0]
            if (copy == null || copy.closed || shiftUrl[0] != address) {
                copy?.close()
                copy = ShiftBuffer.begin(context, address, now?.headers?.takeIf { it.isNotEmpty() } ?: item.headers)
                shiftHolder[0] = copy
                shiftUrl[0] = address
                if (copy != null) {
                    // Recording copies what arrives from the provider, as it arrives: the channel live, whatever is on screen.
                    copy.onJoined = { recorder.newConnection() }
                    copy.onBytes = { b, o, n -> recorder.take(b, o, n) }
                    Report.note("Pause and rewind", "keeping a copy of " + item.name + ", up to " + copy.capBytes / (1024 * 1024) + " MB")
                } else {
                    Report.note("Pause and rewind", "not enough storage here; " + item.name + " plays without it")
                }
            }
            if (copy != null) {
                playFrom = ShiftBuffer.address(copy, copy.head)
                shiftBase = System.currentTimeMillis()
            }
            shifting = copy != null
        } else {
            shiftHolder[0]?.close()
            shiftHolder[0] = null
            shiftUrl[0] = null
            shifting = false
        }
        val media = MediaItem.Builder().setUri(playFrom)
        val subtitle = now?.subtitle?.takeIf { now.url !in withoutSubtitles }
        if (subtitle != null) {
            val kind = if (subtitle.substringBefore('?').endsWith(".vtt", ignoreCase = true)) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            media.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle))
                        .setMimeType(kind).setLanguage(lang.code).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build(),
                ),
            )
        }
        player.setMediaItem(media.build())
        player.prepare()
        // A new channel, or back to live: playing, and at live. The box comes up for a new channel or version only,
        // not each time the player reconnects to the same one by itself.
        player.playWhenReady = true
        behind = false
        if (shownFor[0] != index || shownFor[1] != stream) {
            shownFor[0] = index
            shownFor[1] = stream
            showName += 1
        }
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
    // Every few seconds, note how far in a film has got.
    LaunchedEffect(index) {
        while (!live) {
            delay(8_000)
            keepPlace()
        }
    }
    // A live channel stayed on for a little while goes on Live TV's "Recently watched".
    LaunchedEffect(index) {
        if (!live) return@LaunchedEffect
        val key = items.getOrNull(index)?.key ?: return@LaunchedEffect
        delay(RECENT_AFTER_MS)
        Recent.add(context, key)
        History.add(context, History.LIVE, items.getOrNull(index)?.name.orEmpty(), key, null)
    }
    // The sleep timer: a minute's warning, then what is playing stops and the player closes.
    LaunchedEffect(SleepTimer.endAtMs) {
        val end = SleepTimer.endAtMs
        if (end == 0L) return@LaunchedEffect
        delay((end - System.currentTimeMillis() - 60_000L).coerceAtLeast(0L))
        say("Sleep timer: stopping in a minute. Options › Sleep timer to keep watching.")
        delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
        SleepTimer.endAtMs = 0L
        onClose()
    }
    // Sound only: the picture isn't fetched or shown.
    LaunchedEffect(soundOnly) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, soundOnly).build()
    }
    // A number typed on the remote: a moment after the last key, that channel in the list.
    LaunchedEffect(typed) {
        if (typed.isEmpty()) return@LaunchedEffect
        delay(TYPED_WAIT_MS)
        val n = typed.toIntOrNull() ?: 0
        val zero = typed == "0"
        typed = ""
        when {
            // 0 on its own: back to the channel watched before.
            zero -> if (previous in items.indices) go(previous) else say("There is no channel before this one yet.")
            n in 1..items.size -> go(n - 1)
            else -> say("There is no channel $n in this list. It has " + items.size + ".")
        }
    }
    // The list of channels follows the remote, keeping a few rows above the one it is on.
    LaunchedEffect(channelList, listPick, shownList) {
        if (!channelList) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || listPick <= visible.first().index || listPick >= visible.last().index) listState.scrollToItem((listPick - 3).coerceAtLeast(0))
    }
    // The next episode or programme: a few seconds to see what it is, then it starts. OK starts it now, Back stays here.
    LaunchedEffect(upNext) {
        if (upNext < 0) return@LaunchedEffect
        for (s in UP_NEXT_SECONDS downTo 1) {
            upNextIn = s
            delay(1_000)
        }
        val n = upNext
        if (n in items.indices) go(n)
    }
    // Each channel and film keeps its own sound sync.
    LaunchedEffect(index) {
        val key = items.getOrNull(index)?.key ?: return@LaunchedEffect
        syncMs = SoundSync.saved(context, key)
        SoundSync.offsetUs = syncMs * 1_000L
    }
    DisposableEffect(Unit) { onDispose { SoundSync.offsetUs = 0L } }
    // While something plays, leaving KueTV (Home) can shrink it to a small picture in the corner, where the device allows.
    DisposableEffect(Unit) {
        Pip.wanted = Pip.on(context)
        onDispose { Pip.wanted = false }
    }
    // After a drop: wait a moment, then pick the stream up again.
    LaunchedEffect(retryTick) {
        if (retryTick == 0) return@LaunchedEffect
        delay(1_500)
        if (shifting) {
            // Playing from the copy: start again at its newest end.
            reload += 1
            return@LaunchedEffect
        }
        if (live) player.seekToDefaultPosition()
        player.prepare()
    }
    // What is on each channel watched so far, asked for once the channel has been stayed on for a moment.
    // Whether the name box is up. Back, or the box's own timer, puts it away.
    var nameVisible by remember { mutableStateOf(false) }
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
        // If the name box is still up, keep it up long enough to read what has just arrived. If it has been
        // put away (Back), it stays away: the guide arriving must not bring it back up.
        if (listing.isNotEmpty() && nameVisible) showName += 1
    }
    LaunchedEffect(showName, paused) {
        if (showName == 0 && !paused) return@LaunchedEffect
        nameVisible = true
        // While paused the box stays up, saying so.
        if (paused) return@LaunchedEffect
        // Longer when there is something to read: why the stream changed, what the programme is about, or the buttons.
        delay(if (controls || moved != null || (live && index in guides)) 9_000 else 4_000)
        nameVisible = false
        controls = false
        moved = null
    }

    // What is on now and next on this channel, for the name box and the "From the start" button.
    val clockNow = System.currentTimeMillis()
    val listing = if (live) guides[index].orEmpty() else emptyList()
    val onNow = listing.firstOrNull { it.startMs <= clockNow && it.stopMs > clockNow }
    val nextUp = listing.firstOrNull { it.startMs >= (onNow?.stopMs ?: clockNow) }
    val versions = streamsAt(index)
    val fromStart: (() -> Unit)? = items.getOrNull(index)?.fromStart?.let { make ->
        if (onNow == null || onFromStart == null) null else ({
            Report.note("Player", items.getOrNull(index)?.name.orEmpty() + ": from the start, through catch-up")
            onFromStart(make(onNow))
        })
    }
    // How far behind live the picture is, when playing from the copy. Refreshed each second while the box is up.
    LaunchedEffect(nameVisible, shifting) {
        // Also for a film, whose place along its bar moves on.
        while (nameVisible && (shifting || !live)) {
            delay(1_000)
            tick += 1
        }
    }
    val behindMs = if (!shifting) 0L else {
        tick
        (System.currentTimeMillis() - (shiftBase + player.currentPosition)).coerceAtLeast(0L)
    }
    // Starting up takes a few seconds, so only past this is the picture counted as behind.
    val isBehind = if (shifting) behindMs > 15_000 else behind
    /** Moves the picture [byMs] through the copy: back is negative. Past the newest end, it goes back to live. */
    val jump: (Long) -> Unit = jump@{ byMs ->
        val copy = shiftHolder[0]
        if (!shifting || copy == null) return@jump
        val showing = shiftBase + player.currentPosition
        val target = (showing + byMs).coerceAtLeast(copy.oldestTime())
        if (target >= System.currentTimeMillis() - 5_000) {
            reload += 1
        } else {
            val offset = copy.offsetAt(target)
            shiftBase = copy.timeAt(offset)
            player.setMediaItem(MediaItem.fromUri(ShiftBuffer.address(copy, offset)))
            player.prepare()
        }
        showName += 1
    }
    val togglePause: () -> Unit = {
        if (player.playWhenReady) {
            player.pause()
            behind = true
        } else {
            player.play()
        }
    }
    val toggleSubtitles: () -> Unit = {
        subsOn = !subsOn
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subsOn).build()
    }
    // Winding: a live channel through the copy kept on the device, 30 seconds a press; anything else through itself, 10.
    val windStep = if (live) 30_000L else 10_000L
    val canWind = if (live) shifting else true
    val windBy: (Long) -> Unit = { ms ->
        if (live) {
            jump(ms)
        } else {
            val length = player.duration
            val target = (player.currentPosition + ms).coerceAtLeast(0L).let { if (length > 0) it.coerceAtMost(length - 1_000) else it }
            player.seekTo(target)
            showName += 1
        }
    }
    val subsStatus = when {
        !subsOn -> "Subtitles off"
        subtitles == 1 -> "Subtitles showing"
        subtitles == 3 -> "No " + lang.name + " subtitles"
        subtitles == 2 -> if (live) "No subtitles on this channel" else "No subtitles"
        else -> ""
    }
    val subsButton = Control(if (subsOn) "Subtitles on" else "Subtitles off", if (subsOn) Icon.SUBS_ON else Icon.SUBS_OFF, status = subsStatus, act = toggleSubtitles)
    val pictureButton = if (versions.size > 1 && stream in versions.indices) {
        Control(
            if (live) "Picture" else "Stream", Icon.PICTURE, badge = versions[stream].label,
            status = versions[stream].label.take(12) + " · " + (stream + 1) + " of " + versions.size,
        ) { change((stream + 1) % versions.size, null) }
    } else null
    // Above the wind-back button: how far back the copy goes, or how far behind live the picture is.
    val copyNow = shiftHolder[0]
    val backStatus = when {
        !shifting || copyNow == null -> ""
        isBehind -> (if (behindMs < 60_000) (behindMs / 1000).toString() + " s" else (behindMs / 60_000).toString() + " min") + " behind live"
        else -> { tick; "Can go back " + ((System.currentTimeMillis() - copyNow.oldestTime()) / 60_000) + " min" }
    }
    // The Options panel's rows: the sound, the picture's shape, the sound sync.
    val soundNow = sounds.indexOfFirst { it.selected }
    val optionRows = listOf(
        "Sound: " + when {
            sounds.isEmpty() -> "not known yet"
            sounds.size == 1 -> sounds[0].label + " (the only one)"
            else -> sounds.getOrNull(soundNow)?.label.orEmpty().ifEmpty { "first choice" } + " · " + (soundNow.coerceAtLeast(0) + 1) + " of " + sounds.size
        },
        "Picture shape: " + PictureShape.names[shape],
        "Sound sync: " + SoundSync.words(syncMs),
        "Play in another player",
        "Sleep timer: " + SleepTimer.words(),
        "Picture: " + if (soundOnly) "off, sound only" else "on",
        "Play with VLC, inside KueTV",
    )
    val optionHints = listOf(
        if (phone) "Tap for the next sound" else "OK for the next sound",
        if (phone) "Tap for the next shape: Fit, Stretch, Zoom" else "OK for the next shape: Fit, Stretch, Zoom",
        if (phone) "− sound earlier · + sound later · tap the words for on time" else "◀ sound earlier · ▶ sound later · OK back to on time",
        "VLC, MX Player or another video app, if one is on this device",
        if (phone) "Tap for 15 min, 30 min … 2 hours, then off" else "OK for 15 min, 30 min … 2 hours, then off",
        "Off keeps the sound only, for a commentary or the news",
        "For a stream that won't play here",
    )
    val nextSound: () -> Unit = {
        if (sounds.size > 1) {
            val n = sounds[(soundNow.coerceAtLeast(0) + 1) % sounds.size]
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(n.group, n.track)).build()
        }
    }
    val setSync: (Int) -> Unit = { ms ->
        syncMs = ms.coerceIn(-SoundSync.MOST_MS, SoundSync.MOST_MS)
        SoundSync.offsetUs = syncMs * 1_000L
        items.getOrNull(index)?.let { SoundSync.save(context, it.key, syncMs) }
    }
    val optionAct: (Int) -> Unit = { row ->
        when (row) {
            0 -> { nextSound(); sounds.getOrNull((soundNow.coerceAtLeast(0) + 1) % sounds.size.coerceAtLeast(1))?.let { SoundLanguage.set(context, it.language) } }
            1 -> { shape = (shape + 1) % PictureShape.names.size; PictureShape.save(context, shape) }
            2 -> setSync(0)
            3 -> {
                val item = items.getOrNull(index)
                val url = streamsAt(index).getOrNull(stream)?.url ?: item?.url.orEmpty()
                // It plays there instead, so KueTV lets go of the provider's connection.
                if (item != null && openElsewhere(context, url, item.name)) onClose()
                else say(if (url.startsWith("magnet:")) "A torrent can't be played in another player." else "There is no other video player on this device. VLC or MX Player can be installed from the app store.")
                options = false
            }
            4 -> SleepTimer.next()
            5 -> soundOnly = !soundOnly
            6 -> if (onVlc != null) { keepPlace(); onVlc(items.toList(), index) } else say("VLC isn't available here.")
        }
    }
    // OK on a row of the list over the picture: a list's row shows that list; a channel's row watches it.
    val pickInList: (Int) -> Unit = { row ->
        if (row < headerRows) {
            shownList = row - 1
            listPick = headerRows + if (shownList < 0) index else 0
        } else {
            val i = row - headerRows
            if (shownList < 0) {
                if (i == index) channelList = false else go(i)
            } else {
                // Another list: it becomes the one ▲ ▼ move along.
                val chosen = shownItems
                if (i in chosen.indices) {
                    items.clear()
                    items.addAll(chosen)
                    previous = -1
                    shownList = -1
                    if (i == index) {
                        // The same place in the new list: the new channel starts there, and its name shows.
                        channelList = false
                        shownFor[0] = -1
                        stream = Settled.startFor(items[i])
                        reload += 1
                    } else {
                        go(i)
                    }
                }
            }
        }
    }
    val openList: () -> Unit = {
        channelList = true
        shownList = -1
        listPick = headerRows + index
        controls = false
        nameVisible = false
    }
    // Live: the Live TV list itself, on the channel playing, when there is one to go to; else the list over the picture.
    val toChannels: () -> Unit = {
        val here = items.getOrNull(index)
        if (onChannels != null && here != null) onChannels(here) else openList()
    }
    val openOptions: () -> Unit = {
        options = true
        optionRow = 0
        controls = false
        nameVisible = false
    }
    // The buttons along the bottom of the box, left to right.
    val buttons = buildList {
        if (live) {
            if (shifting) add(Control("Back 30 s", Icon.BACK, seek = -1, status = backStatus) { windBy(-windStep) })
            add(Control(if (paused) "Play" else "Pause", if (paused) Icon.PLAY else Icon.PAUSE, status = if (paused) "Paused" else "", act = togglePause))
            if (shifting && isBehind) add(Control("On 30 s", Icon.ON, seek = 1) { windBy(windStep) })
            if (fromStart != null) add(Control("From the start", Icon.FROM_START, act = fromStart))
            add(Control(if (recording) "Stop recording" else "Record", if (recording) Icon.STOP else Icon.RECORD, status = if (recording) "● Recording" else "", act = toggleRecording))
            pictureButton?.let { add(it) }
            add(subsButton)
            add(Control("Options", Icon.OPTIONS, act = openOptions))
            if (items.size > 1 || onChannels != null) add(Control("Channels", Icon.LIST, act = toChannels))
            // This channel in its own app's live player (ITVX for ITV1, Channel 4's app for Channel 4…), as Freely does.
            items.getOrNull(index)?.let { LiveInApps.forChannel(it.name) }?.let { ch ->
                add(Control("In " + ch.app, Icon.PLAY) { LiveInApps.open(context, ch) })
            }
            if (previous in items.indices) add(Control("Last channel", Icon.PREVIOUS) { go(previous) })
            if (items.size > 1 && onMultiview != null) add(Control("Multiview", Icon.MULTI) { onMultiview(items.toList(), index) })
            // Always last: LIVE, drawn on the right of the box. Behind live, it jumps to what is on now.
            add(Control(if (isBehind || paused) "GO LIVE" else "● LIVE", Icon.LIVE) { if (isBehind || paused) { player.play(); reload += 1 } })
        } else {
            add(Control("Start again", Icon.FROM_START) { player.seekTo(0); showName += 1 })
            add(Control("Back 10 s", Icon.BACK, seek = -1) { windBy(-windStep) })
            add(Control(if (paused) "Play" else "Pause", if (paused) Icon.PLAY else Icon.PAUSE, status = if (paused) "Paused" else "", act = togglePause))
            add(Control("On 10 s", Icon.ON, seek = 1) { windBy(windStep) })
            pictureButton?.let { add(it) }
            add(subsButton)
            add(Control("Options", Icon.OPTIONS, act = openOptions))
        }
    }
    val pickPlay: () -> Int = { buttons.indexOfFirst { it.icon == Icon.PLAY || it.icon == Icon.PAUSE }.coerceAtLeast(0) }

    // Back hides the buttons first; with them gone, Back closes the player.
    BackHandler {
        // The next episode counting down, or the Options panel: Back puts that away and stays here.
        // With the box up (the buttons or not), Back only puts it away. With it gone, Back closes the player.
        if (upNext >= 0) {
            upNext = -1
        } else if (channelList) {
            channelList = false
        } else if (options) {
            options = false
        } else if (nameVisible) {
            controls = false
            nameVisible = false
        } else {
            onClose()
        }
    }

    // KueTV has left the screen (Home was pressed, or another app opened): stop the picture and sound,
    // unless a recording is being made from it. Back on screen: carry on, a live channel from now.
    val leftScreen = remember { booleanArrayOf(false) }
    val visible = Screen.visible
    LaunchedEffect(visible) {
        if (!visible && !recording) {
            leftScreen[0] = true
            player.pause()
            // Away from KueTV, the copy stops too, so nothing holds the connection to the provider.
            shiftHolder[0]?.close()
            shiftHolder[0] = null
        } else if (visible && leftScreen[0]) {
            leftScreen[0] = false
            if (live && shiftWanted) reload += 1
            else if (live) { player.seekToDefaultPosition(); player.prepare() }
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
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                Modifier
                    .testTag("player")
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                        // The next episode counting down: OK starts it now.
                        if (upNext >= 0 && ok) {
                            if (e.type == KeyEventType.KeyDown) go(upNext)
                            return@onPreviewKeyEvent true
                        }
                        // The Options panel: ▲ ▼ move along its rows, OK changes the row, ◀ ▶ move the sound sync.
                        if (options) {
                            val dpad = ok || e.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
                            if (!dpad) return@onPreviewKeyEvent false
                            if (e.type == KeyEventType.KeyDown) {
                                when (e.key) {
                                    Key.DirectionUp -> optionRow = (optionRow - 1).coerceAtLeast(0)
                                    Key.DirectionDown -> optionRow = (optionRow + 1).coerceAtMost(optionRows.size - 1)
                                    Key.DirectionLeft, Key.DirectionRight ->
                                        if (optionRow == 2) setSync(syncMs + if (e.key == Key.DirectionRight) SoundSync.STEP_MS else -SoundSync.STEP_MS)
                                    else -> optionAct(optionRow)
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        // The remote's number keys: a channel by its number in the list, live only.
                        val digit = digitOf(e.nativeKeyEvent.keyCode)
                        if (live && digit >= 0) {
                            if (e.type == KeyEventType.KeyDown && typed.length < 4) typed += digit
                            return@onPreviewKeyEvent true
                        }
                        // The list of channels: ▲ ▼ along it, CH+ CH− a page at a time, OK watches, ▶ puts it away.
                        if (channelList) {
                            val moves = listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.ChannelUp, Key.ChannelDown)
                            if (!ok && e.key !in moves) return@onPreviewKeyEvent false
                            if (e.type == KeyEventType.KeyDown) {
                                val last = headerRows + shownItems.size - 1
                                when (e.key) {
                                    Key.DirectionUp -> listPick = (listPick - 1).coerceAtLeast(0)
                                    Key.DirectionDown -> listPick = (listPick + 1).coerceAtMost(last)
                                    Key.ChannelUp -> listPick = (listPick - LIST_PAGE).coerceAtLeast(0)
                                    Key.ChannelDown -> listPick = (listPick + LIST_PAGE).coerceAtMost(last)
                                    Key.DirectionRight -> channelList = false
                                    Key.DirectionLeft -> {}
                                    else -> pickInList(listPick)
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        val buttonsUp = controls && nameVisible && buttons.isNotEmpty()
                        // Winding: the rewind and fast-forward keys, OK on a wind button, and for a film left and right.
                        // A press winds one step when it is let go; held down, it winds faster and faster until let go.
                        val windDir = when {
                            !canWind -> 0
                            e.key == Key.MediaRewind -> -1
                            e.key == Key.MediaFastForward -> 1
                            buttonsUp && e.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> buttons[pick.coerceIn(0, buttons.size - 1)].seek
                            !live && !buttonsUp && e.key == Key.DirectionLeft -> -1
                            !live && !buttonsUp && e.key == Key.DirectionRight -> 1
                            else -> 0
                        }
                        if (windDir != 0 || (e.type == KeyEventType.KeyUp && heldKey[0] == e.key)) {
                            if (e.type == KeyEventType.KeyDown) {
                                val repeats = e.nativeKeyEvent.repeatCount
                                if (repeats == 0) {
                                    heldKey[0] = e.key
                                    held = 0L
                                    heldSpeed = 1
                                } else if (heldKey[0] == e.key) {
                                    heldSpeed = windSpeed(repeats)
                                    held += windDir * (windStep / 4) * heldSpeed
                                    showName += 1
                                }
                            } else if (e.type == KeyEventType.KeyUp && heldKey[0] == e.key) {
                                val dir = if (windDir != 0) windDir else if (held < 0) -1 else 1
                                windBy(if (held == 0L) dir * windStep else held)
                                heldKey[0] = null
                                held = 0L
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            // Live: up and down change channel. Anything else: they bring up the buttons.
                            Key.DirectionUp, Key.ChannelUp, Key.DirectionDown, Key.ChannelDown -> {
                                if (live) {
                                    if (items.size > 1) go(if (e.key == Key.DirectionUp || e.key == Key.ChannelUp) (index + 1) % items.size else (index - 1 + items.size) % items.size)
                                } else {
                                    if (!buttonsUp) { controls = true; pick = pickPlay() }
                                    showName += 1
                                }
                                true
                            }
                            // OK brings up the buttons; with them up, OK presses the one the remote is on.
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (buttonsUp) {
                                    buttons[pick.coerceIn(0, buttons.size - 1)].act()
                                } else {
                                    controls = true
                                    // Start on Pause, whichever buttons come before it.
                                    pick = pickPlay()
                                }
                                // Options opens its own panel, in place of the box.
                                if (!options) showName += 1
                                true
                            }
                            Key.Menu, Key.MediaRecord -> { if (live) toggleRecording(); live }
                            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePause(); showName += 1; true }
                            // Rewind on a live channel with no copy kept: the programme from its start, through catch-up.
                            Key.MediaRewind -> { fromStart?.invoke(); true }
                            Key.MediaFastForward -> true
                            Key.MediaPrevious -> { if (live) fromStart?.invoke() else { player.seekTo(0); showName += 1 }; true }
                            // With the buttons up, left and right move along them.
                            Key.DirectionLeft, Key.DirectionRight -> if (buttonsUp) {
                                pick = (pick.coerceIn(0, buttons.size - 1) + if (e.key == Key.DirectionLeft) -1 else 1).coerceIn(0, buttons.size - 1)
                                showName += 1
                                true
                            } else {
                                // Live: ◀ goes to the Live TV list, on this channel; ▶ does nothing here.
                                if (live && e.key == Key.DirectionLeft && (items.size > 1 || onChannels != null)) toChannels()
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
                    // KueTV's own box and buttons, for everything it plays.
                    useController = false
                    isFocusable = false
                    // Nothing inside the picture may take the remote from KueTV's own keys.
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    // A film's bar has a subtitles button, to pick a language or turn them off.
                    setShowSubtitleButton(true)
                    // Subtitles: large white letters on a dark band, the same on every channel and film.
                    // Subtitles: as chosen in Settings › Watching (large white letters on a dark band to begin with).
                    subtitleView?.let { SubtitleLook.apply(ctx, it) }
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.resizeMode = PictureShape.mode(shape) },
        )
        // Sound only: the picture is off.
        if (soundOnly) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Sound only  ·  Options › Picture to bring it back", color = Color(0xFFB9C2D3), fontSize = 20.sp)
            }
        }
        if (phone && !live) {
            // A tap on the picture brings up the box and its buttons.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { if (nameVisible) { nameVisible = false; controls = false } else { controls = true; showName += 1 } }) })
        }
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
                    // A tap brings the box up, or puts it away when it is up.
                    .pointerInput(Unit) { detectTapGestures(onTap = { if (nameVisible) { nameVisible = false; controls = false } else showName += 1 }) },
            )
        }
        // In the small picture-in-picture window: the picture only.
        if (!Pip.inPip) {
            val title = items.getOrNull(index)?.name.orEmpty()
            if (nameVisible || note != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(if (phone) 16.dp else 36.dp)
                        .fillMaxWidth()
                        .background(Color(0xE60E1626), RoundedCornerShape(14.dp))
                        // Phone: a tap on the box (not on a button) puts it away.
                        .then(if (phone) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { nameVisible = false; controls = false }) } else Modifier)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
                    val soft = Color(0xFFB9C2D3)
                    // The name, with the time on the right.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text("   " + clock.format(java.util.Date(clockNow)), color = soft, fontSize = 22.sp, maxLines = 1)
                        // Catch-up: the time it was on air at this point in the programme.
                        items.getOrNull(index)?.airedFromMs?.let { aired ->
                            Text("  ·  aired " + clock.format(java.util.Date(aired + player.currentPosition)), color = soft, fontSize = 18.sp, maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        // The keys, small, on a TV while the buttons aren't in use.
                        if (!phone && !controls) {
                            Text(
                                if (live) "OK buttons  ·  ▲ ▼ channel" + (if (items.size > 1 || onChannels != null) "  ·  ◀ channels" else "") + "  ·  ≡ record"
                                else "OK buttons  ·  ◀ ▶ wind, hold to go faster",
                                color = soft, fontSize = 14.sp, maxLines = 1,
                            )
                        }
                    }
                    // TV: what is on now and what is on next side by side, so the box is less tall. A long title
                    // scrolls along, and a long description scrolls up through three lines by itself.
                    if (!phone && (onNow != null || nextUp != null)) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            listOf("Now" to onNow, "Next" to nextUp).forEach { (word, p) ->
                                Column(Modifier.weight(1f)) {
                                    if (p != null) {
                                        Text(
                                            word + " · " + clock.format(java.util.Date(p.startMs)) + " to " + clock.format(java.util.Date(p.stopMs)),
                                            color = Color(0xFFFFD23F), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                        )
                                        Text(
                                            p.title.ifBlank { "No title" }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
                                        )
                                        if (p.description.isNotBlank()) AutoScrollText(p.description, soft, 17.sp, 3)
                                    }
                                }
                            }
                        }
                    }
                    if (phone && onNow != null) {
                        Text(
                            "Now · " + clock.format(java.util.Date(onNow.startMs)) + " to " + clock.format(java.util.Date(onNow.stopMs)),
                            color = Color(0xFFFFD23F), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(onNow.title.ifBlank { "No title" }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        // The whole description, not cut short.
                        // TV: the whole description. Phone: three lines, so the box doesn't fill the screen.
                        if (onNow.description.isNotBlank()) Text(onNow.description, color = soft, fontSize = 18.sp, maxLines = if (phone) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (phone && nextUp != null) {
                        Text(
                            "Next · " + clock.format(java.util.Date(nextUp.startMs)) + " · " + nextUp.title.ifBlank { "No title" },
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
                        )
                        // A line or two about what is on next.
                        if (nextUp.description.isNotBlank()) Text(nextUp.description, color = soft, fontSize = 16.sp, maxLines = if (phone) 1 else 2, overflow = TextOverflow.Ellipsis)
                    }
                    // A film, a recording or catch-up: how far through, along a bar. On a phone the bar can be tapped or dragged.
                    if (!live) {
                        tick
                        val length = player.duration.takeIf { it > 0 }
                        val at = (player.currentPosition + held).coerceAtLeast(0L).let { if (length != null) it.coerceAtMost(length) else it }
                        val clockOf: (Long) -> String = { ms ->
                            val sec = ms / 1000
                            if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(clockOf(at), color = Color.White, fontSize = 16.sp)
                            Box(
                                Modifier.weight(1f).padding(horizontal = 12.dp).height(if (phone) 22.dp else 8.dp)
                                    .then(
                                        if (phone && length != null) Modifier.pointerInput(length) {
                                            detectTapGestures { p -> player.seekTo((length * (p.x / size.width)).toLong()); showName += 1 }
                                        } else Modifier,
                                    ),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0x44FFFFFF), RoundedCornerShape(4.dp)))
                                if (length != null) Box(Modifier.fillMaxWidth((at.toFloat() / length).coerceIn(0f, 1f)).height(8.dp).background(Color(0xFFFFD23F), RoundedCornerShape(4.dp)))
                            }
                            Text(if (length != null) "-" + clockOf((length - at).coerceAtLeast(0)) else "", color = soft, fontSize = 16.sp)
                        }
                    }
                    moved?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp)) }
                    note?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp)) }
                    // The bottom of the box, across its whole width: the buttons, each with how things stand for it
                    // written just above it, and the LIVE button on the right. On a TV the remote moves onto them with OK.
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Row(
                            Modifier.weight(1f).then(if (phone) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                            horizontalArrangement = Arrangement.spacedBy(if (phone) 14.dp else 6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            buttons.forEachIndexed { i, b ->
                                if (b.icon == Icon.LIVE) return@forEachIndexed
                                val on = !phone && controls && i == pick.coerceIn(0, buttons.size - 1)
                                val touch = if (!phone) Modifier else Modifier.pointerInput(b.label) {
                                    if (b.seek == 0) {
                                        detectTapGestures(onTap = { b.act(); if (!options) showName += 1 })
                                    } else {
                                        detectTapGestures(onPress = {
                                            // Held: wind faster and faster until let go. A quick tap winds one step.
                                            held = 0L
                                            val winding = windScope.launch {
                                                delay(400)
                                                var n = 0
                                                while (true) {
                                                    heldSpeed = windSpeed(n)
                                                    held += b.seek * (windStep / 4) * heldSpeed
                                                    showName += 1
                                                    n++
                                                    delay(50)
                                                }
                                            }
                                            tryAwaitRelease()
                                            winding.cancel()
                                            if (held == 0L) b.act() else windBy(held)
                                            held = 0L
                                        })
                                    }
                                }
                                // Winding with this button held: how far, and how fast, in place of its usual note.
                                val winding = held != 0L && b.seek != 0 && (held < 0) == (b.seek < 0)
                                val status = if (winding) windWords(held) + if (heldSpeed > 1) " ×$heldSpeed" else "" else b.status
                                Column(
                                    (if (phone) Modifier.width(84.dp) else Modifier.weight(1f)).then(touch),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Kept the same height on every button, so the pictures line up.
                                    Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.BottomCenter) {
                                        if (status.isNotEmpty()) {
                                            Text(
                                                status, color = if (winding) Color(0xFFFFD23F) else if (b.icon == Icon.STOP) Color(0xFFFF6B5E) else soft,
                                                fontSize = 12.sp, fontWeight = if (winding) FontWeight.Bold else FontWeight.Normal, lineHeight = 14.sp,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                    Box(
                                        Modifier.padding(top = 4.dp).size(if (phone) 50.dp else 52.dp).background(if (on) Color(0xFFFFD23F) else Color(0xFF16213A), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { ButtonIcon(b, if (on) Color.Black else Color.White) }
                                    Text(
                                        b.label, color = if (on) Color(0xFFFFD23F) else Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        // LIVE: red while at live; behind live it says GO LIVE, and choosing it jumps to what is on now.
                        val liveControl = buttons.lastOrNull { it.icon == Icon.LIVE }
                        if (liveControl != null) {
                            Spacer(Modifier.width(16.dp))
                            val atLive = !(isBehind || paused)
                            val liveOn = !phone && controls && pick.coerceIn(0, buttons.size - 1) == buttons.indexOf(liveControl)
                            Text(
                                liveControl.label,
                                color = if (liveOn) Color.Black else Color.White,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                modifier = Modifier
                                    .padding(bottom = 22.dp)
                                    .background(
                                        when { liveOn -> Color(0xFFFFD23F); atLive -> Color(0xFFD32F2F); else -> Color(0xFF3A4660) },
                                        RoundedCornerShape(10.dp),
                                    )
                                    .then(if (phone) Modifier.clickable { liveControl.act(); showName += 1 } else Modifier)
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (phone && live && items.size > 1) Text("Swipe up or down to change channel", color = soft, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            // A channel number being typed, large, in the top right corner.
            if (typed.isNotEmpty()) {
                Text(
                    typed, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(if (phone) 16.dp else 36.dp)
                        .background(Color(0xE60E1626), RoundedCornerShape(14.dp)).padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            // The list of channels, down the left of the picture.
            if (channelList) {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .padding(if (phone) 12.dp else 30.dp)
                        .width(if (phone) 300.dp else 440.dp)
                        .background(Color(0xF00E1626), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)) {
                        Text("Channels", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (phone) PlayerChip("Close") { channelList = false }
                    }
                    if (!phone) Text("▲ ▼ move  ·  CH+ CH− a page  ·  OK watch  ·  ▶ close", color = Color(0xFFB9C2D3), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                        // The lists to switch to.
                        this.items(headerRows) { h ->
                            val on = !phone && h == listPick
                            val name = if (h == 0) "This list" else lists[h - 1].first
                            Text(
                                (if (shownList == h - 1) "● " else "") + name, color = if (on) Color.Black else Color(0xFFFFD23F), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .background(if (on) Color(0xFFFFD23F) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .then(if (phone) Modifier.clickable { pickInList(h) } else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        this.items(shownItems.size) { i ->
                            val on = !phone && headerRows + i == listPick
                            val playing = shownList < 0 && i == index
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(if (on) Color(0xFFFFD23F) else if (playing) Color(0xFF263A63) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .then(if (phone) Modifier.clickable { pickInList(headerRows + i) } else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text((i + 1).toString(), color = if (on) Color.Black else Color(0xFFB9C2D3), fontSize = 16.sp, modifier = Modifier.width(52.dp))
                                Text(
                                    (if (playing) "▶ " else "") + shownItems[i].name, color = if (on) Color.Black else Color.White, fontSize = 18.sp,
                                    fontWeight = if (on || playing) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            // The next episode or programme, counting down in the top right corner.
            if (upNext >= 0) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (phone) 16.dp else 36.dp)
                        .widthIn(max = 460.dp)
                        .background(Color(0xF00E1626), RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text("Next", color = Color(0xFFFFD23F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(items.getOrNull(upNext)?.name.orEmpty(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Starts in $upNextIn s", color = Color(0xFFB9C2D3), fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    if (phone) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlayerChip("Play now", main = true) { go(upNext) }
                            PlayerChip("Stay here") { upNext = -1 }
                        }
                    } else {
                        Text("OK play now  ·  BACK stay here", color = Color(0xFFB9C2D3), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            // Options: the sound, the picture's shape and the sound sync, on the right of the screen.
            if (options) {
                Column(
                    Modifier
                        .align(if (phone) Alignment.BottomCenter else Alignment.CenterEnd)
                        .padding(if (phone) 16.dp else 36.dp)
                        .then(if (phone) Modifier.fillMaxWidth() else Modifier.width(480.dp))
                        .background(Color(0xF00E1626), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Options", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                    optionRows.forEachIndexed { i, words ->
                        val on = !phone && i == optionRow
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .testTag(if (on) "option on" else "option")
                                .background(if (on) Color(0xFFFFD23F) else Color(0xFF16213A), RoundedCornerShape(10.dp))
                                .then(if (phone) Modifier.clickable { optionAct(i) } else Modifier)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(words, color = if (on) Color.Black else Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (phone && i == 2) {
                                    PlayerChip("−") { setSync(syncMs - SoundSync.STEP_MS) }
                                    Spacer(Modifier.width(8.dp))
                                    PlayerChip("+") { setSync(syncMs + SoundSync.STEP_MS) }
                                }
                            }
                            Text(optionHints[i], color = if (on) Color.Black else Color(0xFFB9C2D3), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!phone) Text("BACK close", color = Color(0xFFB9C2D3), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    else PlayerChip("Close") { options = false }
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
}

/**
 * The small TV: a live channel playing in a corner while the lists are browsed. It has its own simple
 * player, which is closed when the full-screen one opens. It stops when KueTV leaves the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniTv(item: Playable, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val address = resolvedAddress(item.url) ?: return
    var retry by remember(address) { mutableIntStateOf(0) }
    val player = remember(address) { lightPlayer(context, address) }
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
    // The remote can move onto it: OK plays the channel full screen, and Back comes back here.
    val link = LocalSmallTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .width(380.dp)
            .then(if (link.back != null) Modifier.focusRequester(link.back) else Modifier)
            .background(if (focused) Looks.accentColour else Color(0xFF16213A), shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (link.open != null) Modifier.clickable { link.open.invoke() } else Modifier)
            .padding(6.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            factory = { ctx -> PlayerView(ctx).apply { useController = false; isFocusable = false } },
            update = { it.player = player },
        )
        Text(
            if (focused) "OK: full screen" else item.name,
            color = if (focused) Color.Black else Color.White, fontSize = 17.sp, maxLines = 1,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** A small label in the live name box: the version playing, subtitles, paused, recording. */
@Composable
private fun Label(text: String, background: Color = Color(0xFF16213A), color: Color = Color(0xFFB9C2D3)) {
    Text(
        text, color = color, fontSize = 15.sp, maxLines = 1,
        modifier = Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The picture on one of the player's buttons, drawn plainly so it reads from across the room. */
@Composable
private fun ButtonIcon(b: Control, color: Color) {
    when (b.icon) {
        Icon.PICTURE, Icon.SUBS_ON, Icon.SUBS_OFF -> {
            // A word in a box: the picture's quality, or CC for subtitles (crossed through when off).
            val word = if (b.icon == Icon.PICTURE) b.badge.take(4).ifBlank { "HD" } else "CC"
            Box(contentAlignment = Alignment.Center) {
                Text(
                    word, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.border(2.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
                if (b.icon == Icon.SUBS_OFF) {
                    Canvas(Modifier.size(34.dp, 22.dp)) {
                        drawLine(color, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
        else -> Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            fun triangle(left: Float, right: Float, pointRight: Boolean) {
                val path = Path().apply {
                    if (pointRight) { moveTo(left, h * 0.18f); lineTo(right, h * 0.5f); lineTo(left, h * 0.82f) }
                    else { moveTo(right, h * 0.18f); lineTo(left, h * 0.5f); lineTo(right, h * 0.82f) }
                    close()
                }
                drawPath(path, color)
            }
            when (b.icon) {
                Icon.PLAY -> triangle(w * 0.24f, w * 0.86f, true)
                Icon.PAUSE -> {
                    drawRect(color, Offset(w * 0.22f, h * 0.18f), Size(w * 0.2f, h * 0.64f))
                    drawRect(color, Offset(w * 0.58f, h * 0.18f), Size(w * 0.2f, h * 0.64f))
                }
                Icon.BACK -> { triangle(w * 0.02f, w * 0.5f, false); triangle(w * 0.48f, w * 0.96f, false) }
                Icon.ON -> { triangle(w * 0.04f, w * 0.52f, true); triangle(w * 0.5f, w * 0.98f, true) }
                Icon.FROM_START -> {
                    drawRect(color, Offset(w * 0.14f, h * 0.18f), Size(w * 0.13f, h * 0.64f))
                    triangle(w * 0.3f, w * 0.86f, false)
                }
                Icon.RECORD -> drawCircle(Color(0xFFE53935), radius = w * 0.34f)
                Icon.STOP -> drawRect(Color(0xFFE53935), Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f))
                Icon.OPTIONS -> for (x in listOf(0.18f, 0.5f, 0.82f)) drawCircle(color, radius = w * 0.1f, center = Offset(w * x, h * 0.5f))
                Icon.LIST -> for (y in listOf(0.22f, 0.5f, 0.78f)) drawRect(color, Offset(w * 0.12f, h * y - h * 0.06f), Size(w * 0.76f, h * 0.12f))
                Icon.PREVIOUS -> {
                    val p = Path().apply { moveTo(w * 0.08f, h * 0.5f); lineTo(w * 0.46f, h * 0.2f); lineTo(w * 0.46f, h * 0.8f); close() }
                    val q = Path().apply { moveTo(w * 0.48f, h * 0.5f); lineTo(w * 0.86f, h * 0.2f); lineTo(w * 0.86f, h * 0.8f); close() }
                    drawPath(p, color)
                    drawPath(q, color)
                }
                Icon.MULTI -> for (x in listOf(0.1f, 0.54f)) for (y in listOf(0.14f, 0.54f)) drawRect(color, Offset(w * x, h * y), Size(w * 0.36f, h * 0.32f))
                else -> {}
            }
        }
    }
}

/** How long the next episode or programme waits before it starts. */
private const val UP_NEXT_SECONDS = 10

/** A small button on the phone, in the player's boxes. */
@Composable
private fun PlayerChip(text: String, main: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (main) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier
            .background(if (main) Color(0xFFFFD23F) else Color(0xFF3A4660), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** How long a live channel is watched before it goes on "Recently watched". */
private const val RECENT_AFTER_MS = 10_000L

/** How long after the last number key the channel typed is gone to. */
private const val TYPED_WAIT_MS = 1_500L

/** How many rows CH+ and CH− move the list of channels by. */
private const val LIST_PAGE = 8

/** Picture-in-picture: whether leaving KueTV now should shrink what is playing to a corner, and whether it has. */
object Pip {
    @Volatile var wanted = false
    var inPip by mutableStateOf(false)

    private fun prefs(context: android.content.Context) = context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE)
    fun on(context: android.content.Context): Boolean = prefs(context).getBoolean("pip", true)
    fun on(context: android.content.Context, value: Boolean) {
        prefs(context).edit().putBoolean("pip", value).apply()
        Choices.touch()
    }
}

/**
 * Words in at most [lines] lines. When there are more, they scroll up slowly by themselves, wait at the
 * end, and start again from the top.
 */
@Composable
private fun AutoScrollText(text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, lines: Int) {
    val scroll = rememberScrollState()
    val lineHeight = fontSize * 1.3f
    val most = with(androidx.compose.ui.platform.LocalDensity.current) { lineHeight.toDp() } * lines
    Box(Modifier.heightIn(max = most).verticalScroll(scroll, enabled = false)) {
        Text(text, color = color, fontSize = fontSize, lineHeight = lineHeight)
    }
    LaunchedEffect(text, scroll.maxValue) {
        scroll.scrollTo(0)
        if (scroll.maxValue <= 0) return@LaunchedEffect
        while (true) {
            delay(3_000)
            scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(durationMillis = (scroll.maxValue * 30).coerceAtLeast(1_500), easing = androidx.compose.animation.core.LinearEasing))
            delay(3_000)
            scroll.scrollTo(0)
        }
    }
}
