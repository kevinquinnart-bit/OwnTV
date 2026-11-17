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
data class AddOn(
    val base: String, val name: String, val streams: Boolean, val subtitles: Boolean,
    /** The lists of films and series it offers to browse, each written "type|id|name". */
    val catalogues: List<String> = emptyList(),
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("base", base); addProperty("name", name); addProperty("streams", streams); addProperty("subtitles", subtitles)
        add("catalogues", com.google.gson.JsonArray().apply { catalogues.forEach { add(it) } })
    }.toString()

    companion object {
        fun fromJson(text: String): AddOn? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            AddOn(
                o.get("base").asString, o.get("name").asString, o.get("streams").asBoolean, o.get("subtitles").asBoolean,
                // One kept by an older version has none noted yet.
                o.get("catalogues")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty(),
            )
        }.getOrNull()
    }
}

/** A list of films or series to browse, from the film index or an add-on. [type] is "movie" or "series". */
data class Catalogue(val base: String, val type: String, val id: String, val name: String, val from: String) {
    /** What it is known by as a category in Films or Series. */
    val key: String get() = PREFIX + base + "|" + type + "|" + id

    companion object {
        const val PREFIX = "cat:"
    }
}

/** One film or series in a catalogue. [id] is the number add-ons know it by. */
data class CatItem(val id: String, val type: String, val name: String, val poster: String, val about: About)

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
        return fromManifest(link, o)
    }

    /** An add-on from its description, already fetched (from its link, or from a Stremio account). */
    fun fromManifest(link: String, o: JsonObject): AddOn {
        val offers = o.list("resources").map { r -> if (r.isJsonObject) r.asJsonObject.text("name") else runCatching { r.asString }.getOrDefault("") }
        val streams = "stream" in offers
        val subtitles = "subtitles" in offers
        val catalogues = readCatalogues(o)
        if (!streams && !subtitles && catalogues.isEmpty()) throw AddOnProblem("That add-on has no streams or subtitles, so KueTV has no use for it.")
        val base = link.substringBefore('?').removeSuffix("/manifest.json")
        return AddOn(base, o.text("name").ifBlank { base.substringAfter("://").substringBefore('/') }, streams, subtitles, catalogues)
    }

    // ------------------------------------------------------------------------------------------ catalogues

    /**
     * The lists of films and series an add-on's description offers, each as "type|id|name". Ones that
     * only answer a search, or need something chosen first (a genre, say), are left out.
     */
    fun readCatalogues(manifest: JsonObject): List<String> = manifest.list("catalogs").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.mapNotNull { c ->
        val type = c.text("type")
        val id = c.text("id")
        val needed = c.list("extraRequired").map { runCatching { it.asString }.getOrDefault("") } +
            c.list("extra").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.filter { it.get("isRequired")?.let { r -> runCatching { r.asBoolean }.getOrDefault(false) } == true }.map { it.text("name") }
        if ((type != "movie" && type != "series") || id.isBlank() || needed.isNotEmpty()) null
        else type + "|" + id + "|" + c.text("name").ifBlank { id }.replace("|", " ")
    }.distinct()

    @Volatile private var indexCatalogues: List<Catalogue>? = null

    /** One of an add-on's catalogues, as it is kept ("type|id|name"), ready to ask for. */
    fun catalogueOf(addOn: AddOn, written: String): Catalogue? = toCatalogue(addOn.base, written, addOn.name)

    private fun toCatalogue(base: String, written: String, from: String): Catalogue? {
        val p = written.split("|", limit = 3)
        return if (p.size < 3) null else Catalogue(base, p[0], p[1], p[2], from)
    }

    /** Every list of films and series there is to browse: the film index's own, then each add-on's. Call off the main thread. */
    fun catalogues(addOns: List<AddOn>): List<Catalogue> {
        val own = indexCatalogues ?: fetch("$index/manifest.json")?.let { m -> readCatalogues(m).mapNotNull { toCatalogue(index, it, "") } }?.also { indexCatalogues = it } ?: emptyList()
        return (own + addOns.filter { it.base != index }.flatMap { a -> a.catalogues.mapNotNull { toCatalogue(a.base, it, a.name) } }).also { lastCatalogues = it }
    }

    /** What [catalogues] last found, so a screen coming back has them at once. */
    @Volatile var lastCatalogues: List<Catalogue> = emptyList()
        private set

    private fun item(type: String, m: JsonObject): CatItem? {
        val id = m.text("id")
        val name = m.text("name")
        if (id.isBlank() || name.isBlank()) return null
        val genres = (m.list("genres").ifEmpty { m.list("genre") }).mapNotNull { runCatching { it.asString }.getOrNull() }.take(2).joinToString(", ")
        val about = About(
            plot = m.text("description"), genre = genres, year = m.text("releaseInfo").ifEmpty { m.text("year") },
            rating = About.rating(m.text("imdbRating")), minutes = Regex("\\d+").find(m.text("runtime"))?.value?.toIntOrNull() ?: 0,
        )
        return CatItem(id, type, name, m.text("poster"), about)
    }

    /** Notes the number a film or series is known by, so asking for its streams by name finds it at once. */
    fun know(type: String, name: String, id: String) = synchronized(ids) { ids["$type:$name"] = id }

    private fun items(type: String, answer: JsonObject): List<CatItem> =
        answer.list("metas").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.mapNotNull { item(type, it) }.onEach { know(type, it.name, it.id) }

    /** One page of a catalogue, starting after the first [skip]; null when it didn't answer. Call off the main thread. */
    fun page(c: Catalogue, skip: Int): List<CatItem>? {
        val id = java.net.URLEncoder.encode(c.id, "UTF-8").replace("+", "%20")
        val answer = fetch(c.base + "/catalog/" + c.type + "/" + id + (if (skip > 0) "/skip=$skip" else "") + ".json") ?: return null
        return items(c.type, answer)
    }

    /** Films ("movie") or series the film index finds for what was typed. Call off the main thread. */
    fun search(type: String, typed: String): List<CatItem> {
        val asked = java.net.URLEncoder.encode(typed.trim(), "UTF-8").replace("+", "%20")
        return fetch("$index/catalog/$type/top/search=$asked.json")?.let { items(type, it) }.orEmpty()
    }

    /** A series' episodes, from the film index, in order; null when it didn't answer. Call off the main thread. */
    fun episodes(id: String): List<Episode>? {
        val meta = fetch("$index/meta/series/$id.json")?.inner("meta") ?: return null
        return meta.list("videos").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.mapNotNull { v ->
            val season = v.text("season").toIntOrNull() ?: return@mapNotNull null
            val number = (v.text("episode").ifEmpty { v.text("number") }).toIntOrNull() ?: return@mapNotNull null
            // Season 0 is extras and specials, which stream add-ons rarely have.
            if (season < 1) null else Episode(v.text("id").ifBlank { "$id:$season:$number" }, season, number, v.text("name").ifEmpty { v.text("title") }, "")
        }.sortedWith(compareBy({ it.season }, { it.number })).distinctBy { it.season to it.number }
    }

    /** A film's name as plain words, for telling whether the provider lists the same film. */
    fun titleKey(name: String): String = plain(title(name).first)

    /** The year in a provider's name for a film, when it gives one. */
    fun titleYear(name: String): Int? = title(name).second

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
    fun streamsFor(
        addOns: List<AddOn>, type: String, name: String, season: Int? = null, episode: Int? = null,
        /** The language subtitles are wanted in. Streams that come with them are tried first. */
        lang: Lang = Langs.all[0],
        /** The number add-ons know it by, when that is already known; otherwise it is found from [name]. */
        knownId: String? = null,
    ): Search {
        val givers = addOns.filter { it.streams }
        if (givers.isEmpty()) return Search(emptyList())
        val id = knownId ?: idFor(type, name) ?: return Search(emptyList())
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
                .mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.firstOrNull { Langs.matches(lang, it.text("lang")) }?.text("url")?.takeIf { it.startsWith("http") }
            val ownSub: (JsonObject) -> String? = { row ->
                row.list("subtitles").mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.firstOrNull { Langs.matches(lang, it.text("lang")) }?.text("url")?.takeIf { it.startsWith("http") }
            }
            // Subtitles first. With a subtitle add-on's file to hand, every stream has them. Without one,
            // a stream that brings its own is tried before one that only says it has some, and that before the rest.
            val subScore: (JsonObject) -> Int = { row ->
                when {
                    spare != null -> 0
                    ownSub(row) != null -> 150
                    has(words(row), "SUBS?", "ESUBS?", "MSUBS?", "SUBTITLES?", "SUBBED") -> 40
                    else -> 0
                }
            }
            // A stream is either a web address to play from, or a torrent, known by its fingerprint.
            val web: (JsonObject) -> Boolean = { it.text("url").startsWith("http://") || it.text("url").startsWith("https://") }
            val address: (JsonObject) -> String = { row ->
                if (web(row)) row.text("url")
                else magnet(row.text("infoHash"), row.list("sources").map { runCatching { it.asString }.getOrDefault("") }, row.text("fileIdx").toIntOrNull(), season, episode)
            }
            val playable = rows.filter { web(it) || it.text("infoHash").isNotBlank() }
            val ranked = playable.withIndex()
                .sortedWith(compareByDescending<IndexedValue<JsonObject>> { score(words(it.value)) + subScore(it.value) + if (web(it.value)) 0 else torrentScore(words(it.value)) }.thenBy { it.index })
                .distinctBy { address(it.value) }.take(MOST).map { (_, row) ->
                    val own = ownSub(row)
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
