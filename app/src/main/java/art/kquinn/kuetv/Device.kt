package art.kquinn.kuetv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** True on a phone or tablet (touch); false on a TV (remote). The screens lay themselves out by it. */
val LocalPhone = staticCompositionLocalOf { false }

/** How much bigger than standard the lettering is; the menu and category columns widen with it. */
val LocalTextScale = staticCompositionLocalOf { 1f }

/** Is this a TV: a Fire TV, an Android TV, or anything else that says it is a television. */
fun isTv(context: Context): Boolean {
    val pm = context.packageManager
    val mode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("amazon.hardware.fire_tv")
}

/**
 * Looks at what the TV itself can show (its aerial tuner, its sockets) and whether it lets this app see
 * the aerial's channels. It changes nothing; it is for the report, to find out whether Freeview from
 * the aerial could ever be brought into KueTV on this TV.
 */
object TvProbe {
    fun lines(context: Context): List<String> {
        val out = ArrayList<String>()
        val manager = runCatching { context.getSystemService(Context.TV_INPUT_SERVICE) as? android.media.tv.TvInputManager }.getOrNull()
        if (manager == null) {
            out += "TV inputs: this device has none to ask about"
        } else {
            val inputs = runCatching { manager.tvInputList }.getOrNull().orEmpty()
            val kind: (Int) -> String = { type ->
                when (type) {
                    android.media.tv.TvInputInfo.TYPE_TUNER -> "tuner"
                    android.media.tv.TvInputInfo.TYPE_HDMI -> "HDMI"
                    android.media.tv.TvInputInfo.TYPE_COMPOSITE -> "composite"
                    else -> "other"
                }
            }
            out += "TV inputs: " + if (inputs.isEmpty()) "none that this app can see" else inputs.joinToString("; ") { input ->
                kind(input.type) + " \"" + runCatching { input.loadLabel(context).toString() }.getOrDefault("?") + "\" (" + input.id.substringBefore('/') + ")"
            }
        }
        out += "Aerial channels this app may see: " + try {
            context.contentResolver.query(android.media.tv.TvContract.Channels.CONTENT_URI, arrayOf("_id", "display_name", "type"), null, null, null).use { rows ->
                if (rows == null) "the TV gave no answer" else {
                    val names = ArrayList<String>()
                    while (rows.moveToNext() && names.size < 8) names += rows.getString(1).orEmpty()
                    rows.count.toString() + (if (names.isEmpty()) "" else " (" + names.joinToString(", ") + (if (rows.count > names.size) ", …" else "") + ")")
                }
            }
        } catch (e: Exception) {
            "refused (" + e.javaClass.simpleName + ")"
        }
        return out
    }
}
