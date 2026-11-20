package art.kquinn.kuesounds

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Signing in to the Google account the music is in, with the phone's own Google sign-in.
 *
 * KueSounds normally asks only to READ that Drive. Fixing the files (putting the right cover and album
 * details into them) needs permission to EDIT it, asked for separately, only when the person first
 * presses "Fix the files". Each permission lasts: every hour Google hands over a fresh pass without asking.
 */
object Google {
    const val DRIVE_READ = "https://www.googleapis.com/auth/drive.readonly"
    const val DRIVE_EDIT = "https://www.googleapis.com/auth/drive"

    /** One permission and the pass held for it. */
    private class Pass(val scope: String, val flag: String) {
        val lock = Mutex()
        var token: String? = null
        var at = 0L
    }

    private val read = Pass(DRIVE_READ, "signedIn")
    private val edit = Pass(DRIVE_EDIT, "canEdit")

    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun prefs() = app.getSharedPreferences("google", Context.MODE_PRIVATE)

    /** The music account's address once signed in; empty before. */
    val email: String get() = prefs().getString("email", "").orEmpty()

    val signedIn: Boolean get() = prefs().getBoolean(read.flag, false)

    /** Whether the person has allowed KueSounds to edit the music files. */
    val canEdit: Boolean get() = prefs().getBoolean(edit.flag, false)

    private fun request(pass: Pass): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(pass.scope)))
            .apply { if (email.isNotBlank()) setAccount(Account(email, "com.google")) }
            .build()

    /** What starting a sign-in gave: a pass straight away, or Google's own screen to show. */
    sealed class Start {
        data class Ready(val token: String) : Start()
        data class ShowGoogle(val intent: PendingIntent) : Start()
        data class Failed(val message: String) : Start()
    }

    /** Starts signing in to read the music. The screen shows Google's page when this says to. */
    suspend fun start(context: Context): Start = begin(context, read)

    /** Starts asking for permission to edit the music files. */
    suspend fun startEdit(context: Context): Start = begin(context, edit)

    private suspend fun begin(context: Context, pass: Pass): Start = try {
        val result = Identity.getAuthorizationClient(context).authorize(request(pass)).await()
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            Start.ShowGoogle(pending)
        } else {
            val token = result.accessToken
            if (token != null) {
                accept(pass, token)
                Start.Ready(token)
            } else {
                Start.Failed("Google gave no sign-in. Try again.")
            }
        }
    } catch (e: ApiException) {
        Start.Failed(explain(e))
    } catch (e: Exception) {
        Start.Failed("Couldn't reach Google. Check the internet and try again." + (e.message?.let { "\nGoogle's words: $it" } ?: ""))
    }

    /** Google's page for reading has closed: reads what it gave. */
    fun finish(context: Context, data: android.content.Intent?): Start = end(context, data, read)

    /** Google's page for editing has closed: reads what it gave. */
    fun finishEdit(context: Context, data: android.content.Intent?): Start = end(context, data, edit)

    private fun end(context: Context, data: android.content.Intent?, pass: Pass): Start = try {
        val result: AuthorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val token = result.accessToken
        if (token != null) {
            accept(pass, token)
            Start.Ready(token)
        } else {
            Start.Failed("Google gave no sign-in. Try again.")
        }
    } catch (e: ApiException) {
        Start.Failed(explain(e))
    }

    /** Plain words for what Google said no to. */
    fun explain(e: ApiException): String = when (e.statusCode) {
        // DEVELOPER_ERROR: the Google project doesn't list this app yet.
        10 -> "Google doesn't know KueSounds yet. The one set-up step in Google Cloud is still needed." + googleWords(e)
        // CANCELED, or the person closed the page.
        16, 12501 -> "Sign-in was closed before it finished."
        7 -> "Couldn't reach Google. Check the internet and try again."
        else -> "Google said no (code " + e.statusCode + ")." + googleWords(e)
    }

    /** Google's own reason, word for word, so a failed sign-in can be looked into. */
    private fun googleWords(e: ApiException): String {
        val words = (e.status.statusMessage ?: e.message).orEmpty().removePrefix(e.statusCode.toString() + ":").trim()
        return if (words.isEmpty()) "" else "\nGoogle's words: " + words
    }

    private fun accept(pass: Pass, token: String) {
        pass.token = token
        pass.at = System.currentTimeMillis()
        prefs().edit().putBoolean(pass.flag, true).apply()
    }

    fun keepEmail(address: String) {
        if (address.isNotBlank()) prefs().edit().putString("email", address).apply()
    }

    private suspend fun current(pass: Pass): String? = pass.lock.withLock {
        val now = System.currentTimeMillis()
        pass.token?.let { if (now - pass.at < 45 * 60 * 1000L) return@withLock it }
        if (!prefs().getBoolean(pass.flag, false)) return@withLock null
        val result = runCatching { Identity.getAuthorizationClient(app).authorize(request(pass)).await() }.getOrNull()
            ?: return@withLock pass.token
        if (result.hasResolution()) return@withLock null
        val token = result.accessToken ?: return@withLock null
        pass.token = token
        pass.at = now
        token
    }

    /** A current pass for reading the Drive, or null when the person needs to sign in again. */
    suspend fun token(): String? = current(read)

    /** A current pass for editing the Drive, or null when permission needs asking for again. */
    suspend fun editToken(): String? = current(edit)

    /** The reading pass, for the player, which asks from its own background thread. */
    fun tokenBlocking(): String? = runBlocking { token() }

    /**
     * Drops the passes held, after Google refused one, so fresh ones are asked for. The phone's own
     * copy is dropped too, or it would hand the same spent pass back. Call off the main thread.
     */
    fun forget() {
        for (pass in listOf(read, edit)) {
            val spent = pass.token
            pass.token = null
            pass.at = 0
            if (spent != null) runCatching { com.google.android.gms.auth.GoogleAuthUtil.clearToken(app, spent) }
        }
    }
}
