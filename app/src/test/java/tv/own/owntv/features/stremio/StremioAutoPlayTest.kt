package tv.own.owntv.features.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.player.TrackOption

class StremioAutoPlayTest {
    private fun s(title: String, detail: String? = null) = StremioAddons.Stream("addon", title, detail, "https://example.test/$title", emptyMap())

    private fun order(vararg streams: StremioAddons.Stream) = StremioAutoPlay.rank(streams.toList()).map { it.title }

    @Test fun biggerPictureFirst() =
        assertEquals(listOf("B 2160p", "C 1080p", "A 720p"), order(s("A 720p"), s("B 2160p"), s("C 1080p")))

    @Test fun otherLanguagesLast() =
        assertEquals(listOf("Film 720p", "Film 1080p ITA"), order(s("Film 1080p ITA"), s("Film 720p")))

    @Test fun flagsCountAsLanguage() =
        assertEquals(listOf("Film 720p", "Film 4K"), order(s("Film 4K", "🇫🇷 1.2 GB"), s("Film 720p", "🇬🇧 900 MB")))

    @Test fun multiLanguageCountsAsEnglish() =
        assertEquals(listOf("Film 1080p MULTI FRENCH", "Film 720p"), order(s("Film 720p"), s("Film 1080p MULTI FRENCH")))

    @Test fun cinemaRecordingsAfterProperCopies() =
        assertEquals(listOf("Film 720p WEB", "Film 1080p HDCAM"), order(s("Film 1080p HDCAM"), s("Film 720p WEB")))

    @Test fun equalStreamsKeepTheAddonsOrder() =
        assertEquals(listOf("one 1080p", "two 1080p", "three"), order(s("one 1080p"), s("three"), s("two 1080p")))

    @Test fun wordsInsideOtherWordsDoNotCount() {
        // "DTS" is a sound format, not a telesync ("TS").
        assertFalse(StremioAutoPlay.isPoorCopy(s("Film 1080p DTS-HD")))
        assertEquals(0, StremioAutoPlay.languageRank(s("Spartacus 1080p")))
    }

    @Test fun readsThePictureSize() {
        assertEquals(2160, StremioAutoPlay.height(s("Torrentio\n4k HDR")))
        assertEquals(1080, StremioAutoPlay.height(s("x", "Film.2019.1080p.BluRay")))
        assertEquals(0, StremioAutoPlay.height(s("Film")))
    }

    @Test fun englishTracks() {
        assertTrue(StremioAutoPlay.isEnglish(TrackOption("x", 1, false, lang = "eng")))
        assertTrue(StremioAutoPlay.isEnglish(TrackOption("x", 1, false, lang = "en-GB")))
        assertTrue(StremioAutoPlay.isEnglish(TrackOption("English (SDH)", 1, false)))
        assertFalse(StremioAutoPlay.isEnglish(TrackOption("English dub", 1, false, lang = "fra")))
        assertFalse(StremioAutoPlay.isEnglish(TrackOption("x", 1, false, lang = "enm")))
    }
}
