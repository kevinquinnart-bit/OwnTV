package art.kquinn.kuesounds

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fixing the files: for each album the catalogue has matched, every song file is downloaded, the
 * pictures inside it are taken out, the catalogue's cover and the album's proper name, artist and year
 * are written in, the file is checked, and it goes back to the Drive as a new version of the same file.
 * Google keeps the old version for 30 days. A file that fails any step is left exactly as it was.
 */
object Fixer {
    /** Albums waiting to be fixed, by album key. */
    private val waiting = LinkedHashSet<String>()

    var status by mutableStateOf("")
        private set
    var running by mutableStateOf(false)
        private set
    var lastResult by mutableStateOf("")
        private set

    /** Why asking for the edit permission failed, shown under the button. */
    var permissionProblem by mutableStateOf("")

    /** Asks Google for the edit permission, then does what is given. Set by the main screen. */
    var askPermission: (then: () -> Unit) -> Unit = { it() }

    /** Asks once to show notices, so the fixing's progress shows in the pull-down. Set by the main screen. */
    var askNotices: () -> Unit = {}

    /** Fixes [albums], asking for the edit permission first when it hasn't been given. */
    fun fix(context: Context, albums: List<Album>) {
        askNotices()
        val app = context.applicationContext
        if (Google.canEdit) start(app, albums) else askPermission { start(app, albums) }
    }

    /** Whether an album can be fixed: the catalogue matched it and gave it a cover. */
    fun canFix(al: Album, d: LibraryData = Scanner.data): Boolean = al.key in d.releases && al.key in d.catalogueCovers &&
        al.tracks.any { Tagger.canFix(it.fileName) }

    fun isFixed(al: Album): Boolean = al.tracks.filter { Tagger.canFix(it.fileName) }.all { it.fixed }

    fun isWaiting(al: Album): Boolean = synchronized(waiting) { al.key in waiting }

    /** Adds albums to fix and starts the work, which carries on with the screens closed. */
    fun start(context: Context, albums: List<Album>) {
        synchronized(waiting) { albums.filter { canFix(it) && !isFixed(it) }.forEach { waiting += it.key } }
        WorkService.start(context)
    }

    val hasWork: Boolean get() = synchronized(waiting) { waiting.isNotEmpty() }

    private fun next(): String? = synchronized(waiting) { waiting.firstOrNull()?.also { waiting.remove(it) } }

    /** Works through the waiting albums. Called by [WorkService]. */
    suspend fun run(context: Context, progress: (String) -> Unit) {
        running = true
        var fixedCount = 0
        var failed = 0
        try {
            // A search of the Drive going on is let finish first, so the two don't change the library at once.
            while (Scanner.running) delay(2000)
            while (true) {
                val key = next() ?: break
                val al = Library.albums(Scanner.data.tracks).firstOrNull { it.key == key } ?: continue  // fresh, not the screens' copy
                val cover = Scanner.data.art[key]?.let { File(it) }?.takeIf { it.exists() && key in Scanner.data.catalogueCovers } ?: continue
                val songs = al.tracks.filter { Tagger.canFix(it.fileName) && !it.fixed }
                songs.forEachIndexed { i, t ->
                    status = "Fixing files · " + al.title + " · " + (i + 1) + " of " + songs.size
                    progress(status)
                    val ok = fixOne(context, t, al, cover)
                    if (ok) fixedCount++ else failed++
                }
            }
        } finally {
            status = ""
            running = false
            lastResult = "Files fixed: $fixedCount" + if (failed > 0) ". Not fixed: $failed (see the report)." else "."
            Scanner.note("fixing finished: $fixedCount fixed, $failed not fixed")
        }
    }

    private suspend fun fixOne(context: Context, t: Track, al: Album, cover: File): Boolean {
        val work = File(context.cacheDir, "fix").apply { mkdirs() }
        val file = File(work, t.id + "." + Library.ending(t.fileName))
        return try {
            val token = Google.editToken() ?: throw Drive.SignedOut()
            Drive.downloadWhole(t.id, file, token)
            if (t.size > 0 && file.length() != t.size) throw java.io.IOException("the download was cut short")
            val artist = t.albumArtist.ifBlank { al.artist }
            Tagger.fix(file, Tagger.Details(al.title, artist, al.year, cover))
            val (size, modified) = Drive.uploadNewVersion(t.id, file, t.mime, Google.editToken() ?: token)
            Scanner.changeTracks(setOf(t.id)) { it.copy(size = size, modified = modified.ifEmpty { it.modified }, albumArtist = artist, fixed = true) }
            true
        } catch (e: Drive.SignedOut) {
            Scanner.note("fixing stopped: Google needs the edit permission again")
            synchronized(waiting) { waiting.clear() }
            false
        } catch (e: Exception) {
            Scanner.note("not fixed: " + t.fileName + ": " + e.javaClass.simpleName + " " + e.message)
            false
        } finally {
            file.delete()
        }
    }
}

/**
 * Keeps the long jobs going with the screens closed (saving songs to the phone, fixing files), with a
 * notice showing how far they have got.
 */
class WorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WorkService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notice(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("work", "Saving and fixing music", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, "work")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("KueSounds")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, 7, notice("Working on your music"),
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!started) {
            started = true
            scope.launch {
                val show: (String) -> Unit = { text -> getSystemService(NotificationManager::class.java).notify(7, notice(text)) }
                do {
                    if (Offline.hasWork) Offline.run(show)
                    if (Fixer.hasWork) Fixer.run(this@WorkService, show)
                } while (Offline.hasWork || Fixer.hasWork)
                started = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
