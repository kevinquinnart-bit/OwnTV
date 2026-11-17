package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Presses the remote's keys on the real Live TV screen, on a pretend TV screen the size of a Fire TV's, and
 * checks which side column is open after each press. Only one may be open at a time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class RemoteTest {
    @get:Rule val rule = createComposeRule()

    private fun show() {
        val library = Library(
            listOf(Category("1", "UK"), Category("2", "Sport")),
            listOf(
                Channel("10", "BBC One", "", "1", "http://tv.example/live/10.ts", 0),
                Channel("11", "Sky Sports", "", "2", "http://tv.example/live/11.ts", 0),
            ),
            emptyList(), emptyList(),
        )
        val favourites = Favourites(RuntimeEnvironment.getApplication())
        rule.setContent { LiveTvForChecks(library, favourites) }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    /** The menu is open when its entries are on screen; the categories when their heading is. */
    private fun menuOpen() = rule.onAllNodes(hasText("TV Guide")).fetchSemanticsNodes().isNotEmpty()
    private fun categoriesOpen() = rule.onAllNodes(hasText("CATEGORIES")).fetchSemanticsNodes().isNotEmpty()
    private fun remoteOn(text: String) = rule.onAllNodes(isFocused() and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))))
        .fetchSemanticsNodes().isNotEmpty()

    /** Where the remote is, for the message when a step fails. */
    private fun where(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { n ->
        (n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") ?: "something with no words") +
            " at x=" + n.boundsInRoot.left.toInt()
    }.ifEmpty { "nothing" } + " · menu " + (if (menuOpen()) "open" else "folded") + " · categories " + (if (categoriesOpen()) "open" else "folded")

    @Test fun theRemoteMovesBetweenTheSideColumnsWithOnlyOneOpen() {
        show()
        assertTrue("at the start the menu is open · remote on " + where(), menuOpen())
        assertFalse("at the start the categories are folded · remote on " + where(), categoriesOpen())
        assertTrue("at the start the remote is on Live TV in the menu · remote on " + where(), remoteOn("Live TV"))

        press(Key.DirectionRight)
        assertTrue("▶ from the menu opens the categories · remote on " + where(), categoriesOpen())
        assertFalse("▶ from the menu folds the menu · remote on " + where(), menuOpen())
        assertTrue("▶ from the menu puts the remote on the category being shown (All) · remote on " + where(), remoteOn("All"))

        press(Key.DirectionLeft)
        assertTrue("◀ from the categories opens the menu · remote on " + where(), menuOpen())
        assertFalse("◀ from the categories folds the categories · remote on " + where(), categoriesOpen())
        assertTrue("◀ from the categories puts the remote back on Live TV · remote on " + where(), remoteOn("Live TV"))

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertFalse("▶ into the channel list folds the categories · remote on " + where(), categoriesOpen())
        assertFalse("▶ into the channel list keeps the menu folded · remote on " + where(), menuOpen())
        assertTrue("▶ into the channel list puts the remote on a channel · remote on " + where(), remoteOn("BBC One") || remoteOn("Sky Sports"))

        press(Key.DirectionLeft)
        assertTrue("◀ from the channel list opens the categories · remote on " + where(), categoriesOpen())
        assertFalse("◀ from the channel list keeps the menu folded · remote on " + where(), menuOpen())
        assertTrue("◀ from the channel list puts the remote on the category being shown · remote on " + where(), remoteOn("All"))
    }
}
