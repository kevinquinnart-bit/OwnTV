package art.kquinn.kuetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Carry on watching" list and programme reminders, as they are kept. */
class CarryOnTest {
    @Test fun aFilmLeftPartWayThroughIsReadBackTheSame() {
        val w = Positions.Watching("http://tv.example/movie/k/p/55.mkv", "The Thing", "http://img.example/thing.jpg", 2_580_000, 6_180_000, 1_763_300_000_000)
        assertEquals(w, Positions.decode(Positions.encode(w)))
    }

    @Test fun aPartWithNoPictureStillReadsBack() {
        val w = Positions.Watching("http://tv.example/series/k/p/9.mkv", "Taskmaster · S3 E2", "", 90_000, 2_700_000, 5)
        assertEquals(w, Positions.decode(Positions.encode(w)))
    }

    @Test fun somethingElseIsNotAPlace() {
        assertNull(Positions.decode("rubbish"))
        assertNull(Positions.decode(""))
    }

    @Test fun aReminderPopsUpFromAMinuteBeforeUntilFiveMinutesIn() {
        val start = 1_763_330_000_000
        val r = Reminder.make("101", "ITV 1", "I'm a Celebrity", start, start + 3_600_000)
        assertNull(Reminder.due(listOf(r), start - 120_000))
        assertEquals(r, Reminder.due(listOf(r), start - 30_000))
        assertEquals(r, Reminder.due(listOf(r), start + 240_000))
        assertNull(Reminder.due(listOf(r), start + 360_000))
        assertEquals(r, Reminder.fromJson(r.toJson()))
        // The same programme set twice is one reminder.
        assertEquals(r.id, Reminder.make("101", "ITV 1", "I'm a Celebrity", start, start + 3_600_000).id)
    }

    @Test fun aSubtitleLanguageIsKnownHoweverASourceWritesIt() {
        val english = Langs.of("en")
        for (said in listOf("en", "eng", "English", "en-GB", "ENG")) assertEquals(said, true, Langs.matches(english, said))
        for (said in listOf("fre", "es", "ger", "", "estonian")) assertEquals(said, false, Langs.matches(english, said))
        assertEquals(true, Langs.matches(Langs.of("fr"), "fre"))
        assertEquals(true, Langs.matches(Langs.of("pt"), "pob"))
        // A code the app doesn't offer falls back to English, and the list goes round.
        assertEquals("English", Langs.of("xx").name)
        assertEquals("English", Langs.after(Langs.all.last()).name)
    }

    @Test fun theHealthCheckSaysWhatIsDeadAndReadsBackTheSame() {
        val all = listOf(
            Check("Your provider", Check.WORKING, "Signed in."),
            Check("The TV guide", Check.DEAD, "Your provider didn't answer when asked what is on."),
            Check("Google Drive", Check.DOUBT, "Not signed in. Recordings stay on this device."),
            Check("Add-on: Subs", Check.DEAD, "It didn't answer in 15 seconds."),
        )
        assertEquals("2 of 4 not working: The TV guide, Add-on: Subs", Health.summary(all))
        assertEquals("Nothing dead. 1 of 2 to look at", Health.summary(listOf(all[0], all[2])))
        assertEquals("All 1 working", Health.summary(listOf(all[0])))
        assertEquals("Not checked yet", Health.summary(emptyList()))
        assertEquals(all, Health.decode(Health.encode(all)))
    }

    @Test fun aFreeListLinkIsTidiedAndNamed() {
        assertEquals("https://lists.example/uk.m3u", FreeStore.tidy(" lists.example/uk.m3u "))
        assertNull(FreeStore.tidy(""))
        assertNull(FreeStore.tidy("not a link"))
        assertEquals("lists.example · uk.m3u", FreeStore.label("https://lists.example/tv/uk.m3u?x=1"))
        assertEquals("lists.example", FreeStore.label("https://lists.example"))
    }

    @Test fun anAddOnsCataloguesAreReadAndKept() {
        val manifest = com.google.gson.JsonParser.parseString(
            """{"name":"Lists","resources":["catalog"],"catalogs":[
                {"type":"movie","id":"top","name":"Popular","extra":[{"name":"search"},{"name":"skip"}]},
                {"type":"series","id":"top","name":"Popular"},
                {"type":"movie","id":"byyear","name":"By year","extra":[{"name":"genre","isRequired":true}]},
                {"type":"movie","id":"find","name":"Search","extraRequired":["search"]},
                {"type":"channel","id":"tv","name":"Channels"}]}""",
        ).asJsonObject
        // The ones that need something chosen first, or only answer a search, or aren't films or series, are left out.
        assertEquals(listOf("movie|top|Popular", "series|top|Popular"), AddOns.readCatalogues(manifest))
        val addOn = AddOn("http://lists.example", "Lists", streams = false, subtitles = false, catalogues = listOf("movie|top|Popular"))
        assertEquals(addOn, AddOn.fromJson(addOn.toJson()))
        // One kept by an older version of the app, with no catalogues noted, still reads.
        assertEquals(
            AddOn("http://a.example", "A", streams = true, subtitles = false),
            AddOn.fromJson("""{"base":"http://a.example","name":"A","streams":true,"subtitles":false}"""),
        )
    }

    @Test fun somethingWithNoAddressOfItsOwnIsKnownByItsName() {
        assertEquals("kue:The Thing", Playable("The Thing", "").key)
        assertEquals("http://tv.example/1.mkv", Playable("The Thing", "http://tv.example/1.mkv").key)
    }

    @Test fun aFreeChannelOfTheSameNameBecomesTheBackup() {
        val provider = Library(
            listOf(Category("1", "UK")),
            listOf(Channel("10", "BBC News HD", "", "1", "http://tv.example/live/10.ts", 0), Channel("11", "BBC News 4K", "", "1", "http://tv.example/live/11.ts", 0)),
            emptyList(), emptyList(),
        )
        val free = listOf(
            Channel(Library.FREE + "a1", "BBC News (1080p)", "", Library.FREE + "News", "http://free.example/bbcnews.m3u8", 0),
            Channel(Library.FREE + "b2", "Sky News", "", Library.FREE + "News", "http://free.example/sky.m3u8", 0),
        )
        val both = provider.withFree(free)
        // The free categories come first, with one holding every free channel.
        assertEquals(listOf("Free: all free channels", "Free: News", "UK"), both.liveCategories.map { it.name })
        assertEquals(2, both.groups.size)
        val bbc = both.groups[0]
        // The provider's versions first, best picture first; the free one last, though its picture is better than HD.
        assertEquals(listOf("4K", "HD", "Free FHD"), bbc.versions.map { it.label })
        // It starts on the provider's best that isn't 4K, and the small pictures use the provider's lightest.
        assertEquals("HD", bbc.versions[bbc.start].label)
        assertEquals("10", bbc.lightest.id)
        // A free channel the provider doesn't have gets a row of its own.
        assertEquals("Sky News", both.groups[1].name)
        assertEquals(0, both.groups[1].start)
        // With no free channels, nothing changes.
        assertEquals(provider, provider.withFree(emptyList()))
    }

    @Test fun theListsKeptOnTheDeviceReadBackTheSame() {
        val account = Account("http://tv.example:8080", "kev", "secret", "")
        val library = Library(
            listOf(Category("1", "UK")), listOf(Channel("10", "BBC One HD", "http://img/bbc.png", "1", "http://tv.example/live/kev/secret/10.ts", 7)),
            listOf(Category("5", "Horror")), listOf(Film("55", "The Thing (2011)", "http://img/thing.jpg", "5", "http://tv.example/movie/kev/secret/55.mkv")),
            listOf(Category("9", "Comedy")), listOf(Show("90", "Taskmaster", "", "9", About(plot = "Five comedians.", year = "2015"))),
            account, 3600,
        )
        val file = java.io.File.createTempFile("kuetv-library", ".gz").also { it.deleteOnExit() }
        LibraryCache.write(file, account, library, at = 1234)
        val read = LibraryCache.read(file, account)!!
        assertEquals(1234L, read.at)
        assertEquals(true, LibraryCache.same(library, read.library))
        assertEquals(account, read.library.account)
        assertEquals(3600, read.library.serverAheadSec)
        assertEquals("Five comedians.", read.library.shows[0].about.plot)
        // Another account's lists are never shown, and a missing or broken file is no lists.
        assertNull(LibraryCache.read(file, account.copy(user = "someone-else")))
        file.writeText("not a kept list")
        assertNull(LibraryCache.read(file, account))
        assertNull(LibraryCache.read(java.io.File(file.path + ".missing"), account))
    }

    @Test fun keepingInStepTakesTheNewerOfEachPart() {
        val tv = mapOf(
            "favourites" to Sync.Part(500, "live:1\nlive:2"),
            "addOns" to Sync.Part(900, "torrents"),
            "free" to Sync.Part(1, ""),
            "places" to Sync.Part(1, "a"),
            "subtitles" to Sync.Part(300, "true\nen"),
        )
        val phone = mapOf(
            "favourites" to Sync.Part(800, "live:1\nlive:2\nlive:3"),
            "addOns" to Sync.Part(400, "nothing"),
            "free" to Sync.Part(1, "https://lists.example/uk.m3u"),
            "places" to Sync.Part(1, "b"),
            "subtitles" to Sync.Part(300, "true\nen"),
            "somethingNewer" to Sync.Part(999, "from a later version"),
        )
        // The TV takes the phone's newer favourites, and its free list (same age, but fuller). Its own newer add-ons stay.
        assertEquals(setOf("favourites", "free", "places"), Sync.newer(tv, phone).keys)
        // The phone takes the TV's newer add-ons, and nothing it already matches or leads on.
        assertEquals(setOf("addOns"), Sync.newer(phone, tv).keys)
        // After each has taken the other's, neither wants anything more.
        val tvAfter = tv + Sync.newer(tv, phone)
        val phoneAfter = phone + Sync.newer(phone, tv)
        assertEquals(emptySet<String>(), Sync.newer(tvAfter, phoneAfter).keys)
        assertEquals(emptySet<String>(), Sync.newer(phoneAfter, tvAfter).keys)
        assertEquals(tv, Sync.unpack(Sync.pack(tv)))
        assertNull(Sync.unpack("not a state"))
    }

    @Test fun theSetUpCarriesThePairingKey() {
        val setUp = SetUp(Account("http://tv.example", "kev", "secret", ""), setOf("live:1"), emptyList(), true, emptyMap(), listOf("https://lists.example/uk.m3u"), "0123abcd")
        assertEquals(setUp, SetUp.unpack(setUp.pack()))
    }

    @Test fun readyMadeListsHavePlainNames() {
        assertEquals("UK free channels", FreeStore.label("https://iptv-org.github.io/iptv/countries/uk.m3u"))
        assertEquals("Ireland free channels", FreeStore.label("https://iptv-org.github.io/iptv/countries/ie.m3u"))
        assertEquals("Every country's free channels (very large)", FreeStore.label("https://iptv-org.github.io/iptv/index.country.m3u"))
    }

    @Test fun holdingAWindButtonGoesFasterTheLongerItIsHeld() {
        assertEquals(1, windSpeed(0))
        assertEquals(2, windSpeed(20))
        assertEquals(4, windSpeed(50))
        assertEquals(8, windSpeed(200))
        assertEquals("45 s", windWords(-45_000))
        assertEquals("2 min 30 s", windWords(150_000))
        assertEquals("3 min", windWords(180_000))
    }

    @Test fun aStremioAccountsAddOnsAreSortedIntoUsableAndNot() {
        val parse = { t: String -> com.google.gson.JsonParser.parseString(t).asJsonObject }
        val streams = Stremio.entry(parse("""{"transportUrl":"https://torrents.example/manifest.json","manifest":{"name":"Torrents","resources":["stream"],"types":["movie"]}}"""))
        assertEquals("Torrents", streams.name)
        assertEquals("https://torrents.example", streams.addOn?.base)
        assertEquals(true, streams.addOn?.streams)
        // One that only runs on the computer with Stremio on it, and one with nothing KueTV uses, are left out with a reason.
        val local = Stremio.entry(parse("""{"transportUrl":"http://127.0.0.1:11470/local-addon/manifest.json","manifest":{"name":"Local Files","resources":["stream"]}}"""))
        assertNull(local.addOn)
        assertEquals("only works on the computer running Stremio", local.why)
        val useless = Stremio.entry(parse("""{"transportUrl":"https://meta.example/manifest.json","manifest":{"name":"Meta only","resources":["meta"]}}"""))
        assertNull(useless.addOn)
        assertEquals("Added 1: A. Already here: B. Left out: C (why).", Stremio.say(Stremio.Brought(listOf("A"), listOf("B"), listOf("C (why)"))))
    }

    @Test fun aHealthDetailKeepsItsLines() {
        val c = listOf(Check("Add-on: Torrents", Check.DOUBT, "✓ Its description: answers\n! Streams: answers, but none"))
        assertEquals(c, Health.decode(Health.encode(c)))
    }

    @Test fun anAddOnsTvChannelsAreRowsOfTheirOwn() {
        val library = Library(
            listOf(Category("1", "UK")),
            listOf(Channel("10", "BBC One HD", "", "1", "http://tv.example/live/10.ts", 0)),
            emptyList(), emptyList(),
        ).withFree(listOf(Channel(Library.FREE + "tv:1", "BBC One", "", Library.FREE + "Live (TV add-on)", AddOns.TV + "https://tv.example|bbc1", 0)))
        // The add-on's BBC One doesn't join the provider's BBC One: it plays only through the add-ons.
        assertEquals(2, library.groups.size)
        assertEquals(listOf("HD"), library.groups.first { !it.versions[0].channel.url.startsWith(AddOns.TV) }.versions.map { it.label })
        assertEquals(false, AddOns.plain(AddOns.TV + "https://tv.example|bbc1"))
        assertEquals(true, AddOns.plain("http://tv.example/live/10.ts"))
        // An add-on's TV catalogue is kept, alongside its films and series.
        val manifest = com.google.gson.JsonParser.parseString("""{"catalogs":[{"type":"tv","id":"uk","name":"UK TV"},{"type":"movie","id":"top","name":"Top"}]}""").asJsonObject
        assertEquals(listOf("tv|uk|UK TV", "movie|top|Top"), AddOns.readCatalogues(manifest))
    }
}
