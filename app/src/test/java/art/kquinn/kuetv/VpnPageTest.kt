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

/** The VPN page on a pretend TV: adding the details, then Connect, with the remote. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class VpnPageTest {
    @get:Rule val rule = createComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }
    private fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun remoteOn(text: String) = rule.onAllNodes(isFocused() and (hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))))
        .fetchSemanticsNodes().isNotEmpty()
    private fun where(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") ?: "something with no words"
    }.ifEmpty { "nothing" }

    @Test fun theVpnPageAddsDetailsThenConnects() {
        val app = RuntimeEnvironment.getApplication()
        Vpn.forget(app)
        rule.setContent { VpnForChecks() }
        rule.waitForIdle()
        assertTrue("with nothing added, the page asks for the file · remote on " + where(), remoteOn("Or a link to the file"))
        assertTrue("…and says where to get a free Irish one", shows("Where to get a free Irish one"))
        assertTrue("…and offers the Dublin server's file in one press", shows("VPN Jantit's Dublin server"))

        rule.runOnIdle { Vpn.keep(app, "client\nremote ie1.example-vpn.com 1194\nauth-user-pass\n", "kevin", "secret") }
        rule.waitForIdle()
        assertTrue("with details added, the remote is on Connect · remote on " + where(), remoteOn("Connect"))
        assertTrue("…and the page names the server", shows("ie1.example-vpn.com"))
        assertTrue("…and says the VPN is off", shows("VPN off"))

        press(Key.DirectionDown)
        assertTrue("▼ moves to where the TV appears to be · remote on " + where(), remoteOn("appears to be"))
        Vpn.forget(app)
    }
}
