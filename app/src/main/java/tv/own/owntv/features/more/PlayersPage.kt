package tv.own.owntv.features.more

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx

/**
 * Kevin's copy (v1): the free UK and Irish players. Their programmes only play inside their own apps,
 * so each row opens the official app when it is on this device, and otherwise opens the device's app
 * store on a search for it. An app is recognised by its name or its package, whichever store it came from.
 */
internal object FreePlayers {
    class Player(val nameRes: Int, val labels: List<String>, val exactLabels: List<String>, val packages: List<String>)

    val all = listOf(
        Player(R.string.kevin_player_bbc, listOf("iplayer"), emptyList(), listOf("iplayer")),
        Player(R.string.kevin_player_itv, listOf("itvx", "itv hub"), emptyList(), listOf("com.itv.", "air.itv")),
        Player(R.string.kevin_player_c4, listOf("channel 4", "all 4"), emptyList(), listOf("channel4")),
        Player(R.string.kevin_player_c5, listOf("my5"), listOf("5"), listOf("channel5", "my5", "demand5")),
        Player(R.string.kevin_player_rte, listOf("rté player", "rte player"), emptyList(), listOf("ie.rte")),
        Player(R.string.kevin_player_virgin, listOf("virgin media"), emptyList(), listOf("virginmedia")),
    )

    class Installed(val packageName: String, val icon: Bitmap?)

    /** Which of [all] are on this device, by position. */
    fun find(context: Context): List<Installed?> {
        val pm = context.packageManager
        val apps = LinkedHashMap<String, String>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val found = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }.getOrDefault(emptyList())
            for (info in found) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || pkg in apps) continue
                apps[pkg] = runCatching { info.loadLabel(pm).toString() }.getOrDefault("").trim().lowercase()
            }
        }
        return all.map { p ->
            val hit = apps.entries.firstOrNull { (pkg, label) ->
                val lowPkg = pkg.lowercase()
                p.labels.any { label.contains(it) } || p.exactLabels.any { label == it } || p.packages.any { lowPkg.contains(it) }
            } ?: return@map null
            Installed(hit.key, runCatching { pm.getApplicationIcon(hit.key).toBitmap(96, 96) }.getOrNull())
        }
    }

    fun open(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** The device's own app store, on a search for [name]: Amazon's on a Fire TV, Google's elsewhere. */
    fun openStore(context: Context, name: String): Boolean {
        val amazon = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
        val first = if (amazon) "amzn://apps/android?s=" else "market://search?q="
        val second = if (amazon) "market://search?q=" else "amzn://apps/android?s="
        for (prefix in listOf(first, second)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(prefix + Uri.encode(name))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }
}

@Composable
internal fun PlayersPage(entry: FocusRequester) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Coming back from the app store, a newly installed player shows as installed.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val installed by produceState<List<FreePlayers.Installed?>>(List(FreePlayers.all.size) { null }, refresh) {
        value = withContext(Dispatchers.IO) { FreePlayers.find(context.applicationContext) }
    }
    val noStore = stringResource(R.string.kevin_players_no_store)
    Column(Modifier.fillMaxSize().focusGroup().verticalScroll(rememberScrollState())) {
        PageLabel(stringResource(R.string.kevin_players_note), Modifier.padding(bottom = 16.mpx))
        FreePlayers.all.forEachIndexed { i, p ->
            val name = stringResource(p.nameRes)
            val app = installed.getOrNull(i)
            StageActionRow(
                height = 84.mpx,
                focus = if (i == 0) entry else null,
                onClick = {
                    val ok = if (app != null) FreePlayers.open(context, app.packageName) else FreePlayers.openStore(context, name)
                    if (!ok) android.widget.Toast.makeText(context, noStore, android.widget.Toast.LENGTH_LONG).show()
                },
                leading = { focused ->
                    val icon = app?.icon
                    if (icon != null) {
                        Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(52.mpx).clip(RoundedCornerShape(12.mpx)))
                    } else {
                        OwnTVIcon(OwnTVIcon.PLAY_CIRCLE, if (focused) StageColors.FxText else StageColors.Muted, Modifier.size(52.mpx))
                    }
                },
                title = name,
                line = stringResource(if (app != null) R.string.kevin_players_installed else R.string.kevin_players_missing),
            )
        }
    }
}
