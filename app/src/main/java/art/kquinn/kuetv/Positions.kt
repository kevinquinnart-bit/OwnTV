package art.kquinn.kuetv

import android.content.Context

/**
 * How far into each film, episode or recording the user has got, kept on this TV or phone, so it
 * carries on from there next time. A film is known by its own address at the provider, whichever
 * stream it was last played from. Once it has been watched to the end, its place is forgotten.
 */
object Positions {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("positions", Context.MODE_PRIVATE)
    private fun key(url: String) = url.hashCode().toString()

    /** Where to carry on from, in milliseconds; 0 for the start. */
    fun of(context: Context, url: String): Long = prefs(context).getLong(key(url), 0)

    fun keep(context: Context, url: String, positionMs: Long, lengthMs: Long) {
        if (lengthMs <= 0) return
        val edit = prefs(context).edit()
        // The first minute isn't worth keeping, and the last few are the credits.
        if (positionMs < 60_000 || positionMs > lengthMs * 95 / 100) edit.remove(key(url)) else edit.putLong(key(url), positionMs)
        edit.apply()
    }

    fun forget(context: Context, url: String) = prefs(context).edit().remove(key(url)).apply()
}
