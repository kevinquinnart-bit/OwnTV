package art.kquinn.kuesounds

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writing the right details into a music file on the phone: every picture inside it is taken out, the
 * catalogue's cover is put in as the front cover, and the album name, album artist and year are set.
 * The song itself (the sound) is not touched. Works on MP3, M4A, FLAC, Ogg, WMA, AIFF and WAV files.
 */
object Tagger {
    init {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** The kinds of file whose details can be written. */
    val fixable = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wma", "aif", "aiff", "wav", "dsf")

    fun canFix(fileName: String): Boolean = Library.ending(fileName) in fixable

    data class Details(val album: String, val albumArtist: String, val year: Int, val cover: File?)

    /** What a file holds, read back after a change to check it. */
    data class Inside(val album: String, val albumArtist: String, val year: String, val pictures: Int, val firstPicture: ByteArray?, val lengthSeconds: Int)

    fun read(file: File): Inside {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val pics = tag?.artworkList.orEmpty()
        return Inside(
            album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
            albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
            year = tag?.getFirst(FieldKey.YEAR).orEmpty(),
            pictures = pics.size,
            firstPicture = pics.firstOrNull()?.binaryData,
            lengthSeconds = audio.audioHeader?.trackLength ?: 0,
        )
    }

    /**
     * Writes [d] into [file], in place, then reads the file back to check it: the details must be
     * there, there must be exactly one picture (or none when no cover was given), and the song must be
     * as long as before. Throws when the check fails, so a damaged file is never sent back to the Drive.
     */
    fun fix(file: File, d: Details) {
        val before = AudioFileIO.read(file)
        val length = before.audioHeader?.trackLength ?: 0
        val tag = before.tagOrCreateAndSetDefault
        tag.deleteArtworkField()
        if (d.cover != null) {
            val art = CoverArt().apply { setFromFile(d.cover) }
            art.pictureType = PictureTypes.DEFAULT_ID
            art.description = ""
            tag.setField(art)
        }
        tag.setField(FieldKey.ALBUM, d.album)
        if (d.albumArtist.isNotBlank()) tag.setField(FieldKey.ALBUM_ARTIST, d.albumArtist)
        if (d.year > 0) tag.setField(FieldKey.YEAR, d.year.toString())
        AudioFileIO.write(before)

        val after = read(file)
        check(after.album == d.album) { "the album name didn't stick" }
        check(after.pictures == (if (d.cover != null) 1 else 0)) { "the file holds " + after.pictures + " pictures after the change" }
        check(kotlin.math.abs(after.lengthSeconds - length) <= 1) { "the song's length changed" }
    }

    /**
     * A cover picture for writing into a file. FLAC and Ogg files store the picture's size beside it;
     * the phone-side picture in the tag library can't measure it, so it is read from the JPEG itself.
     */
    private class CoverArt : AndroidArtwork() {
        override fun setImageFromData(): Boolean {
            val (w, h) = jpegSize(binaryData) ?: return true
            width = w
            height = h
            return true
        }
    }

    /** The width and height written in a JPEG picture's header. Null when it isn't a JPEG. */
    fun jpegSize(b: ByteArray?): Pair<Int, Int>? {
        if (b == null || b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) { i++; continue }
            val marker = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                return w to h
            }
            i += 2 + len
        }
        return null
    }
}
