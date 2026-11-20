package art.kquinn.kuesounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks on pretend Drive listings: no real Drive or real music is used here. */
class LibraryTest {
    private val artistFolder = Folder("fa", "Pretend Artist", null)
    private val albumFolder = Folder("fb", "Pretend Album", "fa")

    @Test
    fun firstGuessComesFromFileNameAndFolders() {
        val g = Library.guess("03 - Third Song.mp3", albumFolder, artistFolder)
        assertEquals("Third Song", g.title)
        assertEquals("Pretend Album", g.album)
        assertEquals("Pretend Artist", g.artist)
        assertEquals(3, g.trackNo)
    }

    @Test
    fun discAndTrackNumbersAreRead() {
        val g = Library.guess("2-07 Seventh.flac", albumFolder, artistFolder)
        assertEquals(2, g.discNo)
        assertEquals(7, g.trackNo)
        assertEquals("Seventh", g.title)
    }

    @Test
    fun aNameThatIsOnlyANumberKeepsIt() {
        assertEquals("1999", Library.guess("1999.mp3", albumFolder, artistFolder).title)
    }

    @Test
    fun looseArtistDashSongGivesTheArtist() {
        val g = Library.guess("Someone - A Song.mp3", null, null)
        assertEquals("Someone", g.artist)
        assertEquals("A Song", g.title)
        assertEquals(Library.UNKNOWN_ALBUM, g.album)
    }

    @Test
    fun musicIsToldFromOtherFiles() {
        assertTrue(Library.isMusic("a.mp3", "audio/mpeg"))
        assertTrue(Library.isMusic("a.flac", "application/octet-stream"))
        assertTrue(Library.isMusic("a.m4a", "video/mp4"))
        assertFalse(Library.isMusic("a.mp4", "video/mp4"))
        assertFalse(Library.isMusic("notes.txt", "application/octet-stream"))
    }

    @Test
    fun numbersAndYearsAreRead() {
        assertEquals(3, Library.number("3/12"))
        assertEquals(0, Library.number(null))
        assertEquals(2004, Library.year("2004-05-01"))
        assertEquals(0, Library.year("unknown"))
    }

    private fun track(id: String, title: String, artist: String, album: String, no: Int, folder: String? = "fb", albumArtist: String = "") = Track(
        id, "$title.mp3", "audio/mpeg", 100, folder, "t", title, artist, album, albumArtist, no, 0, 0, 0, "", true,
    )

    @Test
    fun songsGroupIntoAlbumsInTrackOrder() {
        val tracks = listOf(
            track("2", "Second", "Pretend Artist", "Pretend Album", 2),
            track("1", "First", "Pretend Artist", "Pretend Album", 1),
            track("3", "Other", "Another Artist", "Another Album", 1, folder = "fc"),
        )
        val albums = Library.albums(tracks)
        assertEquals(2, albums.size)
        val al = albums.first { it.title == "Pretend Album" }
        assertEquals(listOf("First", "Second"), al.tracks.map { it.title })
        assertEquals("Pretend Artist", al.artist)
    }

    @Test
    fun aCompilationInOneFolderStaysOneAlbum() {
        val tracks = listOf(
            track("1", "One", "Singer A", "Hits", 1, folder = "fx"),
            track("2", "Two", "Singer B", "Hits", 2, folder = "fx"),
        )
        val albums = Library.albums(tracks)
        assertEquals(1, albums.size)
        assertEquals("Various artists", albums.single().artist)
    }

    @Test
    fun theAlbumArtistKeepsGuestSongsOnTheAlbum() {
        val tracks = listOf(
            track("1", "One", "Main", "Record", 1, albumArtist = "Main"),
            track("2", "Two", "Main feat. Guest", "Record", 2, albumArtist = "Main"),
        )
        val al = Library.albums(tracks).single()
        assertEquals("Main", al.artist)
        assertEquals(2, al.tracks.size)
    }

    @Test
    fun artistsIgnoreTheAndCase() {
        val tracks = listOf(
            track("1", "One", "The Band", "Rec 1", 1, folder = "f1"),
            track("2", "Two", "band", "Rec 2", 1, folder = "f2"),
        )
        val artists = Library.artists(Library.albums(tracks))
        assertEquals(1, artists.size)
        assertEquals(2, artists.single().albums.size)
    }

    @Test
    fun searchNeedsEveryWord() {
        val tracks = listOf(
            track("1", "Blue Moon", "Pretend Artist", "Pretend Album", 1),
            track("2", "Blue Sky", "Another", "Other", 1, folder = "fc"),
        )
        val albums = Library.albums(tracks)
        val r = Library.search("blue pretend", tracks, albums, Library.artists(albums))
        assertEquals(listOf("Blue Moon"), r.songs.map { it.title })
    }

    @Test
    fun aFreshListingKeepsSongsAlreadyReadAndDropsGoneOnes() {
        val old = LibraryData(tracks = listOf(track("1", "Read Title", "Read Artist", "Read Album", 5).copy(size = 100, modified = "t")))
        val files = listOf(
            Drive.FileInfo("1", "01 x.mp3", "audio/mpeg", 100, "fb", "t"),
            Drive.FileInfo("9", "02 New One.mp3", "audio/mpeg", 50, "fb", "t"),
            Drive.FileInfo("8", "photo.jpg", "image/jpeg", 50, "fb", "t"),
        )
        val merged = Library.merge(old, files, listOf(artistFolder, albumFolder), emptyMap(), 1L)
        assertEquals(2, merged.tracks.size)
        assertEquals("Read Title", merged.tracks.first { it.id == "1" }.title)
        val fresh = merged.tracks.first { it.id == "9" }
        assertEquals("New One", fresh.title)
        assertFalse(fresh.tagged)
    }

    @Test
    fun aChangedFileIsReadAgain() {
        val old = LibraryData(tracks = listOf(track("1", "Old", "A", "B", 1).copy(modified = "t1")))
        val merged = Library.merge(old, listOf(Drive.FileInfo("1", "01 New.mp3", "audio/mpeg", 100, "fb", "t2")), listOf(artistFolder, albumFolder), emptyMap(), 1L)
        assertFalse(merged.tracks.single().tagged)
    }

    @Test
    fun foldersShowOnlyWhereThereIsMusic() {
        val folders = listOf(artistFolder, albumFolder, Folder("empty", "Photos", null))
        val tracks = listOf(track("1", "One", "A", "B", 1, folder = "fb"))
        assertEquals(listOf("fa"), Library.foldersIn(null, folders, tracks).map { it.id })
        assertEquals(listOf("fb"), Library.foldersIn("fa", folders, tracks).map { it.id })
        assertEquals(1, Library.songsIn("fb", folders, tracks).size)
    }

    @Test
    fun theLibraryIsKeptAndReadBack() {
        val file = File.createTempFile("library", ".json")
        val data = LibraryData(tracks = listOf(track("1", "One", "A", "B", 1)), folders = listOf(albumFolder), art = mapOf("k" to "/x.jpg"), scannedAt = 5)
        Library.save(file, data)
        val back = Library.load(file)
        assertEquals(data.tracks, back.tracks)
        assertEquals(data.art, back.art)
        assertEquals(5L, back.scannedAt)
        file.delete()
    }

    @Test
    fun aMissingOrBrokenLibraryFileGivesAnEmptyLibrary() {
        val file = File.createTempFile("library", ".json")
        file.writeText("{not json")
        assertEquals(0, Library.load(file).tracks.size)
        file.delete()
        assertEquals(0, Library.load(file).tracks.size)
    }

    @Test
    fun clockReadsLikeAPlayer() {
        assertEquals("3:42", Library.clock(222_000))
        assertEquals("1:02:05", Library.clock(3_725_000))
        assertEquals("0:00", Library.clock(0))
    }
}
