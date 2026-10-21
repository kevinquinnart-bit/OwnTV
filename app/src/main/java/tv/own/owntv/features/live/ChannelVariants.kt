package tv.own.owntv.features.live

import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Kevin's copy: one channel, several streams.
 *
 * Providers list the same channel more than once, one entry per picture quality ("BBC ONE FHD",
 * "BBC ONE HD", "BBC ONE SD", "BBC ONE"). Those entries are one channel to the viewer. This works out
 * which entries belong together, names the channel without the quality label, and orders each group
 * best picture first, so the list can show one entry and the player can step down to a lighter stream
 * when the best one keeps buffering.
 *
 * Entries group only within one playlist, and only when their names match once the quality labels are
 * removed. Anything else in the name (country prefix, "+1", region) keeps channels apart.
 */
object ChannelVariants {

    // A quality label as a whole word: never part of a longer word or number ("HDTV", "4Music", "E4").
    private const val EDGE_BEFORE = "(?<![\\p{L}\\p{N}])"
    private const val EDGE_AFTER = "(?![\\p{L}\\p{N}])"
    private const val LABELS =
        "UHD|4K|FHD|FULL ?HD|HDR|HEVC|H\\.?26[45]|HD|SD|1080[PI]?|720P|576P|540P|(?:50|60) ?FPS|LQ|HQ|RAW"

    private val QUALITY = Regex("$EDGE_BEFORE(?:$LABELS)$EDGE_AFTER|ᵁᴴᴰ|ᶠᴴᴰ|ᴴᴰ|ˢᴰ|ᴿᴬᵂ|ᴴᴱⱽᶜ", RegexOption.IGNORE_CASE)
    private val EMPTY_BRACKETS = Regex("\\(\\s*\\)|\\[\\s*]|\\{\\s*}")
    private val DOUBLED_SEPARATORS = Regex("\\s*([|•\\-:])(\\s*[|•\\-])+\\s*")
    private val EDGE_SEPARATORS = Regex("^[\\s|•\\-:]+|[\\s|•\\-:]+$")
    private val SPACES = Regex("\\s+")
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}+]+")

    private val RANK_UHD = Regex("${EDGE_BEFORE}(?:UHD|4K)$EDGE_AFTER|ᵁᴴᴰ", RegexOption.IGNORE_CASE)
    private val RANK_FHD = Regex("${EDGE_BEFORE}(?:FHD|FULL ?HD|1080[PI]?)$EDGE_AFTER|ᶠᴴᴰ", RegexOption.IGNORE_CASE)
    private val RANK_HD = Regex("${EDGE_BEFORE}(?:HD|HDR|720P)$EDGE_AFTER|ᴴᴰ", RegexOption.IGNORE_CASE)
    private val RANK_SD = Regex("${EDGE_BEFORE}(?:SD|LQ|576P|540P)$EDGE_AFTER|ˢᴰ", RegexOption.IGNORE_CASE)

    /** The name to show: the provider's name with its quality labels taken out. */
    fun displayName(name: String): String {
        val stripped = name.replace(QUALITY, " ")
            .replace(EMPTY_BRACKETS, " ")
            .replace(DOUBLED_SEPARATORS) { " ${it.groupValues[1]} " }
            .replace(SPACES, " ")
            .replace(EDGE_SEPARATORS, "")
            .trim()
        return stripped.ifBlank { name.trim() }
    }

    /** Picture quality from the name: 4 = UHD, 3 = Full HD, 2 = HD, 1 = no label, 0 = SD. */
    fun rank(name: String): Int = when {
        RANK_UHD.containsMatchIn(name) -> 4
        RANK_FHD.containsMatchIn(name) -> 3
        RANK_HD.containsMatchIn(name) -> 2
        RANK_SD.containsMatchIn(name) -> 0
        else -> 1
    }

    /** Entries with the same key are the same channel. */
    fun groupKey(ch: ChannelEntity): String =
        ch.sourceId.toString() + "|" + displayName(ch.name).uppercase().replace(NOT_ALNUM, " ").trim()
}

/** The grouping for a whole lineup, built once per playlist change. */
class VariantIndex(channels: List<ChannelEntity>) {
    private val keyOf = HashMap<Long, String>()
    private val members = HashMap<String, List<ChannelEntity>>()

    init {
        channels.groupBy(ChannelVariants::groupKey).forEach { (key, list) ->
            if (list.size > 1) {
                // Best picture first; equal quality keeps the provider's order.
                val sorted = list.sortedByDescending { ChannelVariants.rank(it.name) }
                members[key] = sorted
                sorted.forEach { keyOf[it.id] = key }
            }
        }
    }

    /** Every stream of [ch]'s channel, best first; just [ch] when it has no other versions. */
    fun group(ch: ChannelEntity): List<ChannelEntity> = keyOf[ch.id]?.let(members::get) ?: listOf(ch)

    /**
     * Whether [ch] is the one entry to show for its channel, among the versions that [inList] says are
     * in the list being shown. A channel with one version always shows.
     */
    fun isShownIn(ch: ChannelEntity, inList: (ChannelEntity) -> Boolean): Boolean {
        val group = keyOf[ch.id]?.let(members::get) ?: return true
        return (group.firstOrNull { it.id == ch.id || inList(it) } ?: ch).id == ch.id
    }

    companion object {
        val EMPTY = VariantIndex(emptyList())
    }
}
