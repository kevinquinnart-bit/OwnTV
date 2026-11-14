package tv.own.owntv.features.stremio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.TrackOption

/**
 * Kevin's copy: the one Play action for add-on streams. The add-ons' answers are put in order
 * (English first, cinema recordings last, then picture size), and each is tried in turn until one
 * actually plays. Once it plays, an English soundtrack and English subtitles are chosen when the
 * file carries them.
 *
 * Web streams are tried first. Torrents come after them, through [TorrentEngine]; ones too big for
 * the box's free space are left out.
 */
object StremioAutoPlay {

    /** How long one stream gets to show its first picture before the next is tried. */
    const val START_TIMEOUT_MS = 20_000L

    /** A torrent has its start downloaded before it is handed to the player, so this is generous. */
    const val TORRENT_START_TIMEOUT_MS = 40_000L

    /** How many web streams are tried before giving up, so a dead title doesn't spin for minutes. */
    const val MAX_TRIES = 6

    /** How many torrents are tried after the web streams. Each can take over a minute to give up. */
    const val MAX_TORRENT_TRIES = 3

    /** A torrent with at least this many people sharing it is treated as well supplied. */
    const val ENOUGH_SEEDERS = 5

    private val splitter = Regex("[^a-z0-9]+")

    /** Words in a stream's name that say the sound is NOT English. */
    private val foreignWords = setOf(
        "ita", "italian", "fre", "french", "vff", "vf", "truefrench", "ger", "german", "deu", "spa", "spanish",
        "latino", "castellano", "esp", "por", "portuguese", "dublado", "rus", "russian", "hin", "hindi", "tam",
        "tamil", "tel", "telugu", "kor", "korean", "jpn", "japanese", "chi", "chinese", "pol", "polish", "tur",
        "turkish", "ukr", "ukrainian", "nl", "dutch", "swe", "swedish", "dan", "danish", "nor", "norwegian",
        "ara", "arabic", "heb", "hebrew", "cze", "czech", "hun", "hungarian", "gre", "greek",
    )
    private val foreignFlags = listOf("🇮🇹", "🇫🇷", "🇩🇪", "🇪🇸", "🇲🇽", "🇵🇹", "🇧🇷", "🇷🇺", "🇮🇳", "🇰🇷", "🇯🇵", "🇨🇳", "🇵🇱", "🇹🇷", "🇺🇦", "🇳🇱")
    private val englishWords = setOf("eng", "english", "multi", "dual")
    private val englishFlags = listOf("🇬🇧", "🇺🇸")

    /** Cinema recordings and other poor copies. */
    private val poorWords = setOf("cam", "camrip", "hdcam", "ts", "hdts", "telesync", "tc", "telecine", "scr", "screener", "dvdscr")

    private fun text(s: StremioAddons.Stream): String = (s.title + " " + (s.detail ?: "")).lowercase()

    /** 0 = English or not labelled with a language, 1 = labelled with another language only. */
    internal fun languageRank(s: StremioAddons.Stream): Int {
        val t = text(s)
        val words = t.split(splitter).toSet()
        val english = words.any { it in englishWords } || englishFlags.any { it in t }
        val foreign = words.any { it in foreignWords } || foreignFlags.any { it in t }
        return if (foreign && !english) 1 else 0
    }

    internal fun isPoorCopy(s: StremioAddons.Stream): Boolean = text(s).split(splitter).any { it in poorWords }

    /** Picture height named in the stream ("2160p", "4K", "1080p"…); 0 when it names none. */
    internal fun height(s: StremioAddons.Stream): Int {
        val words = text(s).split(splitter)
        return when {
            words.any { it == "2160p" || it == "4k" || it == "uhd" } -> 2160
            words.any { it == "1440p" } -> 1440
            words.any { it == "1080p" || it == "fhd" } -> 1080
            words.any { it == "720p" } -> 720
            words.any { it == "480p" || it == "sd" } -> 480
            else -> 0
        }
    }

    /**
     * Best first. Streams an add-on listed earlier stay ahead of equal ones listed later, because
     * add-ons already sort their own answers.
     */
    fun rank(streams: List<StremioAddons.Stream>): List<StremioAddons.Stream> =
        streams.withIndex().sortedWith(
            compareBy<IndexedValue<StremioAddons.Stream>> { languageRank(it.value) }
                .thenBy { if (isPoorCopy(it.value)) 1 else 0 }
                .thenByDescending { height(it.value) }
                .thenBy { it.index },
        ).map { it.value }

    private val seedersPattern = Regex("👤\\s*(\\d+)")
    private val sizePattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(gb|mb)\\b")

    /** People sharing the torrent, when the add-on says ("👤 123"); null when it doesn't. */
    internal fun seeders(s: StremioAddons.Stream): Int? = seedersPattern.find(text(s))?.groupValues?.get(1)?.toIntOrNull()

    /** The file's size: what the add-on reported, else a size written in the stream's text ("2.1 GB"). */
    internal fun sizeBytes(s: StremioAddons.Stream): Long? {
        s.sizeBytes?.let { return it }
        val t = text(s)
        // Add-ons mark the size with a disk; without one, the last size in the text is the file's.
        val marked = if ("💾" in t) sizePattern.find(t, t.indexOf("💾")) else null
        val match = marked ?: sizePattern.findAll(t).lastOrNull() ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = if (match.groupValues[2] == "gb") 1024.0 * 1024 * 1024 else 1024.0 * 1024
        return (number * unit).toLong()
    }

    /**
     * Torrents, best first. Ones known to be bigger than [freeBytes] are dropped: they can't fit on
     * the box. Then English first, cinema recordings last, well-shared ones ahead of poorly shared
     * ones, then picture size, then the add-on's own order.
     */
    fun rankTorrents(streams: List<StremioAddons.Stream>, freeBytes: Long): List<StremioAddons.Stream> =
        streams.filter { (sizeBytes(it) ?: 0L) <= freeBytes }.withIndex().sortedWith(
            compareBy<IndexedValue<StremioAddons.Stream>> { languageRank(it.value) }
                .thenBy { if (isPoorCopy(it.value)) 1 else 0 }
                .thenBy { if ((seeders(it.value) ?: ENOUGH_SEEDERS) >= ENOUGH_SEEDERS) 0 else 1 }
                .thenByDescending { height(it.value) }
                .thenBy { it.index },
        ).map { it.value }

    /** How a "Play best stream" press ended. */
    enum class End { PLAYED, STOPPED, NONE_WORKED, NOTHING_FOUND, TORRENTS_TOO_BIG }

    /**
     * The whole "Play best stream" action: web streams best first, then torrents. [play] must call
     * [OwnTVPlayer.play] with the stream it is given (a torrent arrives with its address filled in).
     * [onTorrentWait] runs once, before the first torrent is prepared, because that takes a while.
     *
     * Returns when playing has ended or nothing would play. A torrent is stopped, and what it
     * downloaded deleted, as soon as the player is closed.
     */
    suspend fun playBest(
        player: OwnTVPlayer,
        found: StremioAddons.Found?,
        engine: TorrentEngine,
        onTorrentWait: () -> Unit,
        play: (StremioAddons.Stream) -> Unit,
    ): End {
        val torrentsFound = found?.torrentStreams.orEmpty()
        val torrents = if (torrentsFound.isEmpty()) emptyList() else rankTorrents(torrentsFound, engine.freeBytes()).take(MAX_TORRENT_TRIES)
        val plan = rank(found?.playable.orEmpty()).take(MAX_TRIES) + torrents
        if (plan.isEmpty()) return if (torrentsFound.isNotEmpty()) End.TORRENTS_TOO_BIG else End.NOTHING_FOUND
        var told = false
        var tooBig = 0
        var prepared = 0
        val outcome = try {
            playFirstWorking(
                player, plan,
                timeoutMs = { if (it.isTorrent) TORRENT_START_TIMEOUT_MS else START_TIMEOUT_MS },
            ) { stream ->
                if (!stream.isTorrent) {
                    play(stream)
                    true
                } else {
                    if (!told) { told = true; onTorrentWait() }
                    when (val opened = engine.open(stream)) {
                        is TorrentEngine.Opened.Ready -> { prepared++; play(stream.copy(url = opened.url)); true }
                        TorrentEngine.Opened.TooBig -> { tooBig++; false }
                        else -> false
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            engine.stop()
            throw e
        }
        return when (outcome) {
            is Outcome.Played -> {
                if (outcome.stream.isTorrent) {
                    try { player.active.first { !it } } finally { engine.stop() }
                }
                End.PLAYED
            }
            Outcome.Stopped -> { engine.stop(); End.STOPPED }
            Outcome.NoneWorked -> {
                engine.stop()
                // Only torrents were on offer and every one was too big once its real size was known.
                if (plan.all { it.isTorrent } && prepared == 0 && tooBig == torrents.size) End.TORRENTS_TOO_BIG else End.NONE_WORKED
            }
        }
    }

    sealed interface Outcome {
        data class Played(val stream: StremioAddons.Stream) : Outcome
        /** The viewer left the player while streams were still being tried. */
        data object Stopped : Outcome
        data object NoneWorked : Outcome
    }

    private enum class Step { STARTED, FAILED, STOPPED }

    /**
     * Starts each of [ranked] with [start] until one shows a picture. [start] must call
     * [OwnTVPlayer.play] for the stream it is given and return true, or return false when the stream
     * couldn't be made ready, which moves straight on to the next. Each stream gets [timeoutMs] to
     * show its first picture.
     */
    suspend fun playFirstWorking(
        player: OwnTVPlayer,
        ranked: List<StremioAddons.Stream>,
        timeoutMs: (StremioAddons.Stream) -> Long = { START_TIMEOUT_MS },
        start: suspend (StremioAddons.Stream) -> Boolean,
    ): Outcome {
        for (stream in ranked) {
            if (!start(stream)) continue
            var seenActive = false
            val step = withTimeoutOrNull(timeoutMs(stream)) {
                combine(player.position, player.duration, player.error, player.active) { pos, dur, err, active ->
                    if (active) seenActive = true
                    when {
                        err != null -> Step.FAILED
                        pos > 0L && dur > 0L -> Step.STARTED
                        seenActive && !active -> Step.STOPPED
                        else -> null
                    }
                }.first { it != null }
            }
            when (step) {
                Step.STARTED -> { chooseEnglish(player); return Outcome.Played(stream) }
                Step.STOPPED -> {
                    // Some failures stop the stream a moment before they report the error.
                    delay(600)
                    if (player.error.value == null) return Outcome.Stopped
                }
                else -> Unit // failed or too slow: try the next one
            }
        }
        return Outcome.NoneWorked
    }

    internal fun isEnglish(t: TrackOption): Boolean {
        val lang = t.lang?.trim()?.lowercase().orEmpty()
        if (lang == "en" || lang == "eng" || lang.startsWith("en-")) return true
        return lang.isEmpty() && t.label.lowercase().contains("english")
    }

    /** English sound if another language is playing; English subtitles if none are showing. */
    private suspend fun chooseEnglish(player: OwnTVPlayer) {
        // The track lists arrive a moment after the first picture.
        var waited = 0L
        while (player.audioTracks().isEmpty() && waited < 3_000L) { delay(250); waited += 250 }
        val audio = player.audioTracks()
        val playing = audio.firstOrNull { it.selected }
        if (playing == null || !isEnglish(playing)) {
            audio.firstOrNull { isEnglish(it) }?.let { player.selectAudio(it.mpvId) }
        }
        val subs = player.textTracks()
        if (subs.none { it.selected && isEnglish(it) }) {
            // A plain English track is better than a "forced" or "signs only" one.
            val english = subs.filter { isEnglish(it) }
            val pick = english.firstOrNull { l -> listOf("forced", "sign", "song").none { it in l.label.lowercase() } } ?: english.firstOrNull()
            pick?.let { player.selectSubtitle(it.mpvId) }
        }
    }
}
