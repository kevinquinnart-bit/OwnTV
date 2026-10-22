package tv.own.owntv.features.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx

/** What "Clear favourites" can remove: one type, or everything ([type] null). */
private enum class FavouriteScope(val type: MediaType?, val labelRes: Int, val confirmRes: Int, val icon: OwnTVIcon) {
    LIVE(MediaType.LIVE, R.string.common_nav_live_tv, R.string.more_fav_clear_confirm_live, OwnTVIcon.LIVE_TV),
    MOVIES(MediaType.MOVIE, R.string.common_nav_movies, R.string.more_fav_clear_confirm_movies, OwnTVIcon.MOVIES),
    SERIES(MediaType.SERIES, R.string.common_nav_series, R.string.more_fav_clear_confirm_series, OwnTVIcon.SERIES),
    ALL(null, R.string.more_fav_clear_all_option, R.string.more_fav_clear_confirm_all, OwnTVIcon.TRASH),
}

/**
 * Clear favourites, in two steps inside one popup — the same shape as Clear watch history:
 * first pick Live TV, Movies, Series or All favourites (each with how many it holds), then confirm
 * with Cancel / OK, Cancel focused so a stray press removes nothing. Back steps back to the choice.
 * Used by More › Favourites and Settings › Profile. [onClear] gets null for everything.
 */
@Composable
internal fun ClearFavouritesDialog(
    counts: TypeCounts,
    onClear: (MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf<FavouriteScope?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }
    val p = pending
    StagePopup(
        onDismiss = { if (pending != null) pending = null else onDismiss() },
        title = if (p == null) stringResource(R.string.more_fav_clear) else stringResource(p.confirmRes),
        body = if (p == null) stringResource(R.string.more_fav_clear_choose) else stringResource(R.string.settings_cannot_undo),
        width = 760.mpx,
        buttons = {
            if (p == null) {
                StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            } else {
                StageButton(stringResource(R.string.common_cancel), onClick = { pending = null }, height = 56.mpx, textSize = 19, modifier = Modifier.focusRequester(firstFocus))
                StageButton(stringResource(R.string.common_ok), onClick = { onClear(p.type) }, height = 56.mpx, textSize = 19, tinted = true)
            }
        },
    ) {
        if (p == null) {
            fun countOf(scope: FavouriteScope) = when (scope) {
                FavouriteScope.LIVE -> counts.live
                FavouriteScope.MOVIES -> counts.movies
                FavouriteScope.SERIES -> counts.series
                FavouriteScope.ALL -> counts.total
            }
            // Focus starts on the first choice that has something in it.
            val firstEnabled = FavouriteScope.entries.indexOfFirst { countOf(it) > 0 }
            FavouriteScope.entries.forEachIndexed { i, scope ->
                val all = scope == FavouriteScope.ALL
                val n = countOf(scope)
                StagePopupOption(
                    title = stringResource(scope.labelRes),
                    value = n.toString(),
                    onClick = { pending = scope },
                    // Nothing to clear: shown so the four choices never move, but not pickable.
                    enabled = n > 0,
                    danger = all,
                    modifier = if (i == firstEnabled) Modifier.focusRequester(firstFocus) else Modifier,
                    leading = { StagePopupIcon(scope.icon, if (all) StageColors.Danger else StageColors.Text) },
                )
            }
        }
    }
}
