package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Presses the remote's keys on the My add-ons pages, on a pretend TV: the list, one add-on's own page,
 * and the question before removing it. Nothing opens underneath another row; each job is its own page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class AddOnsPagesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun remoteOn(text: String) = rule.onAllNodes(isFocused() and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))))
        .fetchSemanticsNodes().isNotEmpty()
    private fun where(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") ?: "something with no words"
    }.ifEmpty { "nothing" }

    @Test fun eachAddOnHasItsOwnPageAndRemovingAsksFirst() {
        val app = RuntimeEnvironment.getApplication()
        AddOnStore.add(app, AddOn("https://addon.invalid", "Test add-on", streams = true, subtitles = false))
        rule.setContent { AddOnsForChecks() }
        rule.waitForIdle()

        assertTrue("the list shows the add-on · remote on " + where(), shown("Test add-on"))
        assertTrue("the remote starts on Check them all again · remote on " + where(), remoteOn("Check them all again"))

        press(Key.DirectionDown)
        assertTrue("▼ moves onto the add-on · remote on " + where(), remoteOn("Test add-on"))
        press(Key.DirectionCenter)
        assertTrue("OK opens the add-on's own page, with Check again · remote on " + where(), remoteOn("Check again"))
        assertFalse("the list's rows are gone from its own page · remote on " + where(), shown("Check them all again"))

        press(Key.DirectionDown)
        assertTrue("▼ moves onto Remove · remote on " + where(), remoteOn("Remove Test add-on"))
        press(Key.DirectionCenter)
        assertTrue("Remove asks first, with the remote on Yes · remote on " + where(), remoteOn("Yes, remove it"))
        assertTrue("…and offers No · remote on " + where(), shown("No, keep it"))
        assertFalse("…with nothing else in the way · remote on " + where(), shown("Check again"))

        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue("No goes back to the add-on's page · remote on " + where(), shown("Check again"))
        assertTrue("…and the add-on is still there", AddOnStore.all(app).any { it.name == "Test add-on" })

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue("Back from the add-on's page goes to the list · remote on " + where(), shown("Check them all again"))
        assertTrue("…with the remote on that add-on · remote on " + where(), remoteOn("Test add-on"))
    }
}
