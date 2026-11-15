package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which apps on a pretend TV count as which of the free UK and Irish players. */
class TvAppsTest {
    @Test fun findsEachAppByNameOrPackage() {
        val onTv = linkedMapOf(
            "com.netflix.ninja" to "Netflix",
            "uk.co.bbc.iplayer" to "BBC iPlayer",
            "air.ITVMobilePlayer" to "ITVX",
            "com.channel4.ondemand" to "Channel 4",
            "com.something.five" to " 5 ",
            "ie.rte.player" to "RTÉ Player",
            "com.virginmedia.play" to "Virgin Media Play",
        )
        assertEquals(
            listOf("uk.co.bbc.iplayer", "air.ITVMobilePlayer", "com.channel4.ondemand", "com.something.five", "ie.rte.player", "com.virginmedia.play"),
            TvAppList.match(onTv),
        )
    }

    @Test fun missingAppsAreNullAndLookalikesDoNotCount() {
        val onTv = linkedMapOf(
            "com.netflix.ninja" to "Netflix",
            "com.example.five" to "5 Minute Crafts",
            "com.example.radio" to "Radio 5 Live",
            "uk.co.bbc.iplayer" to "BBC iPlayer",
        )
        assertEquals(listOf("uk.co.bbc.iplayer", null, null, null, null, null), TvAppList.match(onTv))
    }

    @Test fun nothingOnTheTvGivesAllMissing() {
        assertEquals(List(TvAppList.all.size) { null }, TvAppList.match(emptyMap()))
    }
}
