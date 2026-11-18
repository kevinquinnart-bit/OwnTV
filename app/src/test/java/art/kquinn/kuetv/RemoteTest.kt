package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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

    private fun categoriesShown() = rule.onAllNodes(hasText("CATEGORIES")).fetchSemanticsNodes().isNotEmpty()
    private fun remoteOn(text: String) = rule.onAllNodes(isFocused() and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))))
        .fetchSemanticsNodes().isNotEmpty()
    private fun remoteOnPicture(name: String) = rule.onAllNodes(isFocused() and androidx.compose.ui.test.hasContentDescription(name)).fetchSemanticsNodes().isNotEmpty()

    /** Where the remote is, for the message when a step fails. */
    private fun where(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { n ->
        (n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ")
            ?: n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.joinToString(" ") ?: "something with no words") +
            " at x=" + n.boundsInRoot.left.toInt() + " y=" + n.boundsInRoot.top.toInt()
    }.ifEmpty { "nothing" }

    @Test fun theRemoteMovesBetweenTheTopBarTheCategoriesAndTheList() {
        show()
        assertTrue("the categories are always shown", categoriesShown())
        assertTrue("the old folded edges are gone", rule.onAllNodes(hasText("M")).fetchSemanticsNodes().isEmpty())
        assertTrue("at the start the remote is on Live TV in the top bar · remote on " + where(), remoteOn("Live TV"))

        press(Key.DirectionRight)
        assertTrue("▶ along the bar goes to the TV Guide · remote on " + where(), remoteOn("TV Guide"))
        press(Key.DirectionLeft)

        press(Key.DirectionDown)
        assertTrue("▼ from the bar goes down into the section · remote on " + where(), remoteOn("All") || remoteOn("BBC One") || remoteOn("Sky Sports"))

        // Into the categories, then ▶ into the channel list, ◀ back to the category being shown.
        repeat(3) { press(Key.DirectionLeft) }
        assertTrue("◀ ends on the category being shown · remote on " + where(), remoteOn("All"))
        press(Key.DirectionRight)
        assertTrue("▶ from the categories goes into the channel list · remote on " + where(), remoteOn("BBC One") || remoteOn("Sky Sports"))
        press(Key.DirectionLeft)
        assertTrue("◀ from the channel list goes back to the category being shown · remote on " + where(), remoteOn("All"))

        // ▲ from the top of the categories: up into the bar, onto the section shown.
        repeat(4) { press(Key.DirectionUp) }
        assertTrue("▲ into the bar lands on the section shown · remote on " + where(), remoteOn("Live TV"))
        repeat(12) { press(Key.DirectionRight) }
        assertTrue("Settings, at the end of the bar, can be reached · remote on " + where(), remoteOnPicture("Settings"))
    }
}
