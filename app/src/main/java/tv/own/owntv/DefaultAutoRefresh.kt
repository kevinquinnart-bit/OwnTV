package tv.own.owntv

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Kevin's copy: every playlist (and TV Guide source) refreshes itself every 12 hours unless someone has picked a setting for
 * it. Upstream leaves a playlist on Off until it is set by hand, so the channel list went stale until
 * "Resync now" was pressed. A playlist with any saved choice, Off included, is left exactly as chosen;
 * only playlists with nothing saved get the default. Runs just before the Auto refresh check, so the
 * new default is acted on in the same check.
 */
internal suspend fun applyDefaultPlaylistAutoRefresh(settings: SettingsRepository, sourceDao: SourceDao) {
    runCatching {
        val chosen = settings.playlistAutoRefresh.first().keys
        sourceDao.allSourceIds().filter { it !in chosen }.forEach { id ->
            settings.setPlaylistAutoRefresh(id, PlaylistRefresh(PlaylistAutoRefresh.HOURS_12))
        }
    }
    // The TV Guide's sources get the same treatment: refreshed every 12 hours unless a choice is saved.
    runCatching {
        val epgSources = org.koin.core.context.GlobalContext.get().get<EpgSourceStore>()
        val chosen = settings.epgAutoRefresh.first().keys
        epgSources.getAll().map { it.id }.filter { it !in chosen }.forEach { id ->
            settings.setEpgAutoRefresh(id, EpgRefresh(EpgAutoRefresh.HOURS_12))
        }
    }
}

/**
 * Kevin's copy: any playlist with no channels, films or series, and any TV Guide source with no
 * programmes, starts downloading now. Used at start-up and straight after a Wi-Fi sync, which copies
 * a playlist or guide address but never its contents. An empty list or guide is never left waiting:
 * unlike the timed refresh, this does not hold back while something is playing.
 */
internal suspend fun downloadEmptySources() {
    val koin = org.koin.core.context.GlobalContext.get()
    runCatching {
        val channels = koin.get<tv.own.owntv.core.database.dao.ChannelDao>()
        val movies = koin.get<tv.own.owntv.core.database.dao.MovieDao>()
        val series = koin.get<tv.own.owntv.core.database.dao.SeriesDao>()
        val catalog = koin.get<tv.own.owntv.core.sync.work.CatalogSyncScheduler>()
        koin.get<SourceDao>().allSourceIds().forEach { id ->
            val held = channels.countForSourceOnce(id) + movies.countForSourceOnce(id) + series.countForSourceOnce(id)
            if (held == 0) catalog.enqueueSync(id, reason = EMPTY_FILL, policy = androidx.work.ExistingWorkPolicy.KEEP)
        }
    }
    runCatching {
        val epgDao = koin.get<tv.own.owntv.core.database.dao.EpgDao>()
        val epgSync = koin.get<tv.own.owntv.core.sync.work.EpgSyncScheduler>()
        koin.get<EpgSourceStore>().getAll().forEach { src ->
            if (epgDao.countForSources(listOf(src.id)) == 0) {
                epgSync.enqueueSync(src.id, EMPTY_FILL, 0, androidx.work.ExistingWorkPolicy.KEEP)
            }
        }
    }
}

private const val EMPTY_FILL = "empty_fill"

/**
 * Kevin's copy: refresh anything past its refresh time the moment the app leaves the screen (Home,
 * another app, or the TV going to standby).
 *
 * The timed refresh in core waits for playback to stop, and someone who always has a channel on never
 * gives it that moment: reopening the app resumes the channel, so the guide was never refreshed. With
 * the app off screen nothing is playing, so the download cannot cause a stutter. Each playlist and guide
 * keeps its own Auto refresh choice; Off is respected, and a playlist or guide with nothing saved uses
 * the 12-hour default.
 */
internal suspend fun refreshStaleWhileOffScreen() =
    refreshOlderThan(maxAgeMs = null, reason = OFF_SCREEN, policy = androidx.work.ExistingWorkPolicy.KEEP)

/**
 * Kevin's copy: every time OwnTV is opened — a fresh start, or coming back to the screen — each playlist
 * and TV Guide refreshes, unless it was refreshed in the last [ON_OPEN_MIN_GAP_MS] (so closing and
 * reopening a few times does not download everything again each time). A playlist or guide set to Off
 * is left alone. These refreshes start at once: they do not wait for the channel to stop playing.
 */
internal suspend fun refreshOnOpen() =
    // REPLACE, not KEEP: core's own timed refresh queues its job with KEEP and then holds it until the
    // channel stops playing — and OwnTV reopens straight into the channel. Kept, that waiting job would
    // swallow this one and nothing would download while the channel is on.
    refreshOlderThan(maxAgeMs = ON_OPEN_MIN_GAP_MS, reason = ON_OPEN, policy = androidx.work.ExistingWorkPolicy.REPLACE)

/** [maxAgeMs] null: each source's own Auto refresh time (12 hours when nothing is saved). */
private suspend fun refreshOlderThan(maxAgeMs: Long?, reason: String, policy: androidx.work.ExistingWorkPolicy) {
    val koin = org.koin.core.context.GlobalContext.get()
    val settings = koin.get<SettingsRepository>()
    val now = System.currentTimeMillis()
    val twelveHours = 12 * 3_600_000L
    runCatching {
        val modes = settings.epgAutoRefresh.first()
        val epgDao = koin.get<tv.own.owntv.core.database.dao.EpgDao>()
        val epgSync = koin.get<tv.own.owntv.core.sync.work.EpgSyncScheduler>()
        koin.get<EpgSourceStore>().getAll().forEach { src ->
            val mode = modes[src.id]
            if (mode?.mode == EpgAutoRefresh.OFF) return@forEach
            val threshold = maxAgeMs ?: mode?.thresholdMs ?: twelveHours
            if (now - (src.lastSyncAt ?: 0L) >= threshold) {
                epgSync.enqueueSync(src.id, reason, epgDao.countForSources(listOf(src.id)), policy)
            }
        }
    }
    runCatching {
        val modes = settings.playlistAutoRefresh.first()
        val catalog = koin.get<tv.own.owntv.core.sync.work.CatalogSyncScheduler>()
        koin.get<SourceDao>().getAllOnce().forEach { source ->
            val mode = modes[source.id]
            if (mode?.mode == PlaylistAutoRefresh.OFF) return@forEach
            val threshold = maxAgeMs ?: mode?.thresholdMs ?: twelveHours
            if (now - (source.lastSyncAt ?: 0L) >= threshold) {
                catalog.enqueueSync(
                    source.id,
                    reason = reason,
                    contentTypes = tv.own.owntv.core.sync.SyncContentTypes.enabledOf(source),
                    policy = policy,
                )
            }
        }
    }
}

private const val OFF_SCREEN = "off_screen_refresh"
private const val ON_OPEN = "on_open_refresh"
private const val ON_OPEN_MIN_GAP_MS = 10 * 60_000L
