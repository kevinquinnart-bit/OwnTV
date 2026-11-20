package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RTÉ catch-up, from cut-down copies of RTÉ's own feeds (20 November 2026). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RteCatchUpTest {
    private val schedule = """{"entries":[
      {"plchannelschedule${'$'}listings":[
        {"pllisting${'$'}startTime":1000,"pllisting${'$'}program":{"plprogram${'$'}seriesId":"http://data.entertainment.tv.theplatform.eu/entertainment/data/Program/guid/2700894001/SI0000001694"}},
        {"pllisting${'$'}startTime":3000,"pllisting${'$'}program":{"plprogram${'$'}seriesId":"http://data.entertainment.tv.theplatform.eu/entertainment/data/Program/guid/2700894001/SI0000000564"}},
        {"pllisting${'$'}startTime":9000,"pllisting${'$'}program":{"plprogram${'$'}seriesId":"http://data.entertainment.tv.theplatform.eu/entertainment/data/Program/guid/2700894001/SI0000000825"}}]},
      {"plchannelschedule${'$'}listings":[
        {"pllisting${'$'}startTime":2000,"pllisting${'$'}program":{"plprogram${'$'}seriesId":"http://data.entertainment.tv.theplatform.eu/entertainment/data/Program/guid/2700894001/10001504-00-0000"}},
        {"pllisting${'$'}startTime":2500,"pllisting${'$'}program":{}}]}]}"""

    private val catalogue = """{"entries":[
      {"guid":"SI0000000564","title":"Fair City","plprogram${'$'}programType":"series","rte${'$'}defaultThumbnail":"https://www.rte.ie/images/002075ad.jpg",
       "plprogramavailability${'$'}media":[{"plmedia${'$'}availabilityTags":["WW","connectedtv"]}]},
      {"guid":"10001504-00-0000","title":"Beyond Paradise","plprogram${'$'}programType":"series","rte${'$'}defaultThumbnail":"",
       "plprogramavailability${'$'}media":[{"plmedia${'$'}availabilityTags":["ROI","connectedtv"]}]},
      {"guid":"SI0000001694","title":"The Late Late Show","plprogram${'$'}programType":"series","plprogramavailability${'$'}media":[]},
      {"guid":"SI0000001694","title":"The Late Late Show","plprogram${'$'}programType":"series","plprogramavailability${'$'}media":[{"plmedia${'$'}availabilityTags":["WW"]}]},
      {"guid":"SI0000003606","title":"Weather (Nine O'Clock)","plprogram${'$'}programType":"series","plprogramavailability${'$'}media":[{"plmedia${'$'}availabilityTags":["WW"]}]}]}"""

    @Test fun newestFirstAndNothingStillToCome() {
        assertEquals(listOf("SI0000000564", "10001504-00-0000", "SI0000001694"), RteCatchUp.recent(schedule, now = 5000))
    }

    @Test fun whereEachPlaysAndTheWeatherLeftOut() {
        val d = RteCatchUp.details(catalogue)
        assertTrue(d["SI0000000564"]!!.anywhere)
        assertEquals(false, d["10001504-00-0000"]!!.anywhere)
        assertTrue("a second copy with episodes counts", d["SI0000001694"]!!.anywhere)
        assertEquals(null, d["SI0000003606"])
    }

    @Test fun theLinkTriedOnTheTv() {
        val d = RteCatchUp.details(catalogue)
        assertEquals("https://www.rte.ie/rpng-static/ctv/androidTv/series/the-late-late-show/SI0000001694", RteCatchUp.link(d["SI0000001694"]!!))
    }
}
