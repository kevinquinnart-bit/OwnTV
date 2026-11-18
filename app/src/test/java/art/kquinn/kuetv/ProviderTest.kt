package art.kquinn.kuetv

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.URLEncoder

/** A pretend provider on this machine, answering the way real ones do, with the awkward bits real ones have. */
class ProviderTest {
    private lateinit var server: HttpServer
    private var base = ""

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/player_api.php") { ex ->
            val q = ex.requestURI.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            val body = when {
                q["password"] != "p w/1" -> """{"user_info":{"auth":0}}"""
                q["action"] == null -> """{"user_info":{"auth":1,"status":"Active"},"server_info":{"timezone":"Europe/Berlin","timestamp_now":1794772803,"time_now":"2026-11-15 21:00:01"}}"""
                q["action"] == "get_simple_data_table" && q["stream_id"] == "101" -> """{"epg_listings":[
                    {"id":"2","title":"VGhlIE5ld3M=","description":"SGVhZGxpbmVzLCB0aGVuIHdlYXRoZXIu","start":"2026-11-15 19:00:00","end":"2026-11-15 19:30:00","start_timestamp":"1794765600","stop_timestamp":"1794767400","has_archive":1},
                    {"id":"1","title":"RWFybHkgU2hvdw==","description":"","start":"2026-11-15 18:00:00","end":"2026-11-15 19:00:00","start_timestamp":1794762000,"stop_timestamp":1794765600,"has_archive":1},
                    {"id":"3","title":"QnJva2Vu","start":"x","start_timestamp":"5","stop_timestamp":"5"}]}"""
                q["action"] == "get_simple_data_table" -> """{"epg_listings":[]}"""
                q["action"] == "get_live_categories" -> """[{"category_id":"1","category_name":"UK","parent_id":0},{"category_id":"2","category_name":"Sport","parent_id":0}]"""
                q["action"] == "get_live_streams" -> """[
                    {"num":1,"name":" BBC One ","stream_id":101,"stream_icon":"http://x/l.png","category_id":"1","tv_archive":1,"tv_archive_duration":"7","category_ids":[1]},
                    {"num":2,"name":"Sky Sports, Main Event","stream_id":"102","stream_icon":null,"category_id":"2","tv_archive":0,"tv_archive_duration":0},
                    {"num":3,"name":"BBC One copy","stream_id":101,"category_id":"1"}]"""
                q["action"] == "get_series_categories" -> """[{"category_id":"5","category_name":"Crime"}]"""
                q["action"] == "get_series" -> """[{"num":1,"name":"A Show","series_id":77,"cover":"http://x/c.jpg","category_id":"5","backdrop_path":["a"],"plot":"Spies, mostly.","genre":"Crime, Drama","releaseDate":"2022-04-01","rating":"8"},{"name":"B Show","series_id":78,"cover":"","category_id":"5"}]"""
                q["action"] == "get_series_info" && q["series_id"] == "77" -> """{"seasons":[],"info":{"name":"A Show"},"episodes":{
                    "2":[{"id":"903","episode_num":1,"title":"A Show - S02E01","container_extension":"mkv","season":2,"info":{"duration":"00:45:00","movie_image":"http://x/e3.jpg","air_date":"2021-03-12"}}],
                    "1":[{"id":"902","episode_num":"2","title":"Two","container_extension":"mp4","season":1},{"id":"901","episode_num":1,"title":"One","container_extension":"","season":1}]}}"""
                q["action"] == "get_series_info" && q["series_id"] == "78" -> """{"episodes":[[{"id":"911","episode_num":1,"title":"Only","container_extension":"mp4","season":1}]]}"""
                q["action"] == "get_vod_categories" -> """[{"category_id":"9","category_name":"Drama"}]"""
                q["action"] == "get_vod_info" && q["vod_id"] == "555" -> """{"info":{"plot":" A quiet film. ","genre":"Drama","releasedate":"2024-03-01","rating":"7.123","duration":"01:52:10","backdrop_path":["x"]},"movie_data":{"stream_id":555}}"""
                q["action"] == "get_vod_info" && q["vod_id"] == "556" -> """{"info":{"description":"Only this.","rating":0,"duration_secs":2700,"duration":"00:45:00"}}"""
                q["action"] == "get_vod_info" -> """{"info":[]}"""
                q["action"] == "get_vod_streams" -> """[{"name":"A Film (2024)","stream_id":555,"stream_icon":"http://x/p.jpg","category_id":"9","container_extension":"mkv","rating_5based":3.5}]"""
                else -> "[]"
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        base = "http://127.0.0.1:" + server.address.port
    }

    @After fun stop() = server.stop(0)

    @Test fun saysWhatAFilmOrSeriesIsAbout() {
        val account = Account(base, "kev", "p w/1", "")
        val film = Provider.filmAbout(account, "555")
        assertEquals("A quiet film.", film.plot)
        assertEquals("2024 · Drama · 7.1 · 1 h 52 min", film.line)
        val bare = Provider.filmAbout(account, "556")
        assertEquals("Only this.", bare.plot)
        assertEquals("45 min", bare.line)
        assertTrue("a provider with nothing to say gives nothing, not a failure", Provider.filmAbout(account, "999").isEmpty)
        val shows = Provider.load(account).shows
        assertEquals("Spies, mostly.", shows[0].about.plot)
        assertEquals("2022 · Crime, Drama · 8.0", shows[0].about.line)
        assertTrue(shows[1].about.isEmpty)
    }

    @Test fun loginLoadsChannelsFilmsAndCatchUp() {
        val lib = Provider.load(Account(base, "kev", "p w/1", ""))
        assertEquals(listOf("UK", "Sport"), lib.liveCategories.map { it.name })
        assertEquals("the repeated id is listed once", 2, lib.channels.size)
        val bbc = lib.channels[0]
        assertEquals("BBC One", bbc.name)
        assertEquals(7, bbc.catchupDays)
        assertEquals("$base/live/kev/p%20w%2F1/101.ts", bbc.url)
        assertEquals(0, lib.channels[1].catchupDays)
        assertEquals("", lib.channels[1].logo)
        assertEquals("$base/movie/kev/p%20w%2F1/555.mkv", lib.films.single().url)
        assertEquals("Drama", lib.filmCategories.single().name)
    }

    @Test fun guideAndCatchUpAddresses() {
        val account = Account(base, "kev", "p w/1", "")
        val lib = Provider.load(account)
        assertEquals("the provider's clock is an hour ahead of world time", 3600, lib.serverAheadSec)
        val guide = Provider.guide(account, "101")
        assertEquals(listOf("Early Show", "The News"), guide.map { it.title })
        assertEquals("Headlines, then weather.", guide[1].description)
        assertEquals(1794765600000L, guide[1].startMs)
        // A programme: the provider's own wording of the start, 30 minutes plus 5.
        assertEquals("$base/timeshift/kev/p%20w%2F1/35/2026-11-15:19-00/101.ts", Provider.catchupUrl(account, "101", guide[1], lib.serverAheadSec))
        // A picked moment: 18:00 world time is 19:00 on the provider's clock.
        assertEquals("$base/timeshift/kev/p%20w%2F1/240/2026-11-15:19-00/101.ts", Provider.catchupUrlAt(account, "101", 1794765600000L, 240, lib.serverAheadSec))
        assertTrue(Provider.guide(account, "102").isEmpty())
    }

    @Test fun seriesAndEpisodes() {
        val account = Account(base, "kev", "p w/1", "")
        val lib = Provider.load(account)
        assertEquals(listOf("A Show", "B Show"), lib.shows.map { it.name })
        assertEquals("Crime", lib.showCategories.single().name)
        val eps = Provider.episodes(account, "77")
        assertEquals("season then episode order", listOf("901", "902", "903"), eps.map { it.id })
        assertEquals(listOf(1, 1, 2), eps.map { it.season })
        assertEquals("$base/series/kev/p%20w%2F1/901.mp4", eps[0].url)
        assertEquals("$base/series/kev/p%20w%2F1/903.mkv", eps[2].url)
        assertEquals("the list-of-lists shape", "Only", Provider.episodes(account, "78").single().title)
        assertEquals("an episode's picture", "http://x/e3.jpg", eps[2].image)
        assertEquals("when it first aired", "12 Mar 2021", EpisodeOrder.airedWords(eps[2].aired))
        assertEquals("none given", "", eps[0].aired)
    }

    @Test fun searchMatchesEveryWordInAnyOrder() {
        val lib = Provider.load(Account(base, "kev", "p w/1", ""))
        assertEquals(listOf("BBC One"), Finder.search(lib, "one BBC").channels.map { it.name })
        assertEquals(listOf("Sky Sports, Main Event"), Finder.search(lib, "  sports  ").channels.map { it.name })
        assertEquals(listOf("A Film (2024)"), Finder.search(lib, "film").films.map { it.name })
        assertEquals(listOf("A Show", "B Show"), Finder.search(lib, "SHOW").shows.map { it.name })
        assertTrue("one letter is too little to search on", Finder.search(lib, "b").isEmpty)
        assertTrue(Finder.search(lib, "zzz").isEmpty)
    }

    @Test fun wrongPasswordSaysSo() {
        try {
            Provider.load(Account(base, "kev", "nope", ""))
            fail("should have refused")
        } catch (e: ProviderProblem) {
            assertEquals("Your provider refused the username or password.", e.message)
        }
    }

    @Test fun deadServerSaysSo() {
        try {
            Provider.load(Account("http://127.0.0.1:1", "a", "b", ""))
            fail("should have failed")
        } catch (e: ProviderProblem) {
            assertTrue(e.message!!.startsWith("Couldn't reach your provider"))
        }
    }

    @Test fun playlistLinkWithALoginBecomesALogin() {
        val a = AccountStore.tidy("", "", "", " http://tv.example.com:8080/get.php?username=kev&password=" + URLEncoder.encode("p w/1", "UTF-8") + "&type=m3u_plus ")
        assertEquals(Account("http://tv.example.com:8080", "kev", "p w/1", ""), a)
        assertTrue(a.isLogin)
    }

    @Test fun typedServerIsTidied() {
        assertEquals(Account("http://tv.example.com:8080", "kev", "pw", ""), AccountStore.tidy(" tv.example.com:8080/ ", " kev ", "pw ", ""))
        assertEquals("https://tv.example.com", AccountStore.tidy("https://tv.example.com/player_api.php?x=1", "a", "b", "").server)
        assertTrue(!AccountStore.tidy("", "", "", "").isSet)
        assertEquals("http://x.example/list.m3u", AccountStore.tidy("", "", "", "http://x.example/list.m3u").playlist)
    }

    @Test fun playlistTextIsRead() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1" tvg-logo="http://x/l.png" group-title="UK" catchup="default" catchup-days="3",BBC One
            http://h/live/u/p/1.ts
            #EXTINF:-1 tvg-id="" group-title="Sport",Sky Sports, Main Event
            #EXTVLCOPT:http-user-agent=x
            http://h/live/u/p/2.ts
            #EXTINF:-1 group-title="Films",A Film
            http://h/movie/u/p/9.mkv
            #EXTINF:-1 group-title="Shows",S01E01
            http://h/series/u/p/7.mkv
            #EXTINF:-1,No group
            http://h/3
        """.trimIndent()
        val lib = Provider.parsePlaylist(BufferedReader(StringReader(text)))
        assertEquals(listOf("BBC One", "Sky Sports, Main Event", "No group"), lib.channels.map { it.name })
        assertEquals(listOf(3, 0, 0), lib.channels.map { it.catchupDays })
        assertEquals(listOf("UK", "Sport", "Other"), lib.liveCategories.map { it.name })
        assertEquals("A Film", lib.films.single().name)
        assertEquals("http://h/live/u/p/2.ts", lib.channels[1].url)
    }
}
