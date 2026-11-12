package tv.own.owntv.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderTagsTest {

    @Test
    fun prefixAndSuperscriptTagAreSplitOff() =
        assertEquals(ProviderName("Sky Cinema Premieren", listOf("HD"), "DE"), ProviderTags.parse("DE| Sky Cinema Premieren ᴴᴰ"))

    @Test
    fun spaceBeforeTheBarIsAllowed() = assertEquals("DE", ProviderTags.parse("DE | Prime ᴿᴬᵂ").country)

    @Test
    fun eachRunIsOneTag() =
        assertEquals(listOf("HD", "DOLBY"), ProviderTags.parse("WOW Entertainment ᴴᴰ ᴰᴼᴸᴮʸ").tags)

    @Test
    fun lowerCaseModifierLettersFoldToCapitals() = assertEquals(listOf("RAW"), ProviderTags.parse("Joyn ʳᵃʷ").tags)

    @Test
    fun superscriptDigitsMakeA4kTag() = assertEquals(listOf("4K"), ProviderTags.parse("General ⁴ᴷ").tags)

    @Test
    fun repeatedTagIsKeptOnce() = assertEquals(listOf("HD"), ProviderTags.parse("Sky ᴴᴰ Cinema ᴴᴰ").tags)

    @Test
    fun plainNameIsUntouched() = assertEquals(ProviderName("Sky Cinema", emptyList(), null), ProviderTags.parse("Sky Cinema"))

    @Test
    fun barInsideTheNameIsNotAPrefix() = assertNull(ProviderTags.parse("Sky | Cinema").country)

    @Test
    fun tagOnlyNameKeepsTheProviderSpelling() = assertEquals("DE| ᴴᴰ", ProviderTags.parse("DE| ᴴᴰ").name)

    @Test
    fun sharedCountryWhenAllAgree() = assertEquals(
        "DE",
        ProviderTags.sharedCountry(listOf(ProviderTags.parse("DE| A"), ProviderTags.parse("DE| B"))),
    )

    @Test
    fun noSharedCountryWhenOneDiffers() = assertNull(
        ProviderTags.sharedCountry(listOf(ProviderTags.parse("DE| A"), ProviderTags.parse("UK| B"))),
    )

    @Test
    fun noSharedCountryWhenOneHasNone() = assertNull(
        ProviderTags.sharedCountry(listOf(ProviderTags.parse("DE| A"), ProviderTags.parse("B"))),
    )
}
