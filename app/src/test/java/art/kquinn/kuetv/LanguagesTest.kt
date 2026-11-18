package art.kquinn.kuetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Batch 11, KueTV in other languages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class LanguagesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun backToEnglish() = UiLang.set(context, "en")

    @Test fun wordsAndLinesBuiltFromPartsAreShownInTheLanguage() {
        UiLang.set(context, "de")
        assertEquals("de", UiLang.code)
        assertEquals("Einstellungen", UiLang.tr("Settings"))
        assertEquals("headings in capitals", "EINSTELLUNGEN", UiLang.tr("SETTINGS"))
        assertEquals("Untertitel: an", UiLang.tr("Subtitles: on"))
        assertEquals("Einstellungen › Wiedergabe", UiLang.tr("Settings › Watching"))
        assertEquals("Meine Add-ons (3)", UiLang.tr("My add-ons (3)"))
        assertEquals("● In Gebrauch", UiLang.tr("● In use"))
        assertEquals("Wird gesichert…", UiLang.tr("Backing up…"))
        assertEquals("a channel's own name is left alone", "BBC One", UiLang.tr("BBC One"))
        UiLang.set(context, "en")
        assertEquals("Settings", UiLang.tr("Settings"))
    }

    @Test fun theChoiceIsKeptAndGoesRound() {
        UiLang.set(context, "fr")
        UiLang.load(context)
        assertEquals("fr", UiLang.code)
        UiLang.set(context, "zh-TW")
        assertEquals("en", UiLang.next())
        assertEquals(24, UiLang.all.size)
    }

    @Test fun everyLanguageFileReadsAndPointsAtRealLines() {
        val english = context.assets.open("lang/en.txt").bufferedReader().readLines()
        for (lang in UiLang.all.drop(1)) {
            val lines = runCatching { context.assets.open("lang/" + lang.code + ".txt").bufferedReader().readLines() }.getOrNull() ?: continue
            for (line in lines.filter { it.isNotBlank() }) {
                val n = line.substringBefore('=').trim().toIntOrNull()
                assertTrue(lang.code + ": " + line, n != null && n in 1..english.size)
            }
            assertTrue(lang.code + " has words", UiLang.read(context, lang.code).size > 700)
        }
    }

    @Test fun screensShowTheChosenLanguage() {
        UiLang.set(context, "de")
        rule.setContent { TrText("Live TV") }
        rule.waitForIdle()
        rule.onNodeWithText("Live-TV").assertExists()
    }
}
