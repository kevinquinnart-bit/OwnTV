package art.kquinn.kuetv

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Records a live channel by keeping a copy of what the player itself is being sent, so recording uses
 * no second connection to the provider. Everything the player reads passes through [take]; between
 * [start] and [stop] it is also written to a file.
 *
 * A live stream is a run of 188-byte packets. A recording begins on a packet's first byte, so the file
 * plays from its start.
 */
class Recorder(private val minFreeBytes: Long = 200L * 1024 * 1024, private val onProblem: (String) -> Unit = {}) {
    private var out: OutputStream? = null
    private var file: File? = null
    private var seen = 0L
    private var onPacketStart = false
    private var written = 0L
    private var checkedAt = 0L

    val isRecording: Boolean @Synchronized get() = out != null

    @Synchronized
    fun start(to: File) {
        to.parentFile?.mkdirs()
        out = BufferedOutputStream(FileOutputStream(to), 64 * 1024)
        file = to
        onPacketStart = false
        written = 0
        checkedAt = 0
    }

    /** Ends the recording and gives back its file; null when nothing was being recorded. */
    @Synchronized
    fun stop(): File? {
        val done = file
        runCatching { out?.close() }
        out = null
        file = null
        return done
    }

    /** The player has opened the stream again (a new channel, or a reconnection): packets are counted from here. */
    @Synchronized
    fun newConnection() {
        seen = 0
        onPacketStart = false
    }

    @Synchronized
    fun take(bytes: ByteArray, offset: Int, length: Int) {
        val to = out
        var from = offset
        var count = length
        if (to != null && !onPacketStart) {
            val skip = ((PACKET - seen % PACKET) % PACKET).toInt()
            if (count > skip) {
                from += skip
                count -= skip
                onPacketStart = true
            }
        }
        seen += length
        if (to == null || !onPacketStart) return
        try {
            to.write(bytes, from, count)
            written += count
            if (written - checkedAt >= CHECK_EVERY) {
                checkedAt = written
                val free = file?.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < minFreeBytes) give("Recording stopped: the storage here is nearly full.")
            }
        } catch (e: IOException) {
            give("Recording stopped: it couldn't be written to the storage here.")
        }
    }

    /** Stops writing but keeps the file for [stop] to hand back, and tells the screen why. */
    private fun give(why: String) {
        runCatching { out?.close() }
        out = null
        onProblem(why)
    }

    companion object {
        const val PACKET = 188L
        private const val CHECK_EVERY = 16L * 1024 * 1024
    }
}
