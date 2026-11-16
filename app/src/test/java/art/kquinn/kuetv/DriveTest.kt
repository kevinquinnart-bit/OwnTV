package art.kquinn.kuetv

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress

/** A pretend Google on this machine: the sign-in by code, the recordings folder, and a file sent in pieces. */
class DriveTest {
    private lateinit var server: HttpServer
    private var base = ""
    private val kept = HashMap<String, String>()
    private val keep = object : Keep {
        override fun get(key: String): String? = kept[key]
        override fun put(key: String, value: String?) { if (value == null) kept.remove(key) else kept[key] = value }
    }

    // What the pretend Google holds and how it has been told to behave.
    private var approved = false
    private var refused = false
    private var grantEnded = false
    private var folderMade = false
    private var renewals = 0
    private var sessions = 0
    private val received = ByteArrayOutputStream()
    private var uploadedName = ""
    private var total = -1L
    private var dropAfterPieces = Int.MAX_VALUE
    private var pieces = 0
    private val binned = ArrayList<String>()

    private fun HttpExchange.answer(code: Int, body: String, headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> responseHeaders.add(k, v) }
        val bytes = body.toByteArray()
        if (bytes.isEmpty()) sendResponseHeaders(code, -1) else { sendResponseHeaders(code, bytes.size.toLong()); responseBody.write(bytes) }
        close()
    }

    private fun form(ex: HttpExchange): Map<String, String> = String(ex.requestBody.readBytes()).split('&').filter { it.contains('=') }
        .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/device/code") { ex ->
            val f = form(ex)
            if (f["client_id"] != "id-1" || f["scope"] != "https://www.googleapis.com/auth/drive.file") ex.answer(401, """{"error":"invalid_client"}""")
            else ex.answer(200, """{"device_code":"dev-1","user_code":"ABCD-EFGH","verification_url":"https://www.google.com/device","expires_in":1800,"interval":5}""")
        }
        server.createContext("/token") { ex ->
            val f = form(ex)
            when {
                f["client_secret"] != "secret-1" -> ex.answer(401, """{"error":"invalid_client"}""")
                f["grant_type"] == "refresh_token" && grantEnded -> ex.answer(400, """{"error":"invalid_grant"}""")
                f["grant_type"] == "refresh_token" && f["refresh_token"] == "refresh-1" -> { renewals++; ex.answer(200, """{"access_token":"access-$renewals","expires_in":3599}""") }
                f["device_code"] != "dev-1" -> ex.answer(400, """{"error":"invalid_grant"}""")
                refused -> ex.answer(403, """{"error":"access_denied"}""")
                !approved -> ex.answer(428, """{"error":"authorization_pending"}""")
                else -> ex.answer(200, """{"access_token":"access-0","refresh_token":"refresh-1","expires_in":3599}""")
            }
        }
        server.createContext("/drive/v3/about") { ex -> ex.answer(200, """{"user":{"emailAddress":"films@example.com"}}""") }
        server.createContext("/drive/v3/files") { ex ->
            val signed = ex.requestHeaders.getFirst("Authorization").orEmpty().startsWith("Bearer access-")
            val q = java.net.URLDecoder.decode(ex.requestURI.rawQuery.orEmpty(), "UTF-8")
            when {
                !signed -> ex.answer(401, "{}")
                ex.requestMethod == "PATCH" -> {
                    val wanted = String(ex.requestBody.readBytes()).contains("\"trashed\":true")
                    if (wanted && ex.requestURI.path == "/drive/v3/files/f1") { binned += "f1"; ex.answer(200, "{}") } else ex.answer(404, "{}")
                }
                ex.requestMethod == "POST" -> { folderMade = true; ex.answer(200, """{"id":"folder-1"}""") }
                q.contains("mimeType = 'application/vnd.google-apps.folder'") -> ex.answer(200, if (folderMade) """{"files":[{"id":"folder-1"}]}""" else """{"files":[]}""")
                q.contains("'folder-1' in parents") -> ex.answer(200, """{"files":[{"id":"f2","name":"BBC One 2026-11-16 21.00.00.ts","size":"2147483648"},{"id":"f1","name":"Old.ts"},{"name":"no id"}]}""")
                else -> ex.answer(404, "{}")
            }
        }
        server.createContext("/upload/drive/v3/files") { ex ->
            val text = String(ex.requestBody.readBytes())
            if (!text.contains("\"folder-1\"")) { ex.answer(404, "{}"); return@createContext }
            sessions++
            received.reset()
            uploadedName = Regex("\"name\":\"([^\"]*)\"").find(text)?.groupValues?.get(1).orEmpty()
            total = ex.requestHeaders.getFirst("X-Upload-Content-Length").toLong()
            ex.answer(200, "", mapOf("Location" to "$base/session/$sessions"))
        }
        server.createContext("/session/") { ex ->
            val range = ex.requestHeaders.getFirst("Content-Range").orEmpty()
            val body = ex.requestBody.readBytes()
            if (ex.requestURI.path != "/session/$sessions") { ex.answer(404, "{}"); return@createContext }
            if (!range.startsWith("bytes */")) {
                if (++pieces > dropAfterPieces) { ex.answer(503, "{}"); return@createContext }
                val from = range.removePrefix("bytes ").substringBefore('-').toLong()
                if (from != received.size().toLong()) { ex.answer(400, "{}"); return@createContext }
                received.write(body)
            }
            if (received.size().toLong() == total) ex.answer(200, """{"id":"new-1"}""")
            else ex.answer(308, "", if (received.size() == 0) emptyMap() else mapOf("Range" to "bytes=0-" + (received.size() - 1)))
        }
        server.start()
        base = "http://127.0.0.1:" + server.address.port
    }

    @After fun stop() = server.stop(0)

    private fun drive(secret: String = "secret-1") = Drive(keep, "id-1", secret, auth = base, api = base)

    private fun signIn(): Drive {
        val drive = drive()
        approved = true
        assertTrue(drive.checkSignIn(drive.beginSignIn()))
        return drive
    }

    @Test fun signInWaitsForThePhoneThenRemembersTheAccount() {
        val drive = drive()
        assertFalse(drive.signedIn)
        val code = drive.beginSignIn()
        assertEquals("ABCD-EFGH", code.userCode)
        assertEquals("https://www.google.com/device", code.url)
        assertFalse("still waiting for the phone", drive.checkSignIn(code))
        assertFalse(drive.signedIn)
        approved = true
        assertTrue(drive.checkSignIn(code))
        assertTrue(drive.signedIn)
        assertEquals("films@example.com", drive.email)
        assertTrue("a second copy of the app's Drive sees the same sign-in", drive().signedIn)
        drive.signOut()
        assertFalse(drive.signedIn)
    }

    @Test fun googleNotAnsweringIsToldApartFromGoogleSayingNo() {
        val code = drive().beginSignIn()
        // The same Drive, but pointed at an address where nothing answers.
        val cutOff = Drive(keep, "id-1", "secret-1", auth = "http://127.0.0.1:1", api = "http://127.0.0.1:1")
        try { cutOff.checkSignIn(code); fail("nothing was there to answer") } catch (e: DriveProblem) { assertTrue("the sign-in screen keeps waiting on this", e.offline) }
    }

    @Test fun aRefusalOnThePhoneIsSaidPlainly() {
        val drive = drive()
        val code = drive.beginSignIn()
        refused = true
        try { drive.checkSignIn(code); fail("should have been refused") } catch (e: DriveProblem) {
            assertEquals("The sign-in was refused on the phone.", e.message)
            assertFalse(e.offline)
        }
        assertFalse(drive.signedIn)
    }

    @Test fun thePassIsRenewedWhenItRunsOutAndGoogleEndingTheSignInIsNoticed() {
        val drive = signIn()
        assertEquals("access-0", drive.token())
        kept["expires"] = "0"
        assertEquals("access-1", drive.token())
        assertEquals("access-1", drive.token())
        kept["expires"] = "0"
        grantEnded = true
        try { drive.token(); fail("should need signing in again") } catch (e: DriveProblem) {
            assertTrue(e.signedOut)
            assertEquals("Google Drive needs signing in again.", e.message)
        }
        assertFalse(drive.signedIn)
        assertEquals("the screen can still say whose it was", "films@example.com", drive.email)
    }

    @Test fun listsWhatIsInTheRecordingsFolderMakingItFirst() {
        val drive = signIn()
        val list = drive.list()
        assertTrue(folderMade)
        assertEquals(listOf("f2", "f1"), list.map { it.id })
        assertEquals(2147483648L, list[0].size)
        assertEquals(base + "/drive/v3/files/f2?alt=media", drive.playUrl("f2"))
    }

    @Test fun deletingPutsARecordingInDrivesBin() {
        val drive = signIn()
        drive.bin("f1")
        assertEquals(listOf("f1"), binned)
        try { drive.bin("gone"); fail("should say it couldn't") } catch (e: DriveProblem) { assertEquals("Google Drive wouldn't delete that recording (404).", e.message) }
    }

    private fun recording(size: Int): File = File.createTempFile("BBC One", ".ts").also { f -> f.deleteOnExit(); f.writeBytes(ByteArray(size) { (it % 199).toByte() }) }

    @Test fun aRecordingGoesUpInPiecesAndArrivesWhole() {
        val drive = signIn()
        val file = recording(1000)
        val seen = ArrayList<Long>()
        assertTrue(drive.upload(file, piece = 256) { seen += it })
        assertArrayEquals(file.readBytes(), received.toByteArray())
        assertEquals(file.name, uploadedName)
        assertEquals(listOf(0L, 256L, 512L, 768L, 1000L), seen)
        assertEquals(1, sessions)
    }

    @Test fun aPausedCopyCarriesOnFromWhereItStopped() {
        val drive = signIn()
        val file = recording(1000)
        var asked = 0
        assertFalse("paused after two pieces", drive.upload(file, paused = { ++asked > 2 }, piece = 256))
        assertEquals(512, received.size())
        assertTrue(drive.upload(file, piece = 256))
        assertArrayEquals(file.readBytes(), received.toByteArray())
        assertEquals("the same copy was carried on, not started again", 1, sessions)
    }

    @Test fun aCopyCutOffByGoogleCarriesOnNextTime() {
        val drive = signIn()
        val file = recording(1000)
        dropAfterPieces = 1
        try { drive.upload(file, piece = 256); fail("should have stopped") } catch (e: DriveProblem) { assertEquals("Google Drive stopped taking the recording (503).", e.message) }
        dropAfterPieces = Int.MAX_VALUE
        assertTrue(drive.upload(file, piece = 256))
        assertArrayEquals(file.readBytes(), received.toByteArray())
        assertEquals(1, sessions)
    }
}
