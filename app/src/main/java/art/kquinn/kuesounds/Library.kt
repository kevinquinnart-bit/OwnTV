package art.kquinn.kuesounds

import com.google.gson.Gson
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** One music file in the Drive, with what is known about it. */
data class Track(
    val id: String,
    val fileName: String,
    val mime: String,
    val size: Long,
    val folderId: String?,
    val modified: String,
    var title: String,
    var artist: String,
    var album: String,
    var albumArtist: String,
    var trackNo: Int,
    var discNo: Int,
    var year: Int,
    var durationMs: Long,
    var genre: String,
    /** True once the song's own details (its tags) have been read from the file. */
    var tagged: Boolean,
    /** True once its album has been looked up in the music catalogue (MusicBrainz), found or not. */
    var checked: Boolean = false,
    /** True once the right cover and album details have been written into the file in the Drive. */
    var fixed: Boolean = false,
)

/** A folder in the Drive: its name and the folder it sits in. */
data class Folder(val id: String, val name: String, val parent: String?)

/** One album: the songs that share an album name and an artist. */
data class Album(val key: String, val title: String, val artist: String, val year: Int, val tracks: List<Track>)

/** One artist and their albums. */
data class Artist(val name: String, val albums: List<Album>, val songCount: Int)

/** Everything KueSounds knows about the music, kept on the phone between visits. */
data class LibraryData(
    val tracks: List<Track> = emptyList(),
    val folders: List<Folder> = emptyList(),
    /** Album key to the file holding its cover picture on this phone. */
    val art: Map<String, String> = emptyMap(),
    /** Folder id to the Drive file of a cover picture found in it (cover.jpg, folder.jpg, front.jpg). */
    val folderCovers: Map<String, String> = emptyMap(),
    val scannedAt: Long = 0,
    /** Album key to the catalogue's release it was matched to ("release id|release group id"). */
    val releases: Map<String, String> = emptyMap(),
    /** Albums whose cover came from the catalogue. These win over a picture inside the files. */
    val catalogueCovers: Set<String> = emptySet(),
    /** Which way the kept library is laid out, so an older one can be brought up to date. */
    val version: Int = 0,
)

object Library {
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"

    /** The kinds of music file the player can play. */
    val musicEndings = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "aif", "aiff", "mka", "3ga", "amr", "m4b", "mp4a", "alac", "wma")

    fun ending(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** Whether a Drive file is music, from its kind and its name. */
    fun isMusic(name: String, mime: String): Boolean {
        val end = ending(name)
        if (mime.startsWith("audio/")) return true
        if (mime == "application/ogg" || mime == "application/x-flac") return true
        return end in musicEndings && end != ""
    }

    private val leadingNumber = Regex("""^\s*(?:(\d{1,2})[-.](\d{1,3})|(\d{1,3}))\s*(?:[-._)]\s*|\s+)(.+)$""")

    /**
     * A first guess at a song's details from its file name and folders, used until its own details are
     * read. "03 - Song.mp3" in "Artist/Album" gives track 3, "Song", "Album", "Artist".
     */
    fun guess(fileName: String, folder: Folder?, parentOfFolder: Folder?): Guess {
        var stem = fileName.substringBeforeLast('.', fileName).trim().replace('_', ' ')
        var trackNo = 0
        var discNo = 0
        leadingNumber.find(stem)?.let { m ->
            val rest = m.groupValues[4].trim()
            // A name that is only a number ("1999.mp3") keeps the number as its title.
            if (rest.isNotEmpty() && rest.any { it.isLetter() }) {
                if (m.groupValues[1].isNotEmpty()) {
                    discNo = m.groupValues[1].toInt()
                    trackNo = m.groupValues[2].toInt()
                } else {
                    trackNo = m.groupValues[3].toInt()
                }
                stem = rest
            }
        }
        var artist = parentOfFolder?.name?.trim().orEmpty()
        val album = folder?.name?.let { cleanAlbum(it, artist).first }.orEmpty()
        if (folder != null) discNo = discNo.takeIf { it > 0 } ?: cleanAlbum(folder.name, artist).second
        var title = stem
        // Loose files named "Artist - Song" give their artist when the folders don't.
        if (artist.isEmpty() && " - " in stem) {
            val bits = stem.split(" - ", limit = 2)
            if (bits[0].isNotBlank() && bits[1].isNotBlank() && !bits[0].trim().equals(album, ignoreCase = true)) {
                artist = bits[0].trim()
                title = bits[1].trim()
            }
        }
        return Guess(
            title = title.ifBlank { fileName },
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            album = album.ifBlank { UNKNOWN_ALBUM },
            trackNo = trackNo,
            discNo = discNo,
        )
    }

    data class Guess(val title: String, val artist: String, val album: String, val trackNo: Int, val discNo: Int)

    /** Reads "3", "3/12" or "03 of 12" as 3. */
    fun number(text: String?): Int = text?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    /** Reads "2004", "2004-05-01" or "2004/05" as 2004. Zero when it isn't a plausible year. */
    fun year(text: String?): Int {
        val y = Regex("""(1[89]\d\d|20\d\d)""").find(text.orEmpty())?.value?.toIntOrNull() ?: 0
        return y
    }

    /**
     * The name an artist or album is grouped and sorted by: case, accents, curly quotes, leading
     * punctuation and a leading "The" set aside.
     */
    fun sortName(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
            .replace('\u2019', '\'').replace('\u2018', '\'').lowercase(Locale.ROOT).trim()
        return plain.trimStart { !it.isLetterOrDigit() }.removePrefix("the ").trim()
    }

    private val discMark = Regex("""[\[(\-\s]*\b(?:disc|disk|cd)\s*(\d{1,2})\b\s*[\])]?""", RegexOption.IGNORE_CASE)

    /**
     * Tidies an album name taken from a folder or a tag: "1 - Garth Brooks - Ropin' the Wind [Disc 2] (1991)"
     * becomes "Ropin' the Wind", disc 2. Gives back the tidy name and the disc number (0 when none).
     */
    fun cleanAlbum(raw: String, artist: String): Pair<String, Int> {
        var t = raw.replace('_', ' ').trim()
        var disc = 0
        discMark.find(t)?.let {
            disc = it.groupValues[1].toInt()
            t = t.removeRange(it.range)
        }
        t = t.replace(Regex("""^\s*\d{1,3}\s*[-.)]\s+"""), "")
        if (artist.isNotBlank() && artist != UNKNOWN_ARTIST) {
            t = t.replace(Regex("^\\s*" + Regex.escape(artist) + "\\s*[-\u2013:]\\s*", RegexOption.IGNORE_CASE), "")
        }
        t = t.replace(Regex("""\s*[\[(](?:19|20)\d\d[\])]\s*$"""), "")
        t = t.replace(Regex("""^(?:19|20)\d\d\s*-\s+"""), "")
        t = t.trim().trimStart('-', '+', '_', '.', '~', '*', ' ').trimEnd('-', '+', '_', '~', '*', ' ')
        t = t.replace(Regex("""\s{2,}"""), " ").trim()
        return t.ifBlank { raw.trim() } to disc
    }

    fun albumKey(artist: String, album: String): String = sortName(artist) + "\u0001" + sortName(album)

    /** The artist an album belongs to: the album artist when the songs name one. */
    fun albumArtistOf(t: Track): String = t.albumArtist.ifBlank { t.artist }.ifBlank { UNKNOWN_ARTIST }

    /** The songs grouped into albums, each album's songs in disc and track order. Albums in name order. */
    fun albums(tracks: List<Track>): List<Album> {
        // Songs in one folder with one album name are one album, even if their artists differ
        // (a compilation without an album artist).
        val byFolderAlbum = tracks.groupBy { (it.folderId ?: "") + "\u0001" + sortName(it.album) }
        val groups = mutableMapOf<String, MutableList<Track>>()
        for ((_, list) in byFolderAlbum) {
            val artists = list.map { sortName(albumArtistOf(it)) }.distinct()
            val artist = if (artists.size == 1) albumArtistOf(list.first()) else "Various artists"
            val key = albumKey(artist, list.first().album)
            groups.getOrPut(key) { mutableListOf() }.addAll(list)
        }
        return groups.map { (key, list) ->
            val first = list.first()
            val artists = list.map { sortName(albumArtistOf(it)) }.distinct()
            val artist = if (artists.size == 1) albumArtistOf(first) else "Various artists"
            Album(
                key = key,
                title = first.album,
                artist = artist,
                year = list.map { it.year }.filter { it > 0 }.minOrNull() ?: 0,
                tracks = list.sortedWith(compareBy<Track>({ it.discNo }, { if (it.trackNo == 0) Int.MAX_VALUE else it.trackNo }, { sortName(it.title) })),
            )
        }.sortedWith(compareBy({ it.title == UNKNOWN_ALBUM }, { sortName(it.title) }, { sortName(it.artist) }))
    }

    /** Artists with their albums, in name order. */
    fun artists(albums: List<Album>): List<Artist> =
        albums.groupBy { sortName(it.artist) }.map { (_, list) ->
            Artist(list.first().artist, list.sortedWith(compareBy({ it.year == 0 }, { it.year }, { sortName(it.title) })), list.sumOf { it.tracks.size })
        }.sortedWith(compareBy({ it.name == UNKNOWN_ARTIST }, { sortName(it.name) }))

    /** Every song in title order. */
    fun songs(tracks: List<Track>): List<Track> = tracks.sortedWith(compareBy({ sortName(it.title) }, { sortName(it.artist) }))

    /** Songs, albums and artists whose names hold every word typed. */
    fun search(query: String, tracks: List<Track>, albums: List<Album>, artists: List<Artist>): Results {
        val words = sortName(query).split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return Results(emptyList(), emptyList(), emptyList())
        fun hit(vararg text: String): Boolean {
            val all = text.joinToString(" ") { sortName(it) }
            return words.all { it in all }
        }
        return Results(
            artists = artists.filter { hit(it.name) }.take(20),
            albums = albums.filter { hit(it.title, it.artist) }.take(40),
            songs = songs(tracks.filter { hit(it.title, it.artist, it.album) }).take(200),
        )
    }

    data class Results(val artists: List<Artist>, val albums: List<Album>, val songs: List<Track>)

    /** "3:42" or "1:02:05". */
    fun clock(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    }

    /**
     * Brings a fresh listing from the Drive together with what was known before: songs already read keep
     * their details unless the file has changed, new songs get a first guess, and songs gone from the
     * Drive are dropped.
     */
    fun merge(old: LibraryData, files: List<Drive.FileInfo>, folders: List<Folder>, folderCovers: Map<String, String>, now: Long): LibraryData {
        val known = old.tracks.associateBy { it.id }
        val folderById = folders.associateBy { it.id }
        val tracks = files.filter { isMusic(it.name, it.mime) }.map { f ->
            val before = known[f.id]
            if (before != null && before.tagged && before.modified == f.modified && before.size == f.size) {
                before
            } else {
                val folder = f.parent?.let { folderById[it] }
                val parent = folder?.parent?.let { folderById[it] }
                val g = guess(f.name, folder, parent)
                Track(
                    id = f.id, fileName = f.name, mime = f.mime, size = f.size, folderId = f.parent, modified = f.modified,
                    title = g.title, artist = g.artist, album = g.album, albumArtist = "", trackNo = g.trackNo, discNo = g.discNo,
                    year = 0, durationMs = 0, genre = "", tagged = false,
                )
            }
        }
        return old.copy(tracks = tracks, folders = folders, folderCovers = folderCovers, scannedAt = now)
    }

    private val gson = Gson()

    fun save(file: File, data: LibraryData) {
        val part = File(file.path + ".part")
        part.writeText(gson.toJson(data))
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    fun load(file: File): LibraryData =
        runCatching { gson.fromJson(file.readText(), LibraryData::class.java) }.getOrNull()?.let { fixNulls(it) } ?: LibraryData()

    /** Gson can leave a missing list or text empty as null; put them back as empty. */
    private fun fixNulls(d: LibraryData): LibraryData {
        @Suppress("SENSELESS_COMPARISON")
        val tracks = (d.tracks ?: emptyList()).filter { it != null && it.id != null }.map { t ->
            @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
            t.copy(
                fileName = t.fileName ?: "", mime = t.mime ?: "", modified = t.modified ?: "",
                title = t.title ?: (t.fileName ?: ""), artist = t.artist ?: UNKNOWN_ARTIST, album = t.album ?: UNKNOWN_ALBUM,
                albumArtist = t.albumArtist ?: "", genre = t.genre ?: "",
            )
        }
        @Suppress("USELESS_ELVIS")
        return LibraryData(
            tracks, d.folders ?: emptyList(), d.art ?: emptyMap(), d.folderCovers ?: emptyMap(), d.scannedAt,
            d.releases ?: emptyMap(), d.catalogueCovers ?: emptySet(), d.version,
        )
    }

    /** The folders inside [parent] (null: the top of the Drive), in name order. */
    fun foldersIn(parent: String?, folders: List<Folder>, tracks: List<Track>): List<Folder> {
        val ids = folders.map { it.id }.toSet()
        // Only folders with music somewhere below them are shown.
        val withMusic = mutableSetOf<String>()
        val byId = folders.associateBy { it.id }
        for (t in tracks) {
            var at = t.folderId
            var guard = 0
            while (at != null && at in byId && guard++ < 64) {
                if (!withMusic.add(at)) break
                at = byId[at]?.parent
            }
        }
        return folders.filter { f ->
            f.id in withMusic && (if (parent == null) f.parent == null || f.parent !in ids else f.parent == parent)
        }.sortedBy { sortName(it.name) }
    }

    /** The songs directly in [folder] (null: loose at the top of the Drive), in track then name order. */
    fun songsIn(folder: String?, folders: List<Folder>, tracks: List<Track>): List<Track> {
        val ids = folders.map { it.id }.toSet()
        return tracks.filter { if (folder == null) it.folderId == null || it.folderId !in ids else it.folderId == folder }
            .sortedWith(compareBy({ it.discNo }, { if (it.trackNo == 0) Int.MAX_VALUE else it.trackNo }, { sortName(it.fileName) }))
    }
}
