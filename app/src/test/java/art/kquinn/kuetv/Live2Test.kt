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

/** Batch 7, Guide and Live TV II: every showing, guide times, programmes found by title, other lists in the player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class Live2Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyShowingStillToComeIsSet() {
        val now = 10_000_000L
        val link = SeriesLinks.Link("10", "BBC One", "http://tv/10.ts", "The News")
        val listing = listOf(
            Programme("The News", "", now - 3_600_000L, now - 1_800_000L, ""),
            Programme("the news ", "", now + 3_600_000L, now + 5_400_000L, ""),
            Programme("Quiz", "", now + 5_400_000L, now + 7_200_000L, ""),
            Programme("The News", "", now + 86_400_000L, now + 88_200_000L, ""),
        )
        val set = SeriesLinks.showings(link, listing, now)
        assertEquals("the two showings still to come", listOf(now + 3_600_000L, now + 86_400_000L), set.map { it.startMs })
    }

    @Test fun guideTimesAreSaidPlainly() {
        assertEquals("as your provider gives them", GuideOffset.words(0))
        assertEquals("1 hour later", GuideOffset.words(60))
        assertEquals("2 hours earlier", GuideOffset.words(-120))
        assertEquals("30 minutes later", GuideOffset.words(30))
    }

    @Test fun programmesAreFoundByTitle() {
        val now = System.currentTimeMillis()
        NowNext.keepForChecks("10", listOf(Programme("Match of the Day", "", now + 3_600_000L, now + 7_200_000L, ""), Programme("News", "", now, now + 3_600_000L, "")))
        val library = Library(listOf(Category("1", "UK")), listOf(Channel("10", "BBC One", "", "1", "http://tv/10.ts", 0)), emptyList(), emptyList())
        val found = searchGuides(library, "match", now)
        assertEquals(listOf("Match of the Day"), found.map { it.second.title })
        assertEquals("BBC One", found.first().first.name)
    }

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

    @Test fun thePlayersListCanShowFavourites() {
        val channels = listOf(Playable("Channel A", "http://tv.example/live/1.ts"), Playable("Channel B", "http://tv.example/live/2.ts"))
        val favourites = listOf(Playable("Channel X", "http://tv.example/live/9.ts"))
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(channels, 0, live = true, onClose = {}, lists = listOf("Favourites" to { favourites })) }
        settle()
        press(Key.DirectionLeft)
        assertTrue("◀ opens the list, with the lists to switch to", soon { shownNow("This list") && shownNow("Favourites") })
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertTrue("OK on Favourites shows its channels", soon { shownNow("Channel X") })
        press(Key.DirectionCenter)
        assertTrue("OK on a favourite puts the list away", soon { !shownNow("This list") })
        assertTrue("…and watches it", soon { shownNow("Channel X") })
    }
}
