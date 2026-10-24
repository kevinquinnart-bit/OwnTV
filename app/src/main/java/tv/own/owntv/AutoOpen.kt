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
import kotlinx.coroutines.launch

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
 *
 * Also the place that knows when the app has left the screen, which is when stale playlists and guides
 * are refreshed (see [refreshStaleWhileOffScreen]).
 */
object AutoOpen {
    private const val DELAY_MS = 1_500L
    private var startedActivities = 0

    /** Whether one of OwnTV's screens is showing right now. */
    val isOnScreen: Boolean get() = startedActivities > 0
    private var heldWhileOffScreen: Set<Long> = emptySet()
    private fun watch() = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.live.WatchSession>()
    private val main = Handler(Looper.getMainLooper())
    private val background = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** Called once from [OwnTVApp.onCreate]. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                // Back on screen: the channel resumes, so mark it as being watched again.
                if (startedActivities == 0) heldWhileOffScreen.forEach { watch().open(it) }
                heldWhileOffScreen = emptySet()
                startedActivities++
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                // Off screen: nothing is playing, so refresh anything past its refresh time now.
                if (startedActivities == 0 && !activity.isChangingConfigurations) {
                    // Playback is stopped while off screen (MainActivity.onStop), but the record of what
                    // is being watched is kept by the player screen and would hold every timed refresh
                    // back until the channel is closed. Release it while off screen.
                    heldWhileOffScreen = watch().watching.value
                    watch().closeAll()
                    background.launch { refreshStaleWhileOffScreen() }
                }
            }
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
