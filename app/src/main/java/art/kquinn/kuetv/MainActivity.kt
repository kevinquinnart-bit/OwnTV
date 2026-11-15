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

private enum class Section(val label: String) { LIVE("Live TV"), FILMS("Films"), SETTINGS("Settings") }

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

    /** The row or poster that was sent to the player; focus returns to it. */
    var resumeKey by mutableStateOf<String?>(null)
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
                                film,
                                { onPlay(listOf(Playable(film.name, film.url)), 0, false, key) },
                                if (key == resumeKey) Modifier.focusRequester(resume) else Modifier,
                            )
                        }
                    }
                }
            }
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
private fun Poster(film: Film, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            if (film.poster.isNotBlank()) AsyncImage(model = film.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            film.name,
            color = if (focused) Accent else Color.White,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).height(46.dp),
        )
    }
}
