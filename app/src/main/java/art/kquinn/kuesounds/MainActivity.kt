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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Google.init(this)
        Scanner.init(this)
        Playing.connect(this)
        signedIn = Google.signedIn
        if (signedIn && Scanner.data.tracks.isEmpty() || signedIn && System.currentTimeMillis() - Scanner.data.scannedAt > 6 * 60 * 60 * 1000L) {
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
        lifecycleScope.launch(Dispatchers.IO) { Updates.checkOnOpen() }
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
