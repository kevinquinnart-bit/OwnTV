package art.kquinn.kuetv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import art.kquinn.kuetv.TrText as Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** Which player plays things: KueTV's usual one, or VLC (built in) for streams the usual one can't manage. */
object PlayerEngine {
    val names = listOf("Standard", "VLC")
    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    fun get(context: Context): Int = prefs(context).getInt("engine", 0).coerceIn(0, names.size - 1)
    fun set(context: Context, choice: Int) {
        prefs(context).edit().putInt("engine", choice).apply()
        Choices.touch()
    }

    fun vlc(context: Context): Boolean = get(context) == 1

    /** The addresses VLC can try for [item], best first: its versions, or its own address. */
    fun addresses(item: Playable): List<String> =
        item.streams.map { it.url }.ifEmpty { listOf(item.url) }.filter { it.isNotBlank() && !it.startsWith("magnet:") && !it.startsWith(Playable.NO_ADDRESS) }

    /** "12:05 / 1:45:00": how far in, and how long. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * Plays [items] with VLC. The remote works as in the usual player: up and down change channel, OK shows
 * what is on, and for a film left and right go back 10 seconds and on 30, and Play/Pause pauses. Live TV
 * can't be wound back here; left and right do nothing on it.
 */
@Composable
fun VlcScreen(items: List<Playable>, startIndex: Int, live: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    // VLC's own parts are loaded here; a device it won't run on says so instead.
    val vlc = remember {
        try {
            LibVLC(context.applicationContext, arrayListOf("--network-caching=2000", "--http-reconnect"))
        } catch (e: Throwable) {
            Report.note("VLC", "couldn't start: " + e.javaClass.simpleName)
            null
        }
    }
    if (vlc == null) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onClose() }, contentAlignment = Alignment.Center) {
            Text("VLC couldn't start on this device. Press Back, and set Player to Standard in Settings › Watching.", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(32.dp))
        }
        return
    }
    val player = remember { MediaPlayer(vlc) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var version by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>("Starting in VLC…") }
    var showing by remember { mutableIntStateOf(1) }
    var paused by remember { mutableStateOf(false) }
    var timeMs by remember { mutableLongStateOf(0L) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val keepPlace: () -> Unit = {
        val item = items.getOrNull(index)
        if (!live && item != null && lengthMs > 0 && timeMs > 0) Positions.keep(context, item.key, timeMs, lengthMs, item.name, item.poster)
    }

    DisposableEffect(player) {
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> { note = null; paused = false }
                MediaPlayer.Event.Paused -> paused = true
                MediaPlayer.Event.TimeChanged -> timeMs = e.timeChanged
                MediaPlayer.Event.LengthChanged -> lengthMs = e.lengthChanged
                MediaPlayer.Event.EncounteredError -> failed++
                MediaPlayer.Event.EndReached -> {
                    val item = items.getOrNull(index)
                    if (!live && item != null) {
                        Positions.forget(context, item.key)
                        if (item.poster != null) Watched.set(context, item.key, true)
                        if (index < items.size - 1) { index++; version = 0 }
                    }
                }
            }
        }
        onDispose {
            keepPlace()
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            vlc.release()
        }
    }

    // A version that won't play: the next one down, as the usual player does.
    LaunchedEffect(failed) {
        if (failed == 0) return@LaunchedEffect
        val versions = items.getOrNull(index)?.let { PlayerEngine.addresses(it) }.orEmpty()
        if (version < versions.size - 1) version++ else note = "VLC couldn't play this."
    }

    LaunchedEffect(index, version) {
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        showing++
        val versions = PlayerEngine.addresses(item)
        val raw = versions.getOrNull(version)
        if (raw == null) {
            note = "This one needs the standard player (it comes from an add-on or a torrent)."
            return@LaunchedEffect
        }
        note = "Starting in VLC…"
        val address = if (Stalker.isLink(raw)) {
            withContext(Dispatchers.IO) { runCatching { Stalker.resolve(raw) }.getOrNull() } ?: run {
                note = "The portal didn't give a link for this channel."
                return@LaunchedEffect
            }
        } else raw
        val media = Media(vlc, android.net.Uri.parse(address))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":http-user-agent=" + Provider.USER_AGENT)
        if (!live) {
            val place = Positions.of(context, item.key)
            if (place > 0) media.addOption(":start-time=" + place / 1000)
        }
        player.media = media
        media.release()
        timeMs = 0
        lengthMs = 0
        player.play()
        if (live) Report.note("VLC", "playing " + item.name)
    }

    // What is on shows for a few seconds after each change, or OK.
    var nameUp by remember { mutableStateOf(true) }
    LaunchedEffect(showing) {
        nameUp = true
        delay(5_000)
        nameUp = false
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val go: (Int) -> Unit = { next ->
        keepPlace()
        index = next
        version = 0
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { if (items.size > 1) go((index + 1) % items.size); true }
                    Key.DirectionDown, Key.ChannelDown -> { if (items.size > 1) go((index - 1 + items.size) % items.size); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showing++; true }
                    Key.DirectionLeft -> { if (!live && lengthMs > 0) { player.time = (player.time - 10_000).coerceAtLeast(0); showing++ }; true }
                    Key.DirectionRight -> { if (!live && lengthMs > 0) { player.time = (player.time + 30_000).coerceAtMost(lengthMs - 1_000); showing++ }; true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> {
                        if (player.isPlaying) player.pause() else player.play()
                        showing++
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .clickable { showing++ },
    ) {
        AndroidView(
            factory = { c -> VLCVideoLayout(c).also { layout -> player.attachViews(layout, null, true, false) } },
            modifier = Modifier.fillMaxSize(),
        )
        val item = items.getOrNull(index)
        if (nameUp || note != null || paused) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp)
                    .background(Color(0xCC0E1626), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(item?.name.orEmpty(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val line = when {
                    note != null -> note!!
                    paused -> "Paused · Play/Pause to carry on"
                    !live && lengthMs > 0 -> PlayerEngine.clock(timeMs) + " / " + PlayerEngine.clock(lengthMs) + " · ◀ 10 s back · ▶ 30 s on"
                    else -> "Playing in VLC"
                }
                Text(line, color = Color(0xFFB9C2D3), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
