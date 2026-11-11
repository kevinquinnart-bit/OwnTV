package tv.own.owntv.features.shell.components

import androidx.room.useReaderConnection
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.OwnTVDatabase

/**
 * Kevin's copy: "More like this" and "With <actor>" for films and shows, from the library on this box.
 *
 * Both read only what is already stored — the provider's catalogue and the TMDB details the app has
 * fetched for titles that have been looked at. No new TMDB calls, so nothing is spent or rate-limited.
 * An actor's other titles can therefore only be found among titles whose details have been fetched;
 * the list grows as more of the library is browsed.
 *
 * Details are linked to a library title through `metadata_match` (local key → TMDB id) and
 * `metadata_cache` (TMDB id → genres, cast). The local key is "movie:<sourceId>:<remoteId or name>"
 * for a film and "tv:<sourceId>:<remoteId or name>" for a show, with ":kids" added on a profile that
 * hides adult titles.
 */
object LibraryLinks {

    enum class Kind(val type: String, val table: String) { MOVIE("movie", "movies"), SERIES("tv", "series") }

    /** A title found: its library id, and how strongly it matched (higher first). */
    private data class Hit(val id: Long, val score: Int, val rating: Double)

    private val db: OwnTVDatabase get() = GlobalContext.get().get()

    /** Genre names from a cached `genresJson`. */
    fun genres(json: String?): List<String> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    /**
     * Titles like the one given: those sharing the most genres first, then the best rated in the
     * same provider category. [excludeId] is the title itself.
     */
    suspend fun moreLikeThis(
        kind: Kind,
        excludeId: Long,
        categoryId: Long?,
        genresJson: String?,
        sourceIds: List<Long>,
    ): List<Long> {
        if (sourceIds.isEmpty()) return emptyList()
        val wanted = genres(genresJson)
        val hits = LinkedHashMap<Long, Hit>()
        if (wanted.isNotEmpty()) {
            val where = wanted.joinToString(" OR ") { "mc.genresJson LIKE ?" }
            val rows = db.useReaderConnection { c ->
                c.usePrepared(
                    "SELECT DISTINCT mm.localKey, mc.genresJson, mc.rating FROM metadata_cache mc " +
                        "JOIN metadata_match mm ON mm.tmdbId = mc.tmdbId AND mm.type = ? " +
                        "WHERE mc.type = ? AND ($where) LIMIT $SCAN_LIMIT",
                ) { st ->
                    st.bindText(1, kind.type)
                    st.bindText(2, kind.type)
                    wanted.forEachIndexed { i, g -> st.bindText(3 + i, "%" + JSONObject.quote(g) + "%") }
                    buildList {
                        while (st.step()) {
                            val g = if (st.isNull(1)) null else st.getText(1)
                            val r = if (st.isNull(2)) 0.0 else st.getDouble(2)
                            add(Triple(st.getText(0), g, r))
                        }
                    }
                }
            }
            val ids = idsFor(kind, rows.map { it.first }, sourceIds)
            rows.forEach { (key, g, r) ->
                val id = ids[key] ?: return@forEach
                if (id == excludeId) return@forEach
                val shared = genres(g).count { it in wanted }
                val prev = hits[id]
                if (prev == null || shared > prev.score) hits[id] = Hit(id, shared, r)
            }
        }
        val ranked = hits.values.sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.rating }).map { it.id }
        val out = ranked.take(LIMIT).toMutableList()
        // Topped up from the same provider category, best rated first.
        if (out.size < LIMIT && categoryId != null) {
            val more = db.useReaderConnection { c ->
                c.usePrepared(
                    "SELECT id FROM ${kind.table} WHERE categoryId = ? AND id != ? " +
                        "ORDER BY rating IS NULL, rating DESC, id DESC LIMIT $LIMIT",
                ) { st ->
                    st.bindLong(1, categoryId)
                    st.bindLong(2, excludeId)
                    buildList { while (st.step()) add(st.getLong(0)) }
                }
            }
            more.forEach { if (out.size < LIMIT && it !in out) out += it }
        }
        return out
    }

    /** Library titles whose fetched details name [person] in the cast, best rated first. */
    suspend fun withPerson(kind: Kind, person: String, excludeId: Long?, sourceIds: List<Long>): List<Long> {
        if (person.isBlank() || sourceIds.isEmpty()) return emptyList()
        val rows = db.useReaderConnection { c ->
            c.usePrepared(
                "SELECT DISTINCT mm.localKey, mc.rating FROM metadata_cache mc " +
                    "JOIN metadata_match mm ON mm.tmdbId = mc.tmdbId AND mm.type = ? " +
                    "WHERE mc.type = ? AND mc.castJson LIKE ? LIMIT $SCAN_LIMIT",
            ) { st ->
                st.bindText(1, kind.type)
                st.bindText(2, kind.type)
                // Both cast formats hold the name as a JSON string: ["Name"] or [{"n":"Name"}].
                st.bindText(3, "%" + JSONObject.quote(person) + "%")
                buildList { while (st.step()) add(st.getText(0) to (if (st.isNull(1)) 0.0 else st.getDouble(1))) }
            }
        }
        val ids = idsFor(kind, rows.map { it.first }, sourceIds)
        return rows.mapNotNull { (key, r) -> ids[key]?.takeIf { it != excludeId }?.let { it to r } }
            .distinctBy { it.first }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(LIMIT)
    }

    /** Local keys → library ids, keeping only titles in [sourceIds]. */
    private suspend fun idsFor(kind: Kind, keys: List<String>, sourceIds: List<Long>): Map<String, Long> {
        val allowed = sourceIds.toHashSet()
        val parsed = keys.mapNotNull { key ->
            val bare = key.removeSuffix(KIDS_SUFFIX)
            val parts = bare.split(":", limit = 3)
            if (parts.size < 3 || parts[0] != kind.type) return@mapNotNull null
            val source = parts[1].toLongOrNull()?.takeIf { it in allowed } ?: return@mapNotNull null
            Triple(key, source, parts[2])
        }
        if (parsed.isEmpty()) return emptyMap()
        return db.useReaderConnection { c ->
            c.usePrepared(
                "SELECT id FROM ${kind.table} WHERE sourceId = ? AND (remoteId = ? OR (remoteId IS NULL AND name = ?)) LIMIT 1",
            ) { st ->
                val out = HashMap<String, Long>()
                parsed.forEach { (key, source, rest) ->
                    st.reset()
                    st.clearBindings()
                    st.bindLong(1, source)
                    st.bindText(2, rest)
                    st.bindText(3, rest)
                    if (st.step()) out[key] = st.getLong(0)
                }
                out
            }
        }
    }

    private const val KIDS_SUFFIX = ":kids"
    private const val LIMIT = 24
    private const val SCAN_LIMIT = 400
}
