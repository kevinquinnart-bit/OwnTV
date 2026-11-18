package art.kquinn.kuetv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Batch 10, look and engine: light look, highlight colour, glass, night mode, frame rate, DNS, protected channels, portals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LookEngineTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun theLookChangesAndGoesBack() {
        Looks.reset(context)
        val darkPage = Looks.page
        val darkInk = Looks.ink
        Looks.set(context, "theme", 1)
        assertTrue(Looks.light)
        assertNotEquals(darkPage, Looks.page)
        assertNotEquals(darkInk, Looks.ink)
        // Highlight words are darker than the highlight itself on the light look, so they can be read.
        assertTrue(Looks.accentInk.red + Looks.accentInk.green < Looks.accentColour.red + Looks.accentColour.green)
        Looks.set(context, "accent", 1)
        assertEquals("Teal", Looks.accents[Looks.accent])
        Looks.set(context, "glass", 2)
        assertTrue("glass panels are see-through", Looks.panel.alpha < 1f)
        Looks.set(context, "outline", 2)
        assertEquals(4, Looks.outlineWidth)
        // Kept: read back as the app opens.
        Looks.load(context)
        assertEquals(1, Looks.theme)
        Looks.reset(context)
        assertEquals(0, Looks.theme)
        assertEquals(1f, Looks.panel.alpha)
        assertEquals(0, Looks.outlineWidth)
    }

    @Test fun nightModeSqueezesLoudAndLiftsQuiet() {
        assertTrue("quiet lifted", NightProcessor.gainFor(0.05f) > 1.5f)
        assertTrue("loud brought down", NightProcessor.gainFor(1f) < 0.5f)
        assertTrue("never past full", NightProcessor.gainFor(0.9f) * 0.9f <= 0.95f)

        val p = NightProcessor { true }
        p.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        fun run(amplitude: Int): Int {
            val frames = 48_000 / 2
            val buf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
            repeat(frames) { i ->
                val v = (if (i % 2 == 0) amplitude else -amplitude).toShort()
                buf.putShort(v)
                buf.putShort(v)
            }
            buf.flip()
            p.queueInput(buf)
            val out = p.output.order(ByteOrder.nativeOrder())
            var last = 0
            while (out.remaining() >= 2) last = abs(out.short.toInt())
            return last
        }
        val loud = run(32_000)
        run(1_600)
        // Once it has let go after the loud part.
        val quiet = run(1_600)
        // In: twenty times louder. Out: far less.
        assertTrue("loud $loud quiet $quiet", loud.toFloat() / quiet < 5f)
        assertTrue(quiet > 1_600)

        val off = NightProcessor { false }
        off.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        assertTrue("does nothing while off", !off.isActive)
    }

    @Test fun theScreenIsAskedForTheFilmsOwnRate() {
        val uhd = { id: Int, rate: Float -> FrameRate.Mode(id, 3840, 2160, rate) }
        val modes = listOf(uhd(1, 60f), uhd(2, 50f), uhd(3, 23.976f), uhd(4, 24f), uhd(5, 59.94f), FrameRate.Mode(6, 1920, 1080, 50f))
        val now = uhd(1, 60f)
        assertEquals(3, FrameRate.pick(now, modes, 23.976f)?.id)
        assertEquals(4, FrameRate.pick(now, modes, 24f)?.id)
        assertEquals("25 a second plays at 50", 2, FrameRate.pick(now, modes, 25f)?.id)
        assertEquals(2, FrameRate.pick(now, modes, 50f)?.id)
        assertNull("already suits it", FrameRate.pick(now, modes, 30f))
        assertEquals(5, FrameRate.pick(now, modes, 29.97f)?.id)
        assertNull("no rate to give", FrameRate.pick(now, modes, 0f))
    }

    @Test fun theDnsChoiceIsKept() {
        KueDns.set(context, 1)
        assertEquals(1, KueDns.chosen)
        KueDns.load(context)
        assertEquals("Cloudflare (1.1.1.1)", KueDns.names[KueDns.chosen])
        KueDns.set(context, 0)
        assertTrue(KueDns.lookup("localhost").isNotEmpty())
    }

    @Test fun protectedChannelsKeepTheirKeys() {
        val text = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123456789abcdef0123456789abcdef:fedcba9876543210fedcba9876543210
            #EXTINF:-1 group-title="Sport",Locked One
            https://cdn.example/locked/manifest.mpd
            #EXTINF:-1 group-title="Sport",Open One
            https://cdn.example/open/index.m3u8
            #EXTINF:-1 group-title="Sport",Widevine One
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://licence.example/wv|X-Key=abc&Origin=https%3A%2F%2Fsite
            https://cdn.example/wv/manifest.mpd
        """.trimIndent()
        val lib = Provider.parsePlaylist(BufferedReader(StringReader(text)))
        assertEquals(3, lib.channels.size)
        val clear = Drm.of("https://cdn.example/locked/manifest.mpd")
        assertEquals("clearkey", clear?.type)
        assertNull("an open channel has no key", Drm.of("https://cdn.example/open/index.m3u8"))
        assertEquals("widevine", Drm.of("https://cdn.example/wv/manifest.mpd")?.type)
        val json = Drm.clearKeyJson(clear!!.key)!!
        assertTrue(json, json.contains("\"kid\":\"ASNFZ4mrze8BI0VniavN7w\""))
        assertTrue(json.contains("\"kty\":\"oct\""))
        val (address, headers) = Drm.licenceParts("https://licence.example/wv|X-Key=abc&Origin=https%3A%2F%2Fsite")
        assertEquals("https://licence.example/wv", address)
        assertEquals("abc", headers["X-Key"])
        assertEquals("https://site", headers["Origin"])
        assertNull("a licence address isn't pairs", Drm.clearKeyJson("https://licence.example/ck"))
    }

    @Test fun aPortalAndMacAreSetUp() {
        val spec = AccountStore.tidy("http://portal.example:8080/c/", "00-1a-79-aa-bb-cc", "", "")
        assertEquals("stalker:http://portal.example:8080/c/|00:1A:79:AA:BB:CC", spec.playlist)
        assertTrue(spec.isSet)
        val together = AccountStore.tidy("", "", "", "http://portal.example/c/ 00:1A:79:00:00:01")
        assertEquals("stalker:http://portal.example/c/|00:1A:79:00:00:01", together.playlist)
        // An ordinary login is untouched.
        assertTrue(AccountStore.tidy("http://tv.example", "kevin", "secret", "").isLogin)
        assertEquals(listOf("http://portal.example:8080/portal.php", "http://portal.example:8080/stalker_portal/server/load.php", "http://portal.example:8080/c/portal.php"), Stalker.apis("http://portal.example:8080/c/"))
        assertEquals(listOf("http://p.example/stalker_portal/server/load.php"), Stalker.apis("http://p.example/stalker_portal/c/"))
        assertEquals("http://x/1.ts", Stalker.addressIn("ffmpeg http://x/1.ts"))
    }

    @Test fun aPortalsChannelsLoadAndPlay() {
        val asked = ArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/portal.php") { ex ->
            val q = ex.requestURI.query.orEmpty()
            asked += q
            val authorised = ex.requestHeaders.getFirst("Authorization") == "Bearer tok1"
            val macOk = ex.requestHeaders.getFirst("Cookie")?.contains("mac=00:1A:79:AA:BB:CC") == true
            val body = when {
                !macOk -> "{\"js\":{}}"
                "action=handshake" in q -> "{\"js\":{\"token\":\"tok1\"}}"
                !authorised -> "{\"js\":[]}"
                "action=get_profile" in q -> "{\"js\":{\"id\":\"1\"}}"
                "action=get_genres" in q -> "{\"js\":[{\"id\":\"*\",\"title\":\"All\"},{\"id\":\"5\",\"title\":\"UK Sport\"}]}"
                "action=get_all_channels" in q -> "{\"js\":{\"data\":[{\"id\":\"77\",\"name\":\"Sport 1\",\"cmd\":\"ffmpeg http://localhost/ch/77\",\"logo\":\"\",\"tv_genre_id\":\"5\"}]}}"
                "action=create_link" in q -> "{\"js\":{\"cmd\":\"ffmpeg http://stream.example/live/77.ts?play_token=abc\"}}"
                else -> "{\"js\":null}"
            }.toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val portal = "http://127.0.0.1:" + server.address.port + "/c/"
            val account = AccountStore.tidy(portal, "00:1A:79:AA:BB:CC", "", "")
            Provider.reach(account)
            val lib = Provider.load(account)
            assertEquals(listOf("UK Sport"), lib.liveCategories.map { it.name })
            assertEquals("Sport 1", lib.channels.single().name)
            val url = lib.channels.single().url
            assertTrue(Stalker.isLink(url))
            assertEquals("http://stream.example/live/77.ts?play_token=abc", Stalker.resolve(url))
            assertTrue(asked.any { "cmd=ffmpeg" in it || "cmd=ffmpeg%20" in it })

            val wrong = AccountStore.tidy(portal, "00:1A:79:00:00:09", "", "")
            val problem = runCatching { Provider.reach(wrong) }.exceptionOrNull()
            assertNotNull(problem)
            assertEquals("The portal didn't accept that MAC address.", problem!!.message)
        } finally {
            server.stop(0)
        }
    }
}
