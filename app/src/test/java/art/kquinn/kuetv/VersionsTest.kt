package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Keeping the 4K, FHD, HD and SD versions of one channel together, on a pretend channel list. */
class VersionsTest {
    private var next = 0
    private fun channel(name: String, category: String = "uk") = Channel((++next).toString(), name, "", category, "http://tv/$next.ts", 0)

    @Test fun versionsOfOneChannelBecomeOneRowBestFirst() {
        val groups = Versions.group(
            listOf(channel("UK: BBC One SD"), channel("UK: BBC One"), channel("UK: BBC One FHD", "uk fhd"), channel("UK: BBC One HD"), channel("UK: BBC Two HD")),
        )
        assertEquals(listOf("UK: BBC One", "UK: BBC Two"), groups.map { it.name })
        assertEquals(listOf("FHD", "HD", "Standard", "SD"), groups[0].versions.map { it.label })
        assertEquals(0, groups[0].start)
        assertEquals(listOf("HD"), groups[1].versions.map { it.label })
    }

    @Test fun fourKIsKeptButNotPlayedFirst() {
        val group = Versions.group(listOf(channel("Sky Sports F1 HD"), channel("Sky Sports F1 UHD"), channel("Sky Sports F1 FHD"))).single()
        assertEquals(listOf("4K", "FHD", "HD"), group.versions.map { it.label })
        assertEquals("FHD", group.versions[group.start].label)
        // A channel that only comes in 4K still plays.
        assertEquals(0, Versions.group(listOf(channel("Insight 4K"))).single().start)
    }

    @Test fun differentChannelsStayApart() {
        val groups = Versions.group(
            listOf(channel("Channel 4 HD"), channel("Channel 4 +1"), channel("Channel 4 +1 HD"), channel("IE: Channel 4 HD"), channel("HDNet Movies"), channel("E4 HD"), channel("E4 Extra HD")),
        )
        assertEquals(listOf("Channel 4", "Channel 4 +1", "IE: Channel 4", "HDNet Movies", "E4", "E4 Extra"), groups.map { it.name })
        assertEquals(listOf(1, 2, 1, 1, 1, 1), groups.map { it.versions.size })
    }

    @Test fun readsTheWaysProvidersWriteTheVersion() {
        val read: (String) -> String = { Versions.read(it).let { r -> r.name + " = " + r.label } }
        assertEquals("BBC One = HD", read("BBC One ᴴᴰ"))
        assertEquals("BBC One = FHD", read("FHD: BBC One"))
        assertEquals("BBC One = FHD", read("BBC One 1080p"))
        assertEquals("BBC One = FHD HEVC", read("BBC One FHD (H265)"))
        assertEquals("BBC One = HD 50fps", read("BBC One HD 50FPS"))
        assertEquals("BBC One = Standard", read("BBC One"))
        assertEquals("HD = Standard", read("HD"))
        // The mark that joined the version to the name goes too, whichever mark the provider uses.
        assertEquals("BBC One = FHD", read("BBC One | FHD"))
        assertEquals("BBC One = HD", read("BBC One \u2502 HD\u00A0"))
        assertEquals("BBC One = Standard", read("BBC One |"))
        assertEquals("UK: Channel 4 +1 = SD", read("UK: Channel 4 +1 - SD"))
        assertEquals("Film4 (Ireland) = HD", read("HD | Film4 (Ireland)"))
    }

    @Test fun hiddenCategoriesTakeTheirChannelsFilmsAndSeriesWithThem() {
        val full = Library(
            listOf(Category("uk", "UK"), Category("fr", "France")),
            listOf(channel("BBC One HD", "uk"), channel("TF1 HD", "fr"), channel("BBC One FHD", "fr")),
            listOf(Category("d", "Drama"), Category("x", "Other")),
            listOf(Film("1", "A", "", "d", "u"), Film("2", "B", "", "x", "u")),
            listOf(Category("c", "Crime")),
            listOf(Show("9", "S", "", "c")),
        )
        val shown = full.without(setOf("fr"), setOf("x"), setOf("c"))
        assertEquals(listOf("UK"), shown.liveCategories.map { it.name })
        assertEquals(listOf("BBC One"), shown.groups.map { it.name })
        assertEquals("the version filed under the hidden category goes; the one under a shown category stays", listOf("HD"), shown.groups[0].versions.map { it.label })
        assertEquals(listOf("A"), shown.films.map { it.name })
        assertEquals(emptyList<String>(), shown.shows.map { it.name })
        assertEquals(emptyList<String>(), shown.showCategories.map { it.name })
        assertEquals("nothing hidden gives the same lists back", full, full.without(emptySet(), emptySet(), emptySet()))
    }

    @Test fun sameVersionListedTwiceIsKeptAsASpare() {
        val group = Versions.group(listOf(channel("ITV1 HD"), channel("ITV1 HD"), channel("ITV1 SD"))).single()
        assertEquals(listOf("HD", "HD", "SD"), group.versions.map { it.label })
        assertEquals(listOf("1", "2", "3"), group.versions.map { it.channel.id })
    }

    @Test fun nowTvIsTheSameChannelByAnotherRoute() {
        val groups = Versions.group(
            listOf(
                channel("Sky Sports News | NowTV"), channel("Sky Sports News"),
                channel("Sky Sports Main Event | NowTV"), channel("Sky Sports Main Event"),
                channel("Sky Sports Premier League | Now TV"), channel("Sky Sports Premier League HD"),
                channel("Sky Sports F1 | NowTV"), channel("NOW 80s"), channel("Now TV"),
            ),
        )
        assertEquals(
            listOf("Sky Sports News", "Sky Sports Main Event", "Sky Sports Premier League", "Sky Sports F1", "NOW 80s", "Now TV"),
            groups.map { it.name },
        )
        assertEquals(2, groups[0].versions.size)
        assertEquals("Standard Now TV", Versions.read("Sky Sports News | NowTV").label)
    }
}
