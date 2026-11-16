package art.kquinn.kuetv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** True on a phone or tablet (touch); false on a TV (remote). The screens lay themselves out by it. */
val LocalPhone = staticCompositionLocalOf { false }

/** Is this a TV: a Fire TV, an Android TV, or anything else that says it is a television. */
fun isTv(context: Context): Boolean {
    val pm = context.packageManager
    val mode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("amazon.hardware.fire_tv")
}
