package tv.own.owntv.features.drive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.settings.BackupViewModel
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * Kevin's copy: Google's TV sign-in. The code and the address to enter it at, with a QR of the address
 * for the phone's camera. Closes by itself once the phone side is finished.
 */
@Composable
internal fun DriveSignInPopup(state: BackupViewModel.DriveSignIn, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val code = state.code
    val ended = state.denied || state.expired || state.failed != null
    val retryButton: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { focus.requestFocus() } }
        tv.own.owntv.ui.stage.StageButton(
            stringResource(R.string.kevin_drive_try_again), onClick = onRetry, height = 56.mpx, textSize = 19, tinted = true,
            modifier = Modifier.focusRequester(focus),
        )
    }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.kevin_drive_sign_in_title),
        buttons = if (ended) retryButton else null,
    ) {
        when {
            code == null && state.failed == null -> Box(Modifier.fillMaxWidth().height(120.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 32) }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(32.mpx), verticalAlignment = Alignment.CenterVertically) {
                val url = code?.verificationUrl.orEmpty()
                val qr = remember(url) { if (url.isBlank()) null else tv.own.owntv.core.companion.CompanionLink.renderQr(url) }
                qr?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = url,
                        modifier = Modifier.size(220.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White).padding(9.mpx),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                    if (code != null) {
                        Text(stringResource(R.string.kevin_drive_sign_in_steps, url), style = stageText(19, 500), color = StageColors.Text)
                        Text(code.userCode, style = stageText(46, 800), color = StageColors.Text)
                    }
                    val line = when {
                        state.denied -> stringResource(R.string.kevin_drive_sign_in_denied)
                        state.expired -> stringResource(R.string.kevin_drive_sign_in_expired)
                        state.failed != null -> stringResource(R.string.kevin_drive_sign_in_failed, state.failed)
                        else -> stringResource(R.string.kevin_drive_sign_in_waiting)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                        if (!ended) OwnTVSpinner(sizeDp = 22)
                        Text(line, style = stageText(17, 500), color = if (ended) StageColors.Danger else StageColors.Muted)
                    }
                }
            }
        }
    }
}

/** Kevin's copy: the backups on Drive, newest first, and a way to sign out. */
@Composable
internal fun DriveRestorePopup(
    account: String,
    recordingsOn: Boolean,
    recordingLast: DriveRecordings.Last,
    onToggleRecordings: () -> Unit,
    load: suspend () -> List<GoogleDrive.DriveFile>?,
    onPick: (GoogleDrive.DriveFile) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val files by produceState<List<GoogleDrive.DriveFile>?>(initialValue = null) { value = load() ?: emptyList() }
    val context = LocalContext.current
    val sep = stringResource(R.string.content_epg_bits_separator)
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.kevin_drive_restore_title),
        body = if (account.isNotBlank()) stringResource(R.string.kevin_drive_signed_in_as, account) else stringResource(R.string.kevin_drive_signed_in),
    ) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(files) { if (files != null) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
        val list = files
        Column(Modifier.trapAllFocusExit().focusGroup()) {
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(80.mpx), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                list.isEmpty() -> Text(stringResource(R.string.kevin_drive_no_backups), style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(bottom = 12.mpx))
                else -> list.forEachIndexed { i, f ->
                    tv.own.owntv.ui.stage.StagePopupOption(
                        title = f.name,
                        subtitle = listOf(
                            tv.own.owntv.ui.format.formatBestDateTime(context, "dMMMyyyy", f.createdMs),
                            android.text.format.Formatter.formatShortFileSize(context, f.size),
                        ).joinToString(sep),
                        onClick = { onPick(f) },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.BACKUP) },
                    )
                }
            }
            if (list != null) {
                // Recordings to Drive: the switch, and what the last copy did.
                val recLine = when (val l = recordingLast) {
                    DriveRecordings.Last.None -> stringResource(R.string.kevin_drive_recordings_line)
                    is DriveRecordings.Last.Copying -> stringResource(R.string.kevin_drive_rec_copying, l.title, l.percent)
                    is DriveRecordings.Last.Copied ->
                        if (l.removedOld > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.kevin_drive_rec_copied_removed, l.removedOld, l.title, l.removedOld)
                        else stringResource(R.string.kevin_drive_rec_copied, l.title)
                    is DriveRecordings.Last.Failed -> when {
                        l.signedOut -> stringResource(R.string.kevin_drive_error_signed_out)
                        l.full -> stringResource(R.string.kevin_drive_rec_full, l.title)
                        else -> stringResource(R.string.kevin_drive_rec_failed, l.title)
                    }
                }
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.kevin_drive_recordings),
                    subtitle = recLine,
                    value = stringResource(if (recordingsOn) R.string.common_on else R.string.common_off),
                    onClick = onToggleRecordings,
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.REC) },
                )
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.kevin_drive_sign_out),
                    onClick = onSignOut,
                    danger = true,
                    modifier = if (list.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.POWER) },
                )
            }
        }
    }
}
