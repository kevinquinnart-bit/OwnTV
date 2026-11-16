package art.kquinn.kuetv

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A problem with an add-on the user can read on screen. */
class AddOnProblem(message: String) : Exception(message)

/** One add-on the user has added: where it answers, its name, and what it can be asked for. */
data class AddOn(val base: String, val name: String, val streams: Boolean, val subtitles: Boolean) {
    fun toJson(): String = JsonObject().apply {
        addProperty("base", base); addProperty("name", name); addProperty("streams", streams); addProperty("subtitles", subtitles)
    }.toString()

    companion object {
        fun fromJson(text: String): AddOn? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            AddOn(o.get("base").asString, o.get("name").asString, o.get("streams").asBoolean, o.get("subtitles").asBoolean)
        }.getOrNull()
    }
}

/** What the add-ons had for one film or episode, the best to try first. */
class Search(val streams: List<Stream>)

/**
 * Add-ons, of the kind made for Stremio: small web services the user adds by a link. Asked about a film
 * or an episode, an add-on answers with places it can be played from. KueTV comes with none.
 *
 * The films and series are the ones already in the provider's lists. To ask an add-on about one, its
 * name is first looked up in a public film index to get the number add-ons know it by.
 */
object AddOns {
    /** The public film and series index, searched by name. */
    var index = "https://v3-cinemeta.strem.io"

    /** The most streams worth lining up for one film; each that fails costs the viewer a wait. */
    const val MOST = 8

    private val http: OkHttpClient by lazy { Provider.http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build() }
    private val ids = HashMap<String, String>()

    private fun fetch(url: String): JsonObject? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", Provider.USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) null else JsonParser.parseString(r.body.string()).asJsonObject
        }
    }.getOrNull()

    private fun JsonObject.text(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.list(name: String): List<JsonElement> = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    private fun JsonObject.inner(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    // ------------------------------------------------------------------------------------------ adding one

    /** The link as typed or pasted, made into the address of the add-on's description. */
    fun tidyLink(typed: String): String {
        var link = typed.trim()
        if (link.startsWith("stremio://", ignoreCase = true)) link = "https://" + link.substring(10)
        if (!link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true)) link = "https://$link"
        if (!link.substringBefore('?').endsWith("/manifest.json")) link = link.substringBefore('?').trimEnd('/') + "/manifest.json"
        return link
    }

    /** Reads an add-on from its link. Call off the main thread. */
    fun read(typed: String): AddOn {
        if (typed.isBlank()) throw AddOnProblem("Paste the add-on's link first.")
        val link = tidyLink(typed)
        val o = fetch(link) ?: throw AddOnProblem("That link didn't answer like an add-on. Check it and try again.")
        val offers = o.list("resources").map { r -> if (r.isJsonObject) r.asJsonObject.text("name") else runCatching { r.asString }.getOrDefault("") }
        val streams = "stream" in offers
        val subtitles = "subtitles" in offers
        if (!streams && !subtitles) throw AddOnProblem("That add-on has no streams or subtitles, so KueTV has no use for it.")
        val base = link.substringBefore('?').removeSuffix("/manifest.json")
        return AddOn(base, o.text("name").ifBlank { base.substringAfter("://").substringBefore('/') }, streams, subtitles)
    }

    // ------------------------------------------------------------------------------------------ which film is it

    private val year = Regex("[(\\[]((?:19|20)\\d{2})[)\\]]|\\s-\\s*((?:19|20)\\d{2})\\s*$")
    private val bareYear = Regex("\\s((?:19|20)\\d{2})$")
    private val languagePrefix = Regex("^[A-Z]{2,3}\\s*[-|:]\\s*")
    private val marks = Regex("[^\\p{L}\\p{N}]+")

    /** A provider's name for a film ("EN - A Film 4K (2024)") as its plain title and its year, when it gives one. */
    fun title(name: String): Pair<String, Int?> {
        var text = Versions.read(name).name
        val found = year.findAll(text).lastOrNull()
        val y = found?.let { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
        if (found != null) text = text.removeRange(found.range)
        text = languagePrefix.replace(text.trim(), "")
        text = text.replace(Regex("[(\\[]\\s*[)\\]]"), " ").replace(Regex("\\s+"), " ")
            .trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() && it !in ")!?." }
        return (if (text.isEmpty()) name.trim() else text) to y
    }

    private fun plain(text: String): String = marks.replace(text.lowercase().replace("&", " and "), " ").trim()

    /** The number add-ons know a film ("movie") or series by, found by its name and year; null when it can't be told. */
    fun idFor(type: String, name: String): String? {
        val key = "$type:$name"
        synchronized(ids) { ids[key] }?.let { return it }
        val first = title(name)
        var id = look(type, first.first, first.second)
        if (id == null && first.second == null) {
            // "A Film 2024" may be a film called "A Film 2024", tried above, or "A Film" from 2024.
            val end = bareYear.find(first.first)
            if (end != null) id = look(type, first.first.removeRange(end.range).trim(), end.groupValues[1].toInt())
        }
        if (id != null) synchronized(ids) { ids[key] = id }
        return id
    }

    private fun look(type: String, title: String, y: Int?): String? {
        if (title.isBlank()) return null
        val asked = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val metas = fetch("$index/catalog/$type/top/search=$asked.json")?.list("metas").orEmpty().mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
        val same = metas.filter { it.text("id").startsWith("tt") && plain(it.text("name")) == plain(title) }
        val match = if (y == null) same.firstOrNull() else same.firstOrNull { m ->
            // A series' year reads "2019-2022"; a film in the provider's list can be a year out either way.
            val theirs = m.text("releaseInfo").ifEmpty { m.text("year") }.take(4).toIntOrNull()
            theirs != null && kotlin.math.abs(theirs - y) <= 1
        }
        return match?.text("id")
    }

    // ------------------------------------------------------------------------------------------ finding streams

    /**
     * Everything the add-ons can play [name] from, the best to try first. For an episode, give its
     * [season] and [episode]. Blocks while the add-ons are asked; call off the main thread.
     */
    fun streamsFor(addOns: List<AddOn>, type: String, name: String, season: Int? = null, episode: Int? = null): Search {
        val givers = addOns.filter { it.streams }
        if (givers.isEmpty()) return Search(emptyList())
        val id = idFor(type, name) ?: return Search(emptyList())
        val asked = if (season != null && episode != null) "$id:$season:$episode" else id
        val pool = Executors.newFixedThreadPool((addOns.size).coerceIn(1, 6))
        try {
            // All the add-ons are asked at once, and their answers kept in the order the add-ons were added.
            val answers = givers.map { a -> pool.submit(Callable { fetch(a.base + "/stream/" + type + "/" + asked + ".json")?.list("streams").orEmpty() }) }
            val subtitleAnswers = addOns.filter { it.subtitles }.map { a ->
                pool.submit(Callable { fetch(a.base + "/subtitles/" + type + "/" + asked + ".json")?.list("subtitles").orEmpty() })
            }
            val rows = answers.flatMap { runCatching { it.get(15, TimeUnit.SECONDS) }.getOrDefault(emptyList()) }.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
            val spare = subtitleAnswers.asSequence().flatMap { runCatching { it.get(15, TimeUnit.SECONDS) }.getOrDefault(emptyList()).asSequence() }
                .mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.firstOrNull { english(it.text("lang")) }?.text("url")?.takeIf { it.startsWith("http") }
            // A stream is either a web address to play from, or a torrent, known by its fingerprint.
            val web: (JsonObject) -> Boolean = { it.text("url").startsWith("http://") || it.text("url").startsWith("https://") }
            val address: (JsonObject) -> String = { row ->
                if (web(row)) row.text("url")
                else magnet(row.text("infoHash"), row.list("sources").map { runCatching { it.asString }.getOrDefault("") }, row.text("fileIdx").toIntOrNull(), season, episode)
            }
            val playable = rows.filter { web(it) || it.text("infoHash").isNotBlank() }
            val ranked = playable.withIndex()
                .sortedWith(compareByDescending<IndexedValue<JsonObject>> { score(words(it.value)) + if (web(it.value)) 0 else torrentScore(words(it.value)) }.thenBy { it.index })
                .distinctBy { address(it.value) }.take(MOST).map { (_, row) ->
                    val own = row.list("subtitles").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.firstOrNull { english(it.text("lang")) }?.text("url")?.takeIf { it.startsWith("http") }
                    val headers = row.inner("behaviorHints")?.inner("proxyHeaders")?.inner("request")?.entrySet()
                        ?.filter { it.value.isJsonPrimitive }?.associate { it.key to it.value.asString }.orEmpty()
                    Stream("Add-on stream", address(row), headers, own ?: spare)
                }
            return Search(ranked)
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------------------------------ torrents

    private val openTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce", "udp://open.demonii.com:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce", "udp://exodus.desync.com:6969/announce",
    )

    /** What a torrent's address asks for: the torrent itself, and which file in it. */
    class Magnet(val magnet: String, val file: Int?, val season: Int?, val episode: Int?)

    /**
     * A torrent's address: its fingerprint, where to ask who has it, and, for KueTV's own use at the
     * end, which file in it is wanted (by number when the add-on gave one, else by season and episode).
     */
    fun magnet(infoHash: String, sources: List<String>, file: Int?, season: Int?, episode: Int?): String {
        val trackers = (sources.filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") } + openTrackers).distinct().take(12)
        return "magnet:?xt=urn:btih:" + infoHash.trim().lowercase() +
            trackers.joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") } +
            (if (file != null) "&x.kue.file=$file" else "") +
            (if (season != null && episode != null) "&x.kue.se=${season}x$episode" else "")
    }

    fun readMagnet(address: String): Magnet {
        val file = Regex("&x\\.kue\\.file=(\\d+)").find(address)?.groupValues?.get(1)?.toIntOrNull()
        val se = Regex("&x\\.kue\\.se=(\\d+)x(\\d+)").find(address)
        return Magnet(address.replace(Regex("&x\\.kue\\.[a-z]+=[^&]*"), ""), file, se?.groupValues?.get(1)?.toIntOrNull(), se?.groupValues?.get(2)?.toIntOrNull())
    }

    private val filmEndings = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts")

    /**
     * Which file in a torrent is the film: the one the add-on named; else, for an episode, the biggest
     * film file whose name carries that season and episode; else the biggest film file. Null when none.
     */
    fun pickFile(paths: List<String>, sizes: List<Long>, named: Int?, season: Int?, episode: Int?): Int? {
        if (named != null && named in paths.indices) return named
        val films = paths.indices.filter { i ->
            val name = paths[i].substringAfterLast('/').lowercase()
            name.substringAfterLast('.', "") in filmEndings && !name.contains("sample")
        }
        if (season != null && episode != null) {
            val marks = Regex("(?i)(s0*${season}[ ._-]*e0*$episode(?!\\d))|((?<!\\d)0*${season}x0*$episode(?!\\d))")
            films.filter { marks.containsMatchIn(paths[it].substringAfterLast('/')) }.maxByOrNull { sizes[it] }?.let { return it }
            // A torrent of a whole season with no episode named that way can't be told apart: better none than the wrong one.
            if (films.size > 1) return null
        }
        return films.maxByOrNull { sizes[it] }
    }

    private val sharers = Regex("\\uD83D\\uDC64\\s*(\\d+)")
    private val howBig = Regex("(\\d+(?:\\.\\d+)?)\\s*(GB|MB)")

    /**
     * What counts for or against a torrent, on top of [score]. A web address is quicker to start, so a
     * torrent sits just behind one of the same quality. One with almost nobody sharing it rarely plays,
     * and a very big one may not fit in the storage here.
     */
    fun torrentScore(text: String): Int {
        var s = -10
        val sharing = sharers.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (sharing != null && sharing < 3) s -= 300
        val size = howBig.findAll(text).map { it.groupValues[1].toDouble() * if (it.groupValues[2] == "GB") 1.0 else 0.001 }.maxOrNull()
        if (size != null && size > 6.0) s -= 150
        return s
    }

    private fun english(lang: String): Boolean = lang.lowercase().let { it == "en" || it == "eng" || it.startsWith("english") || it.startsWith("en-") }

    /** Everything an add-on says about a stream, in capitals. Used to judge it, never shown. */
    private fun words(row: JsonObject): String =
        (row.text("name") + " " + row.text("title") + " " + row.text("description") + " " + row.inner("behaviorHints")?.text("filename").orEmpty()).uppercase()

    private fun has(text: String, vararg any: String): Boolean = any.any { Regex("(?<![A-Z0-9])" + it + "(?![A-Z0-9])").containsMatchIn(text) }

    private val flag = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
    private const val FLAG_GB = "🇬🇧"
    private const val FLAG_US = "🇺🇸"

    /**
     * How good a stream is to try, from what the add-on says about it; higher is tried sooner.
     * Full HD comes first, then HD, then 4K (the heaviest to carry), then anything else. A stream marked
     * as another language with no sign of English goes to the back, as do copies filmed in a cinema.
     * Sound this app's player can't be sure of playing (DTS, TrueHD) counts against a stream.
     */
    fun score(text: String): Int {
        var s = when {
            has(text, "1080P", "1080I", "FHD", "FULL ?HD") -> 300
            has(text, "720P") -> 200
            has(text, "2160P", "4K", "UHD") -> 100
            has(text, "480P", "576P", "360P", "SD") -> 0
            else -> 50
        }
        if (has(text, "DTS", "DTS-?HD", "DTS-?X", "TRUEHD", "TRUE-HD")) s -= 150
        if (has(text, "REMUX")) s -= 60
        if (has(text, "DV", "DOVI", "DOLBY ?VISION")) s -= 30
        if (has(text, "3D", "HSBS", "SBS")) s -= 400
        if (has(text, "CAM", "HDCAM", "CAMRIP", "TS", "HDTS", "TELESYNC", "TELECINE", "SCR", "SCREENER")) s -= 500
        val english = has(text, "ENG", "ENGLISH", "MULTI", "DUAL", "DUAL ?AUDIO") || text.contains(FLAG_GB) || text.contains(FLAG_US)
        val other = has(
            text, "ITA", "ITALIAN", "FRENCH", "TRUEFRENCH", "VFF", "VFQ", "VOSTFR", "GERMAN", "DEUTSCH", "SPANISH", "LATINO", "CASTELLANO", "ESP",
            "RUS", "RUSSIAN", "HINDI", "TAMIL", "TELUGU", "POLISH", "LEKTOR", "DUBLADO", "PORTUGUESE", "TURKISH", "DUBBED",
        ) || flag.findAll(text).any { it.value != FLAG_GB && it.value != FLAG_US }
        if (other && !english) s -= 1000
        return s
    }
}
