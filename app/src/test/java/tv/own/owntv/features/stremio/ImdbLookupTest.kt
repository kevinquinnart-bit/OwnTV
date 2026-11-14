package tv.own.owntv.features.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImdbLookupTest {
    private val answers = listOf(
        Triple("tt1375666", "Inception", 2010),
        Triple("tt5581256", "Inception", null),
        Triple("tt7321322", "Inception: The Cobol Job", 2010),
    )

    @Test fun sameNameAndYearIsTaken() = assertEquals("tt1375666", StremioAddons.pickImdbId("Inception", 2010, answers))

    @Test fun caseAndPunctuationDoNotMatter() = assertEquals("tt1375666", StremioAddons.pickImdbId("INCEPTION!", 2010, answers))

    @Test fun aYearOneOutStillCounts() = assertEquals("tt1375666", StremioAddons.pickImdbId("Inception", 2011, answers))

    @Test fun aWrongYearIsRefused() = assertNull(StremioAddons.pickImdbId("Inception", 1999, answers))

    @Test fun withNoYearTheFirstSameNameIsTaken() = assertEquals("tt1375666", StremioAddons.pickImdbId("Inception", null, answers))

    @Test fun aDifferentNameIsRefused() = assertNull(StremioAddons.pickImdbId("Interstellar", 2014, answers))

    @Test fun anAnswerWithoutAnImdbIdIsSkipped() =
        assertNull(StremioAddons.pickImdbId("Inception", 2010, listOf(Triple("tmdb:27205", "Inception", 2010))))
}
