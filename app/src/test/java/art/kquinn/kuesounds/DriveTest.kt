package art.kquinn.kuesounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks on a pretend page of a Drive listing, written the way Google's answers are laid out. */
class DriveTest {
    @Test
    fun aListingPageIsRead() {
        val json = """
            {"nextPageToken":"abc","files":[
              {"id":"1","name":"01 Song.mp3","mimeType":"audio/mpeg","size":"1234","parents":["p1"],"modifiedTime":"2026-01-01T00:00:00Z"},
              {"id":"2","name":"Loose.flac","mimeType":"audio/flac"}
            ]}
        """.trimIndent()
        val page = Drive.parsePage(json)
        assertEquals("abc", page.next)
        assertEquals(2, page.files.size)
        assertEquals(1234L, page.files[0].size)
        assertEquals("p1", page.files[0].parent)
        assertNull(page.files[1].parent)
        assertEquals(0L, page.files[1].size)
    }

    @Test
    fun theLastPageHasNoNext() {
        assertNull(Drive.parsePage("""{"files":[]}""").next)
    }

    @Test
    fun coverPicturesAreKnownByName() {
        assertTrue(Drive.isCoverName("cover.jpg"))
        assertTrue(Drive.isCoverName("Folder.JPG"))
        assertTrue(Drive.isCoverName("AlbumArtSmall.jpg"))
        assertFalse(Drive.isCoverName("holiday.jpg"))
    }

    @Test
    fun theUpdateFileIsRead() {
        val v = Updates.parse("code=7\nname=0.1.7\n")!!
        assertEquals(7, v.code)
        assertEquals("0.1.7", v.name)
        assertTrue(Updates.isNewer(v, 6))
        assertNull(Updates.parse("nothing here"))
    }
}
