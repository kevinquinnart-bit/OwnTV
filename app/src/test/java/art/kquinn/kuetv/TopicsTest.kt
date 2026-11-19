package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Live TV's categories in topics: the provider's and the free lists' names land in the right one. */
class TopicsTest {
    @Test fun namesLandInTheRightTopic() {
        assertEquals("sport", Topics.of("UK | Sport"))
        assertEquals("sport", Topics.of("IE | SPORTS"))
        assertEquals("sport", Topics.of("Free: Football"))
        assertEquals("news", Topics.of("UK | News"))
        assertEquals("kids", Topics.of("Kids Movies"))
        assertEquals("films", Topics.of("UK | Movies"))
        assertEquals("ent", Topics.of("UK | Entertainment"))
        assertEquals("ent", Topics.of("IE | General"))
        assertEquals("docs", Topics.of("Documentary"))
        assertEquals("other", Topics.of("Adult"))
        assertEquals("sport", Topics.of("Todays Live Events"))
        assertEquals("docs", Topics.of("Free: Education (Watchio.live TV)"))
        assertEquals("ent", Topics.of("Free: United Kingdom (Watchio.live TV)"))
    }

    @Test fun onlyTopicsWithChannelsShow() {
        val cats = listOf(Category("1", "UK | Sport"), Category("2", "UK | News"), Category("free:Sports", "Free: Sports"))
        val shown = Topics.categories(Topics.byCategory(cats), setOf("1", "free:Sports"))
        assertEquals(listOf("topic:sport"), shown.map { it.id })
    }
}
