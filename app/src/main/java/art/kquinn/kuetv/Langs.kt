package art.kquinn.kuetv

/** A language subtitles can be asked for in: its short code, its name, and the other ways sources write it. */
data class Lang(val code: String, val name: String, val also: List<String>)

/** The subtitle languages offered in Settings. Add-ons and subtitle sources name languages in several ways. */
object Langs {
    val all = listOf(
        Lang("en", "English", listOf("eng", "english")),
        Lang("ga", "Irish", listOf("gle", "irish", "gaeilge")),
        Lang("fr", "French", listOf("fre", "fra", "french")),
        Lang("es", "Spanish", listOf("spa", "spanish", "espanol", "español")),
        Lang("de", "German", listOf("ger", "deu", "german", "deutsch")),
        Lang("it", "Italian", listOf("ita", "italian")),
        Lang("pt", "Portuguese", listOf("por", "pob", "portuguese")),
        Lang("pl", "Polish", listOf("pol", "polish")),
        Lang("nl", "Dutch", listOf("dut", "nld", "dutch")),
        Lang("ar", "Arabic", listOf("ara", "arabic")),
        Lang("hi", "Hindi", listOf("hin", "hindi")),
        Lang("tr", "Turkish", listOf("tur", "turkish")),
    )

    fun of(code: String): Lang = all.firstOrNull { it.code == code } ?: all[0]

    /** The language after [lang] in the list, going round to the first. */
    fun after(lang: Lang): Lang = all[(all.indexOf(lang) + 1) % all.size]

    /** Whether [said], a source's own wording ("eng", "en-GB", "English"), means [lang]. */
    fun matches(lang: Lang, said: String): Boolean {
        val s = said.lowercase().trim()
        return s == lang.code || s.startsWith(lang.code + "-") || s.startsWith(lang.code + "_") || lang.also.any { s == it || s.startsWith(it) }
    }
}
