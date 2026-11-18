package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Live channels watched lately, newest first, by the address the channel is played from. Kept on this device. */
object Recent {
    private const val MOST = 30

    /** Goes up by one each time the list changes, so screens showing it can follow. */
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(Profiles.scoped(context, "recent"), Context.MODE_PRIVATE)

    fun export(context: Context): String = all(context).joinToString("\n")

    fun import(context: Context, text: String) {
        prefs(context).edit().putString("live", text.split('\n').filter { it.isNotBlank() }.take(MOST).joinToString("\n")).apply()
        changes++
    }

    fun all(context: Context): List<String> = prefs(context).getString("live", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun add(context: Context, key: String) {
        if (key.isBlank()) return
        val list = listOf(key) + all(context).filter { it != key }
        prefs(context).edit().putString("live", list.take(MOST).joinToString("\n")).apply()
        changes++
    }

    fun clear(context: Context) {
        prefs(context).edit().remove("live").apply()
        changes++
    }

    /** [groups] that are in [recent], in the order they were watched, newest first. */
    fun <T> pick(groups: List<T>, recent: List<String>, urls: (T) -> List<String>): List<T> {
        val rank = recent.withIndex().associate { it.value to it.index }
        return groups.mapNotNull { g -> urls(g).mapNotNull { rank[it] }.minOrNull()?.let { g to it } }.sortedBy { it.second }.map { it.first }
    }
}

/** The number keys on a remote, as 0 to 9; -1 for any other key. */
fun digitOf(keyCode: Int): Int = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> keyCode - android.view.KeyEvent.KEYCODE_0
    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0
    else -> -1
}

/**
 * Programmes set to record every time they are on: a channel and a title. Each time the guides are looked at, the
 * showings still to come are set to record.
 */
object SeriesLinks {
    data class Link(val channelId: String, val channelName: String, val url: String, val title: String)

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("series_links", Context.MODE_PRIVATE)

    fun all(context: Context): List<Link> = prefs(context).getString("all", "").orEmpty().split('\n').mapNotNull { line ->
        line.split('\u0001').takeIf { it.size == 4 }?.let { Link(it[0], it[1], it[2], it[3]) }
    }

    private fun save(context: Context, list: List<Link>) {
        prefs(context).edit().putString("all", list.joinToString("\n") { listOf(it.channelId, it.channelName, it.url, it.title).joinToString("\u0001") }).apply()
        changes++
    }

    private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    fun has(context: Context, channelName: String, title: String) = all(context).any { same(it.channelName, channelName) && same(it.title, title) }

    fun add(context: Context, link: Link) = save(context, all(context).filterNot { same(it.channelName, link.channelName) && same(it.title, link.title) } + link)

    fun remove(context: Context, channelName: String, title: String) = save(context, all(context).filterNot { same(it.channelName, channelName) && same(it.title, title) })

    /** The showings of [link] in [listing] still to come, as recordings to set. */
    fun showings(link: Link, listing: List<Programme>, now: Long): List<Timed> =
        listing.filter { same(it.title, link.title) && it.startMs > now }.map { Timed.make(link.channelName, link.title, link.url, it.startMs, it.stopMs) }
}

/** The provider's guide times moved by this much, for a guide that is out by an hour or more. Kept on this device. */
object GuideOffset {
    val choices = listOf(0, 60, -60, 120, -120, 180, -180, 30, -30)

    /** Read by the guide as it arrives; set when the app opens and when changed. */
    @Volatile var ms: Long = 0L

    private fun prefs(context: Context) = context.getSharedPreferences("choices", Context.MODE_PRIVATE)
    fun minutes(context: Context): Int = prefs(context).getInt("guideOffsetMin", 0)
    fun load(context: Context) { ms = minutes(context) * 60_000L }
    fun set(context: Context, minutes: Int) {
        prefs(context).edit().putInt("guideOffsetMin", minutes).apply()
        ms = minutes * 60_000L
        NowNext.forgetAll()
        Choices.touch()
    }

    fun words(minutes: Int): String = when {
        minutes == 0 -> "as your provider gives them"
        minutes % 60 == 0 && minutes > 0 -> (minutes / 60).toString() + (if (minutes == 60) " hour" else " hours") + " later"
        minutes % 60 == 0 -> (-minutes / 60).toString() + (if (minutes == -60) " hour" else " hours") + " earlier"
        minutes > 0 -> "$minutes minutes later"
        else -> (-minutes).toString() + " minutes earlier"
    }
}

/** Where KueTV opens: Home (empty), "last" for the channel watched last, or a channel's id. Kept on this device. */
object OpenOn {
    private fun prefs(context: Context) = context.getSharedPreferences("choices", Context.MODE_PRIVATE)
    fun get(context: Context): String = prefs(context).getString("openOn", "").orEmpty()
    fun set(context: Context, value: String) {
        prefs(context).edit().putString("openOn", value).apply()
        Choices.touch()
    }
}
