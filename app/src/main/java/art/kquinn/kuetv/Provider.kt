package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/** How the app reaches the provider: a login (server, username, password) or one playlist link. */
data class Account(val server: String, val user: String, val pass: String, val playlist: String) {
    val isLogin: Boolean get() = server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    val isSet: Boolean get() = isLogin || playlist.isNotBlank()
}

data class Category(val id: String, val name: String)

data class Channel(
    val id: String,
    val name: String,
    val logo: String,
    val categoryId: String,
    val url: String,
    /** Days of catch-up the provider says this channel keeps; 0 when none. */
    val catchupDays: Int,
)

data class Film(val id: String, val name: String, val poster: String, val categoryId: String, val url: String)

data class Show(val id: String, val name: String, val poster: String, val categoryId: String)

data class Episode(val id: String, val season: Int, val number: Int, val title: String, val url: String)

class Library(
    val liveCategories: List<Category>,
    val channels: List<Channel>,
    val filmCategories: List<Category>,
    val films: List<Film>,
    val showCategories: List<Category> = emptyList(),
    val shows: List<Show> = emptyList(),
    /** The login these came from; null for a playlist link, which has no guide or catch-up to ask for. */
    val account: Account? = null,
    /** How far the provider's own clock is ahead of world time, in seconds. Catch-up is asked for on its clock. */
    val serverAheadSec: Int = 0,
)

/** One programme in a channel's guide. [startText] is the start exactly as the provider wrote it, on its own clock. */
data class Programme(val title: String, val description: String, val startMs: Long, val stopMs: Long, val startText: String)

/** What a search found, at most [Finder.LIMIT] of each kind. */
class Found(val channels: List<Channel>, val films: List<Film>, val shows: List<Show>) {
    val isEmpty: Boolean get() = channels.isEmpty() && films.isEmpty() && shows.isEmpty()
}

object Finder {
    const val LIMIT = 40

    /** Every word typed must be somewhere in the name, in any order, whatever the capitals. */
    fun search(library: Library, typed: String): Found {
        val words = typed.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.sumOf { it.length } < 2) return Found(emptyList(), emptyList(), emptyList())
        val hit: (String) -> Boolean = { name -> val n = name.lowercase(); words.all { n.contains(it) } }
        return Found(
            library.channels.asSequence().filter { hit(it.name) }.take(LIMIT).toList(),
            library.films.asSequence().filter { hit(it.name) }.take(LIMIT).toList(),
            library.shows.asSequence().filter { hit(it.name) }.take(LIMIT).toList(),
        )
    }
}

/**
 * The user's favourites, kept on the TV. A channel or film from a login is known by its number there;
 * from a playlist link it is known by its address, because a playlist's numbering changes when it does.
 */
class Favourites(context: Context) {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)
    var keys by androidx.compose.runtime.mutableStateOf(prefs.getStringSet("keys", emptySet()).orEmpty().toSet())
        private set

    fun toggle(key: String) {
        keys = if (key in keys) keys - key else keys + key
        prefs.edit().putStringSet("keys", keys).apply()
    }

    companion object {
        fun key(channel: Channel, login: Boolean) = "live:" + if (login) channel.id else channel.url
        fun key(film: Film, login: Boolean) = "film:" + if (login) film.id else film.url
        fun key(show: Show) = "show:" + show.id
    }
}

/** A problem the user can read on screen. */
class ProviderProblem(message: String) : Exception(message)

object AccountStore {
    private const val FILE = "account"

    fun load(context: Context): Account {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Account(
            server = p.getString("server", "").orEmpty(),
            user = p.getString("user", "").orEmpty(),
            pass = p.getString("pass", "").orEmpty(),
            playlist = p.getString("playlist", "").orEmpty(),
        )
    }

    fun save(context: Context, account: Account) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("server", account.server)
            .putString("user", account.user)
            .putString("pass", account.pass)
            .putString("playlist", account.playlist)
            .apply()
    }

    /**
     * Tidies what was typed. A playlist link that carries a username and password (the usual
     * "get.php?username=…&password=…" link) becomes a login, because a login also gives films,
     * categories and catch-up details.
     */
    fun tidy(server: String, user: String, pass: String, playlist: String): Account {
        val link = playlist.trim()
        if (link.isNotEmpty()) {
            val url = link.toHttpUrlOrNull()
            val u = url?.queryParameter("username").orEmpty()
            val p = url?.queryParameter("password").orEmpty()
            if (url != null && u.isNotEmpty() && p.isNotEmpty()) {
                val port = if (url.port != HttpUrl.defaultPort(url.scheme)) ":" + url.port else ""
                return Account(url.scheme + "://" + url.host + port, u, p, "")
            }
            return Account("", "", "", link)
        }
        var s = server.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        val cut = s.indexOf("/player_api.php")
        if (cut > 0) s = s.substring(0, cut)
        return Account(s, user.trim(), pass.trim(), "")
    }
}

object Provider {
    const val USER_AGENT = "KueTV/1"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Fetches everything the provider lists. Call off the main thread. */
    fun load(account: Account): Library = if (account.isLogin) loadLogin(account) else loadPlaylist(account.playlist)

    // ---------------------------------------------------------------- login (server, username, password)

    private fun api(account: Account, action: String?): String {
        val base = (account.server + "/player_api.php").toHttpUrlOrNull()
            ?: throw ProviderProblem("That server address isn't a web address.")
        val b = base.newBuilder()
            .addQueryParameter("username", account.user)
            .addQueryParameter("password", account.pass)
        if (action != null) b.addQueryParameter("action", action)
        return b.build().toString()
    }

    private fun open(url: String): okhttp3.Response {
        val response = try {
            http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute()
        } catch (e: IllegalArgumentException) {
            throw ProviderProblem("That server address isn't a web address.")
        } catch (e: java.io.IOException) {
            throw ProviderProblem("Couldn't reach your provider. Check the server address and the internet connection.")
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw ProviderProblem(
                if (code == 401 || code == 403) "Your provider refused the username or password."
                else "Your provider answered with an error ($code).",
            )
        }
        return response
    }

    /** Reads a list of records, keeping only the [keys] asked for. */
    private fun records(url: String, keys: Set<String>, each: (Map<String, String>) -> Unit) {
        open(url).use { response ->
            val reader = JsonReader(response.body.charStream())
            reader.isLenient = true
            try {
                if (reader.peek() != JsonToken.BEGIN_ARRAY) return
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
                    val row = HashMap<String, String>()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        val kind = reader.peek()
                        if (key in keys && (kind == JsonToken.STRING || kind == JsonToken.NUMBER)) row[key] = reader.nextString()
                        else reader.skipValue()
                    }
                    reader.endObject()
                    each(row)
                }
            } catch (e: java.io.IOException) {
                throw ProviderProblem("Your provider's list stopped part way through. Try again.")
            } catch (e: IllegalStateException) {
                throw ProviderProblem("Your provider sent a list the app couldn't read.")
            }
        }
    }

    /** Checks the login; returns how far the provider's clock is ahead of world time, in seconds. */
    private fun checkLogin(account: Account): Int {
        val text = open(api(account, null)).use { it.body.string() }
        val info = runCatching { JsonParser.parseString(text).asJsonObject.getAsJsonObject("user_info") }.getOrNull()
            ?: throw ProviderProblem("That server didn't answer like an IPTV provider. Check the server address.")
        val field: (String) -> String = { name ->
            info.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        }
        if (field("auth") == "0") throw ProviderProblem("Your provider refused the username or password.")
        val status = field("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw ProviderProblem("Your provider says this account is \"$status\".")
        }
        return runCatching {
            val serverInfo = JsonParser.parseString(text).asJsonObject.getAsJsonObject("server_info")
            val world = serverInfo.get("timestamp_now").asString.toLong()
            val wall = parseWall(serverInfo.get("time_now").asString) ?: return@runCatching 0
            // Clocks differ by whole quarter hours; rounding removes the seconds the two readings are apart.
            (Math.round((wall / 1000 - world) / 900.0) * 900).toInt()
        }.getOrDefault(0)
    }

    /** "2026-11-15 20:00:00" read as if it were world time, in milliseconds. */
    private fun parseWall(text: String): Long? = runCatching {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        f.parse(text.trim())?.time
    }.getOrNull()

    private fun who(account: Account): String {
        val part: (String) -> String = { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return part(account.user) + "/" + part(account.pass)
    }

    private fun decode(text: String): String = runCatching {
        String(java.util.Base64.getDecoder().decode(text.trim()), Charsets.UTF_8)
    }.getOrDefault(text).trim()

    /** A series' episodes, in season then episode order. Call off the main thread. */
    fun episodes(account: Account, showId: String): List<Episode> {
        val url = (account.server + "/player_api.php").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("username", account.user)
            ?.addQueryParameter("password", account.pass)
            ?.addQueryParameter("action", "get_series_info")
            ?.addQueryParameter("series_id", showId)
            ?.build()?.toString() ?: throw ProviderProblem("That server address isn't a web address.")
        val text = open(url).use { it.body.string() }
        val holder = runCatching { JsonParser.parseString(text).asJsonObject.get("episodes") }.getOrNull() ?: return emptyList()
        // Providers send the seasons either as named lists ("1": [...], "2": [...]) or as a list of lists.
        val seasons: List<com.google.gson.JsonElement> = when {
            holder.isJsonObject -> holder.asJsonObject.entrySet().map { it.value }
            holder.isJsonArray -> holder.asJsonArray.toList()
            else -> emptyList()
        }
        val base = account.server + "/series/" + who(account) + "/"
        val out = ArrayList<Episode>()
        for (season in seasons) {
            if (!season.isJsonArray) continue
            for (row in season.asJsonArray) {
                val o = runCatching { row.asJsonObject }.getOrNull() ?: continue
                val field: (String) -> String = { name -> o.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() }
                val id = field("id").ifBlank { continue }
                val ext = field("container_extension").ifBlank { "mp4" }
                out += Episode(id, field("season").toIntOrNull() ?: 0, field("episode_num").toIntOrNull() ?: 0, field("title").trim(), "$base$id.$ext")
            }
        }
        return out.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /** A channel's guide, oldest first: what the provider holds, past and coming. Call off the main thread. */
    fun guide(account: Account, channelId: String): List<Programme> {
        val url = (account.server + "/player_api.php").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("username", account.user)
            ?.addQueryParameter("password", account.pass)
            ?.addQueryParameter("action", "get_simple_data_table")
            ?.addQueryParameter("stream_id", channelId)
            ?.build()?.toString() ?: throw ProviderProblem("That server address isn't a web address.")
        val text = open(url).use { it.body.string() }
        val rows = runCatching { JsonParser.parseString(text).asJsonObject.getAsJsonArray("epg_listings") }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<Programme>()
        for (row in rows) {
            val o = runCatching { row.asJsonObject }.getOrNull() ?: continue
            val field: (String) -> String = { name -> o.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() }
            val start = field("start_timestamp").toLongOrNull() ?: continue
            val stop = field("stop_timestamp").toLongOrNull() ?: continue
            if (stop <= start) continue
            out += Programme(decode(field("title")), decode(field("description")), start * 1000, stop * 1000, field("start"))
        }
        return out.sortedBy { it.startMs }.distinctBy { it.startMs }
    }

    /** The address that replays [channelId] from a programme's start, for its length plus a little. */
    fun catchupUrl(account: Account, channelId: String, programme: Programme, serverAheadSec: Int): String {
        val minutes = ((programme.stopMs - programme.startMs) / 60_000 + 5).coerceAtLeast(5)
        // The provider's own wording of the start is on its own clock already, which is what it wants back.
        val stamp = wallStamp(programme.startText) ?: stampFor(programme.startMs, serverAheadSec)
        return account.server + "/timeshift/" + who(account) + "/" + minutes + "/" + stamp + "/" + channelId + ".ts"
    }

    /** The address that replays [channelId] from a moment the user picked, for [minutes]. */
    fun catchupUrlAt(account: Account, channelId: String, startMs: Long, minutes: Int, serverAheadSec: Int): String =
        account.server + "/timeshift/" + who(account) + "/" + minutes + "/" + stampFor(startMs, serverAheadSec) + "/" + channelId + ".ts"

    /** "2026-11-15 20:00:00" as the "2026-11-15:20-00" a catch-up address uses; null when it isn't that shape. */
    private fun wallStamp(text: String): String? {
        val m = Regex("(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}):(\\d{2})").find(text) ?: return null
        return m.groupValues[1] + ":" + m.groupValues[2] + "-" + m.groupValues[3]
    }

    private fun stampFor(worldMs: Long, serverAheadSec: Int): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(worldMs + serverAheadSec * 1000L))
    }

    private fun loadLogin(account: Account): Library {
        val ahead = checkLogin(account)
        val base = account.server
        val who = who(account)

        val liveCategories = ArrayList<Category>()
        records(api(account, "get_live_categories"), setOf("category_id", "category_name")) { r ->
            liveCategories += Category(r["category_id"].orEmpty(), r["category_name"].orEmpty())
        }
        val channels = ArrayList<Channel>()
        records(
            api(account, "get_live_streams"),
            setOf("stream_id", "name", "stream_icon", "category_id", "tv_archive", "tv_archive_duration"),
        ) { r ->
            val id = r["stream_id"] ?: return@records
            val archive = (r["tv_archive"]?.toIntOrNull() ?: 0) > 0
            channels += Channel(
                id = id,
                name = r["name"].orEmpty().trim(),
                logo = r["stream_icon"].orEmpty(),
                categoryId = r["category_id"].orEmpty(),
                url = "$base/live/$who/$id.ts",
                catchupDays = if (archive) (r["tv_archive_duration"]?.toIntOrNull() ?: 1).coerceAtLeast(1) else 0,
            )
        }

        val filmCategories = ArrayList<Category>()
        val films = ArrayList<Film>()
        // A provider with no films still gives live TV, so a failure here is not a failure of the whole load.
        runCatching {
            records(api(account, "get_vod_categories"), setOf("category_id", "category_name")) { r ->
                filmCategories += Category(r["category_id"].orEmpty(), r["category_name"].orEmpty())
            }
            records(
                api(account, "get_vod_streams"),
                setOf("stream_id", "name", "stream_icon", "category_id", "container_extension"),
            ) { r ->
                val id = r["stream_id"] ?: return@records
                val ext = r["container_extension"].orEmpty().ifBlank { "mp4" }
                films += Film(id, r["name"].orEmpty().trim(), r["stream_icon"].orEmpty(), r["category_id"].orEmpty(), "$base/movie/$who/$id.$ext")
            }
        }
        val showCategories = ArrayList<Category>()
        val shows = ArrayList<Show>()
        runCatching {
            records(api(account, "get_series_categories"), setOf("category_id", "category_name")) { r ->
                showCategories += Category(r["category_id"].orEmpty(), r["category_name"].orEmpty())
            }
            records(api(account, "get_series"), setOf("series_id", "name", "cover", "category_id")) { r ->
                val id = r["series_id"] ?: return@records
                shows += Show(id, r["name"].orEmpty().trim(), r["cover"].orEmpty(), r["category_id"].orEmpty())
            }
        }
        if (channels.isEmpty() && films.isEmpty() && shows.isEmpty()) throw ProviderProblem("Your provider signed you in but listed no channels, films or series.")
        // A provider that lists the same id twice must not break the lists, which tell rows apart by id.
        return Library(
            liveCategories.distinctBy { it.id }, channels.distinctBy { it.id }, filmCategories.distinctBy { it.id }, films.distinctBy { it.id },
            showCategories.distinctBy { it.id }, shows.distinctBy { it.id },
            account = account, serverAheadSec = ahead,
        )
    }

    // ---------------------------------------------------------------- one playlist link

    private val attribute = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    private fun loadPlaylist(link: String): Library {
        open(link).use { response -> return parsePlaylist(BufferedReader(response.body.charStream())) }
    }

    /** Reads a playlist's text. Films are the entries whose address holds "/movie/"; series are left out for now. */
    fun parsePlaylist(reader: BufferedReader): Library {
        val liveNames = LinkedHashSet<String>()
        val filmNames = LinkedHashSet<String>()
        val channels = ArrayList<Channel>()
        val films = ArrayList<Film>()
        run {
            var name = ""
            var logo = ""
            var group = ""
            var days = 0
            var pending = false
            var count = 0
            try {
                while (true) {
                    val line = reader.readLine()?.trim() ?: break
                    if (line.startsWith("#EXTINF")) {
                        val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        // The name follows the first comma after the last quoted value, so a comma inside the name is kept.
                        val comma = line.indexOf(',', line.lastIndexOf('"').coerceAtLeast(0))
                        name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                        logo = attrs["tvg-logo"].orEmpty()
                        group = attrs["group-title"].orEmpty().ifBlank { "Other" }
                        days = if (attrs["catchup"] != null || attrs["catchup-type"] != null) (attrs["catchup-days"]?.toIntOrNull() ?: 1).coerceAtLeast(1) else 0
                        pending = true
                    } else if (pending && line.isNotEmpty() && !line.startsWith("#")) {
                        pending = false
                        count++
                        val id = count.toString()
                        if (line.contains("/series/")) continue
                        if (line.contains("/movie/")) {
                            filmNames += group
                            films += Film(id, name, logo, group, line)
                        } else {
                            liveNames += group
                            channels += Channel(id, name, logo, group, line, days)
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                throw ProviderProblem("Your playlist stopped part way through. Try again.")
            }
        }
        if (channels.isEmpty() && films.isEmpty()) throw ProviderProblem("That link didn't hold a playlist the app could read.")
        return Library(liveNames.map { Category(it, it) }, channels, filmNames.map { Category(it, it) }, films)
    }
}
