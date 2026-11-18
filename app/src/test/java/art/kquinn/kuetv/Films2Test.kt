package art.kquinn.kuetv

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Batch 8, Films and series II: favourite films and series on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class Films2Test {
    @get:Rule val rule = createComposeRule()

    @Test fun favouriteFilmsAndSeriesAreOnHome() {
        val film = Film("5", "A Favourite Film", "", "1", "http://films.example/5.mp4")
        val show = Show("7", "A Favourite Show", "", "1")
        val library = Library(listOf(Category("1", "UK")), listOf(Channel("10", "BBC One", "", "1", "http://tv/10.ts", 0)), listOf(Category("1", "Drama")), listOf(film), listOf(Category("1", "Drama")), listOf(show))
        val favourites = Favourites(RuntimeEnvironment.getApplication())
        favourites.toggle(Favourites.key(film, false))
        favourites.toggle(Favourites.key(show))
        rule.setContent { HomeForChecks(library, favourites) }
        rule.waitForIdle()
        val shown: (String) -> Boolean = { rule.onAllNodes(hasText(it, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
        val all = rule.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") }
        assertTrue("on screen: " + all.joinToString(" | "), shown("Favourite films") && shown("A Favourite Film"))
        assertTrue(shown("Favourite series") && shown("A Favourite Show"))
    }
}
