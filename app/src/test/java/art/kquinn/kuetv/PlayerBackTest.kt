package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Presses Back on a live channel playing full screen, on a pretend TV. The first Back puts the programme
 * box away; the second goes back to the list. The programme details arriving in between must not bring
 * the box back, which used to make it take three presses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class PlayerBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    private fun wait(ms: Long) = rule.mainClock.advanceTimeBy(ms)

    @Test fun backTwiceGetsBackToTheList() {
        var closed = 0
        val now = System.currentTimeMillis()
        val channel = Playable(
            "BBC One", "http://tv.example/live/10.ts",
            guide = { listOf(Programme("The News", "What is happening.", now - 600_000, now + 1_200_000, "")) },
        )
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(listOf(channel), 0, live = true, onClose = { closed++ }) }
        wait(300)

        back()
        wait(100)
        assertEquals("the first Back only puts the programme box away", 0, closed)

        // The programme details arrive (the player asks for them after 0.7 seconds).
        wait(2_000)

        back()
        wait(100)
        assertEquals("the second Back goes back to the list", 1, closed)
    }
}
