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
}
