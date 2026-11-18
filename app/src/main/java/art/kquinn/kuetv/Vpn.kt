package art.kquinn.kuetv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import okhttp3.CacheControl
import okhttp3.Request
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * KueTV's own VPN, through the OpenVPN engine built into the app (OpenVPN for Android, by Arne Schwabe).
 *
 * The user adds a VPN company's OpenVPN file (the text of an .ovpn file, or a link to one, or to a .zip
 * holding one) and the username and password that go with it. Connect asks the device once for
 * permission to run a VPN, then starts it. A VPN on a Fire TV or phone covers the whole device. The
 * home network stays reachable, so the phone page and keeping in step still work while it is on.
 */
object Vpn {
    /** Goes up whenever the details or the "connect on opening" choice change. */
    var changes by mutableIntStateOf(0)
        private set

    /** Whether the VPN page is open. */
    var screenOpen by mutableStateOf(false)

    /** How the VPN is now, as the engine last said. */
    var level by mutableStateOf(ConnectionStatus.LEVEL_NOTCONNECTED)
        private set

    /** The engine's own last words on it, for the report and for a problem that needs saying. */
    var detail by mutableStateOf("")
        private set

    /** The details: the OpenVPN file's text, and the login that goes with it. */
    class Details(val config: String, val user: String, val pass: String) {
        val isEmpty: Boolean get() = config.isBlank()
    }

    /** What an OpenVPN file says about itself: the server it connects to, and whether it wants a login. */
    class Reading(val server: String, val wantsLogin: Boolean)

    class VpnProblem(message: String) : Exception(message)

    private fun prefs(context: Context) = context.getSharedPreferences("vpn", Context.MODE_PRIVATE)

    fun details(context: Context): Details {
        val p = prefs(context)
        return Details(p.getString("config", "").orEmpty(), p.getString("user", "").orEmpty(), p.getString("pass", "").orEmpty())
    }

    /** Keeps new details, after checking the file can be read. Throws [VpnProblem] saying what is wrong. */
    fun keep(context: Context, config: String, user: String, pass: String): Reading {
        val reading = read(config)
        prefs(context).edit().putString("config", config.trim()).putString("user", user.trim()).putString("pass", pass).apply()
        changes++
        return reading
    }

    /** Takes the details another device sent, when keeping in step. */
    fun adopt(context: Context, config: String, user: String, pass: String) {
        prefs(context).edit().putString("config", config).putString("user", user).putString("pass", pass).apply()
        changes++
    }

    fun forget(context: Context) {
        prefs(context).edit().remove("config").remove("user").remove("pass").apply()
        changes++
    }

    /** Whether KueTV connects the VPN by itself each time it opens. */
    fun connectOnOpen(context: Context): Boolean = prefs(context).getBoolean("auto", false)
    fun connectOnOpen(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("auto", on).apply()
        changes++
    }

    /** Reads an OpenVPN file without connecting. Throws [VpnProblem] when it isn't one KueTV can use. */
    fun read(config: String): Reading {
        if (config.isBlank()) throw VpnProblem("No OpenVPN file has been added.")
        val profile = parse(config)
        val server = profile.mConnections.firstOrNull()?.mServerName.orEmpty()
        if (server.isBlank() || server == "openvpn.example.com") throw VpnProblem("The file doesn't say which server to connect to.")
        val wantsLogin = profile.mAuthenticationType == VpnProfile.TYPE_USERPASS ||
            profile.mAuthenticationType == VpnProfile.TYPE_USERPASS_CERTIFICATES ||
            profile.mAuthenticationType == VpnProfile.TYPE_USERPASS_PKCS12 ||
            profile.mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE
        return Reading(server, wantsLogin)
    }

    private fun parse(config: String): VpnProfile {
        val parser = ConfigParser()
        try {
            parser.parseConfig(StringReader(config))
            return parser.convertProfile()
        } catch (e: Exception) {
            throw VpnProblem("That isn't an OpenVPN file KueTV can read: " + (e.message ?: e.javaClass.simpleName) + ".")
        }
    }

    /**
     * Fetches an OpenVPN file from a link. A .zip is opened and its first .ovpn file taken. Call off the
     * main thread. Throws [VpnProblem] saying what went wrong.
     */
    fun fetch(link: String): String {
        val url = link.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw VpnProblem("That isn't a link. It should start with http:// or https://")
        val http = Provider.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).header("User-Agent", Provider.USER_AGENT).build()
        val bytes = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw VpnProblem("The link answered " + response.code + ".")
                response.body.bytes()
            }
        } catch (e: VpnProblem) {
            throw e
        } catch (e: Exception) {
            throw VpnProblem("Couldn't fetch the link: " + (e.message ?: e.javaClass.simpleName) + ".")
        }
        val text = fromFile(bytes)
        // A site can answer with a web page (a sign-in, an advert, a check that a person is there) instead of the file.
        val looksLikePage = text.trimStart().take(200).lowercase().let { it.startsWith("<!doctype") || it.startsWith("<html") || it.contains("<head") }
        Report.note("VPN", "fetched " + url + ": " + bytes.size + " bytes, " + if (looksLikePage) "a web page" else "starts \"" + text.trimStart().take(40).replace('\n', ' ') + "\"")
        if (looksLikePage) throw VpnProblem("The link gave a web page, not the OpenVPN file. Download the .ovpn file on your phone instead, and send it from the phone page.")
        return text
    }

    /** The OpenVPN text in a file: the file itself, or the first .ovpn inside a .zip. */
    fun fromFile(bytes: ByteArray): String {
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".ovpn", ignoreCase = true)) return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            throw VpnProblem("The .zip has no OpenVPN (.ovpn) file in it.")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** The device's permission to run a VPN, if it still has to be asked: show this, then call [start]. Null when already given. */
    fun permissionAsk(context: Context): Intent? = VpnService.prepare(context)

    /** Starts the VPN with the kept details. The permission must already be given. Throws [VpnProblem]. */
    fun start(context: Context) {
        val details = details(context)
        if (details.isEmpty) throw VpnProblem("Add the OpenVPN file first.")
        val profile = parse(details.config)
        profile.mName = "KueTV VPN"
        profile.mUsername = details.user
        profile.mPassword = details.pass
        // The home network stays outside the VPN, so the phone page and keeping in step still reach the other device.
        profile.mAllowLocalLAN = true
        val problem = profile.checkProfile(context, false)
        if (problem != de.blinkt.openvpn.R.string.no_error_found) throw VpnProblem(context.getString(problem))
        if (profile.needUserPWInput(null, null) != 0) throw VpnProblem("This VPN needs a username and password. Add them with the file.")
        ProfileManager.setTemporaryProfile(context, profile)
        Report.note("VPN", "connecting to " + profile.mConnections.firstOrNull()?.mServerName.orEmpty())
        VPNLaunchHelper.startOpenVpn(profile, context, "KueTV", true)
    }

    /** Stops the VPN, if it is running. */
    fun stop(context: Context) {
        val app = context.applicationContext
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runCatching { IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false) }
                    .onFailure { Report.note("VPN", "stopping failed: " + it.message) }
                runCatching { app.unbindService(this) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
        if (!app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) Report.note("VPN", "stopping: the VPN service couldn't be reached")
        Report.note("VPN", "disconnecting")
    }

    val isOn: Boolean get() = level == ConnectionStatus.LEVEL_CONNECTED
    val isTrying: Boolean
        get() = level == ConnectionStatus.LEVEL_START || level == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET ||
            level == ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED || level == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT

    private var listening = false

    /** Listens to the engine, once, for as long as the app runs. */
    fun listen() {
        if (listening) return
        listening = true
        val main = Handler(Looper.getMainLooper())
        VpnStatus.addStateListener(object : VpnStatus.StateListener {
            override fun updateState(state: String?, logmessage: String?, localizedResId: Int, level: ConnectionStatus?, intent: Intent?) {
                val now = level ?: return
                main.post {
                    if (now != this@Vpn.level) Report.note("VPN", now.name.removePrefix("LEVEL_").lowercase().replace('_', ' ') + (if (logmessage.isNullOrBlank()) "" else " · " + logmessage))
                    this@Vpn.level = now
                    detail = logmessage.orEmpty()
                }
            }

            override fun setConnectedVPN(uuid: String?) {}
        })
    }

    /** Where the internet sees this device: its address, town and country. */
    class Where(val ip: String, val city: String, val country: String) {
        override fun toString() = listOf(city, if (country.isBlank()) "" else java.util.Locale("", country).getDisplayCountry(java.util.Locale.UK)).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Unknown place" } + " · " + ip
    }

    /** Asks a lookup site where this device appears to be. Call off the main thread. Throws when it can't be reached. */
    fun where(): Where {
        val http = Provider.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("https://ipinfo.io/json").cacheControl(CacheControl.FORCE_NETWORK).header("User-Agent", Provider.USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("the lookup answered " + response.code)
            val o = com.google.gson.JsonParser.parseString(response.body.string()).asJsonObject
            fun s(k: String) = o.get(k)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            return Where(s("ip"), s("city"), s("country"))
        }
    }
}
