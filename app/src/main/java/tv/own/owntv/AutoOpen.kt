package tv.own.owntv

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Kevin's copy: bring OwnTV to the screen on its own.
 *
 * - When the TV starts up ([Intent.ACTION_BOOT_COMPLETED]).
 * - When the TV wakes from standby ([Intent.ACTION_SCREEN_ON]), while the app is still in memory.
 * - Straight after the app has been updated ([Intent.ACTION_MY_PACKAGE_REPLACED]), so an in-app update
 *   ends back in the app instead of on the home screen.
 *
 * Android only lets an app open itself from the background when it holds "display over other apps"
 * (SYSTEM_ALERT_WINDOW). A Fire TV has no menu to grant that; it is granted once over Wi-Fi from a phone.
 * Without it every path here does nothing, so the app behaves exactly as before.
 */
object AutoOpen {
    private const val DELAY_MS = 1_500L
    private var startedActivities = 0
    private val main = Handler(Looper.getMainLooper())

    /** Called once from [OwnTVApp.onCreate]. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) { startedActivities = (startedActivities - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // SCREEN_ON is only ever delivered to a receiver registered at runtime.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                app,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (startedActivities == 0) open(context)
                    }
                },
                IntentFilter(Intent.ACTION_SCREEN_ON),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    /** Opens the app after a short pause, so the TV's own home screen has finished coming up first. */
    fun open(context: Context) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return
        main.postDelayed({
            runCatching {
                val pm = appContext.packageManager
                val launch = pm.getLeanbackLaunchIntentForPackage(appContext.packageName)
                    ?: pm.getLaunchIntentForPackage(appContext.packageName)
                    ?: return@runCatching
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                appContext.startActivity(launch)
            }
        }, DELAY_MS)
    }
}

/** Start-up and just-updated: both arrive here from the manifest. */
class AutoOpenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AutoOpen.open(context)
        }
    }
}
