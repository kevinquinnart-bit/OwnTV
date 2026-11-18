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

/** Batch 5, Extras: the free UK and Irish guides, and Multiview. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ExtrasTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun channelNamesArePlain() {
        assertEquals("bbc one", FreeGuides.plain("UK: BBC One HD"))
        assertEquals("bbc one", FreeGuides.plain("BBC ONE (FHD)"))
        assertEquals("rte one", FreeGuides.plain("IE: RTÉ One"))
        assertEquals("rte one", FreeGuides.plain("RTE One HD"))
        assertEquals("sky sports f1", FreeGuides.plain("|UK| Sky Sports F1 HD"))
        assertEquals("5+1", FreeGuides.plain("5+1"))
        assertEquals("u and dave", FreeGuides.plain("U&Dave HD"))
        assertEquals("bbc1", FreeGuides.key("UK: BBC One HD"))
        assertEquals("itv1", FreeGuides.key("ITV 1"))
        assertEquals("dave", FreeGuides.key("U&Dave HD"))
        assertEquals("5", FreeGuides.key("UK: Channel 5"))
        assertEquals("rte2", FreeGuides.key("RTÉ Two"))
    }

    @Test fun aFreeGuideIsReadForTheChannelsWanted() {
        val now = FreeGuides.time("20261118200000 +0000")!!
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="bbc1london.uk"><display-name lang="en">BBC One London HD</display-name></channel>
              <channel id="bbc1wales.uk"><display-name lang="en">BBC One Wales HD</display-name></channel>
              <channel id="rte1.ie"><display-name lang="en">RTE One HD</display-name></channel>
              <channel id="other.uk"><display-name lang="en">Something Else</display-name></channel>
              <programme channel="bbc1london.uk" start="20261118190000 +0000" stop="20261118193000 +0000"><title>The One Show</title><desc>Chat.</desc></programme>
              <programme channel="bbc1wales.uk" start="20261118190000 +0000" stop="20261118193000 +0000"><title>Wales Today</title></programme>
              <programme channel="rte1.ie" start="20261118200000 +0000" stop="20261118210000 +0000"><title>Prime Time</title><desc>News &amp; views.</desc></programme>
              <programme channel="rte1.ie" start="20260101000000 +0000" stop="20260101010000 +0000"><title>Long ago</title></programme>
              <programme channel="other.uk" start="20261118200000 +0000" stop="20261118210000 +0000"><title>Not wanted</title></programme>
            </tv>"""
        val got = FreeGuides.read(xml.byteInputStream(), setOf("bbc1", "rte1"), now)
        assertEquals("BBC One takes the London one", listOf("The One Show"), got["bbc1"]?.map { it.title })
        assertEquals("RTÉ One's, without what is long gone", listOf("Prime Time"), got["rte1"]?.map { it.title })
        assertEquals("News & views.", got["rte1"]?.first()?.description)
        assertEquals("only the channels wanted", setOf("bbc1", "rte1"), got.keys)
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

    @Test fun multiviewPutsAChannelInEachSquare() {
        val channels = listOf(
            Playable("Channel A", "http://tv.example/live/1.ts"),
            Playable("Channel B", "http://tv.example/live/2.ts"),
            Playable("Channel C", "http://tv.example/live/3.ts"),
        )
        var watched = -1
        rule.mainClock.autoAdvance = false
        rule.setContent { MultiviewScreen(channels, 0, onClose = {}, onWatch = { watched = it }) }
        settle()
        assertTrue("the first square has the channel that was playing", soon { shownNow("Channel A") })
        // ▶ to the second square, OK for the list, ▼ ▼ to Channel C, OK.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue("OK opens the list of channels", soon { shownNow("Channel for this square") })
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue("Channel C is in the second square", soon { !shownNow("Channel for this square") && shownNow("Channel C") })
        // Back to the first square: its list starts with "Watch this one full screen".
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals("Watch full screen plays the first square's channel", 0, watched)
    }
}
