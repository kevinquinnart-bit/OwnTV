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
 *
 * Sky Sports channels also come as NOW TV copies ("Sky Sports Main Event NOW", "NOW TV: Sky Sports F1").
 * On a Sky Sports channel "NOW", "NOW TV" and "NOWTV" count as a label too, so the NOW copy is one more
 * version of the channel, placed after the regular stream of the same quality. Only on Sky Sports, so
 * channels such as "NOW 80s" keep their names.
 */
object ChannelVariants {

    /**
     * The patterns, held apart so that one the phone's pattern engine refuses can never take the app
     * down: if they fail to build, names show as the provider sent them and nothing is grouped.
     */
    private object Patterns {
        // A quality label as a whole word: never part of a longer word or number ("HDTV", "4Music", "E4").
        const val EDGE_BEFORE = "(?<![\\p{L}\\p{N}])"
        const val EDGE_AFTER = "(?![\\p{L}\\p{N}])"
        const val LABELS =
            "UHD|4K|FHD|FULL ?HD|HDR|HEVC|H\\.?26[45]|HD|SD|1080[PI]?|720P|576P|540P|(?:50|60) ?FPS|LQ|HQ|RAW"

        val QUALITY = Regex("$EDGE_BEFORE(?:$LABELS)$EDGE_AFTER|ᵁᴴᴰ|ᶠᴴᴰ|ᴴᴰ|ˢᴰ|ᴿᴬᵂ|ᴴᴱⱽᶜ", RegexOption.IGNORE_CASE)
        // Every bracket escaped: Android's pattern engine refuses a bare ']' or '}' that Java accepts, and
        // refusing one made the whole app crash on start in 1.0.12.
        val EMPTY_BRACKETS = Regex("\\(\\s*\\)|\\[\\s*\\]|\\{\\s*\\}")
        val DOUBLED_SEPARATORS = Regex("\\s*([|•\\-:])(\\s*[|•\\-])+\\s*")
        val EDGE_SEPARATORS = Regex("^[\\s|•\\-:]+|[\\s|•\\-:]+$")
        val SPACES = Regex("\\s+")
        val NOT_ALNUM = Regex("[^\\p{L}\\p{N}+]+")

        // Kevin's copy: the NOW TV copies of Sky Sports channels.
        val NOW_TV = Regex("${EDGE_BEFORE}NOW ?TV$EDGE_AFTER|${EDGE_BEFORE}NOW$EDGE_AFTER", RegexOption.IGNORE_CASE)
        val SKY_SPORTS = Regex("${EDGE_BEFORE}SKY ?SPORTS?$EDGE_AFTER", RegexOption.IGNORE_CASE)

        val RANK_UHD = Regex("${EDGE_BEFORE}(?:UHD|4K)$EDGE_AFTER|ᵁᴴᴰ", RegexOption.IGNORE_CASE)
        val RANK_FHD = Regex("${EDGE_BEFORE}(?:FHD|FULL ?HD|1080[PI]?)$EDGE_AFTER|ᶠᴴᴰ", RegexOption.IGNORE_CASE)
        val RANK_HD = Regex("${EDGE_BEFORE}(?:HD|HDR|720P)$EDGE_AFTER|ᴴᴰ", RegexOption.IGNORE_CASE)
        val RANK_SD = Regex("${EDGE_BEFORE}(?:SD|LQ|576P|540P)$EDGE_AFTER|ˢᴰ", RegexOption.IGNORE_CASE)
    }

    private val patterns: Patterns? by lazy { runCatching { Patterns.QUALITY; Patterns }.getOrNull() }

    /** The name to show: the provider's name with its quality labels taken out. */
    fun displayName(name: String): String {
        val p = patterns ?: return name
        val stripped = runCatching {
            (if (p.SKY_SPORTS.containsMatchIn(name)) name.replace(p.NOW_TV, " ") else name)
                .replace(p.QUALITY, " ")
                .replace(p.EMPTY_BRACKETS, " ")
                .replace(p.DOUBLED_SEPARATORS) { " ${it.groupValues[1]} " }
                .replace(p.SPACES, " ")
                .replace(p.EDGE_SEPARATORS, "")
                .trim()
        }.getOrDefault(name)
        return stripped.ifBlank { name.trim() }
    }

    /** Picture quality from the name: 4 = UHD, 3 = Full HD, 2 = HD, 1 = no label, 0 = SD. */
    fun rank(name: String): Int {
        val p = patterns ?: return 1
        return when {
            p.RANK_UHD.containsMatchIn(name) -> 4
            p.RANK_FHD.containsMatchIn(name) -> 3
            p.RANK_HD.containsMatchIn(name) -> 2
            p.RANK_SD.containsMatchIn(name) -> 0
            else -> 1
        }
    }

    /** True for the NOW TV copy of a Sky Sports channel. */
    fun isNowCopy(name: String): Boolean {
        val p = patterns ?: return false
        return runCatching { p.SKY_SPORTS.containsMatchIn(name) && p.NOW_TV.containsMatchIn(name) }.getOrDefault(false)
    }

    /** Entries with the same key are the same channel. Without the patterns, nothing groups. */
    fun groupKey(ch: ChannelEntity): String {
        val p = patterns ?: return "id|${ch.id}"
        return ch.sourceId.toString() + "|" + displayName(ch.name).uppercase().replace(p.NOT_ALNUM, " ").trim()
    }
}

/** The grouping for a whole lineup, built once per playlist change. */
class VariantIndex(channels: List<ChannelEntity>) {
    private val keyOf = HashMap<Long, String>()
    private val members = HashMap<String, List<ChannelEntity>>()

    init {
        channels.groupBy(ChannelVariants::groupKey).forEach { (key, list) ->
            if (list.size > 1) {
                // Best picture first; at equal quality the regular stream comes before a NOW TV copy,
                // then the provider's order.
                val sorted = list.sortedWith(
                    compareByDescending<ChannelEntity> { ChannelVariants.rank(it.name) }
                        .thenBy { ChannelVariants.isNowCopy(it.name) },
                )
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
