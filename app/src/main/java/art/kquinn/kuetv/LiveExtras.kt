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

    private fun prefs(context: Context) = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

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
