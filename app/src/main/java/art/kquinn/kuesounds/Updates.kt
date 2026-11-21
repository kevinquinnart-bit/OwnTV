package art.kquinn.kuesounds

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checking for a newer KueSounds and putting it on.
 *
 * Every build publishes a small file, version.txt, beside the app on the same download. It holds the
 * build's number and its version, such as "code=33" and "name=0.1.33". The app reads it each time it
 * comes to the front. A higher number than its own means a newer app is waiting. Updating downloads
 * the app into KueSounds' own space and hands it to the device's installer, which asks before it installs.
 */
object Updates {
    private const val BASE = "https://github.com/kevinquinnart-bit/OwnTV/releases/download/kuesounds/"
    const val VERSION_URL = BASE + "version.txt"
    const val APP_URL = BASE + "KueSounds.apk"

    /** A version on the download: its build number (always going up) and the name people see. */
    data class Latest(val code: Int, val name: String)

    /** Reads version.txt. Null when it doesn't hold a build number. */
    fun parse(text: String): Latest? {
        val pairs = text.lines().mapNotNull { line ->
            val at = line.indexOf('=')
            if (at <= 0) null else line.substring(0, at).trim() to line.substring(at + 1).trim()
        }.toMap()
        val code = pairs["code"]?.toIntOrNull() ?: return null
        return Latest(code, pairs["name"].orEmpty().ifBlank { code.toString() })
    }

    /** Whether [latest] is newer than the build [mine] that is running. */
    fun isNewer(latest: Latest, mine: Int): Boolean = latest.code > mine

    /** A newer version found on opening, still waiting to be put on or put off. */
    var offer by mutableStateOf<Latest?>(null)

    /** The version put off with "Not now"; it isn't offered again until the app is next started from cold. */
    var putOff: Int = 0

    private val http by lazy { Drive.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    /** What is on the download now. Call off the main thread; throws when it can't be reached. */
    fun check(): Latest {
        val request = Request.Builder().url(VERSION_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("the download answered " + response.code)
            return parse(response.body.string()) ?: throw java.io.IOException("the version file had no build number")
        }
    }

    /** On opening: offer a newer version, unless that one was put off. Quiet when nothing can be reached. */
    fun checkOnOpen() {
        val latest = runCatching { check() }.getOrElse { return }
        if (isNewer(latest, BuildConfig.VERSION_CODE) && latest.code != putOff) offer = latest
    }

    private fun folder(context: Context) = File(context.cacheDir, "update").apply { mkdirs() }

    /** Downloads the newest app, telling [progress] how far it has got, 0 to 100. Call off the main thread. */
    fun download(context: Context, progress: (Int) -> Unit): File {
        val target = File(folder(context), "KueSounds.apk")
        val part = File(folder(context), "KueSounds.apk.part")
        val big = Drive.http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(APP_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
        big.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("the download answered " + response.code)
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val now = (done * 100 / total).toInt()
                            if (now != shown) { shown = now; progress(now) }
                        }
                    }
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw java.io.IOException("the downloaded app couldn't be kept")
        return target
    }

    /**
     * The build number inside a downloaded app, read the way the installer reads it. Null when the file
     * isn't a whole, readable app (cut short, or something other than an app).
     */
    fun buildInside(context: Context, file: File): Long? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrNull()

    /**
     * Downloads the newest app and checks it before the installer sees it: it must be a whole app with a
     * newer build number than the one running. If not, it downloads once more. Throws when neither try
     * gives a newer app.
     */
    fun downloadChecked(context: Context, progress: (Int) -> Unit): File {
        repeat(2) {
            val file = download(context, progress)
            val inside = buildInside(context, file)
            if (inside != null && inside > BuildConfig.VERSION_CODE) return file
            file.delete()
        }
        throw java.io.IOException("the download wasn't a newer, whole app, twice")
    }

    /** Whether the device lets KueSounds put apps on. Fire TV and phones ask for this once, in their own settings. */
    fun mayInstall(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the device's own page for letting KueSounds put apps on. False when the device has no such page. */
    fun openInstallPermission(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** Hands the downloaded app to the device's installer, which asks before it installs over this one. */
    fun install(context: Context, file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Clears a downloaded app once the newer version is the one running. */
    fun tidy(context: Context) {
        runCatching { File(context.cacheDir, "update").deleteRecursively() }
    }

    // ---------------------------------------------------------------------------------------------
    // Updating by itself
    //
    // Android lets an app put its own updates on without asking only when the app itself put the
    // version that's running on (it is then the app's "installer"), on Android 12 and later. The very
    // first time, KueSounds was put on by the browser, so that one update asks once. From then on each
    // new version is downloaded quietly and goes on the next time KueSounds is left (Home or another
    // app), never while music is playing.

    /** A newer version downloaded and checked, waiting to go on when KueSounds is next left. */
    var ready by mutableStateOf<File?>(null)

    /** What the update is doing, for the screen. Empty when nothing is happening. */
    var doing by mutableStateOf("")

    /** Whether updates can now go on without asking. */
    fun quiet(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !mayInstall(context)) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer == context.packageName
    }

    /**
     * On opening: when a newer version is waiting and updates go on by themselves, downloads it
     * quietly, ready for when KueSounds is left. Otherwise offers it on screen, as before. Call off
     * the main thread.
     */
    fun checkAndFetch(context: Context) {
        val latest = runCatching { check() }.getOrNull() ?: return
        if (!isNewer(latest, BuildConfig.VERSION_CODE)) {
            tidy(context)
            return
        }
        if (!quiet(context)) {
            if (latest.code != putOff) offer = latest
            return
        }
        if (ready != null) return
        doing = "Getting KueSounds " + latest.name
        ready = runCatching { downloadChecked(context) {} }.getOrNull()
        doing = ""
    }

    /**
     * Settings › Check for updates: looks for a newer version and, when there is one, downloads it (or uses
     * the one already downloaded) and puts it on now. [say] is told how it is going. Gives back the last word.
     */
    suspend fun updateByHand(context: Context, say: (String) -> Unit): String {
        say("Checking…")
        val latest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { check() }.getOrNull() }
            ?: return "Couldn't reach the download. Check the internet and try again."
        if (!isNewer(latest, BuildConfig.VERSION_CODE)) return "You have the newest version."
        if (!mayInstall(context)) {
            openInstallPermission(context)
            return "Version " + latest.name + " is ready. First allow KueSounds to install updates on the page that opened, then tap Check for updates again."
        }
        say("Downloading version " + latest.name + " · 0%")
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ready?.takeIf { it.exists() && (buildInside(context, it) ?: 0) >= latest.code }
                ?: runCatching { downloadChecked(context) { p -> say("Downloading version " + latest.name + " · $p%") } }.getOrNull()
        } ?: return "The download didn't work. Try again later."
        ready = null
        say("Putting version " + latest.name + " on…")
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { installSession(context, file) }.isSuccess }
        if (!ok) install(context, file)
        return "Putting version " + latest.name + " on. Android may ask first. KueSounds closes while it goes on: open it again after."
    }

    /** KueSounds has been left: a downloaded update goes on now, unless music is playing. */
    fun installIfReady(context: Context) {
        val file = ready ?: return
        if (Playing.isPlaying || !quiet(context)) return
        ready = null
        runCatching { installSession(context, file) }
    }

    /**
     * Puts the downloaded app on through Android's own installer. It goes on without a question when
     * Android allows that; otherwise Android shows its usual "Update this app?" question.
     */
    fun installSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("KueSounds.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val done = android.app.PendingIntent.getBroadcast(context, id, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(done.intentSender)
        }
    }
}

/** Hears back from Android's installer: shows its question when it has one, and tidies up afterwards. */
class UpdateReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)
        if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val ask = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            runCatching { context.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else if (status != android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            // Tried again the next time KueSounds opens.
            Scanner.note("update didn't go on: " + intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
