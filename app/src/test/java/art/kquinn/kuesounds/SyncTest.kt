package art.kquinn.kuesounds

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks on bringing the phone's and the Drive's changes together. Pretend changes only. */
class SyncTest {
    private fun e(v: String, at: Long) = Sync.Entry(v, at)

    @Test
    fun theLaterChangeWins() {
        val phone = mapOf("fav:1" to e("1", 200), "fav:2" to e("", 100))
        val drive = mapOf("fav:1" to e("", 150), "fav:2" to e("1", 300), "fav:3" to e("1", 50))
        val merged = Sync.merge(phone, drive)
        assertEquals("1", merged["fav:1"]!!.value)
        assertEquals("1", merged["fav:2"]!!.value)
        assertEquals("1", merged["fav:3"]!!.value)
    }

    @Test
    fun anOfflineChangeSurvivesAnOlderDriveCopy() {
        val phone = mapOf("fav:9" to e("1", 500))
        val drive = emptyMap<String, Sync.Entry>()
        assertEquals(phone, Sync.merge(phone, drive))
    }

    @Test
    fun aBrokenDriveFileReadsAsNothing() {
        assertEquals(0, Sync.read("{not json").size)
        assertEquals("1", Sync.read("""{"fav:1":{"value":"1","at":5}}""")["fav:1"]!!.value)
    }
}
