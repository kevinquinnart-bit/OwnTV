package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The record of what went wrong that the user can copy and send. */
class ReportTest {
    @Test fun addressesNeverGetIntoTheRecord() {
        val line = Report.clean("Unable to connect to http://tv.example:8080/live/kev/secret/101.ts and magnet:?xt=urn:btih:abc&tr=udp://x then file:///storage/a.ts\nnext line")
        assertEquals("Unable to connect to [address] and [address] then [address] next line", line)
        assertFalse(Report.clean("HTTPS://TV.EXAMPLE/movie/kev/secret/5.mkv failed").contains("secret"))
    }

    @Test fun keepsTheLatestLinesAndFindsThemAgainAfterAClose() {
        val file = File.createTempFile("kuetv-report", ".txt").also { it.deleteOnExit() }
        Report.keepIn(file)
        repeat(320) { Report.note("Player", "line $it from http://tv.example/kev/secret") }
        assertEquals(300, Report.all().size)
        assertTrue(Report.all().last().endsWith("Player: line 319 from [address]"))
        assertTrue(Report.all().first().endsWith("Player: line 20 from [address]"))
        // The app is closed and opened again.
        Report.keepIn(file)
        assertEquals(300, Report.all().size)
        assertTrue(Report.all().last().endsWith("Player: line 319 from [address]"))
        assertFalse(file.readText().contains("secret"))
        val text = Report.text(listOf("KueTV 2, version 0.0.0", "Pretend TV"))
        assertTrue(text.startsWith("KueTV 2, version 0.0.0\nPretend TV\n\n"))
    }

    @Test fun thePageForThePhoneShowsTheReportSafely() {
        val page = PageServer.copyPage("KueTV report", "a <b> & c")
        assertTrue(page.contains("a &lt;b&gt; &amp; c"))
    }
}
