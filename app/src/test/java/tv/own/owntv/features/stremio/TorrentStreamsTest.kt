package tv.own.owntv.features.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentStreamsTest {
    private val gb = 1024L * 1024 * 1024

    private fun t(title: String, detail: String? = null, size: Long? = null) =
        StremioAddons.Stream("addon", title, detail, "", emptyMap(), infoHash = "a".repeat(40), sizeBytes = size)

    private fun order(free: Long, vararg streams: StremioAddons.Stream) = StremioAutoPlay.rankTorrents(streams.toList(), free).map { it.title }

    @Test fun readsSeeders() {
        assertEquals(123, StremioAutoPlay.seeders(t("Film 1080p", "👤 123 💾 2.1 GB")))
        assertNull(StremioAutoPlay.seeders(t("Film 1080p")))
    }

    @Test fun readsTheSizeFromTheText() {
        assertEquals((2.1 * gb).toLong(), StremioAutoPlay.sizeBytes(t("Film 1080p", "👤 123 💾 2.1 GB ⚙️ site")))
        assertEquals(700L * 1024 * 1024, StremioAutoPlay.sizeBytes(t("Film", "Film.2019.720p 700 MB")))
        assertNull(StremioAutoPlay.sizeBytes(t("Film 1080p")))
    }

    @Test fun reportedSizeWinsOverTheText() =
        assertEquals(5 * gb, StremioAutoPlay.sizeBytes(t("Film", "💾 2 GB", size = 5 * gb)))

    @Test fun theSizeAfterTheDiskMarkIsTheFiles() =
        // A season pack names the pack's size first and the episode's after the disk.
        assertEquals(1 * gb, StremioAutoPlay.sizeBytes(t("Show S01", "Show.S01.40 GB.pack 👤 9 💾 1 GB")))

    @Test fun torrentsTooBigForTheBoxAreLeftOut() =
        assertEquals(listOf("small 1080p"), order(3 * gb, t("big 2160p", "💾 40 GB"), t("small 1080p", "💾 2 GB")))

    @Test fun unknownSizeIsStillTried() =
        assertEquals(listOf("mystery 1080p"), order(3 * gb, t("mystery 1080p")))

    @Test fun wellSharedBeforeBiggerPicture() =
        assertEquals(
            listOf("busy 720p", "quiet 1080p"),
            order(50 * gb, t("quiet 1080p", "👤 1 💾 2 GB"), t("busy 720p", "👤 80 💾 1 GB")),
        )

    @Test fun englishBeforeWellShared() =
        assertEquals(
            listOf("Film 720p", "Film 1080p ITA"),
            order(50 * gb, t("Film 1080p ITA", "👤 500"), t("Film 720p", "👤 2")),
        )

    @Test fun cleansTheTorrentId() {
        assertEquals("ab".repeat(20), StremioAddons.cleanInfoHash(" " + "AB".repeat(20) + " "))
        assertNull(StremioAddons.cleanInfoHash("not-a-hash"))
        assertNull(StremioAddons.cleanInfoHash(""))
    }

    @Test fun keepsOnlyTrackerAddresses() =
        assertEquals(
            listOf("udp://tracker.example:1337/announce"),
            StremioAddons.trackersFrom(listOf("tracker:udp://tracker.example:1337/announce", "dht:" + "a".repeat(40))),
        )

    @Test fun playsTheFileTheAddonPointedAt() =
        assertEquals(2, TorrentEngine.chooseFile(listOf("a.mkv", "b.mkv", "c.mkv"), listOf(9L, 5L, 1L), wanted = 2))

    @Test fun otherwiseTheBiggestVideo() {
        assertEquals(1, TorrentEngine.chooseFile(listOf("extras.zip", "film.mkv", "sample.mkv"), listOf(900L, 500L, 10L), wanted = null))
        assertEquals(1, TorrentEngine.chooseFile(listOf("sample.mkv", "film.mkv"), listOf(10L, 500L), wanted = 7))
        assertNull(TorrentEngine.chooseFile(emptyList(), emptyList(), wanted = null))
    }

    @Test fun readsRanges() {
        assertEquals(0L to 999L, TorrentEngine.parseRange(null, 1000))
        assertEquals(100L to 999L, TorrentEngine.parseRange("bytes=100-", 1000))
        assertEquals(100L to 199L, TorrentEngine.parseRange("bytes=100-199", 1000))
        assertEquals(100L to 999L, TorrentEngine.parseRange("bytes=100-5000", 1000))
        assertEquals(900L to 999L, TorrentEngine.parseRange("bytes=-100", 1000))
        assertNull(TorrentEngine.parseRange("bytes=1000-", 1000))
        assertNull(TorrentEngine.parseRange("bytes=200-100", 1000))
        assertNull(TorrentEngine.parseRange("bytes=abc-", 1000))
    }

    @Test fun startNeedsTheFirstThreeMegabytes() {
        // 1 MB parts: three of them. 16 MB parts: still two. Never past the end of the file.
        assertEquals(listOf(10, 11, 12), TorrentEngine.startPieces(first = 10, last = 500, pieceLength = 1024 * 1024))
        assertEquals(listOf(0, 1), TorrentEngine.startPieces(first = 0, last = 500, pieceLength = 16 * 1024 * 1024))
        assertEquals(listOf(4), TorrentEngine.startPieces(first = 4, last = 4, pieceLength = 1024 * 1024))
    }

    @Test fun buildsTheMagnetLink() =
        assertEquals(
            "magnet:?xt=urn:btih:" + "a".repeat(40) + "&tr=udp%3A%2F%2Ft.example%3A1337%2Fannounce",
            TorrentEngine.magnet("a".repeat(40), listOf("udp://t.example:1337/announce")),
        )
}
