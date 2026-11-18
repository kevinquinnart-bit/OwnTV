package art.kquinn.kuetv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** How many channels Multiview shows at once. */
const val MULTI_SQUARES = 4

/** The rows at the top of Multiview's channel list, before the channels themselves. */
private const val LIST_FULL = -2
private const val LIST_EMPTY = -1

/**
 * Multiview: up to four live channels at once, in squares. The square the remote is on has the sound. OK on a square
 * opens the list of channels to put one there, to watch it full screen, or to empty the square. Back leaves.
 * Each square plays a channel's lightest version, on a connection of its own to the provider.
 */
@Composable
fun MultiviewScreen(items: List<Playable>, first: Int, onClose: () -> Unit, onWatch: (Int) -> Unit) {
    val phone = LocalPhone.current
    val squares = remember { mutableStateListOf(first, -1, -1, -1) }
    var on by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var pickRow by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The list's rows: watch full screen and empty (when the square has a channel), then every channel.
    val extraRows = if (squares[on] >= 0) listOf(LIST_FULL, LIST_EMPTY) else emptyList()
    val rows = extraRows + items.indices.toList()
    val openList: () -> Unit = {
        picking = true
        pickRow = rows.indexOf(squares[on]).coerceAtLeast(0)
    }
    val choose: (Int) -> Unit = { row ->
        when (val r = rows.getOrNull(row)) {
            null -> Unit
            LIST_FULL -> onWatch(squares[on])
            LIST_EMPTY -> squares[on] = -1
            else -> squares[on] = r
        }
        picking = false
    }
    LaunchedEffect(picking, pickRow) {
        if (!picking) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || pickRow <= visible.first().index || pickRow >= visible.last().index) listState.scrollToItem((pickRow - 3).coerceAtLeast(0))
    }
    BackHandler { if (picking) picking = false else onClose() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("multiview")
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                val moves = listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.ChannelUp, Key.ChannelDown)
                if (!ok && e.key !in moves) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (picking) {
                    when (e.key) {
                        Key.DirectionUp -> pickRow = (pickRow - 1).coerceAtLeast(0)
                        Key.DirectionDown -> pickRow = (pickRow + 1).coerceAtMost(rows.size - 1)
                        Key.ChannelUp -> pickRow = (pickRow - 8).coerceAtLeast(0)
                        Key.ChannelDown -> pickRow = (pickRow + 8).coerceAtMost(rows.size - 1)
                        Key.DirectionRight -> picking = false
                        Key.DirectionLeft -> {}
                        else -> choose(pickRow)
                    }
                } else {
                    // The squares: 0 1 on top, 2 3 underneath.
                    when (e.key) {
                        Key.DirectionLeft -> if (on % 2 == 1) on -= 1
                        Key.DirectionRight -> if (on % 2 == 0) on += 1
                        Key.DirectionUp -> if (on >= 2) on -= 2
                        Key.DirectionDown -> if (on < 2) on += 2
                        Key.ChannelUp, Key.ChannelDown -> {}
                        else -> openList()
                    }
                }
                true
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 2) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0 until 2) {
                        val i = row * 2 + col
                        val channel = squares[i]
                        Box(
                            Modifier.weight(1f).fillMaxHeight().padding(3.dp)
                                .border(3.dp, if (i == on) Color(0xFFFFD23F) else Color(0xFF16213A), RoundedCornerShape(6.dp))
                                .padding(3.dp)
                                .then(if (phone) Modifier.clickable { if (on == i) openList() else on = i } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val item = items.getOrNull(channel)
                            if (item != null) {
                                Square(item, sound = i == on)
                            } else {
                                Text(if (phone) "Tap to add a channel" else "OK to add a channel", color = Color(0xFFB9C2D3), fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
        if (!picking) {
            Text(
                if (phone) "Tap a square to give it the sound, tap again to change its channel" else "◀ ▶ ▲ ▼ square (it has the sound)  ·  OK change its channel  ·  BACK leave Multiview",
                color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0xCC0E1626), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (picking) {
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(24.dp).width(if (phone) 300.dp else 440.dp)
                    .background(Color(0xF00E1626), RoundedCornerShape(14.dp)).padding(10.dp),
            ) {
                Text("Channel for this square", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                Text("Each square is a connection to your provider. Some allow only one or two at once.", color = Color(0xFFB9C2D3), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                    this.items(rows.size) { r ->
                        val what = rows[r]
                        val chosen = !phone && r == pickRow
                        Text(
                            when (what) {
                                LIST_FULL -> "▶ Watch this one full screen"
                                LIST_EMPTY -> "Empty this square"
                                else -> (what + 1).toString() + "   " + items[what].name
                            },
                            color = if (chosen) Color.Black else Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (chosen || what < 0) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .background(if (chosen) Color(0xFFFFD23F) else Color.Transparent, RoundedCornerShape(8.dp))
                                .then(if (phone) Modifier.clickable { choose(r) } else Modifier)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One Multiview square: a channel's lightest version, with its sound on or off. */
@OptIn(UnstableApi::class)
@Composable
private fun Square(item: Playable, sound: Boolean) {
    val context = LocalContext.current
    // Its lightest version, to spare the connection; an add-on's TV channel has no address of its own to play here.
    val url = (item.streams.lastOrNull()?.url ?: item.url).takeIf { AddOns.showable(it) }
    if (url == null) {
        Text(item.name + ": this one can only play full screen", color = Color(0xFFB9C2D3), fontSize = 16.sp, modifier = Modifier.padding(12.dp))
        return
    }
    var retry by remember(url) { mutableIntStateOf(0) }
    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Provider.USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredAudioLanguage("en").build()
                setMediaItem(MediaItem.fromUri(url))
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
    LaunchedEffect(sound) { player.volume = if (sound) 1f else 0f }
    val visible = Screen.visible
    LaunchedEffect(player, visible, retry) {
        if (!visible) { player.stop(); return@LaunchedEffect }
        if (retry > 5) return@LaunchedEffect
        if (retry > 0) delay(3_000)
        player.seekToDefaultPosition()
        player.prepare()
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { useController = false; isFocusable = false; descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS; keepScreenOn = true } },
            update = { it.player = player },
        )
        Text(
            (if (sound) "🔊 " else "") + item.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC0E1626), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
