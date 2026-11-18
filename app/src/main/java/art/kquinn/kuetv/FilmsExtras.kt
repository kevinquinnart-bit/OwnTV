package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Films and episodes watched to the end, kept on this device. One is marked by itself when it ends or is left in
 * its last few minutes, and the user can mark one either way. Known by the address it is played from.
 */
object Watched {
    /** Goes up by one whenever a mark changes, so posters and lists can follow. */
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Profiles.scoped(context, "watched"), Context.MODE_PRIVATE)
    private fun key(url: String) = url.hashCode().toString()

    /** Every mark, written out for a backup or the other device. */
    fun export(context: Context): String = prefs(context).all.keys.sorted().joinToString("\n")

    /** Takes the marks from a backup or the other device, in place of these. */
    fun import(context: Context, text: String) {
        val edit = prefs(context).edit().clear()
        for (k in text.split('\n')) if (k.isNotBlank()) edit.putBoolean(k, true)
        edit.apply()
        changes++
    }

    fun isWatched(context: Context, url: String): Boolean = url.isNotBlank() && prefs(context).getBoolean(key(url), false)

    fun set(context: Context, url: String, watched: Boolean) {
        if (url.isBlank()) return
        val edit = prefs(context).edit()
        if (watched) edit.putBoolean(key(url), true) else edit.remove(key(url))
        edit.apply()
        changes++
    }
}

/** How the films and series are listed: their sort, and whether those watched are hidden. Kept on this device. */
object Listing {
    val sorts = listOf("Provider's order", "A to Z", "Newest added")

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("listing", Context.MODE_PRIVATE)

    fun sort(context: Context): Int = prefs(context).getInt("sort", 0).coerceIn(0, sorts.size - 1)
    fun sort(context: Context, value: Int) {
        prefs(context).edit().putInt("sort", value).apply()
        changes++
    }

    fun hideWatched(context: Context): Boolean = prefs(context).getBoolean("hide", false)
    fun hideWatched(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("hide", value).apply()
        changes++
    }

    /** [items] in sort [sort]: as given, by name ignoring "The" and the provider's tags, or newest first. */
    fun <T> sorted(items: List<T>, sort: Int, name: (T) -> String, added: (T) -> Long): List<T> = when (sort) {
        1 -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { plainName(name(it)) })
        2 -> items.sortedByDescending(added)
        else -> items
    }

    /** "|UK| The Batman" as "Batman": a provider's tag and a leading "The" or "A" don't count for A to Z. */
    fun plainName(name: String): String =
        name.trim().replace(Regex("^(\\[[^]]*]|\\|[^|]*\\||[A-Z]{2,4}\\s*[-:|]\\s)\\s*"), "").replace(Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE), "").trim()
}

/** A film's or series' trailer: opens it in the YouTube app, or the web if that isn't there. False when nothing can. */
fun openTrailer(context: Context, trailer: String): Boolean {
    val id = trailerId(trailer)
    if (id.isBlank()) return false
    for (address in listOf("vnd.youtube:$id", "https://www.youtube.com/watch?v=$id")) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(address)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (e: android.content.ActivityNotFoundException) {
            // Try the next way.
        }
    }
    return false
}

/** Plays [url] in another video app (VLC, MX Player…). False when there is none, or it can't be played elsewhere. */
fun openElsewhere(context: Context, url: String, title: String): Boolean {
    if (url.isBlank() || url.startsWith("magnet:") || url.startsWith(Playable.NO_ADDRESS)) return false
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(android.net.Uri.parse(url), "video/*")
        .putExtra("title", title)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }
}

/** The YouTube video's id in a trailer as providers give it: the id itself, or a watch, short or embed address. */
fun trailerId(trailer: String): String = trailer.trim().let { t -> Regex("(?:v=|youtu\\.be/|embed/)([A-Za-z0-9_-]{6,})").find(t)?.groupValues?.get(1) ?: t }
