package art.kquinn.kuetv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** The free channel lists the user has added, by their links, kept on this TV or phone. */
object FreeStore {
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("free", Context.MODE_PRIVATE)

    fun all(context: Context): List<String> = prefs(context).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun save(context: Context, list: List<String>) {
        prefs(context).edit().putString("list", list.joinToString("\n")).apply()
        changes++
    }

    fun add(context: Context, link: String) = save(context, all(context).filter { it != link } + link)
    fun remove(context: Context, link: String) = save(context, all(context).filter { it != link })
    fun replaceAll(context: Context, list: List<String>) = save(context, list)

    /** A link as typed or pasted, made into a web address; null when it can't be one. */
    fun tidy(typed: String): String? {
        var link = typed.trim()
        if (link.isEmpty() || link.contains(' ')) return null
        if (!link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true)) link = "https://$link"
        return link
    }

    /** Lists that can be added with one press. Free channels from iptv-org, gathered by country. */
    val ready = listOf(
        "UK and Ireland free channels (Free-TV)" to "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8#groups=UK,Ireland",
        "UK free channels (iptv-org)" to "https://iptv-org.github.io/iptv/countries/uk.m3u",
        "Ireland free channels (iptv-org)" to "https://iptv-org.github.io/iptv/countries/ie.m3u",
    )

    /** A short name for a list, for rows and the health check: where it comes from and its file's name. */
    fun label(link: String): String {
        ready.firstOrNull { it.second == link }?.let { return it.first }
        if (link.endsWith("iptv-org.github.io/iptv/index.country.m3u")) return "Every country's free channels (very large)"
        val rest = link.substringAfter("://").substringBefore('?')
        val host = rest.substringBefore('/')
        val file = rest.substringAfterLast('/', "").takeIf { it.isNotBlank() && it != host }
        return if (file == null) host else "$host · $file"
    }
}
