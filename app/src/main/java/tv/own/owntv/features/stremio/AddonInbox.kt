package tv.own.owntv.features.stremio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Kevin's copy: a one-page web form on this TV, so an add-on address can be pasted on a phone instead
 * of typed with the remote. Only while the "Add an add-on" window is open, only on the home network,
 * and only with the four-digit code shown on the TV.
 */
class AddonInbox(
    private val scope: CoroutineScope,
    private val texts: Texts,
    private val onAddress: (String) -> Unit,
) {

    /** The page's words, from the app's string resources. */
    data class Texts(
        val title: String,
        val addressLabel: String,
        val codeLabel: String,
        val send: String,
        val sent: String,
        val wrongCode: String,
    )

    val code: String = (1000..9999).random().toString()
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private var job: Job? = null

    /** Opens the form; false when no port could be opened. */
    fun start(): Boolean {
        val s = PORTS.firstNotNullOfOrNull { p ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull()
        } ?: return false
        server = s
        port = s.localPort
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                runCatching { handle(client) }.onFailure { Log.w(TAG, "request failed: ${it.message}") }
            }
        }
        return true
    }

    fun stop() {
        runCatching { server?.close() }
        job?.cancel()
        server = null
    }

    private fun handle(client: Socket) = client.use { sock ->
        sock.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val page = if (requestLine.startsWith("POST")) {
            val buf = CharArray(length.coerceIn(0, MAX_BODY))
            var read = 0
            while (read < buf.size) {
                val r = reader.read(buf, read, buf.size - read)
                if (r < 0) break
                read += r
            }
            val form = String(buf, 0, read).split('&').associate {
                val k = it.substringBefore('=')
                k to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val address = form["url"].orEmpty().trim()
            when {
                form["code"]?.trim() != code -> page(PageText.WRONG_CODE)
                address.isBlank() -> page(PageText.FORM)
                else -> { onAddress(address); page(PageText.SENT) }
            }
        } else page(PageText.FORM)
        val bytes = page.toByteArray(Charsets.UTF_8)
        val out = sock.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
        )
        out.write(bytes)
        out.flush()
    }

    private enum class PageText { FORM, SENT, WRONG_CODE }

    /** The phone page; the words are [texts], escaped. */
    private fun page(kind: PageText): String {
        val note = when (kind) {
            PageText.FORM -> ""
            PageText.SENT -> "<p class=ok>" + esc(texts.sent) + "</p>"
            PageText.WRONG_CODE -> "<p class=bad>" + esc(texts.wrongCode) + "</p>"
        }
        return PAGE_HEAD + "<title>" + esc(texts.title) + "</title>" + PAGE_STYLE +
            "<body><h2>" + esc(texts.title) + "</h2>" + note +
            "<form method=post><label>" + esc(texts.addressLabel) + "</label>" + PAGE_URL_INPUT +
            "<label>" + esc(texts.codeLabel) + "</label>" + PAGE_CODE_INPUT +
            "<button type=submit>" + esc(texts.send) + "</button></form></body></html>"
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val TAG = "KevinAddonInbox"
        private val PORTS = listOf(8691, 8692, 8693, 0)
        private const val MAX_BODY = 8_192
        private const val PAGE_HEAD = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
        private const val PAGE_STYLE = "<style>body{font-family:system-ui,sans-serif;margin:24px;max-width:560px}input,button{font-size:18px;width:90vw;max-width:560px;box-sizing:border-box;padding:12px;margin:8px 0 16px}button{background:#2563eb;color:#fff;border:0;border-radius:8px}.ok{color:#15803d}.bad{color:#b91c1c}</style></head>"
        private const val PAGE_URL_INPUT = "<input name=url type=url required placeholder=\"https://\u2026/manifest.json\">"
        private const val PAGE_CODE_INPUT = "<input name=code inputmode=numeric required>"
    }
}
