package tv.own.owntv.features.stremio

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Kevin's copy: plays one torrent at a time as an ordinary web stream.
 *
 * [open] joins the torrent, picks the film or episode file inside it, and downloads that file from
 * the start. The file is handed to the player through an address on this box
 * ("http://127.0.0.1:port/…"); a part the player asks for that hasn't arrived yet is fetched first
 * and the player simply waits for it, so jumping forward works.
 *
 * The download lives in the app's cache and is deleted when the torrent is stopped, when another is
 * opened, and when the app next starts. Nothing is kept.
 */
class TorrentEngine(context: Context) {

    sealed interface Opened {
        data class Ready(val url: String) : Opened
        /** Nobody answered, or nothing arrived in time. */
        data object NoPeers : Opened
        /** The file is bigger than the free space on this box. */
        data object TooBig : Opened
        data object Failed : Opened
    }

    private val app = context.applicationContext
    private val openMutex = Mutex()
    private val lock = Any()
    private val opening = AtomicBoolean(false)

    private var session: SessionManager? = null
    @Volatile private var current: Active? = null
    private var server: ServerSocket? = null
    @Volatile private var sessionIdleSince = 0L

    private class Active(
        val hash: String,
        val handle: TorrentHandle,
        val file: File,
        val fileOffset: Long,
        val fileSize: Long,
        val pieceLength: Int,
        val lastPiece: Int,
    ) {
        @Volatile var stopped = false
        val connections = AtomicInteger(0)
        @Volatile var lastUsed = SystemClock.elapsedRealtime()

        fun have(piece: Int): Boolean = !stopped && runCatching { handle.havePiece(piece) }.getOrDefault(false)
        fun pieceAt(filePos: Long): Int = ((fileOffset + filePos) / pieceLength).toInt()
    }

    /** Free room for a torrent's file, after the safety margin. */
    fun freeBytes(): Long = (storageDir(app).parentFile?.usableSpace ?: 0L) - SPACE_MARGIN_BYTES

    /**
     * Joins [stream]'s torrent and waits until its first part has arrived. The address in
     * [Opened.Ready] then plays like any web stream. Stops whatever torrent was open before.
     */
    suspend fun open(stream: StremioAddons.Stream): Opened {
        val hash = stream.infoHash ?: return Opened.Failed
        return withContext(Dispatchers.IO) {
            openMutex.withLock {
                opening.set(true)
                try {
                    openLocked(hash, stream)
                } catch (e: CancellationException) {
                    stop()
                    throw e
                } catch (e: Throwable) {
                    // UnsatisfiedLinkError lands here too: a box the engine doesn't run on.
                    Log.w(TAG, "torrent failed to open", e)
                    stop()
                    Opened.Failed
                } finally {
                    opening.set(false)
                }
            }
        }
    }

    private suspend fun openLocked(hash: String, stream: StremioAddons.Stream): Opened {
        stop()
        val s = startSession()
        val dir = storageDir(app)
        dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.isDirectory) return Opened.Failed

        val trackers = (stream.trackers + DEFAULT_TRACKERS).distinct()
        val started = SystemClock.elapsedRealtime()
        val bytes = runInterruptible { s.fetchMagnet(magnet(hash, trackers), METADATA_TIMEOUT_SECS, dir) }
            ?: return Opened.NoPeers.also { Log.i(TAG, "no torrent details within ${METADATA_TIMEOUT_SECS}s") }
        val info = TorrentInfo.bdecode(bytes)
        val files = info.files()
        val names = (0 until files.numFiles()).map { files.filePath(it) }
        val sizes = (0 until files.numFiles()).map { files.fileSize(it) }
        val index = chooseFile(names, sizes, stream.fileIdx) ?: return Opened.Failed
        val size = sizes[index]
        if (size > freeBytes()) {
            Log.i(TAG, "torrent file needs $size bytes, free ${freeBytes()}")
            return Opened.TooBig
        }

        val priorities = Array(files.numFiles()) { if (it == index) Priority.DEFAULT else Priority.IGNORE }
        s.download(info, dir, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
        // The torrent is added in the background; its handle appears a moment later.
        var found: TorrentHandle? = null
        var waited = 0L
        while (waited < 5_000L) {
            found = s.find(info.infoHash())?.takeIf { it.isValid }
            if (found != null) break
            delay(100); waited += 100
        }
        val handle: TorrentHandle = found ?: return Opened.Failed
        trackers.forEach { t -> runCatching { handle.addTracker(AnnounceEntry(t)) } }
        handle.resume()

        val pieceLength = info.pieceLength()
        val offset = files.fileOffset(index)
        val active = Active(
            hash = hash,
            handle = handle,
            file = File(files.filePath(index, dir.absolutePath)),
            fileOffset = offset,
            fileSize = size,
            pieceLength = pieceLength,
            lastPiece = ((offset + size - 1) / pieceLength).toInt(),
        )
        synchronized(lock) { current = active }

        // The start of the file first, so the picture can begin; then the very end, where many
        // files keep the index the player reads before it can jump about.
        val first = active.pieceAt(0)
        val head = startPieces(first, active.lastPiece, pieceLength)
        head.forEachIndexed { i, p -> handle.setPieceDeadline(p, 500 + i * 250) }
        if (active.lastPiece !in head) handle.setPieceDeadline(active.lastPiece, 3_000)

        waited = 0L
        while (!head.all { active.have(it) }) {
            if (waited >= FIRST_DATA_TIMEOUT_MS) {
                Log.i(TAG, "torrent start didn't arrive within ${FIRST_DATA_TIMEOUT_MS}ms")
                stop()
                return Opened.NoPeers
            }
            delay(250); waited += 250
        }
        val port = startServer()
        active.lastUsed = SystemClock.elapsedRealtime()
        Log.i(TAG, "torrent ready in ${SystemClock.elapsedRealtime() - started}ms, file ${size / 1_048_576} MB")
        return Opened.Ready("http://127.0.0.1:$port/t/$hash/video.${extension(names[index])}")
    }

    /** Leaves the torrent and deletes what was downloaded. Safe to call at any time. */
    fun stop() {
        val active = synchronized(lock) { current.also { current = null } } ?: return
        active.stopped = true
        runCatching { session?.remove(active.handle) }
        runCatching { storageDir(app).deleteRecursively() }
        sessionIdleSince = SystemClock.elapsedRealtime()
        Log.i(TAG, "torrent stopped")
    }

    private fun startSession(): SessionManager {
        synchronized(lock) {
            session?.let { return it }
            return newSession().also { session = it; sessionIdleSince = SystemClock.elapsedRealtime() }
        }
    }

    private fun newSession(): SessionManager {
        val settings = SettingsPack()
            .activeDownloads(2)
            .connectionsLimit(120)
            .uploadRateLimit(UPLOAD_LIMIT_BYTES_PER_SEC)
        val params = SessionParams(settings)
        // Plain file writes: a part marked as arrived is already in the file the player reads.
        params.setPosixDiskIO()
        return SessionManager(false).also { it.start(params) }
    }

    private fun startServer(): Int {
        synchronized(lock) { server?.let { return it.localPort } }
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        synchronized(lock) { server = socket }
        thread(name = "kevin-torrent-http", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(name = "kevin-torrent-conn", isDaemon = true) { runCatching { serve(client) } }
            }
        }
        thread(name = "kevin-torrent-watch", isDaemon = true) { watch() }
        return socket.localPort
    }

    /** Stops a torrent nobody is reading any more, and the engine itself after a longer quiet spell. */
    private fun watch() {
        while (true) {
            runCatching { Thread.sleep(15_000) }
            if (opening.get()) continue
            val now = SystemClock.elapsedRealtime()
            val active = current
            if (active != null) {
                if (active.connections.get() == 0 && now - active.lastUsed > IDLE_STOP_MS) stop()
            } else if (now - sessionIdleSince > SESSION_STOP_MS) {
                val idle = synchronized(lock) { if (current == null && !opening.get()) session.also { session = null } else null }
                if (idle != null) {
                    runCatching { idle.stop() }
                    Log.i(TAG, "torrent engine stopped after a quiet spell")
                }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 10_000
            val input = sock.getInputStream().buffered()
            val requestLine = readLine(input) ?: return
            var rangeHeader: String? = null
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                if (line.startsWith("range:", ignoreCase = true)) rangeHeader = line.substringAfter(':').trim()
            }
            val parts = requestLine.split(' ')
            val out = sock.getOutputStream()
            val active = current
            val method = parts.getOrNull(0)
            if (active == null || parts.size < 2 || !parts[1].startsWith("/t/${active.hash}/") || (method != "GET" && method != "HEAD")) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val range = parseRange(rangeHeader, active.fileSize)
            if (range == null) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${active.fileSize}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                return
            }
            val (start, end) = range
            val header = StringBuilder()
            if (rangeHeader != null) {
                header.append("HTTP/1.1 206 Partial Content\r\n")
                header.append("Content-Range: bytes $start-$end/${active.fileSize}\r\n")
            } else {
                header.append("HTTP/1.1 200 OK\r\n")
            }
            header.append("Content-Type: ${contentType(parts[1])}\r\n")
            header.append("Accept-Ranges: bytes\r\n")
            header.append("Content-Length: ${end - start + 1}\r\n")
            header.append("Connection: close\r\n\r\n")
            out.write(header.toString().toByteArray())
            out.flush()
            if (method == "HEAD") return

            active.connections.incrementAndGet()
            try {
                sendBody(sock, out, active, start, end)
            } finally {
                active.connections.decrementAndGet()
                active.lastUsed = SystemClock.elapsedRealtime()
            }
    
        }
    }

    private fun sendBody(sock: Socket, out: OutputStream, active: Active, start: Long, end: Long) {
        val socketIn = sock.getInputStream()
        val window = (WINDOW_BYTES / active.pieceLength).toInt().coerceAtLeast(4)
        val buffer = ByteArray(64 * 1024)
        var file: RandomAccessFile? = null
        var pos = start
        try {
            while (pos <= end && !active.stopped) {
                val piece = active.pieceAt(pos)
                // Ask for this part now and the next few soon after, nearest first.
                for (k in 0 until window) {
                    val p = piece + k
                    if (p > active.lastPiece) break
                    if (!active.have(p)) runCatching { active.handle.setPieceDeadline(p, 800 + k * 400) }
                }
                var waited = 0L
                while (!active.have(piece)) {
                    if (active.stopped || waited > STALL_MS) return
                    // Doubles as the wait: reading returns at once only when the player has hung up.
                    sock.soTimeout = 250
                    val gone = try { socketIn.read() == -1 } catch (_: SocketTimeoutException) { false }
                    if (gone) return
                    waited += 250
                }
                val pieceEnd = (piece + 1).toLong() * active.pieceLength - active.fileOffset - 1
                val stopAt = minOf(end, pieceEnd)
                val raf = file ?: RandomAccessFile(active.file, "r").also { file = it }
                raf.seek(pos)
                while (pos <= stopAt) {
                    val want = minOf(buffer.size.toLong(), stopAt - pos + 1).toInt()
                    val n = raf.read(buffer, 0, want)
                    if (n <= 0) return
                    out.write(buffer, 0, n)
                    pos += n
                }
                active.lastUsed = SystemClock.elapsedRealtime()
            }
            out.flush()
        } finally {
            runCatching { file?.close() }
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8_192) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return null
    }

    companion object {
        private const val TAG = "KevinTorrent"
        private const val DIR = "kevin_torrent"
        private const val METADATA_TIMEOUT_SECS = 30
        private const val FIRST_DATA_TIMEOUT_MS = 45_000L
        private const val STALL_MS = 120_000L
        private const val IDLE_STOP_MS = 900_000L
        private const val SESSION_STOP_MS = 300_000L
        private const val WINDOW_BYTES = 8L * 1024 * 1024
        private const val START_BYTES = 3L * 1024 * 1024
        private const val SPACE_MARGIN_BYTES = 300L * 1024 * 1024
        private const val UPLOAD_LIMIT_BYTES_PER_SEC = 64 * 1024

        /** Used as well as whatever trackers the add-on names. */
        private val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
        )

        private val videoExtensions = setOf("mkv", "mp4", "avi", "m4v", "mov", "ts", "webm", "wmv", "mpg", "mpeg", "m2ts")

        /** Whichever of the app's two cache folders has more room. */
        internal fun storageDir(context: Context): File {
            val roots = listOfNotNull(runCatching { context.externalCacheDir }.getOrNull(), context.cacheDir)
            val root = roots.maxByOrNull { it.usableSpace } ?: context.cacheDir
            return File(root, DIR)
        }

        /** Deletes anything a torrent left behind, e.g. after the app was closed mid-film. */
        fun clearLeftovers(context: Context) {
            listOfNotNull(runCatching { context.externalCacheDir }.getOrNull(), context.cacheDir)
                .forEach { runCatching { File(it, DIR).deleteRecursively() } }
        }

        internal fun extension(name: String): String =
            name.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it in videoExtensions } ?: "mkv"

        internal fun magnet(hash: String, trackers: List<String>): String =
            "magnet:?xt=urn:btih:$hash" + trackers.joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }

        /**
         * The file to play: the one the add-on pointed at, else the biggest video file, else the
         * biggest file. Null when the torrent has no files.
         */
        internal fun chooseFile(names: List<String>, sizes: List<Long>, wanted: Int?): Int? {
            if (names.isEmpty()) return null
            if (wanted != null && wanted in names.indices) return wanted
            val videos = names.indices.filter { names[it].substringAfterLast('.', "").lowercase() in videoExtensions }
            return (videos.ifEmpty { names.indices.toList() }).maxByOrNull { sizes[it] }
        }

        /** The parts that must arrive before playing starts: the first [START_BYTES], at least two parts. */
        internal fun startPieces(first: Int, last: Int, pieceLength: Int): List<Int> {
            val count = ((START_BYTES + pieceLength - 1) / pieceLength).toInt().coerceAtLeast(2)
            return (first..minOf(last, first + count - 1)).toList()
        }

        /**
         * The bytes a "Range" header asks for, as first and last position. No header means the whole
         * file. Null when the range is outside the file or can't be read.
         */
        internal fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
            if (size <= 0L) return null
            if (header == null) return 0L to size - 1
            val spec = header.trim().removePrefix("bytes=").substringBefore(',').trim()
            val from = spec.substringBefore('-').trim()
            val to = spec.substringAfter('-', "").trim()
            return if (from.isEmpty()) {
                // "-500": the last 500 bytes.
                val n = to.toLongOrNull()?.takeIf { it > 0L } ?: return null
                maxOf(0L, size - n) to size - 1
            } else {
                val start = from.toLongOrNull() ?: return null
                if (start < 0L || start >= size) return null
                val end = if (to.isEmpty()) size - 1 else minOf(to.toLongOrNull() ?: return null, size - 1)
                if (end < start) null else start to end
            }
        }

        internal fun contentType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "mp4", "m4v", "mov" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts" -> "video/mp2t"
            else -> "application/octet-stream"
        }
    }
}
