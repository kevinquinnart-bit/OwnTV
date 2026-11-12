package tv.own.owntv.features.stremio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Kevin's copy: Stremio add-ons for films and episodes.
 *
 * An add-on is added by its manifest address (".../manifest.json"). For a title, every add-on that
 * offers streams for that kind of title is asked at `<base>/stream/<movie|series>/<id>.json`, where the
 * id is the IMDb id ("tt0111161") or, for an episode, "tt0944947:1:2". Only streams with a web address
 * can play here; torrent-only streams are counted, not shown.
 *
 * The app ships with no add-ons. Addresses are kept on this box only.
 */
class StremioAddons(context: Context, baseClient: OkHttpClient) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = baseClient

    data class Addon(
        val manifestUrl: String,
        val name: String,
        val types: Set<String>,
        val idPrefixes: Set<String>,
    ) {
        val base: String get() = manifestUrl.substringBeforeLast("/manifest.json")
    }

    data class Stream(val addon: String, val title: String, val detail: String?, val url: String, val headers: Map<String, String>)

    data class Found(val playable: List<Stream>, val torrents: Int, val failedAddons: Int)

    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    private fun load(): List<Addon> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_ADDONS, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Addon(
                manifestUrl = o.getString("url"),
                name = o.optString("name"),
                types = o.optJSONArray("types").strings().toSet(),
                idPrefixes = o.optJSONArray("prefixes").strings().toSet(),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Addon>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("url", a.manifestUrl)
                    .put("name", a.name)
                    .put("types", JSONArray(a.types.toList()))
                    .put("prefixes", JSONArray(a.idPrefixes.toList())),
            )
        }
        prefs.edit().putString(KEY_ADDONS, arr.toString()).apply()
        _addons.value = list
    }

    fun remove(addon: Addon) = save(_addons.value.filterNot { it.manifestUrl == addon.manifestUrl })

    sealed interface AddResult {
        data class Added(val name: String) : AddResult
        data object NotAManifest : AddResult
        data object NoStreams : AddResult
        data object Unreachable : AddResult
    }

    /** Reads the add-on's manifest and keeps it if it offers streams. */
    suspend fun add(raw: String): AddResult = withContext(Dispatchers.IO) {
        val url = normalise(raw) ?: return@withContext AddResult.NotAManifest
        val json = runCatching { getJson(url) }.getOrElse {
            return@withContext if (it is NotJson) AddResult.NotAManifest else AddResult.Unreachable
        }
        if (!json.has("id") || !json.has("resources")) return@withContext AddResult.NotAManifest
        // "resources" holds names ("stream") or objects ({"name":"stream","types":[...],"idPrefixes":[...]}).
        val resources = json.optJSONArray("resources") ?: JSONArray()
        var offersStreams = false
        val types = HashSet(json.optJSONArray("types").strings())
        val prefixes = HashSet(json.optJSONArray("idPrefixes").strings())
        for (i in 0 until resources.length()) {
            val r = resources.opt(i)
            if (r is String && r == "stream") offersStreams = true
            if (r is JSONObject && r.optString("name") == "stream") {
                offersStreams = true
                r.optJSONArray("types")?.strings()?.let { types.addAll(it) }
                r.optJSONArray("idPrefixes")?.strings()?.let { prefixes.addAll(it) }
            }
        }
        if (!offersStreams) return@withContext AddResult.NoStreams
        val name = json.optString("name").ifBlank { json.optString("id") }
        save(_addons.value.filterNot { it.manifestUrl == url } + Addon(url, name, types, prefixes))
        AddResult.Added(name)
    }

    /** Streams for a film ([type] "movie", [id] "tt…") or an episode ("series", "tt…:season:episode"). */
    suspend fun streams(type: String, id: String): Found = coroutineScope {
        val asked = _addons.value.filter { a ->
            (a.types.isEmpty() || type in a.types) && (a.idPrefixes.isEmpty() || a.idPrefixes.any { id.startsWith(it) })
        }
        val answers = asked.map { a ->
            async(Dispatchers.IO) {
                a to withTimeoutOrNull(ADDON_TIMEOUT_MS) { runCatching { getJson("${a.base}/stream/$type/$id.json") }.getOrNull() }
            }
        }.awaitAll()
        val playable = ArrayList<Stream>()
        var torrents = 0
        var failed = 0
        for ((addon, json) in answers) {
            val arr = json?.optJSONArray("streams")
            if (arr == null) { failed++; continue }
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val url = s.optString("url")
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val headers = s.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")
                    val title = s.optString("name").ifBlank { addon.name }.replace('\n', ' ').trim()
                    val detail = (s.optString("title").ifBlank { s.optString("description") }).replace('\n', ' ').trim().ifBlank { null }
                    playable += Stream(addon.name, title, detail, url, headers?.toMap().orEmpty())
                } else if (s.optString("infoHash").isNotBlank()) {
                    torrents++
                }
            }
        }
        Found(playable, torrents, failed)
    }

    private class NotJson : IOException("not a manifest")

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body.string()
            return runCatching { JSONObject(text) }.getOrElse { throw NotJson() }
        }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    private fun JSONObject.toMap(): Map<String, String> = keys().asSequence().associateWith { optString(it) }

    companion object {
        private const val TAG = "KevinStremio"
        private const val PREFS = "kevin_stremio"
        private const val KEY_ADDONS = "addons"
        private const val ADDON_TIMEOUT_MS = 20_000L

        /** "stremio://host/…/manifest.json" → https; anything without "/manifest.json" is refused. */
        fun normalise(raw: String): String? {
            val t = raw.trim()
            if (t.isBlank()) return null
            val url = when {
                t.startsWith("stremio://", ignoreCase = true) -> "https://" + t.substring("stremio://".length)
                t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
                else -> "https://$t"
            }
            if (!url.substringBefore('?').endsWith("/manifest.json")) {
                Log.i(TAG, "refused, not a manifest address")
                return null
            }
            return url
        }
    }
}
