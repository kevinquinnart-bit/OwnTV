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
class SoundChoice(val label: String, val group: TrackGroup, val track: Int, val selected: Boolean)

/** The sounds in [tracks] that can be played, in the order the stream lists them. */
@OptIn(UnstableApi::class)
fun soundChoices(tracks: Tracks): List<SoundChoice> {
    val all = ArrayList<SoundChoice>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            all += SoundChoice(soundName(f.language, f.label, f.channelCount, all.size + 1), group.mediaTrackGroup, i, group.isTrackSelected(i))
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
