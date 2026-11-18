package art.kquinn.kuetv

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetSocketAddress

/** Batch 4, Your things: profiles keep their own things, history, saved providers, and the backup in Google Drive. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class YourThingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun eachProfileKeepsItsOwnFavouritesHistoryAndWatched() {
        Favourites(context).toggle("film:1")
        Watched.set(context, "http://f/1.mp4", true)
        History.add(context, History.FILM, "One", "http://f/1.mp4", "")
        val kids = Profiles.add(context, "Kids", kids = true)
        Profiles.switchTo(context, kids.id)
        assertTrue("a new profile starts with no favourites", Favourites(context).keys.isEmpty())
        assertFalse("…no watched marks", Watched.isWatched(context, "http://f/1.mp4"))
        assertTrue("…and no history", History.all(context).isEmpty())
        Profiles.switchTo(context, "")
        assertEquals("Main's are where they were", setOf("film:1"), Favourites(context).keys)
        assertTrue(Watched.isWatched(context, "http://f/1.mp4"))
        Profiles.remove(context, kids.id)
        assertEquals(1, Profiles.all(context).size)
    }

    @Test fun kidsProfilesHideAdultCategories() {
        assertTrue(Profiles.isAdult("XXX | Adults"))
        assertTrue(Profiles.isAdult("FOR ADULTS 18+"))
        assertFalse(Profiles.isAdult("UK | Kids"))
        assertFalse(Profiles.isAdult("Sussex News"))
    }

    @Test fun historyIsNewestFirstWithEachOnce() {
        History.clear(context)
        History.add(context, History.LIVE, "BBC One", "http://tv/1.ts", null)
        History.add(context, History.FILM, "A Film", "http://f/2.mp4", "p.jpg")
        History.add(context, History.LIVE, "BBC One", "http://tv/1.ts", null)
        assertEquals(listOf("BBC One", "A Film"), History.all(context).map { it.name })
    }

    @Test fun providersAreKeptToSwitchBetween() {
        val a = Account("http://tv.example.com:8080", "kevin", "pw", "")
        val b = Account("", "", "", "http://lists.example.org/get.m3u")
        Providers.keep(context, a)
        Providers.keep(context, b)
        Providers.keep(context, a)
        assertEquals(listOf(a, b), Providers.all(context))
        assertEquals("tv.example.com · kevin", Providers.label(a))
        assertEquals("Playlist · lists.example.org", Providers.label(b))
        Providers.remove(context, b)
        assertEquals(listOf(a), Providers.all(context))
    }

    @Test fun theBackupHoldsEveryPartAndIsDueAsChosen() {
        val parts = mapOf("favourites" to "film:1\nlive:2", "watched" to "123\n456")
        assertEquals(parts, Backup.unpack(Backup.pack(parts)))
        assertNull(Backup.unpack("not a backup"))
        val day = 24 * 3_600_000L
        assertTrue("every time KueTV opens", Backup.dueAfter(0, System.currentTimeMillis(), System.currentTimeMillis()))
        assertFalse("daily, backed up an hour ago", Backup.dueAfter(1, 10 * day - 3_600_000L, 10 * day))
        assertTrue("weekly, backed up eight days ago", Backup.dueAfter(2, 2 * day, 10 * day))
        assertFalse("never", Backup.dueAfter(3, 0, 10 * day))
    }

    @Test fun watchedMarksGoAndComeBack() {
        Watched.set(context, "http://f/7.mp4", true)
        val out = Watched.export(context)
        Watched.set(context, "http://f/7.mp4", false)
        Watched.import(context, out)
        assertTrue(Watched.isWatched(context, "http://f/7.mp4"))
    }

    /** A pretend Google Drive: the backup is written, written over, and read back. */
    @Test fun theBackupGoesToDriveAndComesBack() {
        val files = HashMap<String, String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun com.sun.net.httpserver.HttpExchange.answer(code: Int, body: String) {
            val bytes = body.toByteArray()
            sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) responseBody.write(bytes)
            close()
        }
        server.createContext("/drive/v3/files") { ex ->
            val q = java.net.URLDecoder.decode(ex.requestURI.rawQuery.orEmpty(), "UTF-8")
            val path = ex.requestURI.path
            when {
                path.startsWith("/drive/v3/files/") && q.contains("alt=media") -> ex.answer(200, files[path.removePrefix("/drive/v3/files/")].orEmpty())
                q.contains("mimeType = 'application/vnd.google-apps.folder'") -> ex.answer(200, """{"files":[{"id":"folder-1"}]}""")
                q.contains("name = 'KueTV backup.json'") -> ex.answer(200, if (files.isEmpty()) """{"files":[]}""" else """{"files":[{"id":"b1"}]}""")
                else -> ex.answer(404, "{}")
            }
        }
        server.createContext("/upload/drive/v3/files") { ex ->
            val body = String(ex.requestBody.readBytes())
            when {
                ex.requestMethod == "POST" -> { files["b1"] = body.substringAfter("{\"version\"").let { "{\"version\"" + it.substringBeforeLast("}") + "}" }; ex.answer(200, """{"id":"b1"}""") }
                ex.requestMethod == "PATCH" && ex.requestURI.path.endsWith("/b1") -> { files["b1"] = body; ex.answer(200, """{"id":"b1"}""") }
                else -> ex.answer(404, "{}")
            }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:" + server.address.port
            val kept = hashMapOf("refresh" to "r", "access" to "a", "expires" to (System.currentTimeMillis() + 3_600_000L).toString())
            val keep = object : Keep {
                override fun get(key: String): String? = kept[key]
                override fun put(key: String, value: String?) { if (value == null) kept.remove(key) else kept[key] = value }
            }
            val drive = Drive(keep, "id", "secret", auth = base, api = base)
            assertNull("no backup yet", drive.getText(Backup.FILE))
            drive.putText(Backup.FILE, Backup.pack(mapOf("favourites" to "film:1")))
            assertEquals(mapOf("favourites" to "film:1"), Backup.unpack(drive.getText(Backup.FILE)!!))
            drive.putText(Backup.FILE, Backup.pack(mapOf("favourites" to "film:2")))
            assertEquals("written over, not added again", mapOf("favourites" to "film:2"), Backup.unpack(drive.getText(Backup.FILE)!!))
        } finally {
            server.stop(0)
        }
    }
}
