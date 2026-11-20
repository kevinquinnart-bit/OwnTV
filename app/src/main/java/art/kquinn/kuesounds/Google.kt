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
 * Signing in to the Google account the music is in, with the phone's own Google sign-in. KueSounds asks
 * only to READ that Drive. The sign-in lasts: each hour Google hands over a fresh pass without asking.
 */
object Google {
    const val DRIVE_READ = "https://www.googleapis.com/auth/drive.readonly"

    private lateinit var app: Context
    private val lock = Mutex()
    private var cached: String? = null
    private var cachedAt = 0L

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun prefs() = app.getSharedPreferences("google", Context.MODE_PRIVATE)

    /** The music account's address once signed in; empty before. */
    val email: String get() = prefs().getString("email", "").orEmpty()

    val signedIn: Boolean get() = prefs().getBoolean("signedIn", false)

    fun request(email: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_READ)))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()

    /** What starting a sign-in gave: a pass straight away, or Google's own screen to show. */
    sealed class Start {
        data class Ready(val token: String) : Start()
        data class ShowGoogle(val intent: PendingIntent) : Start()
        data class Failed(val message: String) : Start()
    }

    /** Starts signing in. The screen shows Google's page when this says to. */
    suspend fun start(context: Context): Start = try {
        val result = Identity.getAuthorizationClient(context).authorize(request(email.ifBlank { null })).await()
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            Start.ShowGoogle(pending)
        } else {
            val token = result.accessToken
            if (token != null) {
                accept(token)
                Start.Ready(token)
            } else {
                Start.Failed("Google gave no sign-in. Try again.")
            }
        }
    } catch (e: ApiException) {
        Start.Failed(explain(e))
    } catch (e: Exception) {
        Start.Failed("Couldn't reach Google. Check the internet and try again.")
    }

    /** Google's page has closed: reads what it gave. */
    fun finish(context: Context, data: android.content.Intent?): Start = try {
        val result: AuthorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val token = result.accessToken
        if (token != null) {
            accept(token)
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
        10 -> "Google doesn't know KueSounds yet. The one set-up step in Google Cloud is still needed."
        // CANCELED, or the person closed the page.
        16, 12501 -> "Sign-in was closed before it finished."
        7 -> "Couldn't reach Google. Check the internet and try again."
        else -> "Google said no (code " + e.statusCode + "). Try again."
    }

    private fun accept(token: String) {
        cached = token
        cachedAt = System.currentTimeMillis()
        prefs().edit().putBoolean("signedIn", true).apply()
    }

    fun keepEmail(address: String) {
        if (address.isNotBlank()) prefs().edit().putString("email", address).apply()
    }

    /** A current pass for the Drive, or null when the person needs to sign in again. */
    suspend fun token(): String? = lock.withLock {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < 45 * 60 * 1000L) return@withLock it }
        if (!signedIn) return@withLock null
        val result = runCatching { Identity.getAuthorizationClient(app).authorize(request(email.ifBlank { null })).await() }.getOrNull()
            ?: return@withLock cached
        if (result.hasResolution()) return@withLock null
        val token = result.accessToken ?: return@withLock null
        cached = token
        cachedAt = now
        token
    }

    /** The same, for the player, which asks from its own background thread. */
    fun tokenBlocking(): String? = runBlocking { token() }

    /**
     * Drops the pass held, after Google refused it, so a fresh one is asked for. The phone's own copy is
     * dropped too, or it would hand the same spent pass back. Call off the main thread.
     */
    fun forget() {
        val spent = cached
        cached = null
        cachedAt = 0
        if (spent != null) runCatching { com.google.android.gms.auth.GoogleAuthUtil.clearToken(app, spent) }
    }
}
