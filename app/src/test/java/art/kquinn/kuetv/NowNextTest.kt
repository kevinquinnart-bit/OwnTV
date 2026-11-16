package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The one line that says what is on a channel now and next. */
class NowNextTest {
    private val clock = SimpleDateFormat("HH:mm", Locale.UK).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val hour = 3_600_000L
    private fun programme(title: String, from: Long, to: Long) = Programme(title, "", from, to, "")
    private val guide = listOf(programme("Early", 18 * hour, 19 * hour), programme("The News", 19 * hour, 20 * hour), programme("", 20 * hour, 21 * hour))

    @Test fun saysWhatIsOnNowAndNext() {
        assertEquals("Now: Early · Next 19:00: The News", NowNext.line(guide, 18 * hour + 5, clock))
        assertEquals("Now: The News · Next 20:00: No title", NowNext.line(guide, 19 * hour, clock))
    }

    @Test fun saysWhatItCanWhenTheGuideHasGaps() {
        assertEquals("only what is next, before the guide starts", "Next 18:00: Early", NowNext.line(guide, 17 * hour, clock))
        assertEquals("only what is on, at the guide's end", "Now: No title", NowNext.line(guide, 20 * hour + 5, clock))
        assertNull(NowNext.line(guide, 22 * hour, clock))
        assertNull(NowNext.line(emptyList(), 19 * hour, clock))
    }
}
