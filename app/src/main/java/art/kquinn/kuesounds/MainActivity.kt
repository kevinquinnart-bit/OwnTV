package art.kquinn.kuesounds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** What the sign-in screen shows under its button: empty, or why the last try failed. */
    private var signInProblem by mutableStateOf("")
    private var signingIn by mutableStateOf(false)
    private var signedIn by mutableStateOf(false)

    private val googlePage = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        signingIn = false
        when (val r = Google.finish(this, result.data)) {
            is Google.Start.Ready -> afterSignIn()
            is Google.Start.Failed -> signInProblem = r.message
            is Google.Start.ShowGoogle -> signInProblem = "Sign-in didn't finish. Try again."
        }
    }

    /** What to do once the person has allowed KueSounds to edit the music files. */
    private var afterEditAllowed: (() -> Unit)? = null

    private val editPage = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        when (val r = Google.finishEdit(this, result.data)) {
            is Google.Start.Ready -> afterEditAllowed?.invoke()
            is Google.Start.Failed -> Fixer.permissionProblem = r.message
            is Google.Start.ShowGoogle -> Fixer.permissionProblem = "Permission wasn't given. Try again."
        }
        afterEditAllowed = null
    }

    private val noticesPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Asks Google for permission to edit the music files, then does [then]. */
    private fun askEdit(then: () -> Unit) {
        Fixer.permissionProblem = ""
        afterEditAllowed = then
        lifecycleScope.launch {
            when (val r = Google.startEdit(this@MainActivity)) {
                is Google.Start.Ready -> { afterEditAllowed = null; then() }
                is Google.Start.ShowGoogle -> editPage.launch(IntentSenderRequest.Builder(r.intent.intentSender).build())
                is Google.Start.Failed -> { afterEditAllowed = null; Fixer.permissionProblem = r.message }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Google.init(this)
        Scanner.init(this)
        Offline.init(this)
        Sync.init(this)
        Playing.connect(this)
        Fixer.askPermission = ::askEdit
        Fixer.askNotices = {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                noticesPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        signedIn = Google.signedIn
        val unfinished = Scanner.data.tracks.any { !it.tagged || !it.checked }
        if (signedIn && Net.online(this) && (Scanner.data.tracks.isEmpty() || unfinished || System.currentTimeMillis() - Scanner.data.scannedAt > 6 * 60 * 60 * 1000L)) {
            Scanner.scan()
        }
        setContent {
            Ui.App(
                // Google can take a sign-in back (a changed password, access removed): then it's asked for again.
                signedIn = signedIn && !Scanner.signedOut,
                signingIn = signingIn,
                signInProblem = signInProblem,
                onSignIn = ::signIn,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (signedIn) Sync.later(1000)
        lifecycleScope.launch(Dispatchers.IO) { Updates.checkAndFetch(applicationContext) }
    }

    /** Leaving KueSounds (Home or another app): a downloaded update goes on now, unless music is playing. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Updates.installIfReady(applicationContext)
    }

    private fun signIn() {
        if (signingIn) return
        signingIn = true
        signInProblem = ""
        lifecycleScope.launch {
            when (val r = Google.start(this@MainActivity)) {
                is Google.Start.Ready -> {
                    signingIn = false
                    afterSignIn()
                }
                is Google.Start.ShowGoogle -> googlePage.launch(IntentSenderRequest.Builder(r.intent.intentSender).build())
                is Google.Start.Failed -> {
                    signingIn = false
                    signInProblem = r.message
                }
            }
        }
    }

    private fun afterSignIn() {
        signedIn = true
        Scanner.signedOut = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { Google.keepEmail(Drive.whoAmI()) } }
            Scanner.scan()
        }
    }
}
