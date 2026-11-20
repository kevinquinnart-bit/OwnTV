package art.kquinn.kuetv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The free UK and Irish TV apps. Their programmes only play inside their own apps, so KueTV opens
 * the app when it is on this TV, and otherwise opens the TV's app store on a search for it.
 */
object TvAppList {
    /**
     * One app. It counts as installed when an app on the TV has a name containing one of [names],
     * a name exactly one of [exactNames], or a package containing one of [packages] (all lower case).
     */
    class TvApp(
        val title: String,
        val names: List<String>,
        val exactNames: List<String>,
        val packages: List<String>,
        val storeSearch: String = title,
        val country: String = UK,
    )

    const val UK = "UK"
    const val IRELAND = "Ireland"

    /** The countries, in the order Settings shows them. */
    val countries = listOf(UK, IRELAND)

    val all = listOf(
        TvApp("BBC iPlayer", listOf("iplayer"), emptyList(), listOf("iplayer")),
        TvApp("ITVX", listOf("itvx", "itv hub"), emptyList(), listOf("com.itv.", "air.itv")),
        TvApp("Channel 4", listOf("channel 4", "all 4"), emptyList(), listOf("channel4")),
        TvApp("5 (Channel 5)", listOf("my5", "channel 5"), listOf("5"), listOf("channel5", "my5", "demand5"), storeSearch = "Channel 5"),
        TvApp("RTÉ Player", listOf("rté player", "rte player"), emptyList(), listOf("ie.rte", "tvbridge.rte"), country = IRELAND),
        TvApp("Virgin Media Play", listOf("virgin media"), emptyList(), listOf("virginmedia"), country = IRELAND),
        TvApp("STV Player", listOf("stv player"), listOf("stv"), listOf("stvplayer")),
        TvApp("U (UKTV)", listOf("uktv"), listOf("u"), listOf("uktv"), storeSearch = "UKTV Play"),
        TvApp("TG4 Player", listOf("tg4"), emptyList(), listOf("tg4"), country = IRELAND),
    )

    /**
     * For each of [all], in order, the package of the app on the TV that is it, or null.
     * [onTv] is every app on the TV: package to its name as the TV shows it.
     */
    fun match(onTv: Map<String, String>): List<String?> = all.map { app ->
        onTv.entries.firstOrNull { (pkg, name) ->
            val n = name.trim().lowercase()
            val p = pkg.lowercase()
            app.names.any { n.contains(it) } || app.exactNames.any { n == it } || app.packages.any { p.contains(it) }
        }?.key
    }
}

/** The parts that need the TV itself: finding the apps, opening one, opening the app store. */
object TvAppsOnDevice {
    class Found(val packageName: String, val icon: Bitmap?)

    /** For each of [TvAppList.all], in order, what is on this TV, or null. */
    fun find(context: Context): List<Found?> {
        val pm = context.packageManager
        val onTv = LinkedHashMap<String, String>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }.getOrDefault(emptyList())
            for (info in list) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || pkg in onTv) continue
                onTv[pkg] = runCatching { info.loadLabel(pm).toString() }.getOrDefault("")
            }
        }
        return TvAppList.match(onTv).map { pkg -> pkg?.let { Found(it, iconOf(context, it)) } }
    }

    private fun iconOf(context: Context, pkg: String): Bitmap? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()

    /** Opens the app. False if the TV would not open it. */
    fun open(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Opens the TV's app store on a search for [search]: Amazon's on a Fire TV, Google's elsewhere. */
    fun openStore(context: Context, search: String): Boolean {
        val amazonFirst = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)
        val stores = listOf("amzn://apps/android?s=", "market://search?q=").let { if (amazonFirst) it else it.reversed() }
        return stores.any { prefix ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prefix + Uri.encode(search))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }
}

/**
 * Settings › This TV › Channel player apps: adding a country's players one after another from the TV's app
 * store, and taking one off. Installing and removing are always the TV's own: the store has its Get button,
 * and the TV asks before it removes an app.
 */
object PlayerApps {
    /** Players still to add: the next opens in the store each time KueTV comes back to the front. */
    var queue by mutableStateOf<List<TvAppList.TvApp>>(emptyList())

    /** Opens the store on [app]. The Home watcher stands aside, as the store opens through Amazon's home screen. */
    fun openStore(context: Context, app: TvAppList.TvApp): Boolean {
        HomeGuard.pause()
        val ok = TvAppsOnDevice.openStore(context, app.storeSearch)
        if (!ok) HomeGuard.pausedUntil = 0
        Report.note("Player apps", "store opened on " + app.title + if (ok) "" else ": it wouldn't open")
        return ok
    }

    /** Starts adding the [missing] players: the first opens now, the rest one at a time as KueTV comes back. */
    fun addAll(context: Context, missing: List<TvAppList.TvApp>) {
        if (missing.isEmpty()) return
        queue = missing.drop(1)
        if (!openStore(context, missing.first())) queue = emptyList()
    }

    /** Back in KueTV: the next player still not on the TV opens in the store. [onTv] are the titles on the TV. */
    fun next(context: Context, onTv: Set<String>) {
        val rest = queue.filter { it.title !in onTv }
        if (rest.isEmpty()) { queue = emptyList(); return }
        queue = rest.drop(1)
        if (!openStore(context, rest.first())) queue = emptyList()
    }

    /** Asks the TV to remove the app; the TV shows its own question first. */
    fun remove(context: Context, pkg: String, title: String): Boolean {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        Report.note("Player apps", "remove " + title + if (ok) ": the TV asked" else ": the TV wouldn't ask")
        return ok
    }
}
