package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * Other providers' channels, films and series shown in the same lists as the one in use.
 *
 * Everything from another provider has its id begun with that provider's mark ("~1a2b3c4d~"), made from its
 * details, so the same provider always has the same mark: favourites, hidden channels and watched marks set
 * on its channels stay put however the list of providers changes. The one in use keeps its ids as they are.
 *
 * Asking a provider for a guide, catch-up, a film's details or a series' episodes goes by the id: one with a
 * mark is asked of that provider, with the mark taken off.
 */
object Combined {
    /** Another provider's login and how far its clock is ahead, by its mark. */
    private class Source(val account: Account, val aheadSec: Int)

    private val sources = ConcurrentHashMap<String, Source>()

    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("combined", Context.MODE_PRIVATE)

    /** The mark that begins the ids of everything from [account]. */
    fun mark(account: Account): String = "~" + LibraryCache.who(account) + "~"

    /** Whether [id] is from another provider. */
    fun isOther(id: String): Boolean = id.length > 2 && id[0] == '~' && id.indexOf('~', 1) > 1

    /** The mark [id] begins with, or "" for the provider in use. */
    fun markOf(id: String): String = if (isOther(id)) id.substring(0, id.indexOf('~', 1) + 1) else ""

    /** [id] as its own provider knows it. */
    fun raw(id: String): String = id.removePrefix(markOf(id))

    /** Notes another provider's login, so its channels can be asked about. */
    fun register(account: Account, aheadSec: Int) {
        sources[mark(account)] = Source(account, aheadSec)
    }

    /**
     * Who to ask about [id], and what to call it: null when it is the provider in use's own (ask [primary] as
     * it is). For another provider's that has no login (a playlist link), there is nobody to ask.
     */
    fun which(id: String): Pair<Account, String>? {
        if (!isOther(id)) return null
        val s = sources[markOf(id)] ?: return null
        return s.account to raw(id)
    }

    /** The login to ask about [id]: [primary] for the provider in use's own, the other provider's for one of its. */
    fun accountFor(primary: Account?, id: String): Account? {
        if (!isOther(id)) return primary
        return sources[markOf(id)]?.account?.takeIf { it.isLogin }
    }

    /** How far the clock of [id]'s provider is ahead of world time. */
    fun aheadFor(id: String, primaryAheadSec: Int): Int = if (!isOther(id)) primaryAheadSec else sources[markOf(id)]?.aheadSec ?: 0

    /** The providers chosen to be in the lists as well as the one in use, from the saved ones. */
    fun chosen(context: Context): List<Account> {
        val on = prefs(context).getStringSet("on", emptySet()).orEmpty()
        return Providers.all(context).filter { mark(it) in on }
    }

    fun isOn(context: Context, account: Account): Boolean = mark(account) in prefs(context).getStringSet("on", emptySet()).orEmpty()

    fun setOn(context: Context, account: Account, on: Boolean) {
        val now = prefs(context).getStringSet("on", emptySet()).orEmpty().toMutableSet()
        if (on) now += mark(account) else now -= mark(account)
        prefs(context).edit().putStringSet("on", now).apply()
        changes++
    }

    /**
     * [primary]'s lists with [others]' added. Another provider's channel with the same name as one of
     * [primary]'s joins that channel's row as a backup version; the rest get rows of their own. A film or
     * series [primary] already has is left out. A category with the same name as one already there has
     * the provider's number after it: "UK | Sport (2)".
     */
    fun merge(primary: Library, others: List<Pair<Account, Library>>): Library {
        if (others.isEmpty()) return primary
        val live = primary.liveCategories.toMutableList()
        val channels = primary.channels.toMutableList()
        val filmCats = primary.filmCategories.toMutableList()
        val films = primary.films.toMutableList()
        val showCats = primary.showCategories.toMutableList()
        val shows = primary.shows.toMutableList()
        val filmNames = films.mapTo(HashSet()) { it.name.trim().lowercase() }
        val showNames = shows.mapTo(HashSet()) { it.name.trim().lowercase() }
        others.forEachIndexed { i, (account, lib) ->
            val m = mark(account)
            val n = " (" + (i + 2) + ")"
            fun cats(into: MutableList<Category>, from: List<Category>) {
                val names = into.mapTo(HashSet()) { it.name }
                from.forEach { into += Category(m + it.id, if (it.name in names) it.name + n else it.name) }
            }
            cats(live, lib.liveCategories)
            channels += lib.channels.map { it.copy(id = m + it.id, categoryId = m + it.categoryId) }
            cats(filmCats, lib.filmCategories)
            films += lib.films.filter { filmNames.add(it.name.trim().lowercase()) }.map { it.copy(id = m + it.id, categoryId = m + it.categoryId) }
            cats(showCats, lib.showCategories)
            shows += lib.shows.filter { showNames.add(it.name.trim().lowercase()) }.map { it.copy(id = m + it.id, categoryId = m + it.categoryId) }
        }
        return Library(live, channels, filmCats, films, showCats, shows, primary.account, primary.serverAheadSec)
    }
}
