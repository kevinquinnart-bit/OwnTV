package tv.own.owntv.features.shell.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.mpx

/** One row of a [LibraryLinksPopup]: a title and the line under it. */
data class LibraryLinkRow(val title: String, val subtitle: String?)

/**
 * Kevin's copy: the list behind "More like this", "With an actor…" and the actor's titles.
 * [rows] null shows a spinner while the library is read; empty shows [emptyText].
 */
@Composable
internal fun LibraryLinksPopup(
    title: String,
    rows: List<LibraryLinkRow>?,
    emptyText: String,
    icon: OwnTVIcon,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    tv.own.owntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(rows) {
            if (!rows.isNullOrEmpty()) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
        }
        when {
            rows == null -> Box(Modifier.fillMaxWidth().height(80.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
            rows.isEmpty() -> Text(emptyText, style = tv.own.owntv.ui.theme.stageText(17, 500), color = tv.own.owntv.ui.theme.StageColors.Muted)
            else -> Column(Modifier.trapAllFocusExit().focusGroup()) {
                rows.forEachIndexed { i, row ->
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = row.title, subtitle = row.subtitle, onClick = { onPick(i) },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        leading = { tv.own.owntv.ui.stage.StagePopupIcon(icon) },
                    )
                }
            }
        }
    }
}
