package art.kquinn.kuesounds

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Songs saved on the phone, to play with no internet. Saving downloads each song file whole into
 * KueSounds' own space on the phone; the player then plays that copy instead of the Drive's. Saving
 * carries on with the screens closed. Each device keeps its own choice of saved songs.
 */
object Offline {
    private lateinit var folder: File

    /** The songs saved on this phone, by song id. */
    var saved by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Songs waiting to be saved. */
    private val waiting = LinkedHashSet<String>()

    var status by mutableStateOf("")
        private set

    fun init(context: Context) {
        folder = File(context.filesDir, "offline").apply { mkdirs() }
        folder.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        saved = folder.listFiles()?.map { it.nameWithoutExtension }?.toSet().orEmpty()
    }

    private fun fileName(t: Track) = t.id + "." + Library.ending(t.fileName).ifEmpty { "audio" }

    /** The saved copy of a song, if it is on the phone. */
    fun fileFor(t: Track): File? = if (t.id in saved) File(folder, fileName(t)).takeIf { it.exists() } else null

    fun isSaved(al: Album): Boolean = al.tracks.all { it.id in saved }

    fun isWaiting(al: Album): Boolean = synchronized(waiting) { al.tracks.any { it.id in waiting } }

    /** How much room the saved songs take, in bytes. */
    fun bytesUsed(): Long = folder.listFiles()?.sumOf { it.length() } ?: 0L

    /** Saves these songs on the phone. */
    fun save(context: Context, tracks: List<Track>) {
        synchronized(waiting) { tracks.filter { it.id !in saved }.forEach { waiting += it.id } }
        WorkService.start(context)
    }

    /** Takes these songs off the phone. They still play from the Drive. */
    fun remove(tracks: List<Track>) {
        synchronized(waiting) { tracks.forEach { waiting -= it.id } }
        for (t in tracks) File(folder, fileName(t)).delete()
        saved = saved - tracks.map { it.id }.toSet()
    }

    val hasWork: Boolean get() = synchronized(waiting) { waiting.isNotEmpty() }

    private fun next(): String? = synchronized(waiting) { waiting.firstOrNull()?.also { waiting.remove(it) } }

    /** Saves the waiting songs one by one. Called by [WorkService]. */
    suspend fun run(progress: (String) -> Unit) {
        var done = 0
        try {
            while (true) {
                val id = next() ?: break
                val t = Scanner.data.tracks.firstOrNull { it.id == id } ?: continue
                val left = synchronized(waiting) { waiting.size }
                status = "Saving to phone · " + t.title + if (left > 0) " · $left more" else ""
                progress(status)
                val target = File(folder, fileName(t))
                val part = File(folder, fileName(t) + ".part")
                try {
                    val token = Google.token() ?: throw Drive.SignedOut()
                    Drive.downloadWhole(t.id, part, token)
                    if (t.size > 0 && part.length() != t.size) throw java.io.IOException("the download was cut short")
                    target.delete()
                    if (!part.renameTo(target)) throw java.io.IOException("the saved song couldn't be kept")
                    saved = saved + t.id
                    done++
                } catch (e: Exception) {
                    part.delete()
                    Scanner.note("not saved to phone: " + t.fileName + ": " + e.javaClass.simpleName + " " + e.message)
                    if (e is Drive.SignedOut) synchronized(waiting) { waiting.clear() }
                }
            }
        } finally {
            status = ""
            if (done > 0) Scanner.note("saved $done songs to the phone")
        }
    }
}

/** Whether the phone can reach the internet right now. */
object Net {
    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
