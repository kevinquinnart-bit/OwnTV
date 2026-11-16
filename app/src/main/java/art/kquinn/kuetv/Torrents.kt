package art.kquinn.kuetv

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Plays a film from a torrent while it is still arriving.
 *
 * One torrent at a time. Its pieces are asked for in the order the film plays, starting from wherever
 * the player is reading, and kept in a temporary folder that is emptied when the player closes, when
 * another torrent starts, and when the app next opens. Only the one film file in it is fetched.
 *
 * A torrent is shared between the people fetching it: while one plays, the pieces already here are
 * also given to others. That giving is held to a small trickle.
 */
object Torrents {
    private const val FIND_SECONDS = 35
    private const val PIECE_WAIT_MS = 90_000L
    private const val ROOM_TO_SPARE = 300L * 1024 * 1024
    private const val GIVING_LIMIT = 30 * 1024

    private var session: SessionManager? = null
    private var current: Open? = null

    fun folder(context: Context): File = File(context.externalCacheDir ?: context.cacheDir, "torrents")

    /** One torrent that is being fetched, and the one file in it that is being played. */
    class Open(val key: String, private val handle: TorrentHandle, info: TorrentInfo, index: Int, private val path: File) {
        val size: Long = info.files().fileSize(index)
        private val offset: Long = info.files().fileOffset(index)
        private val pieceLength: Int = info.pieceLength()
        private val lastPiece: Int = ((offset + size - 1) / pieceLength).toInt()
        private var hurried = -1
        private var file: RandomAccessFile? = null

        /** Reads from [position] in the film, waiting for that part to arrive if it hasn't yet. */
        @Synchronized
        fun read(position: Long, buffer: ByteArray, at: Int, length: Int): Int {
            val place = offset + position
            val piece = (place / pieceLength).toInt()
            await(piece)
            val leftInPiece = (piece + 1L) * pieceLength - place
            val count = minOf(length.toLong(), leftInPiece, size - position).toInt()
            val input = file ?: RandomAccessFile(path, "r").also { file = it }
            input.seek(position)
            val read = input.read(buffer, at, count)
            if (read <= 0) throw IOException("The torrent's file is shorter than it said.")
            return read
        }

        private fun await(piece: Int) {
            if (handle.havePiece(piece)) return
            if (piece != hurried) {
                // This piece now, and the ones after it close behind, ahead of everything else.
                hurried = piece
                for (i in 0 until 12) if (piece + i <= lastPiece) handle.setPieceDeadline(piece + i, 500 + i * 300)
            }
            val giveUp = System.currentTimeMillis() + PIECE_WAIT_MS
            while (!handle.havePiece(piece)) {
                if (!handle.isValid) throw FileNotFoundException("The torrent was closed.")
                if (System.currentTimeMillis() > giveUp) throw FileNotFoundException("The torrent is arriving too slowly to play.")
                try { Thread.sleep(150) } catch (e: InterruptedException) { throw InterruptedIOException() }
            }
        }

        fun close(session: SessionManager?) {
            runCatching { file?.close() }
            file = null
            runCatching { if (handle.isValid) session?.remove(handle, SessionHandle.DELETE_FILES) }
        }
    }

    /**
     * Starts fetching the torrent [address] names (or gives back the one already open). Blocks while
     * the torrent is found. A torrent that can't be played says why with a FileNotFoundException, which
     * the player takes as final and moves on from, rather than trying the same one again.
     */
    @Synchronized
    fun open(context: Context, address: String): Open {
        current?.takeIf { it.key == address }?.let { return it }
        closeCurrent()
        val dir = folder(context)
        dir.deleteRecursively()
        dir.mkdirs()
        try {
            val sm = session ?: SessionManager().also {
                it.start(SessionParams(SettingsPack().uploadRateLimit(GIVING_LIMIT).connectionsLimit(120).activeDownloads(1)))
                session = it
            }
            val want = AddOns.readMagnet(address)
            val bytes = sm.fetchMagnet(want.magnet, FIND_SECONDS, dir) ?: throw FileNotFoundException("Nobody was found sharing this torrent.")
            val info = TorrentInfo(bytes)
            val files = info.files()
            val count = files.numFiles()
            val index = AddOns.pickFile(List(count) { files.filePath(it) }, List(count) { files.fileSize(it) }, want.file, want.season, want.episode)
                ?: throw FileNotFoundException("This torrent has no film in it.")
            if (files.fileSize(index) + ROOM_TO_SPARE > dir.usableSpace) throw FileNotFoundException("There isn't enough storage here for this torrent.")
            val priorities = Array(count) { if (it == index) Priority.DEFAULT else Priority.IGNORE }
            sm.download(info, dir, null, priorities, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
            var handle: TorrentHandle? = null
            for (i in 0 until 50) {
                handle = sm.find(info.infoHash())
                if (handle != null && handle.isValid) break
                Thread.sleep(100)
            }
            if (handle == null || !handle.isValid) throw FileNotFoundException("The torrent couldn't be started.")
            return Open(address, handle, info, index, File(dir, files.filePath(index))).also { current = it }
        } catch (e: IOException) {
            throw e
        } catch (e: InterruptedException) {
            throw InterruptedIOException()
        } catch (e: Throwable) {
            // The torrent engine is a separate piece of machinery; if it can't run on this device, say so and move on.
            throw FileNotFoundException("Torrents can't be played on this device (" + e.javaClass.simpleName + ").")
        }
    }

    private fun closeCurrent() {
        current?.close(session)
        current = null
    }

    /** The player has moved on to something that isn't a torrent: stop fetching and remove what was fetched. */
    @Synchronized
    fun stopCurrent() = closeCurrent()

    /** The player has closed: stop fetching, remove what was fetched, and shut the engine down. */
    @Synchronized
    fun close(context: Context) {
        closeCurrent()
        val sm = session
        session = null
        runCatching { sm?.stop() }
        runCatching { folder(context).deleteRecursively() }
    }
}

/** Lets the player read a film out of a torrent as if it were a file on the internet. */
@OptIn(UnstableApi::class)
class TorrentSource(private val context: Context) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var playing: Torrents.Open? = null
    private var position = 0L
    private var left = 0L
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val torrent = Torrents.open(context, dataSpec.uri.toString())
        playing = torrent
        position = dataSpec.position
        if (position > torrent.size) throw FileNotFoundException("Asked to read past the end of the film.")
        left = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else torrent.size - position
        started = true
        transferStarted(dataSpec)
        return left
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (left <= 0L) return C.RESULT_END_OF_INPUT
        val torrent = playing ?: throw IOException("Nothing is open.")
        val count = torrent.read(position, buffer, offset, minOf(length.toLong(), left).toInt())
        position += count
        left -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        playing = null
        if (started) {
            started = false
            transferEnded()
        }
    }
}
