package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One thing the app depends on, and how it did when it was last tried. */
data class Check(val name: String, val state: Int, val detail: String) {
    companion object {
        const val WORKING = 0
        /** Answering, but with nothing useful, or not set up. */
        const val DOUBT = 1
        const val DEAD = 2
    }
}

/**
 * The health check: tries every outside source the app depends on (the provider, its guide and film
 * details, the film index, each add-on, subtitles, Google Drive, the update download, free channel
 * lists) and says which are working. Run by hand from Settings, and quietly once a day.
 */
object Health {
    private const val SEP = "\u0001"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A well-known film and episode every stream and subtitle source should know: used only to ask "do you answer?". */
    private const val TEST_FILM = "tt0111161"
    private const val TEST_EPISODE = "tt0903747:1:1"

    var results by mutableStateOf<List<Check>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var lastRunMs by mutableStateOf(0L)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("health", Context.MODE_PRIVATE)

    private const val BREAK = "\u0002"

    fun encode(checks: List<Check>): String = checks.joinToString("\n") { listOf(it.state, it.name.replace(SEP, " ").replace("\n", " "), it.detail.replace(SEP, " ").replace("\n", BREAK)).joinToString(SEP) }

    fun decode(text: String): List<Check> = text.split('\n').mapNotNull { line ->
        val p = line.split(SEP, limit = 3)
        val state = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        if (p.size < 3) null else Check(p[1], state, p[2].replace(BREAK, "\n"))
    }

    /** "✓ ", "! " or "✗ " for one part of a check. */
    private fun mark(state: Int) = when (state) { Check.WORKING -> "✓ "; Check.DOUBT -> "! "; else -> "✗ " }

    /** "All 7 working", or "2 of 7 not working: the TV guide, Add-on: Subs". */
    fun summary(checks: List<Check>): String {
        if (checks.isEmpty()) return "Not checked yet"
        val dead = checks.filter { it.state == Check.DEAD }
        val doubt = checks.count { it.state == Check.DOUBT }
        return when {
            dead.isNotEmpty() -> dead.size.toString() + " of " + checks.size + " not working: " + dead.joinToString(", ") { it.name }
            doubt > 0 -> "Nothing dead. " + doubt + " of " + checks.size + " to look at"
            else -> "All " + checks.size + " working"
        }
    }

    /** Reads the last results kept on the device. */
    fun restore(context: Context) {
        if (results.isNotEmpty()) return
        results = decode(prefs(context).getString("results", "").orEmpty())
        lastRunMs = prefs(context).getLong("at", 0)
    }

    fun dueDaily(context: Context): Boolean = System.currentTimeMillis() - prefs(context).getLong("at", 0) > DAY_MS

    /** How the thing called [name] did last time; null when it wasn't checked. */
    fun stateOf(name: String): Int? = results.firstOrNull { it.name == name }?.state

    fun addOnName(addOn: AddOn) = "Add-on: " + addOn.name

    private val http by lazy { Provider.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    /** Asks [url] for an answer in the usual form. Gives the answer, or why there wasn't one. */
    private fun ask(url: String): Pair<JsonObject?, String> = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", Provider.USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) null to "It answered with an error (" + r.code + ")"
            else runCatching { JsonParser.parseString(r.body.string()).asJsonObject }.getOrNull()?.let { it to "" } ?: (null to "It answered, but not in the form expected")
        }
    } catch (e: java.io.InterruptedIOException) {
        null to "It didn't answer in 15 seconds"
    } catch (e: Exception) {
        null to "It couldn't be reached"
    }

    private fun JsonObject.count(name: String): Int = get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0

    /** Tries one add-on, part by part: its description, then each thing it gives. One line for each part. */
    private fun addOn(addOn: AddOn, lang: Lang): Check {
        val name = addOnName(addOn)
        val (manifest, why) = ask(addOn.base + "/manifest.json")
        if (manifest == null) return Check(name, Check.DEAD, mark(Check.DEAD) + "Its description: " + why.replaceFirstChar { it.lowercase() } + ". Nothing else in it can work.")
        val parts = ArrayList<Pair<Int, String>>()
        parts += Check.WORKING to "Its description: answers"
        if (addOn.streams) {
            val (film, filmWhy) = ask(addOn.base + "/stream/movie/" + TEST_FILM + ".json")
            val (episode, _) = ask(addOn.base + "/stream/series/" + TEST_EPISODE + ".json")
            val films = film?.count("streams") ?: 0
            val episodes = episode?.count("streams") ?: 0
            parts += when {
                film == null && episode == null -> Check.DEAD to "Streams: " + filmWhy.replaceFirstChar { it.lowercase() }
                films + episodes == 0 -> Check.DOUBT to "Streams: answers, but none for a well-known film or episode"
                else -> Check.WORKING to "Streams: $films for a test film, $episodes for a test episode"
            }
        }
        if (addOn.subtitles) {
            val (subs, subsWhy) = ask(addOn.base + "/subtitles/movie/" + TEST_FILM + ".json")
            val all = subs?.get("subtitles")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.orEmpty()
            val mine = all.count { Langs.matches(lang, it.get("lang")?.takeIf { l -> l.isJsonPrimitive }?.asString.orEmpty()) }
            parts += when {
                subs == null -> Check.DEAD to "Subtitles: " + subsWhy.replaceFirstChar { it.lowercase() }
                mine == 0 -> Check.DOUBT to "Subtitles: answers, but no " + lang.name + " ones for a test film (" + all.size + " in other languages)"
                else -> Check.WORKING to "Subtitles: $mine in " + lang.name + " for a test film"
            }
        }
        for (written in addOn.catalogues.take(3)) {
            val c = AddOns.catalogueOf(addOn, written) ?: continue
            val page = runCatching { AddOns.page(c, 0) }.getOrNull()
            parts += when {
                page == null -> Check.DEAD to "Catalogue \"" + c.name + "\": doesn't answer"
                page.isEmpty() -> Check.DOUBT to "Catalogue \"" + c.name + "\": empty"
                else -> Check.WORKING to "Catalogue \"" + c.name + "\": " + page.size + " titles"
            }
        }
        if (addOn.catalogues.size > 3) parts += Check.WORKING to (addOn.catalogues.size - 3).toString() + " more catalogues not tried"
        // The add-on as a whole is as bad as its worst part.
        return Check(name, parts.maxOf { it.first }, parts.joinToString("\n") { mark(it.first) + it.second })
    }

    /**
     * Checks only the add-ons (and the Stremio account), each result put in as soon as it is known, in place of
     * that add-on's last one. Call off the main thread.
     */
    fun runAddOns(context: Context) {
        if (running) return
        running = true
        try {
            val lang = Langs.of(Choices.subtitleLang(context))
            val put: (Check) -> Unit = { c -> results = results.filter { it.name != c.name } + c }
            stremio(context)?.let(put)
            for (a in AddOnStore.all(context)) put(addOn(a, lang))
            prefs(context).edit().putString("results", encode(results)).apply()
            Report.note("Health check", "add-ons: " + summary(results.filter { it.name.startsWith("Add-on: ") || it.name == "Stremio account" }))
        } finally {
            running = false
        }
    }

    /** The Stremio account, if signed in: does the sign-in still work, and is every usable add-on in KueTV? */
    private fun stremio(context: Context): Check? {
        val key = Stremio.key(context)
        if (key.isEmpty()) return null
        val name = "Stremio account"
        val entries = try {
            Stremio.collection(key)
        } catch (e: AddOnProblem) {
            return Check(name, Check.DEAD, mark(Check.DEAD) + e.message + " Sign in again in Settings › Films and series › Add-ons.")
        }
        val have = AddOnStore.all(context).mapTo(HashSet()) { it.base }
        val usable = entries.filter { it.addOn != null }
        val missing = usable.filter { it.addOn!!.base !in have }
        val lines = ArrayList<String>()
        lines += mark(Check.WORKING) + "Signed in as " + Stremio.email(context)
        lines += if (missing.isEmpty()) mark(Check.WORKING) + "All " + usable.size + " usable add-ons are in KueTV"
        else mark(Check.DOUBT) + "Not in KueTV yet: " + missing.joinToString(", ") { it.name } + ". Press \"Bring in my Stremio add-ons\"."
        entries.filter { it.addOn == null }.forEach { lines += mark(Check.DOUBT) + it.name + ": " + it.why }
        return Check(name, if (missing.isEmpty()) Check.WORKING else Check.DOUBT, lines.joinToString("\n"))
    }

    /**
     * Tries everything. Blocks while it does; call off the main thread. [library] is what the provider
     * last sent, used to pick a channel and a film to ask about.
     */
    fun run(context: Context, account: Account, library: Library?): List<Check> {
        running = true
        val out = ArrayList<Check>()
        try {
            val lang = Langs.of(Choices.subtitleLang(context))
            // The provider: signing in, then its guide and its film details.
            if (account.isSet) {
                val signIn = try {
                    Provider.reach(account)
                    Check("Your provider", Check.WORKING, "Signed in.")
                } catch (e: ProviderProblem) {
                    Check("Your provider", Check.DEAD, e.message.orEmpty())
                } catch (e: Exception) {
                    Check("Your provider", Check.DEAD, "Couldn't be reached.")
                }
                out += signIn
                if (signIn.state == Check.WORKING && account.isLogin && library != null) {
                    val tried = library.groups.take(3).map { it.versions[it.start].channel }
                    if (tried.isNotEmpty()) {
                        val guides = tried.map { c -> runCatching { Provider.guide(account, c.id) } }
                        out += when {
                            guides.all { it.isFailure } -> Check("The TV guide", Check.DEAD, "Your provider didn't answer when asked what is on.")
                            guides.all { it.getOrNull().isNullOrEmpty() } -> Check("The TV guide", Check.DOUBT, "Your provider answers, but sent no programmes for " + tried.joinToString(", ") { it.name } + ".")
                            else -> Check("The TV guide", Check.WORKING, guides.sumOf { it.getOrNull()?.size ?: 0 }.toString() + " programmes for " + tried.size + " test channels.")
                        }
                    }
                    library.films.firstOrNull()?.let { film ->
                        val about = runCatching { Provider.filmAbout(account, film.id) }
                        out += when {
                            about.isFailure -> Check("Film details from your provider", Check.DEAD, "It didn't answer when asked about a film.")
                            about.getOrNull()?.isEmpty != false -> Check("Film details from your provider", Check.DOUBT, "It answers, but sent nothing about \"" + film.name + "\".")
                            else -> Check("Film details from your provider", Check.WORKING, "Sent details for a test film.")
                        }
                    }
                }
            }
            // The public film index add-ons are asked through.
            val (index, indexWhy) = ask(AddOns.index + "/catalog/movie/top/search=the%20thing.json")
            out += when {
                index == null -> Check("The film index", Check.DEAD, "$indexWhy. Add-on streams can't be found without it.")
                index.count("metas") == 0 -> Check("The film index", Check.DOUBT, "It answers, but found nothing for a test search.")
                else -> Check("The film index", Check.WORKING, "Found " + index.count("metas") + " films for a test search.")
            }
            // The lists of films and series to browse, beyond the provider's own: a few of them are tried.
            val catalogues = runCatching { AddOns.catalogues(AddOnStore.all(context)) }.getOrDefault(emptyList())
            if (catalogues.isEmpty()) {
                out += Check("Film and series catalogues", Check.DOUBT, "None found. The film index listed none, and no add-on gives any.")
            } else {
                val tried = catalogues.take(6)
                val pages = tried.map { c -> c to runCatching { AddOns.page(c, 0) }.getOrNull() }
                val dead = pages.filter { it.second == null }.map { it.first.name }
                val empty = pages.filter { it.second?.isEmpty() == true }.map { it.first.name }
                out += when {
                    dead.size == tried.size -> Check("Film and series catalogues", Check.DEAD, "None of the " + tried.size + " tried answered.")
                    dead.isNotEmpty() -> Check("Film and series catalogues", Check.DOUBT, (tried.size - dead.size).toString() + " of " + tried.size + " tried answered. Not answering: " + dead.joinToString(", ") + ".")
                    empty.isNotEmpty() -> Check("Film and series catalogues", Check.DOUBT, "All " + tried.size + " tried answered, but these were empty: " + empty.joinToString(", ") + ".")
                    else -> Check("Film and series catalogues", Check.WORKING, catalogues.size.toString() + " catalogues. The " + tried.size + " tried all answered, " + pages.sumOf { it.second?.size ?: 0 } + " titles on their first pages.")
                }
            }
            stremio(context)?.let { out += it }
            for (a in AddOnStore.all(context)) out += addOn(a, lang)
            for (list in FreeStore.all(context)) {
                out += try {
                    val found = Provider.loadFree(list)
                    if (found.isEmpty()) Check("Free list: " + FreeStore.label(list), Check.DOUBT, "It answers, but holds no channels.")
                    else Check("Free list: " + FreeStore.label(list), Check.WORKING, found.size.toString() + " channels listed. Whether each one plays isn't tested.")
                } catch (e: Exception) {
                    Check("Free list: " + FreeStore.label(list), Check.DEAD, (e as? ProviderProblem)?.message ?: "It couldn't be reached.")
                }
            }
            // Google Drive, for recordings.
            val drive = Recordings.drive(context)
            out += when {
                !drive.isSetUp -> Check("Google Drive", Check.DOUBT, "Not set up in this copy of the app.")
                !drive.signedIn -> Check("Google Drive", Check.DOUBT, "Not signed in. Recordings stay on this device.")
                else -> runCatching { drive.list() }.fold(
                    { Check("Google Drive", Check.WORKING, "Signed in. " + it.size + " recordings there.") },
                    { Check("Google Drive", Check.DEAD, "The sign-in no longer works. Sign out and in again in Settings.") },
                )
            }
            out += runCatching { Updates.check() }.fold(
                { Check("App updates", Check.WORKING, "The download answers. Newest version: " + it.name + ".") },
                { Check("App updates", Check.DEAD, "The download couldn't be reached.") },
            )
            for (c in out) if (c.state == Check.DEAD) Report.note("Health check", "NOT WORKING: " + c.name + " · " + c.detail)
            Report.note("Health check", summary(out))
            val now = System.currentTimeMillis()
            prefs(context).edit().putString("results", encode(out)).putLong("at", now).apply()
            results = out
            lastRunMs = now
        } finally {
            running = false
        }
        return out
    }
}
