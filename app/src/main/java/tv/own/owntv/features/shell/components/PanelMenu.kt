package tv.own.owntv.features.shell.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.own.owntv.core.nav.MainSection

/**
 * Kevin's copy (v1): one left panel instead of two. Where a screen shows its categories as a column on
 * the left (Live TV, Films, Series), the menu that the rail holds — Now playing, Search, Home, Live TV,
 * TV Guide, Films, Series, Downloads, More — is listed at the top of that column, and the rail is not
 * drawn. [sections] in rail order; [current] is marked.
 */
internal data class PanelMenu(
    val sections: List<MainSection>,
    val current: MainSection,
    val onSelect: (MainSection) -> Unit,
    val onNowPlaying: (() -> Unit)?,
)

/** Provided by the shell around the browse screens; null where the rail is the menu. */
internal val LocalPanelMenu = compositionLocalOf<PanelMenu?> { null }

/** What the shell needs to know about the column that carries the menu while it is on screen. */
internal object PanelMenuHost {
    /** A category column with the menu in it is on screen: the rail steps aside. */
    var active by mutableStateOf(false)

    /** The column holds focus (Back from it then offers to leave the app, as Back on the rail did). */
    var focused by mutableStateOf(false)

    /** Puts focus in the column, on its open category. */
    var focusPanel: (() -> Boolean)? = null

    /** Opens the screen's panel when it is a sheet that is closed (or focuses its column). */
    var openPanel: (() -> Unit)? = null
}
