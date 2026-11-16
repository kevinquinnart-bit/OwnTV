package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Carry on watching" list and programme reminders, as they are kept. */
class CarryOnTest {
    @Test fun aFilmLeftPartWayThroughIsReadBackTheSame() {
        val w = Positions.Watching("http://tv.example/movie/k/p/55.mkv", "The Thing", "http://img.example/thing.jpg", 2_580_000, 6_180_000, 1_763_300_000_000)
        assertEquals(w, Positions.decode(Positions.encode(w)))
    }

    @Test fun aPartWithNoPictureStillReadsBack() {
        val w = Positions.Watching("http://tv.example/series/k/p/9.mkv", "Taskmaster · S3 E2", "", 90_000, 2_700_000, 5)
        assertEquals(w, Positions.decode(Positions.encode(w)))
    }

    @Test fun somethingElseIsNotAPlace() {
        assertNull(Positions.decode("rubbish"))
        assertNull(Positions.decode(""))
    }

    @Test fun aReminderPopsUpFromAMinuteBeforeUntilFiveMinutesIn() {
        val start = 1_763_330_000_000
        val r = Reminder.make("101", "ITV 1", "I'm a Celebrity", start, start + 3_600_000)
        assertNull(Reminder.due(listOf(r), start - 120_000))
        assertEquals(r, Reminder.due(listOf(r), start - 30_000))
        assertEquals(r, Reminder.due(listOf(r), start + 240_000))
        assertNull(Reminder.due(listOf(r), start + 360_000))
        assertEquals(r, Reminder.fromJson(r.toJson()))
        // The same programme set twice is one reminder.
        assertEquals(r.id, Reminder.make("101", "ITV 1", "I'm a Celebrity", start, start + 3_600_000).id)
    }

    @Test fun aSubtitleLanguageIsKnownHoweverASourceWritesIt() {
        val english = Langs.of("en")
        for (said in listOf("en", "eng", "English", "en-GB", "ENG")) assertEquals(said, true, Langs.matches(english, said))
        for (said in listOf("fre", "es", "ger", "", "estonian")) assertEquals(said, false, Langs.matches(english, said))
        assertEquals(true, Langs.matches(Langs.of("fr"), "fre"))
        assertEquals(true, Langs.matches(Langs.of("pt"), "pob"))
        // A code the app doesn't offer falls back to English, and the list goes round.
        assertEquals("English", Langs.of("xx").name)
        assertEquals("English", Langs.after(Langs.all.last()).name)
    }
}
