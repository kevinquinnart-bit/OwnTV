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
 * Only streams with a web address are tried. Torrent-only streams can't play here.
 */
object StremioAutoPlay {

    /** How long one stream gets to show its first picture before the next is tried. */
    const val START_TIMEOUT_MS = 20_000L

    /** How many streams are tried before giving up, so a dead title doesn't spin for minutes. */
    const val MAX_TRIES = 6

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

    sealed interface Outcome {
        data class Played(val stream: StremioAddons.Stream) : Outcome
        /** The viewer left the player while streams were still being tried. */
        data object Stopped : Outcome
        data object NoneWorked : Outcome
    }

    private enum class Step { STARTED, FAILED, STOPPED }

    /**
     * Starts each of [ranked] with [start] until one shows a picture. [start] must call
     * [OwnTVPlayer.play] for the stream it is given. Gives up after [MAX_TRIES] streams.
     */
    suspend fun playFirstWorking(
        player: OwnTVPlayer,
        ranked: List<StremioAddons.Stream>,
        start: (StremioAddons.Stream) -> Unit,
    ): Outcome {
        for (stream in ranked.take(MAX_TRIES)) {
            start(stream)
            var seenActive = false
            val step = withTimeoutOrNull(START_TIMEOUT_MS) {
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
