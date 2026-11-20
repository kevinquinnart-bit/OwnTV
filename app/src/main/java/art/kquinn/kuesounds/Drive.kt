package art.kquinn.kuesounds

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reading the music Google Drive: listing its music files, folders and cover pictures, reading part of
 * a file, and downloading a cover. Every call is made with the sign-in from [Google].
 */
object Drive {
    const val API = "https://www.googleapis.com/drive/v3/files"

    /** The address the player plays a song from. */
    fun mediaUrl(id: String): String = "$API/$id?alt=media"

    data class FileInfo(val id: String, val name: String, val mime: String, val size: Long, val parent: String?, val modified: String)

    data class Page(val files: List<FileInfo>, val next: String?)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    /** What the Drive search is asked for: music, by kind; plus files of no stated kind, sorted out by name. */
    const val MUSIC_QUERY = "trashed = false and (mimeType contains 'audio/' or mimeType = 'application/ogg' or " +
        "mimeType = 'application/x-flac' or mimeType = 'application/octet-stream' or mimeType = 'video/mp4')"
    const val FOLDER_QUERY = "trashed = false and mimeType = 'application/vnd.google-apps.folder'"
    const val COVER_QUERY = "trashed = false and mimeType contains 'image/' and " +
        "(name contains 'cover' or name contains 'folder' or name contains 'front' or name contains 'albumart')"

    /** Reads one page of a Drive listing. */
    fun parsePage(json: String): Page {
        val root = JsonParser.parseString(json).asJsonObject
        val files = root.getAsJsonArray("files")?.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            FileInfo(
                id = id,
                name = o.get("name")?.asString.orEmpty(),
                mime = o.get("mimeType")?.asString.orEmpty(),
                size = o.get("size")?.asString?.toLongOrNull() ?: 0L,
                parent = o.getAsJsonArray("parents")?.firstOrNull()?.asString,
                modified = o.get("modifiedTime")?.asString.orEmpty(),
            )
        }.orEmpty()
        val next = root.get("nextPageToken")?.asString?.takeIf { it.isNotBlank() }
        return Page(files, next)
    }

    /** Whether a picture's name is one used for an album cover. */
    fun isCoverName(name: String): Boolean {
        val stem = name.substringBeforeLast('.').lowercase()
        return stem == "cover" || stem == "folder" || stem == "front" || stem.startsWith("albumart") || stem == "album"
    }

    /** Lists everything that matches [query], page by page, telling [found] the running count. */
    suspend fun listAll(query: String, found: (Int) -> Unit = {}): List<FileInfo> {
        val all = mutableListOf<FileInfo>()
        var pageToken: String? = null
        do {
            val url = API.toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,parents,modifiedTime)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = parsePage(get(url.toString()))
            all += page.files
            found(all.size)
            pageToken = page.next
        } while (pageToken != null)
        return all
    }

    /** The music account's address, to show who is signed in. */
    suspend fun whoAmI(): String {
        val json = get("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)")
        return JsonParser.parseString(json).asJsonObject.getAsJsonObject("user")?.get("emailAddress")?.asString.orEmpty()
    }

    private suspend fun get(url: String): String {
        val token = Google.token() ?: throw SignedOut()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { r ->
            if (r.code == 401) {
                Google.forget()
                throw SignedOut()
            }
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code)
            return r.body.string()
        }
    }

    /** Reads bytes [from] to [to] (both included) of a file. Call off the main thread. */
    fun readRange(id: String, from: Long, to: Long, token: String): ByteArray {
        val request = Request.Builder().url(mediaUrl(id)).header("Authorization", "Bearer $token").header("Range", "bytes=$from-$to").build()
        http.newCall(request).execute().use { r ->
            if (r.code == 401) {
                Google.forget()
                throw SignedOut()
            }
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code)
            val bytes = r.body.bytes()
            // A Drive that ignores the range sends the whole file: keep only the part asked for.
            return if (r.code == 200 && bytes.size > (to - from + 1)) bytes.copyOfRange(from.toInt(), minOf(bytes.size, (to + 1).toInt())) else bytes
        }
    }

    /** Downloads a whole (small) file, such as a cover picture, to [target]. */
    suspend fun download(id: String, target: File) {
        val token = Google.token() ?: throw SignedOut()
        val request = Request.Builder().url(mediaUrl(id)).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code)
            target.parentFile?.mkdirs()
            r.body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    /** Downloads a whole song file to [target] with the editing pass. Call off the main thread. */
    fun downloadWhole(id: String, target: File, token: String) {
        val request = Request.Builder().url(mediaUrl(id)).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { r ->
            if (r.code == 401) throw SignedOut()
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to the download")
            target.parentFile?.mkdirs()
            r.body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    /**
     * Puts [file] back in the Drive as the new version of the same file (same name, same folder, same
     * place in the person's Drive). Google keeps the old version for 30 days. Gives back the file's new
     * size and change time. Call off the main thread.
     */
    fun uploadNewVersion(id: String, file: File, mime: String, token: String): Pair<Long, String> {
        val type = mime.ifBlank { "application/octet-stream" }
        val start = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=resumable&fields=size,modifiedTime")
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", type)
            .header("X-Upload-Content-Length", file.length().toString())
            .patch("{}".toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val session = http.newCall(start).execute().use { r ->
            if (r.code == 401) throw SignedOut()
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to starting the upload")
            r.header("Location") ?: throw IOException("Google Drive gave no upload address")
        }
        val slow = http.newBuilder().writeTimeout(10, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
        val put = Request.Builder().url(session).put(file.asRequestBody(type.toMediaType())).build()
        slow.newCall(put).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to the upload")
            val o = JsonParser.parseString(r.body.string()).asJsonObject
            return (o.get("size")?.asString?.toLongOrNull() ?: file.length()) to o.get("modifiedTime")?.asString.orEmpty()
        }
    }

    /** The sign-in has run out or was taken back: the person needs to sign in again. */
    class SignedOut : IOException("signed out")
}
