package tv.own.owntv.features.drive

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Kevin's copy: Google Drive for backups and recordings.
 *
 * Sign-in is Google's TV flow: the TV shows a short code, the user enters it at google.com/device on a
 * phone, and the TV is given a lasting key. The app asks only for `drive.file`, so it can see and change
 * the files it created itself and nothing else in the user's Drive.
 *
 * Everything goes in one folder, "OwnTV", with "Backups" and "Recordings" inside it.
 */
class GoogleDrive(context: Context, baseClient: OkHttpClient) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Uploads of a whole recording can take many minutes, so the read/write timeouts are long. */
    private val http: OkHttpClient = baseClient.newBuilder()
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    /** True once both halves of the sign-in key are built into this copy of the app. */
    val configured: Boolean get() = DriveKeys.CLIENT_ID.isNotBlank() && DriveKeys.CLIENT_SECRET.isNotBlank()

    val signedIn: Boolean get() = !prefs.getString(KEY_REFRESH, null).isNullOrBlank()

    /** The Google account's address, shown on screen once signed in; blank when not known. */
    val account: String get() = prefs.getString(KEY_ACCOUNT, "").orEmpty()

    fun signOut() {
        prefs.edit().clear().apply()
    }

    // ---- Sign-in ----

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresAtMs: Long,
        val intervalMs: Long,
    )

    sealed interface Poll {
        data object Waiting : Poll
        data object SlowDown : Poll
        data object Done : Poll
        data object Denied : Poll
        data object Expired : Poll
        data class Failed(val message: String) : Poll
    }

    /** Step 1: ask Google for a code to show on the TV. */
    suspend fun startSignIn(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", DriveKeys.CLIENT_ID)
            .add("scope", SCOPE)
            .build()
        val json = postForm(DEVICE_CODE_URL, body)
        DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optString("verification_url").ifBlank { json.optString("verification_uri") },
            expiresAtMs = System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000,
            intervalMs = json.optLong("interval", 5).coerceAtLeast(1) * 1000,
        )
    }

    /** Step 2: called every [DeviceCode.intervalMs] until it is [Poll.Done] or a final answer. */
    suspend fun pollSignIn(code: DeviceCode): Poll = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > code.expiresAtMs) return@withContext Poll.Expired
        val body = FormBody.Builder()
            .add("client_id", DriveKeys.CLIENT_ID)
            .add("client_secret", DriveKeys.CLIENT_SECRET)
            .add("device_code", code.deviceCode)
            .add("grant_type", DEVICE_GRANT)
            .build()
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        runCatching {
            http.newCall(request).execute().use { resp ->
                val json = JSONObject(resp.body.string())
                if (resp.isSuccessful) {
                    saveTokens(json)
                    prefs.edit().putString(KEY_ACCOUNT, runCatching { fetchAccount() }.getOrNull().orEmpty()).apply()
                    Poll.Done
                } else when (json.optString("error")) {
                    "authorization_pending" -> Poll.Waiting
                    "slow_down" -> Poll.SlowDown
                    "access_denied" -> Poll.Denied
                    "expired_token" -> Poll.Expired
                    else -> Poll.Failed(json.optString("error_description").ifBlank { json.optString("error") })
                }
            }
        }.getOrElse { Poll.Failed(it.message ?: it.javaClass.simpleName) }
    }

    private fun saveTokens(json: JSONObject) {
        val edit = prefs.edit()
            .putString(KEY_ACCESS, json.getString("access_token"))
            .putLong(KEY_EXPIRY, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000 - EXPIRY_MARGIN_MS)
        // A refresh answer usually carries no new refresh key; the one from sign-in stays.
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { edit.putString(KEY_REFRESH, it) }
        edit.apply()
    }

    /** A usable access key, refreshed when it is about to run out. */
    private fun accessToken(): String {
        val current = prefs.getString(KEY_ACCESS, null)
        if (current != null && System.currentTimeMillis() < prefs.getLong(KEY_EXPIRY, 0)) return current
        val refresh = prefs.getString(KEY_REFRESH, null) ?: throw DriveException(DriveException.Kind.SIGNED_OUT)
        val body = FormBody.Builder()
            .add("client_id", DriveKeys.CLIENT_ID)
            .add("client_secret", DriveKeys.CLIENT_SECRET)
            .add("refresh_token", refresh)
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        http.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body.string())
            if (!resp.isSuccessful) {
                // The key was withdrawn (signed out at Google, or the password changed): sign in again.
                if (json.optString("error") == "invalid_grant") {
                    signOut()
                    throw DriveException(DriveException.Kind.SIGNED_OUT)
                }
                throw IOException("token refresh failed: ${resp.code} ${json.optString("error")}")
            }
            saveTokens(json)
            return json.getString("access_token")
        }
    }

    private fun fetchAccount(): String {
        val request = authed("$API/about?fields=user(emailAddress)").get().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            return JSONObject(resp.body.string()).optJSONObject("user")?.optString("emailAddress").orEmpty()
        }
    }

    // ---- Files ----

    data class DriveFile(val id: String, val name: String, val size: Long, val createdMs: Long)

    data class Space(val usedBytes: Long, val limitBytes: Long?) {
        /** Free space; null when the account has no limit. */
        val freeBytes: Long? get() = limitBytes?.let { (it - usedBytes).coerceAtLeast(0) }
    }

    enum class Folder(val title: String) { BACKUPS("Backups"), RECORDINGS("Recordings") }

    suspend fun space(): Space = withContext(Dispatchers.IO) {
        val json = getJson("$API/about?fields=storageQuota")
        val q = json.getJSONObject("storageQuota")
        Space(
            usedBytes = q.optString("usage").toLongOrNull() ?: 0L,
            limitBytes = q.optString("limit").toLongOrNull(),
        )
    }

    /** The app's files in [folder], oldest first. */
    suspend fun list(folder: Folder): List<DriveFile> = withContext(Dispatchers.IO) {
        val parent = folderId(folder)
        val out = ArrayList<DriveFile>()
        var page: String? = null
        do {
            val q = java.net.URLEncoder.encode("'$parent' in parents and trashed = false", "UTF-8")
            val url = "$API/files?q=$q&orderBy=createdTime&pageSize=200&fields=nextPageToken,files(id,name,size,createdTime)" +
                (page?.let { "&pageToken=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
            val json = getJson(url)
            val files = json.optJSONArray("files")
            if (files != null) for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                out += DriveFile(
                    id = f.getString("id"),
                    name = f.optString("name"),
                    size = f.optString("size").toLongOrNull() ?: 0L,
                    createdMs = runCatching { java.time.Instant.parse(f.optString("createdTime")).toEpochMilli() }.getOrDefault(0L),
                )
            }
            page = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (page != null)
        out
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val request = authed("$API/files/$id").delete().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw IOException("delete failed: ${resp.code}")
        }
    }

    suspend fun download(id: String, dest: File) = withContext(Dispatchers.IO) {
        val request = authed("$API/files/$id?alt=media").get().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("download failed: ${resp.code}")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> resp.body.byteStream().use { it.copyTo(out) } }
        }
    }

    /**
     * Uploads [file] into [folder] as [name], in pieces, so a dropped connection costs one piece rather
     * than the whole file. [onProgress] gets the bytes sent so far. Returns the new file's id.
     */
    suspend fun upload(
        file: File,
        folder: Folder,
        name: String,
        mime: String,
        onProgress: (Long) -> Unit = {},
    ): String = upload(file.length(), { from -> java.io.FileInputStream(file).also { it.channel.position(from) } }, folder, name, mime, onProgress)

    /**
     * The same from anywhere that can be read as a stream — a recording kept on a USB drive is a
     * document, not a file. [open] gives a stream already moved to the byte asked for.
     */
    suspend fun upload(
        total: Long,
        open: (Long) -> java.io.InputStream,
        folder: Folder,
        name: String,
        mime: String,
        onProgress: (Long) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val parent = folderId(folder)
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put(parent)).toString()
        val start = authed("$UPLOAD/files?uploadType=resumable&fields=id")
            .header("X-Upload-Content-Type", mime)
            .header("X-Upload-Content-Length", total.toString())
            .post(meta.toRequestBody(JSON))
            .build()
        val session = http.newCall(start).execute().use { resp ->
            if (!resp.isSuccessful) throw driveError(resp.code, resp.body.string())
            resp.header("Location") ?: throw IOException("no upload address")
        }
        var sent = 0L
        run {
            while (true) {
                val len = minOf(CHUNK, total - sent)
                val body = ChunkBody(open, sent, len, mime)
                val end = sent + len - 1
                val range = if (total == 0L) "bytes */0" else "bytes $sent-$end/$total"
                val put = Request.Builder().url(session).header("Content-Range", range).put(body).build()
                var attempt = 0
                while (true) {
                    val result = runCatching {
                        http.newCall(put).execute().use { resp ->
                            when {
                                resp.isSuccessful -> Step.Finished(JSONObject(resp.body.string()).getString("id"))
                                resp.code == 308 -> Step.Next(resp.header("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: sent)
                                else -> throw driveError(resp.code, resp.body.string())
                            }
                        }
                    }
                    val r = result.getOrNull()
                    if (r is Step.Finished) { onProgress(total); return@withContext r.id }
                    if (r is Step.Next) { sent = r.from; onProgress(sent); break }
                    val e = result.exceptionOrNull()
                    if (e is DriveException || ++attempt > CHUNK_RETRIES) throw (e ?: IOException("upload failed"))
                    Log.w(TAG, "upload piece failed, retrying (${e?.message})")
                    kotlinx.coroutines.delay(2_000L * attempt)
                }
                if (sent >= total) break
            }
        }
        throw IOException("upload ended without a file id")
    }

    private sealed interface Step {
        data class Finished(val id: String) : Step
        data class Next(val from: Long) : Step
    }

    /** One piece of a file, opened at its first byte each time it is sent, so a retry sends it whole again. */
    private class ChunkBody(
        private val open: (Long) -> java.io.InputStream,
        private val from: Long,
        private val len: Long,
        mime: String,
    ) : RequestBody() {
        private val type = mime.toMediaType()
        override fun contentType() = type
        override fun contentLength() = len
        override fun writeTo(sink: BufferedSink) {
            open(from).use { input ->
                val buf = ByteArray(64 * 1024)
                var left = len
                while (left > 0) {
                    val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (r < 0) break
                    sink.write(buf, 0, r)
                    left -= r
                }
            }
        }
    }

    // ---- Folders ----

    private val folderIds = HashMap<String, String>()

    @Synchronized
    private fun folderId(folder: Folder): String {
        val root = findOrCreateFolder(ROOT_FOLDER, null)
        return findOrCreateFolder(folder.title, root)
    }

    private fun findOrCreateFolder(name: String, parent: String?): String {
        val cacheKey = "${parent ?: ""}/$name"
        folderIds[cacheKey]?.let { return it }
        val clauses = buildList {
            // The folder names are this file's own constants, none with a quote in it.
            add("name = '$name'")
            add("mimeType = '$FOLDER_MIME'")
            add("trashed = false")
            if (parent != null) add("'$parent' in parents")
        }.joinToString(" and ")
        val q = java.net.URLEncoder.encode(clauses, "UTF-8")
        val found = getJson("$API/files?q=$q&fields=files(id)").optJSONArray("files")
        val id = if (found != null && found.length() > 0) {
            found.getJSONObject(0).getString("id")
        } else {
            val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
            if (parent != null) meta.put("parents", org.json.JSONArray().put(parent))
            val request = authed("$API/files?fields=id").post(meta.toString().toRequestBody(JSON)).build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw driveError(resp.code, resp.body.string())
                JSONObject(resp.body.string()).getString("id")
            }
        }
        folderIds[cacheKey] = id
        return id
    }

    // ---- Plumbing ----

    private fun authed(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer " + accessToken())

    private fun getJson(url: String): JSONObject {
        http.newCall(authed(url).get().build()).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) throw driveError(resp.code, text)
            return JSONObject(text)
        }
    }

    private fun postForm(url: String, body: FormBody): JSONObject {
        val request = Request.Builder().url(url).post(body).build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) throw IOException("${resp.code}: " + runCatching { JSONObject(text).optString("error") }.getOrDefault(text))
            return JSONObject(text)
        }
    }

    private fun driveError(code: Int, text: String): Exception {
        val reason = runCatching {
            JSONObject(text).optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
        }.getOrNull().orEmpty()
        return when {
            code == 401 -> DriveException(DriveException.Kind.SIGNED_OUT)
            reason == "storageQuotaExceeded" -> DriveException(DriveException.Kind.FULL)
            else -> IOException("Drive $code $reason")
        }
    }

    class DriveException(val kind: Kind) : IOException(kind.name) {
        enum class Kind { SIGNED_OUT, FULL }
    }

    companion object {
        private const val TAG = "KevinDrive"
        private const val PREFS = "kevin_google_drive"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_ACCOUNT = "account"
        private const val EXPIRY_MARGIN_MS = 60_000L
        private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val ROOT_FOLDER = "OwnTV"
        private val JSON = "application/json; charset=UTF-8".toMediaType()

        /** 8 MB pieces: a multiple of 256 KB, as Drive requires for every piece but the last. */
        private const val CHUNK = 8L * 1024 * 1024
        private const val CHUNK_RETRIES = 4
    }
}
