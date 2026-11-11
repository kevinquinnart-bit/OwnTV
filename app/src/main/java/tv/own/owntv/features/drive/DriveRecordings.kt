package tv.own.owntv.features.drive

import android.content.Context
import android.util.Log
import androidx.room.useReaderConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.RecordingDao
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.storage.MediaTarget
import java.io.InputStream

/**
 * Kevin's copy: copies each finished recording to Google Drive › OwnTV › Recordings.
 *
 * Runs whenever a recording stops, and once shortly after the app starts (to catch anything that
 * finished while the app was closed, or an upload cut short). The copy on this box is kept, so a
 * recording still plays exactly as before. When Drive has no room for the next one, the oldest
 * recordings in that Drive folder are deleted until it fits — only files this app put there.
 */
class DriveRecordings(
    context: Context,
    private val drive: GoogleDrive,
    private val db: OwnTVDatabase,
    private val recordingDao: RecordingDao,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** What the last copy did, for the Google Drive popup. */
    sealed interface Last {
        data object None : Last
        data class Copying(val title: String, val percent: Int) : Last
        data class Copied(val title: String, val removedOld: Int) : Last
        data class Failed(val title: String, val full: Boolean, val signedOut: Boolean) : Last
    }

    private val _last = MutableStateFlow<Last>(Last.None)
    val last: StateFlow<Last> = _last.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (on) scope.launch { sweep() }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            delay(STARTUP_DELAY_MS)
            sweep()
        }
        // A recording that stops leaves the running list: that is the moment to copy it.
        scope.launch {
            recordingDao.observeRunning().map { rows -> rows.map { it.id }.toSet() }.distinctUntilChanged()
                .collect { sweep() }
        }
    }

    private fun uploaded(): Set<Long> =
        prefs.getStringSet(KEY_DONE, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun markUploaded(id: Long) {
        val next = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty().toMutableSet().apply { add(id.toString()) }
        prefs.edit().putStringSet(KEY_DONE, next).apply()
    }

    private suspend fun completedIds(): List<Long> = db.useReaderConnection { c ->
        c.usePrepared(
            "SELECT id FROM recordings WHERE status = 'COMPLETED' AND filePath IS NOT NULL AND bytes > 0 ORDER BY endedAt ASC",
        ) { st -> buildList { while (st.step()) add(st.getLong(0)) } }
    }

    suspend fun sweep() {
        if (!_enabled.value || !drive.configured || !drive.signedIn) return
        if (!lock.tryLock()) return
        try {
            val done = uploaded()
            for (id in runCatching { completedIds() }.getOrDefault(emptyList())) {
                if (id in done) continue
                val row = recordingDao.getById(id) ?: continue
                if (!copy(row)) break
            }
        } finally {
            lock.unlock()
        }
    }

    /** True when copied (or nothing to copy); false stops this round — the reason is in [last]. */
    private suspend fun copy(row: RecordingEntity): Boolean {
        val target = MediaTarget.of(appContext, row.filePath) ?: return true
        val size = target.length()
        if (size <= 0L) return true
        _last.value = Last.Copying(row.title, 0)
        return runCatching {
            val removed = makeRoom(size)
            val name = driveName(row)
            drive.upload(
                total = size,
                open = { from -> target.openInput().also { it.skipFully(from) } },
                folder = GoogleDrive.Folder.RECORDINGS,
                name = name,
                mime = mimeOf(row.filePath.orEmpty()),
                onProgress = { sent -> _last.value = Last.Copying(row.title, ((sent * 100) / size).toInt()) },
            )
            markUploaded(row.id)
            _last.value = Last.Copied(row.title, removed)
            true
        }.getOrElse { e ->
            Log.w(TAG, "copy to Drive failed id=${row.id}: ${e.message}")
            val kind = (e as? GoogleDrive.DriveException)?.kind
            _last.value = Last.Failed(
                row.title,
                full = kind == GoogleDrive.DriveException.Kind.FULL || e is NoRoom,
                signedOut = kind == GoogleDrive.DriveException.Kind.SIGNED_OUT,
            )
            false
        }
    }

    private class NoRoom : java.io.IOException("no room on Drive")

    /** Deletes this app's oldest Drive recordings until [needed] bytes fit. Returns how many went. */
    private suspend fun makeRoom(needed: Long): Int {
        val space = drive.space()
        var free = space.freeBytes ?: return 0
        val want = needed + HEADROOM_BYTES
        if (free >= want) return 0
        var removed = 0
        for (old in drive.list(GoogleDrive.Folder.RECORDINGS)) {
            drive.delete(old.id)
            free += old.size
            removed++
            if (free >= want) return removed
        }
        throw NoRoom()
    }

    private fun driveName(row: RecordingEntity): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HHmm", java.util.Locale.ROOT).format(java.util.Date(row.startMs))
        val ext = row.filePath.orEmpty().substringAfterLast('.', "").takeIf { it.length in 2..4 }?.let { ".$it" } ?: ".ts"
        val clean = "${row.title} - ${row.channelName}".replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        return "$stamp $clean$ext"
    }

    private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        else -> "video/mp2t"
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) {
                if (read() < 0) return
                left--
            } else left -= s
        }
    }

    companion object {
        private const val TAG = "KevinDriveRec"
        private const val PREFS = "kevin_drive_recordings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DONE = "uploaded"
        private const val STARTUP_DELAY_MS = 60_000L

        /** Kept free on Drive beyond the recording itself. */
        private const val HEADROOM_BYTES = 50L * 1024 * 1024
    }
}
