package art.kquinn.kuetv

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile

/**
 * A problem with Google Drive the user can read on screen. [signedOut]: Google wants the sign-in done
 * again. [offline]: Google couldn't be reached this time, which may pass.
 */
class DriveProblem(message: String, val signedOut: Boolean = false, val offline: Boolean = false) : Exception(message)

/** Where the sign-in is kept between runs of the app. */
interface Keep {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * Google Drive, for recordings.
 *
 * Signing in is done on a phone: the TV shows a short code, the user types it at google.com/device and
 * picks the Google account. The app is only allowed to see the files it puts there itself, which it
 * keeps in one folder, "KueTV recordings".
 */
class Drive(
    private val keep: Keep,
    private val clientId: String,
    private val clientSecret: String,
    private val auth: String = "https://oauth2.googleapis.com",
    private val api: String = "https://www.googleapis.com",
    http: OkHttpClient = Provider.http,
) {
    // Google answers "carry on" to a part-sent file with a code the web client would otherwise treat as "go elsewhere".
    private val http = http.newBuilder().followRedirects(false).build()

    /** False when this copy of the app was built without its Google keys. */
    val isSetUp: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    val signedIn: Boolean get() = !keep.get("refresh").isNullOrBlank()
    val email: String get() = keep.get("email").orEmpty()

    /** The code to type on a phone, and where to type it. */
    class Code(val userCode: String, val url: String, val deviceCode: String, val intervalSec: Int, val expiresAtMs: Long)

    /** A recording that is in Google Drive. */
    class Saved(val id: String, val name: String, val size: Long)

    private class Answer(val code: Int, val json: JsonObject, val range: String?, val location: String?)

    private fun send(request: Request): Answer = try {
        http.newCall(request).execute().use { r ->
            val text = r.body.string()
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: JsonObject()
            Answer(r.code, json, r.header("Range"), r.header("Location"))
        }
    } catch (e: java.io.IOException) {
        throw DriveProblem("Couldn't reach Google. Check the internet connection.", offline = true)
    }

    private fun JsonObject.text(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun form(url: String, vararg fields: Pair<String, String>): Answer {
        val body = FormBody.Builder()
        for ((k, v) in fields) body.add(k, v)
        return send(Request.Builder().url(url).post(body.build()).build())
    }

    // ------------------------------------------------------------------------------------------ signing in

    fun beginSignIn(): Code {
        val a = form("$auth/device/code", "client_id" to clientId, "scope" to "https://www.googleapis.com/auth/drive.file")
        val device = a.json.text("device_code")
        val user = a.json.text("user_code")
        if (a.code != 200 || device.isEmpty() || user.isEmpty()) {
            throw DriveProblem("Google wouldn't start the sign-in (" + a.json.text("error").ifEmpty { a.code.toString() } + ").")
        }
        val seconds = a.json.text("expires_in").toLongOrNull() ?: 1800
        return Code(
            userCode = user,
            url = a.json.text("verification_url").ifEmpty { "https://www.google.com/device" },
            deviceCode = device,
            intervalSec = (a.json.text("interval").toIntOrNull() ?: 5).coerceAtLeast(5),
            expiresAtMs = System.currentTimeMillis() + seconds * 1000,
        )
    }

    /** True once the user has finished on their phone; false while still waiting. */
    fun checkSignIn(code: Code): Boolean {
        val a = form(
            "$auth/token",
            "client_id" to clientId, "client_secret" to clientSecret, "device_code" to code.deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        )
        val refresh = a.json.text("refresh_token")
        if (a.code == 200 && refresh.isNotEmpty()) {
            keep.put("refresh", refresh)
            keepAccess(a.json)
            keep.put("folder", null)
            keep.put("email", runCatching { whoAmI() }.getOrDefault(""))
            return true
        }
        when (val error = a.json.text("error")) {
            "authorization_pending", "slow_down" -> return false
            "access_denied" -> throw DriveProblem("The sign-in was refused on the phone.")
            "expired_token" -> throw DriveProblem("That code ran out of time. Start again.")
            else -> throw DriveProblem("Google wouldn't finish the sign-in (" + error.ifEmpty { a.code.toString() } + ").")
        }
    }

    fun signOut() {
        for (key in listOf("refresh", "access", "expires", "email", "folder")) keep.put(key, null)
    }

    private fun keepAccess(json: JsonObject) {
        keep.put("access", json.text("access_token"))
        keep.put("expires", (System.currentTimeMillis() + (json.text("expires_in").toLongOrNull() ?: 3000) * 1000).toString())
    }

    /** The short-lived pass every request carries; renewed when it is about to run out. */
    @Synchronized
    fun token(): String {
        val refresh = keep.get("refresh")
        if (refresh.isNullOrBlank()) throw DriveProblem("Google Drive isn't signed in.", signedOut = true)
        val access = keep.get("access").orEmpty()
        val expires = keep.get("expires")?.toLongOrNull() ?: 0
        if (access.isNotEmpty() && expires - System.currentTimeMillis() > 60_000) return access
        val a = form("$auth/token", "client_id" to clientId, "client_secret" to clientSecret, "refresh_token" to refresh, "grant_type" to "refresh_token")
        if (a.code == 200 && a.json.text("access_token").isNotEmpty()) {
            keepAccess(a.json)
            return a.json.text("access_token")
        }
        if (a.json.text("error") == "invalid_grant") {
            // Google has ended the sign-in. The email is kept so the screen can say whose it was.
            for (key in listOf("refresh", "access", "expires", "folder")) keep.put(key, null)
            throw DriveProblem("Google Drive needs signing in again.", signedOut = true)
        }
        throw DriveProblem("Google wouldn't renew the sign-in (" + a.json.text("error").ifEmpty { a.code.toString() } + ").")
    }

    private fun authed(url: String): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer " + token())

    private fun whoAmI(): String {
        val a = send(authed("$api/drive/v3/about?fields=user(emailAddress)").build())
        return a.json.getAsJsonObject("user")?.text("emailAddress").orEmpty()
    }

    // ------------------------------------------------------------------------------------------ the folder

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun folderId(): String {
        keep.get("folder")?.takeIf { it.isNotBlank() }?.let { return it }
        val query = java.net.URLEncoder.encode("name = '$FOLDER' and mimeType = '$FOLDER_TYPE' and trashed = false", "UTF-8")
        val found = send(authed("$api/drive/v3/files?q=$query&fields=files(id)").build())
        var id = (found.json.get("files") as? JsonArray)?.firstOrNull()?.asJsonObject?.text("id").orEmpty()
        if (id.isEmpty()) {
            val body = JsonObject().apply { addProperty("name", FOLDER); addProperty("mimeType", FOLDER_TYPE) }
            val made = send(authed("$api/drive/v3/files?fields=id").post(body.toString().toRequestBody(jsonType)).build())
            id = made.json.text("id")
            if (id.isEmpty()) throw DriveProblem("Google Drive wouldn't make the recordings folder (" + made.code + ").")
        }
        keep.put("folder", id)
        return id
    }

    /** The recordings in Google Drive, newest first. */
    fun list(): List<Saved> {
        val query = java.net.URLEncoder.encode("'" + folderId() + "' in parents and trashed = false", "UTF-8")
        val a = send(authed("$api/drive/v3/files?q=$query&orderBy=createdTime%20desc&pageSize=200&fields=files(id,name,size)").build())
        if (a.code == 404) {
            // The folder was removed in Drive. Nothing is in it; the next recording makes it again.
            keep.put("folder", null)
            return emptyList()
        }
        if (a.code != 200) throw DriveProblem("Google Drive wouldn't list the recordings (" + a.code + ").")
        val files = a.json.get("files") as? JsonArray ?: return emptyList()
        return files.mapNotNull { row ->
            val o = runCatching { row.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val id = o.text("id").ifEmpty { return@mapNotNull null }
            // The backup is kept in the same folder; it isn't a recording.
            if (o.text("name").endsWith(".json")) return@mapNotNull null
            Saved(id, o.text("name"), o.text("size").toLongOrNull() ?: 0)
        }
    }

    /** Puts a recording in Drive's bin, where Google keeps it for 30 days before removing it. */
    fun bin(id: String) {
        val body = JsonObject().apply { addProperty("trashed", true) }
        val a = send(authed("$api/drive/v3/files/$id").patch(body.toString().toRequestBody(jsonType)).build())
        if (a.code != 200) throw DriveProblem("Google Drive wouldn't delete that recording (" + a.code + ").")
    }

    // ------------------------------------------------------------------------------------------ the backup

    /** A small file's id in the KueTV folder, by its name; null when there is none. */
    private fun idOf(name: String): String? {
        val query = java.net.URLEncoder.encode("name = '" + name.replace("'", "\\'") + "' and '" + folderId() + "' in parents and trashed = false", "UTF-8")
        val a = send(authed("$api/drive/v3/files?q=$query&fields=files(id)").build())
        if (a.code != 200) throw DriveProblem("Google Drive wouldn't look for the backup (" + a.code + ").")
        return (a.json.get("files") as? JsonArray)?.firstOrNull()?.asJsonObject?.text("id")?.ifEmpty { null }
    }

    /** Writes a small text file into the KueTV folder, over the one of the same name if it is there. */
    fun putText(name: String, text: String) {
        val body = text.toRequestBody(jsonType)
        val id = idOf(name)
        val a = if (id != null) {
            send(authed("$api/upload/drive/v3/files/$id?uploadType=media&fields=id").patch(body).build())
        } else {
            val meta = JsonObject().apply {
                addProperty("name", name)
                add("parents", JsonArray().apply { add(folderId()) })
            }
            val both = okhttp3.MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(meta.toString().toRequestBody(jsonType))
                .addPart(body)
                .build()
            send(authed("$api/upload/drive/v3/files?uploadType=multipart&fields=id").post(both).build())
        }
        if (a.code == 403 && a.json.toString().contains("storageQuotaExceeded")) throw DriveProblem("That Google Drive is full.")
        if (a.code != 200) throw DriveProblem("Google Drive wouldn't take the backup (" + a.code + ").")
    }

    /** A small text file from the KueTV folder; null when there is none of that name. */
    fun getText(name: String): String? {
        val id = idOf(name) ?: return null
        return try {
            http.newCall(authed("$api/drive/v3/files/$id?alt=media").build()).execute().use { r ->
                if (r.code != 200) throw DriveProblem("Google Drive wouldn't give the backup back (" + r.code + ").")
                r.body.string()
            }
        } catch (e: java.io.IOException) {
            throw DriveProblem("Couldn't reach Google. Check the internet connection.", offline = true)
        }
    }

    /** The address a recording in Drive plays from; it must be asked for with [token]. */
    fun playUrl(id: String): String = "$api/drive/v3/files/$id?alt=media"

    // ------------------------------------------------------------------------------------------ sending a file

    /**
     * Copies [file] to the recordings folder, a piece at a time, carrying on from where an earlier try
     * stopped. Between pieces it asks [paused]; if that says yes it stops and returns false, to be
     * carried on later. Returns true once the whole file is in Drive.
     */
    fun upload(file: File, paused: () -> Boolean = { false }, piece: Int = PIECE, progress: (Long) -> Unit = {}): Boolean {
        val total = file.length()
        val sessionKey = "session:" + file.name + ":" + total
        var session = keep.get(sessionKey)
        var sent = 0L
        if (session != null) {
            // Ask how much of it Google already has.
            val a = send(Request.Builder().url(session).header("Content-Range", "bytes */$total").put(ByteArray(0).toRequestBody()).build())
            when (a.code) {
                200, 201 -> { keep.put(sessionKey, null); return true }
                308 -> sent = received(a.range)
                else -> session = null
            }
        }
        val target: String = session ?: run {
            sent = 0
            var fresh = startSession(file.name, total)
            if (fresh == null) {
                // The folder may have been removed in Drive: look for it again, once.
                keep.put("folder", null)
                fresh = startSession(file.name, total)
            }
            if (fresh == null) throw DriveProblem("Google Drive wouldn't take the recording.")
            keep.put(sessionKey, fresh)
            fresh
        }
        progress(sent)
        RandomAccessFile(file, "r").use { input ->
            val buffer = ByteArray(piece)
            while (sent < total) {
                if (paused()) return false
                input.seek(sent)
                val count = minOf(piece.toLong(), total - sent).toInt()
                input.readFully(buffer, 0, count)
                val a = send(
                    Request.Builder().url(target)
                        .header("Content-Range", "bytes " + sent + "-" + (sent + count - 1) + "/" + total)
                        .put(buffer.toRequestBody(videoType, 0, count)).build(),
                )
                when (a.code) {
                    200, 201 -> sent = total
                    308 -> sent = received(a.range)
                    404, 410 -> { keep.put(sessionKey, null); throw DriveProblem("Google Drive lost its place in the recording. It will start it again.") }
                    else -> throw DriveProblem("Google Drive stopped taking the recording (" + a.code + ").")
                }
                progress(sent)
            }
        }
        keep.put(sessionKey, null)
        return true
    }

    private val videoType = "video/mp2t".toMediaType()

    private fun startSession(name: String, total: Long): String? {
        val body = JsonObject().apply {
            addProperty("name", name)
            add("parents", JsonArray().apply { add(folderId()) })
        }
        val a = send(
            authed("$api/upload/drive/v3/files?uploadType=resumable")
                .header("X-Upload-Content-Type", "video/mp2t")
                .header("X-Upload-Content-Length", total.toString())
                .post(body.toString().toRequestBody(jsonType)).build(),
        )
        if (a.code == 403 && a.json.toString().contains("storageQuotaExceeded")) throw DriveProblem("That Google Drive is full.")
        return if (a.code == 200) a.location else null
    }

    /** "bytes=0-1048575" means the first 1048576 bytes are in; no such line means none are. */
    private fun received(range: String?): Long = range?.substringAfterLast('-')?.trim()?.toLongOrNull()?.plus(1) ?: 0

    companion object {
        const val FOLDER = "KueTV recordings"
        private const val FOLDER_TYPE = "application/vnd.google-apps.folder"

        /** Google wants pieces in multiples of 256 KB. */
        const val PIECE = 8 * 1024 * 1024
    }
}
