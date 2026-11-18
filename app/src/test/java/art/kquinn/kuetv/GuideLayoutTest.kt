package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
 * The TV Guide on a pretend TV screen the size of a Fire TV's: the picture sits in the top right corner, the
 * grid runs the full width of the screen underneath it, and the programme the remote moves onto is described
 * along the top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class GuideLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun show() {
        val now = System.currentTimeMillis()
        NowNext.keepForChecks(
            "10",
            listOf(
                Programme("News at Six", "The day's news.", now - 30 * 60_000L, now + 60 * 60_000L, ""),
                Programme("Late Film", "A late film.", now + 60 * 60_000L, now + 20 * 60 * 60_000L, ""),
            ),
        )
        val library = Library(
            listOf(Category("1", "UK")),
            listOf(Channel("10", "BBC One", "", "1", "http://tv.example/live/10.ts", 0)),
            emptyList(), emptyList(),
            account = Account("http://tv.example", "user", "pass", ""),
        )
        val favourites = Favourites(RuntimeEnvironment.getApplication())
        rule.setContent { GuideForChecks(library, favourites) }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    /** Where a piece of text (or, with [tag], a tagged part) is on the screen, in dp: left, top, right, bottom. */
    private fun at(text: String, tag: Boolean = false): FloatArray {
        val node = rule.onAllNodes(if (tag) hasTestTag(text) else hasText(text, substring = true)).fetchSemanticsNodes().firstOrNull()
            ?: throw AssertionError("\"$text\" isn't on the screen")
        val d = rule.density.density
        val b = node.boundsInRoot
        return floatArrayOf(b.left / d, b.top / d, b.right / d, b.bottom / d)
    }

    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun thePictureIsTopRightAndTheGridRunsTheFullWidth() {
        show()
        val picture = at("guide picture", tag = true)
        val channel = at("BBC One")
        val lateFilm = at("Late Film")
        assertTrue("the picture sits at the right of the screen (its right edge at ${picture[2]} dp of 960)", picture[2] > 880f)
        assertTrue("the picture sits above the grid (picture bottom ${picture[3]} dp, first channel top ${channel[1]} dp)", picture[3] <= channel[1])
        assertTrue("the grid runs to the right of the screen, under the picture (it ends at ${lateFilm[2]} dp of 960)", lateFilm[2] > 880f)

        // Into the grid: ▶ from the menu opens the categories, ▶ again goes into the grid, ▶ moves along a row.
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertTrue(
            "moving along the grid describes that programme along the top",
            shown("The day's news.") || shown("A late film."),
        )
    }
}
