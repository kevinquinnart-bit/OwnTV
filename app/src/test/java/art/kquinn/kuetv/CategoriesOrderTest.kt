package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Presses the remote's keys on the Categories screen, on a pretend TV, to put Live TV's categories in order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class CategoriesOrderTest {
    @get:Rule val rule = createComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun remoteOn(text: String) = rule.onAllNodes(isFocused() and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))))
        .fetchSemanticsNodes().isNotEmpty()
    private fun where(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") ?: "something with no words"
    }.ifEmpty { "nothing" }
    private fun top(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().first().boundsInRoot.top

    @Test fun liveTvCategoriesArePutInOrderWithTheRemote() {
        val app = RuntimeEnvironment.getApplication()
        val library = Library(
            listOf(Category("1", "UK"), Category("2", "Sport"), Category("3", "News")),
            listOf(Channel("10", "BBC One", "", "1", "http://tv.example/live/10.ts", 0)),
            emptyList(), emptyList(),
        )
        rule.setContent { CategoriesForChecks(library) }
        rule.waitForIdle()
        assertTrue("the remote starts on Done · remote on " + where(), remoteOn("Done"))

        press(Key.DirectionDown)
        assertTrue("▼ moves onto Put Live TV's categories in order · remote on " + where(), remoteOn("Put Live TV"))
        press(Key.DirectionCenter)
        assertTrue("OK starts putting them in order, on the first category · remote on " + where(), remoteOn("UK"))

        press(Key.DirectionCenter)
        assertTrue("OK picks UK up · remote on " + where(), remoteOn("↕"))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertTrue("▼ ▼ moves UK down, and the remote stays on it · remote on " + where(), remoteOn("UK"))
        press(Key.DirectionCenter)
        assertEquals("the order is kept: Sport, News, UK", listOf("2", "3", "1"), Choices.order(app, "live"))
        assertTrue("…and shown that way on screen", top("Sport") < top("News") && top("News") < top("UK"))
        assertEquals("the lists follow it", listOf("Sport", "News", "UK"), library.inOrder(Choices.order(app, "live"), emptyList(), emptyList()).liveCategories.map { it.name })
    }
}
