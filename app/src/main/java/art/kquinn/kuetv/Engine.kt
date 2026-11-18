package art.kquinn.kuetv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

// ------------------------------------------------------------------------------------------ sound

/** Choices for the sound: surround passed to the TV or amplifier, or stereo only; and night mode. */
object Sound {
    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    /** Stereo only: Dolby and DTS are turned into two channels here, instead of being passed on as they are. */
    fun stereoOnly(context: Context): Boolean = prefs(context).getBoolean("stereoOnly", false)
    fun stereoOnly(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("stereoOnly", on).apply()
        Choices.touch()
    }

    /** Night mode: loud bangs made quieter and quiet speech louder. */
    fun night(context: Context): Boolean = prefs(context).getBoolean("night", false)
    fun night(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("night", on).apply()
        Choices.touch()
    }
}

/**
 * Night mode's sound: a compressor. Anything louder than a set level is brought down (four to one),
 * then everything is lifted, and nothing is let go past just under full. Works on ordinary sound
 * (16-bit); does nothing while night mode is off.
 */
@OptIn(UnstableApi::class)
class NightProcessor(private val wanted: () -> Boolean) : BaseAudioProcessor() {
    private var level = 0f
    private var attack = 0f
    private var release = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!wanted() || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        val rate = inputAudioFormat.sampleRate.coerceAtLeast(1).toFloat()
        // Reacts to a bang in 5 ms; lets go over 250 ms, so speech after it doesn't pump.
        attack = Math.exp(-1.0 / (0.005 * rate)).toFloat()
        release = Math.exp(-1.0 / (0.25 * rate)).toFloat()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytes = inputBuffer.remaining()
        val out = replaceOutputBuffer(bytes)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frame = ShortArray(channels)
        while (input.remaining() >= channels * 2) {
            var peak = 0f
            for (c in 0 until channels) {
                frame[c] = input.short
                peak = maxOf(peak, abs(frame[c].toFloat()) / 32768f)
            }
            level = if (peak > level) attack * level + (1 - attack) * peak else release * level + (1 - release) * peak
            val gain = gainFor(level)
            for (c in 0 until channels) {
                out.putShort((frame[c] * gain).coerceIn(-32768f, 32767f).toInt().toShort())
            }
        }
        // A part frame left over (never in practice) goes through as it is.
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }

    override fun onFlush() {
        level = 0f
    }

    override fun onReset() {
        level = 0f
    }

    companion object {
        private const val THRESHOLD = 0.1f
        private const val RATIO = 4f
        private const val LIFT = 2f
        private const val CEILING = 0.95f

        /** How much to multiply the sound by when it is at [level] (0 silence, 1 full). */
        fun gainFor(level: Float): Float {
            if (level <= 0.0001f) return LIFT
            val squeezed = if (level <= THRESHOLD) level else THRESHOLD * (level / THRESHOLD).pow(1f / RATIO)
            val gain = squeezed * LIFT / level
            return minOf(gain, CEILING / level)
        }
    }
}

// ---------------------------------------------------------------------------------------- picture

/** The most detail the player picks from streams that come in several sizes. Less saves the connection. */
object PictureQuality {
    val names = listOf("Best it can", "Up to 1080p", "Up to 720p", "Up to 480p")
    private val sizes = listOf(Int.MAX_VALUE to Int.MAX_VALUE, 1920 to 1080, 1280 to 720, 854 to 480)
    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    fun get(context: Context): Int = prefs(context).getInt("maxQuality", 0).coerceIn(0, names.size - 1)
    fun set(context: Context, choice: Int) {
        prefs(context).edit().putInt("maxQuality", choice).apply()
        Choices.touch()
    }

    fun size(choice: Int): Pair<Int, Int> = sizes[choice.coerceIn(0, sizes.size - 1)]
}

/** Switching the TV to the film's own frame rate (24, 25, 50 or 60 a second), so movement doesn't judder. */
object FrameRate {
    /** A way the screen can run: its number, size and how many times a second it changes. */
    data class Mode(val id: Int, val width: Int, val height: Int, val rate: Float)

    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    fun on(context: Context): Boolean = prefs(context).getBoolean("matchRate", false)
    fun on(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("matchRate", on).apply()
        Choices.touch()
    }

    /**
     * The screen mode to ask for, for a picture at [fps] frames a second: one the same size as now, whose
     * rate is the frame rate or a whole number of times it. Film (under 24.5) prefers its own rate, so 24
     * a second plays at 24; others prefer 48 or more, so 25 plays at 50. Null when none fits, or when
     * [current] already does.
     */
    fun pick(current: Mode, modes: List<Mode>, fps: Float): Mode? {
        if (fps <= 1f) return null
        fun times(m: Mode): Int? {
            for (k in 1..5) if (abs(m.rate / k - fps) <= fps * 0.0006f) return k
            return null
        }
        val fits = modes.filter { it.width == current.width && it.height == current.height }
            .mapNotNull { m -> times(m)?.let { k -> m to k } }
        if (fits.isEmpty()) return null
        val best = fits.minWith(
            compareBy<Pair<Mode, Int>> { (m, k) -> if (fps < 24.5f) k else if (m.rate >= 47f) 0 else 1 }.thenBy { it.second },
        ).first
        // Already on a rate that suits it: no switch, so no blank screen while the TV changes over.
        if (times(current) != null) return null
        return best
    }

    /** Asks the screen for the mode that suits [fps]. */
    fun match(context: Context, fps: Float) {
        if (!on(context) || fps <= 0f) return
        val activity = activityOf(context) ?: return
        val display = runCatching { activity.windowManager.defaultDisplay }.getOrNull() ?: return
        val now = display.mode
        val modes = display.supportedModes.map { Mode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val want = pick(Mode(now.modeId, now.physicalWidth, now.physicalHeight, now.refreshRate), modes, fps) ?: return
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId == want.id) return
        attrs.preferredDisplayModeId = want.id
        activity.window.attributes = attrs
        Report.note("Frame rate", "picture at " + fps + " a second; screen asked for " + want.rate + " a second")
    }

    /** Lets the screen go back to its usual mode, once nothing is playing. */
    fun release(context: Context) {
        val activity = activityOf(context) ?: return
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId == 0) return
        attrs.preferredDisplayModeId = 0
        activity.window.attributes = attrs
    }

    private fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

// ------------------------------------------------------------------------------------------- DNS

/**
 * Which DNS server turns names (like a provider's address) into numbers. The internet's own can be
 * set to block some providers; a chosen one asks over an encrypted link instead. If the chosen one
 * can't be reached, the internet's own is used.
 */
object KueDns : Dns {
    val names = listOf("Your internet's own", "Cloudflare (1.1.1.1)", "Google (8.8.8.8)", "Quad9 (9.9.9.9)")
    private val urls = listOf("", "https://cloudflare-dns.com/dns-query", "https://dns.google/dns-query", "https://dns.quad9.net/dns-query")
    private val boots = listOf(emptyList(), listOf("1.1.1.1", "1.0.0.1"), listOf("8.8.8.8", "8.8.4.4"), listOf("9.9.9.9", "149.112.112.112"))

    @Volatile
    var chosen = 0
        private set

    @Volatile
    private var doh: Dns? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("choices", Context.MODE_PRIVATE)

    fun load(context: Context) = use(prefs(context).getInt("dns", 0))

    fun set(context: Context, choice: Int) {
        prefs(context).edit().putInt("dns", choice).apply()
        use(choice)
        Choices.touch()
    }

    private fun use(choice: Int) {
        val c = choice.coerceIn(0, names.size - 1)
        chosen = c
        doh = if (c == 0) null else DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build())
            .url(urls[c].toHttpUrl())
            .bootstrapDnsHosts(boots[c].map { InetAddress.getByName(it) })
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val own = doh ?: return Dns.SYSTEM.lookup(hostname)
        return runCatching { own.lookup(hostname) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Dns.SYSTEM.lookup(hostname)
    }

    /** What the player fetches with: through the chosen DNS server when there is one. */
    @OptIn(UnstableApi::class)
    fun playerHttp(): HttpDataSource.Factory = if (chosen == 0) {
        DefaultHttpDataSource.Factory()
            .setUserAgent(Provider.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
    } else {
        OkHttpDataSource.Factory(Provider.http.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build())
            .setUserAgent(Provider.USER_AGENT)
    }
}

// ------------------------------------------------------------------------------------------- DRM

/**
 * Protected channels: a playlist can say a channel needs a key to unlock it (Widevine or ClearKey), in
 * "#KODIPROP" lines before its address. The key is noted against the address, and the player asks
 * here for it.
 */
object Drm {
    /** A channel's lock: "widevine" or "clearkey", and its key (a licence server's address, or "kid:key" pairs). */
    data class Lock(val type: String, val key: String) {
        val text: String get() = "$type|$key"

        companion object {
            fun of(text: String?): Lock? {
                if (text.isNullOrBlank()) return null
                val type = text.substringBefore('|')
                val key = text.substringAfter('|', "")
                return if (type.isBlank() || key.isBlank()) null else Lock(type, key)
            }
        }
    }

    private val locks = ConcurrentHashMap<String, Lock>()

    fun note(url: String, lock: Lock) {
        locks[url] = lock
    }

    fun of(url: String?): Lock? = url?.let { locks[it] }

    /** "com.widevine.alpha" and "org.w3.clearkey" as the playlist writes them, in the app's words. */
    fun typeOf(said: String): String? {
        val s = said.lowercase()
        return when {
            "widevine" in s -> "widevine"
            "clearkey" in s -> "clearkey"
            else -> null
        }
    }

    /**
     * ClearKey pairs as "kid:key" in hex (several split by commas), turned into the JSON the player's
     * ClearKey system takes. Null when [key] isn't pairs (it is then a licence server's address, or JSON already).
     */
    fun clearKeyJson(key: String): String? {
        val k = key.trim()
        if (k.startsWith("{")) return k
        if (k.startsWith("http", ignoreCase = true)) return null
        val pairs = k.split(',').map { it.trim() }.filter { ':' in it }
        if (pairs.isEmpty()) return null
        val parts = ArrayList<String>()
        for (pair in pairs) {
            val kid = hexToB64(pair.substringBefore(':')) ?: return null
            val value = hexToB64(pair.substringAfter(':')) ?: return null
            parts += "{\"kty\":\"oct\",\"k\":\"$value\",\"kid\":\"$kid\"}"
        }
        val keys = parts.joinToString(",")
        return "{\"keys\":[$keys],\"type\":\"temporary\"}"
    }

    private fun hexToB64(hex: String): String? {
        val h = hex.trim().replace("-", "")
        if (h.isEmpty() || h.length % 2 != 0 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val bytes = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    /** The headers after a licence address, as written "address|Header=value&Other=value". */
    fun licenceParts(key: String): Pair<String, Map<String, String>> {
        val address = key.substringBefore('|')
        val headers = key.substringAfter('|', "").substringBefore('|').split('&')
            .mapNotNull { h -> h.split('=', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0].trim() to URLDecoder.decode(it[1], "UTF-8") } }
            .toMap()
        return address to headers
    }

    /** For the player: unlocks each channel with its own key, and plays every other one as usual. */
    @OptIn(UnstableApi::class)
    fun provider(http: HttpDataSource.Factory): DrmSessionManagerProvider = DrmSessionManagerProvider { item ->
        val lock = of(item.localConfiguration?.uri?.toString()) ?: return@DrmSessionManagerProvider DrmSessionManager.DRM_UNSUPPORTED
        runCatching { manager(lock, http) }.getOrElse {
            Report.note("Protected channel", "couldn't set up its key: " + it.javaClass.simpleName)
            DrmSessionManager.DRM_UNSUPPORTED
        }
    }

    @OptIn(UnstableApi::class)
    private fun manager(lock: Lock, http: HttpDataSource.Factory): DrmSessionManager {
        val clear = lock.type == "clearkey"
        val uuid = if (clear) C.CLEARKEY_UUID else C.WIDEVINE_UUID
        val local = if (clear) clearKeyJson(lock.key) else null
        val callback = if (local != null) {
            LocalMediaDrmCallback(local.toByteArray())
        } else {
            val (address, headers) = licenceParts(lock.key)
            HttpMediaDrmCallback(address, http).apply { headers.forEach { (k, v) -> setKeyRequestProperty(k, v) } }
        }
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(callback)
    }
}

// --------------------------------------------------------------------------------------- Stalker

/**
 * Stalker (MAG) portals: a provider reached by a portal address and a MAC address instead of a
 * username and password. Kept as the playlist "stalker:<portal>|<MAC>"; its channels play from
 * "kuestalker:" addresses that are swapped for a fresh link from the portal as each one starts.
 * Live TV only.
 */
object Stalker {
    const val PREFIX = "stalker:"
    private const val LINK = "kuestalker:"
    private val macPattern = Regex("(?i)([0-9a-f]{2}(?:[:-][0-9a-f]{2}){5})")
    private const val AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

    /** A MAC address in what was typed, as 00:1A:79:AB:CD:EF; null when there is none. */
    fun macIn(text: String): String? = macPattern.find(text)?.groupValues?.get(1)?.replace('-', ':')?.uppercase()

    /**
     * What a portal and a MAC typed in the set-up boxes are kept as. The MAC can be in the username box,
     * or typed after the address in the same box. Null when no MAC is given.
     */
    fun spec(addressText: String, macText: String): String? {
        val mac = macIn(macText) ?: macIn(addressText) ?: return null
        val address = addressText.replace(macPattern, "").trim().trimEnd('|', ',', ' ')
        if (address.toHttpUrlOrNull() == null) return null
        return "$PREFIX$address|$mac"
    }

    fun isSpec(playlist: String): Boolean = playlist.startsWith(PREFIX)
    fun isLink(url: String): Boolean = url.startsWith(LINK)
    private fun portalOf(spec: String) = spec.removePrefix(PREFIX).substringBeforeLast('|')
    private fun macOf(spec: String) = spec.substringAfterLast('|')

    private class Session(val api: String, val token: String)

    private val sessions = ConcurrentHashMap<String, Session>()

    /** The addresses a portal's questions can go to, most likely first. */
    fun apis(portal: String): List<String> {
        val url = portal.trim().toHttpUrlOrNull() ?: throw ProviderProblem("That portal address isn't a web address.")
        val root = url.scheme + "://" + url.host + (if (url.port != okhttp3.HttpUrl.defaultPort(url.scheme)) ":" + url.port else "")
        val path = url.encodedPath
        if (path.endsWith(".php")) return listOf(url.newBuilder().query(null).build().toString())
        val stalker = root + "/stalker_portal/server/load.php"
        return if ("stalker_portal" in path) listOf(stalker) else listOf("$root/portal.php", stalker, "$root/c/portal.php")
    }

    private fun ask(spec: String, api: String, token: String, query: Map<String, String>): JsonObject {
        val b = api.toHttpUrl().newBuilder()
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        b.addQueryParameter("JsHttpRequest", "1-xml")
        val portal = portalOf(spec).toHttpUrlOrNull()
        val referer = portal?.let { it.scheme + "://" + it.host + ":" + it.port + "/c/" }.orEmpty()
        val request = Request.Builder().url(b.build())
            .header("User-Agent", AGENT)
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=" + macOf(spec) + "; stb_lang=en; timezone=Europe/London")
            .apply { if (referer.isNotEmpty()) header("Referer", referer) }
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .build()
        val response = try {
            Provider.http.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw ProviderProblem("Couldn't reach the portal. Check its address and the internet connection.")
        }
        response.use {
            if (!it.isSuccessful) throw ProviderProblem("The portal answered with an error (" + it.code + ").")
            val text = it.body.string()
            return runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: throw ProviderProblem("The portal's answer couldn't be read. Check it is a MAG/Stalker portal address.")
        }
    }

    /** Signs in to the portal with the MAC and keeps the pass it gives. */
    private fun session(spec: String, fresh: Boolean = false): Session {
        if (!fresh) sessions[spec]?.let { return it }
        var last: ProviderProblem? = null
        for (api in apis(portalOf(spec))) {
            try {
                val js = ask(spec, api, "", mapOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "0")).get("js")
                val token = js?.takeIf { it.isJsonObject }?.asJsonObject?.get("token")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                if (token.isEmpty()) {
                    last = ProviderProblem("The portal didn't accept that MAC address.")
                    continue
                }
                // Most portals only let a box in once it has said who it is.
                runCatching { ask(spec, api, token, mapOf("type" to "stb", "action" to "get_profile", "hd" to "1", "auth_second_step" to "1", "stb_type" to "MAG250")) }
                return Session(api, token).also { sessions[spec] = it }
            } catch (e: ProviderProblem) {
                // A refused MAC says more than a later address that isn't there.
                if (last == null) last = e
            }
        }
        throw last ?: ProviderProblem("Couldn't reach the portal.")
    }

    /** The portal's live channels and their categories. Call off the main thread. */
    fun load(spec: String): Library {
        val s = session(spec, fresh = true)
        val genres = ask(spec, s.api, s.token, mapOf("type" to "itv", "action" to "get_genres")).get("js")
            ?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { g -> g.asJsonObject.let { o -> o.str("id") to o.str("title") } }
            ?.filter { (id, name) -> id.isNotEmpty() && id != "*" && name.isNotEmpty() }.orEmpty()
        val data = ask(spec, s.api, s.token, mapOf("type" to "itv", "action" to "get_all_channels")).get("js")
            ?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw ProviderProblem("The portal listed no channels for that MAC address.")
        val channels = data.mapNotNull { e ->
            val o = e.asJsonObject
            val cmd = o.str("cmd")
            if (cmd.isEmpty()) return@mapNotNull null
            val logo = o.str("logo").takeIf { it.startsWith("http") }.orEmpty()
            Channel(o.str("id"), o.str("name"), logo, o.str("tv_genre_id").ifEmpty { "other" }, link(spec, cmd), 0)
        }
        if (channels.isEmpty()) throw ProviderProblem("The portal listed no channels for that MAC address.")
        val used = channels.map { it.categoryId }.toSet()
        val categories = genres.filter { it.first in used }.map { Category(it.first, it.second) } +
            used.filter { id -> genres.none { it.first == id } }.map { Category(it, if (it == "other") "Other" else it) }
        return Library(categories, channels, emptyList(), emptyList())
    }

    /** Checks the portal answers and lets that MAC in. */
    fun reach(spec: String) {
        session(spec, fresh = true)
    }

    private fun link(spec: String, cmd: String) = LINK + URLEncoder.encode(spec, "UTF-8") + "/" + URLEncoder.encode(cmd, "UTF-8")

    /** The address a "kuestalker:" channel plays from now: a fresh link from the portal. Call off the main thread. */
    fun resolve(url: String): String {
        val rest = url.removePrefix(LINK)
        val spec = URLDecoder.decode(rest.substringBefore('/'), "UTF-8")
        val cmd = URLDecoder.decode(rest.substringAfter('/'), "UTF-8")
        for (fresh in listOf(false, true)) {
            val got = runCatching {
                val s = session(spec, fresh)
                ask(spec, s.api, s.token, mapOf("type" to "itv", "action" to "create_link", "cmd" to cmd, "series" to "", "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0"))
                    .get("js")?.takeIf { it.isJsonObject }?.asJsonObject?.str("cmd")
            }.getOrNull()
            val address = got?.let { addressIn(it) }
            if (!address.isNullOrEmpty()) return address
        }
        // The portal gave nothing back: the channel's own command often holds a working address already.
        return addressIn(cmd) ?: throw ProviderProblem("The portal didn't give a link for this channel.")
    }

    /** "ffmpeg http://…" or "ffrt http://…" as portals write it: the address part. */
    fun addressIn(cmd: String): String? = cmd.trim().split(' ').map { it.trim() }.firstOrNull { it.startsWith("http", ignoreCase = true) || it.startsWith("rtmp") || it.startsWith("udp") }

    private fun JsonObject.str(name: String): String = get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.trim().orEmpty()
}

// ------------------------------------------------------------------------------- small players

/** A player for a small picture (the small TV, multiview): the lightest set-up, with the chosen DNS and channel keys. */
@OptIn(UnstableApi::class)
fun lightPlayer(context: Context, url: String): androidx.media3.exoplayer.ExoPlayer {
    val http = KueDns.playerHttp()
    return androidx.media3.exoplayer.ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http))
                .setDrmSessionManagerProvider(Drm.provider(http)),
        )
        .build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setPreferredAudioLanguage("en").build()
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            playWhenReady = true
        }
}

/** Where [url] plays from: itself, or for a portal's channel the fresh link the portal gives (null until it has). */
@androidx.compose.runtime.Composable
fun resolvedAddress(url: String): String? {
    if (!Stalker.isLink(url)) return url
    val got = androidx.compose.runtime.produceState<String?>(null, url) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Stalker.resolve(url) }.getOrNull() }
    }
    return got.value
}
