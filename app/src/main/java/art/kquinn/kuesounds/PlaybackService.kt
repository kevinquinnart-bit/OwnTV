package art.kquinn.kuesounds

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The player itself. It runs on its own, so music carries on with the screen off or another app open,
 * shows on the lock screen and in the pull-down, answers headphone and car buttons, and pauses when
 * headphones are pulled out. Each song plays straight from the Drive with the current sign-in.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var retried = ""

    override fun onCreate() {
        super.onCreate()
        Google.init(this)
        Scanner.init(this)
        val upstream = OkHttpDataSource.Factory(Drive.http)
        // Every request for a song carries the current Drive pass, fetched fresh when the old one has run out.
        val withPass = ResolvingDataSource.Factory(upstream) { spec ->
            val token = Google.tokenBlocking().orEmpty()
            spec.withAdditionalHeaders(mapOf("Authorization" to "Bearer $token"))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(withPass))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause
                val id = player.currentMediaItem?.mediaId.orEmpty()
                // The pass ran out: drop it and try the same song once more with a fresh one.
                if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401 && retried != id) {
                    retried = id
                    val at = player.currentPosition
                    scope.launch {
                        withContext(Dispatchers.IO) { Google.forget() }
                        player.seekTo(at)
                        player.prepare()
                        player.play()
                    }
                    return
                }
                // A song that won't play is skipped for the next one.
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping KueSounds away stops it only when nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
