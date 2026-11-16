package art.kquinn.kuetv

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** A pretend film index and pretend add-ons on this machine. */
class AddOnsTest {
    private lateinit var server: HttpServer
    private var base = ""
    private val asked = ArrayList<String>()

    private fun HttpExchange.answer(code: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.write(bytes)
        close()
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val path = ex.requestURI.path
            synchronized(asked) { asked += path }
            val body = when (path) {
                "/index/catalog/movie/top/search=The Thing.json" -> """{"metas":[
                    {"id":"tt0084787","name":"The Thing","releaseInfo":"1982"},{"id":"tt0905372","name":"The Thing","releaseInfo":"2011"},{"id":"tt9","name":"The Thing Called Love","releaseInfo":"1993"}]}"""
                "/index/catalog/movie/top/search=Blade Runner 2049.json" -> """{"metas":[{"id":"tt1856101","name":"Blade Runner 2049","releaseInfo":"2017"}]}"""
                "/index/catalog/movie/top/search=A Film.json" -> """{"metas":[{"id":"tt111","name":"A Film","releaseInfo":"2024"}]}"""
                "/index/catalog/series/top/search=Slow Horses.json" -> """{"metas":[{"id":"tt5875444","name":"Slow Horses","releaseInfo":"2022-"}]}"""
                "/good/manifest.json" -> """{"id":"a","name":"Good Streams","resources":["catalog",{"name":"stream","types":["movie","series"],"idPrefixes":["tt"]}]}"""
                "/subs/manifest.json" -> """{"id":"b","name":"Subs","resources":["subtitles"]}"""
                "/useless/manifest.json" -> """{"id":"c","name":"Posters","resources":["catalog","meta"]}"""
                "/good/stream/movie/tt0905372.json" -> """{"streams":[
                    {"name":"X 4K","title":"The.Thing.2011.2160p.HDR.mkv","url":"http://cdn/4k"},
                    {"name":"X 1080p","title":"The.Thing.2011.1080p.BluRay.DTS-HD.mkv","url":"http://cdn/dts"},
                    {"name":"X 1080p","title":"The.Thing.2011.ITA.1080p.WEB.mkv","url":"http://cdn/ita"},
                    {"name":"X 720p","description":"The Thing 2011 720p WEB AAC","url":"http://cdn/720"},
                    {"name":"X","title":"The.Thing.2011.HDCAM.1080p.mkv","url":"http://cdn/cam"},
                    {"name":"X 1080p","title":"The.Thing.2011.1080p.WEB-DL.DD5.1.mkv","url":"http://cdn/best","behaviorHints":{"proxyHeaders":{"request":{"Referer":"http://x/"}}},
                     "subtitles":[{"url":"http://subs/fr.srt","lang":"fre"},{"url":"http://subs/own.srt","lang":"eng"}]},
                    {"name":"X 1080p","title":"The.Thing.2011.1080p.WEB-DL.second.mkv","url":"http://cdn/second"},
                    {"name":"torrent","infoHash":"abc"},{"name":"elsewhere","externalUrl":"http://site"}]}"""
                "/subs/subtitles/movie/tt0905372.json" -> """{"subtitles":[{"id":"1","url":"http://subs/de.srt","lang":"ger"},{"id":"2","url":"http://subs/spare.srt","lang":"eng"}]}"""
                "/good/stream/movie/tt111.json" -> """{"streams":[{"name":"torrent","infoHash":"abc"},{"name":"torrent 2","infoHash":"def","fileIdx":1}]}"""
                "/good/stream/series/tt5875444:2:3.json" -> """{"streams":[{"name":"ep","url":"https://cdn/s2e3"}]}"""
                else -> null
            }
            if (body == null) ex.answer(404, "{}") else ex.answer(200, body)
        }
        server.start()
        base = "http://127.0.0.1:" + server.address.port
        AddOns.index = "$base/index"
    }

    @After fun stop() = server.stop(0)

    @Test fun readsAFilmsTitleAndYearFromTheProvidersName() {
        assertEquals("The Thing" to 2011, AddOns.title("EN - The Thing 4K (2011)"))
        assertEquals("The Thing" to 2011, AddOns.title("The Thing [2011] FHD"))
        assertEquals("The Thing" to 1982, AddOns.title("UK| The Thing - 1982"))
        assertEquals("Blade Runner 2049" to 2017, AddOns.title("Blade Runner 2049 (2017)"))
        assertEquals("Blade Runner 2049" to null, AddOns.title("Blade Runner 2049"))
        assertEquals("2012" to 2009, AddOns.title("2012 (2009)"))
        assertEquals("Slow Horses" to null, AddOns.title("Slow Horses"))
    }

    @Test fun tidiesTheLinkHoweverItWasCopied() {
        assertEquals("https://a.example/x/manifest.json", AddOns.tidyLink(" stremio://a.example/x/manifest.json "))
        assertEquals("https://a.example/x/manifest.json", AddOns.tidyLink("a.example/x/"))
        assertEquals("http://a.example/manifest.json", AddOns.tidyLink("http://a.example/manifest.json"))
    }

    @Test fun addingReadsWhatAnAddOnGivesAndRefusesOnesWithNothingToUse() {
        assertEquals(AddOn("$base/good", "Good Streams", streams = true, subtitles = false), AddOns.read("$base/good/manifest.json"))
        assertEquals(AddOn("$base/subs", "Subs", streams = false, subtitles = true), AddOns.read("$base/subs"))
        try { AddOns.read("$base/useless"); fail("should be refused") } catch (e: AddOnProblem) { assertEquals("That add-on has no streams or subtitles, so KueTV has no use for it.", e.message) }
        try { AddOns.read("$base/nothing"); fail("should be refused") } catch (e: AddOnProblem) { assertEquals("That link didn't answer like an add-on. Check it and try again.", e.message) }
        val kept = AddOns.read("$base/good")
        assertEquals(kept, AddOn.fromJson(kept.toJson()))
    }

    @Test fun picksTheRightFilmByNameAndYear() {
        assertEquals("the remake, not the 1982 film of the same name", "tt0905372", AddOns.idFor("movie", "The Thing (2011)"))
        assertEquals("tt0084787", AddOns.idFor("movie", "The Thing HD (1982)"))
        assertEquals("a number that is part of the title", "tt1856101", AddOns.idFor("movie", "Blade Runner 2049"))
        assertEquals("a year written with no brackets", "tt111", AddOns.idFor("movie", "A Film 2024"))
        assertNull("the right name but the wrong year is not guessed at", AddOns.idFor("movie", "A Film (1999)"))
        assertNull(AddOns.idFor("movie", "No Such Film (2020)"))
    }

    private val good get() = AddOn("$base/good", "Good Streams", streams = true, subtitles = false)
    private val subs get() = AddOn("$base/subs", "Subs", streams = false, subtitles = true)

    @Test fun linesUpStreamsBestEnglishFirstAndLeavesOutWhatCantPlay() {
        val search = AddOns.streamsFor(listOf(good, subs), "movie", "The Thing (2011)")
        assertEquals(
            listOf("http://cdn/best", "http://cdn/second", "http://cdn/720", "http://cdn/dts", "http://cdn/4k", "magnet", "http://cdn/cam", "http://cdn/ita"),
            search.streams.map { if (it.url.startsWith("magnet:?xt=urn:btih:abc&tr=")) "magnet" else it.url },
        )
        assertEquals("the stream's own English subtitles", "http://subs/own.srt", search.streams[0].subtitle)
        assertEquals(mapOf("Referer" to "http://x/"), search.streams[0].headers)
        assertEquals("the subtitle add-on's English ones, where the stream has none", "http://subs/spare.srt", search.streams[1].subtitle)
    }

    @Test fun torrentsAreLinedUpTooWithTheFileTheAddOnNamed() {
        val search = AddOns.streamsFor(listOf(good), "movie", "A Film (2024)")
        assertEquals(2, search.streams.size)
        assertEquals(null, AddOns.readMagnet(search.streams[0].url).file)
        assertEquals(1, AddOns.readMagnet(search.streams[1].url).file)
        assertTrue(search.streams[1].url.startsWith("magnet:?xt=urn:btih:def&tr="))
    }

    @Test fun aTorrentsAddressCarriesWhatIsWantedAndGivesItBack() {
        val address = AddOns.magnet("ABC123", listOf("tracker:udp://one.example:80/announce", "dht:abc123"), null, 2, 3)
        assertTrue(address.startsWith("magnet:?xt=urn:btih:abc123&tr=udp%3A%2F%2Fone.example%3A80%2Fannounce&tr="))
        val read = AddOns.readMagnet(address)
        assertEquals(null, read.file)
        assertEquals(2, read.season)
        assertEquals(3, read.episode)
        assertFalse("KueTV's own notes are taken off before the torrent engine sees it", read.magnet.contains("x.kue"))
        assertTrue(read.magnet.startsWith("magnet:?xt=urn:btih:abc123&tr="))
    }

    @Test fun picksTheFilmOutOfATorrentsFiles() {
        val film = listOf("Film/sample.mkv", "Film/Film.1080p.mkv", "Film/readme.txt", "Film/extras.mp4")
        assertEquals(1, AddOns.pickFile(film, listOf(50, 4000, 1, 300), null, null, null))
        assertEquals("the one the add-on named, whatever its size", 3, AddOns.pickFile(film, listOf(50, 4000, 1, 300), 3, null, null))
        val season = listOf("Show/Show.S02E03.mkv", "Show/Show.S02E13.mkv", "Show/Show.S02E30.mkv", "Show/show.2x03.sample.mkv", "Show/Show.S12E03.mkv")
        assertEquals(0, AddOns.pickFile(season, listOf(900, 950, 990, 10, 999), null, 2, 3))
        assertEquals(1, AddOns.pickFile(season, listOf(900, 950, 990, 10, 999), null, 2, 13))
        assertNull("a whole season with no such episode: none, not the wrong one", AddOns.pickFile(season, listOf(900, 950, 990, 10, 999), null, 2, 9))
        assertEquals("one film file is taken to be the episode", 0, AddOns.pickFile(listOf("ep.mkv", "a.nfo"), listOf(700, 1), null, 2, 9))
        assertNull(AddOns.pickFile(listOf("a.txt", "b.jpg"), listOf(1, 2), null, null, null))
    }

    @Test fun aTorrentNobodySharesOrTooBigToHoldGoesBehind() {
        assertEquals(-10, AddOns.torrentScore("FILM 1080P \uD83D\uDC64 120 \uD83D\uDCBE 2.1 GB"))
        assertEquals(-310, AddOns.torrentScore("FILM 1080P \uD83D\uDC64 1 \uD83D\uDCBE 900 MB"))
        assertEquals(-160, AddOns.torrentScore("FILM 2160P \uD83D\uDC64 80 \uD83D\uDCBE 14.3 GB"))
    }

    @Test fun asksAboutAnEpisodeBySeasonAndNumber() {
        val search = AddOns.streamsFor(listOf(good), "series", "Slow Horses", season = 2, episode = 3)
        assertEquals(listOf("https://cdn/s2e3"), search.streams.map { it.url })
        assertNull(search.streams[0].subtitle)
    }

    @Test fun nothingIsAskedWhenNoAddOnGivesStreamsOrTheFilmCantBeTold() {
        assertTrue(AddOns.streamsFor(listOf(subs), "movie", "The Thing (2011)").streams.isEmpty())
        val unknown = AddOns.streamsFor(listOf(good), "movie", "No Such Film (2020)")
        assertTrue(unknown.streams.isEmpty())
        assertFalse(synchronized(asked) { asked.any { it.startsWith("/good/stream/") } })
    }
}
