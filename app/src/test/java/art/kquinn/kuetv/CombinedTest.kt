package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two providers' channels, films and series in one set of lists, on pretend providers. */
class CombinedTest {
    private val first = Account("http://one.tv", "kev", "pw1", "")
    private val second = Account("http://two.tv", "kev", "pw2", "")

    private fun lib(account: Account, vararg channels: Channel, films: List<Film> = emptyList()) = Library(
        listOf(Category("1", "UK | Sport"), Category("2", "UK | News")), channels.toList(),
        listOf(Category("f", "Films")), films, account = account,
    )

    private fun ch(id: String, name: String, cat: String = "1") = Channel(id, name, "", cat, "http://x/$id.ts", 7)

    @Test fun theOtherProvidersThingsAreMarkedAndAdded() {
        val merged = Combined.merge(
            lib(first, ch("10", "Sky Sports 1 HD"), films = listOf(Film("5", "Dune", "", "f", "http://x/5.mp4"))),
            listOf(second to lib(second, ch("10", "Sky Sports 1 FHD"), ch("11", "TNT Sports 1"), films = listOf(Film("5", "Dune", "", "f", "http://y/5.mp4"), Film("6", "Heat", "", "f", "http://y/6.mp4")))),
        )
        val m = Combined.mark(second)
        assertEquals(listOf("10", m + "10", m + "11"), merged.channels.map { it.id })
        // The same film from both is shown once, the one in use's.
        assertEquals(listOf("5", m + "6"), merged.films.map { it.id })
        // A category with a name already there has the provider's number after it.
        assertEquals(listOf("UK | Sport", "UK | News", "UK | Sport (2)", "UK | News (2)"), merged.liveCategories.map { it.name })
        assertEquals(first, merged.account)
    }

    @Test fun aChannelBothHaveIsOneRowWithTheOtherAsBackup() {
        val m = Combined.mark(second)
        val groups = Versions.group(listOf(ch("10", "Sky Sports 1 HD"), ch(m + "10", "Sky Sports 1 FHD"), ch(m + "11", "TNT Sports 1 HD")))
        val sky = groups.first { it.name == "Sky Sports 1" }
        // The one in use's own version comes first and is played first, whatever the other's picture.
        assertEquals(listOf("HD", "Backup FHD"), sky.versions.map { it.label })
        assertEquals("10", sky.id)
        assertEquals(0, sky.start)
        assertEquals("10", sky.lightest.id)
        // One only the other provider has isn't called a backup.
        assertEquals(listOf("HD"), groups.first { it.name == "TNT Sports 1" }.versions.map { it.label })
    }

    @Test fun guidesAndCatchUpAreAskedOfTheChannelsOwnProvider() {
        Combined.register(second, 3600)
        val m = Combined.mark(second)
        assertEquals(second to "11", Combined.which(m + "11"))
        assertNull(Combined.which("11"))
        assertEquals(first, Combined.accountFor(first, "11"))
        assertEquals(second, Combined.accountFor(first, m + "11"))
        assertEquals(3600, Combined.aheadFor(m + "11", 0))
        assertEquals(0, Combined.aheadFor("11", 0))
        val p = Programme("Match", "", 1_700_000_000_000, 1_700_003_600_000, "2023-11-14 22:13:20")
        assertTrue(Provider.catchupUrl(first, m + "11", p, 0).startsWith("http://two.tv/timeshift/kev/pw2/"))
        assertTrue(Provider.catchupUrl(first, m + "11", p, 0).endsWith("/11.ts"))
        assertTrue(Provider.catchupUrl(first, "11", p, 0).startsWith("http://one.tv/timeshift/kev/pw1/"))
    }

    @Test fun marksAreReadBack() {
        val m = Combined.mark(second)
        assertTrue(Combined.isOther(m + "abc"))
        assertFalse(Combined.isOther("abc"))
        assertFalse(Combined.isOther("free:abc"))
        assertEquals("abc", Combined.raw(m + "abc"))
        assertEquals(m, Combined.markOf(m + "abc"))
        // The same provider always has the same mark.
        assertEquals(m, Combined.mark(second.copy()))
        assertFalse(m == Combined.mark(first))
    }
}
