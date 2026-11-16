package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The recordings kept on this TV or phone, and the copying of them to Google Drive.
 *
 * A finished recording waits here until Google Drive is signed in. It is then copied to Drive and,
 * once it is safely there, removed from here. Copying only runs while nothing is playing, so it never
 * competes with the picture for the internet connection, and it carries on from where it stopped.
 */
object Recordings {
    /** Goes up whenever the recordings here or in Drive may have changed, so the screen looks again. */
    var changes by mutableIntStateOf(0)
        private set

    /** The file being copied to Drive now, and how far it has got, 0 to 100; null when none is. */
    var copying by mutableStateOf<Pair<String, Int>?>(null)
        private set

    /** Why copying to Drive last stopped; null when it didn't fail. */
    var problem by mutableStateOf<String?>(null)
        private set

    /** True while the player is open. Copying waits. */
    @Volatile var playing = false

    /** The file being recorded right now; it is not copied until it is finished. */
    @Volatile var recordingNow: File? = null

    /** Files that programmes set to record are being written to right now. */
    val timedNow: MutableSet<File> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    @Volatile private var working = false

    fun folder(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings").also { it.mkdirs() }

    /** The recordings on this TV or phone, newest first. */
    fun here(context: Context): List<File> =
        folder(context).listFiles { f -> f.isFile && f.name.endsWith(".ts") }.orEmpty().sortedByDescending { it.lastModified() }

    /** A new file for a recording of [channel], named so it reads well in Drive. */
    fun newFile(context: Context, channel: String): File {
        val name = channel.replace(Regex("[^\\p{L}\\p{N} +&'.-]+"), " ").replace(Regex("\\s+"), " ").trim().take(60).ifEmpty { "Recording" }
        val at = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        return File(folder(context), "$name $at.ts")
    }

    fun drive(context: Context): Drive {
        val prefs = context.applicationContext.getSharedPreferences("drive", Context.MODE_PRIVATE)
        val keep = object : Keep {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String?) {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
        return Drive(keep, BuildConfig.DRIVE_CLIENT_ID, BuildConfig.DRIVE_CLIENT_SECRET)
    }

    fun changed() { changes++ }

    /** Starts copying whatever is waiting, unless it is already being copied or Drive isn't signed in. */
    @Synchronized
    fun wake(context: Context) {
        val app = context.applicationContext
        val drive = drive(app)
        if (working || !drive.isSetUp || !drive.signedIn) return
        working = true
        Thread({
            var held = false
            try {
                problem = null
                while (true) {
                    val waiting = here(app).filter { it != recordingNow && it !in timedNow }
                    // A file with nothing in it is no recording.
                    waiting.filter { it.length() == 0L }.forEach { it.delete() }
                    // Oldest first, so they reach Drive in the order they were made.
                    val next = waiting.lastOrNull { it.length() > 0 } ?: break
                    // There is something to copy: ask Android to keep the app going if its screens are closed.
                    if (!held) { held = true; CopyService.hold(app) }
                    if (playing) { Thread.sleep(5_000); continue }
                    val total = next.length().coerceAtLeast(1)
                    val done = drive.upload(next, paused = { playing }) { sent -> copying = next.name to (sent * 100 / total).toInt() }
                    copying = null
                    if (done) {
                        next.delete()
                        changes++
                    }
                }
            } catch (e: DriveProblem) {
                problem = e.message
            } catch (e: Exception) {
                problem = "Copying to Google Drive stopped. It will carry on next time."
            } finally {
                if (held) CopyService.release(app)
                copying = null
                working = false
                changes++
            }
        }, "kuetv-drive").start()
    }
}
