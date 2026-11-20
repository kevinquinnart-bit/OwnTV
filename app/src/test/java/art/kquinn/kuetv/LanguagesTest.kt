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

    @Test fun linesWithNumbersAndNamesInThemAreShownInTheLanguage() {
        UiLang.set(context, "de")
        assertEquals("Die 5 ausgewählten ausblenden", UiLang.tr("Hide the 5 picked"))
        assertEquals("3 von 10 angezeigt", UiLang.tr("3 of 10 showing"))
        assertEquals("Version 0.1.140 ist bereit", UiLang.tr("Version 0.1.140 is ready"))
        assertEquals("Angemeldet als kev@example.com", UiLang.tr("Signed in as kev@example.com"))
        assertEquals("BBC One aus KueTV entfernt", UiLang.tr("Removed BBC One from KueTV"))
        // A number-only slot never takes a name: a film's own title is left alone.
        assertEquals("Ten of Hearts", UiLang.tr("Ten of Hearts"))
        // Sentences put together in one line are each translated.
        assertEquals("Kein Internet", UiLang.tr("No internet"))
        assertEquals("Prüfe ihn und versuche es erneut", UiLang.tr("Check it and try again"))
        assertEquals(
            "Dieser Link hat nicht wie ein Add-on geantwortet. Prüfe ihn und versuche es erneut.",
            UiLang.tr("That link didn't answer like an add-on. Check it and try again."),
        )
        // A language that puts the numbers the other way round.
        UiLang.set(context, "tr")
        assertEquals("Denenen 5 taneden 3 tanesi yanıt verdi", UiLang.tr("3 of 5 tried answered"))
        UiLang.set(context, "ja")
        assertEquals("アンテナ放送を見る", UiLang.tr("Watch aerial TV"))
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
