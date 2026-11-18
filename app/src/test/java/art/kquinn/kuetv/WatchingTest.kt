package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Batch 1, Watching: the Options panel in the player, sound sync, the sounds' names, and catch-up playing on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class WatchingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pass(ms: Long) = rule.mainClock.advanceTimeBy(ms)
    // A press, then the change is handed on (it waits on the main thread's queue) and the screen is drawn again.
    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        pass(300)
    }
    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        pass(300)
    }
    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** The words on the Options row the remote is on, for the message when a step fails. */
    private fun onRow(): String = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("option on"), useUnmergedTree = true).fetchSemanticsNodes()
        .flatMap { n -> rule.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("option on")), useUnmergedTree = true).fetchSemanticsNodes() }
        .mapNotNull { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ") }
        .joinToString(" | ").ifEmpty { "no row" }

    @Test fun optionsOpenFromTheButtonsAndChangeThePictureShape() {
        rule.mainClock.autoAdvance = false
        var closed = 0
        rule.setContent { PlayerScreen(listOf(Playable("A Film", "http://films.example/1.mp4")), 0, live = false, onClose = { closed++ }) }
        pass(300)
        // OK brings up the buttons on Play; ▶ ▶ ▶ moves past On 10 s and Subtitles onto Options.
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue("OK on Options opens the panel", shown("Picture shape: Fit"))
        assertTrue("the panel has the sound sync row", shown("Sound sync: On time"))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue("OK on the shape row moves it on to Stretch", shown("Picture shape: Stretch"))
        assertTrue("the player keeps the remote after the shape changes", rule.onAllNodes(androidx.compose.ui.test.hasTestTag("player") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty())
        press(Key.DirectionDown)
        assertTrue("▼ moves onto the sync row · remote on: " + onRow(), onRow().contains("Sound sync"))
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertTrue("▶ ▶ on the sync row: sound 100 ms later · remote on: " + onRow(), shown("Sound 100 ms later"))
        press(Key.DirectionCenter)
        assertTrue("OK on the sync row: back to on time", shown("Sound sync: On time"))
        back()
        assertTrue("Back puts the panel away", !shown("Picture shape"))
        assertEquals("…and stays in the player", 0, closed)
    }

    @Test fun theSyncRowTakesLeftAndRight() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(listOf(Playable("A Film", "http://films.example/1.mp4")), 0, live = false, onClose = {}) }
        pass(300)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertTrue("▼ ▼ moves onto the sync row · remote on: " + onRow(), onRow().contains("Sound sync"))
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertTrue("▶ ▶ on the sync row: sound 100 ms later · remote on: " + onRow(), shown("Sound 100 ms later"))
    }

    @Test fun okOnTheSoundRowLeavesTheRemoteWorking() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PlayerScreen(listOf(Playable("A Film", "http://films.example/1.mp4")), 0, live = false, onClose = {}) }
        pass(300)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        assertTrue("after OK on the sound row, ▼ still moves · remote on: " + onRow(), onRow().contains("Picture shape"))
    }

    @Test fun soundSyncMovesThePlaceAndLeavesNotKnownAlone() {
        assertEquals(1_150_000L, SoundSync.shifted(1_000_000L, 150_000L))
        assertEquals(900_000L, SoundSync.shifted(1_000_000L, -100_000L))
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, SoundSync.shifted(AudioSink.CURRENT_POSITION_NOT_SET, 150_000L))
        assertEquals("Sound 100 ms earlier", SoundSync.words(-100))
    }

    @Test fun soundsAreNamedPlainly() {
        assertEquals("English", soundName("en", null, 2, 1))
        assertEquals("English 5.1", soundName("eng", null, 6, 1))
        assertEquals("Commentary", soundName("en", "Commentary", 2, 2))
        assertEquals("Sound 3", soundName("und", null, 2, 3))
    }

    @Test fun catchUpPlaysOnIntoTheNextProgrammeOnceItHasFinished() = runBlocking {
        val now = System.currentTimeMillis()
        val account = Account("http://tv.example", "user", "pass", "")
        val first = Programme("Early News", "", now - 3 * 3_600_000L, now - 2 * 3_600_000L, "")
        val second = Programme("Quiz", "", now - 2 * 3_600_000L, now - 3_600_000L, "")
        val third = Programme("Film", "", now - 3_600_000L, now + 3_600_000L, "")
        NowNext.keepForChecks("77", listOf(first, second, third))
        val after = catchupPlayable("RTÉ One", account, "77", first, 0).then?.invoke()
        assertEquals("after the first, the second plays on", "RTÉ One · Quiz", after?.name)
        assertNull("after the second, the third is still on air, so nothing plays on", after?.then?.invoke())
    }
}
