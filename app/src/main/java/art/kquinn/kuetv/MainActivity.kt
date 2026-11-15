package art.kquinn.kuetv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Page = Color(0xFF0E1626)
private val Panel = Color(0xFF16213A)
private val Accent = Color(0xFFFFD23F)
private val Soft = Color(0xFFB9C2D3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { App() } }
    }
}

/** What is playing: the list it came from (so ▲ ▼ can change channel), where in it, and its kind. */
private class Playing(val items: List<Playable>, val index: Int, val live: Boolean)

@Composable
private fun App() {
    val context = LocalContext.current
    var account by remember { mutableStateOf(AccountStore.load(context)) }
    var editing by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<Library?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Playing?>(null) }
    // What the browsing screen was showing, kept while the player covers it, so closing the player
    // comes back to the same list, at the same place, on the row or poster that was playing.
    val browse = remember(library) { library?.let { BrowseState(startOnFilms = it.channels.isEmpty()) } }

    LaunchedEffect(account, reload) {
        if (!account.isSet) return@LaunchedEffect
        library = null
        problem = null
        try {
            library = withContext(Dispatchers.IO) { Provider.load(account) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderProblem) {
            problem = e.message
        } catch (e: Exception) {
            problem = "Something went wrong loading your channels. Try again."
        }
    }
    Box(Modifier.fillMaxSize().background(Page)) {
        val lib = library
        val now = playing
        when {
            // Only the player is on screen while it plays, so no key can reach the lists behind it.
            now != null -> PlayerScreen(now.items, now.index, now.live, onClose = { playing = null })
            !account.isSet || editing -> SetupScreen(
                current = account,
                canCancel = account.isSet,
                onCancel = { editing = false },
                onSave = { next ->
                    AccountStore.save(context, next)
                    editing = false
                    if (next == account) reload++ else account = next
                },
            )
            problem != null -> ProblemScreen(problem.orEmpty(), onRetry = { reload++ }, onEdit = { editing = true })
            lib == null || browse == null -> LoadingScreen()
            else -> MainScreen(
                library = lib,
                state = browse,
                onPlay = { items, index, live, key -> browse.resumeKey = key; playing = Playing(items, index, live) },
                onEdit = { editing = true },
                onReload = { reload++ },
            )
        }
    }
}

// --------------------------------------------------------------------------------------------- pieces

/** One row that takes focus and OK: yellow while focused, a pale bar while it is the chosen one. */
@Composable
private fun FocusRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chosen: Boolean = false,
    onFocus: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent else if (chosen) Color(0x33FFFFFF) else Color.Transparent)
            .onFocusChanged {
                val now = it.isFocused
                if (now && !focused) onFocus()
                focused = now
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 21.sp,
            fontWeight = if (focused || chosen) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(22.dp))
        Text("Loading your channels and films…", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun ProblemScreen(message: String, onRetry: () -> Unit, onEdit: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 220.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(start = 16.dp, bottom = 26.dp))
        FocusRow("Try again", onRetry, Modifier.focusRequester(first))
        FocusRow("Change provider details", onEdit)
    }
}

// --------------------------------------------------------------------------------------------- setup

@Composable
private fun SetupScreen(current: Account, canCancel: Boolean, onCancel: () -> Unit, onSave: (Account) -> Unit) {
    var server by remember { mutableStateOf(current.server) }
    var user by remember { mutableStateOf(current.user) }
    var pass by remember { mutableStateOf(current.pass) }
    var playlist by remember { mutableStateOf(current.playlist) }
    var warning by remember { mutableStateOf<String?>(null) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }

    val submit: (String, String, String, String) -> Unit = { s, u, p, l ->
        val next = AccountStore.tidy(s, u, p, l)
        if (next.isSet) onSave(next) else warning = "Fill in the server, username and password, or a playlist link."
    }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val phone = PhoneSetup { s, u, p, l -> main.post { submit(s, u, p, l) } }
        phoneAddress = phone.start()
        onDispose { phone.stop() }
    }
    if (canCancel) BackHandler { onCancel() }

    Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Set up KueTV", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            val address = phoneAddress
            if (address != null) {
                Text("Easiest: on your phone, open", color = Soft, fontSize = 22.sp)
                Text(address, color = Accent, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                Text("and type your provider details there. Your phone must be on the same Wi-Fi as this TV.", color = Soft, fontSize = 20.sp)
            } else {
                Text("Type your provider details in the boxes on the right.", color = Soft, fontSize = 22.sp)
            }
            warning?.let { Text(it, color = Accent, fontSize = 20.sp, modifier = Modifier.padding(top = 26.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Server address", server) { server = it }
            Field("Username", user) { user = it }
            Field("Password", pass) { pass = it }
            Field("Or one playlist link", playlist) { playlist = it }
            Spacer(Modifier.height(6.dp))
            FocusRow("Save", { submit(server, user, pass, playlist) })
            if (canCancel) FocusRow("Cancel", onCancel)
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 16.sp) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

// --------------------------------------------------------------------------------------------- main

private enum class Section(val label: String) { LIVE("Live TV"), GUIDE("TV Guide"), CATCHUP("Catch-up"), FILMS("Films"), SERIES("Series"), SETTINGS("Settings") }

private const val ALL = "\u0000all"

/** Where the browsing screen is: the section, the category in each, how far each list is scrolled. */
private class BrowseState(startOnFilms: Boolean) {
    var section by mutableStateOf(if (startOnFilms) Section.FILMS else Section.LIVE)
    var liveCategory by mutableStateOf(ALL)
    var filmCategory by mutableStateOf(ALL)
    val liveCategoryList = LazyListState()
    val filmCategoryList = LazyListState()
    val channelList = LazyListState()
    val filmGrid = LazyGridState()

    val guide = ChannelPick()
    val catchUp = ChannelPick()

    var showCategory by mutableStateOf(ALL)
    val showCategoryList = LazyListState()
    val showGrid = LazyGridState()
    var openShow by mutableStateOf<Show?>(null)
    val episodeList = LazyListState()
    var showJustClosed by mutableStateOf(false)
    val loadedEpisodes = HashMap<String, List<Episode>>()

    /** The row or poster that was sent to the player; focus returns to it. */
    var resumeKey by mutableStateOf<String?>(null)
}

/** Where the TV Guide (or Catch-up) is: the category, the channel opened, and any date-and-time step. */
private class ChannelPick {
    var category by mutableStateOf(ALL)
    val categoryList = LazyListState()
    val channelList = LazyListState()
    val programmeList = LazyListState()
    var open by mutableStateOf<Channel?>(null)

    /** Null: the programme list. -1: choosing a day. Otherwise: choosing a time on the day that starts then. */
    var pickDay by mutableStateOf<Long?>(null)

    /** Set when a channel's page has just been closed, so focus goes back to that channel's row. */
    var justClosed by mutableStateOf(false)
    val loaded = HashMap<String, List<Programme>>()
}

@Composable
private fun MainScreen(
    library: Library,
    state: BrowseState,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
    onEdit: () -> Unit,
    onReload: () -> Unit,
) {
    var section by state::section
    val liveCategory = state.liveCategory
    val filmCategory = state.filmCategory
    val resumeKey = state.resumeKey
    val scope = rememberCoroutineScope()
    val menuFocus = remember { FocusRequester() }
    val resume = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Back from the player: onto what was playing. Otherwise (or if that row has gone): the menu.
        withFrameNanos { }
        withFrameNanos { }
        val back = resumeKey != null && runCatching { resume.requestFocus() }.getOrDefault(false)
        if (!back) runCatching { menuFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // The menu.
        Column(
            Modifier.width(190.dp).fillMaxHeight().background(Panel, RoundedCornerShape(18.dp)).padding(vertical = 18.dp, horizontal = 8.dp)
                .focusRestorer().focusGroup(),
        ) {
            Heading("Menu")
            Section.entries.forEach { s ->
                FocusRow(s.label, { section = s }, if (s == section) Modifier.focusRequester(menuFocus) else Modifier, chosen = s == section)
            }
        }
        when (section) {
            Section.LIVE -> {
                Categories(library.liveCategories, liveCategory, state.liveCategoryList) {
                    if (it != state.liveCategory) {
                        state.liveCategory = it
                        scope.launch { state.channelList.scrollToItem(0) }
                    }
                }
                val shown = remember(library, liveCategory) {
                    if (liveCategory == ALL) library.channels else library.channels.filter { it.categoryId == liveCategory }
                }
                val playables = remember(shown) { shown.map { Playable(it.name, it.url) } }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Heading(if (shown.size == 1) "1 channel" else "${shown.size} channels")
                    LazyColumn(Modifier.fillMaxSize().focusRestorer(), state = state.channelList) {
                        itemsIndexed(shown, key = { _, c -> c.id }) { i, channel ->
                            val key = "live:" + channel.id
                            FocusRow(
                                channel.name,
                                { onPlay(playables, i, true, key) },
                                if (key == resumeKey) Modifier.focusRequester(resume) else Modifier,
                                leading = { Logo(channel.logo) },
                            )
                        }
                    }
                }
            }
            Section.FILMS -> {
                Categories(library.filmCategories, filmCategory, state.filmCategoryList) {
                    if (it != state.filmCategory) {
                        state.filmCategory = it
                        scope.launch { state.filmGrid.scrollToItem(0) }
                    }
                }
                val shown = remember(library, filmCategory) {
                    if (filmCategory == ALL) library.films else library.films.filter { it.categoryId == filmCategory }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Heading(if (shown.size == 1) "1 film" else "${shown.size} films")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = state.filmGrid,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(shown, key = { _, f -> f.id }) { _, film ->
                            val key = "film:" + film.id
                            Poster(
                                film.name, film.poster,
                                { onPlay(listOf(Playable(film.name, film.url)), 0, false, key) },
                                if (key == resumeKey) Modifier.focusRequester(resume) else Modifier,
                            )
                        }
                    }
                }
            }
            Section.SERIES -> SeriesPane(library, state, resume, onPlay)
            Section.GUIDE -> ChannelGuide(library, false, state.guide, state, resume, onPlay)
            Section.CATCHUP -> ChannelGuide(library, true, state.catchUp, state, resume, onPlay)
            Section.SETTINGS -> Column(Modifier.width(520.dp).fillMaxHeight()) {
                Heading("Settings")
                FocusRow("Change provider details", onEdit)
                FocusRow("Reload channels and films", onReload)
                Text(
                    "KueTV 2 · version " + BuildConfig.VERSION_NAME,
                    color = Soft, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 22.dp),
                )
            }
        }
    }
}

/** The category column: "All" first, then the provider's own, in its order. Moving onto one shows it. */
@Composable
private fun Categories(categories: List<Category>, chosen: String, list: LazyListState, onChoose: (String) -> Unit) {
    Column(Modifier.width(270.dp).fillMaxHeight().background(Panel, RoundedCornerShape(18.dp)).padding(vertical = 18.dp, horizontal = 8.dp)) {
        Heading("Categories")
        LazyColumn(Modifier.fillMaxSize().focusRestorer(), state = list) {
            item(key = ALL) { FocusRow("All", { onChoose(ALL) }, chosen = chosen == ALL, onFocus = { onChoose(ALL) }) }
            itemsIndexed(categories, key = { _, c -> c.id }) { _, c ->
                FocusRow(c.name, { onChoose(c.id) }, chosen = chosen == c.id, onFocus = { onChoose(c.id) })
            }
        }
    }
}

@Composable
private fun Logo(url: String) {
    Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
    }
}

@Composable
private fun Poster(name: String, poster: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Panel)) {
            if (poster.isNotBlank()) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            name,
            color = if (focused) Accent else Color.White,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).height(46.dp),
        )
    }
}

// --------------------------------------------------------------------------------------------- guide and catch-up

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val CHOOSING_DAY = -1L

@Composable
private fun Note(text: String) {
    Text(text, color = Soft, fontSize = 22.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
}

/**
 * TV Guide and Catch-up share one screen. Pick a channel (OK) and its page opens in place of the lists:
 * the guide shows what is on now and next, and OK watches the channel; catch-up shows what has been on,
 * newest first, under "Pick a date and time", and OK plays from the start. Back returns to the channels.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ChannelGuide(
    library: Library,
    catchUp: Boolean,
    pick: ChannelPick,
    state: BrowseState,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val account = library.account
    if (account == null) {
        Column(Modifier.weight(1f)) { Note("The guide and catch-up need a login: server address, username and password. A playlist link on its own doesn't give them.") }
        return
    }
    val channels = remember(library, catchUp) { if (catchUp) library.channels.filter { it.catchupDays > 0 } else library.channels }
    if (channels.isEmpty()) {
        Column(Modifier.weight(1f)) { Note(if (catchUp) "Your provider marks no channel as having catch-up." else "Your provider listed no channels.") }
        return
    }
    val opened = pick.open
    if (opened != null) {
        ProgrammePage(opened, catchUp, account, library.serverAheadSec, pick, state, resume, onPlay)
        return
    }
    val scope = rememberCoroutineScope()
    val categories = remember(library, channels) {
        val used = channels.mapTo(HashSet()) { it.categoryId }
        library.liveCategories.filter { it.id in used }
    }
    Categories(categories, pick.category, pick.categoryList) {
        if (it != pick.category) {
            pick.category = it
            scope.launch { pick.channelList.scrollToItem(0) }
        }
    }
    val shown = remember(channels, pick.category) { if (pick.category == ALL) channels else channels.filter { it.categoryId == pick.category } }
    LaunchedEffect(Unit) {
        if (!pick.justClosed) return@LaunchedEffect
        pick.justClosed = false
        withFrameNanos { }
        withFrameNanos { }
        runCatching { resume.requestFocus() }
    }
    Column(Modifier.weight(1f).fillMaxHeight()) {
        Heading(if (catchUp) "Pick a channel to catch up on" else "Pick a channel to see its guide")
        LazyColumn(Modifier.fillMaxSize().focusRestorer(), state = pick.channelList) {
            itemsIndexed(shown, key = { _, c -> c.id }) { _, channel ->
                val key = "pick:" + channel.id
                FocusRow(
                    channel.name,
                    {
                        state.resumeKey = key
                        pick.pickDay = null
                        scope.launch { pick.programmeList.scrollToItem(0) }
                        pick.open = channel
                    },
                    if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                    leading = { Logo(channel.logo) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ProgrammePage(
    channel: Channel,
    catchUp: Boolean,
    account: Account,
    serverAheadSec: Int,
    pick: ChannelPick,
    state: BrowseState,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val now = remember(channel.id) { System.currentTimeMillis() }
    val windowMs = channel.catchupDays * DAY_MS
    val dayText = remember { java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()) }
    val timeText = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val day: (Long) -> String = { dayText.format(java.util.Date(it)) }
    val time: (Long) -> String = { timeText.format(java.util.Date(it)) }
    val step = pick.pickDay

    BackHandler {
        when (step) {
            null -> { pick.justClosed = true; pick.open = null }
            CHOOSING_DAY -> pick.pickDay = null
            else -> pick.pickDay = CHOOSING_DAY
        }
    }

    // null while loading; a failure carries the sentence to show.
    val loaded by produceState<Result<List<Programme>>?>(pick.loaded[channel.id]?.let { Result.success(it) }, channel.id) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(Provider.guide(account, channel.id))
            } catch (e: ProviderProblem) {
                Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(ProviderProblem("Couldn't load the guide for this channel."))
            }
        }
        value?.getOrNull()?.let { pick.loaded[channel.id] = it }
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(loaded != null, step) {
        if (loaded == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val back = step == null && state.resumeKey?.startsWith("prog:") == true && runCatching { resume.requestFocus() }.getOrDefault(false)
        if (!back) runCatching { first.requestFocus() }
    }

    Column(Modifier.weight(1f).fillMaxHeight()) {
        Heading(channel.name + if (catchUp) " · catch-up" else " · guide")
        val result = loaded
        when {
            result == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }

            // Choosing a day: today back to the oldest day the provider keeps.
            step == CHOOSING_DAY -> {
                val today = startOfDay(now)
                val days = (0..channel.catchupDays).map { startOfDay(today - it * DAY_MS + DAY_MS / 2) }.filter { it + DAY_MS > now - windowMs }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(days) { i, start ->
                        val label = when (i) { 0 -> "Today"; 1 -> "Yesterday"; else -> day(start) }
                        FocusRow(label, { pick.pickDay = start }, if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }

            // Choosing a time on that day: every half hour that has already gone and is still kept, latest first.
            step != null -> {
                val slots = (47 downTo 0).map { step + it * 30L * 60 * 1000 }.filter { it < now - 60_000 && it >= now - windowMs }
                if (slots.isEmpty()) Note("Nothing is kept for that day.")
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(slots) { i, at ->
                        FocusRow(
                            day(at) + "  " + time(at),
                            {
                                val minutes = ((now - at) / 60_000).toInt().coerceIn(5, 240)
                                val url = Provider.catchupUrlAt(account, channel.id, at, minutes, serverAheadSec)
                                state.resumeKey = null
                                onPlay(listOf(Playable(channel.name + " · " + day(at) + " " + time(at), url)), 0, false, "time:$at")
                            },
                            if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }

            else -> {
                val all = result.getOrNull()
                val rows = when {
                    all == null -> emptyList()
                    catchUp -> all.filter { it.startMs < now && it.startMs >= now - windowMs }.sortedByDescending { it.startMs }
                    else -> all.filter { it.stopMs > now }
                }
                val lead = if (catchUp) 1 else 0
                LazyColumn(Modifier.fillMaxSize(), state = pick.programmeList) {
                    if (catchUp) item(key = "pick") {
                        FocusRow("Pick a date and time", { pick.pickDay = CHOOSING_DAY }, Modifier.focusRequester(first))
                    }
                    if (all == null) item(key = "problem") { Note(result.exceptionOrNull()?.message ?: "Couldn't load the guide for this channel.") }
                    else if (rows.isEmpty()) item(key = "none") {
                        Note(if (catchUp) "Your provider sent no past programmes for this channel. Pick a date and time instead." else "Your provider sent no guide for this channel.")
                    }
                    if (!catchUp && rows.isEmpty()) item(key = "watch") {
                        FocusRow("Watch " + channel.name, { onPlay(listOf(Playable(channel.name, channel.url)), 0, true, "pick:" + channel.id) }, Modifier.focusRequester(first))
                    }
                    itemsIndexed(rows, key = { _, p -> p.startMs }) { i, programme ->
                        val key = "prog:" + programme.startMs
                        val onNow = programme.startMs <= now && programme.stopMs > now
                        ProgrammeRow(
                            whenText = (if (onNow) "Now · " else day(programme.startMs) + " · ") + time(programme.startMs) + " to " + time(programme.stopMs),
                            title = programme.title.ifBlank { "No title" },
                            description = programme.description,
                            onClick = {
                                if (catchUp) {
                                    val url = Provider.catchupUrl(account, channel.id, programme, serverAheadSec)
                                    onPlay(listOf(Playable(channel.name + " · " + programme.title, url)), 0, false, key)
                                } else {
                                    onPlay(listOf(Playable(channel.name, channel.url)), 0, true, key)
                                }
                            },
                            modifier = (if (i == 0 && lead == 0) Modifier.focusRequester(first) else Modifier)
                                .then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

private fun startOfDay(ms: Long): Long {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = ms
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** A programme: when it is on, its title, and its description while the row is focused. */
@Composable
private fun ProgrammeRow(whenText: String, title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(whenText, color = if (focused) Color.Black else Soft, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = if (focused) Color.Black else Color.White, fontSize = 22.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (focused && description.isNotBlank()) {
            Text(description, color = Color.Black, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// --------------------------------------------------------------------------------------------- series

/**
 * Series: posters by category, like Films. OK on one opens its page in place of the posters: its
 * episodes under a heading for each season. OK plays an episode, and the next one follows when it
 * ends. Back returns to the posters.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SeriesPane(
    library: Library,
    state: BrowseState,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val account = library.account
    if (account == null || library.shows.isEmpty()) {
        Column(Modifier.weight(1f)) {
            Note(if (account == null) "Series need a login: server address, username and password. A playlist link on its own doesn't list them." else "Your provider listed no series.")
        }
        return
    }
    val opened = state.openShow
    if (opened != null) {
        EpisodePage(opened, account, state, resume, onPlay)
        return
    }
    val scope = rememberCoroutineScope()
    Categories(library.showCategories, state.showCategory, state.showCategoryList) {
        if (it != state.showCategory) {
            state.showCategory = it
            scope.launch { state.showGrid.scrollToItem(0) }
        }
    }
    val shown = remember(library, state.showCategory) {
        if (state.showCategory == ALL) library.shows else library.shows.filter { it.categoryId == state.showCategory }
    }
    LaunchedEffect(Unit) {
        if (!state.showJustClosed) return@LaunchedEffect
        state.showJustClosed = false
        withFrameNanos { }
        withFrameNanos { }
        runCatching { resume.requestFocus() }
    }
    Column(Modifier.weight(1f).fillMaxHeight()) {
        Heading(if (shown.size == 1) "1 series" else "${shown.size} series")
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = state.showGrid,
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(shown, key = { _, s -> s.id }) { _, show ->
                val key = "show:" + show.id
                Poster(
                    show.name, show.poster,
                    {
                        state.resumeKey = key
                        scope.launch { state.episodeList.scrollToItem(0) }
                        state.openShow = show
                    },
                    if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EpisodePage(
    show: Show,
    account: Account,
    state: BrowseState,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    BackHandler { state.showJustClosed = true; state.openShow = null }

    val loaded by produceState<Result<List<Episode>>?>(state.loadedEpisodes[show.id]?.let { Result.success(it) }, show.id) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(Provider.episodes(account, show.id))
            } catch (e: ProviderProblem) {
                Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(ProviderProblem("Couldn't load the episodes for this series."))
            }
        }
        value?.getOrNull()?.let { state.loadedEpisodes[show.id] = it }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(loaded != null) {
        if (loaded == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val back = state.resumeKey?.startsWith("episode:") == true && runCatching { resume.requestFocus() }.getOrDefault(false)
        if (!back) runCatching { first.requestFocus() }
    }

    Column(Modifier.weight(1f).fillMaxHeight()) {
        Heading(show.name)
        val result = loaded
        val episodes = result?.getOrNull()
        when {
            result == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            episodes == null -> Note(result.exceptionOrNull()?.message ?: "Couldn't load the episodes for this series.")
            episodes.isEmpty() -> Note("Your provider listed no episodes for this series.")
            else -> {
                val label: (Episode) -> String = { e ->
                    val plain = "Episode " + e.number
                    if (e.title.isBlank() || e.title.equals(plain, ignoreCase = true)) plain else e.number.toString() + ". " + e.title
                }
                val playables = remember(episodes) { episodes.map { Playable(show.name + " · S" + it.season + " E" + it.number, it.url) } }
                LazyColumn(Modifier.fillMaxSize(), state = state.episodeList) {
                    episodes.forEachIndexed { i, episode ->
                        if (i == 0 || episodes[i - 1].season != episode.season) {
                            item(key = "season:" + episode.season) {
                                Box(Modifier.padding(top = if (i == 0) 0.dp else 14.dp)) { Heading("Season " + episode.season) }
                            }
                        }
                        item(key = episode.id) {
                            val key = "episode:" + episode.id
                            FocusRow(
                                label(episode),
                                { onPlay(playables, i, false, key) },
                                (if (i == 0) Modifier.focusRequester(first) else Modifier)
                                    .then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}
