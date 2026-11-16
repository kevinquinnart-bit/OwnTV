package art.kquinn.kuetv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Recording a copy of a pretend stream as the player reads it. */
class RecorderTest {
    private fun stream(size: Int) = ByteArray(size) { (it % 251).toByte() }
    private fun temp() = File.createTempFile("kuetv", ".ts").also { it.deleteOnExit() }

    @Test fun recordingStartsOnAPacketAndKeepsEverythingAfterIt() {
        val all = stream(1000)
        val file = temp()
        val recorder = Recorder()
        recorder.newConnection()
        recorder.take(all, 0, 100)
        recorder.start(file)
        // Pieces of awkward sizes, the first too short to reach the next packet.
        recorder.take(all, 100, 50)
        recorder.take(all, 150, 400)
        recorder.take(all, 550, 450)
        assertEquals(file, recorder.stop())
        assertArrayEquals(all.copyOfRange(188, 1000), file.readBytes())
    }

    @Test fun nothingIsWrittenOutsideARecording() {
        val all = stream(600)
        val file = temp()
        val recorder = Recorder()
        recorder.take(all, 0, 188)
        recorder.start(file)
        recorder.take(all, 188, 188)
        recorder.stop()
        recorder.take(all, 376, 224)
        assertArrayEquals(all.copyOfRange(188, 376), file.readBytes())
        assertNull(recorder.stop())
    }

    @Test fun aReconnectionCarriesOnInTheSameFileFromAPacketStart() {
        val first = stream(376)
        val second = ByteArray(400) { 7 }
        val file = temp()
        val recorder = Recorder()
        recorder.start(file)
        recorder.take(first, 0, 376)
        recorder.newConnection()
        recorder.take(second, 0, 400)
        recorder.stop()
        assertEquals(776, file.length())
    }

    @Test fun stopsAndSaysSoWhenStorageRunsLow() {
        var said: String? = null
        val file = temp()
        // Asks for more free room than any machine has, so the first check fails.
        val recorder = Recorder(minFreeBytes = Long.MAX_VALUE, onProblem = { said = it })
        recorder.start(file)
        val piece = ByteArray(188 * 1024)
        repeat(100) { recorder.take(piece, 0, piece.size) }
        assertEquals("Recording stopped: the storage here is nearly full.", said)
        assertEquals(false, recorder.isRecording)
        assertEquals(file, recorder.stop())
    }
}
