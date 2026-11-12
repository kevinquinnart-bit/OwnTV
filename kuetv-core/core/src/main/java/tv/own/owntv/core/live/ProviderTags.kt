package tv.own.owntv.core.live

import java.text.Normalizer

/**
 * A provider name split the way Stage shows it (G5): "DE| Sky Cinema Premieren ᴴᴰ" becomes the name
 * "Sky Cinema Premieren", the tag "HD" and the country "DE". Providers write their quality and
 * source marks as superscript letters; each run of them is one tag. The country prefix is shown
 * once, as the "GROUPS · DE" heading, instead of on every row.
 */
data class ProviderName(val name: String, val tags: List<String>, val country: String?)

object ProviderTags {
    // "DE|", "DE |", "UK |" — two or three capitals and a bar at the very start.
    private val countryPrefix = Regex("""^([A-Z]{2,3})\s*\|\s*""")

    fun parse(raw: String): ProviderName {
        val prefix = countryPrefix.find(raw)
        var rest = if (prefix != null) raw.substring(prefix.range.last + 1) else raw
        val tags = mutableListOf<String>()
        val name = StringBuilder()
        val run = StringBuilder()
        fun endRun() {
            if (run.isEmpty()) return
            val tag = Normalizer.normalize(run, Normalizer.Form.NFKC).uppercase()
            if (tag !in tags) tags += tag
            run.clear()
        }
        for (ch in rest) {
            if (isSuperscript(ch)) run.append(ch) else { endRun(); name.append(ch) }
        }
        endRun()
        rest = name.toString().replace(Regex("""\s+"""), " ").trim()
        // A name that was nothing but a prefix and tags keeps the provider's own spelling.
        if (rest.isEmpty()) return ProviderName(raw.trim(), emptyList(), null)
        return ProviderName(rest, tags, prefix?.groupValues?.get(1))
    }

    /**
     * The one country every group shares, for the "GROUPS · DE" heading; null when they differ or
     * any group has none (then the heading reads just "GROUPS").
     */
    fun sharedCountry(names: List<ProviderName>): String? =
        names.map { it.country }.distinct().singleOrNull()

    // Superscript digits and letters, and the small-capital/modifier letters providers use for them
    // (ᴴᴰ, ʳᴬᵂ, ⁴ᴷ). Each folds to a plain letter or digit under NFKC.
    private fun isSuperscript(c: Char): Boolean =
        c in 'ᴬ'..'ᵪ' || c in 'ʰ'..'ʸ' || c in 'ˠ'..'ˤ' ||
            c in '⁰'..'ₜ' || c == '¹' || c == '²' || c == '³' ||
            c == 'ᶜ' || c == 'ᶠ' || c == 'ᶻ' || c == 'ꟸ' || c == 'ꟹ'
}
