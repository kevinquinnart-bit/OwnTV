package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the version file the build publishes, and telling a newer version from the one running. */
class UpdatesTest {
    @Test fun readsTheFileTheBuildWrites() {
        // Exactly what the build's printf writes.
        val latest = Updates.parse("code=34\nname=0.1.34\n")
        assertEquals(Updates.Latest(34, "0.1.34"), latest)
    }

    @Test fun copesWithSpacesAndWindowsLineEnds() {
        assertEquals(Updates.Latest(40, "0.1.40"), Updates.parse(" code = 40 \r\nname= 0.1.40\r\n"))
    }

    @Test fun noBuildNumberMeansNothingToOffer() {
        assertNull(Updates.parse(""))
        assertNull(Updates.parse("<html>Not Found</html>"))
        assertNull(Updates.parse("code=abc\nname=0.1.9"))
    }

    @Test fun onlyAHigherBuildNumberIsNewer() {
        val latest = Updates.Latest(34, "0.1.34")
        assertTrue(Updates.isNewer(latest, 33))
        assertFalse(Updates.isNewer(latest, 34))
        assertFalse(Updates.isNewer(latest, 35))
        // The build numbers are compared as numbers, so 0.1.100 is newer than 0.1.99.
        assertTrue(Updates.isNewer(Updates.parse("code=100\nname=0.1.100")!!, 99))
    }
}
