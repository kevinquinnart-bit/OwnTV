package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Batch 12, the second player: VLC, chosen in Settings or from the player's Options. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class VlcTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun theChoiceIsKept() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PlayerEngine.set(context, 1)
        assertTrue(PlayerEngine.vlc(context))
        PlayerEngine.set(context, 0)
        assertEquals("Standard", PlayerEngine.names[PlayerEngine.get(context)])
    }

    @Test fun vlcTriesTheVersionsItCanPlay() {
        val item = Playable("BBC One", "http://tv/1.ts", streams = listOf(Stream("FHD", "http://tv/1fhd.ts"), Stream("Torrent", "magnet:?xt=1"), Stream("SD", "http://tv/1sd.ts")))
        assertEquals(listOf("http://tv/1fhd.ts", "http://tv/1sd.ts"), PlayerEngine.addresses(item))
        assertEquals(listOf("http://tv/2.ts"), PlayerEngine.addresses(Playable("ITV", "http://tv/2.ts")))
        assertTrue("an add-on film with no address of its own", PlayerEngine.addresses(Playable("Film", "")).isEmpty())
        assertEquals("1:05", PlayerEngine.clock(65_000))
        assertEquals("1:45:00", PlayerEngine.clock(6_300_000))
    }
}
