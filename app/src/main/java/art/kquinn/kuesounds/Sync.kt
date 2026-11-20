package art.kquinn.kuesounds

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * The person's own changes (favourites now; playlists later), kept on the phone and in one file of
 * KueSounds' own in the music Drive, "KueSounds data.json". A change made with no internet is kept on
 * the phone and sent to the Drive as soon as the phone is back online, and a change made on another
 * device comes the other way. When both sides changed the same thing, the later change wins.
 */
object Sync {
    const val FILE_NAME = "KueSounds data.json"

    /** One remembered choice: its value ("" for taken away) and when it was made. */
    data class Entry(val value: String, val at: Long)

    var entries by mutableStateOf<Map<String, Entry>>(emptyMap())
        private set

    /** When the Drive last agreed with the phone; 0 when never. */
    var syncedAt by mutableStateOf(0L)
        private set

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var soon: Job? = null
    private val gson = Gson()

    private fun localFile() = File(app.filesDir, "changes.json")
    private fun prefs() = app.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        entries = read(runCatching { localFile().readText() }.getOrDefault(""))
        syncedAt = prefs().getLong("syncedAt", 0)
        // Back online: send what was changed while offline.
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = later(2000)
        })
    }

    fun read(json: String): Map<String, Entry> = runCatching {
        gson.fromJson<Map<String, Entry>>(json, object : TypeToken<Map<String, Entry>>() {}.type)
    }.getOrNull()?.filterValues { it != null && it.value != null }.orEmpty()

    /** Brings two sets of changes together: for each item, the later change wins. */
    fun merge(a: Map<String, Entry>, b: Map<String, Entry>): Map<String, Entry> {
        val out = HashMap(a)
        for ((k, v) in b) {
            val mine = out[k]
            if (mine == null || v.at > mine.at) out[k] = v
        }
        return out
    }

    private fun set(key: String, value: String) {
        entries = entries + (key to Entry(value, System.currentTimeMillis()))
        runCatching { localFile().writeText(gson.toJson(entries)) }
        later(3000)
    }

    // Favourites ------------------------------------------------------------------------------

    fun isFavourite(id: String): Boolean = entries["fav:$id"]?.value == "1"

    fun flipFavourite(id: String) = set("fav:$id", if (isFavourite(id)) "" else "1")

    fun favouriteIds(): Set<String> = entries.filter { it.key.startsWith("fav:") && it.value.value == "1" }.keys.map { it.removePrefix("fav:") }.toSet()

    // With the Drive --------------------------------------------------------------------------

    /** Syncs in a moment, so several quick changes go together. */
    fun later(wait: Long) {
        if (!::app.isInitialized) return
        soon?.cancel()
        soon = scope.launch {
            delay(wait)
            runCatching { now() }.onFailure { Scanner.note("sync didn't finish: " + it.javaClass.simpleName + " " + it.message) }
        }
    }

    /** Fetches the Drive's copy, brings the two together, and sends the result back when it differs. */
    suspend fun now() = lock.withLock {
        if (!Net.online(app)) return@withLock
        val token = Google.token() ?: return@withLock
        var id = prefs().getString("fileId", null)
        if (id == null) id = find(token)
        val remote = id?.let { download(it, token) }
        if (id != null && remote == null) {
            // The Drive's file has gone: make a new one.
            prefs().edit().remove("fileId").apply()
            id = null
        }
        val theirs = read(remote.orEmpty())
        val merged = merge(entries, theirs)
        if (merged != entries) {
            entries = merged
            localFile().writeText(gson.toJson(entries))
        }
        if (id == null) {
            id = create(gson.toJson(merged), token)
            prefs().edit().putString("fileId", id).apply()
        } else if (merged != theirs) {
            upload(id, gson.toJson(merged), token)
        }
        syncedAt = System.currentTimeMillis()
        prefs().edit().putLong("syncedAt", syncedAt).apply()
    }

    private fun find(token: String): String? {
        val url = Drive.API + "?q=" + java.net.URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8") + "&fields=files(id)&spaces=drive"
        Drive.http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to finding the data file")
            return Drive.parsePage(r.body.string()).files.firstOrNull()?.id?.also { prefs().edit().putString("fileId", it).apply() }
        }
    }

    private fun download(id: String, token: String): String? {
        Drive.http.newCall(Request.Builder().url(Drive.mediaUrl(id)).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to reading the data file")
            return r.body.string()
        }
    }

    private fun upload(id: String, json: String, token: String) {
        val request = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(json.toRequestBody("application/json".toMediaType())).build()
        Drive.http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to saving the data file")
        }
    }

    private fun create(json: String, token: String): String {
        val boundary = "kuesounds" + System.nanoTime()
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            "{\"name\":\"$FILE_NAME\",\"mimeType\":\"application/json\"}\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n" + json + "\r\n--$boundary--"
        val request = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType())).build()
        Drive.http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Google Drive answered " + r.code + " to making the data file")
            return com.google.gson.JsonParser.parseString(r.body.string()).asJsonObject.get("id").asString
        }
    }
}
