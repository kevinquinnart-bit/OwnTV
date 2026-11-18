package art.kquinn.kuetv

import android.app.Application
import android.os.Build
import de.blinkt.openvpn.core.ICSOpenVPNApplication
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KueTV's app as a whole. It is the VPN engine's own start-up (which sets up its notices, and the link
 * from the VPN, running in a process of its own, to KueTV's screens), plus one thing of KueTV's: if any
 * part of the app stops with an error, in either process, what went wrong is written into the Report
 * before Android closes it, so it can be sent and the cause found.
 */
class KueApp : ICSOpenVPNApplication() {
    override fun onCreate() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
        super.onCreate()
        KueDns.load(this)
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            .lines().filter { it.isNotBlank() }.take(40)
        val process = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "?"
        val stamp = SimpleDateFormat("d MMM HH:mm:ss", Locale.UK).format(Date())
        // Straight to the file: in the VPN's own process the Report has not been started.
        val lines = listOf("$stamp  Crash: KueTV stopped (process $process, thread ${thread.name})") +
            trace.map { "$stamp  Crash: " + Report.clean(it) }
        File(filesDir, "report.txt").appendText(lines.joinToString("") { it + "\n" })
    }
}
