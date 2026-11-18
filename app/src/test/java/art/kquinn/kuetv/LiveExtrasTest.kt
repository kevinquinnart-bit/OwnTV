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

/** Batch 2, Guide and Live TV: the list of channels over the picture, typing a channel's number, Recently watched, the guide's days. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class LiveExtrasTest {
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

    @Test fun leftOpensTheChannelListAndOkWatchesTheOneChosen() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(channels, 0, live = true, onClose = {}) }
        settle()
        press(Key.DirectionLeft)
        assertTrue("◀ opens the list of channels", soon { shownNow("OK watch") })
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue("OK watches the third channel and puts the list away", soon { !shownNow("OK watch") && shownNow("Channel C") })
    }

    @Test fun typingANumberGoesToThatChannel() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(channels, 0, live = true, onClose = {}) }
        settle()
        press(Key.Two)
        assertTrue("the number typed shows", soon { shownNow("2") })
        pass(2_000)
        assertTrue("a moment later, channel 2 plays", soon { shownNow("Channel B") })
    }

    @Test fun recentlyWatchedIsNewestFirst() {
        val groups = listOf("a", "b", "c", "d")
        val recent = listOf("url-c", "url-a")
        assertEquals(listOf("c", "a"), Recent.pick(groups, recent) { listOf("url-$it") })
    }

    @Test fun theGuideOffersAWeek() {
        val now = java.util.Calendar.getInstance().apply { set(2026, 10, 18, 20, 10, 0) }.timeInMillis
        assertEquals("Today", dayName(now, 0))
        assertEquals("Fri 20", dayName(now, 2))
        val midnight = midnightOf(now, 1)
        assertEquals(24 * 60 + 0, ((midnight - midnightOf(now, 0)) / 60_000).toInt())
        assertTrue("today starts two hours before now, on the half hour", guideStart(now, 0) == now - now % (30 * 60_000L) - 120 * 60_000L)
    }
}
