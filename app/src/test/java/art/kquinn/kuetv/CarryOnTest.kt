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
        assertEquals(listOf("UK", "Free: News"), both.liveCategories.map { it.name })
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
}
