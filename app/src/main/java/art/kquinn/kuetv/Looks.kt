package art.kquinn.kuetv

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * How the app's screens look: dark or light, the highlight colour, an outline round what the remote
 * is on, and the glass look (see-through panels on a glowing background). Kept on the device; the
 * screens change as soon as a choice does.
 */
object Looks {
    val themes = listOf("Dark", "Light")

    /**
     * Colour schemes for the dark look: the page, the boxes on it and the highlight colour together. Kue blue
     * (dark blue and yellow) is the usual one. Choosing a scheme also sets its highlight colour, which can then
     * be changed on its own.
     */
    class Scheme(val name: String, val page: Long, val panel: Long, val nowCell: Long, val accent: Int, val glow: List<Long> = emptyList())

    val schemes = listOf(
        Scheme("Kue blue", 0xFF0E1626, 0xFF16213A, 0xFF263A63, accent = 0),
        // Orange into pink across the top, over a deep plum page.
        Scheme("Sunset", 0xFF1B1030, 0xFF2C1B45, 0xFF4A2A5E, accent = 4, glow = listOf(0xFFB4532A, 0xFF9B2A6E)),
        Scheme("Midnight", 0xFF0A0A0F, 0xFF1A1A23, 0xFF2E2E3C, accent = 1),
        Scheme("Forest", 0xFF0D1913, 0xFF16281E, 0xFF244030, accent = 3),
    )
    val accents = listOf("Yellow", "Teal", "Blue", "Green", "Orange", "Pink")
    private val accentValues = listOf(0xFFFFD23F, 0xFF52DBC8, 0xFF5EC8FF, 0xFF6BDB8F, 0xFFFFA24C, 0xFFFF8AC8)
    val outlines = listOf("None", "Thin", "Thick")
    private val outlineWidths = listOf(0, 2, 4)
    val glasses = listOf("Off", "Soft", "Strong")

    var theme by mutableIntStateOf(0)
        private set
    var accent by mutableIntStateOf(0)
        private set
    var outline by mutableIntStateOf(0)
        private set
    var glass by mutableIntStateOf(0)
        private set
    var scheme by mutableIntStateOf(0)
        private set

    private val chosen: Scheme get() = schemes[scheme]

    private fun prefs(context: Context) = context.getSharedPreferences("looks", Context.MODE_PRIVATE)

    /** Reads the kept choices. Called once as the app opens. */
    fun load(context: Context) {
        val p = prefs(context)
        theme = p.getInt("theme", 0).coerceIn(0, themes.size - 1)
        accent = p.getInt("accent", 0).coerceIn(0, accents.size - 1)
        outline = p.getInt("outline", 0).coerceIn(0, outlines.size - 1)
        glass = p.getInt("glass", 0).coerceIn(0, glasses.size - 1)
        scheme = p.getInt("scheme", 0).coerceIn(0, schemes.size - 1)
    }

    /** Moves to the next colour scheme, with its own highlight colour, on the dark look. */
    fun nextScheme(context: Context) {
        val next = (scheme + 1) % schemes.size
        prefs(context).edit().putInt("scheme", next).putInt("accent", schemes[next].accent).putInt("theme", 0).apply()
        load(context)
    }

    /** Keeps one choice ("theme", "accent", "outline" or "glass") and shows it straight away. */
    fun set(context: Context, what: String, value: Int) {
        prefs(context).edit().putInt(what, value).apply()
        load(context)
    }

    /** Back to Kue blue: dark, yellow, no outline, no glass. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        load(context)
    }

    /** What is kept, for the backup. */
    fun export(context: Context): String = listOf(theme, accent, outline, glass, scheme).joinToString(",")

    fun import(context: Context, text: String) {
        val v = text.split(',').map { it.trim().toIntOrNull() ?: 0 }
        if (v.size < 4) return
        prefs(context).edit().putInt("theme", v[0]).putInt("accent", v[1]).putInt("outline", v[2]).putInt("glass", v[3])
            .putInt("scheme", v.getOrElse(4) { 0 }).apply()
        load(context)
    }

    val light: Boolean get() = theme == 1

    /** The highlight colour: what the remote is on, and the main buttons. Black words go on it. */
    val accentColour: Color get() = Color(accentValues[accent])

    /** The highlight colour for words and signs on the page: darker on the light look, so it can be read. */
    val accentInk: Color get() = if (light) lerp(accentColour, Color.Black, 0.55f) else accentColour

    /** The page behind everything. */
    val page: Color get() = if (light) Color(0xFFEEF1F6) else Color(chosen.page)

    /** Boxes and buttons on the page; see-through with the glass look. */
    val panel: Color
        get() {
            val solid = if (light) Color.White else Color(chosen.panel)
            return when (glass) {
                1 -> solid.copy(alpha = if (light) 0.72f else 0.62f)
                2 -> solid.copy(alpha = if (light) 0.5f else 0.4f)
                else -> solid
            }
        }

    /** The main words. */
    val ink: Color get() = if (light) Color(0xFF111827) else Color.White

    /** Words that matter less. */
    val soft: Color get() = if (light) Color(0xFF4B5567) else Color(0xFFB9C2D3)

    /** A faint box behind a missing picture, and a faint highlight. */
    val faint: Color get() = ink.copy(alpha = 0.13f)
    val faint2: Color get() = ink.copy(alpha = 0.2f)

    /** The programme on now, in the guide. */
    val nowCell: Color get() = if (light) Color(0xFFD3DEF2) else Color(chosen.nowCell)

    /** The page's background: plain, or with the glass look a soft glow of the highlight colour from the top left. */
    val backdrop: Brush
        get() = if (glass == 0 && !light && chosen.glow.isNotEmpty()) {
            // The scheme's own glow across the top (Sunset: orange into pink), fading into the page.
            val (a, b) = chosen.glow.map { Color(it) }
            Brush.linearGradient(0f to a, 0.3f to b, 0.62f to page, 1f to page, start = Offset(0f, 0f), end = Offset(900f, 1400f))
        } else if (glass == 0) {
            Brush.linearGradient(listOf(page, page))
        } else {
            val glow = lerp(page, accentColour, if (glass == 2) 0.28f else 0.16f)
            val deep = lerp(page, Color.Black, if (light) 0.04f else 0.35f)
            Brush.radialGradient(listOf(glow, page, deep), center = Offset(0f, 0f), radius = 2400f)
        }

    /** How thick the outline round what the remote is on is. */
    val outlineWidth: Int get() = outlineWidths[outline]
}

/** Draws the chosen outline round something while the remote is on it. */
fun Modifier.focusRing(focused: Boolean, shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    val width = Looks.outlineWidth
    return if (focused && width > 0) this.border(width.dp, Looks.ink, shape) else this
}
