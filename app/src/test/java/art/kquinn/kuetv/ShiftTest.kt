package art.kquinn.kuetv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Pause and rewind: the copy of a live channel kept on the device, on a pretend channel and a pretend clock. */
class ShiftTest {
    private var now = 1_000_000L
    private val pattern = ByteArray(6000) { (it % 251).toByte() }

    private fun copy(folder: File) = ShiftBuffer(
        open = { throw IOException("not used in these tests") },
        folder = folder, capBytes = 2500, pieceBytes = 1000, clock = { now },
    )

    /** What the pretend provider sends: 600 bytes a second. */
    private fun send(buffer: ShiftBuffer, seconds: Int) {
        repeat(seconds) {
            val from = buffer.head.toInt()
            buffer.take(pattern.copyOfRange(from, from + 600), 600)
            now += 1000
        }
    }

    @Test fun keepsTheNewestAndThrowsAwayTheOldestPastTheLimit() {
        val folder = Files.createTempDirectory("shift").toFile()
        val buffer = copy(folder)
        send(buffer, 5)
        assertEquals(3000L, buffer.head)
        // Past 2500 bytes the oldest whole piece goes.
        assertEquals(1000L, buffer.oldest)
        assertFalse(File(folder, buffer.id.toString() + "-0.ts").exists())
        assertTrue(File(folder, buffer.id.toString() + "-1.ts").exists())
        folder.deleteRecursively()
    }

    @Test fun readsBackExactlyWhatArrivedAcrossPieces() {
        val folder = Files.createTempDirectory("shift").toFile()
        val buffer = copy(folder)
        send(buffer, 5)
        val got = ByteArray(1300)
        var at = 1500L
        var filled = 0
        while (filled < got.size) {
            val n = buffer.read(at, got, filled, got.size - filled, 50)
            assertTrue(n > 0)
            at += n
            filled += n
        }
        assertArrayEquals(pattern.copyOfRange(1500, 2800), got)
        folder.deleteRecursively()
    }

    @Test fun waitsAtTheNewestEndAndRefusesWhatHasGone() {
        val folder = Files.createTempDirectory("shift").toFile()
        val buffer = copy(folder)
        send(buffer, 5)
        // Nothing new arrives within the wait: nothing read, so the player counts a stop.
        assertEquals(0, buffer.read(3000, ByteArray(10), 0, 10, 30))
        var refused = false
        try {
            buffer.read(500, ByteArray(10), 0, 10, 30)
        } catch (e: IOException) {
            refused = true
        }
        assertTrue(refused)
        buffer.close()
        // Once closed, the reader is told the channel has ended.
        assertEquals(-1, buffer.read(3000, ByteArray(10), 0, 10, 30))
        folder.deleteRecursively()
    }

    @Test fun turnsATimeIntoAPlaceAndBack() {
        val folder = Files.createTempDirectory("shift").toFile()
        val buffer = copy(folder)
        val started = now
        send(buffer, 5)
        // 600 bytes arrived each second: 2.5 seconds after the start is 2100 bytes in.
        assertEquals(2100L, buffer.offsetAt(started + 2500))
        assertEquals(started + 2500, buffer.timeAt(2100))
        // Further back than is kept: the oldest kept place.
        assertEquals(buffer.oldest, buffer.offsetAt(started - 60_000))
        // Later than anything: the newest end.
        assertEquals(buffer.head, buffer.offsetAt(now + 60_000))
        folder.deleteRecursively()
    }
}
