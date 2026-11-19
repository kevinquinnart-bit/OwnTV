package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Channels hidden one by one: gone from the lists, and back when shown again. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class HideChannelsTest {
    @Test fun hiddenChannelsLeaveTheListsAndComeBack() {
        val app = RuntimeEnvironment.getApplication()
        val library = Library(listOf(Category("1", "UK | Sport")), listOf(Channel("10", "Coupang | Event 1", "", "1", "http://tv/10.ts", 0), Channel("11", "Sky Sports", "", "1", "http://tv/11.ts", 0)), emptyList(), emptyList())
        HiddenChannels.hide(app, listOf("10"))
        assertEquals(listOf("Sky Sports"), library.withoutChannels(HiddenChannels.all(app)).channels.map { it.name })
        HiddenChannels.show(app, listOf("10"))
        assertEquals(2, library.withoutChannels(HiddenChannels.all(app)).channels.size)
    }
}
