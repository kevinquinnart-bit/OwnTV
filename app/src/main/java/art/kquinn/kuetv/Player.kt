package art.kquinn.kuetv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** One version of a channel: its quality as the user reads it ("FHD") and its address. */
data class Stream(val label: String, val url: String)

/**
 * One thing the player can show: a channel or a film. A channel the provider lists in several
 * versions carries them in [streams], best picture first, and starts on [startStream].
 */
data class Playable(val name: String, val url: String, val streams: List<Stream> = emptyList(), val startStream: Int = 0)

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

    fun startFor(item: Playable): Int {
        val kept = last[item.url] ?: return item.startStream
        return if (System.currentTimeMillis() - kept.second < SETTLED_MS && kept.first in item.streams.indices) kept.first else item.startStream
    }

    fun played(item: Playable, stream: Int) {
        if (item.streams.size > 1) last[item.url] = stream to System.currentTimeMillis()
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
    var showName by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Provider.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val load = DefaultLoadControl.Builder()
            // Holds more picture in hand than the default before and during play, to ride out short stalls.
            .setBufferDurationsMs(20_000, 60_000, 2_500, 6_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setLoadControl(load)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage("en")
                    .setPreferredTextLanguage("en")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
                playWhenReady = true
            }
    }

    /** Another channel, film or episode: start it on the version it should start on. */
    val go: (Int) -> Unit = { next ->
        index = next
        stream = items.getOrNull(next)?.let { Settled.startFor(it) } ?: 0
        tries = 0
        note = null
        moved = null
    }
    /** Another version of the same channel. [why] is said on screen; null when the user chose it. */
    val change: (Int, String?) -> Unit = { next, why ->
        moved = why
        note = null
        stream = next
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                stopped = state == Player.STATE_BUFFERING
                val item = items.getOrNull(index)
                val versions = item?.streams.orEmpty()
                if (state == Player.STATE_READY) {
                    tries = 0
                    note = null
                    hasPlayed[0] = true
                    if (item != null) Settled.played(item, stream)
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

            override fun onPlayerError(error: PlaybackException) {
                tries += 1
                hasPlayed[0] = false
                val versions = items.getOrNull(index)?.streams.orEmpty()
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
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // A new channel (or film), or another version of this channel: start it clean.
    LaunchedEffect(index, stream) {
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        stops.clear()
        hasPlayed[0] = false
        player.setMediaItem(MediaItem.fromUri(item.streams.getOrNull(stream)?.url ?: item.url))
        player.prepare()
        showName += 1
    }
    // The picture has stayed stopped too long, at the start or part way through: move down a version.
    LaunchedEffect(stopped, index, stream) {
        if (!stopped || !live) return@LaunchedEffect
        delay(LONG_STOP_MS)
        val versions = items.getOrNull(index)?.streams.orEmpty()
        if (stream < versions.size - 1) {
            change(stream + 1, "Kept stopping on " + versions[stream].label + ". Now on " + versions[stream + 1].label + ".")
        }
    }
    // After a drop: wait a moment, then pick the stream up again.
    LaunchedEffect(retryTick) {
        if (retryTick == 0) return@LaunchedEffect
        delay(1_500)
        if (live) player.seekToDefaultPosition()
        player.prepare()
    }
    var nameVisible by remember { mutableStateOf(false) }
    LaunchedEffect(showName) {
        if (showName == 0) return@LaunchedEffect
        nameVisible = true
        delay(if (moved != null) 7_000 else 4_000)
        nameVisible = false
        moved = null
    }

    BackHandler { onClose() }

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
        if ((nameVisible && live) || note != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (phone) 16.dp else 36.dp)
                    .background(Color(0xCC0E1626), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (live && versions.size > 1 && stream in versions.indices) {
                    Text(
                        versions[stream].label + " · version " + (stream + 1) + " of " + versions.size +
                            if (phone) " · swipe sideways to change" else " · ◀ ▶ to change",
                        color = Color(0xFFB9C2D3), fontSize = 18.sp, maxLines = 1,
                    )
                }
                moved?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp) }
                note?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp) }
                if (phone && live && items.size > 1) Text("Swipe up or down to change channel", color = Color(0xFFB9C2D3), fontSize = 16.sp)
            }
        }
    }
}
