package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.stageAccent

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * KueTV (Kevin's copy, v2): the KayKue K mark, the same whatever icon colour or accent is set. [icon] and
 * [followAccent] stay so the callers need no change.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    Image(painterResource(R.drawable.kuetv_mark), contentDescription = null, modifier = modifier.size(size))
}

/**
 * "Pixel": today's TV-set silhouette rebuilt as a dot matrix (the mockup's `markSignal()`), the dots of
 * the play triangle in [triangle]. The same 100-unit grid as core's `owntv_mark_pixel`.
 */
@Composable
fun PixelMark(modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    val dots = remember { pixelDots() }
    Canvas(modifier) {
        val u = size.minDimension / 100f
        dots.forEach { (x, y, kind) ->
            val color = when (kind) { 0 -> PixelBody; 1 -> triangle; else -> PixelSide }
            drawCircle(color, 2.1f * u, Offset(x * u, y * u))
        }
        drawCircle(PixelPower, 4.2f * u, Offset(76f * u, 24f * u))
    }
}

private val PixelBody = Color(0xFFF5EDDA)
private val PixelSide = Color(0xFFCBB795)
private val PixelPower = Color(0xFFE24B36)

/** The mark's dots: (x, y, 0 body / 1 triangle / 2 side). */
private fun pixelDots(): List<Triple<Float, Float, Int>> {
    fun inRoundRect(x: Float, y: Float): Boolean {
        if (x < 10 || x > 90 || y < 12 || y > 88) return false
        val cx = x.coerceIn(28f, 72f)
        val cy = y.coerceIn(30f, 70f)
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= 18f * 18f
    }
    fun inTriangle(x: Float, y: Float): Boolean {
        fun s(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (px - rx) * (qy - ry) - (qx - rx) * (py - ry)
        val d1 = s(x, y, 38f, 30f, 38f, 70f)
        val d2 = s(x, y, 38f, 70f, 72f, 50f)
        val d3 = s(x, y, 72f, 50f, 38f, 30f)
        return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0))
    }
    val out = mutableListOf<Triple<Float, Float, Int>>()
    var y = 14f
    while (y <= 86f) {
        var x = 14f
        while (x <= 86f) {
            if (inRoundRect(x, y) && (x - 76) * (x - 76) + (y - 24) * (y - 24) >= 40) out += Triple(x, y, if (inTriangle(x, y)) 1 else 0)
            x += 4.5f
        }
        y += 4.5f
    }
    for (x in listOf(5f, 95f)) for (sy in listOf(45.5f, 50f, 54.5f)) out += Triple(x, sy, 2)
    return out
}

/**
 * KueTV (Kevin's copy, v2): "Kue" in [own] (cream), "TV" in [tv] (the accent), Poppins SemiBold as in the
 * KayKue logo. As tall as the old 988 × 182 wordmark at [width]; the box is as wide as the word
 * ([KUETV_WORD_ASPECT] × its height).
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier, tv: Color = stageAccent.accent, own: Color = WordmarkCream) {
    Box(modifier.width(width * 182f / 988f * KUETV_WORD_ASPECT).height(width * 182f / 988f)) {
        Image(painterResource(R.drawable.owntv_wordmark_own), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(own))
        Image(painterResource(R.drawable.owntv_wordmark_tv), null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(tv))
    }
}

val WordmarkCream = Color(0xFFFFF6EE)

/** Width ÷ cap height of "KueTV" in the two wordmark drawings. */
const val KUETV_WORD_ASPECT = 4.486f

/**
 * The lockup (Brand tab, C column): the mark, then the #227 wordmark at 2.75 × the mark's width (62 →
 * 170 in the mockup). Side by side the gap is 0.23 × the mark (14 / 62); [stacked] (the preview pane)
 * puts the mark above with a 0.2 × gap.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    stacked: Boolean = false,
) {
    val icon = rememberAppliedIcon()
    val colors = OwnTVTheme.colors
    val parts = @Composable {
        BrandMark(icon, markSize.dp)
        // On a light theme "own" takes the text colour; the cream is for dark surfaces.
        Wordmark((markSize * 2.75f).dp, own = if (colors.isDark) WordmarkCream else colors.textPrimary)
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.23f).dp),
        ) { parts() }
    }
}
