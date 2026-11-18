package art.kquinn.kuetv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Sound sync: moves the sound against the picture, for a channel or film whose sound is early or late.
 * Positive is the sound later, negative the sound earlier. Kept for each channel and film on its own.
 */
object SoundSync {
    /** The amount one press moves it by. */
    const val STEP_MS = 50
    const val MOST_MS = 2_000

    /** What the playing sound is moved by, in microseconds. Read by the sound as it plays, on its own thread. */
    @Volatile var offsetUs: Long = 0L

    private fun prefs(context: Context) = context.getSharedPreferences("sound_sync", Context.MODE_PRIVATE)

    fun saved(context: Context, key: String): Int = prefs(context).getInt(key, 0)

    fun save(context: Context, key: String, ms: Int) {
        val edit = prefs(context).edit()
        if (ms == 0) edit.remove(key) else edit.putInt(key, ms)
        edit.apply()
    }

    /**
     * The sound's place as the picture is told it: moved on by [offsetUs]. The picture shows each frame when the
     * sound reaches it, so a place moved on shows the picture sooner, which hears the sound later. "Not known
     * yet" is passed on as it is.
     */
    fun shifted(positionUs: Long, offsetUs: Long): Long =
        if (positionUs == AudioSink.CURRENT_POSITION_NOT_SET) positionUs else positionUs + offsetUs

    /** "On time", "Sound 150 ms later", "Sound 100 ms earlier". */
    fun words(ms: Int): String = when {
        ms == 0 -> "On time"
        ms > 0 -> "Sound $ms ms later"
        else -> "Sound " + (-ms) + " ms earlier"
    }
}

/** The player's parts, with its sound moved by [SoundSync.offsetUs]. */
@OptIn(UnstableApi::class)
class SyncedRenderers(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
        val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
        return object : ForwardingAudioSink(sink) {
            override fun getCurrentPositionUs(sourceEnded: Boolean): Long = SoundSync.shifted(super.getCurrentPositionUs(sourceEnded), SoundSync.offsetUs)
        }
    }
}

/** One of the sounds a stream carries: a language, or a commentary, in stereo or surround. */
class SoundChoice(val label: String, val group: TrackGroup, val track: Int, val selected: Boolean, val language: String? = null)

/** The sounds in [tracks] that can be played, in the order the stream lists them. */
@OptIn(UnstableApi::class)
fun soundChoices(tracks: Tracks): List<SoundChoice> {
    val all = ArrayList<SoundChoice>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            all += SoundChoice(soundName(f.language, f.label, f.channelCount, all.size + 1), group.mediaTrackGroup, i, group.isTrackSelected(i), f.language)
        }
    }
    return all
}

/** "English", "English 5.1", "Commentary", or "Sound 2" when the stream says nothing about it. */
fun soundName(language: String?, label: String?, channels: Int, number: Int): String {
    val lang = language?.takeIf { it.isNotBlank() && it != "und" && it != C.LANGUAGE_UNDETERMINED }
        ?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.UK).ifBlank { it } }
    val name = label?.takeIf { it.isNotBlank() } ?: lang ?: "Sound $number"
    val surround = when {
        channels >= 8 -> " 7.1"
        channels >= 6 -> " 5.1"
        else -> ""
    }
    return name + surround
}

/** The picture's shape on the screen: as made, stretched to fill, or zoomed in to fill and cropped. */
object PictureShape {
    val names = listOf("Fit", "Stretch", "Zoom")

    @OptIn(UnstableApi::class)
    fun mode(shape: Int): Int = when (shape) {
        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    fun saved(context: Context): Int = context.getSharedPreferences("player", Context.MODE_PRIVATE).getInt("shape", 0).coerceIn(0, names.size - 1)

    fun save(context: Context, shape: Int) {
        context.getSharedPreferences("player", Context.MODE_PRIVATE).edit().putInt("shape", shape).apply()
    }
}

/** How subtitles look: their size, colour, background and height on the screen. Kept on this device. */
object SubtitleLook {
    val sizes = listOf("Small", "Normal", "Large", "Very large")
    private val sizeScale = listOf(1.0f, 1.4f, 1.8f, 2.2f)
    val colours = listOf("White", "Yellow")
    private val colourValues = listOf(android.graphics.Color.WHITE, 0xFFFFE14D.toInt())
    val backgrounds = listOf("Dark band", "None, outlined letters")
    val heights = listOf("At the bottom", "A little higher")

    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    fun get(context: Context, what: String, default: Int = 0): Int = prefs(context).getInt("subs.$what", default)
    fun set(context: Context, what: String, value: Int) {
        prefs(context).edit().putInt("subs.$what", value).apply()
        Choices.touch()
    }

    fun size(context: Context) = get(context, "size", 1).coerceIn(0, sizes.size - 1)
    fun colour(context: Context) = get(context, "colour").coerceIn(0, colours.size - 1)
    fun background(context: Context) = get(context, "background").coerceIn(0, backgrounds.size - 1)
    fun height(context: Context) = get(context, "height").coerceIn(0, heights.size - 1)

    /** Puts the chosen look on a player's subtitles. */
    @OptIn(UnstableApi::class)
    fun apply(context: Context, view: androidx.media3.ui.SubtitleView) {
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        val band = background(context) == 0
        view.setStyle(
            androidx.media3.ui.CaptionStyleCompat(
                colourValues[colour(context)], if (band) 0xCC000000.toInt() else android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
            ),
        )
        view.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * sizeScale[size(context)])
        view.setBottomPaddingFraction(if (height(context) == 1) 0.18f else androidx.media3.ui.SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
    }
}

/** The sleep timer: when what is playing stops by itself. Not kept: it is for this sitting only. */
object SleepTimer {
    val minutes = listOf(0, 15, 30, 45, 60, 90, 120)

    /** When it stops, or 0 with no timer set. */
    var endAtMs by androidx.compose.runtime.mutableLongStateOf(0L)

    /** The next choice along: off, 15 minutes … 2 hours, then off again. */
    fun next(now: Long = System.currentTimeMillis()) {
        val left = if (endAtMs == 0L) 0 else ((endAtMs - now) / 60_000L).toInt()
        val after = minutes.firstOrNull { it > left + 1 } ?: 0
        endAtMs = if (after == 0) 0L else now + after * 60_000L
    }

    fun words(now: Long = System.currentTimeMillis()): String {
        if (endAtMs == 0L) return "Off"
        val left = ((endAtMs - now + 59_999L) / 60_000L).coerceAtLeast(0)
        return left.toString() + " min · stops at " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(endAtMs))
    }
}

/** The sound language last chosen in a player's Options, tried first on every channel and film after. */
object SoundLanguage {
    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    fun get(context: Context): String = prefs(context).getString("soundLang", "en").orEmpty().ifBlank { "en" }
    fun set(context: Context, language: String?) {
        if (language.isNullOrBlank() || language == "und") return
        prefs(context).edit().putString("soundLang", language).apply()
    }
}
