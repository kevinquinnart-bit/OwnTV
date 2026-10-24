package tv.own.owntv.features.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.update.UpdateManager

/**
 * Kevin's copy: a written record of what the automatic update last did, so a TV that "doesn't update"
 * says at which step it stopped. Shown on More › About. One entry, overwritten by the next step.
 *
 * It also listens to the same installer broadcast as core's updater. When the system's Install screen
 * is ready but OwnTV is not on screen, Android does not let an app open a screen from the background,
 * so that screen never appears. The request is kept here and opened the next time OwnTV is on screen.
 */
object UpdateRecord {
    private const val PREFS = "kevin_update_record"
    private const val INSTALL_STATUS_ACTION = "tv.own.owntv.UPDATE_INSTALL_STATUS"

    enum class Step { UP_TO_DATE, FOUND, HANDED_TO_INSTALLER, WAITING_FOR_SCREEN, CANCELLED, FAILED }

    data class Entry(val at: Long, val step: Step, val version: String?, val failure: UpdateManager.Failure?)

    /** The installer's own "ask the user" screen, kept while OwnTV was not on screen to show it. */
    @Volatile private var pendingConfirm: Intent? = null

    /** Whether one of OwnTV's screens is showing — kept by [tv.own.owntv.AutoOpen]'s activity counter. */
    var isOnScreen: () -> Boolean = { true }

    fun note(context: Context, step: Step, version: String?, failure: UpdateManager.Failure? = null) {
        val (code, number, message) = encode(failure)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis())
            .putString("step", step.name)
            .putString("version", version)
            .putString("failure", code)
            .putLong("failureNumber", number)
            .putString("failureMessage", message)
            .apply()
    }

    fun read(context: Context): Entry? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val step = p.getString("step", null)?.let { s -> Step.entries.firstOrNull { it.name == s } } ?: return null
        val failure = decode(p.getString("failure", null), p.getLong("failureNumber", 0L), p.getString("failureMessage", null))
        return Entry(p.getLong("at", 0L), step, p.getString("version", null), failure)
    }

    /** Called once from the Application: hears the installer's answers alongside core's updater. */
    fun install(context: Context) {
        val app = context.applicationContext
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val version = read(app)?.version
                    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> if (!isOnScreen()) {
                            pendingConfirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                            note(app, Step.WAITING_FOR_SCREEN, version)
                        }
                        PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            pendingConfirm = null
                            note(app, Step.CANCELLED, version)
                        }
                        PackageInstaller.STATUS_SUCCESS -> pendingConfirm = null
                        else -> {
                            pendingConfirm = null
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.takeIf { it.isNotBlank() }
                            note(app, Step.FAILED, version, UpdateManager.Failure.InstallRejected(message))
                        }
                    }
                }
            },
            IntentFilter(INSTALL_STATUS_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** OwnTV is on screen again: open the Install screen that could not open while it was away. */
    fun showPendingInstall(context: Context) {
        val confirm = pendingConfirm ?: return
        pendingConfirm = null
        runCatching { context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { note(context, Step.HANDED_TO_INSTALLER, read(context)?.version) }
    }

    private fun encode(f: UpdateManager.Failure?): Triple<String?, Long, String?> = when (f) {
        null -> Triple(null, 0L, null)
        is UpdateManager.Failure.CheckHttp -> Triple("CheckHttp", f.code.toLong(), null)
        UpdateManager.Failure.NoCompatibleApk -> Triple("NoCompatibleApk", 0L, null)
        UpdateManager.Failure.InvalidReleaseResponse -> Triple("InvalidReleaseResponse", 0L, null)
        UpdateManager.Failure.CheckNetwork -> Triple("CheckNetwork", 0L, null)
        is UpdateManager.Failure.DownloadHttp -> Triple("DownloadHttp", f.code.toLong(), null)
        UpdateManager.Failure.EmptyDownload -> Triple("EmptyDownload", 0L, null)
        UpdateManager.Failure.DownloadNetwork -> Triple("DownloadNetwork", 0L, null)
        UpdateManager.Failure.Install -> Triple("Install", 0L, null)
        is UpdateManager.Failure.NotEnoughSpace -> Triple("NotEnoughSpace", f.requiredBytes, null)
        UpdateManager.Failure.DamagedDownload -> Triple("DamagedDownload", 0L, null)
        is UpdateManager.Failure.InstallRejected -> Triple("InstallRejected", 0L, f.message)
    }

    private fun decode(code: String?, number: Long, message: String?): UpdateManager.Failure? = when (code) {
        "CheckHttp" -> UpdateManager.Failure.CheckHttp(number.toInt())
        "NoCompatibleApk" -> UpdateManager.Failure.NoCompatibleApk
        "InvalidReleaseResponse" -> UpdateManager.Failure.InvalidReleaseResponse
        "CheckNetwork" -> UpdateManager.Failure.CheckNetwork
        "DownloadHttp" -> UpdateManager.Failure.DownloadHttp(number.toInt())
        "EmptyDownload" -> UpdateManager.Failure.EmptyDownload
        "DownloadNetwork" -> UpdateManager.Failure.DownloadNetwork
        "Install" -> UpdateManager.Failure.Install
        "NotEnoughSpace" -> UpdateManager.Failure.NotEnoughSpace(number)
        "DamagedDownload" -> UpdateManager.Failure.DamagedDownload
        "InstallRejected" -> UpdateManager.Failure.InstallRejected(message)
        else -> null
    }
}

/** The record in plain words, for More › About. A version handed to the installer that is now running
 *  reads as installed; one that is still newer than what runs reads as not installed. */
@Composable
internal fun updateRecordText(entry: UpdateRecord.Entry): String {
    val v = entry.version.orEmpty()
    val running = BuildConfig.VERSION_NAME
    return when (entry.step) {
        UpdateRecord.Step.UP_TO_DATE -> stringResource(R.string.kevin_update_up_to_date, running)
        UpdateRecord.Step.FOUND -> stringResource(R.string.kevin_update_found, v)
        UpdateRecord.Step.HANDED_TO_INSTALLER, UpdateRecord.Step.WAITING_FOR_SCREEN ->
            if (v == running) stringResource(R.string.kevin_update_installed, v)
            else if (entry.step == UpdateRecord.Step.WAITING_FOR_SCREEN) stringResource(R.string.kevin_update_waiting, v)
            else stringResource(R.string.kevin_update_not_installed, v)
        UpdateRecord.Step.CANCELLED -> stringResource(R.string.kevin_update_cancelled, v)
        UpdateRecord.Step.FAILED -> stringResource(
            R.string.kevin_update_failed,
            entry.failure?.let { updateFailureText(it) } ?: stringResource(R.string.update_install_failed),
        )
    }
}
