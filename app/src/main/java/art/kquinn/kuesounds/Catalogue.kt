package art.kquinn.kuesounds

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Looking songs and albums up in MusicBrainz, the free, open catalogue of the world's music, to put
 * the right album name, artist and year on them, and fetching each album's cover from its picture
 * library (the Cover Art Archive). No account or key is needed. MusicBrainz asks for no more than one
 * question a second, so the lookups are spaced out.
 */
object Catalogue {
    private const val SEARCH = "https://musicbrainz.org/ws/2/"
    private const val COVERS = "https://coverartarchive.org/"
    /** MusicBrainz asks every app to say who it is. */
    const val WHO = "KueSounds/1.0 ( https://github.com/kevinquinnart-bit/OwnTV )"

    /** One release (an edition of an album) found in the catalogue. */
    data class Release(
        val id: String,
        val groupId: String,
        val score: Int,
        val title: String,
        val artist: String,
        val date: String,
        val trackCount: Int,
        val type: String,
        val otherTypes: List<String>,
        val status: String,
    ) {
        val year: Int get() = Library.year(date)
    }

    /** What a lookup decided: the album's proper name, artist and year, and where its cover is. */
    data class Match(val title: String, val artist: String, val year: Int, val releaseId: String, val groupId: String)

    private var lastAsked = 0L

    /** Writes a value into a search so its punctuation is read as words, not as search commands. */
    fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun ask(path: String, query: String, limit: Int = 10): String {
        // One question a second at most, as MusicBrainz asks.
        val wait = lastAsked + 1100 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastAsked = System.currentTimeMillis()
        val url = (SEARCH + path).toHttpUrl().newBuilder().addQueryParameter("query", query).addQueryParameter("fmt", "json")
            .addQueryParameter("limit", limit.toString()).build()
        val request = Request.Builder().url(url).header("User-Agent", WHO).header("Accept", "application/json").build()
        repeat(5) { attempt ->
            Drive.http.newCall(request).execute().use { r ->
                if (r.isSuccessful) return r.body.string()
                // Busy: wait and ask again.
                if (r.code != 503 && r.code != 429) throw IOException("MusicBrainz answered " + r.code)
            }
            Thread.sleep(2000L * (attempt + 1))
            lastAsked = System.currentTimeMillis()
        }
        throw IOException("MusicBrainz stayed busy")
    }

    private fun creditOf(o: JsonObject): String =
        o.getAsJsonArray("artist-credit")?.joinToString("") { el ->
            val c = el.asJsonObject
            (c.get("name")?.asString ?: c.getAsJsonObject("artist")?.get("name")?.asString.orEmpty()) + (c.get("joinphrase")?.asString.orEmpty())
        }.orEmpty()

    private fun releaseOf(o: JsonObject, score: Int): Release? {
        val id = o.get("id")?.asString ?: return null
        val group = o.getAsJsonObject("release-group")
        return Release(
            id = id,
            groupId = group?.get("id")?.asString.orEmpty(),
            score = o.get("score")?.asInt ?: score,
            title = o.get("title")?.asString.orEmpty().replace('’', '\''),
            artist = creditOf(o).replace('’', '\''),
            date = o.get("date")?.asString.orEmpty(),
            trackCount = o.get("track-count")?.asInt ?: 0,
            type = group?.get("primary-type")?.asString.orEmpty(),
            otherTypes = group?.getAsJsonArray("secondary-types")?.map { it.asString }.orEmpty(),
            status = o.get("status")?.asString.orEmpty(),
        )
    }

    /** Reads MusicBrainz's answer to an album search. */
    fun parseReleases(json: String): List<Release> {
        val root = JsonParser.parseString(json).asJsonObject
        return root.getAsJsonArray("releases")?.mapNotNull { releaseOf(it.asJsonObject, 0) }.orEmpty()
    }

    /** Reads MusicBrainz's answer to a song search: the albums each found song is on. */
    fun parseSongReleases(json: String): List<Pair<Int, Release>> {
        val root = JsonParser.parseString(json).asJsonObject
        return root.getAsJsonArray("recordings")?.flatMap { el ->
            val rec = el.asJsonObject
            val score = rec.get("score")?.asInt ?: 0
            rec.getAsJsonArray("releases")?.mapNotNull { r -> releaseOf(r.asJsonObject, score)?.let { score to it } }.orEmpty()
        }.orEmpty()
    }

    private fun sameArtist(a: String, b: String): Boolean {
        val x = Library.sortName(a)
        val y = Library.sortName(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.startsWith(y) || y.startsWith(x)
    }

    fun isVarious(name: String) = Library.sortName(name) in setOf("various artists", "various", "various artist", "va")

    /**
     * Picks the right release for one of the person's albums, or none. It must be a close match on
     * name and artist; then a proper album over a single, an official release over a bootleg, the
     * number of songs nearest to the person's, and the earliest date.
     */
    fun choose(found: List<Release>, artist: String, songs: Int): Release? {
        val known = artist.isNotBlank() && artist != Library.UNKNOWN_ARTIST
        val fits = found.filter { r ->
            r.score >= 90 && when {
                !known -> r.trackCount == songs
                isVarious(artist) -> isVarious(r.artist)
                else -> sameArtist(r.artist, artist)
            }
        }
        return fits.minWithOrNull(
            compareBy<Release>(
                { if (it.type.equals("Album", true)) 0 else if (it.type.isEmpty()) 1 else 2 },
                { if (it.status.isEmpty() || it.status.equals("Official", true)) 0 else 1 },
                { kotlin.math.abs(it.trackCount - songs) },
                { if (it.year > 0) it.year else 9999 },
            ),
        )
    }

    /**
     * Picks the album a loose song comes from: the artist's own official album, not a live or
     * compilation record, earliest first.
     */
    fun chooseForSong(found: List<Pair<Int, Release>>, artist: String): Release? =
        found.filter { (score, r) ->
            score >= 95 && sameArtist(r.artist, artist) && r.type.equals("Album", true) && r.otherTypes.isEmpty() &&
                (r.status.isEmpty() || r.status.equals("Official", true))
        }.map { it.second }.minByOrNull { if (it.year > 0) it.year else 9999 }

    /** Looks an album up. Call off the main thread. */
    fun findAlbum(title: String, artist: String, songs: Int): Match? {
        val known = artist.isNotBlank() && artist != Library.UNKNOWN_ARTIST
        val query = "release:" + quote(title) + if (known) " AND artist:" + quote(if (isVarious(artist)) "Various Artists" else artist) else ""
        val r = choose(parseReleases(ask("release/", query)), artist, songs) ?: return null
        return Match(r.title, r.artist, r.year, r.id, r.groupId)
    }

    /** Looks a loose song up, to find the album it comes from. Call off the main thread. */
    fun findSong(title: String, artist: String): Match? {
        if (artist.isBlank() || artist == Library.UNKNOWN_ARTIST) return null
        val r = chooseForSong(parseSongReleases(ask("recording/", "recording:" + quote(title) + " AND artist:" + quote(artist))), artist) ?: return null
        return Match(r.title, r.artist, r.year, r.id, r.groupId)
    }

    /**
     * Where a song properly belongs: the artist's own studio album it first came out on, and the single
     * it came out as (if it did). Either can be missing.
     */
    data class SongHome(val album: Match?, val single: Match?)

    /** Reads a song search: each found release with the found song's own title and its score. */
    fun parseRecordings(json: String): List<Triple<Int, String, Release>> {
        val root = JsonParser.parseString(json).asJsonObject
        return root.getAsJsonArray("recordings")?.flatMap { el ->
            val rec = el.asJsonObject
            val score = rec.get("score")?.asInt ?: 0
            val title = rec.get("title")?.asString.orEmpty().replace('’', '\'')
            rec.getAsJsonArray("releases")?.mapNotNull { r -> releaseOf(r.asJsonObject, score)?.let { Triple(score, title, it) } }.orEmpty()
        }.orEmpty()
    }

    /** A song's name without anything in brackets, for comparing ("Wonderwall (remastered)" is "wonderwall"). */
    private fun bare(title: String): String = Library.sortName(title.replace(Regex("""\s*[\[(][^\])]*[\])]"""), ""))

    /**
     * Picks a song's home from a song search. Only the artist's own official releases count, and only
     * plain ones: no live, compilation, remix or soundtrack records. The album is the earliest such album
     * with the song on it; the single is the earliest single named after the song.
     */
    fun chooseSongHome(found: List<Triple<Int, String, Release>>, title: String, artist: String): SongHome {
        val name = bare(title)
        val plain = found.filter { (score, song, r) ->
            score >= 85 && bare(song) == name && sameArtist(r.artist, artist) && r.otherTypes.isEmpty() &&
                (r.status.isEmpty() || r.status.equals("Official", true))
        }.map { it.third }
        val byYear = compareBy<Release> { if (it.year > 0) it.year else 9999 }
        val album = plain.filter { it.type.equals("Album", true) }.minWithOrNull(byYear)
        val single = plain.filter { it.type.equals("Single", true) && bare(it.title).startsWith(name) }.minWithOrNull(byYear)
        return SongHome(album?.let { Match(it.title, it.artist, it.year, it.id, it.groupId) }, single?.let { Match(it.title, it.artist, it.year, it.id, it.groupId) })
    }

    /** Looks a song up, to find the album it first came out on and the single it came out as. Call off the main thread. */
    fun findSongHome(title: String, artist: String): SongHome {
        if (artist.isBlank() || artist == Library.UNKNOWN_ARTIST || isVarious(artist)) return SongHome(null, null)
        val query = "recording:" + quote(title) + " AND artist:" + quote(artist) + " AND status:official AND (primarytype:album OR primarytype:single)"
        return chooseSongHome(parseRecordings(ask("recording/", query, 15)), title, artist)
    }

    /** Fetches the album's front cover (500 dots across). Null when the catalogue has none. */
    fun cover(m: Match): ByteArray? {
        val tries = listOfNotNull(m.groupId.takeIf { it.isNotEmpty() }?.let { COVERS + "release-group/$it/front-500" }, COVERS + "release/${m.releaseId}/front-500")
        for (url in tries) {
            val request = Request.Builder().url(url).header("User-Agent", WHO).build()
            Drive.http.newCall(request).execute().use { r ->
                if (r.isSuccessful) return r.body.bytes()
                if (r.code != 404) throw IOException("the cover library answered " + r.code)
            }
        }
        return null
    }
}
