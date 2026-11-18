package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Batch 6, Watching II: the previous channel, the sleep timer, sound only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class Watching2Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pass(ms: Long) = rule.mainClock.advanceTimeBy(ms)
    private fun settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        pass(100)
    }
    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        settle()
    }
    private fun shownNow(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun soon(check: () -> Boolean): Boolean {
        repeat(30) {
            if (check()) return true
            settle()
        }
        return check()
    }

    private val channels = listOf(
        Playable("Channel A", "http://tv.example/live/1.ts"),
        Playable("Channel B", "http://tv.example/live/2.ts"),
        Playable("Channel C", "http://tv.example/live/3.ts"),
    )

    @Test fun zeroGoesBackToTheChannelBefore() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(channels, 0, live = true, onClose = {}) }
        settle()
        press(Key.Three)
        pass(2_000)
        assertTrue("3 goes to Channel C", soon { shownNow("Channel C") })
        press(Key.Zero)
        pass(2_000)
        assertTrue("0 goes back to Channel A", soon { shownNow("Channel A") })
    }

    @Test fun theSleepTimerStepsAlongAndOff() {
        val now = 1_000_000_000L
        SleepTimer.endAtMs = 0L
        SleepTimer.next(now)
        assertEquals(now + 15 * 60_000L, SleepTimer.endAtMs)
        SleepTimer.next(now)
        assertEquals(now + 30 * 60_000L, SleepTimer.endAtMs)
        repeat(4) { SleepTimer.next(now) }
        assertEquals(now + 120 * 60_000L, SleepTimer.endAtMs)
        SleepTimer.next(now)
        assertEquals("after 2 hours, off", 0L, SleepTimer.endAtMs)
        assertEquals("Off", SleepTimer.words(now))
    }

    @Test fun optionsTurnThePictureOffForSoundOnly() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(listOf(Playable("A Film", "http://films.example/1.mp4")), 0, live = false, onClose = {}) }
        settle()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue("Options open", soon { shownNow("Picture: on") })
        repeat(5) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        assertTrue("OK on Picture: sound only", soon { shownNow("Sound only") })
    }
}
