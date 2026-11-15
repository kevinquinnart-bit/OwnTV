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
                q["action"] == null -> """{"user_info":{"auth":1,"status":"Active"},"server_info":{}}"""
                q["action"] == "get_live_categories" -> """[{"category_id":"1","category_name":"UK","parent_id":0},{"category_id":"2","category_name":"Sport","parent_id":0}]"""
                q["action"] == "get_live_streams" -> """[
                    {"num":1,"name":" BBC One ","stream_id":101,"stream_icon":"http://x/l.png","category_id":"1","tv_archive":1,"tv_archive_duration":"7","category_ids":[1]},
                    {"num":2,"name":"Sky Sports, Main Event","stream_id":"102","stream_icon":null,"category_id":"2","tv_archive":0,"tv_archive_duration":0},
                    {"num":3,"name":"BBC One copy","stream_id":101,"category_id":"1"}]"""
                q["action"] == "get_vod_categories" -> """[{"category_id":"9","category_name":"Drama"}]"""
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
