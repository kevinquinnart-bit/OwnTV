package art.kquinn.kuesounds

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/**
 * The screens' side of the player: what is playing, and the buttons. The music itself plays in
 * [PlaybackService], which carries on when the screens close.
 */
object Playing {
    private var controller: MediaController? = null

    var currentId by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var artwork by mutableStateOf<Uri?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var problem by mutableStateOf("")
        private set

    /** Whether anything is loaded to play. */
    val active: Boolean get() = currentId.isNotEmpty()

    private lateinit var appContext: Context

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(player)
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    problem = "That song won't play."
                }
            })
            refresh(c)
        }, MoreExecutors.directExecutor())
    }

    private fun refresh(p: Player) {
        val item = p.currentMediaItem
        currentId = item?.mediaId.orEmpty()
        val m = p.mediaMetadata
        title = m.title?.toString().orEmpty()
        artist = m.artist?.toString().orEmpty()
        album = m.albumTitle?.toString().orEmpty()
        artwork = m.artworkUri
        isPlaying = p.isPlaying
        buffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.takeIf { it > 0 } ?: 0L
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        if (p.isPlaying) problem = ""
    }

    /** Where the song has got to, read when the screen asks. */
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun item(t: Track): MediaItem {
        val art = Scanner.artForTrack(t)
        // A song saved on the phone plays from there; the rest from the Drive.
        val local = Offline.fileFor(t)
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(local?.let { Uri.fromFile(it).toString() } ?: Drive.mediaUrl(t.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setAlbumArtist(t.albumArtist.ifBlank { null })
                    .setTrackNumber(t.trackNo.takeIf { it > 0 })
                    .setArtworkUri(art?.let { Uri.fromFile(it) })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
    }

    /** Plays [list] from the song at [start], in order, or shuffled. */
    fun play(list: List<Track>, start: Int, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        // With no internet, only the songs saved on the phone can play: the rest are left out.
        if (!Net.online(appContext)) {
            val chosen = list.getOrNull(start)
            val onPhone = list.filter { it.id in Offline.saved }
            if (onPhone.isEmpty()) {
                problem = "No internet, and these songs aren't saved on the phone."
                return
            }
            return play(onPhone, chosen?.let { onPhone.indexOf(it) }?.takeIf { it >= 0 } ?: 0, shuffled)
        }
        c.shuffleModeEnabled = shuffled
        val first = if (shuffled) list.indices.random() else start.coerceIn(0, list.size - 1)
        c.setMediaItems(list.map { item(it) }, first, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    /** Back to the start of the song, or to the song before when it has only just begun. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun flipShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Off, then the whole list again and again, then this one song again and again. */
    fun nextRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
