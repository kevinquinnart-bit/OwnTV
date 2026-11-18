package art.kquinn.kuetv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What is on each channel, for the line under a channel's name in the lists and for the player's name
 * box. A channel's guide is asked for once and kept for ten minutes, and only a few are asked for at a
 * time, so scrolling a long list doesn't flood the provider.
 */
object NowNext {
    private const val KEEP_MS = 10 * 60_000L
    private val kept = ConcurrentHashMap<String, Pair<Long, List<Programme>>>()
    private val gate = Semaphore(4)

    /** The guide already in hand for a channel, if it is fresh enough. */
    fun cached(channelId: String): List<Programme>? = kept[channelId]?.takeIf { System.currentTimeMillis() - it.first < KEEP_MS }?.second

    /** For the automatic checks only: a channel's guide, as if it had just been fetched. */
    internal fun keepForChecks(channelId: String, guide: List<Programme>) {
        kept[channelId] = System.currentTimeMillis() to guide
    }

    suspend fun get(account: Account, channelId: String): List<Programme> {
        // A channel from a free list isn't one the provider has a guide for.
        if (channelId.startsWith(Library.FREE)) return emptyList()
        cached(channelId)?.let { return it }
        return gate.withPermit {
            cached(channelId) ?: withContext(Dispatchers.IO) { Provider.guide(account, channelId) }.also { kept[channelId] = System.currentTimeMillis() to it }
        }
    }

    /** "Now: The News · Next 21:00: Film", or null when the guide has nothing for this moment. */
    fun line(guide: List<Programme>, at: Long, clock: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())): String? {
        val onNow = guide.firstOrNull { it.startMs <= at && it.stopMs > at }
        val next = guide.firstOrNull { it.startMs >= (onNow?.stopMs ?: at) }
        val now = onNow?.let { "Now: " + it.title.ifBlank { "No title" } }
        val then = next?.let { "Next " + clock.format(Date(it.startMs)) + ": " + it.title.ifBlank { "No title" } }
        return listOfNotNull(now, then).joinToString(" · ").ifEmpty { null }
    }
}
