package tv.own.owntv.features.more

/**
 * Kevin's copy: which of the free UK and Irish players ([FreePlayers.all], by position) carries a
 * channel, going by the channel's name; null when none does. Plain text rules only, so it can be
 * tested without a device.
 */
internal object PlayerForChannel {
    const val BBC = 0
    const val ITV = 1
    const val C4 = 2
    const val C5 = 3
    const val RTE = 4
    const val VIRGIN = 5

    fun indexFor(channelName: String): Int? {
        // Lower case, accents off the one letter that matters, everything but letters and digits to spaces.
        val name = " " + channelName.lowercase().replace('é', 'e').map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ") + " "
        fun has(vararg words: String) = words.any { name.contains(" $it ") }
        fun starts(vararg starts: String) = starts.any { name.contains(" $it") }
        return when {
            has("bbc", "cbbc", "cbeebies") -> BBC
            starts("rte") -> RTE
            has("virgin") -> VIRGIN
            starts("itv") || has("utv", "stv") -> ITV
            has("channel 4", "channel4", "e4", "more4", "more 4", "film4", "film 4", "4seven") -> C4
            has("channel 5", "channel5", "5usa", "5 usa", "5star", "5 star", "5action", "5 action", "5select", "5 select") -> C5
            else -> null
        }
    }
}
