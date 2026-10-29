package tv.own.owntv.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.Text

/*
 * Kevin's copy: two ways of never cutting guide text short.
 *
 * - [ticker]: one line of text that does not fit rolls sideways like a news ticker, and stays still
 *   when it fits. Pass the Animations setting ([tv.own.owntv.ui.theme.animationsOn]): with animations
 *   off the line keeps its "…" as before.
 * - [FitText]: a block of text (a programme description) given a fixed space shrinks its type a step at
 *   a time until all of it fits, down to [minScale] of its size; past that it ends in "…".
 */

/** Rolls the text sideways when, and only when, it is wider than its space. */
fun Modifier.ticker(on: Boolean): Modifier =
    if (on) this.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500) else this

/** How far, in px, a cut-off line drawn by hand has rolled at [elapsedMs]: still, along, still, back. */
fun tickerShift(overflowPx: Float, elapsedMs: Long, pxPerMs: Float, pauseMs: Long = 1500L): Float {
    if (overflowPx <= 0f || pxPerMs <= 0f) return 0f
    val travelMs = (overflowPx / pxPerMs).toLong().coerceAtLeast(1L)
    val cycle = pauseMs + travelMs + pauseMs
    val p = elapsedMs.mod(cycle)
    return when {
        p < pauseMs -> 0f
        p < pauseMs + travelMs -> ((p - pauseMs) * pxPerMs).coerceAtMost(overflowPx)
        else -> overflowPx
    }
}

/**
 * [text] at the largest size, from [style]'s down to [minScale] of it in 5% steps, that fits the space
 * this is given. Needs a height limit to work to (a weight or a fixed height); with none it is plain text.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.75f,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val fitted = remember(text, style, maxW, maxH, minScale) {
            if (maxW == Constraints.Infinity || maxH == Constraints.Infinity || text.isEmpty()) {
                style to Int.MAX_VALUE
            } else {
                var scale = 1f
                var chosen = style
                var lines = Int.MAX_VALUE
                while (true) {
                    val s = style.copy(
                        fontSize = style.fontSize * scale,
                        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
                    )
                    val r = measurer.measure(text, s, constraints = Constraints(maxWidth = maxW))
                    chosen = s
                    if (r.size.height <= maxH) { lines = Int.MAX_VALUE; break }
                    if (scale <= minScale + 0.001f) {
                        // Still too long at the smallest size: as many whole lines as fit, then "…".
                        var fit = 0
                        while (fit < r.lineCount && r.getLineBottom(fit) <= maxH) fit++
                        lines = fit.coerceAtLeast(1)
                        break
                    }
                    scale = (scale - 0.05f).coerceAtLeast(minScale)
                }
                chosen to lines
            }
        }
        Text(text, style = fitted.first, color = color, maxLines = fitted.second, overflow = TextOverflow.Ellipsis)
    }
}
