package art.kquinn.kuetv

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The channels, films and series the provider last sent, kept on this TV or phone, so the app opens
 * on them at once and doesn't wait for the provider to send them all again. They are fetched afresh in
 * the background when the kept ones are more than a few hours old, and whenever the user reloads.
 */
object LibraryCache {
    /** Kept lists older than this are fetched again in the background when the app opens. */
    const val FRESH_MS = 6L * 60 * 60 * 1000

    /** What is written to the file. [who] says whose lists they are, so another account's are never shown. */
    private class Kept(
        val who: String = "", val at: Long = 0, val ahead: Int = 0,
        val liveCategories: List<Category>? = null, val channels: List<Channel>? = null,
        val filmCategories: List<Category>? = null, val films: List<Film>? = null,
        val showCategories: List<Category>? = null, val shows: List<Show>? = null,
    )

    /** The lists read back, and when they were fetched from the provider. */
    class Read(val library: Library, val at: Long)

    fun file(context: Context) = File(context.filesDir, "library.json.gz")

    private fun who(account: Account): String = Integer.toHexString((account.server + "\n" + account.user + "\n" + account.pass + "\n" + account.playlist).hashCode())

    fun write(file: File, account: Account, library: Library, at: Long = System.currentTimeMillis()) {
        val part = File(file.path + ".part")
        OutputStreamWriter(GZIPOutputStream(part.outputStream()), Charsets.UTF_8).use { out ->
            Gson().toJson(
                Kept(who(account), at, library.serverAheadSec, library.liveCategories, library.channels, library.filmCategories, library.films, library.showCategories, library.shows),
                out,
            )
        }
        file.delete()
        part.renameTo(file)
    }

    /** The kept lists, if there are any and they are [account]'s; null otherwise, or when the file can't be read. */
    fun read(file: File, account: Account): Read? = runCatching {
        if (!file.exists()) return null
        val kept = InputStreamReader(GZIPInputStream(file.inputStream()), Charsets.UTF_8).use { Gson().fromJson(it, Kept::class.java) } ?: return null
        if (kept.who != who(account)) return null
        val channels = kept.channels ?: return null
        val films = kept.films ?: return null
        if (channels.isEmpty() && films.isEmpty()) return null
        Read(
            Library(
                kept.liveCategories.orEmpty(), channels, kept.filmCategories.orEmpty(), films, kept.showCategories.orEmpty(), kept.shows.orEmpty(),
                // A playlist link has no login to ask the guide or catch-up with.
                account.takeIf { it.isLogin }, kept.ahead,
            ),
            kept.at,
        )
    }.getOrNull()

    /** Whether two sets of lists hold the same channels, films and series. */
    fun same(a: Library, b: Library): Boolean =
        a.serverAheadSec == b.serverAheadSec && a.channels == b.channels && a.films == b.films && a.shows == b.shows &&
            a.liveCategories == b.liveCategories && a.filmCategories == b.filmCategories && a.showCategories == b.showCategories
}
