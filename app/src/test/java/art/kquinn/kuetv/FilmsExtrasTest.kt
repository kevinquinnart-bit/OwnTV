package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Batch 3, Films and series: watched marks, progress, the sort, the hold menu, and trailers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class FilmsExtrasTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    // Keys go to the window on top: the menu once it is open.
    private fun press(key: Key) {
        rule.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test fun holdingOkOpensTheMenuWithoutPlayingAndMarksWatched() {
        val film = Film("5", "A Film", "", "1", "http://films.example/5.mp4")
        val library = Library(listOf(Category("1", "UK")), listOf(Channel("10", "BBC One", "", "1", "http://tv.example/10.ts", 0)), listOf(Category("1", "Drama")), listOf(film))
        var played = 0
        rule.setContent { FilmsForChecks(library, Favourites(context)) { played++ } }
        rule.waitForIdle()
        // ▼ from the top bar into the categories, ▶ onto the buttons above the posters, ▼ onto the film.
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        // Hold OK on the film for a second: the key repeats while held, as a remote's does.
        rule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        rule.waitForIdle()
        assertTrue("holding OK opens the film's menu", shown("Add to favourites"))
        assertEquals("letting go of OK doesn't play the film", 0, played)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertFalse("a choice closes the menu", shown("Add to favourites"))
        assertTrue("Mark as watched marks it", Watched.isWatched(context, film.url))
        assertTrue("…and its poster shows ✓", shown("✓"))
    }

    @Test fun watchingIntoTheCreditsMarksItWatched() {
        val url = "http://films.example/9.mp4"
        Positions.keep(context, url, 40 * 60_000L, 100 * 60_000L, "Nine", "")
        assertEquals(0.4f, Positions.progress(context, url)!!, 0.001f)
        assertFalse(Watched.isWatched(context, url))
        Positions.keep(context, url, 97 * 60_000L, 100 * 60_000L, "Nine", "")
        assertTrue("past 95% is watched", Watched.isWatched(context, url))
        assertNull("…and no longer part way", Positions.progress(context, url))
    }

    @Test fun aToZIgnoresTagsAndThe() {
        val names = listOf("|UK| Zulu", "The Abyss", "Batman", "EN - Alien")
        assertEquals(listOf("The Abyss", "EN - Alien", "Batman", "|UK| Zulu"), Listing.sorted(names, 1, { it }, { 0L }))
        assertEquals(listOf("b", "a"), Listing.sorted(listOf("a", "b"), 2, { it }, { if (it == "b") 2L else 1L }))
    }

    @Test fun trailersAreFoundInAnyAddress() {
        assertEquals("dQw4w9WgXcQ", trailerId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", trailerId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", trailerId("https://youtu.be/dQw4w9WgXcQ"))
    }
}
