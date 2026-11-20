package art.kquinn.kuesounds

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks on writing the right cover and details into real music files: short tone files made for the
 * test (MP3, M4A, FLAC with a wrong red cover inside; Ogg with none). No song of Kevin's is used.
 * The fixed copies are left in build/tagger-out so they can be played back to check the sound.
 */
class TaggerTest {
    private fun copyOf(name: String): File {
        val out = File("build/tagger-out").apply { mkdirs() }
        val f = File(out, name)
        javaClass.getResourceAsStream("/tagger/$name")!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    private val right: File by lazy { copyOf("right.jpg") }

    private fun fixes(name: String, hadPicture: Boolean) {
        val f = copyOf(name)
        val before = Tagger.read(f)
        assertEquals("Wrong Album", before.album)
        assertEquals(hadPicture, before.pictures > 0)
        val length = before.lengthSeconds

        Tagger.fix(f, Tagger.Details("Right Album", "Right Artist", 1995, right))

        val after = Tagger.read(f)
        assertEquals("Right Album", after.album)
        assertEquals("Right Artist", after.albumArtist)
        assertTrue(after.year.startsWith("1995"))
        assertEquals(1, after.pictures)
        assertArrayEquals(right.readBytes(), after.firstPicture)
        assertEquals(length, after.lengthSeconds)
    }

    @Test fun mp3() = fixes("song.mp3", hadPicture = true)

    @Test fun m4a() = fixes("song.m4a", hadPicture = true)

    @Test fun flac() = fixes("song.flac", hadPicture = true)

    @Test fun ogg() = fixes("song.ogg", hadPicture = false)

    @Test
    fun onlyKnownKindsAreTouched() {
        assertTrue(Tagger.canFix("a.MP3"))
        assertTrue(!Tagger.canFix("a.3ga"))
        assertTrue(!Tagger.canFix("a.aac"))
    }
}
