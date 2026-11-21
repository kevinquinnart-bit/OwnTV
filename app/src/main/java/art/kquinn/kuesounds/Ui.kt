package art.kquinn.kuesounds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** KueSounds' colours: KueTV's dark blue page, deeper blue boxes, and its yellow. */
object K {
    val Page = Color(0xFF0E1626)
    val Panel = Color(0xFF16213A)
    val Raised = Color(0xFF263A63)
    val Accent = Color(0xFFFFD23F)
    val Ink = Color.White
    val Soft = Color(0xFFB9C2D3)
    /** The light blue of the Kue K: the second word of every Kue app's name ("TV", "Sounds"). */
    val KueBlue = Color(0xFF5CCEFF)
    val Poppins = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_bold, FontWeight.Bold),
        Font(R.font.poppins_bold, FontWeight.SemiBold),
    )
}

/** The screens a person can be on, newest last. */
sealed class Screen {
    data object Home : Screen()
    data class AlbumPage(val key: String) : Screen()
    data class ArtistPage(val name: String) : Screen()
    data class FolderPage(val id: String?, val name: String) : Screen()
    data object NowPlaying : Screen()
    data object Settings : Screen()
}

enum class Tab(val label: String) {
    ALBUMS("Albums"), ARTISTS("Artists"), SONGS("Songs"), SINGLES("Singles"), FOLDERS("Folders"), FAVOURITES("Favourites"), ON_PHONE("On phone"),
}

object Ui {
    @Composable
    fun App(signedIn: Boolean, signingIn: Boolean, signInProblem: String, onSignIn: () -> Unit) {
        Box(Modifier.fillMaxSize().background(K.Page).safeDrawingPadding()) {
            if (!signedIn) SignIn(signingIn, signInProblem, onSignIn) else Main()
        }
    }

    @Composable
    private fun Words(
        text: String,
        size: Int = 16,
        colour: Color = K.Ink,
        bold: Boolean = false,
        lines: Int = 1,
        modifier: Modifier = Modifier,
        centre: Boolean = false,
    ) {
        Text(
            text, modifier = modifier, color = colour, fontSize = size.sp, fontFamily = K.Poppins,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = lines, overflow = TextOverflow.Ellipsis,
            textAlign = if (centre) TextAlign.Center else null, lineHeight = (size * 1.3f).sp,
        )
    }

    /** "Kue" in white and "Sounds" in the Kue blue, as KueTV's name is written. */
    @Composable
    private fun Brand(size: Int, modifier: Modifier = Modifier) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("Kue")
                // A soft dark shadow keeps the light blue clear on any background.
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = K.KueBlue,
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), androidx.compose.ui.geometry.Offset(0f, 2f), 6f),
                    ),
                ) { append("Sounds") }
            },
            modifier = modifier, color = K.Ink, fontSize = size.sp, fontFamily = K.Poppins, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }

    /** The Kue K, the same mark as KueTV's. */
    @Composable
    private fun Mark(size: Dp) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.kue_mark),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }

    /** A big yellow button, or a blue one when it is the second choice. */
    @Composable
    private fun Button(label: String, main: Boolean = true, glyph: Glyph? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Row(
            modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(if (main) K.Accent else K.Panel)
                .clickable(onClick = onClick).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (glyph != null) {
                Sign(glyph, 20.dp, if (main) K.Page else K.Ink)
                Spacer(Modifier.width(8.dp))
            }
            Words(label, 16, if (main) K.Page else K.Ink, bold = main)
        }
    }

    @Composable
    private fun RoundButton(glyph: Glyph, label: String, size: Dp = 44.dp, onClick: () -> Unit) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(K.Panel).clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Sign(glyph, size * 0.5f, K.Ink) }
    }

    // ---------------------------------------------------------------------------------------------
    // Signing in

    @Composable
    private fun SignIn(busy: Boolean, problem: String, onSignIn: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mark(84.dp)
            Spacer(Modifier.height(20.dp))
            Brand(30)
            Spacer(Modifier.height(10.dp))
            Words("Your music, straight from Google Drive.", 17, K.Soft, lines = 3, centre = true)
            Spacer(Modifier.height(32.dp))
            if (busy) {
                CircularProgressIndicator(color = K.Accent)
            } else {
                Button("Sign in with Google", modifier = Modifier.fillMaxWidth(), onClick = onSignIn)
                Spacer(Modifier.height(12.dp))
                Words("Pick the Google account your music is in.", 15, K.Soft, lines = 3, centre = true)
            }
            if (problem.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Words(problem, 16, K.Accent, lines = 7, centre = true)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The library

    @Composable
    private fun Main() {
        var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
        val go: (Screen) -> Unit = { stack = stack + it }
        val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
        BackHandler(enabled = stack.size > 1, onBack = back)

        val data = Scanner.data
        val view = Scanner.view
        val albums = view.albums
        val artists = view.artists
        val songs = view.songs

        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when (val s = stack.last()) {
                    Screen.Home -> Home(albums, artists, songs, go)
                    is Screen.AlbumPage -> view.byKey[s.key]?.let { AlbumPage(it, back, go) } ?: Gone(back)
                    is Screen.ArtistPage -> artists.firstOrNull { it.name == s.name }?.let { ArtistPage(it, back, go) } ?: Gone(back)
                    is Screen.FolderPage -> FolderPage(s, data, back, go)
                    Screen.NowPlaying -> NowPlaying(back)
                    Screen.Settings -> Settings(back)
                }
            }
            if (stack.last() != Screen.NowPlaying && Playing.active) MiniPlayer { go(Screen.NowPlaying) }
        }
    }

    @Composable
    private fun Gone(back: () -> Unit) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            RoundButton(Glyph.BACK, "Back", onClick = back)
            Spacer(Modifier.height(20.dp))
            Words("This is no longer in your music.", 17, K.Soft)
        }
    }

    @Composable
    private fun Home(albums: List<Album>, artists: List<Artist>, songs: List<Track>, go: (Screen) -> Unit) {
        var tab by rememberSaveable { mutableStateOf(Tab.ALBUMS) }
        var query by rememberSaveable { mutableStateOf("") }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(36.dp)
                    Spacer(Modifier.width(10.dp))
                    Brand(24, Modifier.weight(1f))
                    RoundButton(Glyph.GEAR, "Settings") { go(Screen.Settings) }
                }
                SearchBox(query) { query = it }
                if (query.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Tab.entries) { t ->
                            val on = t == tab
                            Box(
                                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) K.Accent else K.Panel)
                                    .clickable { tab = t }.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Words(t.label, 15, if (on) K.Page else K.Ink, bold = on) }
                        }
                    }
                }
                Status()
            }
            Box(Modifier.weight(1f)) {
                when {
                    Scanner.data.tracks.isEmpty() -> Empty()
                    query.isNotBlank() -> {
                        val results = remember(query, albums) { Library.search(query, Scanner.data.tracks, albums, artists) }
                        SearchResults(results, go)
                    }
                    tab == Tab.ALBUMS -> AlbumGrid(albums, go)
                    tab == Tab.ARTISTS -> ArtistList(artists, go)
                    tab == Tab.SONGS -> SongList(songs)
                    tab == Tab.SINGLES -> {
                        val titles = Scanner.data.singleTitles
                        val singles = remember(titles, songs) {
                            songs.filter { it.id in titles }.sortedWith(compareBy({ Library.sortName(it.artist) }, { Library.sortName(it.title) }))
                        }
                        if (singles.isEmpty()) Note("Songs that came out as singles show here, with the single's cover. They are found at step 3 in Settings.")
                        else SingleList(singles, titles)
                    }
                    tab == Tab.FAVOURITES -> {
                        val ids = Sync.favouriteIds()
                        val favs = remember(ids, songs) { songs.filter { it.id in ids } }
                        if (favs.isEmpty()) Note("Tap the heart on a playing song to keep it here.") else SongList(favs)
                    }
                    tab == Tab.ON_PHONE -> {
                        val saved = Offline.saved
                        val onPhone = remember(saved, albums) { albums.filter { al -> al.tracks.any { it.id in saved } } }
                        if (onPhone.isEmpty()) Note("Save an album to play it with no internet. Open an album and tap Save to phone.")
                        else AlbumGrid(onPhone, go)
                    }
                    else -> FolderList(null, Scanner.data, go)
                }
            }
        }
    }

    @Composable
    private fun Note(text: String) {
        Box(Modifier.fillMaxSize().padding(28.dp)) { Words(text, 17, K.Soft, lines = 4) }
    }

    @Composable
    private fun SearchBox(query: String, onChange: (String) -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(K.Panel).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sign(Glyph.SEARCH, 20.dp, K.Soft)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Words("Search songs, artists, albums", 16, K.Soft)
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = K.Ink, fontSize = 16.sp, fontFamily = K.Poppins),
                    cursorBrush = SolidColor(K.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(36.dp).clickable(onClickLabel = "Clear") { onChange("") }, contentAlignment = Alignment.Center) {
                    Sign(Glyph.CLOSE, 18.dp, K.Soft)
                }
            }
        }
    }

    /** One line saying what the scan is doing, or what went wrong, or that a newer KueSounds is ready. */
    @Composable
    private fun Status() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var updating by remember { mutableStateOf("") }
        val offer = Updates.offer
        if (offer != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.Raised).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Words(if (updating.isNotEmpty()) updating else "KueSounds " + offer.name + " is ready. After this one, updates go on by themselves.", 15, lines = 3, modifier = Modifier.weight(1f))
                if (updating.isEmpty()) {
                    Button("Update", modifier = Modifier.height(44.dp)) {
                        if (!Updates.mayInstall(context)) {
                            Updates.openInstallPermission(context)
                        } else {
                            updating = "Downloading · 0%"
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    runCatching { Updates.downloadChecked(context) { p -> updating = "Downloading · $p%" } }.getOrNull()
                                }
                                if (file != null) {
                                    updating = ""
                                    // Through Android's own installer, so from now on updates go on by themselves.
                                    runCatching { Updates.installSession(context, file) }.onFailure { Updates.install(context, file) }
                                } else {
                                    updating = "The download didn't work. Try again later."
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(40.dp).clickable(onClickLabel = "Not now") { Updates.putOff = offer.code; Updates.offer = null }, contentAlignment = Alignment.Center) {
                        Sign(Glyph.CLOSE, 18.dp, K.Soft)
                    }
                }
            }
        }
        val status = Scanner.status.ifEmpty { Fixer.status }.ifEmpty { Offline.status }.ifEmpty { Updates.doing }
        if (status.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = K.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Words(status, 14, K.Soft)
            }
        }
        if (Scanner.problem.isNotEmpty() && status.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Words(Scanner.problem, 14, K.Accent, lines = 2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button("Try again", main = false, modifier = Modifier.height(44.dp)) { Scanner.scan() }
            }
        }
    }

    @Composable
    private fun Empty() {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (Scanner.running) {
                CircularProgressIndicator(color = K.Accent)
                Spacer(Modifier.height(16.dp))
                Words("Looking through your Google Drive.", 17, K.Soft, lines = 2, centre = true)
            } else {
                Words("No music found in this Google Drive.", 18, lines = 2, centre = true)
                Spacer(Modifier.height(8.dp))
                Words(Google.email, 15, K.Soft, centre = true)
                Spacer(Modifier.height(20.dp))
                Button("Look again") { Scanner.scan() }
            }
        }
    }

    @Composable
    private fun Cover(file: File?, modifier: Modifier, corner: Dp = 14.dp, glyphSize: Dp = 40.dp) {
        Box(modifier.clip(RoundedCornerShape(corner)).background(K.Panel), contentAlignment = Alignment.Center) {
            if (file != null) {
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Sign(Glyph.DISC, glyphSize, K.Soft)
            }
        }
    }

    @Composable
    private fun AlbumGrid(albums: List<Album>, go: (Screen) -> Unit, header: (@Composable () -> Unit)? = null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            items(albums, key = { it.key }) { al ->
                Column(Modifier.clickable { go(Screen.AlbumPage(al.key)) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Cover(Scanner.artFor(al), Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(2.dp))
                    Words(al.title, 15, bold = true)
                    Words(al.artist, 13, K.Soft)
                }
            }
        }
    }

    @Composable
    private fun Row2(top: String, below: String, glyph: Glyph?, highlight: Boolean = false, lead: String = "", onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                .background(if (highlight) K.Panel else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lead.isNotEmpty()) {
                Words(lead, 15, if (highlight) K.Accent else K.Soft, modifier = Modifier.width(30.dp))
                Spacer(Modifier.width(8.dp))
            } else if (glyph != null) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(K.Panel), contentAlignment = Alignment.Center) { Sign(glyph, 22.dp, K.Soft) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Words(top, 16, if (highlight) K.Accent else K.Ink, bold = highlight)
                if (below.isNotEmpty()) Words(below, 13, K.Soft)
            }
        }
    }

    @Composable
    private fun ArtistList(artists: List<Artist>, go: (Screen) -> Unit) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            items(artists, key = { "a:" + it.name }) { a ->
                Row2(a.name, count(a.albums.size, "album") + " · " + count(a.songCount, "song"), Glyph.PERSON) { go(Screen.ArtistPage(a.name)) }
            }
        }
    }

    private fun count(n: Int, word: String): String = String.format("%,d", n) + " " + word + if (n == 1) "" else "s"

    @Composable
    private fun SongList(songs: List<Track>) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                Row2(t.title, t.artist, Glyph.NOTE, highlight = t.id == Playing.currentId) { Playing.play(songs, i) }
            }
        }
    }

    /** Songs that came out as singles, each with its single's cover and name. */
    @Composable
    private fun SingleList(songs: List<Track>, titles: Map<String, String>) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                val on = t.id == Playing.currentId
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) K.Panel else Color.Transparent).clickable { Playing.play(songs, i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(Scanner.singleArtFor(t), Modifier.size(56.dp), corner = 10.dp, glyphSize = 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Words(t.title, 16, if (on) K.Accent else K.Ink, bold = on)
                        Words(t.artist + " · single, " + (titles[t.id] ?: t.title), 13, K.Soft)
                    }
                }
            }
        }
    }

    @Composable
    private fun FolderList(folder: String?, data: LibraryData, go: (Screen) -> Unit) {
        val subs = remember(data, folder) { Library.foldersIn(folder, data.folders, data.tracks) }
        val songs = remember(data, folder) { Library.songsIn(folder, data.folders, data.tracks) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            items(subs, key = { "f:" + it.id }) { f -> Row2(f.name, "", Glyph.FOLDER) { go(Screen.FolderPage(f.id, f.name)) } }
            itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                Row2(t.title, t.artist, Glyph.NOTE, highlight = t.id == Playing.currentId) { Playing.play(songs, i) }
            }
        }
    }

    @Composable
    private fun SearchResults(r: Library.Results, go: (Screen) -> Unit) {
        if (r.artists.isEmpty() && r.albums.isEmpty() && r.songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp)) { Words("Nothing matches that.", 17, K.Soft) }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            if (r.artists.isNotEmpty()) item { Heading("Artists") }
            items(r.artists, key = { "a:" + it.name }) { a -> Row2(a.name, count(a.albums.size, "album"), Glyph.PERSON) { go(Screen.ArtistPage(a.name)) } }
            if (r.albums.isNotEmpty()) item { Heading("Albums") }
            items(r.albums, key = { "l:" + it.key }) { al -> Row2(al.title, al.artist, Glyph.DISC) { go(Screen.AlbumPage(al.key)) } }
            if (r.songs.isNotEmpty()) item { Heading("Songs") }
            itemsIndexed(r.songs, key = { _, t -> "s:" + t.id }) { i, t ->
                Row2(t.title, t.artist + " · " + t.album, Glyph.NOTE, highlight = t.id == Playing.currentId) { Playing.play(r.songs, i) }
            }
        }
    }

    @Composable
    private fun Heading(text: String) {
        Words(text, 19, bold = true, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp))
    }

    // ---------------------------------------------------------------------------------------------
    // Album, artist and folder pages

    @Composable
    private fun AlbumPage(al: Album, back: () -> Unit, go: (Screen) -> Unit) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth()) { RoundButton(Glyph.BACK, "Back", onClick = back) }
                    Spacer(Modifier.height(12.dp))
                    Cover(Scanner.artFor(al), Modifier.size(220.dp), corner = 18.dp, glyphSize = 72.dp)
                    Spacer(Modifier.height(14.dp))
                    Words(al.title, 22, bold = true, lines = 2, centre = true)
                    val bits = listOfNotNull(al.artist, al.year.takeIf { it > 0 }?.toString(), count(al.tracks.size, "song"))
                    Words(bits.joinToString(" · "), 15, K.Soft, lines = 2, centre = true, modifier = Modifier.clickable { go(Screen.ArtistPage(al.artist)) })
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button("Play", glyph = Glyph.PLAY, modifier = Modifier.weight(1f)) { Playing.play(al.tracks, 0) }
                        Button("Shuffle", main = false, glyph = Glyph.SHUFFLE, modifier = Modifier.weight(1f)) { Playing.play(al.tracks, 0, shuffled = true) }
                    }
                    Spacer(Modifier.height(12.dp))
                    SaveToPhone(al)
                    if (Fixer.canFix(al)) {
                        Spacer(Modifier.height(12.dp))
                        FixFiles(listOf(al))
                    }
                }
            }
            itemsIndexed(al.tracks, key = { _, t -> t.id }) { i, t ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    val len = if (t.durationMs > 0) Library.clock(t.durationMs) else ""
                    // The song's own artist shows only when it isn't the album's (a guest, or a compilation).
                    val onPhone = if (t.id in Offline.saved) "On phone" else ""
                    val sub = listOf(t.artist.takeIf { it != al.artist }.orEmpty(), len, onPhone).filter { it.isNotEmpty() }.joinToString(" · ")
                    Row2(t.title, sub, null, highlight = t.id == Playing.currentId, lead = if (t.trackNo > 0) t.trackNo.toString() else (i + 1).toString()) {
                        Playing.play(al.tracks, i)
                    }
                }
            }
        }
    }

    /** "Save to phone": keeps the album's songs on the phone to play with no internet. */
    @Composable
    private fun SaveToPhone(al: Album) {
        val context = LocalContext.current
        val saved = Offline.saved
        val all = al.tracks.all { it.id in saved }
        when {
            all -> Row(verticalAlignment = Alignment.CenterVertically) {
                Words("Saved on this phone.", 14, K.Soft, modifier = Modifier.weight(1f))
                Button("Remove", main = false, modifier = Modifier.height(44.dp)) { Offline.remove(al.tracks) }
            }
            Offline.isWaiting(al) || Offline.status.contains(al.tracks.firstOrNull { it.id !in saved }?.title ?: "\u0000") ->
                Words(
                    "Saving to phone · " + al.tracks.count { it.id in saved } + " of " + al.tracks.size,
                    14, K.Accent, centre = true,
                )
            else -> Button("Save to phone", main = false, glyph = Glyph.SAVE, modifier = Modifier.fillMaxWidth()) {
                Fixer.askNotices()
                Offline.save(context, al.tracks)
            }
        }
    }

    /**
     * "Fix the files" for one album or many: asks once to be sure, then puts the right cover and album
     * details into the files in the Drive.
     */
    @Composable
    private fun FixFiles(albums: List<Album>, label: String = "Fix the files") {
        val context = LocalContext.current
        var sure by remember { mutableStateOf(false) }
        val todo = albums.filter { Fixer.canFix(it) && !Fixer.isFixed(it) }
        val songs = todo.sumOf { al -> al.tracks.count { Tagger.canFix(it.fileName) && !it.fixed } }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                // Nothing matched yet: nothing has been fixed either, so it mustn't say so.
                albums.isEmpty() -> Words(
                    if (Scanner.running) "Nothing to fix yet. Albums are looked up once the song details are read." else "No albums matched yet.",
                    14, K.Soft, lines = 2,
                )
                todo.isEmpty() -> Words("Files fixed in your Drive.", 14, K.Soft, centre = albums.size == 1)
                Fixer.running && todo.any { Fixer.isWaiting(it) } -> Words("Waiting to be fixed.", 14, K.Soft, centre = albums.size == 1)
                Fixer.running && albums.size == 1 && Fixer.status.contains(todo.first().title) -> Words(Fixer.status, 14, K.Accent, lines = 2, centre = true)
                !sure -> Button(label, main = false, modifier = Modifier.fillMaxWidth()) { sure = true }
                else -> {
                    Words(
                        "This puts the right cover and album details into " + count(songs, "song file") +
                            " in your Drive and takes out any other pictures. Google keeps the old versions for 30 days.",
                        14, K.Soft, lines = 5,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button("Fix them", modifier = Modifier.weight(1f)) {
                            sure = false
                            Fixer.fix(context, todo)
                        }
                        Button("Cancel", main = false, modifier = Modifier.weight(1f)) { sure = false }
                    }
                }
            }
            if (Fixer.permissionProblem.isNotEmpty()) Words(Fixer.permissionProblem, 14, K.Accent, lines = 6)
        }
    }

    @Composable
    private fun ArtistPage(a: Artist, back: () -> Unit, go: (Screen) -> Unit) {
        val all = remember(a) { a.albums.flatMap { it.tracks } }
        AlbumGrid(a.albums, go) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundButton(Glyph.BACK, "Back", onClick = back)
                Words(a.name, 24, bold = true, lines = 2)
                Words(count(a.albums.size, "album") + " · " + count(a.songCount, "song"), 15, K.Soft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Play all", glyph = Glyph.PLAY, modifier = Modifier.weight(1f)) { Playing.play(all, 0) }
                    Button("Shuffle", main = false, glyph = Glyph.SHUFFLE, modifier = Modifier.weight(1f)) { Playing.play(all, 0, shuffled = true) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun FolderPage(s: Screen.FolderPage, data: LibraryData, back: () -> Unit, go: (Screen) -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Glyph.BACK, "Back", onClick = back)
                Spacer(Modifier.width(14.dp))
                Words(s.name, 20, bold = true, modifier = Modifier.weight(1f))
            }
            Box(Modifier.weight(1f)) { FolderList(s.id, data, go) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Playing

    @Composable
    private fun MiniPlayer(open: () -> Unit) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.Raised)
                .clickable(onClick = open).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(K.Panel), contentAlignment = Alignment.Center) {
                val art = Playing.artwork
                if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Sign(Glyph.NOTE, 22.dp, K.Soft)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Words(Playing.title, 15, bold = true)
                Words(if (Playing.problem.isNotEmpty()) Playing.problem else Playing.artist, 13, if (Playing.problem.isNotEmpty()) K.Accent else K.Soft)
            }
            Box(Modifier.size(48.dp).clickable(onClickLabel = if (Playing.isPlaying) "Pause" else "Play") { Playing.toggle() }, contentAlignment = Alignment.Center) {
                if (Playing.buffering) CircularProgressIndicator(color = K.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                else Sign(if (Playing.isPlaying) Glyph.PAUSE else Glyph.PLAY, 26.dp, K.Ink)
            }
        }
    }

    @Composable
    private fun NowPlaying(close: () -> Unit) {
        var position by remember { mutableLongStateOf(0L) }
        var dragging by remember { mutableStateOf(false) }
        var dragAt by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                if (!dragging) position = Playing.positionMs()
                delay(500)
            }
        }
        if (!Playing.active) {
            LaunchedEffect(Unit) { close() }
            return
        }
        val duration = Playing.durationMs
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Glyph.DOWN, "Close", onClick = close)
                Words(if (Playing.album.isNotEmpty()) "Playing from " + Playing.album else "", 14, K.Soft, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), centre = true)
                Spacer(Modifier.size(44.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(K.Panel), contentAlignment = Alignment.Center) {
                    val art = Playing.artwork
                    if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Sign(Glyph.DISC, 96.dp, K.Soft)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Words(Playing.title, 24, bold = true, lines = 2)
                    Words(Playing.artist, 17, K.Soft)
                    if (Playing.problem.isNotEmpty()) Words(Playing.problem, 15, K.Accent, lines = 2)
                }
                val fav = Sync.isFavourite(Playing.currentId)
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(K.Panel)
                        .clickable(onClickLabel = if (fav) "Take out of favourites" else "Add to favourites") { Sync.flipFavourite(Playing.currentId) },
                    contentAlignment = Alignment.Center,
                ) { Sign(if (fav) Glyph.HEART_FULL else Glyph.HEART, 26.dp, if (fav) K.Accent else K.Soft) }
            }
            Column {
                Slider(
                    value = if (dragging) dragAt else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = true; dragAt = it },
                    onValueChangeFinished = {
                        Playing.seekTo((dragAt * duration).toLong())
                        position = (dragAt * duration).toLong()
                        dragging = false
                    },
                    colors = SliderDefaults.colors(thumbColor = K.Ink, activeTrackColor = K.Accent, inactiveTrackColor = K.Raised),
                )
                Row {
                    Words(Library.clock(if (dragging) (dragAt * duration).toLong() else position), 14, K.Soft, modifier = Modifier.weight(1f))
                    Words(Library.clock(duration), 14, K.Soft)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Control(Glyph.SHUFFLE, "Shuffle", 48.dp, if (Playing.shuffle) K.Accent else K.Soft) { Playing.flipShuffle() }
                Control(Glyph.PREVIOUS, "Previous", 56.dp, K.Ink) { Playing.previous() }
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(K.Accent).clickable(onClickLabel = if (Playing.isPlaying) "Pause" else "Play") { Playing.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (Playing.buffering) CircularProgressIndicator(color = K.Page, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    else Sign(if (Playing.isPlaying) Glyph.PAUSE else Glyph.PLAY, 34.dp, K.Page)
                }
                Control(Glyph.NEXT, "Next", 56.dp, K.Ink) { Playing.next() }
                Control(
                    if (Playing.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Glyph.REPEAT_ONE else Glyph.REPEAT,
                    "Repeat", 48.dp, if (Playing.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) K.Accent else K.Soft,
                ) { Playing.nextRepeat() }
            }
        }
    }

    @Composable
    private fun Control(glyph: Glyph, label: String, size: Dp, colour: Color, onClick: () -> Unit) {
        Box(Modifier.size(size).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Sign(glyph, size * 0.55f, colour)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Settings

    /**
     * The four steps KueSounds takes with your music, each saying what it does and how far it has got.
     * Steps 1 to 3 only read; the files in the Drive are changed only at step 4, when Fix is pressed.
     */
    @Composable
    private fun MusicSteps(view: Scanner.LibraryView) {
        val data = Scanner.data
        val songs = data.tracks.size
        val read = data.tracks.count { it.tagged }
        val albums = view.albums.size
        val stage = Scanner.stage
        val n = { x: Int -> String.format("%,d", x) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Words("Steps 1 to 3 only read your music. Your files are only changed at step 4, when you choose.", 14, K.Soft, lines = 3)
            Step(
                1, "Find your music", "Lists every song in your Google Drive.",
                state = if (stage == 1) NOW else if (songs > 0) DONE else WAITING,
                progress = if (stage == 1) Scanner.status.ifBlank { "Looking…" } else if (songs > 0) count(songs, "song") + " found" else "",
            )
            Step(
                2, "Read the song details", "Reads each song's title, artist, album and any picture inside it.",
                state = if (stage == 2) NOW else if (songs > 0 && read >= songs) DONE else WAITING,
                progress = n(read) + " of " + n(songs) + " read",
            )
            val singlesChecked = data.tracks.count { it.singleChecked }
            Step(
                3, "Look up albums and singles",
                "Finds each album's proper name, year and cover in an online music catalogue. Songs on compilations are given the album they first came out on. Songs that came out as singles get the single's cover, in the Singles tab.",
                state = if (stage == 3) NOW else if (albums > 0 && view.lookedUp >= albums && singlesChecked >= songs) DONE else WAITING,
                progress = n(view.lookedUp) + " of " + n(albums) + " albums looked up · " + n(view.matched.size) + " matched\n" +
                    n(singlesChecked) + " of " + n(songs) + " songs checked for singles · " + n(data.singleTitles.size) + " found" +
                    (if (stage == 3 && Scanner.status.isNotEmpty()) "\nNow: " + Scanner.status else "") +
                    (if (Scanner.waitingForWifi && !Scanner.running) "\nLooking for singles carries on when the phone is on Wi-Fi." else ""),
            )
            Step(
                4, "Fix the files", "Only when you press Fix: writes the right album name and cover into each song file in your Drive and takes out wrong pictures. Google keeps the old files for 30 days.",
                state = if (Fixer.running) NOW else if (view.matched.isNotEmpty() && view.leftToFix == 0) DONE else WAITING,
                progress = if (view.matched.isEmpty()) "Nothing ready to fix yet"
                else n(view.leftToFix) + " of " + count(view.matched.size, "matched album") + " still to fix · see Fix the files below",
            )
            if (Scanner.running) Words(
                if (stage == 1) "Now on step 1." else if (stage == 2) "Now on step 2. Keep KueSounds open: it carries on from here next time if it is stopped."
                else if (stage == 3) "Now on step 3. Keep KueSounds open: it carries on from here next time if it is stopped." else "Working…",
                14, K.Accent, lines = 3,
            )
        }
    }

    private const val DONE = 2
    private const val NOW = 1
    private const val WAITING = 0

    /** One step: its number (a tick once done), its name, what it does, and how far it has got. */
    @Composable
    private fun Step(number: Int, name: String, what: String, state: Int, progress: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (state == WAITING) K.Page else K.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Words(if (state == DONE) "✓" else number.toString(), 14, if (state == WAITING) K.Soft else K.Page, bold = true)
            }
            Column(Modifier.weight(1f)) {
                Words(name + when (state) { DONE -> " · done"; NOW -> " · now"; else -> "" }, 16, if (state == NOW) K.Accent else K.Ink, bold = true)
                Words(what, 14, K.Soft, lines = 4)
                if (progress.isNotEmpty()) Words(progress, 14, if (state == NOW) K.Accent else K.Ink, lines = 6)
            }
        }
    }

    @Composable
    private fun Settings(back: () -> Unit) {
        val data = Scanner.data
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Glyph.BACK, "Back", onClick = back)
                Spacer(Modifier.width(14.dp))
                Words("Settings", 22, bold = true)
            }
            Panel {
                Words("Google account", 14, K.Soft)
                Words(Google.email.ifBlank { "Signed in" }, 17)
            }
            Panel {
                Words("Your music", 14, K.Soft)
                val view = Scanner.view
                Words(count(data.tracks.size, "song") + " · " + count(view.albums.size, "album"), 17)
                Words(String.format("%,d", view.withCover) + " with a cover", 14, K.Soft)
                Spacer(Modifier.height(12.dp))
                MusicSteps(view)
                Spacer(Modifier.height(8.dp))
                if (!Scanner.running) Button("Look for new music", modifier = Modifier.fillMaxWidth()) { Scanner.scan() }
            }
            Panel {
                Words("On this phone", 14, K.Soft)
                val saved = Offline.saved
                val bytes = remember(saved) { Offline.bytesUsed() }
                Words(count(saved.size, "song") + " saved · " + String.format("%.1f", bytes / 1_000_000_000.0) + " GB", 17)
                if (Offline.status.isNotEmpty()) Words(Offline.status, 14, K.Accent, lines = 2)
                val when_ = Sync.syncedAt
                Words(
                    if (when_ == 0L) "Favourites not synced with the Drive yet."
                    else "Favourites synced with the Drive at " + java.text.SimpleDateFormat("HH:mm, d MMM", java.util.Locale.UK).format(java.util.Date(when_)),
                    14, K.Soft, lines = 2,
                )
            }
            Panel {
                Words("Fix the files", 14, K.Soft)
                val fixable = Scanner.view.matched
                val left = Scanner.view.leftToFix
                Words(count(fixable.size, "album") + " matched · " + String.format("%,d", left) + " still to fix", 17)
                if (Fixer.status.isNotEmpty()) Words(Fixer.status, 14, K.Accent, lines = 2)
                if (Fixer.lastResult.isNotEmpty() && !Fixer.running) Words(Fixer.lastResult, 14, K.Soft, lines = 2)
                Spacer(Modifier.height(8.dp))
                FixFiles(fixable, "Fix all matched albums")
            }
            Panel {
                Words("KueSounds", 14, K.Soft)
                Words("Version " + BuildConfig.VERSION_NAME, 17)
                Spacer(Modifier.height(8.dp))
                var copied by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                Button(if (copied) "Copied. Paste it to Claude." else "Copy report for Claude", main = false, modifier = Modifier.fillMaxWidth()) {
                    scope.launch {
                        val text = withContext(Dispatchers.Default) { Scanner.report() }
                        val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                        clip?.setPrimaryClip(android.content.ClipData.newPlainText("KueSounds report", text))
                        copied = true
                    }
                }
            }
        }
    }

    @Composable
    private fun Panel(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

/** The small drawn signs used on buttons and rows. */
enum class Glyph { PLAY, PAUSE, NEXT, PREVIOUS, SHUFFLE, REPEAT, REPEAT_ONE, BACK, DOWN, CLOSE, SEARCH, GEAR, NOTE, DISC, FOLDER, PERSON, SAVE, HEART, HEART_FULL }

@Composable
fun Sign(glyph: Glyph, size: Dp, colour: Color) {
    Canvas(Modifier.size(size)) { drawGlyph(glyph, colour) }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val w = size.width
    val u = w / 24f
    val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun p(vararg pts: Float, close: Boolean = false): Path = Path().apply {
        moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) {
            lineTo(pts[i] * u, pts[i + 1] * u)
            i += 2
        }
        if (close) close()
    }
    when (g) {
        Glyph.PLAY -> drawPath(p(7f, 4f, 20f, 12f, 7f, 20f, close = true), c)
        Glyph.PAUSE -> {
            drawRoundRect(c, Offset(6f * u, 4f * u), Size(4f * u, 16f * u), androidx.compose.ui.geometry.CornerRadius(u))
            drawRoundRect(c, Offset(14f * u, 4f * u), Size(4f * u, 16f * u), androidx.compose.ui.geometry.CornerRadius(u))
        }
        Glyph.NEXT -> {
            drawPath(p(5f, 5f, 16f, 12f, 5f, 19f, close = true), c)
            drawRect(c, Offset(16f * u, 5f * u), Size(2.5f * u, 14f * u))
        }
        Glyph.PREVIOUS -> {
            drawPath(p(19f, 5f, 8f, 12f, 19f, 19f, close = true), c)
            drawRect(c, Offset(5.5f * u, 5f * u), Size(2.5f * u, 14f * u))
        }
        Glyph.SHUFFLE -> {
            drawPath(p(3f, 7f, 7f, 7f, 17f, 17f, 21f, 17f), c, style = line)
            drawPath(p(3f, 17f, 7f, 17f, 17f, 7f, 21f, 7f), c, style = line)
            drawPath(p(18f, 4f, 21f, 7f, 18f, 10f), c, style = line)
            drawPath(p(18f, 14f, 21f, 17f, 18f, 20f), c, style = line)
        }
        Glyph.REPEAT, Glyph.REPEAT_ONE -> {
            drawPath(p(4f, 11f, 4f, 7f, 20f, 7f), c, style = line)
            drawPath(p(17f, 4f, 20f, 7f, 17f, 10f), c, style = line)
            drawPath(p(20f, 13f, 20f, 17f, 4f, 17f), c, style = line)
            drawPath(p(7f, 14f, 4f, 17f, 7f, 20f), c, style = line)
            if (g == Glyph.REPEAT_ONE) drawPath(p(11f, 11f, 12.5f, 10f, 12.5f, 14f), c, style = Stroke(1.6f * u, cap = StrokeCap.Round))
        }
        Glyph.BACK -> drawPath(p(15f, 5f, 8f, 12f, 15f, 19f), c, style = line)
        Glyph.DOWN -> drawPath(p(6f, 9f, 12f, 15f, 18f, 9f), c, style = line)
        Glyph.CLOSE -> {
            drawPath(p(6f, 6f, 18f, 18f), c, style = line)
            drawPath(p(18f, 6f, 6f, 18f), c, style = line)
        }
        Glyph.SEARCH -> {
            drawCircle(c, 7f * u, Offset(11f * u, 11f * u), style = line)
            drawPath(p(16f, 16f, 20f, 20f), c, style = line)
        }
        Glyph.GEAR -> {
            drawCircle(c, 3f * u, Offset(12f * u, 12f * u), style = line)
            drawCircle(c, 7.5f * u, Offset(12f * u, 12f * u), style = line)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val x1 = 12f + 7.5f * Math.cos(a).toFloat()
                val y1 = 12f + 7.5f * Math.sin(a).toFloat()
                val x2 = 12f + 10f * Math.cos(a).toFloat()
                val y2 = 12f + 10f * Math.sin(a).toFloat()
                drawPath(p(x1, y1, x2, y2), c, style = Stroke(2.6f * u, cap = StrokeCap.Round))
            }
        }
        Glyph.NOTE -> {
            drawPath(p(9f, 18f, 9f, 5f, 20f, 3f, 20f, 16f), c, style = line)
            drawCircle(c, 3f * u, Offset(6.5f * u, 18f * u))
            drawCircle(c, 3f * u, Offset(17.5f * u, 16f * u))
        }
        Glyph.DISC -> {
            drawCircle(c, 9f * u, Offset(12f * u, 12f * u), style = Stroke(1.5f * u))
            drawCircle(c, 2.5f * u, Offset(12f * u, 12f * u), style = Stroke(1.5f * u))
        }
        Glyph.FOLDER -> drawPath(p(3f, 7f, 3f, 19f, 21f, 19f, 21f, 9f, 11f, 9f, 9f, 6f, 3f, 6f, close = true), c, style = line)
        Glyph.SAVE -> {
            drawPath(p(12f, 4f, 12f, 15f), c, style = line)
            drawPath(p(7f, 10f, 12f, 15f, 17f, 10f), c, style = line)
            drawPath(p(5f, 20f, 19f, 20f), c, style = line)
        }
        Glyph.HEART, Glyph.HEART_FULL -> {
            val heart = Path().apply {
                moveTo(12f * u, 20f * u)
                cubicTo(4f * u, 14.5f * u, 3f * u, 10f * u, 4.5f * u, 7f * u)
                cubicTo(6.5f * u, 3.5f * u, 10.5f * u, 4f * u, 12f * u, 7.5f * u)
                cubicTo(13.5f * u, 4f * u, 17.5f * u, 3.5f * u, 19.5f * u, 7f * u)
                cubicTo(21f * u, 10f * u, 20f * u, 14.5f * u, 12f * u, 20f * u)
                close()
            }
            if (g == Glyph.HEART_FULL) drawPath(heart, c) else drawPath(heart, c, style = line)
        }
        Glyph.PERSON -> {
            drawCircle(c, 4f * u, Offset(12f * u, 8f * u), style = line)
            drawPath(p(4f, 21f, 4f, 19f, 8f, 15f, 16f, 15f, 20f, 19f, 20f, 21f), c, style = line)
        }
    }
}
