package art.kquinn.kuetv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pause and rewind for live TV.
 *
 * While a live channel plays full screen, KueTV itself fetches the channel from the provider on its one
 * connection and keeps what arrives on the TV's storage, in pieces, the newest last. The player reads
 * from that copy, not from the provider. So a pause doesn't stop the fetching: the channel keeps
 * arriving while the picture waits. Rewinding moves the player back through the copy; "Back to live"
 * moves it to the newest end. Once the copy reaches its size limit, the oldest piece is thrown away.
 *
 * Each byte's arrival time is noted as it comes, so "back 30 seconds" can be turned into a place in the copy.
 */
class ShiftBuffer(
    /** Opens the channel: one connection to the provider. Called again whenever the connection drops. */
    private val open: () -> InputStream,
    private val folder: File,
    /** The most the copy keeps; the oldest goes first past this. */
    val capBytes: Long,
    private val pieceBytes: Long = 8L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val id: Int = ids.incrementAndGet()

    /** Everything that has arrived, counted from the first byte: the newest end of the copy. */
    @Volatile var head = 0L
        private set

    /** The oldest byte still kept. */
    @Volatile var oldest = 0L
        private set

    @Volatile var closed = false
        private set

    /** When the connection to the provider started failing; 0 while it is fine. */
    @Volatile var failingSince = 0L
        private set

    @Volatile var lastProblem = ""
        private set

    /** Told when the provider has been joined (again), and given every byte that arrives: for recording. */
    @Volatile var onJoined: (() -> Unit)? = null
    @Volatile var onBytes: ((ByteArray, Int, Int) -> Unit)? = null

    private val lock = Object()

    // Where the copy had got to, and when: [offset, time] pairs, one every half second or so.
    private val marks = ArrayList<LongArray>()
    private var writing: FileOutputStream? = null
    private var writingPiece = -1L
    private var thread: Thread? = null

    init {
        folder.mkdirs()
    }

    private fun piece(index: Long) = File(folder, "$id-$index.ts")

    fun start() {
        thread = Thread({ run() }, "kuetv-shift-$id").apply { isDaemon = true; start() }
    }

    private fun run() {
        val buffer = ByteArray(64 * 1024)
        while (!closed) {
            try {
                open().use { input ->
                    failingSince = 0
                    onJoined?.invoke()
                    while (!closed) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) take(buffer, count)
                    }
                }
            } catch (e: IOException) {
                if (failingSince == 0L) failingSince = clock()
                lastProblem = e.message.orEmpty()
            }
            // The stream ended or dropped: join again after a moment.
            if (!closed) runCatching { Thread.sleep(1_500) }
        }
    }

    /** Adds what has arrived to the copy. Public for the tests; the fetching thread is the only other caller. */
    fun take(bytes: ByteArray, length: Int) {
        var from = 0
        while (from < length) {
            val index = head / pieceBytes
            val room = (pieceBytes - head % pieceBytes).toInt()
            val count = minOf(room, length - from)
            if (index != writingPiece) {
                runCatching { writing?.close() }
                writing = FileOutputStream(piece(index))
                writingPiece = index
            }
            writing!!.write(bytes, from, count)
            synchronized(lock) {
                head += count
                lock.notifyAll()
            }
            from += count
        }
        onBytes?.invoke(bytes, 0, length)
        mark()
        trim()
    }

    private fun mark() {
        val now = clock()
        synchronized(marks) {
            val last = marks.lastOrNull()
            if (last == null || now - last[1] >= 500) marks += longArrayOf(head, now)
        }
    }

    private fun trim() {
        while (head - oldest > capBytes) {
            val index = oldest / pieceBytes
            if (index >= head / pieceBytes) break
            piece(index).delete()
            synchronized(lock) { oldest = (index + 1) * pieceBytes }
            synchronized(marks) { marks.removeAll { it[0] < oldest } }
        }
    }

    /** When the byte at [offset] arrived. */
    fun timeAt(offset: Long): Long = synchronized(marks) {
        if (marks.isEmpty()) return clock()
        val after = marks.indexOfFirst { it[0] >= offset }
        when {
            after < 0 -> marks.last()[1]
            after == 0 -> marks[0][1]
            else -> {
                val a = marks[after - 1]
                val b = marks[after]
                if (b[0] == a[0]) b[1] else a[1] + (b[1] - a[1]) * (offset - a[0]) / (b[0] - a[0])
            }
        }
    }

    /** The place in the copy that arrived at [time], kept within what is still held. */
    fun offsetAt(time: Long): Long = synchronized(marks) {
        if (marks.isEmpty()) return head
        val after = marks.indexOfFirst { it[1] >= time }
        val at = when {
            after < 0 -> head
            after == 0 -> marks[0][0]
            else -> {
                val a = marks[after - 1]
                val b = marks[after]
                if (b[1] == a[1]) b[0] else a[0] + (b[0] - a[0]) * (time - a[1]) / (b[1] - a[1])
            }
        }
        at.coerceIn(oldest, head)
    }

    /** When the oldest byte still kept arrived: how far back the copy goes. */
    fun oldestTime(): Long = timeAt(oldest)

    /**
     * Reads from the copy at [position] into [target]. Waits up to [waitMs] for something to arrive
     * when the reader has caught up with the newest end. Gives back the count read; -1 once closed;
     * 0 when nothing arrived in time.
     */
    fun read(position: Long, target: ByteArray, offset: Int, length: Int, waitMs: Long): Int {
        if (length == 0) return 0
        // The wait is timed on the device's own running clock, whatever clock the arrival times are noted by.
        val until = System.nanoTime() + waitMs * 1_000_000
        synchronized(lock) {
            while (!closed && head <= position) {
                val left = (until - System.nanoTime()) / 1_000_000
                if (left <= 0) return 0
                try {
                    lock.wait(minOf(left, 250))
                } catch (e: InterruptedException) {
                    throw InterruptedIOException("stopped")
                }
            }
            if (closed) return -1
        }
        if (position < oldest) throw IOException("That part is no longer kept.")
        val index = position / pieceBytes
        val within = position % pieceBytes
        val count = minOf(length.toLong(), head - position, pieceBytes - within).toInt()
        RandomAccessFile(piece(index), "r").use { file ->
            file.seek(within)
            return file.read(target, offset, count)
        }
    }

    /** Stops fetching and throws the copy away. */
    fun close() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
        runCatching { thread?.interrupt() }
        Thread({
            runCatching { thread?.join(3_000) }
            runCatching { writing?.close() }
            folder.listFiles()?.filter { it.name.startsWith("$id-") }?.forEach { it.delete() }
        }, "kuetv-shift-close-$id").start()
        sessions.remove(id)
    }

    companion object {
        private val ids = AtomicInteger(0)

        /** The copies being made now, by number, for the player's reader to find. */
        val sessions = ConcurrentHashMap<Int, ShiftBuffer>()

        fun folder(context: Context) = File(context.cacheDir, "shift")

        /** Clears copies left behind when the app was closed while one was being made. */
        fun tidy(context: Context) {
            runCatching { folder(context).deleteRecursively() }
        }

        /** The size limit for a copy: about half an hour of HD, less if the storage is short. Null when there is too little room. */
        fun capFor(context: Context): Long? {
            val free = folder(context).apply { mkdirs() }.usableSpace
            val cap = minOf(1_500L * 1024 * 1024, free / 3)
            return if (cap < 150L * 1024 * 1024) null else cap
        }

        /** Starts fetching [url] into a copy on this device. Null when the storage is too short. */
        fun begin(context: Context, url: String, headers: Map<String, String>): ShiftBuffer? {
            val cap = capFor(context) ?: return null
            val http = Provider.http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
            val buffer = ShiftBuffer(
                open = {
                    val request = Request.Builder().url(url).header("User-Agent", Provider.USER_AGENT)
                    headers.forEach { (k, v) -> request.header(k, v) }
                    val response = http.newCall(request.build()).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        throw IOException("The provider answered " + response.code)
                    }
                    response.body.byteStream()
                },
                folder = folder(context),
                capBytes = cap,
            )
            sessions[buffer.id] = buffer
            buffer.start()
            return buffer
        }

        /** The address the player is given for a copy, starting at [offset]. */
        fun address(buffer: ShiftBuffer, offset: Long): String = "shift://" + buffer.id + "/" + offset
    }
}

/** How long the player waits at the newest end for the provider before counting it as a stop. */
private const val SHIFT_WAIT_MS = 15_000L

/** The player's reader for a copy: "shift://<copy>/<place>". */
@OptIn(UnstableApi::class)
class ShiftSource : DataSource {
    private var buffer: ShiftBuffer? = null
    private var position = 0L
    private var uri: android.net.Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val id = dataSpec.uri.host?.toIntOrNull() ?: throw IOException("Not a copy's address.")
        val start = dataSpec.uri.lastPathSegment?.toLongOrNull() ?: 0L
        val found = ShiftBuffer.sessions[id] ?: throw IOException("The copy has gone.")
        buffer = found
        position = maxOf(start + dataSpec.position, found.oldest)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val from = this.buffer ?: return C.RESULT_END_OF_INPUT
        if (length == 0) return 0
        val count = from.read(position, buffer, offset, length, SHIFT_WAIT_MS)
        if (count < 0) return C.RESULT_END_OF_INPUT
        if (count == 0) throw IOException("Nothing arriving from the provider" + if (from.lastProblem.isEmpty()) "" else ": " + from.lastProblem)
        position += count
        return count
    }

    override fun getUri(): android.net.Uri? = uri
    override fun close() {
        buffer = null
    }
}
