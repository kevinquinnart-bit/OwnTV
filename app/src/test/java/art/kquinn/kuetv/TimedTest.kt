package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Programmes set to record: how they are kept and told apart. */
class TimedTest {
    @Test fun isKeptAndReadBackTheSame() {
        val plan = Timed.make("BBC One", "The \"News\" at Ten", "http://tv/live/u/p/101.ts", 1_794_780_000_000, 1_794_781_800_000)
        assertEquals(plan, Timed.fromJson(plan.toJson()))
        assertNull(Timed.fromJson("not a programme"))
    }

    @Test fun theSameProgrammeIsTheSameOneAndOthersAreNot() {
        val a = Timed.make("BBC One", "News", "http://tv/101.ts", 1_794_780_000_000, 1_794_781_800_000)
        assertEquals(a.id, Timed.make("BBC One HD", "News at Ten", "http://tv/101.ts", 1_794_780_000_000, 1_794_781_800_000).id)
        assertNotEquals("a later programme on the same channel", a.id, Timed.make("BBC One", "News", "http://tv/101.ts", 1_794_781_800_000, 1_794_783_600_000).id)
        assertNotEquals("the same time on another channel", a.id, Timed.make("BBC Two", "News", "http://tv/102.ts", 1_794_780_000_000, 1_794_781_800_000).id)
        assertTrue(a.id >= 0)
    }

    @Test fun finishedOnesAreDroppedAndTheRestComeSoonestFirst() {
        val early = Timed.make("A", "Early", "http://tv/1.ts", 1_000, 2_000)
        val onNow = Timed.make("B", "On now", "http://tv/2.ts", 2_500, 4_000)
        val later = Timed.make("C", "Later", "http://tv/3.ts", 9_000, 9_500)
        assertEquals(listOf(onNow, later), Timed.stillToCome(listOf(later, early, onNow), 3_000))
    }
}
