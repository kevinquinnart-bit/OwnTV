package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

/** One channel, several streams: names, quality order and which entry the list shows. */
class ChannelVariantsTest {

    private fun ch(id: Long, name: String, source: Long = 1, category: Long? = 10) =
        ChannelEntity(id = id, sourceId = source, categoryId = category, name = name, streamUrl = "http://x/$id")

    @Test
    fun labelsAreTakenOutOfTheName() {
        assertEquals("UK: BBC ONE", ChannelVariants.displayName("UK: BBC ONE FHD"))
        assertEquals("UK | BBC ONE", ChannelVariants.displayName("UK | BBC ONE (HD)"))
        assertEquals("UK: ITV1", ChannelVariants.displayName("UK: ITV1 HD [1080p]"))
        assertEquals("Sky Sports F1", ChannelVariants.displayName("Sky Sports F1 ᴴᴰ"))
        assertEquals("UK: Channel 4", ChannelVariants.displayName("UK: Channel 4 - HD -"))
    }

    @Test
    fun wordsThatOnlyContainALabelAreLeftAlone() {
        assertEquals("4Music", ChannelVariants.displayName("4Music"))
        assertEquals("HDTV Test", ChannelVariants.displayName("HDTV Test"))
        assertEquals("UK: BBC ONE +1", ChannelVariants.displayName("UK: BBC ONE +1"))
        // A name that is nothing but a label keeps its name rather than going blank.
        assertEquals("SD", ChannelVariants.displayName("SD"))
    }

    @Test
    fun qualityOrder() {
        assertEquals(4, ChannelVariants.rank("Sky Sports Main Event 4K"))
        assertEquals(3, ChannelVariants.rank("UK: BBC ONE FHD"))
        assertEquals(2, ChannelVariants.rank("UK: BBC ONE HD"))
        assertEquals(1, ChannelVariants.rank("UK: BBC ONE"))
        assertEquals(0, ChannelVariants.rank("UK: BBC ONE SD"))
    }

    @Test
    fun versionsOfOneChannelGroupBestFirst() {
        val sd = ch(1, "UK: BBC ONE SD")
        val fhd = ch(2, "UK: BBC ONE FHD")
        val plain = ch(3, "UK | BBC ONE")
        val hd = ch(4, "UK: BBC ONE HD")
        val plusOne = ch(5, "UK: BBC ONE +1")
        val index = VariantIndex(listOf(sd, fhd, plain, hd, plusOne))
        assertEquals(listOf(2L, 4L, 3L, 1L), index.group(sd).map { it.id })
        assertEquals(listOf(5L), index.group(plusOne).map { it.id })
    }

    @Test
    fun otherPlaylistsNeverGroup() {
        val index = VariantIndex(listOf(ch(1, "BBC ONE HD", source = 1), ch(2, "BBC ONE SD", source = 2)))
        assertEquals(1, index.group(ch(1, "BBC ONE HD", source = 1)).size)
    }

    @Test
    fun theListShowsOnlyTheBestVersionPresent() {
        val sd = ch(1, "UK: BBC ONE SD")
        val fhd = ch(2, "UK: BBC ONE FHD")
        val hd = ch(3, "UK: BBC ONE HD", category = 20)
        val index = VariantIndex(listOf(sd, fhd, hd))
        val everything: (ChannelEntity) -> Boolean = { true }
        assertTrue(index.isShownIn(fhd, everything))
        assertFalse(index.isShownIn(sd, everything))
        assertFalse(index.isShownIn(hd, everything))
        // In a folder that holds only the HD version, the HD version is the one shown.
        val folder20: (ChannelEntity) -> Boolean = { it.categoryId == 20L }
        assertTrue(index.isShownIn(hd, folder20))
    }
}
