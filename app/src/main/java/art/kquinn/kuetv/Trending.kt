package art.kquinn.kuetv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What is trending on the big streaming services and the UK's own TV apps, for the banner across the top of
 * Home: a few titles from each, with a wide picture and a line about it. JustWatch's own figures, for the UK.
 * Kept on the device for six hours, so the banner shows straight away when KueTV opens.
 */
object Trending {
    /** A service: JustWatch's short name for it, its name, and how its app on the TV is recognised. */
    class Service(val code: String, val name: String, val names: List<String>, val packages: List<String> = emptyList())

    val services = listOf(
        Service("nfx", "Netflix", listOf("netflix"), listOf("netflix")),
        Service("amp", "Prime Video", listOf("prime video"), listOf("com.amazon.avod", "primevideo")),
        Service("dnp", "Disney+", listOf("disney"), listOf("disney")),
        Service("bbc", "BBC iPlayer", listOf("iplayer"), listOf("iplayer")),
        Service("itv", "ITVX", listOf("itvx", "itv hub"), listOf("com.itv.", "air.itv")),
        Service("al4", "Channel 4", listOf("channel 4", "all 4"), listOf("channel4")),
        Service("myf", "5", listOf("my5", "channel 5"), listOf("channel5", "my5", "demand5")),
        Service("ptv", "Pluto TV", listOf("pluto"), listOf("pluto")),
    )

    /** One slide: a title trending on a service. */
    data class Slide(val service: String, val title: String, val about: String, val picture: String, val poster: String, val show: Boolean)

    /** The slides, a service at a time in turn; empty until they have been fetched. */
    var slides by mutableStateOf<List<Slide>>(emptyList())
        private set

    /** For the automatic checks only. */
    internal fun setForChecks(list: List<Slide>) { slides = list }

    private const val KEEP_MS = 6 * 60 * 60_000L
    private const val PER_SERVICE = 3
    private const val IMAGES = "https://images.justwatch.com"

    private val http by lazy { Provider.http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    private fun file(context: Context) = File(context.filesDir, "trending.json")

    /** The kept slides at once, then fresh ones from the internet if the kept ones are more than six hours old. */
    suspend fun load(context: Context) {
        val f = file(context)
        val kept = withContext(Dispatchers.IO) { runCatching { parseKept(f.readText()) }.getOrNull() }
        if (kept != null && kept.second.isNotEmpty()) slides = kept.second
        if (kept != null && System.currentTimeMillis() - kept.first < KEEP_MS && kept.second.isNotEmpty()) return
        val fresh = withContext(Dispatchers.IO) { fetchAll() }
        if (fresh.isEmpty()) return
        slides = fresh
        withContext(Dispatchers.IO) { runCatching { f.writeText(keep(System.currentTimeMillis(), fresh)) } }
    }

    /** Each service's top shows and films, interleaved: Netflix, Prime Video, Disney+, … then round again. */
    private fun fetchAll(): List<Slide> {
        val each = services.map { s ->
            val shows = runCatching { fetch(s, "SHOW") }.onFailure { Report.note("Trending", s.name + ": " + it.message) }.getOrDefault(emptyList())
            val films = runCatching { fetch(s, "MOVIE") }.onFailure { Report.note("Trending", s.name + ": " + it.message) }.getOrDefault(emptyList())
            // Shows and films in turn, the most trending first.
            (0 until maxOf(shows.size, films.size)).flatMap { i -> listOfNotNull(shows.getOrNull(i), films.getOrNull(i)) }.take(PER_SERVICE)
        }
        return (0 until PER_SERVICE).flatMap { i -> each.mapNotNull { it.getOrNull(i) } }
    }

    private fun fetch(service: Service, type: String): List<Slide> {
        val body = JSONObject()
            .put("operationName", "GetPopularTitles")
            .put("variables", JSONObject()
                .put("popularTitlesSortBy", "TRENDING")
                .put("first", PER_SERVICE + 2)
                .put("sortRandomSeed", 0)
                .put("platform", "WEB")
                // The filter as JustWatch's own website sends it: every part present, most of them empty.
                .put("popularTitlesFilter", JSONObject()
                    .put("ageCertifications", JSONArray()).put("excludeGenres", JSONArray()).put("excludeProductionCountries", JSONArray())
                    .put("genres", JSONArray()).put("objectTypes", JSONArray().put(type)).put("productionCountries", JSONArray())
                    .put("packages", JSONArray().put(service.code)).put("excludeIrrelevantTitles", false)
                    .put("presentationTypes", JSONArray()).put("monetizationTypes", JSONArray()))
                .put("language", "en")
                .put("country", "GB"))
            .put("query", QUERY)
            .toString()
        val request = Request.Builder().url("https://apis.justwatch.com/graphql")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", Provider.USER_AGENT)
            .build()
        val text = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("answered " + r.code)
            r.body.string()
        }
        return parse(service.name, text)
    }

    /** The slides in JustWatch's answer: only titles with a wide picture, since the banner is built round it. */
    internal fun parse(serviceName: String, text: String): List<Slide> {
        val edges = JSONObject(text).optJSONObject("data")?.optJSONObject("popularTitles")?.optJSONArray("edges") ?: return emptyList()
        val out = ArrayList<Slide>()
        for (i in 0 until edges.length()) {
            val node = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            val c = node.optJSONObject("content") ?: continue
            val backdrop = c.optJSONArray("backdrops")?.optJSONObject(0)?.optString("backdropUrl").orEmpty()
            if (backdrop.isBlank()) continue
            out += Slide(
                serviceName,
                c.optString("title"),
                c.optString("shortDescription").takeUnless { it == "null" }.orEmpty(),
                image(backdrop, "s1440"),
                image(c.optString("posterUrl"), "s332"),
                node.optString("objectType") == "SHOW",
            )
        }
        return out
    }

    /** JustWatch gives "/backdrop/123/{profile}/name.{format}": a picture of the given size, as a JPEG. */
    internal fun image(path: String, size: String): String =
        if (path.isBlank() || path == "null") "" else IMAGES + path.replace("{profile}", size).replace("{format}", "jpg")

    private fun keep(at: Long, list: List<Slide>): String = JSONObject().put("at", at).put("slides", JSONArray().apply {
        list.forEach { s -> put(JSONObject().put("service", s.service).put("title", s.title).put("about", s.about).put("picture", s.picture).put("poster", s.poster).put("show", s.show)) }
    }).toString()

    private fun parseKept(text: String): Pair<Long, List<Slide>> {
        val o = JSONObject(text)
        val a = o.getJSONArray("slides")
        return o.getLong("at") to (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            Slide(s.getString("service"), s.getString("title"), s.optString("about"), s.getString("picture"), s.optString("poster"), s.optBoolean("show"))
        }
    }

    /** Opens the service's app on this TV; if it isn't on the TV, the app store on a search for it. */
    fun open(context: Context, serviceName: String) {
        val service = services.firstOrNull { it.name == serviceName } ?: return
        val pkg = DeviceApps.list(context).firstOrNull { a ->
            val n = a.name.lowercase()
            service.names.any { n.contains(it) } || service.packages.any { a.packageName.lowercase().contains(it) }
        }?.packageName
        if (pkg == null || !TvAppsOnDevice.open(context, pkg)) TvAppsOnDevice.openStore(context, service.name)
    }

    private const val QUERY = "query GetPopularTitles(\$country: Country!, \$popularTitlesFilter: TitleFilter, \$popularTitlesSortBy: PopularTitlesSorting! = POPULAR, " +
        "\$first: Int!, \$language: Language!, \$sortRandomSeed: Int! = 0) { popularTitles(country: \$country, filter: \$popularTitlesFilter, sortBy: \$popularTitlesSortBy, " +
        "first: \$first, sortRandomSeed: \$sortRandomSeed) { edges { node { objectType content(country: \$country, language: \$language) { title shortDescription " +
        "posterUrl backdrops { backdropUrl } } } } } }"
}

/** Every app on this device that can be opened, for the row of apps on Home. */
object DeviceApps {
    class App(val packageName: String, val name: String, val picture: Bitmap?)

    fun list(context: Context): List<App> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, App>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }.getOrDefault(emptyList())
            for (info in list) {
                val a = info.activityInfo ?: continue
                if (a.packageName == context.packageName || a.packageName in found) continue
                val name = runCatching { info.loadLabel(pm).toString() }.getOrDefault(a.packageName)
                // A TV app's wide banner where it has one, otherwise its icon.
                val picture = runCatching {
                    val d = a.loadBanner(pm) ?: a.applicationInfo.loadBanner(pm) ?: info.loadIcon(pm)
                    val wide = d.intrinsicWidth > d.intrinsicHeight
                    val w = if (wide) 320 else 120
                    val h = if (wide) 180 else 120
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, w, h); d.draw(Canvas(b)) }
                }.getOrNull()
                found[a.packageName] = App(a.packageName, name, picture)
            }
        }
        return found.values.sortedBy { it.name.lowercase() }
    }
}

/**
 * KueTV as the TV's Home button. It is on while KueTV's Home button watcher ([HomeGuard]) is switched on in the
 * TV's accessibility settings, which is done from a computer: Fire OS won't let anything replace Amazon's home screen.
 */
object Launcher {
    /** One part of the TV's own settings that opens on its own screen, so Back comes straight back to KueTV. */
    class TvSetting(val label: String, val action: String)

    private val parts = listOf(
        TvSetting("Network", android.provider.Settings.ACTION_WIFI_SETTINGS),
        TvSetting("Display", android.provider.Settings.ACTION_DISPLAY_SETTINGS),
        TvSetting("Sound", android.provider.Settings.ACTION_SOUND_SETTINGS),
        TvSetting("Apps", android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        TvSetting("Storage", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        TvSetting("Remotes and Bluetooth", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
        TvSetting("Accessibility", android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
        TvSetting("Date and time", android.provider.Settings.ACTION_DATE_SETTINGS),
        TvSetting("Language", android.provider.Settings.ACTION_LOCALE_SETTINGS),
        TvSetting("About this TV", android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS),
        TvSetting("Developer options", android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    /** The parts this TV really has: each must open a real settings screen, not Amazon's home screen or a stand-in. */
    fun tvSettings(context: Context): List<TvSetting> = parts.filter { target(context, it.action) != null }

    private fun target(context: Context, action: String): android.content.pm.ActivityInfo? {
        val a = runCatching { context.packageManager.resolveActivity(Intent(action), 0)?.activityInfo }.getOrNull() ?: return null
        if (a.packageName == HomeGuard.AMAZON_HOME || a.name.contains("Dummy", ignoreCase = true) || a.packageName == "android") return null
        return a
    }

    /** Opens one part of the TV's settings inside KueTV's own task, so Back returns to KueTV. */
    fun open(context: Context, setting: TvSetting): Boolean {
        val a = target(context, setting.action) ?: return false
        val intent = Intent(setting.action).setClassName(a.packageName, a.name)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isHome(context: Context): Boolean = runCatching {
        val on = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_accessibility_services").orEmpty()
        on.split(':').any { it.equals(context.packageName + "/" + HomeGuard::class.java.name, ignoreCase = true) || it.equals(context.packageName + "/.HomeGuard", ignoreCase = true) }
    }.getOrDefault(false)

    /**
     * The TV's own settings. On Fire TV they live inside Amazon's home screen (checked on Kevin's TV: the request
     * goes through a small Amazon part that opens Amazon's home screen on its settings), so the Home button watcher
     * leaves Amazon's home screen alone while they are open. Developer options are their own screen.
     */
    fun openTvSettings(context: Context, developer: Boolean = false): Boolean {
        val tries = if (developer) listOf(
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).setPackage("com.amazon.tv.settings.v2"),
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        ) else listOf(Intent(android.provider.Settings.ACTION_SETTINGS))
        if (!developer) HomeGuard.pause()
        for (intent in tries) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        HomeGuard.pausedUntil = 0
        Report.note("Home", "TV settings: nothing on this TV opened")
        return false
    }

    /** Amazon's Appstore, which shows its pages inside Amazon's home screen: left alone while it opens. */
    fun openAppstore(context: Context): Boolean {
        HomeGuard.pause()
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("amzn://apps/android?s=")).setPackage(HomeGuard.APPSTORE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) HomeGuard.pausedUntil = 0
        return ok
    }
}

/**
 * Live channels that open straight into their own app's live player, as Freely does. Each link was tried on
 * Kevin's Fire TV on 19 November 2026 and went straight to that channel playing live.
 */
object LiveInApps {
    class Ch(val name: String, val app: String, val pkg: String, val link: String)

    val all = listOf(
        Ch("ITV1", "ITVX", "air.ITVMobilePlayer", "https://www.itv.com/watch?channel=itv"),
        Ch("ITV2", "ITVX", "air.ITVMobilePlayer", "https://www.itv.com/watch?channel=itv2"),
        Ch("ITV3", "ITVX", "air.ITVMobilePlayer", "https://www.itv.com/watch?channel=itv3"),
        Ch("ITV4", "ITVX", "air.ITVMobilePlayer", "https://www.itv.com/watch?channel=itv4"),
        Ch("ITV Quiz", "ITVX", "air.ITVMobilePlayer", "https://www.itv.com/watch?channel=itvbe"),
        Ch("Channel 4", "Channel 4", "com.channel4.ondemand", "channel4://www.channel4.com/now/C4"),
        Ch("4seven", "Channel 4", "com.channel4.ondemand", "channel4://www.channel4.com/now/4S"),
        // E4 opened Channel 4's live player; More4 and Film4 opened it saying "off air" at 4am, which it only says for a real channel.
        Ch("E4", "Channel 4", "com.channel4.ondemand", "channel4://www.channel4.com/now/E4"),
        Ch("More4", "Channel 4", "com.channel4.ondemand", "channel4://www.channel4.com/now/M4"),
        Ch("Film4", "Channel 4", "com.channel4.ondemand", "channel4://www.channel4.com/now/F4"),
        // RTÉ Player's own Live page: its "Live Now" row of RTÉ One, RTÉ2, RTÉ News and RTÉ ISL. This is the only
        // link RTÉ Player answers for live TV; links naming one channel open its Box Sets page instead (all tried on
        // Kevin's TV, 20 November 2026). Shown everywhere: RTÉ lets some programmes play in the UK, and its app says
        // on screen when one can't.
        Ch("RTÉ Live", "RTÉ Player", "com.twentyfouri.tvbridge.rte", "https://www.rte.ie/rpng-static/ctv/androidTv/onnow"),
    )

    /**
     * The entry for a channel by its name in a provider's list ("ITV 1 HD", "UK: Channel 4", "E4 +1" is left out:
     * the apps only play the main channel). RTÉ's channels all open RTÉ Player's Live page, where the channel is picked.
     */
    fun forChannel(name: String): Ch? {
        val n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("^[a-z]{2,3}\\s*[:|]\\s*"), "").replace(Regex("\\b(hd|fhd|uhd|4k|sd|uk|london|\\(.*?\\))\\b"), " ")
            .replace(Regex("[^a-z0-9+]"), "")
        if (n.contains("+1") || n.contains("plus1")) return null
        val key = when (n) {
            "rteone", "rte1", "rteonehd", "rte2", "rtetwo", "rtenews", "rtenewsnow", "rtenewschannel", "rtejr", "rtejunior" -> "RTÉ Live"
            // UTV is ITV1 in Northern Ireland: ITVX plays the viewer's own region of ITV1.
            "itv", "itv1", "utv" -> "ITV1"
            "itv2" -> "ITV2"
            "itv3" -> "ITV3"
            "itv4" -> "ITV4"
            "itvquiz", "itvbe" -> "ITV Quiz"
            "channel4", "c4", "ch4" -> "Channel 4"
            "4seven" -> "4seven"
            "e4" -> "E4"
            "more4" -> "More4"
            "film4" -> "Film4"
            else -> return null
        }
        return all.firstOrNull { it.name == key }
    }

    /** Opens the channel in its app; if the app isn't on the TV, the app store on a search for it. */
    fun open(context: Context, ch: Ch) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ch.link)).setPackage(ch.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) TvAppsOnDevice.openStore(context, ch.app)
    }
}

/**
 * RTÉ Player catch-up: the series RTÉ One and RTÉ2 showed in the last 8 days, newest first, from RTÉ's own
 * programme feeds. Each opens its page in RTÉ Player, with Watch now and All Episodes: the series link was tried
 * on Kevin's TV on 20 November 2026 (Fair City, The Late Late Show, Prime Time). RTÉ marks each programme for
 * where it may play: "WW" anywhere, "IOI" the island of Ireland, "ROI" the Republic. Programmes marked for
 * Ireland don't open in England (RTÉ Player keeps loading), so they get a row of their own, not hidden.
 */
object RteCatchUp {
    class Show(val guid: String, val title: String, val image: String, val anywhere: Boolean)

    var list by mutableStateOf<List<Show>>(emptyList())
        private set
    private var loadedAt = 0L

    private const val FEEDS = "https://feed.entertainment.tv.theplatform.eu/f/1uC-gC/"
    private val http by lazy { Provider.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build() }

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", Provider.USER_AGENT).build()).execute()
            .use { r -> if (!r.isSuccessful) throw IllegalStateException("answered " + r.code); r.body.string() }

    /** Loads at most once an hour. */
    suspend fun load() {
        if (list.isNotEmpty() && System.currentTimeMillis() - loadedAt < 3_600_000L) return
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val schedule = get(FEEDS + "rte-prd-prd-all-schedules?form=json&schema=2.15&range=1-2&maxListings=1000&byCallSign=RTEONE%7CRTE2" +
                    "&byListingTime=" + (now - 8 * 86_400_000L) + "~" + now +
                    "&fields=plchannelschedule\$listings.pllisting\$startTime,plchannelschedule\$listings.pllisting\$program.plprogram\$seriesId")
                val order = recent(schedule, now)
                val shows = HashMap<String, Show>()
                order.chunked(60).forEach { some ->
                    val text = get(FEEDS + "rte-prd-prd-all-movies-series?form=json&schema=2.15&byGuid=" + some.joinToString("%7C") +
                        "&fields=guid,title,rte\$defaultThumbnail,plprogram\$programType,plprogramavailability\$media.plmedia\$availabilityTags")
                    shows.putAll(details(text))
                }
                order.mapNotNull { shows[it] }
            }.onFailure { Report.note("RTÉ catch-up", it.message.orEmpty()) }.getOrDefault(emptyList())
        }
        if (fresh.isNotEmpty()) { list = fresh; loadedAt = System.currentTimeMillis() }
    }

    /** The series in the schedule, the one shown last first; programmes yet to start are left out. */
    internal fun recent(text: String, now: Long): List<String> {
        val last = HashMap<String, Long>()
        val entries = JSONObject(text).optJSONArray("entries") ?: return emptyList()
        for (i in 0 until entries.length()) {
            val listings = entries.optJSONObject(i)?.optJSONArray("plchannelschedule\$listings") ?: continue
            for (j in 0 until listings.length()) {
                val l = listings.optJSONObject(j) ?: continue
                val start = l.optLong("pllisting\$startTime")
                val series = l.optJSONObject("pllisting\$program")?.optString("plprogram\$seriesId").orEmpty().substringAfterLast('/')
                if (series.isBlank() || start > now) continue
                if (start > (last[series] ?: 0L)) last[series] = start
            }
        }
        return last.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** Series with episodes to watch, by guid. The weather and the Angelus are left out. */
    internal fun details(text: String): Map<String, Show> {
        val out = HashMap<String, Show>()
        val entries = JSONObject(text).optJSONArray("entries") ?: return out
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val guid = e.optString("guid")
            val title = e.optString("title")
            if (guid.isBlank() || e.optString("plprogram\$programType") != "series") continue
            if (title.startsWith("Weather") || title.contains("Weather (") || title == "The Angelus") continue
            val places = HashSet<String>()
            val media = e.optJSONArray("plprogramavailability\$media")
            for (j in 0 until (media?.length() ?: 0)) {
                val tags = media!!.optJSONObject(j)?.optJSONArray("plmedia\$availabilityTags") ?: continue
                for (k in 0 until tags.length()) places += tags.optString(k)
            }
            if (places.none { it == "WW" || it == "IOI" || it == "ROI" }) continue
            val old = out[guid]
            val show = Show(guid, title, e.optString("rte\$defaultThumbnail"), "WW" in places)
            if (old == null || (show.anywhere && !old.anywhere)) out[guid] = show
        }
        return out
    }

    fun link(show: Show): String {
        val slug = java.text.Normalizer.normalize(show.title, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "x" }
        return "https://www.rte.ie/rpng-static/ctv/androidTv/series/" + slug + "/" + show.guid
    }

    fun open(context: Context, show: Show) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link(show))).setPackage("com.twentyfouri.tvbridge.rte").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) TvAppsOnDevice.openStore(context, "RTÉ Player")
    }
}

/** Pluto TV's live channels in the UK, from Pluto's own list; each opens straight into Pluto TV playing it. */
object PlutoChannels {
    class Ch(val number: Int, val name: String, val slug: String, val logo: String)

    var list by mutableStateOf<List<Ch>>(emptyList())
        private set

    private val http by lazy { Provider.http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    suspend fun load() {
        if (list.isNotEmpty()) return
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                val text = http.newCall(Request.Builder().url("https://api.pluto.tv/v2/channels.json").header("User-Agent", Provider.USER_AGENT).build())
                    .execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("answered " + r.code); r.body.string() }
                parse(text)
            }.onFailure { Report.note("Pluto", it.message.orEmpty()) }.getOrDefault(emptyList())
        }
        if (fresh.isNotEmpty()) list = fresh
    }

    /** Pluto's list: the numbered channels in number order, leaving out the ones made for other makers' TVs. */
    internal fun parse(text: String): List<Ch> {
        val a = JSONArray(text)
        val out = ArrayList<Ch>()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            val number = c.optInt("number")
            val slug = c.optString("slug")
            val category = c.optString("category")
            if (number <= 0 || slug.isBlank() || category == "Samsung" || category == "Xiaomi TV") continue
            out += Ch(number, c.optString("name"), slug, c.optJSONObject("colorLogoPNG")?.optString("path").orEmpty())
        }
        return out.sortedBy { it.number }
    }

    fun open(context: Context, ch: Ch) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://pluto.tv/uk/live-tv/" + ch.slug)).setPackage("tv.pluto.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) TvAppsOnDevice.openStore(context, "Pluto TV")
    }
}

/**
 * Which row of apps on Home an app belongs in, from its name and package: so an app installed later
 * (YouTube, Spotify, Virgin Media Player) lands in the right row by itself.
 */
object AppKinds {
    private val tv = listOf("iplayer", "itv", "channel4", "channel 4", "demand5", "my5", "channel5", "rte", "rté", "virgin", "uktv", "pluto",
        "stv", "freely", "tg4", "s4c", "opapp", "sky news", "gb news", "tvbridge", "live tv")
    private val films = listOf("netflix", "prime video", "avod", "firebat", "disney", "youtube", "smarttube", "now tv", "nowtv", "paramount",
        "apple tv", "britbox", "stremio", "plex", "jellyfin", "emby", "kodi", "tubi", "rakuten", "mubi", "hayu", "discovery+", "crunchyroll")
    private val music = listOf("spotify", "music", "tidal", "deezer", "soundcloud", "audible", "tunein", "radio", "podcast")

    fun of(app: DeviceApps.App): String {
        val n = (app.name + " " + app.packageName).lowercase()
        return when {
            music.any { n.contains(it) } && !n.contains("youtube") -> "music"
            tv.any { n.contains(it) } -> "tv"
            films.any { n.contains(it) } -> "films"
            else -> "other"
        }
    }
}
