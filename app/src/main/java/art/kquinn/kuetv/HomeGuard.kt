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
 *  - a plain black cover, made ready when the watcher starts, goes up at once so Amazon's home screen isn't seen;
 *  - KueTV is opened by an alarm, which the TV starts at once (an ordinary start waits 5 seconds after Home);
 *  - Fire OS can leave its home screen on top after a quick burst of starts, so it tries again each second
 *    until KueTV is in front (for up to 8 seconds), and stops as soon as any other app comes to the front;
 *  - the cover comes down as soon as KueTV is on screen, or after 1.5 seconds whatever happens.
 * It only works while it is switched on in the TV's accessibility settings (done from a computer), and it reads
 * nothing on screen: it only hears which app has come to the front.
 */
class HomeGuard : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var onAmazonHome = false
    private var lastLaunch = 0L
    private var firstAsk = 0L
    private val retry = Runnable { retryHome() }
    private val uncover = Runnable { cover?.visibility = View.GONE }

    override fun onServiceConnected() {
        running = this
        val view = View(this).apply { setBackgroundColor(android.graphics.Color.BLACK); visibility = View.GONE }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { windowAnimations = 0 }
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params); cover = view }
            .onFailure { Report.note("Home", "cover: " + it.message) }
        Report.note("Home", "Home button watcher on")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        android.util.Log.i("KueHome", "front: $pkg / $cls (KueTV in front: $inFront)")
        when {
            pkg == AMAZON_HOME -> {
                onAmazonHome = cls == AMAZON_HOME_SCREEN
                if (!onAmazonHome || !Launcher.isHome(this)) { stop(); return }
                firstAsk = SystemClock.uptimeMillis()
                retryHome()
            }
            pkg == "com.android.systemui" || pkg == "android" -> Unit
            // KueTV's own windows: only KueTV itself being in front counts as arrived.
            pkg == packageName -> if (inFront) { onAmazonHome = false; stop() }
            // Any other app in front: the user went there, so nothing may pull them back.
            else -> { onAmazonHome = false; stop(); cancelAlarm(this) }
        }
    }

    private fun retryHome() {
        main.removeCallbacks(retry)
        val now = SystemClock.uptimeMillis()
        if (running !== this || !onAmazonHome || inFront || now - firstAsk > 8_000) return
        if (now - lastLaunch >= 1_000) {
            lastLaunch = now
            android.util.Log.i("KueHome", "opening KueTV")
            showCover()
            openKueTv(this)
        }
        main.postAtTime(retry, lastLaunch + 1_000)
    }

    private fun stop() { main.removeCallbacks(retry) }

    private fun showCover() {
        val v = cover ?: return
        v.visibility = View.VISIBLE
        main.removeCallbacks(uncover)
        main.postDelayed(uncover, 1_500)
    }

    fun removeCover() { main.post { main.removeCallbacks(uncover); cover?.visibility = View.GONE } }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stop()
        main.removeCallbacks(uncover)
        cover?.let { v -> runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } }
        cover = null
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        const val AMAZON_HOME = "com.amazon.tv.launcher"
        private const val AMAZON_HOME_SCREEN = "com.amazon.tv.launcher.ui.HomeActivity_vNext"

        /** The watcher while it is running, so KueTV can take the cover down once it is on screen. */
        @Volatile var running: HomeGuard? = null

        /** True while KueTV's screen is in front. */
        @Volatile var inFront = false

        /** KueTV on screen: the cover can come down. */
        fun shown() { inFront = true; running?.removeCover() }

        private fun launch(context: Context): PendingIntent {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val noAnimation = android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            return PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, noAnimation)
        }

        /** Opens KueTV through an alarm set for now: the TV starts it at once rather than 5 seconds after Home. */
        fun openKueTv(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            runCatching { alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), launch(context)) }
                .onFailure { Report.note("Home", "alarm: " + it.message) }
        }

        /** Drops a waiting start, so it can't pull the user back after they opened another app. */
        fun cancelAlarm(context: Context) {
            runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(launch(context)) }
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
