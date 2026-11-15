package tv.own.owntv.features.more

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.live.CatchupManualTimeDialog
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberBestDateFormatter
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageGroupItem
import tv.own.owntv.ui.stage.StagePopupIcon
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Kevin's copy (v1): the Catch-up page. Left, the channels the provider marks as having catch-up.
 * Right, for the chosen channel: "Pick a date and time", then the programmes already shown, newest
 * first under a heading for each day. OK on a programme plays it from its start; a picked time plays
 * the channel from that moment. The programme list reaches back only as far as the guide the app holds;
 * the date and time picker reaches the whole of the provider's archive.
 */
@Composable
internal fun CatchupPage(
    entry: FocusRequester,
    loadChannels: suspend () -> List<ChannelEntity>,
    loadProgrammes: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    windowSecOf: (ChannelEntity) -> Int,
    onPlayProgramme: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onPlayAt: (ChannelEntity, Int) -> Unit,
) {
    val channels by produceState<List<ChannelEntity>?>(null) {
        value = runCatching { loadChannels() }.getOrDefault(emptyList())
    }
    var chosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val list = channels
    if (list == null) {
        Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.kevin_menu_catchup_none), style = stageText(22, 500), color = StageColors.Muted)
        return
    }
    val chosen = list.firstOrNull { it.id == chosenId } ?: list.first()
    var pickTime by remember { mutableStateOf(false) }
    if (pickTime) {
        CatchupManualTimeDialog(
            windowSec = windowSecOf(chosen),
            onPick = { offset -> pickTime = false; onPlayAt(chosen, offset) },
            onDismiss = { pickTime = false },
        )
    }
    val programmes by produceState<List<EpgProgrammeEntity>?>(null, chosen.id) {
        value = null
        value = runCatching { loadProgrammes(chosen) }.getOrDefault(emptyList())
    }
    val dayOf = rememberBestDateFormatter("EEEMMMd")
    val timeOf = rememberSystemTimeFormatter()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
        Column(Modifier.width(400.mpx).fillMaxHeight()) {
            PageLabel(stringResource(R.string.kevin_catchup_channels), Modifier.padding(bottom = 12.mpx))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).focusGroup()) {
                itemsIndexed(list, key = { _, ch -> ch.id }) { i, ch ->
                    StageGroupItem(
                        text = ch.name,
                        icon = OwnTVIcon.LIVE_TV,
                        selected = ch.id == chosen.id,
                        onClick = { chosenId = ch.id },
                        modifier = if (i == 0) Modifier.focusRequester(entry) else Modifier,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PageLabel(chosen.name, Modifier.padding(bottom = 12.mpx))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
                item(key = -1L) {
                    StagePopupOption(
                        title = stringResource(R.string.kevin_catchup_pick_time),
                        onClick = { pickTime = true },
                        chosen = true,
                        leading = { StagePopupIcon(OwnTVIcon.CLOCK) },
                    )
                }
                val shown = programmes
                if (shown == null) {
                    item(key = -2L) {
                        Box(Modifier.fillMaxWidth().height(100.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                    }
                } else if (shown.isEmpty()) {
                    item(key = -3L) {
                        Text(
                            stringResource(R.string.kevin_catchup_no_guide), style = stageText(18, 500), color = StageColors.Muted,
                            modifier = Modifier.padding(start = 16.mpx, top = 14.mpx),
                        )
                    }
                } else {
                    var lastDay: String? = null
                    shown.forEach { programme ->
                        val day = dayOf(programme.startMs)
                        if (day != lastDay) {
                            lastDay = day
                            // A day's first programme start, negated and offset, cannot meet a programme id.
                            item(key = -1000L - programme.startMs) {
                                PageLabel(day, Modifier.padding(start = 16.mpx, top = 18.mpx, bottom = 6.mpx))
                            }
                        }
                        item(key = programme.id) {
                            StagePopupOption(
                                title = programme.title,
                                subtitle = stringResource(R.string.kevin_catchup_time_range, timeOf(programme.startMs), timeOf(programme.stopMs)),
                                onClick = { onPlayProgramme(chosen, programme) },
                                leading = { StagePopupIcon(OwnTVIcon.CATCHUP) },
                            )
                        }
                    }
                }
            }
        }
    }
}
