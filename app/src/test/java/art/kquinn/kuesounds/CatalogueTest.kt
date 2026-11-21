package art.kquinn.kuesounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks on choosing the right album, using real MusicBrainz answers saved on 20 Nov 2026 (in
 * test/resources/musicbrainz). No question is sent to MusicBrainz while these run.
 */
class CatalogueTest {
    private fun saved(name: String): String = javaClass.getResource("/musicbrainz/$name")!!.readText()

    @Test
    fun tidyAlbumNamesFromFolders() {
        assertEquals("Ropin' the Wind" to 2, Library.cleanAlbum("1 - Garth Brooks - Ropin' the Wind [Disc 2] (1991)", "Garth Brooks"))
        assertEquals("100 80s Hits" to 1, Library.cleanAlbum("100 80s Hits [Disc 1]", "Various Artists"))
        assertEquals("MeeTTechno Cr" to 0, Library.cleanAlbum("-+MeeTTechno Cr", "Faithless"))
        assertEquals("Disco 2000" to 0, Library.cleanAlbum("Disco 2000", "Pulp"))
        assertEquals("Greatest Hits" to 3, Library.cleanAlbum("Greatest Hits CD3", "Queen"))
    }

    @Test
    fun oasisGetsTheTwelveSongAlbumNotThePromoSingle() {
        val r = Catalogue.choose(Catalogue.parseReleases(saved("oasis_morning_glory.json")), "Oasis", 12)
        assertNotNull(r)
        assertEquals("(What's the Story) Morning Glory?", r!!.title)
        assertEquals("Album", r.type)
        assertEquals(1995, r.year)
        assertEquals("Oasis", r.artist)
    }

    @Test
    fun garthBrooksGetsTheTenSongOriginal() {
        val r = Catalogue.choose(Catalogue.parseReleases(saved("garth_ropin.json")), "Garth Brooks", 10)!!
        assertEquals(10, r.trackCount)
        assertEquals(1991, r.year)
    }

    @Test
    fun aCompilationMatchesOnlyAVariousArtistsRelease() {
        val found = Catalogue.parseReleases(saved("hits_80s.json"))
        val r = Catalogue.choose(found, "Various Artists", 25)!!
        assertEquals("100 80s Hits", r.title)
        assertEquals(2007, r.year)
        assertNull(Catalogue.choose(found, "Oasis", 25))
    }

    @Test
    fun theWrongArtistIsNeverTaken() {
        assertNull(Catalogue.choose(Catalogue.parseReleases(saved("garth_ropin.json")), "Faithless", 10))
    }

    @Test
    fun aLooseSongSkipsLiveAndBootlegRecords() {
        val found = Catalogue.parseSongReleases(saved("oasis_wonderwall_song.json"))
        val r = Catalogue.chooseForSong(found, "Oasis")
        if (r != null) {
            assertEquals("Album", r.type)
            assertEquals(emptyList<String>(), r.otherTypes)
        }
    }

    @Test
    fun searchWordsAreQuotedSafely() {
        assertEquals("\"Ropin' \\\"the\\\" Wind\"", Catalogue.quote("Ropin' \"the\" Wind"))
    }

    /** Only the first 15 songs found, as the app asks for. */
    private fun first15(name: String): String {
        val root = com.google.gson.JsonParser.parseString(saved(name)).asJsonObject
        val recs = root.getAsJsonArray("recordings")
        val keep = com.google.gson.JsonArray()
        for (i in 0 until minOf(15, recs.size())) keep.add(recs[i])
        root.add("recordings", keep)
        return root.toString()
    }

    @Test
    fun wonderwallGoesHomeToMorningGloryAndItsSingle() {
        val home = Catalogue.chooseSongHome(Catalogue.parseRecordings(first15("oasis_wonderwall_home.json")), "Wonderwall", "Oasis")
        assertEquals("(What's the Story) Morning Glory?", home.album?.title)
        assertEquals(1995, home.album?.year)
        assertEquals("Wonderwall", home.single?.title)
    }

    @Test
    fun billieJeanGoesHomeToThrillerAndItsSingle() {
        val home = Catalogue.chooseSongHome(Catalogue.parseRecordings(first15("mj_billie_jean_home.json")), "Billie Jean", "Michael Jackson")
        assertEquals("Thriller", home.album?.title)
        assertEquals("Billie Jean", home.single?.title)
    }

    @Test
    fun anotherArtistsSongFindsNoHome() {
        val home = Catalogue.chooseSongHome(Catalogue.parseRecordings(first15("oasis_wonderwall_home.json")), "Wonderwall", "Ryan Adams")
        assertEquals(null, home.album)
        assertEquals(null, home.single)
    }

    @Test
    fun variousArtistsCountAsVarious() {
        assertEquals(true, Catalogue.isVarious("Various Artists"))
        assertEquals(true, Catalogue.isVarious("Various artists"))
        assertEquals(false, Catalogue.isVarious("Oasis"))
    }
}
