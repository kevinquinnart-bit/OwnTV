package tv.own.owntv.features.more

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerForChannelTest {
    private fun check(expected: Int?, vararg names: String) = names.forEach { assertEquals(it, expected, PlayerForChannel.indexFor(it)) }

    @Test fun bbc() = check(PlayerForChannel.BBC, "UK: BBC ONE FHD", "BBC One NI", "BBC Four", "CBeebies", "BBC News | HD |")

    @Test fun itv() = check(PlayerForChannel.ITV, "ITV 1", "UK: ITV1 FHD", "ITV2", "ITVBe", "UTV")

    @Test fun channel4() = check(PlayerForChannel.C4, "Channel 4", "UK: CHANNEL 4 HD", "E4", "More4", "Film4", "4seven")

    @Test fun channel5() = check(PlayerForChannel.C5, "Channel 5", "5USA", "5 STAR", "5Action")

    @Test fun rte() = check(PlayerForChannel.RTE, "RTE One | FHD |", "RTÉ2", "RTE News Now", "RTEjr")

    @Test fun virgin() = check(PlayerForChannel.VIRGIN, "Virgin Three | FHD |", "Virgin Media One")

    @Test fun none() = check(null, "Sky Sports Main Event", "TG4", "Euronews", "Dave", "Channel 44", "Film 400", "MTV", "Sky Arts")
}
