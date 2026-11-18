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

data class Film(val id: String, val name: String, val poster: String, val categoryId: String, val url: String, val added: Long = 0)

data class Show(val id: String, val name: String, val poster: String, val categoryId: String, val about: About = About(), val added: Long = 0)

/** What a film or series is about, as far as the provider says. Any part may be empty. */
data class About(
    val plot: String = "", val genre: String = "", val year: String = "", val rating: String = "", val minutes: Int = 0,
    /** The trailer, as the provider gives it: a YouTube video's id or address. May be missing in lists kept from older versions. */
    val trailer: String? = null,
) {
    /** "2024 · Drama · 7.1 · 1 h 52 min", leaving out whatever isn't known. */
    val line: String
        get() = listOf(
            year, genre, rating,
            when {
                minutes <= 0 -> ""
                minutes < 60 -> "$minutes min"
                else -> (minutes / 60).toString() + " h " + (minutes % 60) + " min"
            },
        ).filter { it.isNotBlank() }.joinToString(" · ")
    val isEmpty: Boolean get() = plot.isBlank() && line.isBlank()

    companion object {
        /** A rating as the provider writes it ("7.123", "0", "") as one figure after the point, or nothing when there is none. */
        fun rating(text: String): String = text.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { String.format(java.util.Locale.UK, "%.1f", it) }.orEmpty()

        /** The year out of "2024-03-01", "2024" or "01/03/2024". */
        fun year(text: String): String = Regex("(19|20)\\d{2}").find(text)?.value.orEmpty()
    }
}

data class Episode(
    val id: String, val season: Int, val number: Int, val title: String, val url: String,
    /** The episode's own picture, when the provider has one. */
    val image: String = "",
    /** When it first aired, as the provider writes it ("2021-03-12"); empty when not known. */
    val aired: String = "",
)

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
) {
    /** The channels with the versions of the same channel (4K, FHD, HD, SD) kept together as one. */
    val groups: List<ChannelGroup> by lazy {
        // A TV channel from an add-on is always a row of its own: it plays only through the add-ons, so it can't be
        // a backup version in another channel's row.
        val (fromAddOns, rest) = channels.partition { it.url.startsWith(AddOns.TV) }
        Versions.group(rest) + fromAddOns.map { ChannelGroup(it.name, listOf(Version(it, "", 0))) }
    }

    /**
     * The same lists with the categories the user has chosen to hide taken out, and everything filed
     * only under them. Something also filed under a category that is still shown stays.
     */
    fun without(hiddenLive: Set<String>, hiddenFilms: Set<String>, hiddenShows: Set<String>): Library {
        if (hiddenLive.isEmpty() && hiddenFilms.isEmpty() && hiddenShows.isEmpty()) return this
        return Library(
            liveCategories.filter { it.id !in hiddenLive }, channels.filter { it.categoryId !in hiddenLive },
            filmCategories.filter { it.id !in hiddenFilms }, films.filter { it.categoryId !in hiddenFilms },
            showCategories.filter { it.id !in hiddenShows }, shows.filter { it.categoryId !in hiddenShows },
            account, serverAheadSec,
        )
    }

    /** The same lists with channels given the names the user chose for them (by channel id). */
    fun renamed(names: Map<String, String>): Library {
        if (names.isEmpty()) return this
        return Library(
            liveCategories, channels.map { c -> names[c.id]?.let { c.copy(name = it) } ?: c },
            filmCategories, films, showCategories, shows, account, serverAheadSec,
        )
    }

    /** The same lists with each kind's categories in the user's own order (one not in it goes after, as sent). */
    fun inOrder(liveOrder: List<String>, filmOrder: List<String>, showOrder: List<String>): Library {
        if (liveOrder.isEmpty() && filmOrder.isEmpty() && showOrder.isEmpty()) return this
        return Library(
            ordered(liveCategories, liveOrder), channels, ordered(filmCategories, filmOrder), films,
            ordered(showCategories, showOrder), shows, account, serverAheadSec,
        )
    }

    /**
     * The same lists with channels from free channel lists added. A free channel with the same name as
     * one of the provider's joins that channel's row as a backup version; the rest get rows of their own.
     * Each group in a free list becomes a category called "Free: " and its name.
     */
    fun withFree(free: List<Channel>): Library {
        if (free.isEmpty()) return this
        val have = channels.mapTo(HashSet()) { it.id }
        val added = free.filter { have.add(it.id) }
        // The free ones go at the end, after the provider's: one with every free channel, then one for each
        // group in the lists and each add-on's TV list. The user can put them anywhere in Categories.
        val categories = listOf(Category(FREE_ALL, "Free: all free channels")) +
            added.map { it.categoryId }.distinct().map { Category(it, "Free: " + it.removePrefix(FREE)) }
        return Library(liveCategories + categories, channels + added, filmCategories, films, showCategories, shows, account, serverAheadSec)
    }

    companion object {
        /** How a free channel's number and category begin, to tell them from the provider's. */
        const val FREE = "free:"

        /** [categories] in the order of [order], by id. Ones not in it come after, in the order they were in. */
        fun ordered(categories: List<Category>, order: List<String>): List<Category> {
            if (order.isEmpty()) return categories
            val at = order.withIndex().associate { it.value to it.index }
            return categories.sortedBy { at[it.id] ?: Int.MAX_VALUE }
        }

        /** The category holding every free channel. */
        const val FREE_ALL = "free:\u0000all"
    }

    /** For each channel as the provider lists it, the one row it sits behind. */
    val groupOf: Map<String, ChannelGroup> by lazy { groups.flatMap { g -> g.versions.map { it.channel.id to g } }.toMap() }
}

/** One version of a channel: the channel as the provider lists it, and its quality as read from its name. */
class Version(val channel: Channel, val label: String, val rank: Int) {
    /** From a free channel list, not from the provider: kept behind the provider's versions, as a backup. */
    val free: Boolean get() = channel.id.startsWith(Library.FREE)
}

/**
 * One channel with every version the provider lists of it, best picture first. [start] is the one
 * to play first: the best that is not 4K, because 4K is the heaviest to carry and the likeliest to stop.
 */
class ChannelGroup(val name: String, val versions: List<Version>) {
    val start: Int = when {
        // The provider's own versions come before any free one, whatever their picture.
        versions.any { !it.free } -> versions.indexOfFirst { !it.free && it.rank < Versions.RANK_4K }.coerceAtLeast(0)
        else -> versions.indexOfFirst { it.rank < Versions.RANK_4K }.coerceAtLeast(0)
    }
    val id: String get() = versions[0].channel.id

    /** The lightest of the provider's own versions, for the small pictures shown while browsing; a free one only if there is no other. */
    val lightest: Channel get() = (versions.lastOrNull { !it.free } ?: versions.last()).channel
    val logo: String get() = versions.firstOrNull { it.channel.logo.isNotBlank() }?.channel?.logo.orEmpty()
}

object Versions {
    const val RANK_4K = 5
    private const val RANK_FHD = 4
    private const val RANK_HD = 3
    private const val RANK_PLAIN = 2
    private const val RANK_SD = 1

    /** The words in a channel's name that say which version it is, standing on their own. Now TV counts as one. */
    private val word = Regex(
        "(?<![A-Za-z0-9])(4K|UHD|2160P|FHD|FULL ?HD|1080[PI]|HD|720P|SD|576[PI]|480P|HEVC|H\\.?265|[56]0 ?FPS|NOW ?TV)(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val emptyBrackets = Regex("[(\\[]\\s*[)\\]]")
    private val spaces = Regex("\\s+")
    private val notNameMarks = Regex("[^\\p{L}\\p{N}+]+")
    private val small = listOf("ᶠᴴᴰ" to " FHD ", "ᵁᴴᴰ" to " UHD ", "ᴴᴰ" to " HD ", "ˢᴰ" to " SD ", "⁴ᴷ" to " 4K ")

    /** What a name says: the name without the version words, the version's label, and how good its picture is. */
    class Reading(val name: String, val label: String, val rank: Int)

    fun read(full: String): Reading {
        var text = full
        for ((from, to) in small) text = text.replace(from, to)
        var rank = RANK_PLAIN
        var quality = "Standard"
        var extra = ""
        for (m in word.findAll(text)) {
            val w = m.value.uppercase().replace(" ", "").replace(".", "")
            val found = when (w) {
                "4K", "UHD", "2160P" -> RANK_4K to "4K"
                "FHD", "FULLHD", "1080P", "1080I" -> RANK_FHD to "FHD"
                "HD", "720P" -> RANK_HD to "HD"
                "SD", "576P", "576I", "480P" -> RANK_SD to "SD"
                else -> null
            }
            if (found == null) {
                // "| NowTV" is the same channel by another route, so it joins the row as one more version.
                val tag = if (w.endsWith("FPS")) w.dropLast(3) + "fps" else if (w == "NOWTV") "Now TV" else "HEVC"
                if (!extra.contains(tag)) extra += " $tag"
            } else if (quality == "Standard" || found.first > rank) {
                rank = found.first
                quality = found.second
            }
        }
        val name = word.replace(text, " ")
            .replace(emptyBrackets, " ")
            .replace(spaces, " ")
            // Whatever joined the version word to the name (a bar, a dash, a colon, odd spaces) goes with it.
            .trimStart { !it.isLetterOrDigit() && it !in "([+'\"#" }
            .trimEnd { !it.isLetterOrDigit() && it !in ")]+!?.'\"&%" }
        // A channel whose whole name is a version word keeps its name.
        return if (name.isEmpty()) Reading(full.trim(), "Standard", RANK_PLAIN) else Reading(name, quality + extra, rank)
    }

    /**
     * Puts channels with the same name, once the version words are taken out, together. Groups stay in
     * the provider's order, by the first version it lists. Within a group the best picture comes first.
     */
    fun group(channels: List<Channel>): List<ChannelGroup> {
        val byName = LinkedHashMap<String, ArrayList<Version>>()
        val names = HashMap<String, String>()
        for (channel in channels) {
            val r = read(channel.name)
            val key = r.name.lowercase().replace(notNameMarks, " ").trim().ifEmpty { r.name.lowercase() }
            val free = channel.id.startsWith(Library.FREE)
            byName.getOrPut(key) { names[key] = r.name; ArrayList() } += Version(channel, if (free) "Free " + r.label else r.label, r.rank)
        }
        // Best picture first among the provider's own; then the free ones, best first, as the backup.
        return byName.map { (key, versions) -> ChannelGroup(names.getValue(key), versions.sortedWith(compareBy<Version> { it.free }.thenByDescending { it.rank })) }
    }
}

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
    // Each profile has its own; Main's are kept where they always were.
    private val prefs = context.getSharedPreferences(Profiles.scoped(context, "favourites"), Context.MODE_PRIVATE)
    var keys by androidx.compose.runtime.mutableStateOf(prefs.getStringSet("keys", emptySet()).orEmpty().toSet())
        private set

    fun toggle(key: String) {
        keys = if (key in keys) keys - key else keys + key
        prefs.edit().putStringSet("keys", keys).apply()
    }

    /** The user's own order for favourite channels, by their keys. Channels not in it come after, as listed. */
    var order by androidx.compose.runtime.mutableStateOf(prefs.getString("order", "").orEmpty().split('\n').filter { it.isNotEmpty() })
        private set

    fun putInOrder(keys: List<String>) {
        order = keys
        prefs.edit().putString("order", keys.joinToString("\n")).apply()
    }

    /** Where a channel comes in the user's order, from any of its versions' keys; past the end when not in it. */
    fun rank(keys: List<String>): Int = keys.minOfOrNull { k -> order.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } } ?: Int.MAX_VALUE

    /** Takes on the favourites copied from another device, in place of the ones here. */
    fun replaceAll(all: Set<String>) {
        keys = all
        prefs.edit().putStringSet("keys", keys).apply()
    }

    /** A channel with several versions: all of them are favourites together, or none. */
    fun toggleAll(all: List<String>) {
        keys = if (all.any { it in keys }) keys - all.toSet() else keys + all
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

    // Notes in square brackets, and the circled letters some lists put after a name (Ⓖ for geo-blocked, Ⓢ, Ⓨ).
    private val freeNote = Regex("\\s*\\[[^\\]]*]|\\s*[\\u24B6-\\u24E9]")

    /** Signs in (or opens the playlist) to see that the provider answers. Throws a [ProviderProblem] saying why not. */
    fun reach(account: Account) {
        if (account.isLogin) checkLogin(account) else open(account.playlist).close()
    }

    /** The channels in a free channel list, read from its link. Call off the main thread. */
    fun loadFree(link: String): List<Channel> = try {
        // A link can end "#groups=UK,Ireland" to take only those groups from a long list.
        val wanted = link.substringAfter("#groups=", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        open(link.substringBefore('#')).use { parsePlaylist(BufferedReader(it.body.charStream())).channels }
            .filter { wanted.isEmpty() || it.categoryId in wanted }
            .map { c ->
            // Known by its address, so a favourite survives the list being reordered. Notes in square brackets
            // ("[Geo-blocked]") are dropped from the name so it can match the provider's channel of the same name.
            Channel(Library.FREE + Integer.toHexString(c.url.hashCode()), c.name.replace(freeNote, "").trim().ifEmpty { c.name }, c.logo, Library.FREE + c.categoryId, c.url, 0)
        }.distinctBy { it.id }
    } catch (e: ProviderProblem) {
        throw ProviderProblem(
            e.message.orEmpty().replace("Your provider", "The list's site").replace("your provider", "the list's site")
                .replace("Your playlist", "The list").replace("the server address and ", ""),
        )
    }

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

    /** What the provider says a film is about. Call off the main thread. */
    fun filmAbout(account: Account, filmId: String): About {
        val url = (account.server + "/player_api.php").toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("username", account.user)
            ?.addQueryParameter("password", account.pass)
            ?.addQueryParameter("action", "get_vod_info")
            ?.addQueryParameter("vod_id", filmId)
            ?.build()?.toString() ?: return About()
        val text = open(url).use { it.body.string() }
        val info = runCatching { JsonParser.parseString(text).asJsonObject.getAsJsonObject("info") }.getOrNull() ?: return About()
        val field: (String) -> String = { name -> info.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim() }
        val seconds = field("duration_secs").toIntOrNull() ?: 0
        // Some providers give only "01:52:00".
        val clock = Regex("(\\d+):(\\d{2}):(\\d{2})").find(field("duration"))
        val minutes = if (seconds > 0) seconds / 60 else clock?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() } ?: 0
        return About(
            plot = field("plot").ifBlank { field("description") },
            genre = field("genre"),
            year = About.year(field("releasedate").ifBlank { field("release_date") }.ifBlank { field("year") }),
            rating = About.rating(field("rating")),
            minutes = minutes,
            trailer = field("youtube_trailer").ifBlank { null },
        )
    }

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
                val info = runCatching { o.get("info")?.takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()
                val infoField: (String) -> String = { name -> info?.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() }
                out += Episode(
                    id, field("season").toIntOrNull() ?: 0, field("episode_num").toIntOrNull() ?: 0, field("title").trim(), "$base$id.$ext",
                    image = infoField("movie_image").ifBlank { infoField("cover_big") },
                    aired = infoField("air_date").ifBlank { infoField("releasedate") }.ifBlank { infoField("release_date") },
                )
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
                setOf("stream_id", "name", "stream_icon", "category_id", "container_extension", "added"),
            ) { r ->
                val id = r["stream_id"] ?: return@records
                val ext = r["container_extension"].orEmpty().ifBlank { "mp4" }
                films += Film(id, r["name"].orEmpty().trim(), r["stream_icon"].orEmpty(), r["category_id"].orEmpty(), "$base/movie/$who/$id.$ext", r["added"]?.toLongOrNull() ?: 0)
            }
        }
        val showCategories = ArrayList<Category>()
        val shows = ArrayList<Show>()
        runCatching {
            records(api(account, "get_series_categories"), setOf("category_id", "category_name")) { r ->
                showCategories += Category(r["category_id"].orEmpty(), r["category_name"].orEmpty())
            }
            records(api(account, "get_series"), setOf("series_id", "name", "cover", "category_id", "plot", "genre", "releaseDate", "release_date", "rating", "youtube_trailer", "last_modified")) { r ->
                val id = r["series_id"] ?: return@records
                val about = About(
                    plot = r["plot"].orEmpty().trim(), genre = r["genre"].orEmpty().trim(),
                    year = About.year(r["releaseDate"].orEmpty().ifBlank { r["release_date"].orEmpty() }), rating = About.rating(r["rating"].orEmpty()),
                    trailer = r["youtube_trailer"]?.trim()?.ifBlank { null },
                )
                shows += Show(id, r["name"].orEmpty().trim(), r["cover"].orEmpty(), r["category_id"].orEmpty(), about, r["last_modified"]?.toLongOrNull() ?: 0)
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
