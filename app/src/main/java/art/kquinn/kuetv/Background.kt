package art.kquinn.kuetv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The small notice Android asks for while the app does work with its screens closed. */
object Notices {
    private const val CHANNEL = "work"

    fun build(context: Context, text: String): Notification {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "KueTV at work", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(context, CHANNEL)
            .setContentTitle("KueTV").setContentText(text).setSmallIcon(R.drawable.kuetv_mark).setOngoing(true).build()
    }

    /** Tells Android this service is doing something the user asked for, so it is left running. */
    fun hold(service: Service, id: Int, text: String) {
        val notice = build(service, text)
        if (Build.VERSION.SDK_INT >= 29) service.startForeground(id, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else service.startForeground(id, notice)
    }
}

/**
 * Keeps the app alive while recordings are copied to Google Drive, so the copying carries on after
 * KueTV's screens are closed. The copying itself is done by [Recordings]; this only holds the door.
 */
class CopyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { Notices.hold(this, 41, "Copying recordings to Google Drive") }.onFailure { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    companion object {
        /** Asked for when copying starts. Android only allows it while KueTV is on screen; if it says no, copying still runs while the app is open. */
        fun hold(context: Context) {
            runCatching { context.startForegroundService(Intent(context, CopyService::class.java)) }
        }

        /** A few seconds' grace, so a hold that has only just been asked for is in place before it is let go. */
        fun release(context: Context) {
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { context.stopService(Intent(context, CopyService::class.java)) } }, 6_000)
        }
    }
}

/** Whether KueTV's screens are showing. Anything that plays stops when they are not. */
object Screen {
    var visible by mutableStateOf(true)
}

/** The live channel last watched, in its lightest version, for the small TV shown while browsing. */
object LastLive {
    var item by androidx.compose.runtime.mutableStateOf<Playable?>(null)
}

/** The few choices the app keeps. */
object Choices {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("choices", Context.MODE_PRIVATE)
    var changes by mutableIntStateOf(0)
        private set

    /** The categories the user has chosen not to see, for "live", "film" or "show". */
    fun hidden(context: Context, kind: String): Set<String> = prefs(context).getStringSet("hide.$kind", emptySet()).orEmpty().toSet()
    fun hidden(context: Context, kind: String, ids: Set<String>) {
        prefs(context).edit().putStringSet("hide.$kind", ids).apply()
        changes++
    }

    /** How much bigger than standard the lettering is drawn: 0 standard, 1 large, 2 larger. */
    fun textSize(context: Context): Int = prefs(context).getInt("textSize", 0).coerceIn(0, 2)
    fun textSize(context: Context, size: Int) {
        prefs(context).edit().putInt("textSize", size.coerceIn(0, 2)).apply()
        changes++
    }
    val textSizeNames = listOf("standard", "large", "larger")
    val textSizeScales = listOf(1f, 1.2f, 1.4f)

    fun subtitles(context: Context): Boolean = prefs(context).getBoolean("subtitles", true)
    fun subtitles(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("subtitles", on).apply()
        changes++
    }

    fun smallTv(context: Context): Boolean = prefs(context).getBoolean("smallTv", true)
    fun smallTv(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("smallTv", on).apply()
        changes++
    }
}
