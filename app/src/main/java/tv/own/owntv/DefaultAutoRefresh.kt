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
