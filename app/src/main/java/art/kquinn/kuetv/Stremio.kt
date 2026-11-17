package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The user's Stremio account. Signing in once brings every add-on installed in Stremio into KueTV.
 * The password isn't kept: only the key Stremio gives back, which lets KueTV ask for the add-ons again.
 */
object Stremio {
    private const val API = "https://api.strem.io/api/"

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("stremio", Context.MODE_PRIVATE)

    fun key(context: Context): String = prefs(context).getString("key", "").orEmpty()
    fun email(context: Context): String = prefs(context).getString("email", "").orEmpty()

    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
        changes++
    }

    private val http by lazy { Provider.http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    /** Asks Stremio's own service. Gives the "result" part of the answer, or throws an [AddOnProblem] saying why not. */
    private fun ask(method: String, body: JsonObject): JsonObject {
        val request = Request.Builder().url(API + method).header("User-Agent", Provider.USER_AGENT)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val answer = try {
            http.newCall(request).execute().use { r -> JsonParser.parseString(r.body.string()).asJsonObject }
        } catch (e: Exception) {
            Report.note("Stremio", method + " couldn't be reached: " + e.javaClass.simpleName + " · " + e.message)
            throw AddOnProblem("Couldn't reach Stremio. Check the internet and try again.")
        }
        answer.get("result")?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val said = answer.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        Report.note("Stremio", method + " refused: " + said.ifBlank { "no reason given" })
        throw AddOnProblem("Stremio said: \"" + said.ifBlank { "no reason given" } + "\"" + if (method == "login") ". Check the email and password." else ".")
    }

    /** Signs in, and keeps the key Stremio gives back. Call off the main thread. */
    fun signIn(context: Context, email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) throw AddOnProblem("Type your Stremio email and password first.")
        val result = ask("login", JsonObject().apply { addProperty("type", "Login"); addProperty("email", email.trim()); addProperty("password", password); addProperty("facebook", false) })
        val key = result.get("authKey")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (key.isEmpty()) throw AddOnProblem("Stremio signed you in but gave no key back.")
        prefs(context).edit().putString("key", key).putString("email", email.trim()).apply()
        changes++
    }

    /** One add-on in the Stremio account: its name, its link, and what KueTV makes of it (null when it can't use it, and why). */
    data class Entry(val name: String, val link: String, val addOn: AddOn?, val why: String)

    /** The add-ons in the Stremio account exactly as Stremio sends them, to send back with only one change made. */
    private fun raw(key: String): com.google.gson.JsonArray {
        val result = ask("addonCollectionGet", JsonObject().apply { addProperty("type", "AddonCollectionGet"); addProperty("authKey", key); addProperty("update", true) })
        return result.get("addons")?.takeIf { it.isJsonArray }?.asJsonArray ?: com.google.gson.JsonArray()
    }

    /** Every add-on installed in the Stremio account. Call off the main thread; throws an [AddOnProblem]. */
    fun collection(key: String): List<Entry> = raw(key).mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.map { d -> entry(d) }

    /** An add-on's link with "manifest.json" and anything after "?" taken off: how KueTV knows an add-on. */
    fun baseOf(link: String): String = link.substringBefore('?').removeSuffix("/manifest.json").removeSuffix("/")

    private fun linkOf(d: JsonObject) = d.get("transportUrl")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun isProtected(d: JsonObject) = d.get("flags")?.takeIf { it.isJsonObject }?.asJsonObject?.get("protected")?.let { runCatching { it.asBoolean }.getOrDefault(false) } == true

    /** What removing [base] from the account's list does: the list to send back, untouched except for it, and what came out. */
    class Removal(val kept: com.google.gson.JsonArray, val removed: List<JsonObject>, val refused: List<JsonObject>)

    /** Works out a removal, without sending anything. Stremio's own built-in add-ons ("protected") are never taken out. */
    fun removal(list: com.google.gson.JsonArray, base: String): Removal {
        val kept = com.google.gson.JsonArray()
        val removed = ArrayList<JsonObject>()
        val refused = ArrayList<JsonObject>()
        for (item in list) {
            val d = runCatching { item.asJsonObject }.getOrNull()
            when {
                d == null || baseOf(linkOf(d)) != baseOf(base) -> kept.add(item)
                isProtected(d) -> { refused += d; kept.add(item) }
                else -> removed += d
            }
        }
        return Removal(kept, removed, refused)
    }

    /** An add-on as Stremio keeps one in its list. */
    fun descriptor(base: String, manifest: JsonObject): JsonObject = JsonObject().apply {
        addProperty("transportUrl", baseOf(base) + "/manifest.json")
        addProperty("transportName", "http")
        add("manifest", manifest)
        add("flags", JsonObject())
    }

    /** What is sent to Stremio to save its add-on list. */
    fun saveRequest(key: String, addons: com.google.gson.JsonArray): JsonObject = JsonObject().apply {
        addProperty("type", "AddonCollectionSet"); addProperty("authKey", key); add("addons", addons)
    }

    /**
     * Takes an add-on out of the Stremio account as well, after it has been removed in KueTV. Says what happened.
     * Nothing is sent unless exactly that add-on comes out and every other one stays as it was. Call off the main thread.
     */
    fun removeFromAccount(context: Context, base: String, name: String): String {
        val key = key(context)
        if (key.isEmpty()) return ""
        val r = removal(raw(key), base)
        forgetBrought(context, base)
        return when {
            r.refused.isNotEmpty() -> "$name is built into Stremio, so it stays in your Stremio account."
            r.removed.isEmpty() -> "$name wasn't in your Stremio account."
            else -> {
                ask("addonCollectionSet", saveRequest(key, r.kept))
                Report.note("Stremio", "removed from the account: $name")
                "Also removed from your Stremio account."
            }
        }
    }

    /** Puts an add-on added in KueTV into the Stremio account too. Says what happened. Call off the main thread. */
    fun addToAccount(context: Context, addOn: AddOn): String {
        val key = key(context)
        if (key.isEmpty()) return ""
        val list = raw(key)
        if (list.any { runCatching { baseOf(linkOf(it.asJsonObject)) == baseOf(addOn.base) }.getOrDefault(false) }) return "It was already in your Stremio account."
        val manifest = runCatching {
            Provider.http.newCall(Request.Builder().url(baseOf(addOn.base) + "/manifest.json").header("User-Agent", Provider.USER_AGENT).build()).execute()
                .use { r -> JsonParser.parseString(r.body.string()).asJsonObject }
        }.getOrNull() ?: return "Couldn't add it to your Stremio account: its description didn't answer."
        val all = com.google.gson.JsonArray().apply { addAll(list); add(descriptor(addOn.base, manifest)) }
        ask("addonCollectionSet", saveRequest(key, all))
        noteBrought(context, listOf(baseOf(addOn.base)))
        Report.note("Stremio", "added to the account: " + addOn.name)
        return "Also added to your Stremio account."
    }

    // The add-ons that came from (or went into) the Stremio account, so ones taken out of Stremio go from KueTV too.
    private fun brought(context: Context): Set<String> = prefs(context).getStringSet("brought", emptySet()).orEmpty()
    private fun noteBrought(context: Context, bases: List<String>) = prefs(context).edit().putStringSet("brought", brought(context) + bases).apply()
    private fun forgetBrought(context: Context, base: String) = prefs(context).edit().putStringSet("brought", brought(context) - baseOf(base)).apply()

    /** The sign-in and what came from the account, for keeping the TV and phone in step. */
    fun export(context: Context): String = listOf(key(context), email(context), brought(context).sorted().joinToString("\u0001")).joinToString("\n")

    fun import(context: Context, text: String) {
        val p = text.split('\n')
        if (p.size < 3) return
        prefs(context).edit().putString("key", p[0]).putString("email", p[1]).putStringSet("brought", p[2].split('\u0001').filter { it.isNotEmpty() }.toSet()).apply()
        changes++
    }

    /** What KueTV makes of one add-on from the account. Public for the tests. */
    fun entry(d: JsonObject): Entry {
        val link = d.get("transportUrl")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val manifest = d.get("manifest")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val name = manifest.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().ifBlank { link.substringAfter("://").substringBefore('/') }
        val host = link.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return when {
            !link.startsWith("http") -> Entry(name, link, null, "not a web link")
            host == "127.0.0.1" || host == "localhost" -> Entry(name, link, null, "only works on the computer running Stremio")
            else -> try {
                Entry(name, link, AddOns.fromManifest(link, manifest), "")
            } catch (e: AddOnProblem) {
                Entry(name, link, null, "nothing in it KueTV uses")
            }
        }
    }

    /** What bringing the add-ons in did. */
    data class Brought(val added: List<String>, val had: List<String>, val skipped: List<String>, val removed: List<String> = emptyList())

    /** Adds every usable add-on in the account that KueTV hasn't got; refreshes the ones it has. Call off the main thread. */
    fun bringIn(context: Context): Brought {
        val key = key(context)
        if (key.isEmpty()) throw AddOnProblem("Sign in to Stremio first.")
        val entries = collection(key)
        val added = ArrayList<String>()
        val had = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (e in entries) {
            val addOn = e.addOn
            if (addOn == null) {
                skipped += e.name + " (" + e.why + ")"
                continue
            }
            if (AddOnStore.all(context).any { it.base == addOn.base }) {
                AddOnStore.update(context, addOn)
                had += e.name
            } else {
                AddOnStore.add(context, addOn)
                added += e.name
            }
        }
        // Ones that came from Stremio before but have since been taken out of it there go from KueTV too.
        val now = entries.mapNotNull { it.addOn?.base }.map { baseOf(it) }.toSet()
        val removed = ArrayList<String>()
        for (gone in brought(context) - now) {
            val here = AddOnStore.all(context).firstOrNull { baseOf(it.base) == gone } ?: continue
            AddOnStore.remove(context, here)
            removed += here.name
        }
        prefs(context).edit().putStringSet("brought", now).apply()
        Report.note("Stremio", "brought in: " + added.size + " added, " + had.size + " already here, " + removed.size + " removed as gone from Stremio, " + skipped.size + " left out")
        return Brought(added, had, skipped, removed)
    }

    /** "Added 3: A, B, C. Already here: D. Left out: E (why)." */
    fun say(b: Brought): String = buildList {
        add(if (b.added.isEmpty()) "Nothing new to add" else "Added " + b.added.size + ": " + b.added.joinToString(", "))
        if (b.had.isNotEmpty()) add("Already here: " + b.had.joinToString(", "))
        if (b.removed.isNotEmpty()) add("Removed, as they're gone from Stremio: " + b.removed.joinToString(", "))
        if (b.skipped.isNotEmpty()) add("Left out: " + b.skipped.joinToString(", "))
    }.joinToString(". ") + "."
}

/**
 * Adding by link, whichever kind it is: an add-on (its manifest.json) or a free channel list (.m3u).
 * The link is tried as the kind it looks like first, then as the other.
 */
object Links {
    data class Added(val ok: Boolean, val said: String, val addOn: AddOn? = null)

    /** Call off the main thread. */
    fun add(context: Context, typed: String): Added {
        val link = typed.trim()
        if (link.isEmpty()) return Added(false, "Paste the link first.")
        val path = link.substringBefore('?').lowercase()
        val asList = path.endsWith(".m3u") || path.endsWith(".m3u8")
        val asAddOn: () -> Added = {
            val addOn = AddOns.read(link)
            AddOnStore.add(context, addOn)
            Added(true, "Added the add-on " + addOn.name + ".", addOn)
        }
        val asFree: () -> Added = {
            val tidy = FreeStore.tidy(link) ?: throw AddOnProblem("That isn't a link.")
            val found = Provider.loadFree(tidy)
            if (found.isEmpty()) throw AddOnProblem("That list holds no channels.")
            FreeStore.add(context, tidy)
            Added(true, "Added a free channel list: " + found.size + " channels. They are in Live TV, at the top of the categories, under \"Free:\".")
        }
        val order = if (asList) listOf(asFree, asAddOn) else listOf(asAddOn, asFree)
        var first: String? = null
        for (attempt in order) {
            try {
                return attempt()
            } catch (e: Exception) {
                if (first == null) first = (e as? AddOnProblem)?.message ?: (e as? ProviderProblem)?.message
            }
        }
        return Added(false, "That link isn't an add-on or a channel list KueTV can read." + (first?.let { " ($it)" } ?: ""))
    }
}
