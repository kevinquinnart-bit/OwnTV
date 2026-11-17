package art.kquinn.kuetv

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Everything that makes one device's KueTV the user's own: the provider details, favourites, add-ons and choices. */
data class SetUp(
    val account: Account,
    val favourites: Set<String>,
    val addOns: List<AddOn>,
    val smallTv: Boolean,
    /** The hidden categories, under "live", "film" and "show". */
    val hidden: Map<String, Set<String>> = emptyMap(),
    /** The free channel lists, by their links. */
    val free: List<String> = emptyList(),
    /** The secret key that pairs the two devices, so they keep in step afterwards. Empty from an older version. */
    val pair: String = "",
) {
    fun pack(): String = JsonObject().apply {
        add("account", JsonObject().apply {
            addProperty("server", account.server); addProperty("user", account.user); addProperty("pass", account.pass); addProperty("playlist", account.playlist)
        })
        add("favourites", JsonArray().apply { favourites.sorted().forEach { add(it) } })
        add("addOns", JsonArray().apply { addOns.forEach { add(it.toJson()) } })
        addProperty("smallTv", smallTv)
        add("hidden", JsonObject().apply { hidden.forEach { (kind, ids) -> add(kind, JsonArray().apply { ids.sorted().forEach { add(it) } }) } })
        add("free", JsonArray().apply { free.forEach { add(it) } })
        addProperty("pair", pair)
    }.toString()

    companion object {
        fun unpack(text: String): SetUp? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            val a = o.getAsJsonObject("account")
            SetUp(
                Account(a.get("server").asString, a.get("user").asString, a.get("pass").asString, a.get("playlist").asString),
                o.getAsJsonArray("favourites").map { it.asString }.toSet(),
                o.getAsJsonArray("addOns").mapNotNull { AddOn.fromJson(it.asString) },
                o.get("smallTv")?.asBoolean ?: true,
                // A set-up sent by an older version has none.
                o.getAsJsonObject("hidden")?.entrySet()?.associate { (kind, ids) -> kind to ids.asJsonArray.map { it.asString }.toSet() }.orEmpty(),
                o.getAsJsonArray("free")?.map { it.asString }.orEmpty(),
                o.get("pair")?.asString.orEmpty(),
            )
        }.getOrNull()
    }
}

/**
 * Hands this device's set-up to another device on the same home network. While the "send" screen is
 * open, this device answers to anyone on the network who gives the four-figure code on that screen.
 * After a few wrong codes it stops answering. Nothing leaves the home network.
 */
class SetUpSender(private val code: String, private val setUp: () -> String, private val onTaken: () -> Unit = {}) {
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var wrong = 0

    /** The port being answered on, or null when it couldn't be opened. */
    fun start(port: Int = PORT): Int? {
        val server = runCatching { ServerSocket(port) }.getOrNull() ?: return null
        socket = server
        thread(isDaemon = true, name = "setup-sender") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { answer(client) }; runCatching { client.close() } }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun answer(client: Socket) {
        client.soTimeout = 8_000
        val first = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8)).readLine() ?: return
        val path = first.split(' ').getOrNull(1).orEmpty()
        when {
            path == "/hello" -> send(client, 200, HELLO)
            path.startsWith("/setup?code=") && wrong < 5 && path.substringAfter("code=") == code -> {
                send(client, 200, setUp())
                onTaken()
            }
            path.startsWith("/setup") -> {
                wrong++
                send(client, 403, "no")
                if (wrong >= 5) stop()
            }
            else -> send(client, 404, "no")
        }
    }

    private fun send(client: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(("HTTP/1.1 $status X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val PORT = 8766
        const val HELLO = "KueTV set-up"
    }
}

/** Finds a device that is sending its set-up, and takes the set-up from it with the code. */
object SetUpTaker {
    fun ask(host: String, port: Int, path: String, timeoutMs: Int): Pair<Int, String>? = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = 8_000
            socket.getOutputStream().apply { write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
            val text = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val status = text.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
            status to text.substringAfter("\r\n\r\n", "")
        }
    }.getOrNull()

    /** Every address on this device's part of the home network ("192.168.1.") that is sending a set-up. */
    fun find(mine: String, port: Int = SetUpSender.PORT, hello: String = SetUpSender.HELLO): List<String> {
        val prefix = mine.substringBeforeLast('.', "") + "."
        if (prefix == ".") return emptyList()
        val pool = Executors.newFixedThreadPool(48)
        try {
            val asks = (1..254).map { n ->
                val host = prefix + n
                pool.submit(Callable { if (ask(host, port, "/hello", 700)?.second == hello) host else null })
            }
            return asks.mapNotNull { runCatching { it.get(20, TimeUnit.SECONDS) }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }

    /** The set-up from [host], given the right [code]. Null when the code is wrong or it didn't answer. */
    fun take(host: String, code: String, port: Int = SetUpSender.PORT): SetUp? {
        val answer = ask(host, port, "/setup?code=" + code.filter { it.isDigit() }, 3_000) ?: return null
        return if (answer.first == 200) SetUp.unpack(answer.second) else null
    }
}
