package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** The add-ons the user has added, kept on this TV or phone, in the order they were added. */
object AddOnStore {
    /** Goes up whenever an add-on is added or removed, so the screens look again. */
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("addons", Context.MODE_PRIVATE)

    fun all(context: Context): List<AddOn> =
        prefs(context).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { AddOn.fromJson(it) }

    private fun save(context: Context, list: List<AddOn>) {
        prefs(context).edit().putString("list", list.joinToString("\n") { it.toJson() }).apply()
        changes++
    }

    /** Takes on the add-ons copied from another device, in place of the ones here. */
    fun replaceAll(context: Context, list: List<AddOn>) = save(context, list)

    /** Adds [addOn], or replaces the one already there from the same place. */
    fun add(context: Context, addOn: AddOn) = save(context, all(context).filter { it.base != addOn.base } + addOn)

    fun remove(context: Context, addOn: AddOn) = save(context, all(context).filter { it.base != addOn.base })
}
