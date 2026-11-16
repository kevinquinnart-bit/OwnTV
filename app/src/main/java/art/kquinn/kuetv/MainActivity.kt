package art.kquinn.kuetv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Page = Color(0xFF0E1626)
private val Panel = Color(0xFF16213A)
private val Accent = Color(0xFFFFD23F)
private val Soft = Color(0xFFB9C2D3)

/** The app's lettering: Poppins throughout. Signs it doesn't have (★ ● ≡) come from the device's own. */
private val Poppins = FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_bold, FontWeight.Bold))
private val KueType = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Poppins), displayMedium = t.displayMedium.copy(fontFamily = Poppins), displaySmall = t.displaySmall.copy(fontFamily = Poppins),
        headlineLarge = t.headlineLarge.copy(fontFamily = Poppins), headlineMedium = t.headlineMedium.copy(fontFamily = Poppins), headlineSmall = t.headlineSmall.copy(fontFamily = Poppins),
        titleLarge = t.titleLarge.copy(fontFamily = Poppins), titleMedium = t.titleMedium.copy(fontFamily = Poppins), titleSmall = t.titleSmall.copy(fontFamily = Poppins),
        // Every line of text on the screens starts from this one and sets its own size, so the space
        // between lines is left to follow that size. A fixed one made large lines sit on top of each other.
        bodyLarge = t.bodyLarge.copy(fontFamily = Poppins, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
        bodyMedium = t.bodyMedium.copy(fontFamily = Poppins), bodySmall = t.bodySmall.copy(fontFamily = Poppins),
        labelLarge = t.labelLarge.copy(fontFamily = Poppins), labelMedium = t.labelMedium.copy(fontFamily = Poppins), labelSmall = t.labelSmall.copy(fontFamily = Poppins),
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Report.file == null) Report.keepIn(java.io.File(filesDir, "report.txt"))
        Report.note("App", "opened, version " + BuildConfig.VERSION_NAME)
        // A TV is always sideways. A phone turns with the way it is held.
        val tv = isTv(this)
        if (tv) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent), typography = KueType) {
                // The text size chosen in Settings makes every line of lettering bigger, on every screen.
                val scale = remember(Choices.changes) { Choices.textSizeScales[Choices.textSize(this)] }
                val density = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(
                    LocalPhone provides !tv,
                    LocalTextScale provides scale,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * scale),
                ) { App() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Resumes.count++
    }

    override fun onStart() {
        super.onStart()
        Screen.visible = true
    }

    override fun onStop() {
        super.onStop()
        Screen.visible = false
    }
}

/** Goes up each time KueTV comes back to the front, for example back from another app or the app store. */
private object Resumes {
    var count by mutableIntStateOf(0)
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
    val browse = remember(library) { library?.let { BrowseState(saved = Place.read(context)) } }
    val favourites = remember { Favourites(context.applicationContext) }
    var driveSignIn by remember { mutableStateOf(false) }
    var addOnsOpen by remember { mutableStateOf(false) }
    // 1: sending this device's set-up to another. 2: copying another device's set-up to here.
    var setUpShare by remember { mutableIntStateOf(0) }
    var reportOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var healthOpen by remember { mutableStateOf(false) }
    // A film or episode that can play from an add-on or from the provider's own copy, waiting for the user to pick.
    var choice by remember { mutableStateOf<Playing?>(null) }
    // What the lists show: everything the provider sent, less the categories chosen to be hidden.
    val shownLibrary = remember(library, Choices.changes) {
        library?.without(Choices.hidden(context, "live"), Choices.hidden(context, "film"), Choices.hidden(context, "show"))
    }
    // Each time KueTV comes to the front: carry on copying any recordings that are waiting for Google Drive.
    LaunchedEffect(Resumes.count) { Recordings.wake(context) }
    // Anything a torrent left behind, if the app was closed while one played, is cleared when the app opens.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { Torrents.folder(context).deleteRecursively() } } }
    // Programmes set to record or to remind: make sure each still has its alarm.
    LaunchedEffect(Unit) { runCatching { TimedStore.arm(context); Reminders.arm(context) } }
    // While KueTV is on screen: every few seconds, is a reminded programme starting?
    LaunchedEffect(Screen.visible) {
        while (Screen.visible) {
            runCatching { Reminders.check(context) }
            delay(10_000)
        }
    }
    // A newer version downloaded last time is no longer needed once the app starts afresh.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Updates.tidy(context); ShiftBuffer.tidy(context) } }
    // The health check's last results, and once a day a fresh one, quietly, once the lists have loaded.
    LaunchedEffect(Unit) { Health.restore(context) }
    LaunchedEffect(library != null) {
        val lib = library ?: return@LaunchedEffect
        if (!Health.dueDaily(context) || Health.running) return@LaunchedEffect
        delay(20_000)
        withContext(Dispatchers.IO) { runCatching { Health.run(context, account, lib) } }
    }
    // Each time KueTV comes to the front: is a newer version on the download?
    LaunchedEffect(Resumes.count) { withContext(Dispatchers.IO) { Updates.checkOnOpen() } }

    LaunchedEffect(account, reload) {
        if (!account.isSet) return@LaunchedEffect
        library = null
        problem = null
        try {
            library = withContext(Dispatchers.IO) { Provider.load(account) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderProblem) {
            Report.note("Provider", "loading failed: " + e.message)
            problem = e.message
        } catch (e: Exception) {
            Report.note("Provider", "loading failed: " + e.javaClass.name + " · " + e.message)
            problem = "Something went wrong loading your channels. Try again."
        }
        library?.let { Report.note("Provider", "loaded: " + it.channels.size + " channels as " + it.groups.size + " rows, " + it.films.size + " films, " + it.shows.size + " series") }
    }
    // On a phone the screens keep clear of the notch and the bars; the player uses the whole screen.
    val reminder = Reminders.showing
    /** OK on a reminder: watch that channel, in place of whatever is showing. */
    val watchReminded: () -> Unit = {
        val r = Reminders.showing
        Reminders.showing = null
        val group = r?.let { library?.groupOf?.get(it.channelId) }
        if (group != null) {
            browse?.resumeKey = null
            LastLive.item = Playable(group.name, group.versions.last().channel.url)
            playing = Playing(listOf(livePlayable(group, library?.account, library?.serverAheadSec ?: 0)), 0, true)
        }
    }
    // A reminder that isn't answered goes after a minute and a half.
    LaunchedEffect(reminder) {
        if (reminder == null) return@LaunchedEffect
        delay(90_000)
        if (Reminders.showing == reminder) Reminders.showing = null
    }
    Box(
        Modifier.fillMaxSize().background(Page).then(if (playing == null) Modifier.safeDrawingPadding() else Modifier)
            // With a reminder up, OK watches it and Back puts it away, before anything underneath sees those keys.
            .onPreviewKeyEvent { e ->
                if (Reminders.showing == null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.type == KeyEventType.KeyUp) watchReminded(); true }
                    Key.Back -> { if (e.type == KeyEventType.KeyUp) Reminders.showing = null; true }
                    else -> false
                }
            },
    ) {
        val lib = shownLibrary
        val whole = library
        val now = playing
        when {
            // Only the player is on screen while it plays, so no key can reach the lists behind it.
            // A fresh player for each thing played, so "From the start" begins clean in place of the live channel.
            now != null -> androidx.compose.runtime.key(now) {
                PlayerScreen(
                    now.items, now.index, now.live,
                    onClose = { playing = null },
                    onFromStart = { programme -> playing = Playing(listOf(programme), 0, false) },
                )
            }
            choice != null -> {
                val c = choice!!
                CopyChoiceScreen(
                    c.items.getOrNull(c.index)?.name.orEmpty(),
                    onAddOn = { Choices.copyPick(context, 0); choice = null; playing = c },
                    // The provider's copy: the add-ons aren't asked at all.
                    onProvider = { Choices.copyPick(context, 1); choice = null; playing = Playing(c.items.map { it.copy(find = null) }, c.index, false) },
                    onCancel = { choice = null },
                )
            }
            setUpShare == 1 -> SendSetUpScreen(account, favourites, onDone = { setUpShare = 0 })
            setUpShare == 2 -> TakeSetUpScreen(
                onDone = { setUpShare = 0 },
                onTaken = { taken ->
                    // The other device's set-up replaces this one's, and the lists load again from its provider details.
                    AccountStore.save(context, taken.account)
                    favourites.replaceAll(taken.favourites)
                    AddOnStore.replaceAll(context, taken.addOns)
                    Choices.smallTv(context, taken.smallTv)
                    for (kind in listOf("live", "film", "show")) Choices.hidden(context, kind, taken.hidden[kind].orEmpty())
                    editing = false
                    if (taken.account == account) reload++ else account = taken.account
                },
            )
            !account.isSet || editing -> SetupScreen(
                current = account,
                canCancel = account.isSet,
                onCancel = { editing = false },
                onCopy = { setUpShare = 2 },
                onSave = { next ->
                    AccountStore.save(context, next)
                    editing = false
                    if (next == account) reload++ else account = next
                },
            )
            // A newer version found on opening. "Not now" puts it off until the app is next started.
            Updates.offer != null -> UpdateOfferScreen(Updates.offer!!, onLater = {
                Updates.putOff = Updates.offer?.code ?: 0
                Updates.offer = null
            })
            driveSignIn -> DriveSignInScreen(onDone = { driveSignIn = false })
            addOnsOpen -> AddOnsScreen(onDone = { addOnsOpen = false })
            reportOpen -> ReportScreen(onDone = { reportOpen = false })
            aboutOpen -> AboutScreen(onDone = { aboutOpen = false })
            healthOpen -> HealthScreen(account, library, onDone = { healthOpen = false })
            categoriesOpen && whole != null -> CategoriesScreen(whole, onDone = { categoriesOpen = false })
            problem != null -> ProblemScreen(problem.orEmpty(), onRetry = { reload++ }, onEdit = { editing = true })
            lib == null || browse == null -> LoadingScreen()
            else -> MainScreen(
                library = lib,
                state = browse,
                favourites = favourites,
                onPlay = { items, index, live, key ->
                    browse.resumeKey = key
                    // The small TV shown while browsing carries on with this channel, in its lightest version.
                    if (live) items.getOrNull(index)?.let { LastLive.item = Playable(it.name, it.streams.lastOrNull()?.url ?: it.url) }
                    val item = items.getOrNull(index)
                    // Both an add-on's stream and the provider's own copy: the user picks. Only one of them: it plays.
                    if (!live && item?.find != null && item.url.isNotEmpty()) choice = Playing(items, index, false)
                    else playing = Playing(items, index, live)
                },
                onEdit = { editing = true },
                onReload = { reload++ },
                onDriveSignIn = { driveSignIn = true },
                onAddOns = { addOnsOpen = true },
                onSendSetUp = { setUpShare = 1 },
                onTakeSetUp = { setUpShare = 2 },
                onReport = { reportOpen = true },
                onCategories = { categoriesOpen = true },
                onAbout = { aboutOpen = true },
                onHealth = { healthOpen = true },
            )
        }
        if (reminder != null) ReminderPopUp(reminder, onWatch = watchReminded, onClose = { Reminders.showing = null }, Modifier.align(Alignment.TopCenter))
    }
}

/** A reminded programme starting: over whatever is on screen, at the top. */
@Composable
private fun ReminderPopUp(reminder: Reminder, onWatch: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val phone = LocalPhone.current
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val started = System.currentTimeMillis() >= reminder.startMs
    Column(
        modifier.padding(top = if (phone) 12.dp else 28.dp, start = 12.dp, end = 12.dp).widthIn(max = 720.dp)
            .background(Color(0xF216213A), RoundedCornerShape(16.dp)).border(2.dp, Accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(
            (if (started) "Started at " else "Starting at ") + clock.format(java.util.Date(reminder.startMs)) + " on " + reminder.channel,
            color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        Text(reminder.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (phone) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Watch", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Accent, RoundedCornerShape(22.dp)).clickable(onClick = onWatch).padding(horizontal = 18.dp, vertical = 9.dp),
                )
                Text(
                    "Not now", color = Color.White, fontSize = 18.sp,
                    modifier = Modifier.background(Panel, RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        } else {
            Text("OK to watch  ·  Back to put this away", color = Soft, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * OK on a programme that hasn't started steps it along: a reminder, then set to record, then neither.
 * Says on screen what it is now set to.
 */
private fun stepLater(context: android.content.Context, plan: Timed, remind: Reminder, reminded: Boolean, recording: Boolean, at: String) {
    val say: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    when {
        recording -> {
            TimedStore.remove(context, plan)
            say("Nothing set for " + plan.title)
        }
        reminded -> {
            Reminders.remove(context, remind)
            TimedStore.add(context, plan)
            say("Set to record: " + plan.title + ", " + at + ". OK again for nothing.")
        }
        else -> {
            Reminders.add(context, remind)
            say("Reminder set: " + plan.title + ", " + at + ". OK again to record it instead.")
        }
    }
}

// --------------------------------------------------------------------------------------------- pieces

/** How a row looks on a phone, where nothing is focused: a plain row, the main button, or another button. */
private enum class Look { ROW, MAIN, OTHER }

/**
 * One row that takes focus and OK: yellow while focused, a pale bar while it is the chosen one.
 * On a phone a row that is a button is drawn as one, so it reads as something to tap.
 */
@Composable
private fun FocusRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chosen: Boolean = false,
    onFocus: () -> Unit = {},
    onHold: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    look: Look = Look.ROW,
    /** A second, smaller line under the first: what is on a channel, say. */
    below: String? = null,
    textSize: androidx.compose.ui.unit.TextUnit = 21.sp,
    /** The room above and below the words. The side menu uses less, so all of it fits on the screen. */
    padV: Dp = 12.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val button = if (LocalPhone.current) look else Look.ROW
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused || button == Look.MAIN -> Accent
                    chosen -> Color(0x33FFFFFF)
                    button == Look.OTHER -> Panel
                    else -> Color.Transparent
                },
            )
            .onFocusChanged {
                val now = it.isFocused
                if (now && !focused) onFocus()
                focused = now
            }
            .press(onClick, onHold)
            .padding(horizontal = 16.dp, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (button == Look.ROW) Arrangement.spacedBy(14.dp) else Arrangement.Center,
    ) {
        leading?.invoke()
        Column {
            Text(
                text,
                color = if (focused || button == Look.MAIN) Color.Black else Color.White,
                fontSize = textSize,
                fontWeight = if (focused || chosen || button == Look.MAIN) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (below != null) Text(below, color = if (focused) Color.Black else Soft, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Holding OK for half a second does [onHold] once, and the release that follows is swallowed so it
 * does not also count as a press.
 */
@Composable
private fun Modifier.holdOk(onHold: (() -> Unit)?): Modifier {
    if (onHold == null) return this
    val held = remember { booleanArrayOf(false) }
    return this.onPreviewKeyEvent { e ->
        if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
        val native = e.nativeKeyEvent
        when {
            e.type == KeyEventType.KeyDown && native.repeatCount == 0 -> { held[0] = false; false }
            e.type == KeyEventType.KeyDown && !held[0] && native.eventTime - native.downTime >= 500 -> { held[0] = true; onHold(); true }
            e.type == KeyEventType.KeyDown && held[0] -> true
            e.type == KeyEventType.KeyUp && held[0] -> { held[0] = false; true }
            else -> false
        }
    }
}

/** OK or a tap does [onClick]. Holding OK on a TV, or pressing and holding on a phone, does [onHold]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.press(onClick: () -> Unit, onHold: (() -> Unit)?): Modifier =
    if (LocalPhone.current && onHold != null) this.combinedClickable(onClick = onClick, onLongClick = onHold)
    else this.holdOk(onHold).clickable(onClick = onClick)

/** A pill to tap, for the phone's menu and category strips: yellow while it is the chosen one. */
@Composable
private fun Chip(text: String, chosen: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (chosen) Color.Black else Color.White,
        fontSize = 17.sp,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (chosen) Accent else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/**
 * Where a section's screen goes. On a TV it sits to the right of the menu, categories beside the
 * list. On a phone it sits under the menu strip, categories in a strip above the list.
 */
private class Pane(private val row: RowScope?, private val column: ColumnScope?) {
    /** Takes all the room that is left. */
    fun fill(): Modifier = when {
        row != null -> with(row) { Modifier.weight(1f).fillMaxHeight() }
        column != null -> with(column) { Modifier.weight(1f).fillMaxWidth() }
        else -> Modifier
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
        Text(
            "Loading your channels and films…", color = Color.White, fontSize = 24.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun ProblemScreen(message: String, onRetry: () -> Unit, onEdit: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = if (LocalPhone.current) 12.dp else 220.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(start = 16.dp, bottom = 26.dp))
        FocusRow("Try again", onRetry, Modifier.focusRequester(first), look = Look.MAIN)
        Spacer(Modifier.height(8.dp))
        FocusRow("Change provider details", onEdit, look = Look.OTHER)
    }
}

// --------------------------------------------------------------------------------------------- setup

@Composable
private fun SetupScreen(current: Account, canCancel: Boolean, onCancel: () -> Unit, onCopy: () -> Unit, onSave: (Account) -> Unit) {
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
    val phone = LocalPhone.current
    DisposableEffect(Unit) {
        // Typing from a phone is for the TV. On a phone the boxes are right here.
        val main = Handler(Looper.getMainLooper())
        val server = if (phone) null else PhoneSetup { s, u, p, l -> main.post { submit(s, u, p, l) } }
        phoneAddress = server?.start()
        onDispose { server?.stop() }
    }
    if (canCancel) BackHandler { onCancel() }

    val intro: @Composable () -> Unit = {
        Text("Set up KueTV", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (phone) 8.dp else 26.dp))
        val address = phoneAddress
        if (address != null) {
            Text("Easiest: on your phone, open", color = Soft, fontSize = 22.sp)
            Text(address, color = Accent, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
            Text("and type your provider details there. Your phone must be on the same Wi-Fi as this TV.", color = Soft, fontSize = 20.sp)
        } else {
            Text(if (phone) "Type your provider details in the boxes below." else "Type your provider details in the boxes on the right.", color = Soft, fontSize = 22.sp)
        }
        warning?.let { Text(it, color = Accent, fontSize = 20.sp, modifier = Modifier.padding(top = if (phone) 10.dp else 26.dp)) }
    }
    val form: @Composable () -> Unit = {
        // The keyboard's own button moves to the next box, and on the last one it saves.
        Field("Server address", server) { server = it }
        Field("Username", user) { user = it }
        Field("Password", pass) { pass = it }
        Field("Or one playlist link", playlist, onDone = { submit(server, user, pass, playlist) }) { playlist = it }
        Spacer(Modifier.height(6.dp))
        FocusRow("Save", { submit(server, user, pass, playlist) }, look = Look.MAIN)
        // Already set up on the TV or the phone: bring it all across instead of typing it again.
        FocusRow("Copy set-up from another device", onCopy, look = Look.OTHER)
        if (canCancel) FocusRow("Cancel", onCancel, look = Look.OTHER)
    }
    if (phone) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            intro()
            form()
        }
    } else {
        Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f)) { intro() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { form() }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 16.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
        textStyle = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

// --------------------------------------------------------------------------------------------- main

private enum class Section(val label: String) { HOME("Home"), SEARCH("Search"), LIVE("Live TV"), GUIDE("TV Guide"), CATCHUP("Catch-up"), FILMS("Films"), SERIES("Series"), RECORDINGS("Recordings"), APPS("TV apps"), SETTINGS("Settings") }

private const val ALL = "\u0000all"
private const val FAVOURITES = "\u0000favourites"

// "All" and "Favourites" are known inside the app by marks that can't be written to the device's
// storage as they are, so they are kept under these plain words instead.
private fun keptAs(category: String) = when (category) { ALL -> "*all"; FAVOURITES -> "*favourites"; else -> category }
private fun fromKept(kept: String?) = when (kept) { null, "*all" -> ALL; "*favourites" -> FAVOURITES; else -> kept }
@Composable
private fun holdHint() = if (LocalPhone.current) " · press and hold for favourite" else " · hold OK for favourite"

/** Where the browsing screen is: the section, the category in each, how far each list is scrolled. */
private class BrowseState(saved: Map<String, String> = emptyMap()) {
    // The app opens on Home. Each section still opens on the category it was last left on.
    var section by mutableStateOf(Section.HOME)

    /** The section last opened from Home; going back to Home lands on its tile. */
    var cameFrom by mutableStateOf(Section.LIVE)
    var liveCategory by mutableStateOf(fromKept(saved["live"]))
    var filmCategory by mutableStateOf(fromKept(saved["film"]))
    val liveCategoryList = LazyListState()
    val filmCategoryList = LazyListState()
    val channelList = LazyListState()
    val filmGrid = LazyGridState()

    val guide = ChannelPick()
    val catchUp = ChannelPick()

    var query by mutableStateOf("")
    val searchList = LazyListState()

    var showCategory by mutableStateOf(fromKept(saved["show"]))
    val showCategoryList = LazyListState()
    val showGrid = LazyGridState()
    var openShow by mutableStateOf<Show?>(null)

    /** The film and the series the remote is on, for the lines above the posters saying what they are about. */
    var onFilm by mutableStateOf<Film?>(null)
    var onShow by mutableStateOf<Show?>(null)
    val filmAbout = HashMap<String, About>()
    val episodeList = LazyListState()
    var showJustClosed by mutableStateOf(false)
    val loadedEpisodes = HashMap<String, List<Episode>>()

    /** The row or poster that was sent to the player; focus returns to it. */
    var resumeKey by mutableStateOf<String?>(null)

    /** The channel the remote is on in Live TV, for the picture and programme details beside the list. */
    var liveOn by mutableStateOf<ChannelGroup?>(null)
}

/** Which of the TV's side columns the remote is in: the menu, the categories, or the list beside them. */
private enum class Zone { MENU, CATEGORIES, CONTENT }

/**
 * The TV's two side columns fold to a thin edge when the remote moves right, past them, and open again
 * when it moves left onto that edge. The menu is open only while the remote is in it. The categories are
 * open while the remote is in them or in the menu, and fold when it moves right into the list.
 */
private class Panels {
    var zone by mutableStateOf(Zone.MENU)
    var menuHas by mutableStateOf(false)
    var catsHas by mutableStateOf(false)
    var anyHas by mutableStateOf(false)

    /** Set when the remote lands on a folded edge: once the column has opened, the remote goes onto its chosen row. */
    var menuWanted by mutableStateOf(false)
    var catsWanted by mutableStateOf(false)
}

/** The side columns on a TV; null on a phone, where nothing folds. */
private val LocalPanels = staticCompositionLocalOf<Panels?> { null }

/** A folded side column: a thin edge. Moving onto it with the remote opens the column. */
@Composable
private fun PanelEdge(onLanded: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.width(18.dp).fillMaxHeight()
            .background(if (focused) Accent else Panel, RoundedCornerShape(10.dp))
            .onFocusChanged {
                if (it.isFocused && !focused) onLanded()
                focused = it.isFocused
            }
            .focusable(),
    )
}

/** The KueTV mark and name. */
@Composable
private fun KueLogo(markSize: Dp, textSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Drawn by the device itself from the same picture as the app's icon.
        val context = LocalContext.current
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { markSize.roundToPx() }.coerceAtLeast(1)
        val mark = remember(px) {
            val bitmap = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
            context.getDrawable(R.drawable.kuetv_mark)?.let { d ->
                d.setBounds(0, 0, px, px)
                d.draw(android.graphics.Canvas(bitmap))
            }
            bitmap.asImageBitmap()
        }
        Image(bitmap = mark, contentDescription = null, modifier = Modifier.size(markSize))
        Text("KueTV", color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
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
    favourites: Favourites,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
    onEdit: () -> Unit,
    onReload: () -> Unit,
    onDriveSignIn: () -> Unit,
    onAddOns: () -> Unit,
    onSendSetUp: () -> Unit,
    onTakeSetUp: () -> Unit,
    onReport: () -> Unit,
    onCategories: () -> Unit,
    onAbout: () -> Unit,
    onHealth: () -> Unit,
) {
    val context = LocalContext.current
    // The category in each section, kept for the next time the app opens.
    LaunchedEffect(state.liveCategory, state.filmCategory, state.showCategory) {
        Place.keep(context, mapOf("live" to keptAs(state.liveCategory), "film" to keptAs(state.filmCategory), "show" to keptAs(state.showCategory)))
    }
    // A category that has just been hidden can't stay the one being shown.
    LaunchedEffect(library) {
        if (state.liveCategory != ALL && state.liveCategory != FAVOURITES && library.liveCategories.none { it.id == state.liveCategory }) state.liveCategory = ALL
        if (state.filmCategory != ALL && state.filmCategory != FAVOURITES && library.filmCategories.none { it.id == state.filmCategory }) state.filmCategory = ALL
        if (state.showCategory != ALL && state.showCategory != FAVOURITES && library.showCategories.none { it.id == state.showCategory }) state.showCategory = ALL
        for (pick in listOf(state.guide, state.catchUp)) {
            if (pick.category != ALL && pick.category != FAVOURITES && library.liveCategories.none { it.id == pick.category }) pick.category = ALL
        }
    }
    // With add-ons added, a film or episode asks them for streams first. With none, nothing changes.
    val addOns = remember(AddOnStore.changes) { AddOnStore.all(context) }
    val subtitleLang = remember(Choices.changes) { Langs.of(Choices.subtitleLang(context)) }
    val finder: (String, String, Int?, Int?) -> (suspend () -> Search)? = remember(addOns, subtitleLang) {
        { type, name, season, episode -> streamFinder(addOns, type, name, season, episode, subtitleLang) }
    }
    var section by state::section
    val liveCategory = state.liveCategory
    val filmCategory = state.filmCategory
    val login = library.account != null
    val favs = favourites.keys
    val resumeKey = state.resumeKey
    val scope = rememberCoroutineScope()
    val menuFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    val panels = remember { Panels() }
    val resume = remember { FocusRequester() }
    // The section shown before this one; null until the screen has been shown once.
    val before = remember { arrayOfNulls<Section>(1) }
    val shownOnce = remember { booleanArrayOf(false) }
    LaunchedEffect(section) {
        withFrameNanos { }
        withFrameNanos { }
        val first = !shownOnce[0]
        shownOnce[0] = true
        val previous = before[0]
        before[0] = section
        // Back from the player: onto what was playing. Otherwise (or if that row has gone): Home's tile,
        // or the menu when a section has just been opened from Home.
        if (first && state.resumeKey != null && runCatching { resume.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        when {
            section == Section.HOME -> runCatching { homeFocus.requestFocus() }
            first || previous == Section.HOME -> {
                // The menu may be folded: open it, let it be drawn, then put the remote on it.
                panels.zone = Zone.MENU
                withFrameNanos { }
                withFrameNanos { }
                runCatching { menuFocus.requestFocus() }
            }
        }
    }
    // Which column the remote is in, worked out once each move has settled. While a folded edge
    // opens, nothing has focus for a moment; that leaves the column as it was.
    LaunchedEffect(panels.menuHas, panels.catsHas, panels.anyHas) {
        withFrameNanos { }
        panels.zone = when {
            panels.menuHas -> Zone.MENU
            panels.catsHas -> Zone.CATEGORIES
            panels.anyHas -> Zone.CONTENT
            else -> panels.zone
        }
    }
    // Landed on the menu's folded edge: once it has opened, onto the section being shown.
    LaunchedEffect(panels.menuWanted) {
        if (!panels.menuWanted) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { menuFocus.requestFocus() }
        panels.menuWanted = false
    }
    // Back from any section goes to Home. A page open inside a section (a channel's guide, a series) closes first.
    BackHandler(enabled = section != Section.HOME) {
        state.cameFrom = section
        section = Section.HOME
    }
    // A film or episode from "Carry on watching": a film is found again in the lists, so its add-ons are asked as before.
    val carryOn: (Positions.Watching) -> Unit = { w ->
        val film = library.films.firstOrNull { it.url == w.url }
        val item = if (film != null) Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster) else Playable(w.name, w.url, poster = w.poster)
        onPlay(listOf(item), 0, false, "carry:" + w.url)
    }
    val openFromHome: (Section) -> Unit = { s ->
        state.cameFrom = s
        section = s
    }

    val phone = LocalPhone.current
    // The chosen section's screen. Where it sits is decided underneath, by TV or phone.
    val body: @Composable Pane.() -> Unit = {
        when (section) {
            Section.HOME -> HomeScreen(library, state, favourites, homeFocus, resume, onPlay, openFromHome, carryOn, fill())
            Section.LIVE -> {
                Categories(library.liveCategories, liveCategory, state.liveCategoryList) {
                    if (it != state.liveCategory) {
                        state.liveCategory = it
                        scope.launch { state.channelList.scrollToItem(0) }
                    }
                }
                // One row for each channel: its 4K, FHD, HD and SD versions are kept together behind it.
                val shown = remember(library, liveCategory, if (liveCategory == FAVOURITES) favs else null) {
                    when (liveCategory) {
                        ALL -> library.groups
                        FAVOURITES -> library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }
                        else -> library.groups.filter { g -> g.versions.any { it.channel.categoryId == liveCategory } }
                    }
                }
                val playables = remember(shown) { shown.map { livePlayable(it, library.account, library.serverAheadSec) } }
                val list: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        Heading((if (shown.size == 1) "1 channel" else "${shown.size} channels") + holdHint())
                        LazyColumn(Modifier.fillMaxSize().focusRestorer(), state = state.channelList) {
                            itemsIndexed(shown, key = { _, c -> c.id }) { i, channel ->
                                val key = "live:" + channel.id
                                val favKeys = channel.versions.map { Favourites.key(it.channel, login) }
                                FocusRow(
                                    (if (favKeys.any { it in favs }) "★ " else "") + channel.name,
                                    { onPlay(playables, i, true, key) },
                                    if (key == resumeKey) Modifier.focusRequester(resume) else Modifier,
                                    onFocus = { state.liveOn = channel },
                                    onHold = { favourites.toggleAll(favKeys) },
                                    leading = { Logo(channel.logo) },
                                    below = nowLine(library.account, channel.versions[channel.start].channel.id),
                                )
                            }
                        }
                    }
                }
                if (phone) {
                    list(fill())
                } else {
                    // TV: the list takes the left half; the channel the remote is on plays top right, what is on it below.
                    Row(fill(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        list(Modifier.weight(1f).fillMaxHeight())
                        LivePreview(state.liveOn, library.account, Modifier.weight(1f).fillMaxHeight())
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
                val shown = remember(library, filmCategory, if (filmCategory == FAVOURITES) favs else null) {
                    when (filmCategory) {
                        ALL -> library.films
                        FAVOURITES -> library.films.filter { Favourites.key(it, login) in favs }
                        else -> library.films.filter { it.categoryId == filmCategory }
                    }
                }
                Column(fill()) {
                    Heading((if (shown.size == 1) "1 film" else "${shown.size} films") + holdHint())
                    val onFilm = state.onFilm
                    val account = library.account
                    if (!phone) {
                        // What the film the remote is on is about, asked for once it has been stayed on for a moment.
                        val about by produceState(onFilm?.let { state.filmAbout[it.id] }, onFilm?.id) {
                            if (value != null || onFilm == null || account == null) return@produceState
                            delay(600)
                            value = withContext(Dispatchers.IO) { runCatching { Provider.filmAbout(account, onFilm.id) }.getOrDefault(About()) }.also { state.filmAbout[onFilm.id] = it }
                        }
                        AboutStrip(onFilm?.name, about)
                    }
                    LazyVerticalGrid(
                        columns = if (LocalPhone.current) GridCells.Adaptive(104.dp) else GridCells.Fixed(4),
                        state = state.filmGrid,
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(shown, key = { _, f -> f.id }) { _, film ->
                            val key = "film:" + film.id
                            val favKey = Favourites.key(film, login)
                            Poster(
                                film.name, film.poster,
                                { onPlay(listOf(Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster)), 0, false, key) },
                                if (key == resumeKey) Modifier.focusRequester(resume) else Modifier,
                                favourite = favKey in favs,
                                onHold = { favourites.toggle(favKey) },
                                onFocus = { state.onFilm = film },
                            )
                        }
                    }
                }
            }
            Section.SERIES -> SeriesPane(library, state, favourites, resume, finder, onPlay)
            Section.GUIDE -> ChannelGuide(library, false, state.guide, state, favs, resume, onPlay)
            Section.CATCHUP -> ChannelGuide(library, true, state.catchUp, state, favs, resume, onPlay)
            Section.SEARCH -> SearchPane(library, state, resume, finder, onPlay) { show ->
                state.openShow = show
                section = Section.SERIES
            }
            Section.RECORDINGS -> RecordingsPane(state, resume, onPlay)
            Section.APPS -> TvAppsPane()
            Section.SETTINGS -> Column(
                // The rows run past the bottom of a TV screen, so the list scrolls as the remote moves down it.
                (if (phone) fill() else Modifier.width(560.dp * LocalTextScale.current).fillMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = if (phone) Arrangement.spacedBy(8.dp) else Arrangement.Top,
            ) {
                Heading("Settings")
                FocusRow("Change provider details", onEdit, look = Look.OTHER)
                FocusRow("Reload channels and films", onReload, look = Look.OTHER)
                val drive = remember(Recordings.changes) { Recordings.drive(context) }
                when {
                    !drive.isSetUp -> Note("Google Drive isn't set up in this copy of the app.")
                    drive.signedIn -> FocusRow(
                        "Sign out of Google Drive" + if (drive.email.isBlank()) "" else " (" + drive.email + ")",
                        { drive.signOut(); Recordings.changed() },
                        look = Look.OTHER,
                    )
                    else -> FocusRow("Sign in to Google Drive, for recordings", onDriveSignIn, look = Look.OTHER)
                }
                FocusRow("Add-ons for films and series (" + addOns.size + ")", onAddOns, look = Look.OTHER)
                val hiddenCount = remember(Choices.changes) { listOf("live", "film", "show").sumOf { Choices.hidden(context, it).size } }
                FocusRow("Categories to show" + if (hiddenCount == 0) "" else " ($hiddenCount hidden)", onCategories, look = Look.OTHER)
                val textSize = remember(Choices.changes) { Choices.textSize(context) }
                FocusRow("Text size: " + Choices.textSizeNames[textSize], { Choices.textSize(context, (textSize + 1) % 3) }, look = Look.OTHER)
                val shiftOn = remember(Choices.changes) { Choices.timeShift(context) }
                FocusRow("Pause and rewind live TV: " + if (shiftOn) "on" else "off", { Choices.timeShift(context, !shiftOn) }, look = Look.OTHER)
                val subtitlesOn = remember(Choices.changes) { Choices.subtitles(context) }
                FocusRow("Subtitles: " + if (subtitlesOn) "on" else "off", { Choices.subtitles(context, !subtitlesOn) }, look = Look.OTHER)
                FocusRow("Subtitle language: " + subtitleLang.name, { Choices.subtitleLang(context, Langs.after(subtitleLang).code) }, look = Look.OTHER)
                FocusRow("Send this set-up to another device", onSendSetUp, look = Look.OTHER)
                FocusRow("Copy set-up from another device", onTakeSetUp, look = Look.OTHER)
                if (!phone) {
                    val smallTv = remember(Choices.changes) { Choices.smallTv(context) }
                    FocusRow("Small TV while browsing: " + if (smallTv) "on" else "off", { Choices.smallTv(context, !smallTv) })
                }
                FocusRow("Reset text size, subtitles and small TV", { Choices.resetDisplay(context) }, look = Look.OTHER)
                val notWorking = Health.results.count { it.state == Check.DEAD }
                FocusRow("Health check" + if (notWorking == 0) "" else " · $notWorking not working", onHealth, look = Look.OTHER)
                FocusRow("Report for Claude", onReport, look = Look.OTHER)
                FocusRow("About and updates · version " + BuildConfig.VERSION_NAME, onAbout, look = Look.OTHER)
            }
        }
    }
    if (phone) {
        // Phone: the menu is a strip across the top that slides sideways; the section fills the rest.
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Home has its own tiles for the sections, so the strip is left off it.
            if (section != Section.HOME) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Section.entries.forEach { s -> Chip(s.label, s == section) { section = s } }
                }
            }
            Pane(null, this).body()
        }
    } else if (section == Section.HOME) {
        // TV: Home fills the screen, with no menu down the side.
        Pane(null, null).body()
    } else {
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPanels provides panels) {
        Row(
            Modifier.fillMaxSize().padding(24.dp).onFocusChanged { panels.anyHas = it.hasFocus },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The menu: open while the remote is in it, otherwise folded to an edge.
            Box(Modifier.fillMaxHeight().onFocusChanged { panels.menuHas = it.hasFocus }) {
                if (panels.zone == Zone.MENU) {
                    // Every entry fits on the screen: the entries share the height, so the menu never scrolls.
                    Column(
                        Modifier.width(190.dp * LocalTextScale.current).fillMaxHeight().background(Panel, RoundedCornerShape(18.dp)).padding(vertical = 14.dp, horizontal = 8.dp)
                            .focusRestorer().focusGroup(),
                    ) {
                        KueLogo(30.dp, 20.sp, Modifier.padding(start = 10.dp, bottom = 6.dp))
                        Section.entries.forEach { s ->
                            FocusRow(
                                s.label, { section = s },
                                Modifier.weight(1f).then(if (s == section) Modifier.focusRequester(menuFocus) else Modifier),
                                chosen = s == section, textSize = 18.sp, padV = 2.dp,
                            )
                        }
                    }
                } else {
                    PanelEdge { panels.menuWanted = true; panels.zone = Zone.MENU }
                }
            }
            Pane(this, null).body()
        }
        }
        // The small TV: the channel last watched carries on in the bottom right corner while browsing.
        // Live TV has its own, bigger picture beside the list, so it is left off there.
        val small = LastLive.item
        val wanted = remember(Choices.changes) { Choices.smallTv(context) }
        if (small != null && wanted && section != Section.LIVE) MiniTv(small, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        }
    }
}

/**
 * The category column: "All", then Favourites, then the provider's own in its order. Moving onto one
 * shows it. Coming into the column always lands on the one being shown, so arriving never changes it.
 */
@Composable
private fun Categories(categories: List<Category>, chosen: String, list: LazyListState, onChoose: (String) -> Unit) {
    if (LocalPhone.current) {
        // Phone: a strip of categories that slides sideways. A tap shows that category.
        LazyRow(Modifier.fillMaxWidth(), state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = ALL) { Chip("All", chosen == ALL) { onChoose(ALL) } }
            item(key = FAVOURITES) { Chip("★ Favourites", chosen == FAVOURITES) { onChoose(FAVOURITES) } }
            itemsIndexed(categories, key = { _, c -> c.id }) { _, c -> Chip(c.name, chosen == c.id) { onChoose(c.id) } }
        }
        return
    }
    val chosenFocus = remember { FocusRequester() }
    val panels = LocalPanels.current
    // Landed on the folded edge: once the column has opened, onto the category being shown.
    LaunchedEffect(panels?.catsWanted) {
        if (panels == null || !panels.catsWanted) return@LaunchedEffect
        withFrameNanos { }
        val at = when (chosen) { ALL -> 0; FAVOURITES -> 1; else -> 2 + categories.indexOfFirst { it.id == chosen }.coerceAtLeast(0) }
        if (list.layoutInfo.visibleItemsInfo.none { it.index == at }) list.scrollToItem(at)
        withFrameNanos { }
        withFrameNanos { }
        runCatching { chosenFocus.requestFocus() }
        panels.catsWanted = false
    }
    if (panels != null && panels.zone == Zone.CONTENT) {
        Box(Modifier.fillMaxHeight().onFocusChanged { panels.catsHas = it.hasFocus }) {
            PanelEdge { panels.catsWanted = true; panels.zone = Zone.CATEGORIES }
        }
        return
    }
    val row: @Composable (String, String) -> Unit = { id, name ->
        FocusRow(
            name, { onChoose(id) },
            if (chosen == id) Modifier.focusRequester(chosenFocus) else Modifier,
            chosen = chosen == id, onFocus = { onChoose(id) },
        )
    }
    Column(
        Modifier.width(270.dp * LocalTextScale.current).fillMaxHeight().onFocusChanged { panels?.catsHas = it.hasFocus }
            .background(Panel, RoundedCornerShape(18.dp)).padding(vertical = 18.dp, horizontal = 8.dp),
    ) {
        Heading("Categories")
        LazyColumn(
            Modifier.fillMaxSize()
                .focusProperties { onEnter = { runCatching { chosenFocus.requestFocus() } } }
                .focusGroup(),
            state = list,
        ) {
            item(key = ALL) { row(ALL, "All") }
            item(key = FAVOURITES) { row(FAVOURITES, "★ Favourites") }
            itemsIndexed(categories, key = { _, c -> c.id }) { _, c -> row(c.id, c.name) }
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
private fun Poster(
    name: String,
    poster: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favourite: Boolean = false,
    onHold: (() -> Unit)? = null,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            }
            .press(onClick, onHold)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Panel)) {
            if (poster.isNotBlank()) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (favourite) {
                Text(
                    "★", color = Accent, fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xCC0E1626), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            name,
            color = if (focused) Accent else Color.White,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
            modifier = Modifier.padding(top = 6.dp),
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
private fun Pane.ChannelGuide(
    library: Library,
    catchUp: Boolean,
    pick: ChannelPick,
    state: BrowseState,
    favs: Set<String>,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val account = library.account
    if (account == null) {
        Column(fill()) { Note("The guide and catch-up need a login: server address, username and password. A playlist link on its own doesn't give them.") }
        return
    }
    // One row for each channel, as in Live TV. For catch-up: the channels with a version the provider keeps.
    val channels = remember(library, catchUp) { if (catchUp) library.groups.filter { g -> g.versions.any { it.channel.catchupDays > 0 } } else library.groups }
    if (channels.isEmpty()) {
        Column(fill()) { Note(if (catchUp) "Your provider marks no channel as having catch-up." else "Your provider listed no channels.") }
        return
    }
    val opened = pick.open
    if (opened != null) {
        // Watching from here plays the channel the same way Live TV does: best version first, moving down if it must.
        val watch = remember(opened.id) { library.groupOf[opened.id]?.let { livePlayable(it, account, library.serverAheadSec) } ?: Playable(opened.name, opened.url) }
        val rowKey = "pick:" + (library.groupOf[opened.id]?.id ?: opened.id)
        ProgrammePage(opened, watch, rowKey, catchUp, account, library.serverAheadSec, pick, state, resume, onPlay)
        return
    }
    val scope = rememberCoroutineScope()
    val categories = remember(library, channels) {
        val used = channels.flatMapTo(HashSet()) { g -> g.versions.map { it.channel.categoryId } }
        library.liveCategories.filter { it.id in used }
    }
    Categories(categories, pick.category, pick.categoryList) {
        if (it != pick.category) {
            pick.category = it
            scope.launch { pick.channelList.scrollToItem(0) }
        }
    }
    val shown = remember(channels, pick.category, if (pick.category == FAVOURITES) favs else null) {
        when (pick.category) {
            ALL -> channels
            FAVOURITES -> channels.filter { g -> g.versions.any { Favourites.key(it.channel, true) in favs } }
            else -> channels.filter { g -> g.versions.any { it.channel.categoryId == pick.category } }
        }
    }
    LaunchedEffect(Unit) {
        if (!pick.justClosed) return@LaunchedEffect
        pick.justClosed = false
        withFrameNanos { }
        withFrameNanos { }
        runCatching { resume.requestFocus() }
    }
    /** Opens a channel's own page: its programmes one under another. */
    val openPage: (ChannelGroup) -> Unit = { channel ->
        state.resumeKey = "pick:" + channel.id
        pick.pickDay = null
        scope.launch { pick.programmeList.scrollToItem(0) }
        // The version asked about: for catch-up the best one the provider keeps, else the one that plays first.
        val version = if (catchUp) channel.versions.first { it.channel.catchupDays > 0 } else channel.versions[channel.start]
        pick.open = version.channel.copy(name = channel.name)
    }
    if (!catchUp && !LocalPhone.current) {
        // On a TV the guide is a grid: channels down the side, the day running across.
        GuideGrid(library, account, shown, pick, state, favs, resume, onPlay, openPage)
        return
    }
    Column(fill()) {
        Heading(if (catchUp) "Pick a channel to catch up on" else "Pick a channel to see its guide")
        LazyColumn(Modifier.fillMaxSize().focusRestorer(), state = pick.channelList) {
            itemsIndexed(shown, key = { _, c -> c.id }) { _, channel ->
                val key = "pick:" + channel.id
                FocusRow(
                    (if (channel.versions.any { Favourites.key(it.channel, true) in favs }) "★ " else "") + channel.name,
                    { openPage(channel) },
                    if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                    leading = { Logo(channel.logo) },
                    below = if (catchUp) null else nowLine(account, channel.versions[channel.start].channel.id),
                )
            }
        }
    }
}

@Composable
private fun Pane.ProgrammePage(
    channel: Channel,
    watch: Playable,
    /** The channel's row in the list behind this page; closing the page lands back on it. */
    rowKey: String,
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
            null -> { state.resumeKey = rowKey; pick.justClosed = true; pick.open = null }
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

    val context = LocalContext.current
    val timed = remember(TimedStore.changes) { TimedStore.all(context) }
    val reminders = remember(Reminders.changes) { Reminders.all(context) }
    Column(fill()) {
        Heading(channel.name + if (catchUp) " · catch-up" else " · guide · OK on a later programme: ◆ reminder, then ● record, then neither")
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
                        FocusRow("Watch " + channel.name, { onPlay(listOf(watch), 0, true, rowKey) }, Modifier.focusRequester(first))
                    }
                    itemsIndexed(rows, key = { _, p -> p.startMs }) { i, programme ->
                        val key = "prog:" + programme.startMs
                        val onNow = programme.startMs <= now && programme.stopMs > now
                        val later = !catchUp && programme.startMs > System.currentTimeMillis()
                        val plan = Timed.make(channel.name, programme.title.ifBlank { "Programme" }, channel.url, programme.startMs, programme.stopMs)
                        val set = timed.any { it.id == plan.id }
                        val remind = Reminder.make(channel.id, channel.name, programme.title.ifBlank { "Programme" }, programme.startMs, programme.stopMs)
                        val reminded = reminders.any { it.id == remind.id }
                        ProgrammeRow(
                            whenText = (if (set) "● Will record · " else "") + (if (reminded) "◆ Reminder · " else "") + (if (onNow) "Now · " else day(programme.startMs) + " · ") + time(programme.startMs) + " to " + time(programme.stopMs),
                            title = programme.title.ifBlank { "No title" },
                            description = programme.description,
                            onClick = {
                                if (catchUp) {
                                    val url = Provider.catchupUrl(account, channel.id, programme, serverAheadSec)
                                    onPlay(listOf(Playable(channel.name + " · " + programme.title, url)), 0, false, key)
                                } else if (later) {
                                    // A programme that hasn't started: OK sets a reminder, again sets it to record, again sets neither.
                                    stepLater(context, plan, remind, reminded, set, day(programme.startMs) + " " + time(programme.startMs))
                                } else {
                                    onPlay(listOf(watch), 0, true, key)
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
private fun Pane.SeriesPane(
    library: Library,
    state: BrowseState,
    favourites: Favourites,
    resume: FocusRequester,
    finder: (String, String, Int?, Int?) -> (suspend () -> Search)?,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val favs = favourites.keys
    val account = library.account
    if (account == null || library.shows.isEmpty()) {
        Column(fill()) {
            Note(if (account == null) "Series need a login: server address, username and password. A playlist link on its own doesn't list them." else "Your provider listed no series.")
        }
        return
    }
    val opened = state.openShow
    if (opened != null) {
        EpisodePage(opened, account, state, resume, finder, onPlay)
        return
    }
    val scope = rememberCoroutineScope()
    Categories(library.showCategories, state.showCategory, state.showCategoryList) {
        if (it != state.showCategory) {
            state.showCategory = it
            scope.launch { state.showGrid.scrollToItem(0) }
        }
    }
    val shown = remember(library, state.showCategory, if (state.showCategory == FAVOURITES) favs else null) {
        when (state.showCategory) {
            ALL -> library.shows
            FAVOURITES -> library.shows.filter { Favourites.key(it) in favs }
            else -> library.shows.filter { it.categoryId == state.showCategory }
        }
    }
    LaunchedEffect(Unit) {
        if (!state.showJustClosed) return@LaunchedEffect
        state.showJustClosed = false
        withFrameNanos { }
        withFrameNanos { }
        runCatching { resume.requestFocus() }
    }
    Column(fill()) {
        Heading((if (shown.size == 1) "1 series" else "${shown.size} series") + holdHint())
        if (!LocalPhone.current) AboutStrip(state.onShow?.name, state.onShow?.about)
        LazyVerticalGrid(
            columns = if (LocalPhone.current) GridCells.Adaptive(104.dp) else GridCells.Fixed(4),
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
                    favourite = Favourites.key(show) in favs,
                    onHold = { favourites.toggle(Favourites.key(show)) },
                    onFocus = { state.onShow = show },
                )
            }
        }
    }
}

@Composable
private fun Pane.EpisodePage(
    show: Show,
    account: Account,
    state: BrowseState,
    resume: FocusRequester,
    finder: (String, String, Int?, Int?) -> (suspend () -> Search)?,
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

    Column(fill()) {
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
                val playables = remember(episodes, finder) {
                    episodes.map { Playable(show.name + " · S" + it.season + " E" + it.number, it.url, find = finder("series", show.name, it.season, it.number), poster = show.poster) }
                }
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

// --------------------------------------------------------------------------------------------- search

/**
 * Search: one box, and under it what matches in channels, films and series, in that order. OK on a
 * channel or film plays it; OK on a series opens its episodes.
 */
@Composable
private fun Pane.SearchPane(
    library: Library,
    state: BrowseState,
    resume: FocusRequester,
    finder: (String, String, Int?, Int?) -> (suspend () -> Search)?,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
    onOpenShow: (Show) -> Unit,
) {
    val found = remember(library, state.query) { Finder.search(library, state.query) }
    // One row for each channel found, as in Live TV, whichever of its versions matched.
    val channelsFound = remember(found) { found.channels.mapNotNull { library.groupOf[it.id] }.distinct() }
    Column(fill()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            label = { Text("Search channels, films and series", fontSize = 16.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        when {
            state.query.trim().length < 2 -> Note("Press OK on the box and type at least two letters.")
            found.isEmpty -> Note("Nothing matches \"" + state.query.trim() + "\".")
            else -> LazyColumn(Modifier.fillMaxSize(), state = state.searchList) {
                if (channelsFound.isNotEmpty()) item(key = "h:live") { Heading("Channels") }
                itemsIndexed(channelsFound, key = { _, c -> "live:" + c.id }) { _, channel ->
                    val key = "live:" + channel.id
                    FocusRow(
                        channel.name,
                        { onPlay(listOf(livePlayable(channel, library.account, library.serverAheadSec)), 0, true, key) },
                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                        leading = { Logo(channel.logo) },
                    )
                }
                if (found.films.isNotEmpty()) item(key = "h:film") { Box(Modifier.padding(top = 14.dp)) { Heading("Films") } }
                itemsIndexed(found.films, key = { _, f -> "film:" + f.id }) { _, film ->
                    val key = "film:" + film.id
                    FocusRow(
                        film.name,
                        { onPlay(listOf(Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster)), 0, false, key) },
                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                    )
                }
                if (found.shows.isNotEmpty()) item(key = "h:show") { Box(Modifier.padding(top = 14.dp)) { Heading("Series") } }
                itemsIndexed(found.shows, key = { _, s -> "show:" + s.id }) { _, show ->
                    FocusRow(show.name, { state.resumeKey = "show:" + show.id; onOpenShow(show) })
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- TV apps

/**
 * TV apps: the free UK and Irish players. Each row says whether the app is on this TV. OK opens it
 * if it is, or the TV's app store on a search for it if not. Back in the other app returns here.
 */
@Composable
private fun Pane.TvAppsPane() {
    val context = LocalContext.current
    // Looked at again each time KueTV comes back to the front, so an app just installed shows as installed.
    val found by produceState<List<TvAppsOnDevice.Found?>?>(null, Resumes.count) {
        value = withContext(Dispatchers.IO) { TvAppsOnDevice.find(context.applicationContext) }
    }
    Column(fill()) {
        Heading("TV apps · these play in their own apps")
        val apps = found
        if (apps == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().focusRestorer()) {
            itemsIndexed(TvAppList.all, key = { _, a -> a.title }) { i, app ->
                val here = apps.getOrNull(i)
                TvAppRow(
                    title = app.title,
                    line = if (here != null) "Installed. OK opens it." else "Not installed. OK finds it in the app store.",
                    icon = here?.icon,
                    onClick = {
                        val opened = if (here != null) TvAppsOnDevice.open(context, here.packageName) else TvAppsOnDevice.openStore(context, app.storeSearch)
                        if (!opened) android.widget.Toast.makeText(context, "Couldn't open the app or the app store.", android.widget.Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun TvAppRow(title: String, line: String, icon: android.graphics.Bitmap?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
            if (icon != null) {
                val picture = remember(icon) { icon.asImageBitmap() }
                Image(bitmap = picture, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text("▶", color = if (focused) Color.Black else Soft, fontSize = 24.sp)
            }
        }
        Column {
            Text(title, color = if (focused) Color.Black else Color.White, fontSize = 22.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = if (focused) Color.Black else Soft, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --------------------------------------------------------------------------------------------- recordings

private fun sizeText(bytes: Long): String =
    if (bytes >= 1024L * 1024 * 1024) String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    else (bytes / (1024 * 1024)).coerceAtLeast(1).toString() + " MB"

/**
 * Recordings: the ones still on this TV or phone first, then the ones in Google Drive. OK plays one.
 * Each row says where the recording is and, while it is being copied to Drive, how far it has got.
 */
@Composable
private fun Pane.RecordingsPane(
    state: BrowseState,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val changes = Recordings.changes
    val drive = remember(changes) { Recordings.drive(context) }
    val canCopy = drive.isSetUp && drive.signedIn
    val here = remember(changes, Resumes.count) { Recordings.here(context) }
    // null while loading; a failure carries the sentence to show.
    val inDrive by produceState<Result<List<Drive.Saved>>?>(null, changes, Resumes.count) {
        value = if (!canCopy) Result.success(emptyList()) else withContext(Dispatchers.IO) {
            try {
                Result.success(drive.list())
            } catch (e: DriveProblem) {
                Result.failure(e)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(DriveProblem("Couldn't list the recordings in Google Drive."))
            }
        }
    }
    val copying = Recordings.copying
    val saved = inDrive?.getOrNull().orEmpty()
    val title: (String) -> String = { it.removeSuffix(".ts") }
    // Deleting is a mode of its own, switched on by its own row, so a recording is never deleted by a slip.
    var deleting by remember { mutableStateOf(false) }
    if (deleting) BackHandler { deleting = false }
    val tell: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    val toCome = remember(TimedStore.changes, changes, Resumes.count) { TimedStore.all(context) }
    val whenText = remember { java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault()) }
    val endText = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    Column(fill()) {
        Heading("Recordings")
        when {
            !drive.isSetUp -> Note("Google Drive isn't set up in this copy of the app. Recordings stay here.")
            !drive.signedIn -> Note("Google Drive isn't signed in, so recordings stay here. Sign in under Settings.")
        }
        Recordings.problem?.let { Note(it) }
        inDrive?.exceptionOrNull()?.let { Note(it.message ?: "Couldn't list the recordings in Google Drive.") }
        if (here.isEmpty() && saved.isEmpty() && toCome.isEmpty() && inDrive != null) {
            Note(if (phone) "No recordings yet. While watching a live channel, tap the picture, then Record." else "No recordings yet. While watching a live channel, press ≡ on the remote to record it.")
        }
        if (deleting) Note((if (phone) "Tap a recording to delete it." else "OK on a recording deletes it.") + " Ones in Google Drive go to its bin, where Google keeps them for 30 days.")
        LazyColumn(Modifier.fillMaxSize().focusRestorer()) {
            if (here.isNotEmpty() || saved.isNotEmpty() || toCome.isNotEmpty()) item(key = "deleting") {
                FocusRow(if (deleting) "Finish deleting" else "Delete recordings", { deleting = !deleting }, look = Look.OTHER)
            }
            // Programmes set to record that haven't been recorded yet. They are set from the TV Guide.
            itemsIndexed(toCome, key = { _, t -> "timed:" + t.id }) { _, plan ->
                val going = plan.id in TimedService.running
                ProgrammeRow(
                    whenText = (if (going) "Recording now · until " + endText.format(java.util.Date(plan.stopMs)) else "Will record · " + whenText.format(java.util.Date(plan.startMs)) + " to " + endText.format(java.util.Date(plan.stopMs))) + " · " + plan.channel,
                    title = plan.title,
                    description = "",
                    onClick = {
                        when {
                            going -> tell("That one is being recorded now. It will be here when it finishes.")
                            deleting -> TimedStore.remove(context, plan)
                            else -> tell("That hasn't been recorded yet. To take it off, use Delete recordings.")
                        }
                    },
                )
            }
            itemsIndexed(here, key = { _, f -> "here:" + f.name }) { _, file ->
                val key = "rec:" + file.name
                val where = when {
                    file == Recordings.recordingNow || file in Recordings.timedNow -> "Recording now"
                    copying?.first == file.name -> "Copying to Google Drive · " + (copying?.second ?: 0) + "%"
                    canCopy -> "Waiting to copy to Google Drive"
                    else -> if (phone) "On this phone" else "On this TV"
                }
                ProgrammeRow(
                    whenText = where + " · " + sizeText(file.length()),
                    title = title(file.name),
                    description = "",
                    onClick = {
                        when {
                            !deleting -> onPlay(listOf(Playable(title(file.name), android.net.Uri.fromFile(file).toString())), 0, false, key)
                            file in Recordings.timedNow -> tell("That one is being recorded now.")
                            Recordings.copying?.first == file.name -> tell("That one is being copied to Google Drive. Delete it once it is there.")
                            else -> { file.delete(); Recordings.changed() }
                        }
                    },
                    modifier = if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                )
            }
            itemsIndexed(saved, key = { _, s -> "drive:" + s.id }) { _, recording ->
                val key = "rec:" + recording.id
                ProgrammeRow(
                    whenText = "In Google Drive · " + sizeText(recording.size),
                    title = title(recording.name),
                    description = "",
                    onClick = {
                        if (deleting) scope.launch {
                            val gone = withContext(Dispatchers.IO) { runCatching { drive.bin(recording.id) } }
                            if (gone.isFailure) tell((gone.exceptionOrNull() as? DriveProblem)?.message ?: "Couldn't delete that recording.")
                            Recordings.changed()
                        } else scope.launch {
                            val pass = withContext(Dispatchers.IO) { runCatching { drive.token() } }
                            val token = pass.getOrNull()
                            if (token != null) {
                                onPlay(listOf(Playable(title(recording.name), drive.playUrl(recording.id), headers = mapOf("Authorization" to "Bearer $token"))), 0, false, key)
                            } else {
                                val why = (pass.exceptionOrNull() as? DriveProblem)?.message ?: "Couldn't open Google Drive."
                                android.widget.Toast.makeText(context, why, android.widget.Toast.LENGTH_LONG).show()
                                Recordings.changed()
                            }
                        }
                    },
                    modifier = if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                )
            }
        }
    }
}

/**
 * Signing in to Google Drive. The screen shows a short code; the user opens google.com/device on a
 * phone or computer, types it, and picks the Google account the recordings should go to.
 */
@Composable
private fun DriveSignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val drive = remember { Recordings.drive(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf<Drive.Code?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    BackHandler { onDone() }

    LaunchedEffect(attempt) {
        code = null
        problem = null
        try {
            val asked = withContext(Dispatchers.IO) { drive.beginSignIn() }
            code = asked
            while (true) {
                delay((asked.intervalSec + 1) * 1000L)
                if (System.currentTimeMillis() > asked.expiresAtMs) throw DriveProblem("That code ran out of time. Start again.")
                // Google not answering one check is not the end of the sign-in: the code is still good, so keep
                // checking. It happens while KueTV sits behind the web page the code is being typed into.
                val finished = try {
                    withContext(Dispatchers.IO) { drive.checkSignIn(asked) }
                } catch (e: DriveProblem) {
                    if (!e.offline) throw e
                    false
                }
                if (finished) break
            }
            done = true
            Recordings.changed()
            Recordings.wake(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveProblem) {
            Report.note("Drive", "sign-in stopped: " + e.message)
            problem = e.message
        } catch (e: Exception) {
            Report.note("Drive", "sign-in stopped: " + e.javaClass.name + " · " + e.message)
            problem = "Something went wrong signing in. Try again."
        }
        if (done) Report.note("Drive", "signed in")
    }

    val first = remember { FocusRequester() }
    val shown = code
    LaunchedEffect(done, problem, shown != null) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 220.dp), verticalArrangement = Arrangement.Center) {
        val line: @Composable (String) -> Unit = { Text(it, color = Soft, fontSize = 22.sp, modifier = Modifier.padding(start = 16.dp)) }
        val big: @Composable (String) -> Unit = {
            Text(it, color = Accent, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 14.dp))
        }
        Text("Google Drive", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 22.dp))
        when {
            done -> {
                line("Signed in" + if (drive.email.isBlank()) "." else " as " + drive.email + ".")
                line("Recordings will be copied to the folder \"" + Drive.FOLDER + "\".")
                Spacer(Modifier.height(22.dp))
                FocusRow("Done", onDone, Modifier.focusRequester(first), look = Look.MAIN)
            }
            problem != null -> {
                Text(problem.orEmpty(), color = Color.White, fontSize = 26.sp, modifier = Modifier.padding(start = 16.dp, bottom = 26.dp))
                FocusRow("Try again", { attempt++ }, Modifier.focusRequester(first), look = Look.MAIN)
                Spacer(Modifier.height(8.dp))
                FocusRow("Back", onDone, look = Look.OTHER)
            }
            shown == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            else -> {
                line(if (phone) "The button below copies this code and opens this page:" else "On your phone or computer, open")
                big(shown.url.removePrefix("https://").removePrefix("www."))
                line(if (phone) "Paste the code there:" else "and type this code:")
                big(shown.userCode)
                line("Then choose the Google account your recordings should go to." + if (phone) " Come back here afterwards." else " This screen moves on by itself.")
                Spacer(Modifier.height(22.dp))
                if (phone) {
                    FocusRow("Copy the code and open the page", {
                        runCatching {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KueTV code", shown.userCode))
                        }
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(shown.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }, look = Look.MAIN)
                    Spacer(Modifier.height(8.dp))
                }
                FocusRow("Cancel", onDone, Modifier.focusRequester(first), look = Look.OTHER)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- add-ons

/** What a film or episode uses to ask the add-ons for streams; null when no add-on gives streams. */
private fun streamFinder(addOns: List<AddOn>, type: String, name: String, season: Int?, episode: Int?, lang: Lang, knownId: String? = null): (suspend () -> Search)? {
    if (addOns.none { it.streams }) return null
    return { withContext(Dispatchers.IO) { AddOns.streamsFor(addOns, type, name, season, episode, lang, knownId) } }
}

/** A film or episode that can play from two places: the user picks which. It opens on the one picked last time. */
@Composable
private fun CopyChoiceScreen(name: String, onAddOn: () -> Unit, onProvider: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    BackHandler { onCancel() }
    val last = remember { Choices.copyPick(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 220.dp, vertical = if (phone) 12.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
        Note("Which copy do you want to play?")
        Spacer(Modifier.height(8.dp))
        FocusRow("Add-on stream", onAddOn, if (last == 0) Modifier.focusRequester(first) else Modifier, look = if (last == 0) Look.MAIN else Look.OTHER)
        FocusRow("Your provider's copy", onProvider, if (last == 1) Modifier.focusRequester(first) else Modifier, look = if (last == 1) Look.MAIN else Look.OTHER)
        FocusRow("Cancel", onCancel, look = Look.OTHER)
        Note("The add-on stream tries the best one with subtitles first. If none of its streams play, it moves to your provider's copy by itself.")
    }
}

/**
 * Add-ons: the ones added, each with a row that removes it, and a box to add another by its link.
 * On a TV the link can be pasted on a phone instead: the TV shows an address to open on the same Wi-Fi.
 */
@Composable
private fun AddOnsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val addOns = remember(AddOnStore.changes) { AddOnStore.all(context) }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone() }

    val add: (String) -> Unit = { typed ->
        if (!busy) {
            busy = true
            said = null
            scope.launch {
                said = try {
                    val addOn = withContext(Dispatchers.IO) { AddOns.read(typed) }
                    AddOnStore.add(context, addOn)
                    link = ""
                    "Added " + addOn.name + "."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AddOnProblem) {
                    e.message
                } catch (e: Exception) {
                    "Something went wrong adding that. Try again."
                }
                busy = false
            }
        }
    }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val server = if (phone) null else PhoneSetup(onAddOn = { typed -> main.post { add(typed) } }) { _, _, _, _ -> }
        phoneAddress = server?.start()
        onDispose { server?.stop() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 220.dp, vertical = if (phone) 12.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add-ons", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Note("Add-ons find other places to play the films and series already in your lists. KueTV comes with none: you add your own by its link.")
        phoneAddress?.let { address ->
            Note("Easiest: on your phone, on the same Wi-Fi, open")
            Text(address, color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            Note("and paste the link there.")
        }
        Field("Add-on link", link, onDone = { add(link) }) { link = it }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!phone) runCatching { first.requestFocus() }
        }
        FocusRow(if (busy) "Adding…" else "Add", { add(link) }, Modifier.focusRequester(first), look = Look.MAIN)
        said?.let { Text(it, color = Accent, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp)) }
        if (addOns.isNotEmpty()) Box(Modifier.padding(top = 14.dp)) { Heading("Added") }
        addOns.forEach { addOn ->
            val gives = when {
                addOn.streams && addOn.subtitles -> "streams and subtitles"
                addOn.streams -> "streams"
                else -> "subtitles"
            }
            // How it did in the last health check, if it has been checked.
            val health = when (Health.stateOf(Health.addOnName(addOn))) {
                Check.WORKING -> "✓ working · "
                Check.DOUBT -> "! look at Health check · "
                Check.DEAD -> "✗ NOT WORKING · "
                else -> ""
            }
            FocusRow(health + "Remove " + addOn.name + " (" + gives + ")", { AddOnStore.remove(context, addOn); said = "Removed " + addOn.name + "." }, look = Look.OTHER)
        }
        FocusRow("Done", onDone, look = Look.OTHER)
    }
}

// --------------------------------------------------------------------------------------------- copying a set-up

/**
 * Sending this device's set-up: the screen shows a four-figure code, and while it is open the other
 * device, on the same Wi-Fi, can copy the provider details, favourites and add-ons by giving that code.
 */
@Composable
private fun SendSetUpScreen(account: Account, favourites: Favourites, onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val code = remember { (1000 + java.security.SecureRandom().nextInt(9000)).toString() }
    var taken by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    BackHandler { onDone() }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val app = context.applicationContext
        val sender = SetUpSender(
            code,
            { SetUp(account, favourites.keys, AddOnStore.all(app), Choices.smallTv(app), listOf("live", "film", "show").associateWith { Choices.hidden(app, it) }).pack() },
            { main.post { taken = true } },
        )
        ready = PhoneSetup.localAddress() != null && sender.start() != null
        onDispose { sender.stop() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 220.dp), verticalArrangement = Arrangement.Center) {
        Text("Send this set-up", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 18.dp))
        when {
            ready == false -> Note("This device isn't on a home network, so it can't send its set-up. Join the Wi-Fi and try again.")
            taken -> Note("Sent. The other device now has your provider details, favourites and add-ons.")
            else -> {
                Note("On the other device, on the same Wi-Fi, open KueTV and choose \"Copy set-up from another device\". Then type this code there:")
                Text(code, color = Accent, fontSize = 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp))
                Note("Keep this screen open until it says Sent.")
            }
        }
        Spacer(Modifier.height(22.dp))
        FocusRow(if (taken) "Done" else "Cancel", onDone, Modifier.focusRequester(first), look = if (taken) Look.MAIN else Look.OTHER)
    }
}

/** Copying another device's set-up to here: type the code its screen shows, and this device finds it on the Wi-Fi. */
@Composable
private fun TakeSetUpScreen(onDone: () -> Unit, onTaken: (SetUp) -> Unit) {
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone() }

    val copy: () -> Unit = {
        val typed = code.filter { it.isDigit() }
        if (!busy && typed.length != 4) said = "Type the four figures shown on the other device."
        else if (!busy) {
            busy = true
            said = "Looking for the other device…"
            scope.launch {
                // null: this device isn't on a network. Empty: nothing is sending. Otherwise the set-up, or null inside for a wrong code.
                val result = withContext(Dispatchers.IO) {
                    val mine = PhoneSetup.localAddress() ?: return@withContext null
                    val senders = SetUpTaker.find(mine)
                    if (senders.isEmpty()) emptyList() else listOf(senders.firstNotNullOfOrNull { SetUpTaker.take(it, typed) })
                }
                busy = false
                val taken = result?.firstOrNull()
                when {
                    result == null -> said = "This device isn't on a home network. Join the Wi-Fi and try again."
                    result.isEmpty() -> said = "Couldn't find the other device. Check it is on the same Wi-Fi and still showing its code."
                    taken == null -> said = "That code wasn't right. Check it and try again."
                    else -> { onTaken(taken); onDone() }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 220.dp), verticalArrangement = Arrangement.Center) {
        Text("Copy set-up from another device", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 18.dp))
        Note("On the device that is already set up, open KueTV › Settings › \"Send this set-up to another device\". It shows a code. Type it here.")
        Note("This replaces the provider details, favourites and add-ons on this device.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { typed -> code = typed.filter { it.isDigit() }.take(4) },
            label = { Text("Code", fontSize = 16.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { copy() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        FocusRow(if (busy) "Looking…" else "Copy", copy, look = Look.MAIN)
        said?.let { Text(it, color = Accent, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp)) }
        Spacer(Modifier.height(8.dp))
        FocusRow("Cancel", onDone, look = Look.OTHER)
    }
}

/**
 * A channel ready for the player: every version behind it, the one to start on, where to ask what is on,
 * and, when the provider keeps one of its versions for catch-up, how to play the programme on now from its start.
 */
private fun livePlayable(group: ChannelGroup, account: Account?, serverAheadSec: Int = 0): Playable {
    val first = group.versions[group.start].channel
    val kept = group.versions.firstOrNull { it.channel.catchupDays > 0 }?.channel
    return Playable(
        group.name, first.url, group.versions.map { Stream(it.label, it.channel.url) }, group.start,
        guide = if (account == null) null else ({ NowNext.get(account, first.id) }),
        fromStart = if (account == null || kept == null) null else ({ programme ->
            Playable(group.name + " · " + programme.title.ifBlank { "from the start" }, Provider.catchupUrl(account, kept.id, programme, serverAheadSec))
        }),
    )
}

/**
 * What is on a channel now and next, for the line under its name in a list; null until it is known, and
 * when the provider has no guide for it. A row has to stay on screen for half a second before it asks,
 * so rows that are only passing by while the list is scrolled don't.
 */
@Composable
private fun nowLine(account: Account?, channelId: String): String? {
    if (account == null) return null
    val line by produceState(NowNext.cached(channelId)?.let { NowNext.line(it, System.currentTimeMillis()) }, channelId) {
        if (value != null) return@produceState
        delay(500)
        value = try {
            NowNext.line(NowNext.get(account, channelId), System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    return line
}

// --------------------------------------------------------------------------------------------- the guide as a grid

private const val GRID_DP_PER_MIN = 6
private const val GRID_EARLIER_MIN = 120
private const val GRID_SPAN_MIN = 26 * 60

/**
 * The TV Guide on a TV: channels down the left, the day running across, from two hours ago to a day
 * ahead. Every row moves sideways together, so a column is one moment on every channel. OK on what is
 * on now watches it; on a later programme sets it to record (and again takes that off); on an earlier
 * one plays it from catch-up, where the provider keeps it. OK on a channel's name opens its own page.
 * The programme the remote is on is described underneath.
 */
@Composable
private fun Pane.GuideGrid(
    library: Library,
    account: Account,
    shown: List<ChannelGroup>,
    pick: ChannelPick,
    state: BrowseState,
    favs: Set<String>,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
    onOpen: (ChannelGroup) -> Unit,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scale = LocalTextScale.current
    val opened = remember { System.currentTimeMillis() }
    val start = remember { opened - opened % (30 * 60_000L) - GRID_EARLIER_MIN * 60_000L }
    val end = start + GRID_SPAN_MIN * 60_000L
    val total = (GRID_SPAN_MIN * GRID_DP_PER_MIN).dp
    val across: (Long) -> Dp = { ms -> (ms / 60_000f * GRID_DP_PER_MIN).dp }
    val strip = rememberScrollState()
    LaunchedEffect(Unit) {
        // Open on now, with a little of what came before showing.
        strip.scrollTo(with(density) { across(opened - start - 20 * 60_000L).roundToPx() })
    }
    val timed = remember(TimedStore.changes) { TimedStore.all(context) }
    val reminders = remember(Reminders.changes) { Reminders.all(context) }
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val time: (Long) -> String = { clock.format(java.util.Date(it)) }
    var about by remember { mutableStateOf<Pair<String, Programme>?>(null) }
    val nameWidth = 230.dp * scale
    val tell: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }

    Column(fill()) {
        Heading("OK: watch what is on now · on a later one, ◆ reminder, then ● record, then neither · catch up on an earlier one")
        Row {
            Spacer(Modifier.width(nameWidth))
            Box(Modifier.weight(1f).horizontalScroll(strip, enabled = false)) {
                Row(Modifier.width(total)) {
                    for (i in 0 until GRID_SPAN_MIN / 30) {
                        Text(time(start + i * 30 * 60_000L), color = Soft, fontSize = 15.sp, maxLines = 1, modifier = Modifier.width((30 * GRID_DP_PER_MIN).dp).padding(start = 8.dp))
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = pick.channelList) {
            itemsIndexed(shown, key = { _, c -> c.id }) { _, channel ->
                val first = channel.versions[channel.start].channel
                // null while it is being asked for. A row asks only after staying on screen for half a second.
                val guide by produceState(NowNext.cached(first.id), first.id) {
                    if (value != null) return@produceState
                    delay(500)
                    value = try {
                        NowNext.get(account, first.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                Row(Modifier.fillMaxWidth().height(62.dp * scale)) {
                    val nameKey = "pick:" + channel.id
                    GridCell(
                        (if (channel.versions.any { Favourites.key(it.channel, true) in favs }) "★ " else "") + channel.name, "",
                        nameWidth, false, { onOpen(channel) }, { about = null },
                        if (nameKey == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                    )
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(strip)) {
                        Row(Modifier.width(total).fillMaxHeight()) {
                            val listing = guide?.filter { it.stopMs > start && it.startMs < end }
                            if (listing == null || listing.isEmpty()) {
                                Text(
                                    if (listing == null) "…" else "No guide for this channel", color = Soft, fontSize = 16.sp,
                                    modifier = Modifier.padding(start = across(opened - start - 15 * 60_000L), top = 16.dp),
                                )
                            } else {
                                var at = start
                                for (programme in listing) {
                                    val from = maxOf(programme.startMs, at)
                                    val to = minOf(programme.stopMs, end)
                                    if (to <= from) continue
                                    if (from > at) Spacer(Modifier.width(across(from - at)))
                                    at = to
                                    val key = "cell:" + channel.id + ":" + programme.startMs
                                    val plan = Timed.make(channel.name, programme.title.ifBlank { "Programme" }, first.url, programme.startMs, programme.stopMs)
                                    val set = timed.any { it.id == plan.id }
                                    val remind = Reminder.make(first.id, channel.name, programme.title.ifBlank { "Programme" }, programme.startMs, programme.stopMs)
                                    val reminded = reminders.any { it.id == remind.id }
                                    GridCell(
                                        (if (set) "● " else "") + (if (reminded) "◆ " else "") + programme.title.ifBlank { "No title" }, time(programme.startMs),
                                        across(to - from), programme.startMs <= opened && programme.stopMs > opened,
                                        {
                                            val now = System.currentTimeMillis()
                                            val kept = channel.versions.firstOrNull { it.channel.catchupDays > 0 }?.channel
                                            when {
                                                programme.startMs <= now && programme.stopMs > now -> onPlay(listOf(livePlayable(channel, account, library.serverAheadSec)), 0, true, key)
                                                programme.startMs > now -> stepLater(context, plan, remind, reminded, set, time(programme.startMs))
                                                kept == null || programme.startMs < now - kept.catchupDays * DAY_MS -> tell("Your provider keeps no catch-up for this.")
                                                else -> onPlay(
                                                    listOf(Playable(channel.name + " · " + programme.title, Provider.catchupUrl(account, kept.id, programme, library.serverAheadSec))), 0, false, key,
                                                )
                                            }
                                        },
                                        { about = channel.name to programme },
                                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // What the remote is on, in full. Kept clear of the small TV in the corner.
        Column(Modifier.fillMaxWidth().height(104.dp * scale).padding(start = 16.dp, top = 8.dp, end = 410.dp)) {
            about?.let { (channel, programme) ->
                Text(
                    channel + " · " + time(programme.startMs) + " to " + time(programme.stopMs) + " · " + programme.title.ifBlank { "No title" },
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(programme.description, color = Soft, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One box in the guide's grid: a channel's name, or a programme as wide as it is long. */
@Composable
private fun GridCell(title: String, sub: String, width: Dp, onNow: Boolean, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Accent else if (onNow) Color(0xFF263A63) else Panel)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = if (focused) Color.Black else Color.White, fontSize = 17.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, color = if (focused) Color.Black else Soft, fontSize = 13.sp, maxLines = 1)
    }
}

// --------------------------------------------------------------------------------------------- the report

/**
 * The report: what the app did and what went wrong, to copy and send. A phone copies it with a button.
 * A TV shows an address to open on a phone on the same Wi-Fi, where the same report can be copied.
 */
@Composable
private fun ReportScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val text = remember {
        val drive = Recordings.drive(context)
        Report.text(
            listOf(
                "KueTV 2, version " + BuildConfig.VERSION_NAME,
                (if (phone) "Phone" else "TV") + ": " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE,
                "Add-ons: " + AddOnStore.all(context).size + " · Google Drive: " + (if (!drive.isSetUp) "no keys in this copy" else if (drive.signedIn) "signed in" else "not signed in") +
                    " · Set to record: " + TimedStore.all(context).size + " · Recordings here: " + Recordings.here(context).size,
            ) + runCatching { TvProbe.lines(context) }.getOrDefault(listOf("TV inputs: couldn't be asked")),
        )
    }
    var address by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    BackHandler { onDone() }
    DisposableEffect(Unit) {
        val server = if (phone) null else PageServer { PageServer.copyPage("KueTV report", text) }
        val ip = PhoneSetup.localAddress()
        address = if (server != null && ip != null && server.start() != null) "http://$ip:" + PageServer.PORT else null
        onDispose { server?.stop() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 120.dp, vertical = if (phone) 12.dp else 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Report for Claude", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Note("What the app did and what went wrong, most recent last. It holds no addresses, usernames or passwords.")
        if (phone) {
            FocusRow(if (copied) "Copied. Paste it into your chat with Claude." else "Copy the report", {
                runCatching {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KueTV report", text))
                    copied = true
                }
            }, look = Look.MAIN)
        } else if (address != null) {
            Note("To send it: on your phone, on the same Wi-Fi, open")
            Text(address.orEmpty(), color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            Note("and tap Copy there. Keep this screen open while you do.")
        } else {
            Note("This TV isn't on a home network, so the report can only be read here.")
        }
        FocusRow("Done", onDone, Modifier.focusRequester(first), look = Look.OTHER)
        // The last of it, to read here.
        Text(
            text.lines().takeLast(if (phone) 40 else 9).joinToString("\n"),
            color = Soft, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        )
    }
}

/**
 * Above the posters on a TV: the name of the film or series the remote is on, its year, kind, rating
 * and length, and what it is about. It keeps its height whether or not there is anything to say, so
 * the posters don't jump about.
 */
@Composable
private fun AboutStrip(name: String?, about: About?) {
    Column(Modifier.fillMaxWidth().height(96.dp * LocalTextScale.current).padding(horizontal = 16.dp)) {
        if (name != null) {
            Text(
                name + if (about == null || about.line.isBlank()) "" else "  ·  " + about.line,
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (about != null && about.plot.isNotBlank()) Text(about.plot, color = Soft, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --------------------------------------------------------------------------------------------- categories to show

/** One kind of thing with categories (live channels, films, series): its categories and how much is filed under each. */
private class Kind(val key: String, val title: String, val categories: List<Category>, val counts: Map<String, Int>)

/**
 * Choosing which of the provider's categories show. One list: Live TV's categories, then Films', then
 * Series'. OK on a category hides it or shows it again. A hidden category, and everything filed only
 * under it, leaves every list and the search. Nothing is deleted: showing it again brings it all back.
 */
@Composable
private fun CategoriesScreen(library: Library, onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    BackHandler { onDone() }
    val changes = Choices.changes
    val kinds = remember(library) {
        listOf(
            Kind("live", "Live TV", library.liveCategories, library.channels.groupingBy { it.categoryId }.eachCount()),
            Kind("film", "Films", library.filmCategories, library.films.groupingBy { it.categoryId }.eachCount()),
            Kind("show", "Series", library.showCategories, library.shows.groupingBy { it.categoryId }.eachCount()),
        ).filter { it.categories.isNotEmpty() }
    }
    val hidden = remember(changes) { kinds.associate { it.key to Choices.hidden(context, it.key) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 160.dp, vertical = if (phone) 12.dp else 30.dp)) {
        Text("Categories to show", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Note((if (phone) "Tap" else "OK on") + " a category to hide it or show it again. Hidden ones leave every list and the search. Nothing is deleted.")
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).focusRestorer(), verticalArrangement = if (phone) Arrangement.spacedBy(6.dp) else Arrangement.Top) {
            item(key = "done") { FocusRow("Done", onDone, Modifier.focusRequester(first), look = Look.MAIN) }
            kinds.forEach { kind ->
                val off = hidden[kind.key].orEmpty()
                item(key = "h:" + kind.key) {
                    Box(Modifier.padding(top = 16.dp)) { Heading(kind.title + " · " + (kind.categories.size - kind.categories.count { it.id in off }) + " of " + kind.categories.size + " showing") }
                }
                item(key = "all:" + kind.key) { FocusRow("Show all of " + kind.title, { Choices.hidden(context, kind.key, emptySet()) }, look = Look.OTHER) }
                item(key = "none:" + kind.key) { FocusRow("Hide all of " + kind.title, { Choices.hidden(context, kind.key, kind.categories.mapTo(HashSet()) { it.id }) }, look = Look.OTHER) }
                itemsIndexed(kind.categories, key = { _, c -> kind.key + ":" + c.id }) { _, category ->
                    val isOff = category.id in off
                    FocusRow(
                        (if (isOff) "✗  " else "✓  ") + category.name + " · " + (kind.counts[category.id] ?: 0) + if (isOff) " · hidden" else "",
                        { Choices.hidden(context, kind.key, if (isOff) off - category.id else off + category.id) },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- home

/**
 * Home, where the app opens. On a TV, like the Fire TV's own home: a tile for each section in a row
 * across the screen (◀ ▶ to move, OK to open), and under it a row of favourite channels to play
 * straight away. The channel last watched carries on at the top. On a phone the tiles sit three to a row.
 */
@Composable
private fun HomeScreen(
    library: Library,
    state: BrowseState,
    favourites: Favourites,
    homeFocus: FocusRequester,
    resume: FocusRequester,
    onPlay: (items: List<Playable>, index: Int, live: Boolean, key: String) -> Unit,
    onOpen: (Section) -> Unit,
    onCarryOn: (Positions.Watching) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val login = library.account != null
    val favs = favourites.keys
    val tiles = Section.entries.filter { it != Section.HOME }
    val channels = remember(library, favs) { library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, login) in favs } } }
    val playables = remember(channels) { channels.map { livePlayable(it, library.account, library.serverAheadSec) } }
    val clock by produceState(homeClock()) {
        while (true) {
            delay(10_000)
            value = homeClock()
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (phone) 8.dp else 40.dp, vertical = if (phone) 8.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 14.dp else 18.dp),
    ) {
        // The top: the app's name and the time, and on a TV the channel last watched, still playing.
        val last = LastLive.item
        val smallTv = remember(Choices.changes) { Choices.smallTv(context) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                KueLogo(if (phone) 36.dp else 48.dp, if (phone) 30.sp else 40.sp)
                Text(clock, color = Soft, fontSize = if (phone) 17.sp else 22.sp)
                if (!phone && last != null && smallTv) {
                    Spacer(Modifier.height(16.dp))
                    Text("Last watched", color = Soft, fontSize = 16.sp)
                    Text(last.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!phone && last != null && smallTv) MiniTv(last, Modifier.width(320.dp))
        }

        if (phone) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s ->
                        Tile({ onOpen(s) }, Modifier.weight(1f).aspectRatio(1.1f)) { focused ->
                            SectionIcon(s, if (focused) Color.Black else Accent, Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(s.label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // A short last row keeps its tiles the same size as the others.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            // All the tiles are drawn, so the one to land on can always take focus, even when it is off to the right.
            Row(
                Modifier.fillMaxWidth().focusRestorer().focusGroup().horizontalScroll(rememberScrollState()).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                tiles.forEach { s ->
                    Tile(
                        { onOpen(s) },
                        Modifier.size(width = 190.dp, height = 136.dp).then(if (s == state.cameFrom) Modifier.focusRequester(homeFocus) else Modifier),
                    ) { focused ->
                        SectionIcon(s, if (focused) Color.Black else Accent, Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(s.label, color = if (focused) Color.Black else Color.White, fontSize = 20.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Films and episodes left part way through: OK carries on from where it was left.
        val carry = remember(Positions.changes) { Positions.watching(context) }
        if (carry.isNotEmpty()) {
            Heading("Carry on watching")
            val carryList = remember { LazyListState(carry.indexOfFirst { "carry:" + it.url == state.resumeKey }.coerceAtLeast(0)) }
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                state = carryList,
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 18.dp),
            ) {
                itemsIndexed(carry, key = { _, w -> w.url }) { _, w ->
                    val key = "carry:" + w.url
                    Tile(
                        { onCarryOn(w) },
                        Modifier.width(if (phone) 110.dp else 150.dp).then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                    ) { focused ->
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                            if (w.poster.isNotBlank()) AsyncImage(model = w.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(w.name, color = Soft, fontSize = 14.sp, maxLines = 4, modifier = Modifier.padding(8.dp))
                        }
                        // How far through: a bar, and the minutes left.
                        val through = if (w.lengthMs > 0) (w.positionMs.toFloat() / w.lengthMs).coerceIn(0f, 1f) else 0f
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(through).fillMaxHeight().background(if (focused) Color.Black else Accent, RoundedCornerShape(2.dp)))
                        }
                        Text(
                            w.name, color = if (focused) Color.Black else Color.White, fontSize = if (phone) 13.sp else 15.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        Text(
                            ((w.lengthMs - w.positionMs) / 60_000).coerceAtLeast(1).toString() + " min left",
                            color = if (focused) Color.Black else Soft, fontSize = if (phone) 12.sp else 13.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Heading("Favourite channels")
        if (channels.isEmpty()) {
            Note(if (phone) "Press and hold a channel in Live TV to make it a favourite. Favourites show here." else "Hold OK on a channel in Live TV to make it a favourite. Favourites show here.")
        } else {
            // Opens on the channel that was playing, so coming back from the player lands on it.
            val list = remember { LazyListState(channels.indexOfFirst { "home:" + it.id == state.resumeKey }.coerceAtLeast(0)) }
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                state = list,
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 18.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, channel ->
                    val key = "home:" + channel.id
                    Tile(
                        { onPlay(playables, i, true, key) },
                        Modifier.width(if (phone) 150.dp else 220.dp).then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                        onHold = { favourites.toggleAll(channel.versions.map { Favourites.key(it.channel, login) }) },
                    ) { focused ->
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                            if (channel.logo.isNotBlank()) AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(10.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            channel.name, color = if (focused) Color.Black else Color.White, fontSize = if (phone) 15.sp else 18.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            nowLine(library.account, channel.versions[channel.start].channel.id).orEmpty(),
                            color = if (focused) Color.Black else Soft, fontSize = if (phone) 12.sp else 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun homeClock(): String = java.text.SimpleDateFormat("EEEE d MMMM · HH:mm", java.util.Locale.UK).format(java.util.Date())

/** A tile on Home: dark until the remote lands on it, then yellow and a little bigger. */
@Composable
private fun Tile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHold: (() -> Unit)? = null,
    content: @Composable ColumnScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .scale(if (focused) 1.06f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Accent else Panel)
            .onFocusChanged { focused = it.isFocused }
            .press(onClick, onHold)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content(focused) }
}

/** A plain drawn picture for each section, so the tiles read at a glance from the sofa. */
@Composable
private fun SectionIcon(section: Section, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = w * 0.08f
        val stroke = Stroke(width = line, cap = StrokeCap.Round)
        when (section) {
            Section.SEARCH -> {
                drawCircle(color, radius = w * 0.27f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.88f, h * 0.88f), strokeWidth = line * 1.3f, cap = StrokeCap.Round)
            }
            Section.LIVE -> {
                drawRoundRect(color, topLeft = Offset(w * 0.08f, h * 0.3f), size = Size(w * 0.84f, h * 0.58f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.08f), Offset(w * 0.5f, h * 0.28f), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, h * 0.08f), Offset(w * 0.5f, h * 0.28f), strokeWidth = line, cap = StrokeCap.Round)
            }
            Section.GUIDE -> for (i in 0..2) {
                val y = h * (0.25f + 0.25f * i)
                drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.24f, y), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.38f, y), Offset(w * 0.9f, y), strokeWidth = line, cap = StrokeCap.Round)
            }
            Section.CATCHUP -> {
                drawArc(color, startAngle = -50f, sweepAngle = 290f, useCenter = false, topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.3f), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.36f, h * 0.6f), strokeWidth = line, cap = StrokeCap.Round)
                val tip = Path().apply {
                    moveTo(w * 0.62f, h * 0.06f)
                    lineTo(w * 0.86f, h * 0.2f)
                    lineTo(w * 0.62f, h * 0.32f)
                    close()
                }
                drawPath(tip, color)
            }
            Section.FILMS -> {
                drawRoundRect(color, topLeft = Offset(w * 0.08f, h * 0.18f), size = Size(w * 0.84f, h * 0.64f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                val play = Path().apply {
                    moveTo(w * 0.42f, h * 0.36f)
                    lineTo(w * 0.64f, h * 0.5f)
                    lineTo(w * 0.42f, h * 0.64f)
                    close()
                }
                drawPath(play, color)
            }
            Section.SERIES -> {
                drawRoundRect(color, topLeft = Offset(w * 0.3f, h * 0.1f), size = Size(w * 0.6f, h * 0.48f), cornerRadius = CornerRadius(w * 0.06f), style = stroke)
                drawRoundRect(color, topLeft = Offset(w * 0.1f, h * 0.32f), size = Size(w * 0.6f, h * 0.5f), cornerRadius = CornerRadius(w * 0.06f))
            }
            Section.RECORDINGS -> {
                drawCircle(color, radius = w * 0.42f, center = center, style = stroke)
                drawCircle(color, radius = w * 0.24f, center = center)
            }
            Section.APPS -> for (x in 0..1) for (y in 0..1) {
                drawRoundRect(color, topLeft = Offset(w * (0.1f + 0.44f * x), h * (0.1f + 0.44f * y)), size = Size(w * 0.36f, h * 0.36f), cornerRadius = CornerRadius(w * 0.07f))
            }
            Section.SETTINGS -> {
                for (i in 0 until 8) rotate(45f * i) {
                    drawRoundRect(color, topLeft = Offset(w * 0.43f, h * 0.06f), size = Size(w * 0.14f, h * 0.18f), cornerRadius = CornerRadius(w * 0.03f))
                }
                drawCircle(color, radius = w * 0.25f, center = center, style = Stroke(width = line * 1.6f))
            }
            Section.HOME -> {
                val house = Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    lineTo(w * 0.9f, h * 0.48f)
                    lineTo(w * 0.78f, h * 0.48f)
                    lineTo(w * 0.78f, h * 0.88f)
                    lineTo(w * 0.22f, h * 0.88f)
                    lineTo(w * 0.22f, h * 0.48f)
                    lineTo(w * 0.1f, h * 0.48f)
                    close()
                }
                drawPath(house, color)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- updates

/** Shown on opening when a newer version is on the download. */
@Composable
private fun UpdateOfferScreen(latest: Updates.Latest, onLater: () -> Unit) {
    val phone = LocalPhone.current
    BackHandler { onLater() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 220.dp, vertical = if (phone) 12.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("A newer KueTV 2 is ready", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Note("You have version " + BuildConfig.VERSION_NAME + ". Version " + latest.name + " is on the download.")
        Spacer(Modifier.height(8.dp))
        UpdateNow(latest, Modifier.focusRequester(first))
        FocusRow("Not now", onLater, look = Look.OTHER)
    }
}

/** Settings › About: the version, and checking for a newer one by hand. */
@Composable
private fun AboutScreen(onDone: () -> Unit) {
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    BackHandler { onDone() }
    var checking by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Updates.Latest?>(null) }
    val check: () -> Unit = {
        if (!checking) {
            checking = true
            said = null
            found = null
            scope.launch {
                try {
                    val latest = withContext(Dispatchers.IO) { Updates.check() }
                    if (Updates.isNewer(latest, BuildConfig.VERSION_CODE)) found = latest
                    else said = "You have the newest version."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Report.note("Updates", "check by hand failed: " + e.message)
                    said = "Couldn't reach the download. Check the internet and try again."
                } finally {
                    checking = false
                }
            }
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 220.dp, vertical = if (phone) 12.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("About KueTV 2", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Note("Version " + BuildConfig.VERSION_NAME + ". It also checks for a newer version each time it opens.")
        Spacer(Modifier.height(8.dp))
        FocusRow(if (checking) "Checking…" else "Check for updates", check, Modifier.focusRequester(first), look = Look.MAIN)
        said?.let { Note(it) }
        found?.let { latest ->
            Note("Version " + latest.name + " is ready.")
            UpdateNow(latest)
        }
        FocusRow("Done", onDone, look = Look.OTHER)
    }
}

/**
 * The row that puts a newer version on: it downloads the app, then hands it to the device's installer,
 * which asks before it installs over this one. The first time, the device has to be told KueTV may do this.
 */
@Composable
private fun UpdateNow(latest: Updates.Latest, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    val go: () -> Unit = {
        when {
            busy -> {}
            !Updates.mayInstall(context) -> {
                val opened = Updates.openInstallPermission(context)
                Report.note("Updates", "not yet allowed to install; settings page " + if (opened) "opened" else "not found")
                said = if (opened) "First, on the page that opened, turn on KueTV 2. Then come back and choose Update now again."
                else "First allow KueTV 2 to install apps. On a Fire TV: Settings › My Fire TV › Developer options › Install unknown apps › KueTV 2 › On. Then choose Update now again."
            }
            else -> {
                busy = true
                said = "Downloading · 0%"
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { Updates.download(context) { p -> scope.launch { said = "Downloading · $p%" } } }
                        said = "Downloaded. Choose Install on the next screen."
                        Report.note("Updates", "downloaded " + latest.name + ", handing to the installer")
                        Updates.install(context, file)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Report.note("Updates", "update failed: " + e.javaClass.simpleName + " · " + e.message)
                        said = "Couldn't put the update on. Check the internet and try again."
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
    FocusRow(if (busy) "Downloading…" else "Update now to version " + latest.name, go, modifier, look = Look.MAIN)
    said?.let { Note(it) }
}

// --------------------------------------------------------------------------------------------- live TV picture

/**
 * Beside the Live TV list on a TV: the channel the remote is on, playing in the top right, and what is
 * on it underneath. It changes channel once the remote has stayed on a row for a moment, so running down
 * the list doesn't open a connection for every row passed. It plays the channel's lightest version.
 * OK on the row opens the channel full screen.
 */
@Composable
private fun LivePreview(on: ChannelGroup?, account: Account?, modifier: Modifier) {
    var showing by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == null || on.id == showing?.id) return@LaunchedEffect
        delay(900)
        showing = on
    }
    val group = showing
    val item = remember(group?.id) { group?.let { Playable(it.name, it.versions.last().channel.url) } } ?: LastLive.item
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item != null) {
            MiniTv(item, Modifier.fillMaxWidth())
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Panel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("Move onto a channel to watch it here.", color = Soft, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
            }
        }
        if (group != null) ProgrammeDetails(group, account, Modifier.fillMaxWidth().weight(1f))
    }
}

/** What is on a channel now (with the whole description, as far as there is room) and what is on next. */
@Composable
private fun ProgrammeDetails(group: ChannelGroup, account: Account?, modifier: Modifier) {
    val id = group.versions[group.start].channel.id
    val guide by produceState(NowNext.cached(id), id) {
        if (value != null || account == null) return@produceState
        value = try {
            NowNext.get(account, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val at = System.currentTimeMillis()
    val listing = guide.orEmpty()
    val onNow = listing.firstOrNull { it.startMs <= at && it.stopMs > at }
    val next = listing.firstOrNull { it.startMs >= (onNow?.stopMs ?: at) }
    Column(modifier.background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        when {
            account == null -> Text("Programme details need a login: server address, username and password.", color = Soft, fontSize = 17.sp)
            guide == null -> Text("Finding what is on…", color = Soft, fontSize = 17.sp)
            onNow == null && next == null -> Text("No programme details for this channel.", color = Soft, fontSize = 17.sp)
        }
        if (onNow != null) {
            Text(
                "Now · " + clock.format(java.util.Date(onNow.startMs)) + " to " + clock.format(java.util.Date(onNow.stopMs)),
                color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text(onNow.title.ifBlank { "No title" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onNow.description.isNotBlank()) {
                Text(
                    onNow.description, color = Soft, fontSize = 16.sp, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(top = 4.dp),
                )
            }
        }
        if (next != null) {
            Text(
                "Next · " + clock.format(java.util.Date(next.startMs)) + " · " + next.title.ifBlank { "No title" },
                color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// --------------------------------------------------------------------------------------------- health check

/** Settings › Health check: every outside source the app depends on, tried, with a tick or a cross and why. */
@Composable
private fun HealthScreen(account: Account, library: Library?, onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    BackHandler { onDone() }
    val running = Health.running
    val results = Health.results
    val check: () -> Unit = {
        if (!Health.running) scope.launch { withContext(Dispatchers.IO) { runCatching { Health.run(context, account, library) } } }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
        // Opened with nothing to show yet: check straight away.
        if (Health.results.isEmpty()) check()
    }
    val at = remember(Health.lastRunMs) {
        if (Health.lastRunMs == 0L) "" else java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK).format(java.util.Date(Health.lastRunMs))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 140.dp, vertical = if (phone) 12.dp else 30.dp)) {
        Text("Health check", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Note(if (running) "Checking everything. This takes up to a minute…" else Health.summary(results) + if (at.isEmpty()) "" else " · checked " + at)
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).focusRestorer(), verticalArrangement = if (phone) Arrangement.spacedBy(6.dp) else Arrangement.Top) {
            item(key = "check") { FocusRow(if (running) "Checking…" else "Check now", check, Modifier.focusRequester(first), look = Look.MAIN) }
            item(key = "done") { FocusRow("Done", onDone, look = Look.OTHER) }
            itemsIndexed(results, key = { i, c -> i.toString() + c.name }) { _, c ->
                val mark = when (c.state) { Check.WORKING -> "✓  "; Check.DOUBT -> "!  "; else -> "✗  NOT WORKING · " }
                HealthRow(mark + c.name, c.detail, c.state)
            }
        }
    }
}

/** One result: its name with a tick, a mark or a cross, and underneath, in full, what was found. */
@Composable
private fun HealthRow(title: String, detail: String, state: Int) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(title, color = when (state) { Check.WORKING -> Color.White; Check.DOUBT -> Accent; else -> Color(0xFFFF6B5E) }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Soft, fontSize = 16.sp)
    }
}
