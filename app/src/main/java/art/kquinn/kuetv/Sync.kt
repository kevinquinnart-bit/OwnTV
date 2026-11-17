package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Keeping the TV and the phone in step by themselves.
 *
 * Devices are paired once, by copying the set-up from one to the other with the four-figure code: that
 * copy hands over a long secret key. From then on, whenever KueTV is open on both on the same Wi-Fi,
 * each asks the other every minute what it has, and takes whatever is newer.
 *
 * What is kept in step is split into parts (provider details, favourites, add-ons, and so on). Each part
 * carries the time it was last changed, and for each part the newer one wins. So a favourite added on
 * the phone and an add-on added on the TV both survive. Two changes to the same part, made on both
 * devices before they next meet, don't: the later one wins.
 *
 * Text size, the small TV and pause-and-rewind are left out on purpose: a TV and a phone want their own.
 */
object Sync {
    const val PORT = 8768
    const val HELLO = "KueTV in step"

    /** One part as a device holds it: when it was last changed, and what it is. */
    data class Part(val at: Long, val body: String)

    var changes by mutableIntStateOf(0)
        private set

    /** What was last taken from the other device, in plain words, with the time. */
    var lastTook by androidx.compose.runtime.mutableStateOf("")
        private set

    private val partNames = mapOf(
        "account" to "provider details", "favourites" to "favourites", "favouriteOrder" to "favourites order", "addOns" to "add-ons",
        "free" to "free channel lists", "hidden" to "hidden categories", "subtitles" to "subtitles", "places" to "Carry on watching",
        "reminders" to "reminders", "stremio" to "Stremio sign-in",
    )

    fun tookNote(parts: Set<String>) {
        lastTook = "Last taken from the other device at " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date()) + ": " +
            parts.map { partNames[it] ?: it }.sorted().joinToString(", ")
    }

    /** When this device last got an answer from another one. */
    var lastMetMs by mutableLongStateOf(0L)
        private set

    /** What this device answers with when a paired device asks. Refreshed every few seconds while the app is open. */
    @Volatile var published: String = ""

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /** The secret key shared by paired devices; empty when this one isn't paired. */
    fun key(context: Context): String = prefs(context).getString("key", "").orEmpty()

    fun setKey(context: Context, key: String) {
        prefs(context).edit().putString("key", key).apply()
        changes++
    }

    /** This device's key, made now if it has none: called when it sends its set-up to another. */
    fun keyOrMake(context: Context): String {
        key(context).takeIf { it.isNotEmpty() }?.let { return it }
        val bytes = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }.also { setKey(context, it) }
    }

    fun pack(parts: Map<String, Part>): String = JsonObject().apply {
        parts.forEach { (name, part) -> add(name, JsonObject().apply { addProperty("at", part.at); addProperty("body", part.body) }) }
    }.toString()

    fun unpack(text: String): Map<String, Part>? = runCatching {
        JsonParser.parseString(text).asJsonObject.entrySet().associate { (name, v) -> name to Part(v.asJsonObject.get("at").asLong, v.asJsonObject.get("body").asString) }
    }.getOrNull()

    /**
     * The parts of [theirs] this device should take in place of its own: the ones changed more recently.
     * Parts this version doesn't know are left alone. When two differ but carry the same time (neither
     * has been changed since the devices were paired), the fuller one wins, the same way on both devices.
     */
    fun newer(mine: Map<String, Part>, theirs: Map<String, Part>): Map<String, Part> = theirs.filter { (name, t) ->
        val m = mine[name] ?: return@filter false
        t.body != m.body && (t.at > m.at || (t.at == m.at && (t.body.length > m.body.length || (t.body.length == m.body.length && t.body > m.body))))
    }

    private fun mark(body: String) = body.length.toString() + ":" + body.hashCode()

    /**
     * This device's parts, with their times. A part found different from last time has been changed
     * here since, and is given the time now. One never seen before is given the oldest time there is.
     */
    fun current(context: Context, bodies: Map<String, String>, now: Long = System.currentTimeMillis()): Map<String, Part> {
        val p = prefs(context)
        val edit = p.edit()
        val out = bodies.mapValues { (name, body) ->
            val seen = p.getString("mark.$name", null)
            val at = when (seen) {
                mark(body) -> p.getLong("at.$name", 1)
                null -> 1L
                else -> now
            }
            if (seen != mark(body)) edit.putString("mark.$name", mark(body)).putLong("at.$name", at)
            Part(at, body)
        }
        edit.apply()
        return out
    }

    /** Notes that a part was taken from another device, as it now stands here, with the time it carried there. */
    fun adopted(context: Context, name: String, body: String, at: Long) {
        prefs(context).edit().putString("mark.$name", mark(body)).putLong("at.$name", at).apply()
    }

    /**
     * Asks each paired device on the Wi-Fi what it has. The ones that answered last time are tried first;
     * if none answers, the network is looked over again, at most every five minutes. Call off the main thread.
     */
    fun pull(context: Context, key: String): List<Map<String, Part>> {
        val mine = PhoneSetup.localAddress() ?: return emptyList()
        val p = prefs(context)
        val known = p.getString("peers", "").orEmpty().split('\n').filter { it.isNotBlank() && it != mine }
        val ask: (String) -> Map<String, Part>? = { host -> SetUpTaker.ask(host, PORT, "/sync?key=$key", 2_000)?.takeIf { it.first == 200 }?.let { unpack(it.second) } }
        var got = known.mapNotNull(ask)
        val now = System.currentTimeMillis()
        if (got.isEmpty() && now - p.getLong("looked", 0) > 5 * 60_000) {
            val found = SetUpTaker.find(mine, PORT, HELLO).filter { it != mine }
            p.edit().putLong("looked", now).putString("peers", found.joinToString("\n")).apply()
            got = found.mapNotNull(ask)
        }
        if (got.isNotEmpty()) lastMetMs = now
        return got
    }
}

/** Answers paired devices that ask what this one has. Runs only while KueTV is on screen. */
class SyncServer(private val key: String) {
    @Volatile private var socket: ServerSocket? = null

    fun start(): Boolean {
        val server = runCatching { ServerSocket(Sync.PORT) }.getOrNull() ?: return false
        socket = server
        thread(isDaemon = true, name = "kuetv-in-step") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { answer(client) }; runCatching { client.close() } }
            }
        }
        return true
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
            path == "/hello" -> send(client, 200, Sync.HELLO)
            // Only a device holding the same key is answered.
            path == "/sync?key=$key" && Sync.published.isNotEmpty() -> send(client, 200, Sync.published)
            else -> send(client, 403, "no")
        }
    }

    private fun send(client: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(("HTTP/1.1 $status X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }
}
