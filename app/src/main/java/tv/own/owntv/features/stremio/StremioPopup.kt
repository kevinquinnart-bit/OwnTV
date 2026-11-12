package tv.own.owntv.features.stremio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private enum class Mode { STREAMS, ADD, REMOVE }

/**
 * Kevin's copy: "Add-on streams" for a film or an episode. [type] is "movie" or "series"; [id] the
 * IMDb id, with ":season:episode" for an episode, or null when the title's details have not been
 * fetched. Adding and removing add-ons happen in the same window.
 */
@Composable
internal fun StremioPopup(
    type: String,
    id: String?,
    onPlay: (StremioAddons.Stream) -> Unit,
    onDismiss: () -> Unit,
) {
    val addons: StremioAddons = koinInject()
    val list by addons.addons.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(Mode.STREAMS) }
    when (mode) {
        Mode.STREAMS -> StreamsView(addons, list, type, id, onPlay, onAdd = { mode = Mode.ADD }, onRemove = { mode = Mode.REMOVE }, onDismiss = onDismiss)
        Mode.ADD -> AddView(addons, onDone = { mode = Mode.STREAMS })
        Mode.REMOVE -> RemoveView(addons, list, onDone = { mode = Mode.STREAMS })
    }
}

@Composable
private fun StreamsView(
    addons: StremioAddons,
    list: List<StremioAddons.Addon>,
    type: String,
    id: String?,
    onPlay: (StremioAddons.Stream) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var found by remember(list, id) { mutableStateOf<StremioAddons.Found?>(null) }
    LaunchedEffect(list, id) {
        found = null
        if (id != null && list.isNotEmpty()) found = runCatching { addons.streams(type, id) }.getOrDefault(StremioAddons.Found(emptyList(), 0, list.size))
    }
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = stringResource(R.string.kevin_stremio_streams)) {
        val firstFocus = remember { FocusRequester() }
        val ready = list.isEmpty() || id == null || found != null
        LaunchedEffect(ready) { if (ready) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
        val muted = @Composable { text: String -> Text(text, style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(bottom = 10.mpx)) }
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            var focusUsed = false
            fun first(): Modifier = if (!focusUsed) { focusUsed = true; Modifier.focusRequester(firstFocus) } else Modifier
            when {
                list.isEmpty() -> muted(stringResource(R.string.kevin_stremio_none_added))
                id == null -> muted(stringResource(R.string.kevin_stremio_needs_imdb))
                found == null -> Box(Modifier.fillMaxWidth().height(80.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                else -> {
                    val f = found!!
                    if (f.playable.isEmpty()) muted(stringResource(R.string.kevin_stremio_no_streams))
                    f.playable.forEach { s ->
                        tv.own.owntv.ui.stage.StagePopupOption(
                            title = s.title,
                            subtitle = s.detail,
                            onClick = { onPlay(s) },
                            modifier = first(),
                            leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.PLAY) },
                        )
                    }
                    if (f.torrents > 0) muted(pluralStringResource(R.plurals.kevin_stremio_torrents, f.torrents, f.torrents))
                    if (f.failedAddons > 0) muted(pluralStringResource(R.plurals.kevin_stremio_failed, f.failedAddons, f.failedAddons))
                }
            }
            if (ready) {
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.kevin_stremio_add), onClick = onAdd, modifier = first(),
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.ADD) },
                )
                if (list.isNotEmpty()) {
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = stringResource(R.string.kevin_stremio_remove), onClick = onRemove,
                        leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.EYE_OFF) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddView(addons: StremioAddons, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf(false) }
    val texts = AddonInbox.Texts(
        title = stringResource(R.string.kevin_stremio_add_title),
        addressLabel = stringResource(R.string.kevin_stremio_address_label),
        codeLabel = stringResource(R.string.kevin_stremio_page_code),
        send = stringResource(R.string.kevin_stremio_page_send),
        sent = stringResource(R.string.kevin_stremio_page_sent),
        wrongCode = stringResource(R.string.kevin_stremio_page_wrong),
    )
    val msgNotManifest = stringResource(R.string.kevin_stremio_not_manifest)
    val msgNoStreams = stringResource(R.string.kevin_stremio_no_stream_resource)
    val msgUnreachable = stringResource(R.string.kevin_stremio_unreachable)
    val context = androidx.compose.ui.platform.LocalContext.current
    val submit: (String) -> Unit = { address ->
        scope.launch {
            result = when (val r = addons.add(address)) {
                is StremioAddons.AddResult.Added -> context.getString(R.string.kevin_stremio_added, r.name)
                StremioAddons.AddResult.NotAManifest -> msgNotManifest
                StremioAddons.AddResult.NoStreams -> msgNoStreams
                StremioAddons.AddResult.Unreachable -> msgUnreachable
            }
        }
    }
    // The phone page runs only while this window is open.
    val inbox = remember { AddonInbox(scope, texts) { address -> submit(address) } }
    var started by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(inbox) { started = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { inbox.start() } }
    DisposableEffect(inbox) { onDispose { inbox.stop() } }
    val url = remember(started) {
        if (started == true) tv.own.owntv.core.companion.CompanionLink.lanHosts().firstOrNull()?.let { "http://$it:${inbox.port}/" } else null
    }
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDone, title = stringResource(R.string.kevin_stremio_add_title)) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
        Row(horizontalArrangement = Arrangement.spacedBy(28.mpx), verticalAlignment = Alignment.CenterVertically) {
            val qr = remember(url) { url?.let { tv.own.owntv.core.companion.CompanionLink.renderQr(it) } }
            qr?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = url,
                    modifier = Modifier.size(200.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White).padding(9.mpx),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                if (url != null) {
                    Text(stringResource(R.string.kevin_stremio_add_steps, url, inbox.code), style = stageText(19, 500), color = StageColors.Text)
                } else if (started == false) {
                    Text(stringResource(R.string.kevin_stremio_server_failed), style = stageText(17, 500), color = StageColors.Danger)
                }
                result?.let { Text(it, style = stageText(18, 700), color = StageColors.Text) }
            }
        }
        Column(Modifier.padding(top = 16.mpx).trapAllFocusExit().focusGroup()) {
            tv.own.owntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.kevin_stremio_type_here), onClick = { typing = true },
                modifier = Modifier.focusRequester(firstFocus),
                leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.PENCIL) },
            )
        }
    }
    if (typing) {
        tv.own.owntv.ui.components.TextInputDialog(
            title = stringResource(R.string.kevin_stremio_add_title),
            label = stringResource(R.string.kevin_stremio_address_label),
            allowBlank = false,
            onConfirm = { typing = false; submit(it) },
            onDismiss = { typing = false },
        )
    }
}

@Composable
private fun RemoveView(addons: StremioAddons, list: List<StremioAddons.Addon>, onDone: () -> Unit) {
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDone, title = stringResource(R.string.kevin_stremio_remove_title)) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            list.forEachIndexed { i, a ->
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = a.name, subtitle = a.base.substringAfter("://").substringBefore('/'),
                    onClick = { addons.remove(a); onDone() },
                    danger = true,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.EYE_OFF) },
                )
            }
        }
    }
}
