package art.kquinn.kuetv

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * KueTV as the TV's Home button. Fire OS won't let Amazon's home screen be switched off or replaced, so this
 * waits for it to come to the front and opens KueTV over it straight away, the way dumbtv's home screen does:
 *  - a plain black cover goes up at once, so Amazon's home screen isn't seen;
 *  - KueTV is opened by an alarm, which the TV starts at once (an ordinary start waits 5 seconds after Home);
 *  - the cover comes down as soon as KueTV is on screen, or after 3 seconds whatever happens.
 * It only works while KueTV is set as the home screen (switched on from a computer), and it reads nothing on screen.
 */
class HomeGuard : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var lastOpen = 0L

    override fun onServiceConnected() {
        running = this
        Report.note("Home", "Home button watcher on")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != AMAZON_HOME) return
        if (!Launcher.isHome(this)) return
        // Amazon's home screen fires several of these as it opens: one KueTV start is enough.
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpen < 1_500) return
        lastOpen = now
        showCover()
        openKueTv(this)
    }

    private fun showCover() {
        if (cover != null) return
        val view = View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params); cover = view }
            .onFailure { Report.note("Home", "cover: " + it.message) }
        main.postDelayed({ removeCover() }, 3_000)
    }

    fun removeCover() {
        main.post {
            val v = cover ?: return@post
            cover = null
            runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeCover()
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        const val AMAZON_HOME = "com.amazon.tv.launcher"

        /** The watcher while it is running, so KueTV can take the cover down once it is on screen. */
        @Volatile var running: HomeGuard? = null

        /** KueTV on screen: the cover can come down. */
        fun shown() { running?.removeCover() }

        /** Opens KueTV through an alarm set for now: the TV starts it at once rather than 5 seconds after Home. */
        fun openKueTv(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val pending = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val ok = runCatching { alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), pending) }.isSuccess
            if (!ok) runCatching { context.startActivity(intent) }
        }
    }
}

/** The TV's own channel list: the aerial's channels and the ones the TV apps (ITVX, Channel 4, 5, Pluto TV) add. */
object TvChannels {
    class Row(val id: Long, val input: String, val name: String, val number: String, val type: String)

    fun read(context: Context): List<Row> = runCatching {
        context.contentResolver.query(
            android.media.tv.TvContract.Channels.CONTENT_URI,
            arrayOf("_id", "input_id", "display_name", "display_number", "type"), null, null, null,
        )?.use { c ->
            val out = ArrayList<Row>()
            while (c.moveToNext()) out += Row(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty())
            out
        }.orEmpty()
    }.onFailure { Report.note("TV channels", "refused: " + it.javaClass.simpleName) }.getOrDefault(emptyList())

    /** Writes the list to KueTV's folder on the TV, for reading from a computer. */
    fun writeProbe(context: Context) {
        val rows = read(context)
        val text = buildString {
            append("channels the TV lets KueTV see: ").append(rows.size).append('\n')
            rows.forEach { r -> append(r.id).append('\t').append(r.number).append('\t').append(r.name).append('\t').append(r.type).append('\t').append(r.input).append('\n') }
        }
        runCatching { java.io.File(context.getExternalFilesDir(null), "tv-channels.txt").writeText(text) }
    }
}
