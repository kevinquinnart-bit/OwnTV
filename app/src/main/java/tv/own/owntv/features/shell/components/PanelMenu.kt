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
    /** Kevin's copy (v2): OK on Catch-up or TV apps (a [MenuExtra.key]). */
    val onExtra: (String) -> Unit = {},
)

/**
 * Kevin's copy (v2): two menu entries that are not screens of their own in the original menu, listed
 * with Live TV, Films and the rest: Catch-up (Live TV on its Catch-up list) and TV apps (the free
 * players' page). [after] is the screen each one follows.
 */
internal data class MenuExtra(
    val key: String,
    @param:androidx.annotation.StringRes val labelRes: Int,
    val icon: tv.own.owntv.ui.components.OwnTVIcon,
    val after: MainSection,
)

internal const val MENU_EXTRA_CATCHUP = "catchup"
internal const val MENU_EXTRA_TV_APPS = "tvapps"

internal val MenuExtras = listOf(
    MenuExtra(MENU_EXTRA_CATCHUP, tv.own.owntv.R.string.kevin_menu_catchup, tv.own.owntv.ui.components.OwnTVIcon.CATCHUP, MainSection.EPG),
    MenuExtra(MENU_EXTRA_TV_APPS, tv.own.owntv.R.string.kevin_players_title, tv.own.owntv.ui.components.OwnTVIcon.PLAY_CIRCLE, MainSection.SERIES),
)

/** One row of the menu: a screen, or one of the [MenuExtras]. */
internal sealed interface MenuRow {
    data class Screen(val section: MainSection) : MenuRow
    data class Extra(val extra: MenuExtra) : MenuRow
}

/** [sections] in order with Catch-up and TV apps slotted in (before More, when their screen is not shown). */
internal fun menuRows(sections: List<MainSection>): List<MenuRow> {
    val rows = mutableListOf<MenuRow>()
    val placed = mutableSetOf<String>()
    sections.forEach { section ->
        if (section == MainSection.MORE) MenuExtras.filter { it.key !in placed }.forEach { rows += MenuRow.Extra(it); placed += it.key }
        rows += MenuRow.Screen(section)
        MenuExtras.filter { it.after == section && it.key !in placed }.forEach { rows += MenuRow.Extra(it); placed += it.key }
    }
    MenuExtras.filter { it.key !in placed }.forEach { rows += MenuRow.Extra(it) }
    return rows
}

/** Kevin's copy (v2): a page More should open on when it is next shown (TV apps, from the menu elsewhere). */
internal object MenuExtraHost {
    var pendingMore by mutableStateOf<String?>(null)
}

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
