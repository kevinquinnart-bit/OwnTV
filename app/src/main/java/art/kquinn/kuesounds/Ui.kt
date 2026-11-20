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

enum class Tab(val label: String) { ALBUMS("Albums"), ARTISTS("Artists"), SONGS("Songs"), FOLDERS("Folders") }

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

    @Composable
    private fun Mark(size: Dp) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(K.Accent), contentAlignment = Alignment.Center) {
            Sign(Glyph.NOTE, size * 0.6f, K.Page)
        }
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
            Words("KueSounds", 30, bold = true)
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
                Words(problem, 16, K.Accent, lines = 4, centre = true)
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
        val albums = remember(data) { Library.albums(data.tracks) }
        val artists = remember(albums) { Library.artists(albums) }
        val songs = remember(data) { Library.songs(data.tracks) }

        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when (val s = stack.last()) {
                    Screen.Home -> Home(albums, artists, songs, go)
                    is Screen.AlbumPage -> albums.firstOrNull { it.key == s.key }?.let { AlbumPage(it, back, go) } ?: Gone(back)
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
                    Words("KueSounds", 24, bold = true, modifier = Modifier.weight(1f))
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
                    query.isNotBlank() -> SearchResults(Library.search(query, Scanner.data.tracks, albums, artists), go)
                    tab == Tab.ALBUMS -> AlbumGrid(albums, go)
                    tab == Tab.ARTISTS -> ArtistList(artists, go)
                    tab == Tab.SONGS -> SongList(songs)
                    else -> FolderList(null, Scanner.data, go)
                }
            }
        }
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
                Words(if (updating.isNotEmpty()) updating else "KueSounds " + offer.name + " is ready.", 15, lines = 2, modifier = Modifier.weight(1f))
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
                                    Updates.install(context, file)
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
        val status = Scanner.status
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
                }
            }
            itemsIndexed(al.tracks, key = { _, t -> t.id }) { i, t ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    val len = if (t.durationMs > 0) Library.clock(t.durationMs) else ""
                    // The song's own artist shows only when it isn't the album's (a guest, or a compilation).
                    val sub = listOf(t.artist.takeIf { it != al.artist }.orEmpty(), len).filter { it.isNotEmpty() }.joinToString(" · ")
                    Row2(t.title, sub, null, highlight = t.id == Playing.currentId, lead = if (t.trackNo > 0) t.trackNo.toString() else (i + 1).toString()) {
                        Playing.play(al.tracks, i)
                    }
                }
            }
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
            Column {
                Words(Playing.title, 24, bold = true, lines = 2)
                Words(Playing.artist, 17, K.Soft)
                if (Playing.problem.isNotEmpty()) Words(Playing.problem, 15, K.Accent)
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

    @Composable
    private fun Settings(back: () -> Unit) {
        val data = Scanner.data
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Words(count(data.tracks.size, "song") + " · " + count(Library.albums(data.tracks).size, "album"), 17)
                val left = data.tracks.count { !it.tagged }
                if (left > 0) Words("Song details still to read: " + String.format("%,d", left), 14, K.Soft, lines = 2)
                Spacer(Modifier.height(8.dp))
                if (Scanner.running) {
                    Words(Scanner.status.ifBlank { "Looking through your music" }, 15, K.Accent, lines = 2)
                } else {
                    Button("Look for new music", modifier = Modifier.fillMaxWidth()) { Scanner.scan() }
                }
            }
            Panel {
                Words("KueSounds", 14, K.Soft)
                Words("Version " + BuildConfig.VERSION_NAME, 17)
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
enum class Glyph { PLAY, PAUSE, NEXT, PREVIOUS, SHUFFLE, REPEAT, REPEAT_ONE, BACK, DOWN, CLOSE, SEARCH, GEAR, NOTE, DISC, FOLDER, PERSON }

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
        Glyph.PERSON -> {
            drawCircle(c, 4f * u, Offset(12f * u, 8f * u), style = line)
            drawPath(p(4f, 21f, 4f, 19f, 8f, 15f, 16f, 15f, 20f, 19f, 20f, 21f), c, style = line)
        }
    }
}
