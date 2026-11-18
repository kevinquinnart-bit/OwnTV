package art.kquinn.kuetv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Batch 9, Your things II: own categories, renamed channels, spare buttons, profile pictures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class Things2Test {
    @get:Rule val rule = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun ownCategoriesKeepTheirChannelsInOrder() {
        val mine = MyCategories.make(context, "Sport", "10")
        MyCategories.toggle(context, mine.id, "12")
        MyCategories.toggle(context, mine.id, "11")
        assertEquals(listOf("10", "12", "11"), MyCategories.all(context).single { it.id == mine.id }.channels)
        MyCategories.toggle(context, mine.id, "12")
        assertEquals("toggled again: taken out", listOf("10", "11"), MyCategories.all(context).single { it.id == mine.id }.channels)
        MyCategories.remove(context, mine.id)
        assertTrue(MyCategories.all(context).none { it.id == mine.id })
    }

    @Test fun aRenamedChannelShowsItsNewName() {
        val library = Library(listOf(Category("1", "UK")), listOf(Channel("10", "UK: BBC ONE FHD", "", "1", "http://tv/10.ts", 0)), emptyList(), emptyList())
        Renames.set(context, listOf("10"), "BBC One")
        assertEquals(listOf("BBC One"), library.renamed(Renames.all(context)).groups.map { it.name })
        Renames.set(context, listOf("10"), "")
        assertTrue("an empty name puts the provider's back", Renames.all(context).isEmpty())
    }

    @Test fun theGuideButtonOpensTheGuideToBeginWith() {
        assertEquals("TV Guide", SpareKeys.action(context, android.view.KeyEvent.KEYCODE_GUIDE))
        assertEquals("Nothing", SpareKeys.action(context, android.view.KeyEvent.KEYCODE_PROG_RED))
        SpareKeys.next(context, android.view.KeyEvent.KEYCODE_PROG_RED)
        assertEquals("Home", SpareKeys.action(context, android.view.KeyEvent.KEYCODE_PROG_RED))
        assertTrue(SpareKeys.pressed(context, android.view.KeyEvent.KEYCODE_PROG_RED))
        assertEquals("Home", SpareKeys.asked)
        // Put back as it was: the button's ask would otherwise be taken by the next check's screen.
        SpareKeys.asked = null
        SpareKeys.next(context, android.view.KeyEvent.KEYCODE_PROG_RED)
        repeat(SpareKeys.actions.size - 2) { SpareKeys.next(context, android.view.KeyEvent.KEYCODE_PROG_RED) }
        assertEquals("Nothing", SpareKeys.action(context, android.view.KeyEvent.KEYCODE_PROG_RED))
    }

    @Test fun profilesGetPictures() {
        Profiles.nextPicture(context, "")
        assertEquals(Profiles.pictures[0], Profiles.current(context).picture)
        Profiles.nextPicture(context, "")
        assertEquals(Profiles.pictures[1], Profiles.current(context).picture)
    }

    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun press(key: Key) {
        rule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test fun holdingOkOnAChannelPutsItInACategory() {
        val library = Library(listOf(Category("1", "UK")), listOf(Channel("10", "BBC One", "", "1", "http://tv/10.ts", 0)), emptyList(), emptyList())
        val evenings = MyCategories.make(context, "Evenings", null)
        rule.setContent { LiveTvForChecks(library, Favourites(context)) }
        rule.waitForIdle()
        // Into the categories (▶): your own is there. Into the list (▶), then hold OK on BBC One.
        press(Key.DirectionRight)
        assertTrue("your own category is in Live TV's categories", shown("📁 Evenings"))
        press(Key.DirectionRight)
        rule.onAllNodes(isRoot()).onLast().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        rule.waitForIdle()
        assertTrue("the channel's menu", shown("New category with this channel") && shown("Rename this channel"))
        // ▼ to "Add to 📁 Evenings", OK.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals("BBC One is in Evenings", listOf("10"), MyCategories.all(context).single { it.id == evenings.id }.channels)
        MyCategories.remove(context, evenings.id)
    }
}
