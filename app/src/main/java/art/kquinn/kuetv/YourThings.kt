package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Profiles: each person's own favourites, history, watched marks and places. A profile can have a PIN, asked for
 * when switching to it, and can be a kids profile, which hides adult categories. The first profile, "Main", is the
 * one there has always been: its things are kept where they always were.
 */
object Profiles {
    data class Profile(val id: String, val name: String, val pin: String = "", val kids: Boolean = false, val picture: String = "")

    /** The pictures a profile can have. */
    val pictures = listOf("🙂", "🦊", "🐼", "🦁", "🐸", "🐙", "🚀", "⚽", "🎮", "🎸", "🌟", "🍕")

    /** Goes up by one whenever the profiles or the one in use change. */
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    private fun encode(p: Profile) = JsonObject().apply { addProperty("id", p.id); addProperty("name", p.name); addProperty("pin", p.pin); addProperty("kids", p.kids); addProperty("picture", p.picture) }.toString()
    private fun decode(text: String): Profile? = runCatching {
        val o = JsonParser.parseString(text).asJsonObject
        Profile(o.get("id").asString, o.get("name").asString, o.get("pin")?.asString.orEmpty(), o.get("kids")?.asBoolean ?: false, o.get("picture")?.asString.orEmpty())
    }.getOrNull()

    /** Every profile, Main first. */
    fun all(context: Context): List<Profile> {
        val p = prefs(context)
        val main = Profile("", p.getString("mainName", "Main").orEmpty().ifBlank { "Main" }, p.getString("mainPin", "").orEmpty(), picture = p.getString("mainPicture", "").orEmpty())
        return listOf(main) + p.getString("others", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull(::decode)
    }

    fun currentId(context: Context): String = prefs(context).getString("current", "").orEmpty()
    fun current(context: Context): Profile = all(context).firstOrNull { it.id == currentId(context) } ?: all(context).first()

    /** Where a store keeps its things for the profile in use: Main's in the same place as always. */
    fun scoped(context: Context, name: String): String = currentId(context).let { if (it.isEmpty()) name else "$name.$it" }

    fun switchTo(context: Context, id: String) {
        prefs(context).edit().putString("current", id).apply()
        changes++
    }

    fun add(context: Context, name: String, kids: Boolean): Profile {
        val profile = Profile("p" + System.currentTimeMillis().toString(36), name.trim().ifBlank { if (kids) "Kids" else "Profile" }, "", kids)
        saveOthers(context, all(context).drop(1) + profile)
        return profile
    }

    fun setPin(context: Context, id: String, pin: String) {
        if (id.isEmpty()) prefs(context).edit().putString("mainPin", pin).apply()
        else saveOthers(context, all(context).drop(1).map { if (it.id == id) it.copy(pin = pin) else it })
        changes++
    }

    /** Gives a profile the next picture along. */
    fun nextPicture(context: Context, id: String) {
        val now = all(context).firstOrNull { it.id == id }?.picture.orEmpty()
        val next = pictures[(pictures.indexOf(now) + 1) % pictures.size]
        if (id.isEmpty()) prefs(context).edit().putString("mainPicture", next).apply()
        else saveOthers(context, all(context).drop(1).map { if (it.id == id) it.copy(picture = next) else it })
        changes++
    }

    /** Removes a profile and everything kept for it. Main can't be removed. */
    fun remove(context: Context, id: String) {
        if (id.isEmpty()) return
        saveOthers(context, all(context).drop(1).filter { it.id != id })
        for (store in STORES) context.applicationContext.deleteSharedPreferences("$store.$id")
        if (currentId(context) == id) switchTo(context, "")
        changes++
    }

    private fun saveOthers(context: Context, others: List<Profile>) {
        prefs(context).edit().putString("others", others.joinToString("\n") { encode(it) }).apply()
        changes++
    }

    /** The stores kept for each profile on its own. */
    val STORES = listOf("favourites", "positions", "watched", "recent", "history")

    private val adult = Regex("\\b(adult|adults|xxx|18\\+|porn|erotic|for adults)\\b|\\+18\\b", RegexOption.IGNORE_CASE)

    /** A category a kids profile doesn't see. */
    fun isAdult(name: String): Boolean = adult.containsMatchIn(name)
}

/** Everything played, newest first: channels, films, episodes, catch-up and recordings. Kept for each profile. */
object History {
    data class Entry(val kind: String, val name: String, val key: String, val poster: String, val atMs: Long)

    private const val MOST = 100
    private const val SEP = "\u0001"

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Profiles.scoped(context, "history"), Context.MODE_PRIVATE)

    fun encode(e: Entry) = listOf(e.kind, e.name.replace(SEP, " "), e.key, e.poster.replace(SEP, ""), e.atMs.toString()).joinToString(SEP)
    fun decode(text: String): Entry? = text.split(SEP).takeIf { it.size == 5 }?.let { p -> p[4].toLongOrNull()?.let { Entry(p[0], p[1], p[2], p[3], it) } }

    fun all(context: Context): List<Entry> = prefs(context).getString("all", "").orEmpty().split('\n').mapNotNull(::decode)

    /** Notes [key] as just played; an earlier note of the same one goes, so each is listed once. */
    fun add(context: Context, kind: String, name: String, key: String, poster: String?) {
        if (key.isBlank()) return
        val list = listOf(Entry(kind, name, key, poster.orEmpty(), System.currentTimeMillis())) + all(context).filter { it.key != key }
        prefs(context).edit().putString("all", list.take(MOST).joinToString("\n") { encode(it) }).apply()
        changes++
    }

    fun clear(context: Context) {
        prefs(context).edit().remove("all").apply()
        changes++
    }

    const val LIVE = "live"
    const val FILM = "film"
    const val OTHER = "other"
}

/** Provider details used before, kept so the app can be switched between them. The one in use is in [AccountStore]. */
object Providers {
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("providers", Context.MODE_PRIVATE)

    fun all(context: Context): List<Account> = prefs(context).getString("all", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { line ->
        line.split('\u0001').takeIf { it.size == 4 }?.let { Account(it[0], it[1], it[2], it[3]) }
    }

    /** Keeps [account] on the list, if it isn't there already. */
    fun keep(context: Context, account: Account) {
        if (!account.isSet || account in all(context)) return
        save(context, all(context) + account)
    }

    fun remove(context: Context, account: Account) = save(context, all(context).filter { it != account })

    private fun save(context: Context, list: List<Account>) {
        prefs(context).edit().putString("all", list.joinToString("\n") { listOf(it.server, it.user, it.pass, it.playlist).joinToString("\u0001") }).apply()
        changes++
    }

    /** "tv.example.com · kevin", or "Playlist · lists.example.com". */
    fun label(account: Account): String {
        if (account.isLogin) return account.server.substringAfter("://").substringBefore('/').substringBefore(':') + " · " + account.user
        return "Playlist · " + account.playlist.substringAfter("://").substringBefore('/')
    }
}

/**
 * A copy of everything that makes KueTV the user's own, kept in Google Drive as one small file: the same parts
 * the TV and phone keep in step, plus watched marks and recently watched channels.
 */
object Backup {
    const val FILE = "KueTV backup.json"
    val hows = listOf("every time KueTV opens", "once a day", "once a week", "never")

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun pack(parts: Map<String, String>, atMs: Long = System.currentTimeMillis()): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("at", atMs)
        add("parts", JsonObject().apply { parts.forEach { (name, body) -> addProperty(name, body) } })
    }.toString()

    fun unpack(text: String): Map<String, String>? = runCatching {
        JsonParser.parseString(text).asJsonObject.getAsJsonObject("parts").entrySet().associate { (name, v) -> name to v.asString }
    }.getOrNull()

    /** How often it backs up by itself: 0 every time KueTV opens (the first choice), 1 daily, 2 weekly, 3 never. */
    fun how(context: Context): Int = prefs(context).getInt("how", 0).coerceIn(0, hows.size - 1)
    fun how(context: Context, value: Int) {
        prefs(context).edit().putInt("how", value).apply()
        changes++
    }

    fun lastMs(context: Context): Long = prefs(context).getLong("last", 0)
    fun done(context: Context, atMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong("last", atMs).apply()
        changes++
    }

    /** Whether it is time to back up by itself, opening KueTV now. */
    fun due(context: Context, now: Long = System.currentTimeMillis()): Boolean = dueAfter(how(context), lastMs(context), now)

    fun dueAfter(how: Int, lastMs: Long, now: Long): Boolean = when (how) {
        0 -> true
        1 -> now - lastMs >= 24 * 3_600_000L
        2 -> now - lastMs >= 7 * 24 * 3_600_000L
        else -> false
    }
}

/** The user's own categories of live channels: a name and the channels in it, in the order added. */
object MyCategories {
    data class Mine(val id: String, val name: String, val channels: List<String>)

    const val PREFIX = "my:"

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Profiles.scoped(context, "my_categories"), Context.MODE_PRIVATE)

    fun all(context: Context): List<Mine> = prefs(context).getString("all", "").orEmpty().split('\n').mapNotNull { line ->
        val p = line.split('\u0001')
        if (p.size < 2 || p[0].isBlank()) null else Mine(p[0], p[1], p.drop(2).filter { it.isNotBlank() })
    }

    private fun save(context: Context, list: List<Mine>) {
        prefs(context).edit().putString("all", list.joinToString("\n") { (listOf(it.id, it.name.replace('\u0001', ' ')) + it.channels).joinToString("\u0001") }).apply()
        changes++
    }

    fun make(context: Context, name: String, firstChannel: String?): Mine {
        val mine = Mine(PREFIX + System.currentTimeMillis().toString(36), name.trim().ifBlank { "My channels" }, listOfNotNull(firstChannel))
        save(context, all(context) + mine)
        return mine
    }

    fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    fun toggle(context: Context, id: String, channel: String) = save(context, all(context).map { m ->
        if (m.id != id) m else m.copy(channels = if (channel in m.channels) m.channels - channel else m.channels + channel)
    })

    fun export(context: Context): String = prefs(context).getString("all", "").orEmpty()
    fun import(context: Context, text: String) {
        prefs(context).edit().putString("all", text).apply()
        changes++
    }
}

/** Names the user gave channels, by channel id. Kept on this device. */
object Renames {
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("renames", Context.MODE_PRIVATE)

    fun all(context: Context): Map<String, String> = prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    /** Gives every one of [channelIds] the name [name]; an empty name puts the provider's back. */
    fun set(context: Context, channelIds: List<String>, name: String) {
        val edit = prefs(context).edit()
        for (id in channelIds) if (name.isBlank()) edit.remove(id) else edit.putString(id, name.trim())
        edit.apply()
        changes++
    }

    fun export(context: Context): String = all(context).entries.joinToString("\n") { it.key + "\u0001" + it.value }
    fun import(context: Context, text: String) {
        val edit = prefs(context).edit().clear()
        for (line in text.split('\n')) line.split('\u0001').takeIf { it.size == 2 }?.let { edit.putString(it[0], it[1]) }
        edit.apply()
        changes++
    }
}

/**
 * The remote's spare buttons (Guide, and the red, green, yellow and blue ones some remotes have): each can open a
 * part of KueTV. Kept on this device.
 */
object SpareKeys {
    class Spare(val code: Int, val name: String)

    val keys = listOf(
        Spare(android.view.KeyEvent.KEYCODE_GUIDE, "Guide button"),
        Spare(android.view.KeyEvent.KEYCODE_PROG_RED, "Red button"),
        Spare(android.view.KeyEvent.KEYCODE_PROG_GREEN, "Green button"),
        Spare(android.view.KeyEvent.KEYCODE_PROG_YELLOW, "Yellow button"),
        Spare(android.view.KeyEvent.KEYCODE_PROG_BLUE, "Blue button"),
    )

    /** What a button can do. The names match the menu's. */
    val actions = listOf("Nothing", "Home", "Search", "Live TV", "TV Guide", "Catch-up", "Films", "Series", "Recordings", "Settings")

    private fun prefs(context: Context) = context.getSharedPreferences("choices", Context.MODE_PRIVATE)

    /** The part of KueTV a spare button has just asked for; taken by the screen that opens it. */
    var asked by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** A spare button pressed: true when it is set to do something, which is then asked for. */
    fun pressed(context: Context, code: Int): Boolean {
        if (keys.none { it.code == code }) return false
        val what = action(context, code)
        if (what == "Nothing") return false
        asked = what
        return true
    }

    fun action(context: Context, code: Int): String =
        prefs(context).getString("spare.$code", if (code == android.view.KeyEvent.KEYCODE_GUIDE) "TV Guide" else "Nothing").orEmpty()

    fun next(context: Context, code: Int) {
        val now = action(context, code)
        prefs(context).edit().putString("spare.$code", actions[(actions.indexOf(now) + 1) % actions.size]).apply()
        Choices.touch()
    }
}
