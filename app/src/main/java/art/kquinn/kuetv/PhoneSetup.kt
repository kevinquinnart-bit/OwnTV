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
    private val onDetails: (server: String, user: String, pass: String, playlist: String) -> Unit,
) {
    @Volatile private var socket: ServerSocket? = null

    /** The address to open on the phone, or null when the TV has no home-network address. */
    fun start(): String? {
        val ip = localAddress() ?: return null
        val server = runCatching { ServerSocket(PORT) }.getOrNull() ?: return null
        socket = server
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
        var length = 0
        while (true) {
            val header = input.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        if (first.startsWith("POST")) {
            val body = CharArray(length.coerceIn(0, 20_000))
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
            if (onAddOn != null) {
                onAddOn.invoke(fields["link"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>The TV says whether it was added. To add another, go back and paste the next link.</p>"))
            } else {
                onDetails(fields["server"].orEmpty(), fields["user"].orEmpty(), fields["pass"].orEmpty(), fields["playlist"].orEmpty())
                send(client, page("<h1>Sent to your TV</h1><p>You can close this page.</p>"))
            }
        } else {
            send(client, page(if (onAddOn != null) ADDON_FORM else FORM))
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

        private const val ADDON_FORM = "<h1>KueTV: add an add-on</h1>" +
            "<p>Paste the add-on's link here. It is the one its page gives you to copy, and usually ends in manifest.json.</p>" +
            "<form method=\"post\" action=\"/\">" +
            "<label>Add-on link</label><input name=\"link\" autocapitalize=\"none\" autocorrect=\"off\">" +
            "<button type=\"submit\">Send to TV</button></form>"

        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
