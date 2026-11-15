package art.kquinn.kuetv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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

/** One thing the player can show: a channel or a film. */
data class Playable(val name: String, val url: String)

private const val MAX_TRIES = 6

/**
 * The full-screen player.
 *
 * Live: ▲ / CH+ is the next channel, ▼ / CH− the one before, OK shows the channel's name, Back closes.
 * A film: the player's own bar handles play, pause and skipping; Back closes.
 *
 * English audio and English subtitles are chosen whenever the stream carries them. When a stream
 * drops, the player reconnects by itself and says so; after [MAX_TRIES] failures in a row it says the
 * stream isn't playing instead of leaving a black screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(items: List<Playable>, startIndex: Int, live: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var note by remember { mutableStateOf<String?>(null) }
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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { tries = 0; note = null }
            }

            override fun onPlayerError(error: PlaybackException) {
                tries += 1
                if (tries >= MAX_TRIES) {
                    note = if (live) "This channel isn't playing right now." else "This film isn't playing right now."
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

    // A new channel (or film): start it clean.
    LaunchedEffect(index) {
        val item = items.getOrNull(index) ?: return@LaunchedEffect
        tries = 0
        note = null
        player.setMediaItem(MediaItem.fromUri(item.url))
        player.prepare()
        showName += 1
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
        delay(4_000)
        nameVisible = false
    }

    BackHandler { onClose() }

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
                            Key.DirectionUp, Key.ChannelUp -> { if (items.size > 1) index = (index + 1) % items.size; true }
                            Key.DirectionDown, Key.ChannelDown -> { if (items.size > 1) index = (index - 1 + items.size) % items.size; true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showName += 1; true }
                            Key.DirectionLeft, Key.DirectionRight -> true
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
        val title = items.getOrNull(index)?.name.orEmpty()
        if ((nameVisible && live) || note != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(36.dp)
                    .background(Color(0xCC0E1626), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                note?.let { Text(it, color = Color(0xFFFFD23F), fontSize = 20.sp) }
            }
        }
    }
}
