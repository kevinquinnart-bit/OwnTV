package art.kquinn.kuetv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Lets the provider details be typed on a phone instead of with the TV remote. While the setup screen
 * is open the TV answers on the home network: the phone opens the address shown on the TV, fills in a
 * short form, and the details arrive here. Nothing leaves the home network.
 */
class PhoneSetup(
    /** Given: the page asks for an add-on's link instead of the provider details, and the link arrives here. */
    private val onAddOn: ((link: String) -> Unit)? = null,
    /** With [onAddOn]: the link asked for is a free channel list's, and the page says so. */
    private val freeList: Boolean = false,
    /** Given: the page asks for a Stremio email and password instead, and they arrive here. */
    private val onStremio: ((email: String, password: String) -> Unit)? = null,
    /** Given: the page asks for a VPN's OpenVPN file (its text, or a link to it) and login instead, and they arrive here. */
    private val onVpn: ((config: String, link: String, user: String, pass: String) -> Unit)? = null,
    private val onDetails: (server: String, user: String, pass: String, playlist: String) -> Unit,
) {
    @Volatile private var socket: ServerSocket? = null

    /** The address to open on the phone, or null when the TV has no home-network address. */
    fun start(): String? {
        val ip = localAddress() ?: run {
            Report.note("Phone page", "not started: this device has no home-network address")
            return null
        }
        val server = runCatching { ServerSocket(PORT) }.getOrNull() ?: run {
            Report.note("Phone page", "not started: another screen still holds its door")
            return null
        }
        socket = server
        Report.note("Phone page", "open at http://$ip:$PORT")
        thread(isDaemon = true, name = "phone-setup") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { answer(client) }; runCatching { client.close() } }
            }
        }
        return "http://$ip:$PORT"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun answer(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val first = input.readLine() ?: return
        // So the report shows whether the phone ever reached the TV.
        Report.note("Phone page", "asked for by " + (client.inetAddress?.hostAddress ?: "?") + ": " + first.substringBefore(" HTTP"))
        var length = 0
        while (true) {
            val header = input.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        if (first.startsWith("POST")) {
            // An OpenVPN file sent from the phone, with its certificates, runs to tens of thousands of characters.
            val body = CharArray(length.coerceIn(0, 400_000))
            var read = 0
            while (read < body.size) {
                val n = input.read(body, read, body.size - read)
                if (n < 0) break
                read += n
            }
            val fields = String(body, 0, read).split('&').mapNotNull { pair ->
                val at = pair.indexOf('=')
                if (at < 0) null else URLDecoder.decode(pair.substring(0, at), "UTF-8") to URLDecoder.decode(pair.substring(at + 1), "UTF-8")
            }.toMap()
            if (onVpn != null) {
                onVpn.invoke(fields["config"].orEmpty(), fields["link"].orEmpty(), fields["user"].orEmpty(), fields["pass"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>The TV says whether it could read the file. You can close this page.</p>"))
            } else if (onStremio != null) {
                onStremio.invoke(fields["email"].orEmpty(), fields["password"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>The TV says whether Stremio accepted it. You can close this page.</p>"))
            } else if (onAddOn != null) {
                onAddOn.invoke(fields["link"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>The TV says whether it was added. To add another, go back and paste the next link.</p>"))
            } else {
                onDetails(fields["server"].orEmpty(), fields["user"].orEmpty(), fields["pass"].orEmpty(), fields["playlist"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>You can close this page.</p>"))
            }
        } else {
            send(client, page(if (onVpn != null) VPN_FORM else if (onStremio != null) STREMIO_FORM else if (onAddOn == null) FORM else if (freeList) FREE_FORM else ADDON_FORM))
        }
    }

    private fun send(client: Socket, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun page(inner: String) = "<!doctype html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>KueTV</title><style>" +
        "body{font-family:sans-serif;background:#0e1626;color:#fff;margin:0;padding:22px;font-size:18px}" +
        "h1{font-size:24px}label{display:block;margin:16px 0 6px}" +
        "input{width:100%;box-sizing:border-box;padding:14px;font-size:18px;border-radius:10px;border:0}" +
        "button{margin-top:22px;width:100%;padding:16px;font-size:20px;border:0;border-radius:10px;background:#ffd23f;font-weight:bold}" +
        "p{color:#b9c2d3}hr{border:0;border-top:1px solid #33405a;margin:26px 0}" +
        "</style></head><body>$inner</body></html>"

    companion object {
        const val PORT = 8765

        private const val FORM = "<h1>KueTV: your provider details</h1>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>Server address</label><input name=\"server\" autocapitalize=\"none\" autocorrect=\"off\" placeholder=\"http://example.com:8080\">" +
            "<label>Username</label><input name=\"user\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<label>Password</label><input name=\"pass\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<hr><p>Or, if your provider gave you one playlist link instead, paste it here and leave the three boxes above empty.</p>" +
            "<label>Playlist link</label><input name=\"playlist\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<button type=\"submit\">Send to TV</button></form>"

        private const val STREMIO_FORM = "<h1>KueTV: sign in to Stremio</h1>" +
            "<p>KueTV brings in the add-ons installed in your Stremio account. Your password goes only to Stremio, and isn't kept.</p>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>Stremio email</label><input name=\"email\" type=\"email\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<label>Stremio password</label><input name=\"password\" type=\"password\">" +
            "<button type=\"submit\">Send to TV</button></form>"

        private const val VPN_FORM = "<h1>KueTV: your VPN</h1>" +
            "<p>Choose the OpenVPN file your VPN company gave you (it ends in .ovpn), or paste a link to it. Then the username and password that go with it.</p>" +
            "<form id=\"f\" method=\"post\" action=\"/\">" +
            "<label>OpenVPN file (.ovpn)</label><input type=\"file\" id=\"file\">" +
            "<textarea name=\"config\" id=\"config\" style=\"display:none\"></textarea>" +
            "<p>Or a link to the file (a .ovpn, or a .zip with one in it):</p>" +
            "<label>Link</label><input name=\"link\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<hr><label>VPN username</label><input name=\"user\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<label>VPN password</label><input name=\"pass\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<button type=\"submit\">Send to TV</button></form>" +
            // The chosen file is read on the phone and sent as text with the rest.
            "<script>document.getElementById('f').onsubmit=function(e){var f=document.getElementById('file').files[0];" +
            "if(!f)return true;e.preventDefault();var r=new FileReader();r.onload=function(){" +
            "document.getElementById('config').value=r.result;document.getElementById('f').submit();};r.readAsText(f);return false;};</script>"

        private const val FREE_FORM = "<h1>KueTV: add a free channel list</h1>" +
            "<p>Paste the list's link here. It usually ends in .m3u or .m3u8. An add-on's link works here too.</p>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>List link</label><input name=\"link\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<button type=\"submit\">Send to TV</button></form>"

        private const val ADDON_FORM = "<h1>KueTV: add an add-on</h1>" +
            "<p>Paste the add-on's link here (it usually ends in manifest.json), or a channel list's link (it usually ends in .m3u).</p>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>Add-on link</label><input name=\"link\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<button type=\"submit\">Send to TV</button></form>"

        /** Set when the app starts, so the address can be asked of the network the device is using. */
        @Volatile var app: android.content.Context? = null

        fun localAddress(): String? = activeAddress() ?: anyAddress()

        /**
         * The device's address on the home network it is using now: its Wi-Fi, or its cable. With a VPN on, the
         * VPN is the network in use, so the home network beside it is looked for instead: the VPN's own address
         * can't be reached by the phone or the other device.
         */
        private fun activeAddress(): String? = runCatching {
            val manager = app?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return null
            val home = (listOfNotNull(manager.activeNetwork) + manager.allNetworks).distinct().filter { n ->
                manager.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == false
            }
            home.firstNotNullOfOrNull { n ->
                manager.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
            }
        }.getOrNull()

        private fun anyAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                // The VPN's own connection ("tun") isn't the home network.
                .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
