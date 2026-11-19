package art.kquinn.kuetv

/**
 * Live TV's categories in a handful of topics, worked out from each category's name: the provider's
 * "UK | Sport", "IE | Sport" and a free list's "Sports" all go under Sport, and so on. Free channels go in
 * with the rest, so there is one Sport, one News, and so on, instead of a long list of near-repeats.
 */
object Topics {
    const val PREFIX = "topic:"

    class Topic(val key: String, val label: String, val words: List<String>)

    // In the order they are checked: "kids movies" is Kids, "sports news" is Sport.
    private val checked = listOf(
        Topic("kids", "🧸 Kids", listOf("kid", "child", "cartoon", "junior", "toon", "cbeebies", "cbbc", "nick", "disney", "family")),
        Topic("sport", "⚽ Sport", listOf("sport", "football", "soccer", "racing", "golf", "f1", "formula", "boxing", "ufc", "wwe", "nfl", "nba", "nhl", "mlb", "cricket", "rugby", "tennis", "darts", "snooker", "ppv", "premier league", "laliga", "bein", "dazn", "tnt sport", "espn", "eurosport", "fight", "event", "flosport", "epl")),
        Topic("news", "📰 News", listOf("news", "politic", "weather", "parliament", "business")),
        Topic("docs", "🌍 Documentaries", listOf("doc", "history", "nature", "science", "discovery", "geographic", "nat geo", "wildlife", "travel", "knowledge", "learning", "crime", "investigation", "educat", "culture", "outdoor")),
        Topic("music", "🎵 Music", listOf("music", "radio", "hits", "concert", "mtv", "vevo")),
        Topic("films", "🎬 Films", listOf("movie", "film", "cinema", "box office", "vod")),
        Topic("ent", "📺 Entertainment", listOf("entertain", "general", "drama", "comedy", "reality", "lifestyle", "variety", "series", "classic", "food", "home", "religio", "shopping", "local", "regional", "freeview", "saorview", "uk", "ireland", "irish", "bbc", "itv", "english", "united kingdom", "paramount")),
    )
    private val other = Topic("other", "📁 Everything else", emptyList())

    /** In the order they show in the categories column. */
    val shown: List<Topic> = listOf("sport", "news", "ent", "films", "kids", "docs", "music").map { k -> checked.first { it.key == k } } + other

    /** The topic a category's name belongs to. */
    fun of(name: String): String {
        val n = " " + name.lowercase() + " "
        return checked.firstOrNull { t -> t.words.any { n.contains(it) } }?.key ?: other.key
    }

    /** Each category's topic, by category id. */
    fun byCategory(categories: List<Category>): Map<String, String> = categories.associate { it.id to of(it.name.removePrefix("Free: ")) }

    /** The topics that have channels in them, as categories for the column. */
    fun categories(categoryTopic: Map<String, String>, usedCategoryIds: Set<String>): List<Category> {
        val used = usedCategoryIds.mapNotNullTo(HashSet()) { categoryTopic[it] }
        return shown.filter { it.key in used }.map { Category(PREFIX + it.key, it.label) }
    }
}
