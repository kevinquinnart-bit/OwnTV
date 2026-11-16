package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copying a set-up from one pretend device to another on this machine. */
class SetupShareTest {
    private val setUp = SetUp(
        Account("http://tv.example:8080", "kev", "p w/\"1\"", ""),
        setOf("live:101", "film:555", "show:77"),
        listOf(AddOn("https://a.example/x", "Good Streams", streams = true, subtitles = false)),
        smallTv = false,
    )

    @Test fun aSetUpIsPackedAndUnpackedTheSame() {
        assertEquals(setUp, SetUp.unpack(setUp.pack()))
        assertNull(SetUp.unpack("not a set-up"))
    }

    @Test fun theRightCodeGetsTheSetUpAndTheWrongOneDoesNot() {
        var taken = 0
        val sender = SetUpSender("4821", { setUp.pack() }, { taken++ })
        val port = sender.start(0)!!
        try {
            assertNull(SetUpTaker.take("127.0.0.1", "0000", port))
            assertEquals(0, taken)
            assertEquals(setUp, SetUpTaker.take("127.0.0.1", "4821", port))
            assertEquals(1, taken)
        } finally {
            sender.stop()
        }
    }

    @Test fun afterFiveWrongCodesItStopsAnsweringEvenToTheRightOne() {
        val sender = SetUpSender("4821", { setUp.pack() })
        val port = sender.start(0)!!
        try {
            repeat(5) { assertNull(SetUpTaker.take("127.0.0.1", "111$it", port)) }
            assertNull(SetUpTaker.take("127.0.0.1", "4821", port))
        } finally {
            sender.stop()
        }
    }

    @Test fun aSendingDeviceIsFoundOnTheNetwork() {
        val sender = SetUpSender("4821", { setUp.pack() })
        val port = sender.start(0)!!
        try {
            assertTrue(SetUpTaker.find("127.0.0.77", port).contains("127.0.0.1"))
            assertEquals(emptyList<String>(), SetUpTaker.find("nonsense", port))
        } finally {
            sender.stop()
        }
    }
}
