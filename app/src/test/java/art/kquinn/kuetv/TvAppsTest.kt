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
            "com.stv.android" to "STV Player",
            "uk.co.uktv.dave" to "U",
            "ie.tg4.player" to "TG4 Player",
        )
        assertEquals(
            listOf(
                "uk.co.bbc.iplayer", "air.ITVMobilePlayer", "com.channel4.ondemand", "com.something.five", "ie.rte.player", "com.virginmedia.play",
                "com.stv.android", "uk.co.uktv.dave", "ie.tg4.player",
            ),
            TvAppList.match(onTv),
        )
    }

    @Test fun missingAppsAreNullAndLookalikesDoNotCount() {
        val onTv = linkedMapOf(
            "com.netflix.ninja" to "Netflix",
            "com.example.five" to "5 Minute Crafts",
            "com.example.radio" to "Radio 5 Live",
            "com.example.youtube" to "YouTube",
            "com.example.stvp" to "STV News Extra",
            "uk.co.bbc.iplayer" to "BBC iPlayer",
        )
        assertEquals(listOf("uk.co.bbc.iplayer", null, null, null, null, null, null, null, null), TvAppList.match(onTv))
    }

    @Test fun nothingOnTheTvGivesAllMissing() {
        assertEquals(List(TvAppList.all.size) { null }, TvAppList.match(emptyMap()))
    }

    @Test fun rtePlayerOnFireTvCounts() {
        val onTv = linkedMapOf("com.twentyfouri.tvbridge.rte" to "RTE Player")
        assertEquals("com.twentyfouri.tvbridge.rte", TvAppList.match(onTv)[4])
    }

    @Test fun eachCountryHasItsPlayers() {
        assertEquals(listOf("BBC iPlayer", "ITVX", "Channel 4", "5 (Channel 5)", "STV Player", "U (UKTV)"), TvAppList.all.filter { it.country == TvAppList.UK }.map { it.title })
        assertEquals(listOf("RTÉ Player", "Virgin Media Play", "TG4 Player"), TvAppList.all.filter { it.country == TvAppList.IRELAND }.map { it.title })
    }
}
