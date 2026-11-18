package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * How far into each film, episode or recording the user has got, kept on this TV or phone, so it
 * carries on from there next time. A film is known by its own address at the provider, whichever
 * stream it was last played from. Once it has been watched to the end, its place is forgotten.
 *
 * Films and episodes left part way through are also listed for Home's "Carry on watching" row,
 * newest first, with their name and picture.
 */
object Positions {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("positions", Context.MODE_PRIVATE)
    private fun key(url: String) = url.hashCode().toString()
    private const val LISTED = "w."
    private const val SEP = "\u0001"

    /** Goes up whenever the "Carry on watching" list may have changed. */
    var changes by mutableIntStateOf(0)
        private set

    /** A film or episode left part way through. */
    data class Watching(val url: String, val name: String, val poster: String, val positionMs: Long, val lengthMs: Long, val atMs: Long)

    fun encode(w: Watching): String = listOf(w.atMs, w.positionMs, w.lengthMs, w.name.replace(SEP, " "), w.poster.replace(SEP, ""), w.url).joinToString(SEP)

    fun decode(text: String): Watching? {
        val p = text.split(SEP, limit = 6)
        if (p.size < 6) return null
        val at = p[0].toLongOrNull() ?: return null
        val position = p[1].toLongOrNull() ?: return null
        val length = p[2].toLongOrNull() ?: return null
        return Watching(p[5], p[3], p[4], position, length, at)
    }

    /** Where to carry on from, in milliseconds; 0 for the start. */
    fun of(context: Context, url: String): Long = prefs(context).getLong(key(url), 0)

    /**
     * Notes the place. [poster] is given for films and episodes only (empty when they have no picture);
     * those are the ones listed under "Carry on watching". Catch-up and recordings aren't listed.
     */
    fun keep(context: Context, url: String, positionMs: Long, lengthMs: Long, name: String? = null, poster: String? = null) {
        if (lengthMs <= 0) return
        val edit = prefs(context).edit()
        // Into the credits: watched.
        if (positionMs > lengthMs * 95 / 100 && poster != null) Watched.set(context, url, true)
        // The first minute isn't worth keeping, and the last few are the credits.
        if (positionMs < 60_000 || positionMs > lengthMs * 95 / 100) {
            edit.remove(key(url)).remove(LISTED + key(url))
        } else {
            edit.putLong(key(url), positionMs)
            if (poster != null && name != null) edit.putString(LISTED + key(url), encode(Watching(url, name, poster, positionMs, lengthMs, System.currentTimeMillis())))
        }
        edit.apply()
        changes++
    }

    fun forget(context: Context, url: String) {
        prefs(context).edit().remove(key(url)).remove(LISTED + key(url)).apply()
        changes++
    }

    /** Everything under "Carry on watching", written out for another device to take. */
    fun export(context: Context): String = watching(context, Int.MAX_VALUE).sortedBy { it.url }.joinToString("\n") { encode(it) }

    /** Takes another device's "Carry on watching" in place of this one's. Places of recordings and catch-up are left alone. */
    fun import(context: Context, text: String) {
        val p = prefs(context)
        val edit = p.edit()
        for (name in p.all.keys.filter { it.startsWith(LISTED) }) edit.remove(name).remove(name.removePrefix(LISTED))
        for (w in text.split('\n').mapNotNull(::decode)) edit.putLong(key(w.url), w.positionMs).putString(LISTED + key(w.url), encode(w))
        edit.apply()
        changes++
    }

    /** How far through a film or episode left part way is, 0 to 1; null when it isn't part way. */
    fun progress(context: Context, url: String): Float? {
        val w = prefs(context).getString(LISTED + key(url), null)?.let(::decode) ?: return null
        return if (w.lengthMs > 0) (w.positionMs.toFloat() / w.lengthMs).coerceIn(0f, 1f) else null
    }

    /** Films and episodes left part way through, most recently watched first. */
    fun watching(context: Context, most: Int = 15): List<Watching> =
        prefs(context).all.filterKeys { it.startsWith(LISTED) }.values.mapNotNull { (it as? String)?.let(::decode) }.sortedByDescending { it.atMs }.take(most)
}
