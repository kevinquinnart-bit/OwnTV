package art.kquinn.kuetv

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * The language KueTV's own words are shown in. The English words are listed in assets/lang/en.txt, one
 * to a line; each other language's file (assets/lang/de.txt and so on) gives "line number=its words".
 * New English words go at the end of en.txt only, so the numbers already given stay right. Anything
 * with no translation shows in English.
 */
object UiLang {
    /** A language: its code and its name in its own words. */
    data class Option(val code: String, val name: String)

    val all = listOf(
        Option("en", "English"),
        Option("ar", "العربية"),
        Option("bn", "বাংলা"),
        Option("cs", "Čeština"),
        Option("da", "Dansk"),
        Option("de", "Deutsch"),
        Option("es", "Español"),
        Option("eu", "Euskara"),
        Option("fr", "Français"),
        Option("hi", "हिन्दी"),
        Option("hu", "Magyar"),
        Option("it", "Italiano"),
        Option("ja", "日本語"),
        Option("ko", "한국어"),
        Option("ml", "മലയാളം"),
        Option("nb", "Norsk"),
        Option("nl", "Nederlands"),
        Option("pl", "Polski"),
        Option("pt", "Português"),
        Option("ru", "Русский"),
        Option("sv", "Svenska"),
        Option("tr", "Türkçe"),
        Option("zh-CN", "简体中文"),
        Option("zh-TW", "繁體中文"),
    )

    var code by mutableStateOf("en")
        private set

    @Volatile private var words: Map<String, String> = emptyMap()
    private val made = HashMap<String, String>()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("choices", Context.MODE_PRIVATE)

    fun name(code: String): String = all.firstOrNull { it.code == code }?.name ?: "English"

    /** Reads the chosen language's words. Called as the app opens. */
    fun load(context: Context) = use(context, prefs(context).getString("uiLang", "en") ?: "en")

    fun set(context: Context, code: String) {
        prefs(context).edit().putString("uiLang", code).apply()
        use(context, code)
    }

    /** The language after this one in the list, going round to English. */
    fun next(): String = all[(all.indexOfFirst { it.code == code } + 1) % all.size].code

    private fun use(context: Context, wanted: String) {
        val c = all.firstOrNull { it.code == wanted }?.code ?: "en"
        words = if (c == "en") emptyMap() else read(context, c)
        synchronized(made) { made.clear() }
        code = c
    }

    /** "English → theirs", for [code], out of the two files. */
    fun read(context: Context, code: String): Map<String, String> = runCatching {
        val english = context.assets.open("lang/en.txt").bufferedReader(Charsets.UTF_8).readLines()
        val map = HashMap<String, String>()
        context.assets.open("lang/$code.txt").bufferedReader(Charsets.UTF_8).forEachLine { line ->
            val n = line.substringBefore('=').trim().toIntOrNull() ?: return@forEachLine
            val theirs = line.substringAfter('=').trim()
            val en = english.getOrNull(n - 1)?.trim()
            // As written ("On" and "on" can differ), and in small letters for words written in capitals.
            if (!en.isNullOrEmpty() && theirs.isNotEmpty()) {
                map[en] = theirs
                map.putIfAbsent(en.lowercase(), theirs)
            }
        }
        map
    }.getOrElse { emptyMap() }

    /** [text] in the chosen language, as far as there are words for it. */
    fun tr(text: String): String {
        if (words.isEmpty() || text.isBlank()) return text
        synchronized(made) { made[text]?.let { return it } }
        val done = translate(text)
        synchronized(made) { made[text] = done }
        return done
    }

    private val lead = Regex("^[^\\p{L}]+")
    private val tail = Regex("(\\s*\\([^)]*\\)|…|\\.|\\?|!|\\s+[\\d][\\d ,.:]*)$")

    private fun translate(t: String): String {
        (words[t.trim()] ?: words[t.trim().lowercase()])?.let { return cased(t, it) }
        // Lines built from parts: each part on its own.
        for (cut in listOf("\n", " · ", " › ", ": ")) {
            if (cut in t) return t.split(cut).joinToString(cut) { tr(it) }
        }
        // A sign or number in front ("● ", "📁 ", "3 "), or something after ("(3)", "…", "."), kept as it is.
        lead.find(t)?.value?.let { if (it.length < t.length) return it + tr(t.substring(it.length)) }
        tail.find(t)?.value?.let { if (it.length < t.length) return tr(t.substring(0, t.length - it.length)) + it }
        return t
    }

    /** Capitals in, capitals out: headings are written in capitals. */
    private fun cased(english: String, theirs: String): String {
        val letters = english.filter { it.isLetter() }
        return if (letters.length > 1 && letters.all { it.isUpperCase() }) theirs.uppercase() else theirs
    }
}

/** The app's Text, with its words in the chosen language. Used in place of Material's Text in the screens. */
@Composable
fun TrText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // Read here, so every line changes when the language does.
    val shown = if (UiLang.code == "en") text else UiLang.tr(text)
    androidx.compose.material3.Text(
        text = shown, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style,
    )
}
