package art.kquinn.kuesounds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * Finding the music and reading each song's details.
 *
 * Step 1 lists every music file and folder in the Drive (a few seconds even for thousands of songs) and
 * gives each song a first guess from its file name and folders, so the library shows straight away.
 * Step 2 reads each song's own details (title, artist, album, track number, length, cover) from the
 * file itself, a small piece of each file at a time, and keeps them on the phone so it is done once.
 */
object Scanner {
    var data by mutableStateOf(LibraryData())
        private set

    /** What the scan is doing, in words for the screen. Empty when it is idle. */
    var status by mutableStateOf("")
        private set

    /** How many songs have their own details read, out of how many. */
    var read by mutableStateOf(0)
        private set

    var problem by mutableStateOf("")
        private set

    var signedOut by mutableStateOf(false)

    /** A plain record of what the scan did and what failed, newest last, for the report. */
    private val notes = ArrayDeque<String>()

    fun note(text: String) = synchronized(notes) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
        notes.addLast("$time $text")
        while (notes.size > 200) notes.removeFirst()
    }

    /** The report: counts and the record, with no addresses or passwords in it. */
    fun report(): String {
        val d = data
        val albums = Library.albums(d.tracks)
        val lines = mutableListOf(
            "KueSounds " + BuildConfig.VERSION_NAME,
            "Songs: " + d.tracks.size,
            "Song details read: " + d.tracks.count { it.tagged },
            "Albums: " + albums.size,
            "Albums looked up: " + albums.count { al -> al.tracks.all { it.checked } },
            "Albums with a cover: " + albums.count { artFor(it) != null },
            "Folders: " + d.folders.size + ", folder cover pictures: " + d.folderCovers.size,
            "Now: " + status.ifEmpty { "idle" } + (if (problem.isNotEmpty()) " / problem: $problem" else ""),
            "",
        )
        synchronized(notes) { lines += notes }
        return lines.joinToString("\n")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var app: Context

    private fun libraryFile() = File(app.filesDir, "library.json")
    private fun artFolder() = File(app.filesDir, "art").apply { mkdirs() }

    fun init(context: Context) {
        app = context.applicationContext
        data = Library.load(libraryFile())
        // Libraries kept before 0.1.5 had their albums looked up without keeping the catalogue's cover in
        // first place: every album is looked up once more.
        if (data.version < 2) {
            data = data.copy(tracks = data.tracks.map { it.copy(checked = false) }, version = 2)
            save()
        }
        read = data.tracks.count { it.tagged }
    }

    /** Whether a scan is going on. Kept as screen state, so the screens change the moment it starts and ends. */
    var running by mutableStateOf(false)
        private set

    /** Changes some songs and keeps the library at once. Used by the file fixing. */
    fun changeTracks(ids: Set<String>, change: (Track) -> Track) = synchronized(this) {
        data = data.copy(tracks = data.tracks.map { if (it.id in ids) change(it) else it })
        save()
    }

    /** Looks for music in the Drive, then reads the details of any song not yet read. */
    fun scan() {
        // Not while files are being fixed: the two would change the library at once.
        if (job?.isActive == true || Fixer.running) return
        running = true
        job = scope.launch {
            problem = ""
            try {
                status = "Finding your music"
                val files = Drive.listAll(Drive.MUSIC_QUERY) { n -> status = "Finding your music · " + String.format("%,d", n) + " files" }
                val found = String.format("%,d", files.count { Library.isMusic(it.name, it.mime) }) + " songs found"
                status = "Finding your folders · $found"
                val folderFiles = Drive.listAll(Drive.FOLDER_QUERY)
                val folders = folderFiles.map { Folder(it.id, it.name, it.parent) }
                status = "Finding album covers · $found"
                val covers = runCatching { Drive.listAll(Drive.COVER_QUERY) }.getOrDefault(emptyList())
                    .filter { Drive.isCoverName(it.name) && it.parent != null && it.size in 1..8_000_000 }
                    .groupBy { it.parent!! }
                    .mapValues { (_, list) -> list.sortedBy { coverRank(it.name) }.first().id }
                data = Library.merge(data, files, folders, covers, System.currentTimeMillis())
                save()
                note("found " + data.tracks.size + " songs in " + folders.size + " folders, " + covers.size + " folder cover pictures")
                if (Google.email.isBlank()) runCatching { Google.keepEmail(Drive.whoAmI()) }
                readDetails()
                matchAlbums()
                status = ""
                note("scan finished")
            } catch (e: Drive.SignedOut) {
                note("signed out by Google")
                signedOut = true
                status = ""
            } catch (e: Exception) {
                note("scan stopped: " + e.javaClass.simpleName + " " + e.message)
                problem = "Couldn't finish looking through your music. " + (e.message ?: "")
                status = ""
            } finally {
                running = false
            }
        }
    }

    private fun coverRank(name: String): Int = when (name.substringBeforeLast('.').lowercase()) {
        "cover" -> 0
        "folder" -> 1
        "front" -> 2
        else -> 3
    }

    private fun save() = Library.save(libraryFile(), data)

    @Volatile private var failures = 0

    /** Step 2: each song's own details, four at a time, kept every 25 songs. */
    private suspend fun readDetails() {
        val todo = data.tracks.filter { !it.tagged }
        read = data.tracks.size - todo.size
        if (todo.isEmpty()) {
            fillCovers()
            return
        }
        val gate = Semaphore(4)
        var sinceSave = 0
        todo.chunked(25).forEach { chunk ->
            if (!scope.isActive) return
            status = "Reading song details · " + String.format("%,d", read) + " of " + String.format("%,d", data.tracks.size)
            // A fresh pass for each batch: one that ran out part way was dropped, and this asks for a new one.
            val pass = Google.token() ?: throw Drive.SignedOut()
            val done = coroutineScopeAsync(chunk.map { t -> suspend { gate.withPermit { readOne(t, pass) } } })
            val byId = done.filterNotNull().associateBy { it.first.id }
            data = data.copy(tracks = data.tracks.map { byId[it.id]?.first ?: it }, art = data.art + done.filterNotNull().mapNotNull { it.second })
            read = data.tracks.count { it.tagged }
            sinceSave += chunk.size
            if (sinceSave >= 25) {
                save()
                sinceSave = 0
            }
        }
        save()
        fillCovers()
        save()
    }

    private suspend fun <T> coroutineScopeAsync(work: List<suspend () -> T>): List<T> =
        kotlinx.coroutines.coroutineScope { work.map { w -> async { w() } }.awaitAll() }

    /**
     * Reads one song's details from the file. Gives back the song with its details, and its album's
     * cover picture when the file holds one and the album has none yet.
     */
    private fun readOne(t: Track, token: String): Pair<Track, Pair<String, String>?>? {
        val retriever = MediaMetadataRetriever()
        val source = DriveSource(t.id, t.size, token)
        return try {
            retriever.setDataSource(source)
            fun key(k: Int) = retriever.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            val title = key(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val tidy = album?.let { Library.cleanAlbum(it, albumArtist ?: artist.orEmpty()) }
            val updated = t.copy(
                title = title ?: t.title,
                artist = artist ?: albumArtist ?: t.artist,
                album = tidy?.first ?: t.album,
                albumArtist = albumArtist.orEmpty(),
                trackNo = Library.number(key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)).takeIf { it > 0 } ?: t.trackNo,
                discNo = Library.number(key(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)).takeIf { it > 0 }
                    ?: tidy?.second?.takeIf { it > 0 } ?: t.discNo,
                year = Library.year(key(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: key(MediaMetadataRetriever.METADATA_KEY_DATE)),
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty(),
                tagged = true,
                // Fresh details mean the album is looked up again with them.
                checked = false,
            )
            val albumKey = Library.albumKey(Library.albumArtistOf(updated), updated.album)
            var art: Pair<String, String>? = null
            if (albumKey !in data.art && (updated.folderId == null || updated.folderId !in data.folderCovers)) {
                retriever.embeddedPicture?.let { bytes ->
                    keepPicture(bytes, albumKey)?.let { art = albumKey to it }
                }
            }
            updated to art
        } catch (e: Exception) {
            if (failures++ < 15) {
                note("details not read for " + t.fileName + ": " + (source.failure ?: e).let { it.javaClass.simpleName + " " + it.message })
            }
            // The Drive couldn't be reached, or the pass ran out: left as it is, to be read next time.
            if (source.failure != null) return null
            // A file whose details can't be read keeps its first guess, and isn't tried again.
            t.copy(tagged = true) to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Albums whose folder holds a cover picture get it, downloaded once and kept small. */
    private suspend fun fillCovers() {
        val albums = Library.albums(data.tracks)
        val wanted = albums.filter { it.key !in data.art }.mapNotNull { al ->
            val folder = al.tracks.firstNotNullOfOrNull { it.folderId?.takeIf { f -> f in data.folderCovers } } ?: return@mapNotNull null
            al.key to data.folderCovers.getValue(folder)
        }
        if (wanted.isEmpty()) return
        val added = mutableMapOf<String, String>()
        wanted.forEachIndexed { i, (key, fileId) ->
            status = "Fetching album covers · " + (i + 1) + " of " + wanted.size
            runCatching {
                val raw = File(app.cacheDir, "cover.part")
                Drive.download(fileId, raw)
                keepPicture(raw.readBytes(), key)?.let { added[key] = it }
                raw.delete()
            }
            if (added.size % 20 == 0 && added.isNotEmpty()) {
                data = data.copy(art = data.art + added)
            }
        }
        data = data.copy(art = data.art + added)
    }

    /**
     * Step 3: each album not yet looked up is found in MusicBrainz, which gives it its proper name,
     * artist and year, and its cover. Songs with no album are looked up one by one. An album that
     * isn't found keeps its tidied name. Each is looked up once.
     */
    private fun matchAlbums() {
        val todo = Library.albums(data.tracks).filter { al -> al.tracks.any { !it.checked } }
        if (todo.isEmpty()) return
        var found = 0
        var covers = 0
        var notFound = 0
        todo.forEachIndexed { i, al ->
            status = "Matching albums · " + (i + 1) + " of " + todo.size
            try {
                if (Library.sortName(al.title) == Library.sortName(Library.UNKNOWN_ALBUM)) {
                    for (t in al.tracks.filter { !it.checked }) {
                        val m = Catalogue.findSong(t.title, t.artist)
                        if (m != null) found++ else notFound++
                        if (apply(setOf(t.id), al, m, t.album)) covers++
                    }
                } else {
                    val (clean, _) = Library.cleanAlbum(al.title, al.artist)
                    val m = Catalogue.findAlbum(clean, al.artist, al.tracks.size)
                    if (m != null) found++ else {
                        notFound++
                        if (notFound <= 30) note("not in the catalogue: " + al.artist + " / " + clean)
                    }
                    if (apply(al.tracks.map { it.id }.toSet(), al, m, clean)) covers++
                }
            } catch (e: java.io.IOException) {
                note("album lookups stopped: " + e.message)
                save()
                return@matchAlbums
            }
            if (i % 10 == 9) save()
        }
        save()
        note("looked up " + todo.size + " albums: " + found + " found, " + notFound + " not found, " + covers + " covers fetched")
    }

    /**
     * Puts what the catalogue found on these songs (or, when nothing was found, the tidied album name),
     * and gives the album the catalogue's cover in place of any picture inside the files. True when a
     * catalogue cover was fetched.
     */
    private fun apply(ids: Set<String>, al: Album, m: Catalogue.Match?, cleanTitle: String): Boolean {
        val oldKey = al.key
        data = data.copy(
            tracks = data.tracks.map { t ->
                if (t.id !in ids) t else {
                    val disc = if (t.discNo > 0) t.discNo else Library.cleanAlbum(t.album, al.artist).second
                    t.copy(
                        album = m?.title ?: cleanTitle,
                        albumArtist = m?.artist ?: t.albumArtist,
                        year = m?.year?.takeIf { it > 0 } ?: t.year,
                        discNo = disc,
                        checked = true,
                    )
                }
            },
        )
        val sample = data.tracks.first { it.id in ids }
        val newKey = Library.albumKey(Library.albumArtistOf(sample), sample.album)
        if (m != null) data = data.copy(releases = data.releases + (newKey to m.releaseId + "|" + m.groupId))
        if (newKey in data.catalogueCovers) return false
        // The catalogue's cover comes first: a picture inside the files can be the wrong one.
        if (m != null) {
            val bytes = runCatching { Catalogue.cover(m) }.getOrNull()
            val path = bytes?.let { keepPicture(it, newKey + "\u0002catalogue") }
            if (path != null) {
                data = data.copy(art = data.art + (newKey to path), catalogueCovers = data.catalogueCovers + newKey)
                return true
            }
        }
        // No catalogue cover: one already found for the album (in its folder or its files) moves with it.
        if (newKey !in data.art) data.art[oldKey]?.let { data = data.copy(art = data.art + (newKey to it)) }
        return false
    }

    /** Shrinks a cover picture to at most 600 dots across and keeps it. Gives back the kept file's path. */
    private fun keepPicture(bytes: ByteArray, albumKey: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val name = MessageDigest.getInstance("SHA-1").digest(albumKey.toByteArray()).joinToString("") { "%02x".format(it) } + ".jpg"
        val file = File(artFolder(), name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return file.path
    }

    /** The cover picture kept for an album, if there is one. */
    fun artFor(album: Album): File? = data.art[album.key]?.let { File(it) }?.takeIf { it.exists() }

    fun artForTrack(t: Track): File? = data.art[Library.albumKey(Library.albumArtistOf(t), t.album)]?.let { File(it) }?.takeIf { it.exists() }

    /**
     * A song file read in pieces straight from the Drive, for the phone's own details reader. Pieces of
     * 64 KB are fetched as asked for and the last few kept, so the start and end of a file are read once.
     */
    private class DriveSource(private val id: String, private val length: Long, private val token: String) : MediaDataSource() {
        private val blockSize = 64 * 1024L
        private val blocks = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > 48
        }
        private var fetched = 0L

        /** Set when the Drive couldn't be read (as against a file that holds no details). */
        @Volatile var failure: Exception? = null

        override fun getSize(): Long = length

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= length) return -1
            // A file that needs much more than its details read (8 MB) is given up on.
            if (fetched > 8L * 1024 * 1024) throw java.io.IOException("too much reading")
            var done = 0
            var pos = position
            while (done < size && pos < length) {
                val index = pos / blockSize
                val block = blocks[index] ?: fetch(index)
                val inBlock = (pos - index * blockSize).toInt()
                val n = minOf(size - done, block.size - inBlock)
                if (n <= 0) break
                System.arraycopy(block, inBlock, buffer, offset + done, n)
                done += n
                pos += n
            }
            return if (done == 0) -1 else done
        }

        private fun fetch(index: Long): ByteArray {
            val from = index * blockSize
            val to = minOf(length - 1, from + blockSize - 1)
            val bytes = try {
                Drive.readRange(id, from, to, token)
            } catch (e: Exception) {
                failure = e
                throw e
            }
            fetched += bytes.size
            blocks[index] = bytes
            return bytes
        }

        override fun close() {
            blocks.clear()
        }
    }
}
