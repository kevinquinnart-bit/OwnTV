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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import art.kquinn.kuetv.TrText as Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.testTag
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

// The colours come from the look chosen in Settings › Looks, so a change shows straight away.
private val Page: Color get() = Looks.page
private val Panel: Color get() = Looks.panel
private val Accent: Color get() = Looks.accentColour
private val Soft: Color get() = Looks.soft
private val Ink: Color get() = Looks.ink
private val AccentInk: Color get() = Looks.accentInk

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
        PhoneSetup.app = applicationContext
        Report.note("App", "opened, version " + BuildConfig.VERSION_NAME)
        // TV: what the TV's own channel list holds (the aerial, and the channels the TV apps add), written where a
        // computer can read it, to find out how Freely's way of opening channels can be brought into KueTV.
        if (isTv(this)) Thread { TvChannels.writeProbe(applicationContext) }.start()
        if (isTv(this)) Thread { runCatching { UiSounds.load(applicationContext) } }.start()
        // A TV is always sideways. A phone turns with the way it is held.
        val tv = isTv(this)
        if (tv) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // On a TV the screen stays on while KueTV is showing: the Fire TV screensaver doesn't come on over it.
        if (tv) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Looks.load(this)
        UiLang.load(this)
        setContent {
            MaterialTheme(colorScheme = if (Looks.light) lightColorScheme(primary = Accent) else darkColorScheme(primary = Accent), typography = KueType) {
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

    override fun onPause() {
        super.onPause()
        HomeGuard.inFront = false
    }

    override fun onResume() {
        super.onResume()
        Resumes.count++
        launching = false
        // KueTV is on screen: the Home button watcher's black cover can come down.
        HomeGuard.shown()
        // Just updated: the Home button watcher may need starting again.
        if (isTv(this)) Thread { HomeGuard.wakeIfAsleep(applicationContext) }.start()
    }

    // Set while KueTV itself opens something (Google sign-in, the installer, another player, a VPN prompt),
    // so that isn't taken for leaving KueTV.
    private var launching = false

    override fun startActivity(intent: android.content.Intent, options: Bundle?) {
        launching = true
        super.startActivity(intent, options)
    }

    @Deprecated("Still what the activity-result tools call")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        launching = true
        @Suppress("DEPRECATION") super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Still what Google sign-in calls")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender, requestCode: Int, fillInIntent: android.content.Intent?,
        flagsMask: Int, flagsValues: Int, extraFlags: Int, options: Bundle?,
    ) {
        launching = true
        @Suppress("DEPRECATION") super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }

    override fun onStart() {
        super.onStart()
        Screen.visible = true
    }

    override fun onStop() {
        super.onStop()
        Screen.visible = false
    }

    // Leaving KueTV (Home) while something plays: it carries on in a small picture in the corner, where the device allows.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // On a TV, Home (or another app) closes KueTV completely: nothing stays running or showing in a corner.
        // As the TV's home screen, KueTV stays: it is what Home comes back to.
        if (isTv(this) && !launching && !Launcher.isHome(this)) {
            Report.note("App", "closed on leaving")
            finishAndRemoveTask()
            return
        }
        if (!Pip.wanted || !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build())
        }.onFailure { Report.note("Player", "couldn't go to a small picture: " + it.message) }
    }

    // A spare button on the remote (Guide, red, green, yellow, blue), when set in Settings to open part of KueTV.
    // The Home button (through the Home watcher) brings KueTV back: it goes to its own Home, leaving the player.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(android.content.Intent.CATEGORY_HOME)) HomePresses.count++
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (SpareKeys.keys.any { it.code == event.keyCode } && SpareKeys.action(this, event.keyCode) != "Nothing") {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) SpareKeys.pressed(this, event.keyCode)
            return true
        }
        // TV: a quiet tick as the remote moves, a fuller one for OK, a lower one for Back (not over a programme).
        if (isTv(this)) {
            val k = event.keyCode
            if (event.action == android.view.KeyEvent.ACTION_DOWN) when (k) {
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> UiSounds.move(this)
            } else if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) when (k) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> UiSounds.ok(this)
                android.view.KeyEvent.KEYCODE_BACK -> UiSounds.back(this)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Pip.inPip = isInPictureInPictureMode
    }
}

/** Goes up each time the TV's Home button brings KueTV to the front: KueTV then shows its own Home. */
private object HomePresses {
    var count by mutableIntStateOf(0)
}

/** Goes up each time KueTV comes back to the front, for example back from another app or the app store. */
private object Resumes {
    var count by mutableIntStateOf(0)
}

/** What is playing: the list it came from (so ▲ ▼ can change channel), where in it, and its kind. */
private class Playing(val items: List<Playable>, val index: Int, val live: Boolean, val vlc: Boolean = false)

@Composable
private fun App() {
    val context = LocalContext.current
    var account by remember { mutableStateOf(AccountStore.load(context)) }
    var editing by remember { mutableStateOf(false) }
    // What the provider last sent. The lists the screens use, [library], are these with any free channels added.
    var loaded by remember { mutableStateOf<Library?>(null) }
    val noProvider = remember(Choices.changes) { Choices.noProvider(context) }
    val freeLinks = remember(FreeStore.changes) { FreeStore.all(context) }
    val addOns = remember(AddOnStore.changes) { AddOnStore.all(context) }
    // The player asks these for the add-ons' TV channels, in the subtitle language chosen.
    LaunchedEffect(addOns) { AddOns.inUse = addOns }
    val chosenLang = remember(Choices.changes) { Choices.subtitleLang(context) }
    LaunchedEffect(chosenLang) { AddOns.lang = Langs.of(chosenLang) }
    var free by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var freeOpen by remember { mutableStateOf(false) }
    // Other providers chosen in Settings › Your provider to be in the lists too.
    var others by remember { mutableStateOf<List<Pair<Account, Library>>>(emptyList()) }
    val library = remember(loaded, free, others) { loaded?.let { Combined.merge(it, others) }?.withFree(free) }
    var problem by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Playing?>(null) }
    // Multiview: several live channels at once, from the player.
    var multi by remember { mutableStateOf<Playing?>(null) }
    // A spare button asking for part of KueTV: whatever is playing closes first.
    LaunchedEffect(SpareKeys.asked) {
        if (SpareKeys.asked != null) { playing = null; multi = null }
    }
    LaunchedEffect(HomePresses.count) { if (HomePresses.count > 0) multi = null }
    // What the browsing screen was showing, kept while the player covers it, so closing the player
    // comes back to the same list, at the same place, on the row or poster that was playing.
    val browse = remember(account, reload) { BrowseState(saved = Place.read(context)) }
    // Home pressed on the remote: close whatever is playing and show KueTV's Home.
    LaunchedEffect(HomePresses.count) {
        if (HomePresses.count == 0) return@LaunchedEffect
        playing = null
        browse.section = Section.HOME
    }
    // The profile in use: its own favourites, history, watched marks and places. A kids profile hides adult categories.
    val profileId = remember(Profiles.changes) { Profiles.currentId(context) }
    val kids = remember(Profiles.changes) { Profiles.current(context).kids }
    val favourites = remember(profileId) { Favourites(context.applicationContext) }
    // "Add another provider": the provider details screen, empty, with the one in use kept on the list.
    var addingProvider by remember { mutableStateOf(false) }
    // What the last backup or restore said, for the Google Drive page.
    var driveNote by remember { mutableStateOf("") }
    var driveSignIn by remember { mutableStateOf(false) }
    var addOnsOpen by remember { mutableStateOf(false) }
    var stremioOpen by remember { mutableStateOf(false) }
    var addLinkOpen by remember { mutableStateOf(false) }
    // 1: sending this device's set-up to another. 2: copying another device's set-up to here.
    var setUpShare by remember { mutableIntStateOf(0) }
    var reportOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var healthOpen by remember { mutableStateOf(false) }
    // A film or episode that can play from an add-on or from the provider's own copy, waiting for the user to pick.
    var choice by remember { mutableStateOf<Playing?>(null) }
    // What the lists show: everything the provider sent, less the categories chosen to be hidden.
    val shownLibrary = remember(library, Choices.changes, kids, Renames.changes, HiddenChannels.changes) {
        // A kids profile: adult categories hidden too.
        val adult: (List<Category>) -> Set<String> = { cats -> if (kids) cats.filter { Profiles.isAdult(it.name) }.mapTo(HashSet()) { it.id } else emptySet() }
        // Channels given names of the user's own.
        library?.renamed(Renames.all(context))?.without(
            Choices.hidden(context, "live") + adult(library.liveCategories),
            Choices.hidden(context, "film") + adult(library.filmCategories),
            Choices.hidden(context, "show") + adult(library.showCategories),
        )
            // ...and each kind's categories in the user's own order.
            ?.inOrder(Choices.order(context, "live"), Choices.order(context, "film"), Choices.order(context, "show"))
            // ...and without the channels hidden one by one.
            ?.withoutChannels(HiddenChannels.all(context))
    }
    // Each time KueTV comes to the front: carry on copying any recordings that are waiting for Google Drive.
    LaunchedEffect(Resumes.count) { Recordings.wake(context) }
    // Anything a torrent left behind, if the app was closed while one played, is cleared when the app opens.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { Torrents.folder(context).deleteRecursively() } } }
    // Programmes set to record or to remind: make sure each still has its alarm.
    LaunchedEffect(Unit) { runCatching { TimedStore.arm(context); Reminders.arm(context) } }
    remember { GuideOffset.load(context); true }
    // While KueTV is on screen: every few seconds, is a reminded programme starting?
    LaunchedEffect(Screen.visible) {
        while (Screen.visible) {
            runCatching { Reminders.check(context) }
            delay(10_000)
        }
    }
    // A newer version downloaded last time is no longer needed once the app starts afresh.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Updates.tidy(context); ShiftBuffer.tidy(context) } }
    // The VPN: what it is doing, for its page; and on opening, connect it if that was chosen.
    LaunchedEffect(Unit) {
        Vpn.listen(context)
        if (!Vpn.connectOnOpen(context) || Vpn.details(context).isEmpty) return@LaunchedEffect
        // The engine says how it is a moment after the app opens; a VPN already on is left alone.
        delay(1_500)
        if (Vpn.isOn || Vpn.isTrying) return@LaunchedEffect
        if (Vpn.permissionAsk(context) != null) {
            Report.note("VPN", "not connected on opening: the device's permission for KueTV's VPN hasn't been given yet")
            return@LaunchedEffect
        }
        runCatching { Vpn.start(context) }.onFailure { Report.note("VPN", "not connected on opening: " + it.message) }
    }
    // Keeping in step with a paired device: while KueTV is on screen, this device answers the other when it
    // asks, and every minute asks the other what it has, taking each part that was changed more recently there.
    val pairKey = remember(Sync.changes) { Sync.key(context) }
    // Everything that makes KueTV the user's own, in parts: what the two devices keep in step, and what a backup holds.
    val kinds = listOf("live", "film", "show")
    val bodies: () -> Map<String, String> = {
            val a = AccountStore.load(context)
            mapOf(
                "account" to listOf(a.server, a.user, a.pass, a.playlist).joinToString("\n"),
                "favourites" to favourites.keys.sorted().joinToString("\n"),
                "favouriteOrder" to favourites.order.joinToString("\n"),
                "categoryOrder" to kinds.joinToString("\n") { kind -> kind + "=" + Choices.order(context, kind).joinToString("\u0001") },
                "stremio" to Stremio.export(context),
                "addOns" to AddOnStore.all(context).joinToString("\n") { it.toJson() },
                "free" to FreeStore.all(context).joinToString("\n"),
                "hidden" to kinds.joinToString("\n") { kind -> kind + "=" + Choices.hidden(context, kind).sorted().joinToString("\u0001") },
                "subtitles" to Choices.subtitles(context).toString() + "\n" + Choices.subtitleLang(context),
                "places" to Positions.export(context),
                "reminders" to Reminders.all(context).sortedBy { it.id }.joinToString("\n") { it.toJson() },
                "vpn" to Vpn.details(context).let { d -> d.user + "\u0002" + d.pass + "\u0002" + d.config },
                "watched" to Watched.export(context),
                "recent" to Recent.export(context),
                "mycategories" to MyCategories.export(context),
                "renames" to Renames.export(context),
            )
        }
        val take: (String, String) -> Unit = { name, body ->
            val lines = body.split('\n')
            when (name) {
                "account" -> if (lines.size == 4) {
                    val theirs = Account(lines[0], lines[1], lines[2], lines[3])
                    AccountStore.save(context, theirs)
                    // The lists load again from the new provider details.
                    account = theirs
                }
                "favourites" -> favourites.replaceAll(lines.filter { it.isNotEmpty() }.toSet())
                "favouriteOrder" -> favourites.putInOrder(lines.filter { it.isNotEmpty() })
                "categoryOrder" -> for (line in lines) {
                    val kind = line.substringBefore('=')
                    if (kind in kinds) Choices.order(context, kind, line.substringAfter('=', "").split('\u0001').filter { it.isNotEmpty() })
                }
                "stremio" -> Stremio.import(context, body)
                "addOns" -> AddOnStore.replaceAll(context, lines.mapNotNull { AddOn.fromJson(it) })
                "free" -> FreeStore.replaceAll(context, lines.filter { it.isNotBlank() })
                "hidden" -> for (line in lines) {
                    val kind = line.substringBefore('=')
                    if (kind in kinds) Choices.hidden(context, kind, line.substringAfter('=', "").split('\u0001').filter { it.isNotEmpty() }.toSet())
                }
                "subtitles" -> if (lines.size == 2) {
                    Choices.subtitles(context, lines[0] == "true")
                    Choices.subtitleLang(context, lines[1])
                }
                "places" -> Positions.import(context, body)
                "reminders" -> Reminders.replaceAll(context, lines.mapNotNull { Reminder.fromJson(it) })
                "vpn" -> body.split('\u0002', limit = 3).takeIf { it.size == 3 }?.let { Vpn.adopt(context, it[2], it[0], it[1]) }
                "watched" -> Watched.import(context, body)
                "recent" -> Recent.import(context, body)
                "mycategories" -> MyCategories.import(context, body)
                "renames" -> Renames.import(context, body)
            }
        }
    LaunchedEffect(Screen.visible, pairKey) {
        if (!Screen.visible || pairKey.isEmpty()) return@LaunchedEffect
        val server = SyncServer(pairKey)
        val answering = server.start()
        Report.note("Keeping in step", if (answering) "on: answering the other device" else "on, but this device couldn't open its door to be asked; it can still ask")
        try {
            var round = 0
            while (true) {
                var mine = Sync.current(context, bodies())
                Sync.published = Sync.pack(mine)
                // The other device is asked straight away, then every minute.
                if (round % 4 == 0) {
                    val others = withContext(Dispatchers.IO) { runCatching { Sync.pull(context, pairKey) }.getOrDefault(emptyList()) }
                    for (theirs in others) {
                        val wanted = Sync.newer(mine, theirs)
                        for ((name, part) in wanted) {
                            runCatching { take(name, part.body) }
                            // Noted as it now stands here, with the time it was changed there, so it isn't sent back as a change of this device's.
                            Sync.adopted(context, name, bodies()[name].orEmpty(), part.at)
                        }
                        if (wanted.isNotEmpty()) {
                            Report.note("Keeping in step", "took from the other device: " + wanted.keys.sorted().joinToString(", "))
                            Sync.tookNote(wanted.keys)
                            mine = Sync.current(context, bodies())
                            Sync.published = Sync.pack(mine)
                        }
                    }
                }
                round++
                delay(15_000)
            }
        } finally {
            server.stop()
        }
    }
    // The backup in Google Drive: the same parts, in one small file.
    val scope = rememberCoroutineScope()
    val backUpNow: suspend () -> String = {
        val drive = Recordings.drive(context)
        try {
            if (!drive.signedIn) "Sign in to Google Drive first."
            else {
                val text = Backup.pack(bodies())
                withContext(Dispatchers.IO) { drive.putText(Backup.FILE, text) }
                Backup.done(context)
                Report.note("Backup", "saved to Google Drive: " + text.length / 1024 + " KB")
                "Backed up to Google Drive just now."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Report.note("Backup", "failed: " + e.message)
            "Couldn't back up: " + (e.message ?: "something went wrong")
        }
    }
    val restoreNow: suspend () -> String = {
        val drive = Recordings.drive(context)
        try {
            val text = if (!drive.signedIn) null else withContext(Dispatchers.IO) { drive.getText(Backup.FILE) }
            val parts = text?.let { Backup.unpack(it) }
            when {
                !drive.signedIn -> "Sign in to Google Drive first."
                text == null -> "There is no backup in this Google Drive yet."
                parts == null -> "The backup in Google Drive couldn't be read."
                else -> {
                    for ((name, body) in parts) runCatching { take(name, body) }
                    Report.note("Backup", "restored from Google Drive: " + parts.keys.sorted().joinToString(", "))
                    "Restored from Google Drive."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Report.note("Backup", "restore failed: " + e.message)
            "Couldn't restore: " + (e.message ?: "something went wrong")
        }
    }
    // Backing up by itself: by default every time KueTV opens, once the lists are in.
    LaunchedEffect(Resumes.count, library != null) {
        if (library == null || !Recordings.drive(context).signedIn || !Backup.due(context)) return@LaunchedEffect
        delay(30_000)
        driveNote = backUpNow()
    }
    // The free UK and Irish guides: read once the lists are in, for the channels the provider has no guide for.
    val guidesOn = remember(FreeGuides.changes) { FreeGuides.sources.count { FreeGuides.isOn(context, it.id) } }
    LaunchedEffect(library?.groups?.size, guidesOn, Resumes.count) {
        val lib = library ?: return@LaunchedEffect
        if (guidesOn == 0) return@LaunchedEffect
        delay(15_000)
        withContext(Dispatchers.IO) { runCatching { FreeGuides.refresh(context, lib.groups.map { it.name }) } }
        NowNext.forgetEmpty()
    }
    // Provider details that have loaded are kept on the list, to switch back to later.
    LaunchedEffect(loaded != null, account) { if (loaded != null) Providers.keep(context, account) }
    // On opening, when signed in to Stremio: its add-ons brought in, and ones taken out of Stremio taken out here.
    LaunchedEffect(Unit) {
        if (Stremio.key(context).isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { Stremio.bringIn(context) }.onFailure { Report.note("Stremio", "bringing in on opening failed: " + it.message) } }
    }
    // Each add-on is read again when the app opens, so the lists of films and series it offers are up to date.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            for (a in AddOnStore.all(context)) runCatching { AddOns.read(a.base) }.getOrNull()?.let { fresh -> if (fresh != a) AddOnStore.update(context, fresh) }
        }
    }
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

    // The free channel lists: read when the app opens, when one is added or removed, and on a reload.
    LaunchedEffect(freeLinks, reload, addOns) {
        free = withContext(Dispatchers.IO) {
            val lists = freeLinks.flatMap { link ->
                runCatching { Provider.loadFree(link) }
                    .onFailure { Report.note("Free channels", FreeStore.label(link) + " couldn't be read: " + it.message) }
                    .getOrDefault(emptyList())
            }
            // The TV channels the add-ons list (Stremio's live TV add-ons, say).
            val fromAddOns = runCatching { AddOns.tvChannels(addOns) }.getOrDefault(emptyList())
            if (fromAddOns.isNotEmpty()) Report.note("Free channels", fromAddOns.size.toString() + " TV channels from add-ons")
            lists + fromAddOns
        }
        Report.note("Free channels", free.size.toString() + " channels in all, from " + freeLinks.size + " lists and the add-ons")
    }
    LaunchedEffect(account, reload, noProvider) {
        if (!account.isSet) {
            // No provider: the app runs on free channel lists and add-ons alone.
            loaded = if (noProvider) Library(emptyList(), emptyList(), emptyList(), emptyList()) else null
            return@LaunchedEffect
        }
        loaded = null
        problem = null
        // Opening the app: the lists kept here from last time are shown at once. A reload always fetches them afresh.
        val file = LibraryCache.file(context)
        val kept = if (reload == 0) withContext(Dispatchers.IO) { LibraryCache.read(file, account) } else null
        if (kept != null) {
            loaded = kept.library
            val age = System.currentTimeMillis() - kept.at
            Report.note("Provider", "opened on the lists kept here, fetched " + age / 60_000 + " minutes ago: " + kept.library.channels.size + " channels, " + kept.library.films.size + " films, " + kept.library.shows.size + " series")
            if (age in 0 until LibraryCache.FRESH_MS) return@LaunchedEffect
            // They are getting old: fetched again behind the scenes. If the provider can't be reached, the kept ones stay.
            try {
                val fresh = withContext(Dispatchers.IO) { Provider.load(account).also { LibraryCache.write(file, account, it) } }
                val changed = withContext(Dispatchers.Default) { !LibraryCache.same(fresh, kept.library) }
                Report.note("Provider", "fetched afresh in the background: " + if (changed) "changed, now " + fresh.channels.size + " channels, " + fresh.films.size + " films, " + fresh.shows.size + " series" else "no changes")
                if (changed) loaded = fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Report.note("Provider", "fetching afresh in the background failed, keeping the lists from last time: " + e.message)
            }
            return@LaunchedEffect
        }
        try {
            loaded = withContext(Dispatchers.IO) { Provider.load(account).also { fresh -> runCatching { LibraryCache.write(file, account, fresh) } } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderProblem) {
            Report.note("Provider", "loading failed: " + e.message)
            problem = e.message
        } catch (e: Exception) {
            Report.note("Provider", "loading failed: " + e.javaClass.name + " · " + e.message)
            problem = "Something went wrong loading your channels. Try again."
        }
        loaded?.let { Report.note("Provider", "loaded: " + it.channels.size + " channels as " + it.groups.size + " rows, " + it.films.size + " films, " + it.shows.size + " series") }
    }
    // The other providers' lists: kept ones at once, fetched afresh when old. One that can't be reached is left out.
    LaunchedEffect(account, reload, Combined.changes, Providers.changes) {
        val chosen = Combined.chosen(context).filter { it != account && it.isSet }
        if (chosen.isEmpty()) { others = emptyList(); return@LaunchedEffect }
        others = withContext(Dispatchers.IO) {
            chosen.mapNotNull { a ->
                val file = LibraryCache.file(context, a)
                val kept = if (reload == 0) LibraryCache.read(file, a) else null
                val lib = if (kept != null && System.currentTimeMillis() - kept.at in 0 until LibraryCache.FRESH_MS) kept.library else try {
                    Provider.load(a).also { runCatching { LibraryCache.write(file, a, it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Report.note("Provider", Providers.label(a) + " (shown alongside) couldn't be loaded: " + e.message)
                    kept?.library
                }
                lib?.also { Combined.register(a, it.serverAheadSec) }?.let { a to it }
            }
        }
        Report.note("Provider", others.size.toString() + " other providers in the lists: " + others.sumOf { it.second.channels.size } + " channels")
    }
    // Every showing set to record: the showings still to come in each channel's guide are set, each time the app opens.
    LaunchedEffect(library != null, Resumes.count, SeriesLinks.changes) {
        val lib = library ?: return@LaunchedEffect
        val account = lib.account ?: return@LaunchedEffect
        for (link in SeriesLinks.all(context)) {
            val group = lib.groups.firstOrNull { g -> g.versions.any { it.channel.id == link.channelId } } ?: lib.groups.firstOrNull { it.name == link.channelName } ?: continue
            val listing = runCatching { NowNext.get(account, group.versions[group.start].channel.id, group.name) }.getOrDefault(emptyList())
            val set = TimedStore.all(context).map { it.id }.toSet()
            for (t in SeriesLinks.showings(link, listing, System.currentTimeMillis())) if (t.id !in set) TimedStore.add(context, t)
        }
    }
    // Opening KueTV on a channel, as chosen in Settings › Watching: once, when the lists are first in.
    var openedOn by remember { mutableStateOf(false) }
    LaunchedEffect(library != null) {
        val lib = library ?: return@LaunchedEffect
        if (openedOn) return@LaunchedEffect
        openedOn = true
        val choice = OpenOn.get(context)
        val group = when {
            choice.isEmpty() -> null
            choice == "last" -> Recent.all(context).firstOrNull()?.let { key -> lib.groups.firstOrNull { g -> g.versions.any { it.channel.url == key } } }
            else -> lib.groups.firstOrNull { it.id == choice }
        } ?: return@LaunchedEffect
        if (AddOns.showable(group.lightest.url)) LastLive.item = Playable(group.name, group.lightest.url)
        playing = Playing(listOf(livePlayable(group, lib.account, lib.serverAheadSec)), 0, true)
    }
    // On a phone the screens keep clear of the notch and the bars; the player uses the whole screen.
    val reminder = Reminders.showing
    /** OK on a reminder: watch that channel, in place of whatever is showing. */
    val watchReminded: () -> Unit = {
        val r = Reminders.showing
        Reminders.showing = null
        val group = r?.let { library?.groupOf?.get(it.channelId) }
        if (group != null) {
            browse.resumeKey = null
            if (AddOns.showable(group.lightest.url)) LastLive.item = Playable(group.name, group.lightest.url)
            playing = Playing(listOf(livePlayable(group, library?.account, library?.serverAheadSec ?: 0)), 0, true)
        }
    }
    // The home internet gone for 15 seconds, on a TV with an aerial: the aerial is offered, once each time it goes.
    LaunchedEffect(Unit) {
        NetWatch.start(context)
        Report.note("Aerial", withContext(Dispatchers.Default) { Aerial.describe(context) })
    }
    LaunchedEffect(NetWatch.online) {
        if (NetWatch.online) { Aerial.offering = false; return@LaunchedEffect }
        delay(15_000)
        if (!NetWatch.online && Aerial.offerWhenOffline(context) && withContext(Dispatchers.Default) { Aerial.available(context) }) Aerial.offering = true
    }
    val watchAerial: () -> Unit = {
        Aerial.offering = false
        playing = null
        multi = null
        if (!Aerial.open(context)) kueToast(context, "The TV's aerial channels wouldn't open.")
    }
    // A reminder that isn't answered goes after a minute and a half.
    LaunchedEffect(reminder) {
        if (reminder == null) return@LaunchedEffect
        delay(90_000)
        if (Reminders.showing == reminder) Reminders.showing = null
    }
    Box(
        Modifier.fillMaxSize().background(Looks.backdrop).then(if (playing == null) Modifier.safeDrawingPadding() else Modifier)
            // With a reminder up, OK watches it and Back puts it away, before anything underneath sees those keys.
            .onPreviewKeyEvent { e ->
                // With the aerial offered, OK watches it and Back puts the offer away.
                if (Aerial.offering) return@onPreviewKeyEvent when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.type == KeyEventType.KeyUp) watchAerial(); true }
                    Key.Back -> { if (e.type == KeyEventType.KeyUp) Aerial.offering = false; true }
                    else -> false
                }
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
            multi != null -> {
                DisposableEffect(Unit) { UiSounds.muted = true; onDispose { UiSounds.muted = false } }
                val m = multi!!
                MultiviewScreen(
                    m.items, m.index,
                    onClose = { multi = null },
                    onWatch = { i -> multi = null; playing = Playing(m.items, i, true) },
                )
            }
            now != null && (now.vlc || PlayerEngine.vlc(context)) -> androidx.compose.runtime.key(now) {
                DisposableEffect(Unit) { UiSounds.muted = true; onDispose { UiSounds.muted = false } }
                VlcScreen(now.items, now.index, now.live, onClose = { playing = null })
            }
            now != null -> androidx.compose.runtime.key(now) {
                DisposableEffect(Unit) { UiSounds.muted = true; onDispose { UiSounds.muted = false } }
                PlayerScreen(
                    now.items, now.index, now.live,
                    onClose = { playing = null },
                    onFromStart = { programme -> playing = Playing(listOf(programme), 0, false) },
                    onMultiview = { items, index -> playing = null; multi = Playing(items, index, true) },
                    onVlc = { items, index -> playing = Playing(items, index, now.live, vlc = true) },
                    // ◀ on a live channel: back to the Live TV list, on that channel, in the category it was in if it is there.
                    onChannels = if (!now.live || whole == null) null else ({ item ->
                        val group = whole.groups.firstOrNull { g -> g.versions.any { it.channel.url == item.url } }
                            ?: whole.groups.firstOrNull { it.name == item.name }
                        if (group != null) {
                            val login = whole.account != null
                            val cat = browse.liveCategory
                            val inCat = when {
                                cat.startsWith(MyCategories.PREFIX) -> MyCategories.all(context).firstOrNull { it.id == cat }?.channels.orEmpty().contains(group.id)
                                cat == ALL || cat == RECENT -> true
                                cat == FAVOURITES -> group.versions.any { Favourites.key(it.channel, login) in favourites.keys }
                                cat == Library.FREE_ALL -> group.versions.any { it.free }
                                else -> group.versions.any { it.channel.categoryId == cat }
                            }
                            if (!inCat) browse.liveCategory = if (group.versions.any { Favourites.key(it.channel, login) in favourites.keys }) FAVOURITES else ALL
                            browse.resumeKey = "live:" + group.id
                            browse.liveOn = group
                            browse.jumpLive = true
                            browse.section = Section.LIVE
                        }
                        playing = null
                    }),
                    // The list over the picture holds only the channels being played: the other lists that were
                    // offered above them (0.1.91) confused more than they helped.
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
                    FreeStore.replaceAll(context, taken.free)
                    // The sender's key pairs the two devices: from now on they keep in step by themselves.
                    if (taken.pair.isNotEmpty()) Sync.setKey(context, taken.pair)
                    for (kind in listOf("live", "film", "show")) Choices.hidden(context, kind, taken.hidden[kind].orEmpty())
                    editing = false
                    if (taken.account == account) reload++ else account = taken.account
                },
            )
            (!account.isSet && !noProvider) || editing || addingProvider -> SetupScreen(
                current = if (addingProvider) Account("", "", "", "") else account,
                canCancel = account.isSet || noProvider,
                onNoProvider = if (account.isSet) null else ({ Choices.noProvider(context, true); editing = false }),
                onCancel = { editing = false; addingProvider = false },
                onCopy = { setUpShare = 2 },
                onSave = { next ->
                    AccountStore.save(context, next)
                    Providers.keep(context, next)
                    editing = false
                    addingProvider = false
                    if (next == account) reload++ else account = next
                },
            )
            // A newer version found on opening. "Not now" puts it off until the app is next started.
            Updates.offer != null -> UpdateOfferScreen(Updates.offer!!, onLater = {
                Updates.putOff = Updates.offer?.code ?: 0
                Updates.offer = null
            })
            driveSignIn -> DriveSignInScreen(onDone = { driveSignIn = false })
            stremioOpen -> StremioScreen(onDone = { stremioOpen = false })
            Vpn.screenOpen -> VpnScreen(onDone = { Vpn.screenOpen = false })
            addLinkOpen -> AddLinkScreen(onDone = { addLinkOpen = false })
            addOnsOpen -> AddOnsScreen(onDone = { addOnsOpen = false })
            reportOpen -> ReportScreen(onDone = { reportOpen = false })
            aboutOpen -> AboutScreen(onDone = { aboutOpen = false })
            freeOpen -> FreeListsScreen(
                free.size,
                onDone = { freeOpen = false },
                // Straight to Live TV, on the category holding every free channel.
                onWatch = {
                    freeOpen = false
                    browse.section = Section.LIVE
                    browse.liveCategory = Library.FREE_ALL
                },
            )
            healthOpen -> HealthScreen(account, library, onDone = { healthOpen = false })
            categoriesOpen && whole != null -> CategoriesScreen(whole, onDone = { categoriesOpen = false })
            problem != null -> ProblemScreen(problem.orEmpty(), onRetry = { reload++ }, onEdit = { editing = true })
            lib == null -> LoadingScreen()
            else -> androidx.compose.runtime.key(profileId) { MainScreen(
                extras = SettingsExtras(
                    switchProvider = { next ->
                        Providers.keep(context, account)
                        AccountStore.save(context, next)
                        if (next != account) account = next
                    },
                    addProvider = { Providers.keep(context, account); addingProvider = true },
                    backUp = { scope.launch { driveNote = "Backing up…"; driveNote = backUpNow() } },
                    restore = { scope.launch { driveNote = "Restoring…"; driveNote = restoreNow() } },
                    driveNote = driveNote,
                ),
                library = lib,
                state = browse,
                favourites = favourites,
                onPlay = { items, index, live, key ->
                    browse.resumeKey = key
                    // The small TV shown while browsing carries on with this channel, in its lightest version.
                    if (live) items.getOrNull(index)?.let {
                        val url = (it.streams.lastOrNull { s -> !s.label.startsWith("Free ") && !s.label.startsWith("Backup ") } ?: it.streams.lastOrNull())?.url ?: it.url
                        // An add-on's TV channel has no address of its own to show in the small picture.
                        if (AddOns.showable(url)) LastLive.item = Playable(it.name, url)
                    }
                    val item = items.getOrNull(index)
                    // Both an add-on's stream and the provider's own copy: the user picks. Only one of them: it plays.
                    if (!live && item != null && item.url.isEmpty() && item.find == null) {
                        // From a catalogue, with no copy at the provider and no add-on that gives streams.
                        kueToast(context, "Nothing to play this from yet. Add an add-on that gives streams, in Settings › Add-ons.")
                    } else if (!live && item?.find != null && item.url.isNotEmpty()) choice = Playing(items, index, false)
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
                onFree = { freeOpen = true },
                onStremio = { stremioOpen = true },
                onAddLink = { addLinkOpen = true },
            ) }
        }
        if (reminder != null) ReminderPopUp(reminder, onWatch = watchReminded, onClose = { Reminders.showing = null }, Modifier.align(Alignment.TopCenter))
        if (Aerial.offering) AerialPopUp(onWatch = watchAerial, onClose = { Aerial.offering = false }, Modifier.align(Alignment.TopCenter))
    }
}

/** The home internet gone: the TV's aerial channels offered, over whatever is on screen, at the top. */
@Composable
private fun AerialPopUp(onWatch: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(top = 28.dp, start = 12.dp, end = 12.dp).widthIn(max = 720.dp)
            .background(Looks.panel.copy(alpha = 0.95f), RoundedCornerShape(16.dp)).border(2.dp, Accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text("No internet", color = AccentInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Watch the TV's aerial channels?", color = Ink, fontSize = Sizes.ROW, fontWeight = FontWeight.Bold)
        Text("OK to watch  ·  Back to put this away", color = Soft, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
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
            .background(Looks.panel.copy(alpha = 0.95f), RoundedCornerShape(16.dp)).border(2.dp, Accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Text(
            (if (started) "Started at " else "Starting at ") + clock.format(java.util.Date(reminder.startMs)) + " on " + reminder.channel,
            color = AccentInk, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        Text(reminder.title, color = Ink, fontSize = Sizes.ROW, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (phone) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Watch", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Accent, RoundedCornerShape(22.dp)).clickable(onClick = onWatch).padding(horizontal = 18.dp, vertical = 9.dp),
                )
                Text(
                    "Not now", color = Ink, fontSize = 18.sp,
                    modifier = Modifier.background(Panel, RoundedCornerShape(22.dp)).clickable(onClick = onClose).padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        } else {
            Text("OK to watch  ·  Back to put this away", color = Soft, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * OK on a programme that hasn't started steps it along: a reminder, then set to record, then set to record every
 * time it is on that channel, then neither. Says on screen what it is now set to.
 */
private fun stepLater(context: android.content.Context, plan: Timed, remind: Reminder, reminded: Boolean, recording: Boolean, at: String, channelId: String = "") {
    val say: (String) -> Unit = { kueToast(context, it) }
    val linked = SeriesLinks.has(context, plan.channel, plan.title)
    when {
        linked -> {
            SeriesLinks.remove(context, plan.channel, plan.title)
            TimedStore.remove(context, plan)
            say("Nothing set for " + plan.title)
        }
        recording -> {
            SeriesLinks.add(context, SeriesLinks.Link(channelId, plan.channel, plan.url, plan.title))
            say("Set to record every showing of " + plan.title + " on " + plan.channel + ". OK again for nothing.")
        }
        reminded -> {
            Reminders.remove(context, remind)
            TimedStore.add(context, plan)
            say("Set to record: " + plan.title + ", " + at + ". OK again to record every showing.")
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
@OptIn(ExperimentalFoundationApi::class)
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
    textSize: androidx.compose.ui.unit.TextUnit = Sizes.ROW,
    /** The room above and below the words. The side menu uses less, so all of it fits on the screen. */
    padV: Dp = 12.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val phoneRow = LocalPhone.current
    val button = if (phoneRow) look else Look.ROW
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .focusRing(focused)
            .background(
                when {
                    focused || button == Look.MAIN -> Accent
                    chosen -> Looks.faint2
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
        // Only the main yellow button is centred; every other row lines up on the left, so a list of them is even.
        horizontalArrangement = if (button == Look.MAIN) Arrangement.Center else Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column {
            Text(
                text,
                color = if (focused || button == Look.MAIN) Color.Black else Ink,
                fontSize = textSize,
                fontWeight = if (focused || chosen || button == Look.MAIN) FontWeight.Bold else FontWeight.Normal,
                // A phone has no remote to scroll a long row along, so there the words run onto more lines instead.
                maxLines = if (phoneRow) 3 else 1,
                // Too long for the row: cut short with "…", and while the remote is on it, scrolls along to show the rest.
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200) else Modifier,
            )
            if (below != null) {
                Text(
                    // Dark on yellow, light on dark: the main button is yellow even when the remote isn't on it.
                    below, color = if (focused || button == Look.MAIN) Color(0xFF2B2B2B) else Soft, fontSize = Sizes.SUB, maxLines = if (phoneRow) 3 else 1,
                    overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_200) else Modifier,
                )
            }
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
        color = if (chosen) Color.Black else Ink,
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
    Text(UiLang.tr(text).uppercase(), color = Soft, fontSize = Sizes.HEADING, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AccentInk)
        Spacer(Modifier.height(22.dp))
        Text(
            "Loading your channels and films…", color = Ink, fontSize = Sizes.ROW,
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
        Text(message, color = Ink, fontSize = Sizes.ROW, modifier = Modifier.padding(start = 16.dp, bottom = 26.dp))
        FocusRow("Try again", onRetry, Modifier.focusRequester(first), look = Look.MAIN)
        Spacer(Modifier.height(8.dp))
        FocusRow("Change provider details", onEdit, look = Look.OTHER)
    }
}

// --------------------------------------------------------------------------------------------- setup

@Composable
private fun SetupScreen(current: Account, canCancel: Boolean, onCancel: () -> Unit, onCopy: () -> Unit, onSave: (Account) -> Unit, onNoProvider: (() -> Unit)? = null) {
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
        Text("Set up KueTV", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (phone) 8.dp else 26.dp))
        val address = phoneAddress
        if (address != null) {
            Text("Easiest: on your phone, open", color = Soft, fontSize = Sizes.BODY)
            Text(address, color = AccentInk, fontSize = Sizes.CODE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
            Text("and type your provider details there. Your phone must be on the same Wi-Fi as this TV.", color = Soft, fontSize = Sizes.BODY)
        } else {
            Text(if (phone) "Type your provider details in the boxes below." else "Type your provider details in the boxes on the right.", color = Soft, fontSize = Sizes.BODY)
        }
        warning?.let { Text(it, color = AccentInk, fontSize = Sizes.BODY, modifier = Modifier.padding(top = if (phone) 10.dp else 26.dp)) }
    }
    val form: @Composable () -> Unit = {
        // The keyboard's own button moves to the next box, and on the last one it saves.
        Field("Server address", server) { server = it }
        Field("Username (or MAC address, for a portal)", user) { user = it }
        Field("Password", pass) { pass = it }
        Field("Or one playlist link", playlist, onDone = { submit(server, user, pass, playlist) }) { playlist = it }
        Spacer(Modifier.height(6.dp))
        FocusRow("Save", { submit(server, user, pass, playlist) }, look = Look.MAIN)
        // Already set up on the TV or the phone: bring it all across instead of typing it again.
        FocusRow("Copy set-up from another device", onCopy, look = Look.OTHER)
        // No provider at all: free channel lists and add-ons can still be used.
        if (onNoProvider != null) FocusRow("Use without a provider", onNoProvider, look = Look.OTHER)
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
private fun Field(
    label: String,
    value: String,
    onDone: (() -> Unit)? = null,
    /** A password: shown as dots. */
    secret: Boolean = false,
    /** Put on the row a TV shows, so a screen can land the remote on it. */
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    val box: @Composable (Modifier, () -> Unit) -> Unit = { m, finish ->
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, fontSize = 16.sp) },
            singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                imeAction = if (onDone != null || !LocalPhone.current) ImeAction.Done else ImeAction.Next,
                keyboardType = if (secret) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
            ),
            keyboardActions = KeyboardActions(onDone = { finish(); onDone?.invoke() }),
            textStyle = MaterialTheme.typography.titleLarge,
            modifier = m.fillMaxWidth(),
        )
    }
    if (LocalPhone.current) {
        box(Modifier) {}
        return
    }
    // TV: a row showing what has been typed. OK opens the box and the keyboard; Done, Back, up or down closes it.
    var editing by remember { mutableStateOf(false) }
    val row = remember { FocusRequester() }
    val input = remember { FocusRequester() }
    val backToRow = remember { booleanArrayOf(false) }
    if (!editing) {
        FocusRow(
            label + ": " + when { value.isEmpty() -> "not typed yet"; secret -> "•".repeat(minOf(value.length, 12)); else -> value },
            { editing = true },
            modifier.focusRequester(row),
            look = Look.OTHER,
            below = "OK to type",
        )
        LaunchedEffect(Unit) {
            if (!backToRow[0]) return@LaunchedEffect
            backToRow[0] = false
            withFrameNanos { }
            runCatching { row.requestFocus() }
        }
    } else {
        val finish: () -> Unit = {
            backToRow[0] = true
            editing = false
        }
        box(
            Modifier.focusRequester(input).onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.Back, Key.DirectionUp, Key.DirectionDown -> { if (e.type == KeyEventType.KeyUp) finish(); true }
                    else -> false
                }
            },
            finish,
        )
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { input.requestFocus() }
        }
    }
}

// --------------------------------------------------------------------------------------------- main

private enum class Section(val label: String) { HOME("Home"), SEARCH("Search"), LIVE("Live TV"), GUIDE("TV Guide"), CATCHUP("Catch-up"), FILMS("Films"), SERIES("Series"), RECORDINGS("Recordings"), APPS("TV apps"), SETTINGS("Settings") }

private const val ALL = "\u0000all"
private const val FAVOURITES = "\u0000favourites"
/** Live TV's "Recently watched": the channels watched lately, newest first. */
private const val RECENT = "\u0000recent"

// "All" and "Favourites" are known inside the app by marks that can't be written to the device's
// storage as they are, so they are kept under these plain words instead.
private fun keptAs(category: String) = when (category) { ALL -> "*all"; FAVOURITES -> "*favourites"; else -> category }
private fun fromKept(kept: String?) = when (kept) { null, "*all" -> ALL; "*favourites" -> FAVOURITES; else -> kept }
@Composable
private fun holdHint() = if (LocalPhone.current) " · press and hold for favourite" else " · hold OK for favourite"

@Composable
private fun menuHint() = if (LocalPhone.current) " · press and hold for more" else " · hold OK for more"

/** Above the films or series: how they are sorted, and (films) whether those watched are hidden. OK changes each. */
@Composable
private fun ListingChips(watchedToo: Boolean) {
    val context = LocalContext.current
    val sort = remember(Listing.changes) { Listing.sort(context) }
    val hide = remember(Listing.changes) { Listing.hideWatched(context) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GuideChip("Sort: " + Listing.sorts[sort], false) { Listing.sort(context, (sort + 1) % Listing.sorts.size) }
        if (watchedToo) GuideChip(if (hide) "Watched: hidden" else "Watched: shown", false) { Listing.hideWatched(context, !hide) }
    }
}

/**
 * Hold OK on a film: what can be done with it, in a short list. The trailer row shows once the provider has said
 * there is one. Back, or any choice, closes it.
 */
@Composable
private fun TitleMenu(
    name: String,
    favourite: Boolean,
    watched: Boolean,
    trailer: String?,
    askTrailer: (suspend () -> String?)?,
    /** The provider's own copy, for "Play in another player"; empty when there is none. */
    playUrl: String,
    onPlay: () -> Unit,
    onFavourite: () -> Unit,
    onWatched: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val found by produceState(trailer, name) {
        if (value == null && askTrailer != null) value = withContext(Dispatchers.IO) { runCatching { askTrailer() }.getOrNull() }
    }
    val first = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        // The OK that opened this is still held down when it appears: its last repeats and its letting go are not
        // a choice. Only a fresh press counts.
        val armed = remember { booleanArrayOf(false) }
        Column(
            Modifier.width(460.dp).background(Color(0xFF0E1626), RoundedCornerShape(18.dp)).padding(14.dp)
                .onPreviewKeyEvent { e ->
                    if (armed[0]) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { armed[0] = true; false } else true
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            FocusRow("Play", { onClose(); onPlay() }, Modifier.focusRequester(first), look = Look.MAIN)
            FocusRow(if (favourite) "★ Remove from favourites" else "★ Add to favourites", { onFavourite(); onClose() }, look = Look.OTHER)
            FocusRow(if (watched) "Mark as not watched" else "✓ Mark as watched", { onWatched(); onClose() }, look = Look.OTHER)
            if (playUrl.isNotBlank()) {
                FocusRow("Play in another player", {
                    onClose()
                    if (!openElsewhere(context, playUrl, name)) kueToast(context, "There is no other video player on this device. VLC or MX Player can be installed from the app store.")
                }, look = Look.OTHER)
            }
            val t = found
            if (!t.isNullOrBlank()) {
                FocusRow("▶ Watch the trailer", {
                    onClose()
                    if (!openTrailer(context, t)) kueToast(context, "Nothing on this device can play YouTube trailers.")
                }, look = Look.OTHER)
            }
            FocusRow("Close", onClose, look = Look.OTHER)
        }
    }
}

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

    /** Set when ◀ in the player comes back to the Live TV list: it is scrolled to the channel that was playing. */
    var jumpLive = false

    /** What has been fetched of each catalogue so far, where each one's grid is scrolled to, and the ones fetched to the end. */
    val catItems = HashMap<String, androidx.compose.runtime.snapshots.SnapshotStateList<CatItem>>()
    val catGrids = HashMap<String, LazyGridState>()
    val catDone = HashSet<String>()

    /** Putting the favourite channels in order: on, and the channel picked up to move (null when none is). */
    var sorting by mutableStateOf(false)
    var picked by mutableStateOf<String?>(null)

    /** The part of Settings open: null for the list of parts. */
    var settingsPage by mutableStateOf<String?>(null)

    /** Picking channels to hide in Live TV: the rows picked so far; null when not picking. */
    var hiding by mutableStateOf<Set<String>?>(null)

    /** The channel the remote is on in Live TV, for the picture and programme details beside the list. */
    var liveOn by mutableStateOf<ChannelGroup?>(null)
}

/** The width of the picture column on the right of a TV screen: the same on every section, and on Home. */
private val SIDE_WIDTH = 300.dp

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

    /** Whether the section on screen has a categories column, so ▶ from the menu goes into it. */
    var catsShown by mutableStateOf(false)
}

/** The side columns on a TV; null on a phone, where nothing folds. */
private val LocalPanels = staticCompositionLocalOf<Panels?> { null }

/**
 * The bar along the top of a TV screen, as on Freely: KueTV's mark, then the sections by name, with Search and
 * Settings as pictures at the end. The section shown is lit; coming up into the bar lands on it. OK opens one.
 */
@Composable
private fun TopBar(section: Section, focus: FocusRequester, bar: BarReach, onPick: (Section) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KueLogo(26.dp, 18.sp)
        Spacer(Modifier.width(14.dp))
        Row(
            Modifier.weight(1f).focusProperties { onEnter = { runCatching { focus.requestFocus() } } }.focusGroup()
                .onFocusChanged { bar.inside = it.hasFocus }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pictures = listOf(Section.SEARCH, Section.SETTINGS)
            (Section.entries.filter { it !in pictures } + pictures).forEach { s ->
                if (s == Section.SEARCH) Spacer(Modifier.width(8.dp))
                BarItem(s, s == section, s in pictures, if (s == section) Modifier.focusRequester(focus) else Modifier, bar) { onPick(s) }
            }
        }
        // The time and the weather, at the right of the bar.
        val time by produceState(barClock()) {
            while (true) {
                value = barClock()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
        LaunchedEffect(time) { Weather.refresh() }
        Weather.now?.let { Text(it, color = Soft, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(start = 20.dp, end = 14.dp)) }
        Text(time, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** When the top bar can take the remote: plain values, read at the moment the remote moves. */
private class BarReach {
    var inside = false
    var up = false
    var placing = false
}

/** One section in the top bar: yellow with the remote on it, a dark pill when it is the one shown. */
@Composable
private fun BarItem(section: Section, chosen: Boolean, picture: Boolean, modifier: Modifier, bar: BarReach, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val ink = when {
        focused -> Color.Black
        chosen -> AccentInk
        else -> Ink
    }
    val name = UiLang.tr(section.label)
    Row(
        // Asked each time the remote moves: reachable from inside the bar, with ▲, or when KueTV puts the remote there.
        modifier.focusProperties { canFocus = bar.inside || bar.up || bar.placing }.clip(shape).focusRing(focused, shape)
            .background(if (focused) Accent else if (chosen) Panel else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .press(onClick, null)
            .then(if (picture) Modifier.semantics { contentDescription = name } else Modifier)
            .padding(horizontal = if (picture) 9.dp else 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (picture) SectionIcon(section, ink, Modifier.size(24.dp))
        else Text(section.label, color = ink, fontSize = 16.sp, fontWeight = if (chosen || focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

/** A folded side column: a thin edge. Moving onto it with the remote opens the column. */
@Composable
private fun PanelEdge(label: String, onLanded: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // The folded column's name runs down the edge, a letter to a line, so it says what opens there.
    Column(
        Modifier.width(28.dp).fillMaxHeight()
            .focusRing(focused, RoundedCornerShape(10.dp))
            .background(if (focused) Accent else Panel, RoundedCornerShape(10.dp))
            .onFocusChanged {
                if (it.isFocused && !focused) onLanded()
                focused = it.isFocused
            }
            .focusable()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (letter in label.uppercase()) {
            Text(letter.toString(), color = if (focused) Color.Black else Soft, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 1)
        }
    }
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
        Text("KueTV", color = Ink, fontSize = textSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Where the TV Guide (or Catch-up) is: the category, the channel opened, and any date-and-time step. */
private class ChannelPick {
    var category by mutableStateOf(ALL)

    /** The TV Guide's day: 0 today, 1 tomorrow, -1 yesterday. */
    var guideDay = 0
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
    onFree: () -> Unit,
    onStremio: () -> Unit,
    onAddLink: () -> Unit,
    extras: SettingsExtras = SettingsExtras(),
) {
    val context = LocalContext.current
    // The category in each section, kept for the next time the app opens.
    LaunchedEffect(state.liveCategory, state.filmCategory, state.showCategory) {
        Place.keep(context, mapOf("live" to keptAs(state.liveCategory), "film" to keptAs(state.filmCategory), "show" to keptAs(state.showCategory)))
    }
    // A category that has just been hidden can't stay the one being shown.
    LaunchedEffect(library) {
        if (state.liveCategory != ALL && state.liveCategory != FAVOURITES && state.liveCategory != RECENT && !state.liveCategory.startsWith(MyCategories.PREFIX) &&
            library.liveCategories.none { it.id == state.liveCategory }
        ) state.liveCategory = ALL
        // A catalogue isn't one of the provider's categories, and is left alone.
        if (state.filmCategory != ALL && state.filmCategory != FAVOURITES && !state.filmCategory.startsWith(Catalogue.PREFIX) && library.filmCategories.none { it.id == state.filmCategory }) state.filmCategory = ALL
        if (state.showCategory != ALL && state.showCategory != FAVOURITES && !state.showCategory.startsWith(Catalogue.PREFIX) && library.showCategories.none { it.id == state.showCategory }) state.showCategory = ALL
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
    // The lists of films and series to browse beyond the provider's own: the film index's, and each add-on's.
    val catalogues by produceState(AddOns.lastCatalogues, addOns) {
        value = withContext(Dispatchers.IO) { runCatching { AddOns.catalogues(addOns) }.getOrDefault(AddOns.lastCatalogues) }
    }
    val catCategory: (Catalogue) -> Category = { c -> Category(c.key, "More: " + c.name + if (c.from.isBlank()) "" else " · " + c.from) }
    val filmCatalogues = remember(catalogues) { catalogues.filter { it.type == "movie" } }
    val showCatalogues = remember(catalogues) { catalogues.filter { it.type == "series" } }
    // The provider's films by their plain titles, to tell whether it has a copy of a film found in a catalogue.
    val providerByTitle by produceState<Map<String, List<Film>>>(emptyMap(), library) {
        value = withContext(Dispatchers.Default) { library.films.groupBy { AddOns.titleKey(it.name) } }
    }
    /** OK on a film from a catalogue: with a copy at the provider too, the user gets the choice; otherwise the add-ons play it. */
    val playCat: (CatItem, String) -> Unit = { item, key ->
        val year = item.about.year.take(4).toIntOrNull()
        val mine = providerByTitle[AddOns.titleKey(item.name)]?.firstOrNull { f ->
            val theirs = AddOns.titleYear(f.name)
            theirs == null || year == null || kotlin.math.abs(theirs - year) <= 1
        }
        onPlay(listOf(Playable(item.name, mine?.url.orEmpty(), find = finder("movie", item.name, null, null), poster = item.poster)), 0, false, key)
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
    val bar = remember { BarReach() }
    val resume = remember { FocusRequester() }
    // The section shown before this one; null until the screen has been shown once.
    val before = remember { arrayOfNulls<Section>(1) }
    val shownOnce = remember { booleanArrayOf(false) }
    // The small TV, before anything has been played since KueTV opened: the channel watched last time.
    LaunchedEffect(library) {
        if (LastLive.item != null) return@LaunchedEffect
        Recent.pick(library.groups, Recent.all(context)) { g -> g.versions.map { it.channel.url } }.firstOrNull()
            ?.takeIf { AddOns.showable(it.lightest.url) }?.let { LastLive.item = Playable(it.name, it.lightest.url) }
    }
    // Live TV, Films and Series always open on Favourites (All when there are none), each list from its top.
    // Coming back from the player isn't opening a section, so it stays where it was.
    LaunchedEffect(section) {
        if (before[0] != section && !(!shownOnce[0] && state.resumeKey != null)) {
            when (section) {
                Section.LIVE -> {
                    state.liveCategory = if (library.groups.any { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }) FAVOURITES else ALL
                    launch { runCatching { state.liveCategoryList.scrollToItem(0) } }
                    launch { runCatching { state.channelList.scrollToItem(0) } }
                }
                Section.FILMS -> {
                    state.filmCategory = if (library.films.any { Favourites.key(it, login) in favs }) FAVOURITES else ALL
                    launch { runCatching { state.filmCategoryList.scrollToItem(0) } }
                    launch { runCatching { state.filmGrid.scrollToItem(0) } }
                }
                Section.SERIES -> if (state.openShow == null) {
                    state.showCategory = if (library.shows.any { Favourites.key(it) in favs }) FAVOURITES else ALL
                    launch { runCatching { state.showCategoryList.scrollToItem(0) } }
                    launch { runCatching { state.showGrid.scrollToItem(0) } }
                }
                else -> Unit
            }
        }
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
                // Onto the section's name in the bar along the top.
                panels.zone = Zone.MENU
                withFrameNanos { }
                withFrameNanos { }
                bar.placing = true
                runCatching { menuFocus.requestFocus() }
                bar.placing = false
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
        // The remote is in the menu now: say so before the "opening" mark goes, or for a moment the menu
        // counts as shut, is taken away with the remote on it, and the remote is lost.
        panels.zone = Zone.MENU
        panels.menuWanted = false
    }
    // Back from any section goes to Home. A page open inside a section (a channel's guide, a series) closes first.
    // As the TV's home screen, Back on Home has nowhere to go: KueTV stays.
    val isHome = remember { Launcher.isHome(context) }
    BackHandler(enabled = section == Section.HOME && isHome) { }
    BackHandler(enabled = section != Section.HOME) {
        state.cameFrom = section
        section = Section.HOME
    }
    // A film or episode from "Carry on watching": a film is found again in the lists, so its add-ons are asked as before.
    val carryOn: (Positions.Watching) -> Unit = { w ->
        val film = library.films.firstOrNull { it.url == w.url }
        val episode = Regex("^(.*) · S(\\d+) E(\\d+)$").find(w.name)
        val item = when {
            film != null -> Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster)
            // From a catalogue, with no address of its own: the add-ons are asked again, by its name.
            w.url.startsWith(Playable.NO_ADDRESS) && episode != null ->
                Playable(w.name, "", find = finder("series", episode.groupValues[1], episode.groupValues[2].toIntOrNull(), episode.groupValues[3].toIntOrNull()), poster = w.poster)
            w.url.startsWith(Playable.NO_ADDRESS) -> Playable(w.name, "", find = finder("movie", w.name, null, null), poster = w.poster)
            else -> Playable(w.name, w.url, poster = w.poster)
        }
        onPlay(listOf(item), 0, false, "carry:" + w.url)
    }
    val openFromHome: (Section) -> Unit = { s ->
        state.cameFrom = s
        section = s
    }

    val phone = LocalPhone.current
    // The chosen section's screen. Where it sits is decided underneath, by TV or phone.
    // A spare button on the remote asked for a part of KueTV.
    LaunchedEffect(SpareKeys.asked) {
        val asked = SpareKeys.asked ?: return@LaunchedEffect
        SpareKeys.asked = null
        Section.entries.firstOrNull { it.label == asked }?.let { section = it }
    }
    val body: @Composable Pane.() -> Unit = {
        when (section) {
            Section.HOME -> HomeScreen(
                library, state, favourites, homeFocus, resume, onPlay, openFromHome, carryOn, fill(),
                onFilm = { film -> onPlay(listOf(Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster)), 0, false, "homefilm:" + film.id) },
                onShow = { show ->
                    state.openShow = show
                    section = Section.SERIES
                },
            )
            Section.LIVE -> {
                var menuChannel by remember { mutableStateOf<ChannelGroup?>(null) }
                val profileNow = Profiles.changes
                val mine = remember(MyCategories.changes, profileNow) { MyCategories.all(context) }
                // Grouped (the usual): a handful of topics, free channels in with the rest. Otherwise the provider's own list.
                val grouped = remember(Choices.changes) { Choices.groupedLive(context) }
                val categoryTopic = remember(library) { Topics.byCategory(library.liveCategories) }
                val topicCats = remember(library, categoryTopic) { Topics.categories(categoryTopic, library.channels.mapTo(HashSet()) { it.categoryId }) }
                val columnCats = if (grouped) topicCats else library.liveCategories
                // For checking the topics from a computer: each category's name, its topic and how many channels it has.
                LaunchedEffect(library) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val counts = library.channels.groupingBy { it.categoryId }.eachCount()
                            val text = library.liveCategories.joinToString("\n") { c -> (counts[c.id] ?: 0).toString() + "\t" + categoryTopic[c.id] + "\t" + c.id + "\t" + c.name }
                            java.io.File(context.getExternalFilesDir(null), "live-categories.txt").writeText(text)
                        }
                    }
                }
                Categories(listOf(Category(RECENT, "🕘 Recently watched")) + mine.map { Category(it.id, "📁 " + it.name) } + columnCats, liveCategory, state.liveCategoryList) {
                    if (it != state.liveCategory) {
                        state.liveCategory = it
                        scope.launch { state.channelList.scrollToItem(0) }
                    }
                }
                // One row for each channel: its 4K, FHD, HD and SD versions are kept together behind it.
                val recentNow = if (liveCategory == RECENT) Recent.changes else -1
                val shown = remember(library, liveCategory, favs, favourites.order, recentNow, mine, categoryTopic) {
                    val isFav: (ChannelGroup) -> Boolean = { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }
                    val favRank: (ChannelGroup) -> Int = { g -> favourites.rank(g.versions.map { Favourites.key(it.channel, login) }) }
                    when {
                        // One of the user's own categories: its channels in the order they were added.
                        liveCategory.startsWith(MyCategories.PREFIX) -> mine.firstOrNull { it.id == liveCategory }?.channels.orEmpty().mapNotNull { id -> library.groups.firstOrNull { it.id == id } }
                        // A topic: every channel filed under a category of that topic, the provider's first, then free ones.
                        liveCategory.startsWith(Topics.PREFIX) -> {
                            val topic = liveCategory.removePrefix(Topics.PREFIX)
                            library.groups.filter { g -> g.versions.any { categoryTopic[it.channel.categoryId] == topic } }
                                .sortedBy { g -> if (g.versions.all { it.free }) 1 else 0 }
                        }
                        else -> when (liveCategory) {
                        ALL -> library.groups
                        RECENT -> Recent.pick(library.groups, Recent.all(context)) { g -> g.versions.map { it.channel.url } }
                        // Favourites in the user's own order.
                        FAVOURITES -> library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }
                            .sortedBy { g -> favourites.rank(g.versions.map { Favourites.key(it.channel, login) }) }
                        Library.FREE_ALL -> library.groups.filter { g -> g.versions.any { it.free } }
                        else -> library.groups.filter { g -> g.versions.any { it.channel.categoryId == liveCategory } }
                        }
                    }.let { list ->
                        // Every other category: its favourite channels first, in the order set in Favourites, then the rest.
                        if (liveCategory == FAVOURITES || liveCategory == RECENT || liveCategory.startsWith(MyCategories.PREFIX)) list
                        else list.partition(isFav).let { (fav, rest) -> fav.sortedBy(favRank) + rest }
                    }
                }
                val playables = remember(shown) { shown.map { livePlayable(it, library.account, library.serverAheadSec) } }
                // Come from the player with ◀: the list moves to the channel that was playing, and the remote lands on it.
                LaunchedEffect(shown) {
                    if (!state.jumpLive) return@LaunchedEffect
                    state.jumpLive = false
                    val at = shown.indexOfFirst { "live:" + it.id == state.resumeKey }
                    if (at < 0) return@LaunchedEffect
                    runCatching { state.channelList.scrollToItem((at - 3).coerceAtLeast(0)) }
                    withFrameNanos { }
                    withFrameNanos { }
                    runCatching { resume.requestFocus() }
                }
                val list: @Composable (Modifier) -> Unit = { m ->
                    // Favourites can be put in the user's own order: pick one up, move it, put it down.
                    val sortable = liveCategory == FAVOURITES && shown.size > 1
                    val sorting = sortable && state.sorting
                    BackHandler(enabled = sorting) { state.sorting = false; state.picked = null }
                    // Picking channels to hide: Back stops picking, hiding nothing.
                    val hiding = state.hiding
                    BackHandler(enabled = hiding != null) { state.hiding = null }
                    val hideNow: () -> Unit = {
                        val picked = state.hiding.orEmpty()
                        val ids = library.groups.filter { it.id in picked }.flatMap { g -> g.versions.map { it.channel.id } }
                        HiddenChannels.hide(context, ids)
                        state.hiding = null
                        kueToast(context, "Hidden " + picked.size + (if (picked.size == 1) " channel" else " channels") + ". Settings › Categories › Hidden channels brings them back.")
                    }
                    /** Moves the picked channel to [to] in the list, and keeps that as the order. */
                    val moveTo: (Int) -> Unit = { to ->
                        val from = shown.indexOfFirst { it.id == state.picked }
                        if (from >= 0 && to in shown.indices && to != from) {
                            val list = shown.toMutableList()
                            list.add(to, list.removeAt(from))
                            favourites.putInOrder(list.map { Favourites.key(it.versions[0].channel, login) })
                        }
                    }
                    // Hold OK on a channel: what can be done with it, in a short list.
                    menuChannel?.let { ch ->
                        ChannelMenu(ch, login, favourites, mine, onClose = { menuChannel = null }, onPickToHide = { state.hiding = setOf(ch.id) })
                    }
                    // A channel number typed on the remote's number keys: a moment after the last key, the remote goes to that row.
                    var typed by remember { mutableStateOf("") }
                    var jumpTo by remember { mutableIntStateOf(-1) }
                    val jumpFocus = remember { FocusRequester() }
                    LaunchedEffect(typed) {
                        if (typed.isEmpty()) return@LaunchedEffect
                        delay(1_500)
                        val n = typed.toIntOrNull() ?: 0
                        typed = ""
                        if (n in 1..shown.size) {
                            state.channelList.scrollToItem((n - 3).coerceAtLeast(0))
                            jumpTo = n - 1
                            withFrameNanos { }
                            withFrameNanos { }
                            runCatching { jumpFocus.requestFocus() }
                        } else {
                            kueToast(context, "There is no channel $n in this list. It has ${shown.size}.", long = false)
                        }
                    }
                    Column(m) {
                        Heading(
                            when {
                                typed.isNotEmpty() -> "Channel $typed…"
                                hiding != null -> hiding.size.toString() + " picked. " + (if (phone) "Tap" else "OK on") + " a channel to pick it or not. Back to stop."
                                sorting && state.picked != null -> "Moving. " + if (phone) "Tap where it should go." else "▲ ▼ to move it, OK to put it down."
                                sorting -> (if (phone) "Tap a channel to pick it up." else "OK on a channel to pick it up.") + " Back when done."
                                else -> (if (shown.size == 1) "1 channel" else "${shown.size} channels") + menuHint()
                            },
                        )
                        if (hiding != null) {
                            FocusRow("Hide the " + hiding.size + " picked", hideNow, look = Look.MAIN)
                        }
                        if (sortable && hiding == null) {
                            FocusRow(
                                if (sorting) "Done sorting" else "Sort my favourites",
                                { state.sorting = !state.sorting; state.picked = null },
                                look = if (sorting) Look.MAIN else Look.OTHER,
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxSize().focusRestorer().onPreviewKeyEvent { e ->
                                // The number keys: a channel by its place in the list.
                                val digit = digitOf(e.nativeKeyEvent.keyCode)
                                if (digit >= 0 && !sorting) {
                                    if (e.type == KeyEventType.KeyDown && typed.length < 4) typed += digit
                                    return@onPreviewKeyEvent true
                                }
                                // A picked-up channel moves with ▲ ▼, and the remote stays on it.
                                if (!sorting || state.picked == null) return@onPreviewKeyEvent false
                                if (e.key != Key.DirectionUp && e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                                if (e.type == KeyEventType.KeyDown) {
                                    val from = shown.indexOfFirst { it.id == state.picked }
                                    val to = from + if (e.key == Key.DirectionUp) -1 else 1
                                    moveTo(to)
                                    if (to in shown.indices && state.channelList.layoutInfo.visibleItemsInfo.let { v -> v.firstOrNull()?.index ?: 0 >= to || (v.lastOrNull()?.index ?: 0) <= to }) {
                                        scope.launch { state.channelList.scrollToItem((to - 2).coerceAtLeast(0)) }
                                    }
                                }
                                true
                            },
                            state = state.channelList,
                        ) {
                            itemsIndexed(shown, key = { _, c -> c.id }) { i, channel ->
                                val key = "live:" + channel.id
                                val favKeys = channel.versions.map { Favourites.key(it.channel, login) }
                                val isPicked = sorting && state.picked == channel.id
                                val toHide = hiding != null && channel.id in hiding
                                FocusRow(
                                    (if (hiding != null) (if (toHide) "☑ " else "☐ ") else if (isPicked) "↕ " else if (favKeys.any { it in favs }) "★ " else "") + channel.name,
                                    {
                                        when {
                                            hiding != null -> state.hiding = if (toHide) hiding - channel.id else hiding + channel.id
                                            !sorting -> onPlay(playables, i, true, key)
                                            state.picked == null -> state.picked = channel.id
                                            state.picked == channel.id -> state.picked = null
                                            // Phone: tapping another channel puts the picked one there.
                                            else -> { moveTo(i); state.picked = null }
                                        }
                                    },
                                    (if (key == resumeKey) Modifier.focusRequester(resume) else Modifier).then(if (i == jumpTo) Modifier.focusRequester(jumpFocus) else Modifier),
                                    chosen = isPicked || toHide,
                                    onFocus = { state.liveOn = channel },
                                    onHold = if (sorting || hiding != null) null else ({ menuChannel = channel }),
                                    leading = { Logo(channel.logo) },
                                    below = nowLine(library.account, channel.versions[channel.start].channel.id, channel.name),
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
                        // With the remote off the list (in the categories), the details are of the channel playing.
                        val playingGroup = remember(LastLive.item, library) { LastLive.item?.let { l -> library.groups.firstOrNull { g -> g.versions.any { it.channel.url == l.url } } ?: library.groups.firstOrNull { it.name == l.name } } }
                        LivePreview(state.liveOn ?: playingGroup, library.account, Modifier.width(SIDE_WIDTH).fillMaxHeight())
                    }
                }
            }
            Section.FILMS -> {
                Categories(library.filmCategories + filmCatalogues.map(catCategory), filmCategory, state.filmCategoryList) {
                    if (it != state.filmCategory) {
                        state.filmCategory = it
                        scope.launch { state.filmGrid.scrollToItem(0) }
                    }
                }
                val filmCatalogue = filmCatalogues.firstOrNull { it.key == filmCategory }
                if (filmCatalogue != null) CatalogueGrid(filmCatalogue, state, resume, playCat, fill())
                else {
                val sort = remember(Listing.changes) { Listing.sort(context) }
                val hideWatched = remember(Listing.changes) { Listing.hideWatched(context) }
                val watchedNow = Watched.changes
                val shown = remember(library, filmCategory, if (filmCategory == FAVOURITES) favs else null, sort, hideWatched, if (hideWatched) watchedNow else 0) {
                    val inCategory = when (filmCategory) {
                        ALL -> library.films
                        FAVOURITES -> library.films.filter { Favourites.key(it, login) in favs }
                        else -> library.films.filter { it.categoryId == filmCategory }
                    }
                    Listing.sorted(if (hideWatched) inCategory.filter { !Watched.isWatched(context, it.url) } else inCategory, sort, { it.name }, { it.added })
                }
                // Hold OK on a film: a short list of what can be done with it.
                var menuFilm by remember { mutableStateOf<Film?>(null) }
                menuFilm?.let { film ->
                    val favKey = Favourites.key(film, login)
                    TitleMenu(
                        film.name,
                        favourite = favKey in favs,
                        watched = Watched.isWatched(context, film.url),
                        trailer = state.filmAbout[film.id]?.trailer,
                        askTrailer = library.account?.let { account -> suspend { Provider.filmAbout(account, film.id).also { state.filmAbout[film.id] = it }.trailer } },
                        playUrl = film.url,
                        onPlay = { onPlay(listOf(Playable(film.name, film.url, find = finder("movie", film.name, null, null), poster = film.poster)), 0, false, "film:" + film.id) },
                        onFavourite = { favourites.toggle(favKey) },
                        onWatched = { Watched.set(context, film.url, !Watched.isWatched(context, film.url)) },
                        onClose = { menuFilm = null },
                    )
                }
                Column(fill()) {
                    Heading((if (shown.size == 1) "1 film" else "${shown.size} films") + menuHint())
                    ListingChips(watchedToo = true)
                    val onFilm = state.onFilm
                    val account = library.account
                    if (!phone) {
                        // What the film the remote is on is about, asked for once it has been stayed on for a moment.
                        val about by keyedState(onFilm?.id, { onFilm?.let { state.filmAbout[it.id] } }) {
                            if (value != null || onFilm == null || account == null) return@keyedState
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
                                onHold = { menuFilm = film },
                                onFocus = { state.onFilm = film },
                                watched = watchedNow >= 0 && Watched.isWatched(context, film.url),
                                progress = Positions.changes.let { Positions.progress(context, film.url) },
                            )
                        }
                    }
                }
            }
            }
            Section.SERIES -> SeriesPane(library, state, favourites, resume, finder, onPlay, showCatalogues, catCategory)
            Section.GUIDE -> ChannelGuide(library, false, state.guide, state, favs, resume, onPlay, favourites)
            Section.CATCHUP -> ChannelGuide(library, true, state.catchUp, state, favs, resume, onPlay, favourites)
            Section.SEARCH -> SearchPane(library, state, resume, finder, onPlay, playCat) { show ->
                state.openShow = show
                section = Section.SERIES
            }
            Section.RECORDINGS -> RecordingsPane(state, resume, onPlay)
            Section.APPS -> TvAppsPane()
            Section.SETTINGS -> {
                // Settings is split into parts. The first page lists the parts; OK opens one; Back returns to the list.
                val page = state.settingsPage
                val pageFirst = remember { FocusRequester() }
                val backTo = remember { FocusRequester() }
                val leftFrom = remember { arrayOfNulls<String>(1) }
                LaunchedEffect(page) {
                    if (phone) return@LaunchedEffect
                    withFrameNanos { }
                    withFrameNanos { }
                    if (page != null) runCatching { pageFirst.requestFocus() } else if (leftFrom[0] != null) runCatching { backTo.requestFocus() }
                }
                val close: () -> Unit = {
                    leftFrom[0] = state.settingsPage
                    state.settingsPage = null
                }
                BackHandler(enabled = page != null) { close() }
                val drive = remember(Recordings.changes) { Recordings.drive(context) }
                val notWorking = Health.results.count { it.state == Check.DEAD }
                val freeCount = remember(FreeStore.changes) { FreeStore.all(context).size }
                val paired = remember(Sync.changes) { Sync.key(context).isNotEmpty() }
                val savedProviders = remember(Providers.changes) { Providers.all(context) }
                val profile = remember(Profiles.changes) { Profiles.current(context) }
                val parts = listOf(
                    "Your things" to "History, profiles · using " + profile.picture + (if (profile.picture.isEmpty()) "" else " ") + profile.name + if (profile.kids) " (kids)" else "",
                    "Favourites" to "Every channel, film and series you have starred",
                    "Your provider" to "Provider details" + (if (savedProviders.size > 1) " (" + savedProviders.size + " saved)" else "") + ", reload, categories, free channel lists ($freeCount)",
                    "Films and series" to "Add-ons (" + addOns.size + "), Stremio account",
                    "Watching" to "Subtitles, pause and rewind, sound and picture, text size",
                    "Looks" to UiLang.name(UiLang.code) + " · " + Looks.themes[Looks.theme] + " · " + Looks.accents[Looks.accent] + if (Looks.glass > 0) " · Glass look" else "",
                    "Google Drive" to when { !drive.isSetUp -> "Not set up"; drive.signedIn -> "Signed in · backup and recordings"; else -> "Not signed in · backup and recordings" },
                    "TV and phone" to "Copy the set-up, keep in step: " + if (paired) "on" else "off",
                    "VPN" to remember(Vpn.changes, Vpn.level) { vpnLine(context) },
                    *(if (phone) emptyArray() else arrayOf("This TV" to "The TV's own settings: network, display, sound, apps, developer options")),
                    "Help" to "Health check" + (if (notWorking == 0) "" else ": $notWorking not working") + ", report, updates · version " + BuildConfig.VERSION_NAME,
                )
                Column(
                    // The rows can run past the bottom of a TV screen, so the list scrolls as the remote moves down it.
                    fill().verticalScroll(rememberScrollState()),
                    verticalArrangement = if (phone) Arrangement.spacedBy(8.dp) else Arrangement.Top,
                ) {
                    if (page == null) {
                        Heading("Settings")
                        parts.forEach { (name, summary) ->
                            FocusRow(
                                // The VPN has a page of its own, opened straight away.
                                name, { if (name == "VPN") Vpn.screenOpen = true else { leftFrom[0] = null; state.settingsPage = name } },
                                if (name == leftFrom[0]) Modifier.focusRequester(backTo) else Modifier,
                                look = Look.OTHER, below = summary,
                            )
                        }
                    } else {
                    Heading("Settings › $page")
                    // The remote lands on the page's first row.
                    var given = false
                    val first: () -> Modifier = { if (given) Modifier else { given = true; Modifier.focusRequester(pageFirst) } }
                    val sub: @Composable (String) -> Unit = { Box(Modifier.padding(top = 14.dp)) { Heading(it) } }
                    when (page) {
                        "Favourites" -> {
                            val favChannels = library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }
                                .sortedBy { g -> favourites.rank(g.versions.map { Favourites.key(it.channel, login) }) }
                            val favFilms = library.films.filter { Favourites.key(it, login) in favs }
                            val favShows = library.shows.filter { Favourites.key(it) in favs }
                            if (favChannels.isEmpty() && favFilms.isEmpty() && favShows.isEmpty()) Note("Nothing starred yet. Hold OK on a channel, film or series to star it.")
                            var given = false
                            val firstOne: () -> Modifier = { if (given) Modifier else { given = true; first() } }
                            if (favChannels.isNotEmpty()) sub("Channels")
                            val livePlayables = remember(favChannels) { favChannels.map { livePlayable(it, library.account, library.serverAheadSec) } }
                            favChannels.forEachIndexed { i, g ->
                                FocusRow(g.name, { onPlay(livePlayables, i, true, "fav:" + g.id) }, firstOne(), look = Look.OTHER, leading = { Logo(g.logo) },
                                    below = "Hold OK to take off", onHold = { favourites.toggleAll(g.versions.map { Favourites.key(it.channel, login) }) })
                            }
                            if (favFilms.isNotEmpty()) sub("Films")
                            for (f in favFilms) {
                                FocusRow(f.name, { onPlay(listOf(Playable(f.name, f.url, find = finder("movie", f.name, null, null), poster = f.poster)), 0, false, "fav:" + f.id) }, firstOne(),
                                    look = Look.OTHER, below = "Hold OK to take off", onHold = { favourites.toggle(Favourites.key(f, login)) })
                            }
                            if (favShows.isNotEmpty()) sub("Series")
                            for (sh in favShows) {
                                FocusRow(sh.name, { state.openShow = sh; section = Section.SERIES }, firstOne(),
                                    look = Look.OTHER, below = "Hold OK to take off", onHold = { favourites.toggle(Favourites.key(sh)) })
                            }
                        }
                        "Your things" -> YourThingsPage(first, sub) { e ->
                            when (e.kind) {
                                History.LIVE -> {
                                    val group = library.groups.firstOrNull { g -> g.versions.any { it.channel.url == e.key } }
                                    if (group != null) onPlay(listOf(livePlayable(group, library.account, library.serverAheadSec)), 0, true, "history:" + e.key)
                                    else kueToast(context, "That channel isn't in your provider's list any more.")
                                }
                                History.FILM -> carryOn(Positions.Watching(e.key, e.name, e.poster, 0, 0, e.atMs))
                                else -> onPlay(listOf(Playable(e.name, e.key)), 0, false, "history:" + e.key)
                            }
                        }
                        "Your provider" -> {
                            // Other providers used before: OK switches the whole app to one.
                            val inUse = library.account ?: AccountStore.load(context)
                            if (savedProviders.size > 1) {
                                sub("Switch provider")
                                for (a in savedProviders) {
                                    val using = a == inUse
                                    FocusRow(
                                        (if (using) "● " else "") + Providers.label(a), { if (!using) extras.switchProvider(a) },
                                        look = Look.OTHER, below = if (using) "In use" else "OK to switch to this one · hold OK to take it off the list",
                                        onHold = if (using) null else ({ Providers.remove(context, a) }),
                                    )
                                }
                                sub("Also in your lists")
                                for (a in savedProviders) {
                                    if (a == inUse) continue
                                    val on = remember(Combined.changes) { Combined.isOn(context, a) }
                                    FocusRow(
                                        Providers.label(a) + ": " + if (on) "on" else "off", { Combined.setOn(context, a, !on) },
                                        look = Look.OTHER, below = "Its channels, films and series shown with these ones · OK to change",
                                    )
                                }
                                sub("This provider")
                            }
                            FocusRow("Change provider details", onEdit, first(), look = Look.OTHER)
                            FocusRow("Add another provider", extras.addProvider, look = Look.OTHER, below = "The one in use is kept, to switch back to")
                            FocusRow("Reload channels and films", onReload, look = Look.OTHER, below = "Fetches everything afresh from your provider")
                            val dns = remember(Choices.changes) { KueDns.chosen }
                            FocusRow(
                                "DNS server: " + KueDns.names[dns], { KueDns.set(context, (dns + 1) % KueDns.names.size) }, look = Look.OTHER,
                                below = "If your internet blocks your provider, try another · OK to change",
                            )
                            val hiddenCount = remember(Choices.changes) { listOf("live", "film", "show").sumOf { Choices.hidden(context, it).size } }
                            FocusRow("Categories: show, hide and order" + if (hiddenCount == 0) "" else " ($hiddenCount hidden)", onCategories, look = Look.OTHER)
                            sub("Backup")
                            FocusRow("Free channel lists ($freeCount)", onFree, look = Look.OTHER, below = "Free channels, tried when your provider's won't play")
                            sub("TV guide")
                            val offset = remember(Choices.changes) { GuideOffset.minutes(context) }
                            FocusRow(
                                "Guide times: " + GuideOffset.words(offset), {
                                    val i = GuideOffset.choices.indexOf(offset)
                                    GuideOffset.set(context, GuideOffset.choices[(i + 1) % GuideOffset.choices.size])
                                }, look = Look.OTHER, below = "For a guide that is out by an hour or more · OK to change",
                            )
                            sub("Free TV guides")
                            val guidesNow = FreeGuides.changes
                            for (g in FreeGuides.sources) {
                                val on = guidesNow >= 0 && FreeGuides.isOn(context, g.id)
                                FocusRow(g.name + ": " + if (on) "on" else "off", { FreeGuides.setOn(context, g.id, !on) }, look = Look.OTHER)
                            }
                            Note(
                                "Used for channels your provider has no guide for, and for free channels. " +
                                    if (FreeGuides.lastFetchedMs(context) == 0L) "Not read yet." else FreeGuides.matched.toString() + " of your channels have one.",
                            )
                        }
                        "Films and series" -> {
                            // Three jobs, three screens.
                            val stremioEmail = remember(Stremio.changes) { if (Stremio.key(context).isEmpty()) "" else Stremio.email(context) }
                            FocusRow("Stremio account", onStremio, first(), look = Look.OTHER, below = if (stremioEmail.isEmpty()) "Not signed in" else "Signed in as $stremioEmail")
                            FocusRow("My add-ons (" + addOns.size + ")", onAddOns, look = Look.OTHER, below = addOnsSummary(addOns))
                            FocusRow("Add an add-on or channel list by its link", onAddLink, look = Look.OTHER)
                        }
                        "Watching" -> {
                            sub("Subtitles")
                            val subtitlesOn = remember(Choices.changes) { Choices.subtitles(context) }
                            FocusRow("Subtitles: " + if (subtitlesOn) "on" else "off", { Choices.subtitles(context, !subtitlesOn) }, first(), look = Look.OTHER)
                            FocusRow("Subtitle language: " + subtitleLang.name, { Choices.subtitleLang(context, Langs.after(subtitleLang).code) }, look = Look.OTHER)
                            val look = Choices.changes
                            val sSize = if (look >= 0) SubtitleLook.size(context) else 0
                            val sColour = SubtitleLook.colour(context)
                            val sBack = SubtitleLook.background(context)
                            val sHeight = SubtitleLook.height(context)
                            FocusRow("Subtitle size: " + SubtitleLook.sizes[sSize], { SubtitleLook.set(context, "size", (sSize + 1) % SubtitleLook.sizes.size) }, look = Look.OTHER)
                            FocusRow("Subtitle colour: " + SubtitleLook.colours[sColour], { SubtitleLook.set(context, "colour", (sColour + 1) % SubtitleLook.colours.size) }, look = Look.OTHER)
                            FocusRow("Subtitle background: " + SubtitleLook.backgrounds[sBack], { SubtitleLook.set(context, "background", (sBack + 1) % SubtitleLook.backgrounds.size) }, look = Look.OTHER)
                            FocusRow("Subtitle position: " + SubtitleLook.heights[sHeight], { SubtitleLook.set(context, "height", (sHeight + 1) % SubtitleLook.heights.size) }, look = Look.OTHER)
                            sub("Live TV")
                            // When KueTV opens: Home, the channel watched last, or one of the favourite channels.
                            val openOn = remember(Choices.changes) { OpenOn.get(context) }
                            val favChannels = remember(library, favs) { library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, true) in favs } } }
                            val openChoices = listOf("", "last") + favChannels.map { it.id }
                            FocusRow(
                                "When KueTV opens: " + when (openOn) { "" -> "Home"; "last" -> "the channel watched last"; else -> library.groups.firstOrNull { it.id == openOn }?.name ?: "Home" },
                                { OpenOn.set(context, openChoices[(openChoices.indexOf(openOn) + 1) % openChoices.size]) },
                                look = Look.OTHER, below = "OK to change: Home, the channel watched last, or one of your favourite channels",
                            )
                            val shiftOn = remember(Choices.changes) { Choices.timeShift(context) }
                            FocusRow("Pause and rewind live TV: " + if (shiftOn) "on" else "off", { Choices.timeShift(context, !shiftOn) }, look = Look.OTHER)
                            if (!phone) {
                                val smallTv = remember(Choices.changes) { Choices.smallTv(context) }
                                FocusRow("Small TV while browsing: " + if (smallTv) "on" else "off", { Choices.smallTv(context, !smallTv) }, look = Look.OTHER)
                                val sounds = remember(Choices.changes) { UiSounds.on(context) }
                                FocusRow("Sounds as you move around: " + if (sounds) "on" else "off", { UiSounds.on(context, !sounds) }, look = Look.OTHER, below = "Quiet ticks for moving, OK and Back. Never over a programme")
                            }
                            sub("The remote's spare buttons")
                            val spareNow = Choices.changes
                            for (k in SpareKeys.keys) {
                                FocusRow(k.name + ": " + (if (spareNow >= 0) SpareKeys.action(context, k.code) else ""), { SpareKeys.next(context, k.code) }, look = Look.OTHER, below = "OK to change · only on remotes that have it")
                            }
                            sub("Sound and picture")
                            val engineNow = Choices.changes
                            val stereo = engineNow >= 0 && Sound.stereoOnly(context)
                            FocusRow(
                                "Surround sound: " + if (stereo) "off, stereo only" else "on, when your TV or speakers can", { Sound.stereoOnly(context, !stereo) }, look = Look.OTHER,
                                below = "Dolby and DTS sent on to your TV or sound bar, or made into stereo here",
                            )
                            val night = engineNow >= 0 && Sound.night(context)
                            FocusRow("Night mode: " + if (night) "on" else "off", { Sound.night(context, !night) }, look = Look.OTHER, below = "Loud bangs quieter, speech louder · from the next thing you play")
                            val quality = if (engineNow >= 0) PictureQuality.get(context) else 0
                            FocusRow(
                                "Picture quality: " + PictureQuality.names[quality], { PictureQuality.set(context, (quality + 1) % PictureQuality.names.size) }, look = Look.OTHER,
                                below = "Less detail uses less internet, for a slow connection · OK to change",
                            )
                            val engine = if (engineNow >= 0) PlayerEngine.get(context) else 0
                            FocusRow(
                                "Player: " + PlayerEngine.names[engine], { PlayerEngine.set(context, (engine + 1) % PlayerEngine.names.size) }, look = Look.OTHER,
                                below = "VLC plays some streams the standard one can't, but without pause and rewind, recording or add-ons",
                            )
                            if (!phone) {
                                val matchRate = engineNow >= 0 && FrameRate.on(context)
                                FocusRow(
                                    "Match the TV to the film: " + if (matchRate) "on" else "off", { FrameRate.on(context, !matchRate) }, look = Look.OTHER,
                                    below = "Smoother movement. The screen may go dark for a second as each one starts",
                                )
                            }
                            sub("Screen")
                            val textSize = remember(Choices.changes) { Choices.textSize(context) }
                            FocusRow("Text size: " + Choices.textSizeNames[textSize], { Choices.textSize(context, (textSize + 1) % 3) }, look = Look.OTHER)
                            FocusRow("Reset text size, subtitles and small TV", { Choices.resetDisplay(context) }, look = Look.OTHER)
                            val pipOn = remember(Choices.changes) { Pip.on(context) }
                            // On a TV, leaving KueTV closes it, so there is no small picture to choose.
                            if (phone) FocusRow(
                                "Small picture when you leave KueTV: " + if (pipOn) "on" else "off", { Pip.on(context, !pipOn) }, look = Look.OTHER,
                                below = "What is playing carries on in a corner when you press Home, where the device allows it",
                            )
                        }
                        "Looks" -> {
                            FocusRow(
                                "Language: " + UiLang.name(UiLang.code), { UiLang.set(context, UiLang.next()) }, first(), look = Look.OTHER,
                                below = "KueTV's own words. OK for the next language",
                            )
                            FocusRow("Colours: " + Looks.themes[Looks.theme], { Looks.set(context, "theme", (Looks.theme + 1) % Looks.themes.size) }, look = Look.OTHER, below = "Dark or light · OK to change")
                            FocusRow("Highlight colour: " + Looks.accents[Looks.accent], { Looks.set(context, "accent", (Looks.accent + 1) % Looks.accents.size) }, look = Look.OTHER, below = "What the remote is on, and the main buttons")
                            FocusRow("Outline round what the remote is on: " + Looks.outlines[Looks.outline], { Looks.set(context, "outline", (Looks.outline + 1) % Looks.outlines.size) }, look = Look.OTHER, below = "Easier to spot from across the room")
                            FocusRow("Glass look: " + Looks.glasses[Looks.glass], { Looks.set(context, "glass", (Looks.glass + 1) % Looks.glasses.size) }, look = Look.OTHER, below = "See-through panels on a soft glow of the highlight colour")
                            FocusRow("Back to the usual look", { Looks.reset(context) }, look = Look.OTHER)
                        }
                        "Google Drive" -> {
                            when {
                                !drive.isSetUp -> Note("Google Drive isn't set up in this copy of the app.")
                                drive.signedIn -> FocusRow(
                                    "Sign out of Google Drive", { drive.signOut(); Recordings.changed() }, first(), look = Look.OTHER,
                                    below = drive.email.ifBlank { null },
                                )
                                else -> FocusRow("Sign in to Google Drive", onDriveSignIn, first(), look = Look.OTHER, below = "Your backup and recordings are kept there")
                            }
                            if (drive.signedIn) {
                                sub("Backup")
                                val last = remember(Backup.changes) { Backup.lastMs(context) }
                                val how = remember(Backup.changes) { Backup.how(context) }
                                if (extras.driveNote.isNotEmpty()) StatusBox(
                                    Status(
                                        extras.driveNote,
                                        when {
                                            extras.driveNote.endsWith("…") -> Status.BUSY
                                            extras.driveNote.startsWith("Couldn't") || extras.driveNote.startsWith("There is no") || extras.driveNote.startsWith("Sign in") -> Status.FAIL
                                            else -> Status.OK
                                        },
                                    ),
                                )
                                FocusRow(
                                    "Back up now", extras.backUp, look = Look.OTHER,
                                    below = if (last == 0L) "Not backed up yet" else "Last backed up " + java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK).format(java.util.Date(last)),
                                )
                                FocusRow("Back up by itself: " + Backup.hows[how], { Backup.how(context, (how + 1) % Backup.hows.size) }, look = Look.OTHER, below = "OK to change")
                                var sure by remember { mutableStateOf(false) }
                                if (!sure) {
                                    FocusRow("Restore from Google Drive", { sure = true }, look = Look.OTHER, below = "Puts back favourites, add-ons, provider details and choices from the backup")
                                } else {
                                    FocusRow("Yes, restore: what is here is replaced", { sure = false; extras.restore() }, look = Look.OTHER)
                                    FocusRow("No, keep what is here", { sure = false }, look = Look.OTHER)
                                }
                            }
                        }
                        "TV and phone" -> {
                            sub("Copy")
                            FocusRow("Send this set-up to another device", onSendSetUp, first(), look = Look.OTHER, below = "Also pairs the two, so they keep in step")
                            FocusRow("Copy set-up from another device", onTakeSetUp, look = Look.OTHER)
                            sub("Keep in step")
                            val met = Sync.lastMetMs
                            if (paired) {
                                FocusRow(
                                    "Keeping in step: on · press to stop",
                                    {
                                        Sync.setKey(context, "")
                                        kueToast(context, "This device no longer keeps in step. To start again, send the set-up from the other device.")
                                    },
                                    look = Look.OTHER,
                                    below = if (met == 0L) "Not met the other device since opening" else "Last met the other device at " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(met)),
                                )
                                // What was last taken from the other device, so it can be seen working.
                                Note(Sync.lastTook.ifEmpty { "Nothing taken from the other device since opening: they already matched, or haven't met." })
                                Note("What keeps in step: provider details, favourites and their order, add-ons, free channel lists, hidden categories and their order, subtitles, the Stremio sign-in, the VPN details, Carry on watching, and reminders. Both devices need KueTV open on the same Wi-Fi.")
                            } else {
                                Note("Off. Send the set-up from one device to the other to turn it on.")
                            }
                        }
                        "This TV" -> {
                            // The TV's own aerial channels, when it has a tuner.
                            val aerialName = remember(Resumes.count) { Aerial.name(context) }
                            if (aerialName != null) {
                                sub("Aerial TV")
                                FocusRow("Watch aerial TV", { if (!Aerial.open(context)) kueToast(context, "The TV's aerial channels wouldn't open.") },
                                    first(), look = Look.OTHER, below = "The digital channels from the aerial plugged into the TV. They work with no internet")
                                val offer = remember(Aerial.changes) { Aerial.offerWhenOffline(context) }
                                FocusRow("Offer aerial TV when the internet goes: " + if (offer) "on" else "off", { Aerial.offerWhenOffline(context, !offer) },
                                    look = Look.OTHER, below = "OK to change")
                                sub("The TV's settings")
                            }
                            // The TV's own settings: each opens straight onto that screen, and Back comes back here.
                            val tvParts = remember(Resumes.count) { Launcher.tvSettings(context) }
                            tvParts.forEachIndexed { i, p ->
                                FocusRow(p.label, { if (!Launcher.open(context, p)) kueToast(context, "That part of the TV's settings wouldn't open.") },
                                    if (i == 0 && aerialName == null) first() else Modifier, look = Look.OTHER)
                            }
                            FocusRow("All Fire TV settings", { Launcher.openTvSettings(context) }, if (tvParts.isEmpty() && aerialName == null) first() else Modifier,
                                look = Look.OTHER, below = "Amazon's own settings menu. Home or Back when done comes back to KueTV")
                            FocusRow("Get more apps", { if (!Launcher.openAppstore(context)) kueToast(context, "Amazon's Appstore wouldn't open.") },
                                look = Look.OTHER, below = "Amazon's Appstore. New apps land in the right row on Home by themselves")
                        }
                        "Help" -> {
                            FocusRow("Health check" + if (notWorking == 0) "" else " · $notWorking not working", onHealth, first(), look = Look.OTHER, below = "Tries every source the app uses")
                            FocusRow("Report for Claude", onReport, look = Look.OTHER)
                            FocusRow("About and updates · version " + BuildConfig.VERSION_NAME, onAbout, look = Look.OTHER)
                        }
                    }
                    FocusRow("‹ Back to Settings", close, if (given) Modifier else Modifier.focusRequester(pageFirst), look = Look.OTHER)
                    }
                }
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
        // As on Freely: the sections in a bar across the top, and below it the section itself, with at most
        // one column down the left (the categories). ▲ from the top of a list goes up into the bar.
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp).onFocusChanged { panels.anyHas = it.hasFocus }
                // Which key is being pressed: only ▲ goes up into the bar, so ◀ ▶ never jump up into it.
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) bar.up = e.key == Key.DirectionUp
                    false
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        TopBar(section, menuFocus, bar) { section = it }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pane(this, null).body()
            // The small TV: on every section the same size and place as Live TV's, on the right, with what is on
            // that channel underneath. Live TV draws its own, which follows the remote down the list. The TV Guide's
            // grid also draws its own, in its top right corner, so the grid can run the full width of the screen.
            val small = LastLive.item
            val wanted = remember(Choices.changes) { Choices.smallTv(context) }
            val guideGrid = section == Section.GUIDE && state.guide.open == null
            if (small != null && wanted && section != Section.LIVE && !guideGrid) {
                val group = remember(small.url, library) { library.groups.firstOrNull { g -> g.versions.any { it.channel.url == small.url } } }
                val narrow = section == Section.FILMS || section == Section.SERIES
                Column(Modifier.width(if (narrow) 220.dp else SIDE_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniTv(small, Modifier.fillMaxWidth())
                    if (group != null) ProgrammeDetails(group, library.account, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        }
        }
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
        // The remote is in the categories now: say so before the "opening" mark goes (see the menu's own).
        panels.zone = Zone.CATEGORIES
        panels.catsWanted = false
    }
    if (panels != null) {
        DisposableEffect(panels) {
            panels.catsShown = true
            onDispose { panels.catsShown = false }
        }
    }
    // The categories column is always open: it is the one column down the left.
    // The remote resting on a category shows it; moving quickly past categories doesn't fetch each one's list,
    // which made going down a long column slow.
    var landed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(landed) {
        val id = landed ?: return@LaunchedEffect
        delay(350)
        if (id != chosen) onChoose(id)
    }
    val row: @Composable (String, String) -> Unit = { id, name ->
        FocusRow(
            name, { onChoose(id) },
            if (chosen == id) Modifier.focusRequester(chosenFocus) else Modifier,
            chosen = chosen == id, onFocus = { landed = id }, textSize = 19.sp,
        )
    }
    Column(
        Modifier.width(240.dp * LocalTextScale.current).fillMaxHeight().onFocusChanged { panels?.catsHas = it.hasFocus }
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
    Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Looks.faint), contentAlignment = Alignment.Center) {
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
    /** Watched to the end: a ✓ in the corner. */
    watched: Boolean = false,
    /** Part way through: how far, 0 to 1, as a bar along the bottom of the picture. */
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border((3 + Looks.outlineWidth).dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
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
                    "★", color = AccentInk, fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Looks.page.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            if (watched) {
                Text(
                    "✓", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xFF6BD08A), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
            if (progress != null && !watched) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(Color(0x99000000))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(Accent))
                }
            }
        }
        Text(
            name,
            color = if (focused) AccentInk else Ink,
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
    Text(text, color = Soft, fontSize = Sizes.BODY, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
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
    favourites: Favourites? = null,
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
    val shown = remember(channels, pick.category, favs, favourites?.order) {
        val isFav: (ChannelGroup) -> Boolean = { g -> g.versions.any { Favourites.key(it.channel, true) in favs } }
        // In the order set in Live TV's Favourites.
        val rank: (ChannelGroup) -> Int = { g -> favourites?.rank(g.versions.map { Favourites.key(it.channel, true) }) ?: 0 }
        when (pick.category) {
            ALL -> channels
            FAVOURITES -> channels.filter(isFav)
            else -> channels.filter { g -> g.versions.any { it.channel.categoryId == pick.category } }
        }.let { list ->
            // Favourites first, in that order, in every category; the rest as before.
            list.partition(isFav).let { (fav, rest) -> fav.sortedBy(rank) + rest }
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
                    below = if (catchUp) null else nowLine(account, channel.versions[channel.start].channel.id, channel.name),
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
    val linksNow = SeriesLinks.changes
    Column(fill()) {
        Heading(channel.name + if (catchUp) " · catch-up" else " · guide · OK on a later programme: ◆ reminder, then ● record, then neither")
        val result = loaded
        when {
            result == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentInk) }

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
                            whenText = (if (linksNow >= 0 && SeriesLinks.has(context, channel.name, programme.title)) "●● Every showing · " else if (set) "● Will record · " else "") + (if (reminded) "◆ Reminder · " else "") + (if (onNow) "Now · " else day(programme.startMs) + " · ") + time(programme.startMs) + " to " + time(programme.stopMs),
                            title = programme.title.ifBlank { "No title" },
                            description = programme.description,
                            onClick = {
                                if (catchUp) {
                                    onPlay(listOf(catchupPlayable(channel.name, account, channel.id, programme, serverAheadSec)), 0, false, key)
                                } else if (later) {
                                    // A programme that hasn't started: OK sets a reminder, again sets it to record, again sets neither.
                                    stepLater(context, plan, remind, reminded, set, day(programme.startMs) + " " + time(programme.startMs), channel.id)
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
            .focusRing(focused)
            .background(if (focused) Accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(whenText, color = if (focused) Color.Black else Soft, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = if (focused) Color.Black else Ink, fontSize = Sizes.ROW, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    catalogues: List<Catalogue>,
    catCategory: (Catalogue) -> Category,
) {
    val favs = favourites.keys
    val account = library.account
    val opened = state.openShow
    if (opened != null && (account != null || opened.id.startsWith(Catalogue.PREFIX))) {
        EpisodePage(opened, account, state, resume, finder, onPlay)
        return
    }
    if ((account == null || library.shows.isEmpty()) && catalogues.isEmpty()) {
        Column(fill()) {
            Note(if (account == null) "Series need a login: server address, username and password. A playlist link on its own doesn't list them." else "Your provider listed no series.")
        }
        return
    }
    val scope = rememberCoroutineScope()
    Categories(library.showCategories + catalogues.map(catCategory), state.showCategory, state.showCategoryList) {
        if (it != state.showCategory) {
            state.showCategory = it
            scope.launch { state.showGrid.scrollToItem(0) }
        }
    }
    val catalogue = catalogues.firstOrNull { it.key == state.showCategory }
    if (catalogue != null) {
        // A series from a catalogue opens its episodes the same way as one of the provider's.
        CatalogueGrid(catalogue, state, resume, { item, key ->
            state.resumeKey = key
            scope.launch { state.episodeList.scrollToItem(0) }
            state.openShow = catShow(item)
        }, fill())
        return
    }
    val context = LocalContext.current
    val sort = remember(Listing.changes) { Listing.sort(context) }
    val shown = remember(library, state.showCategory, if (state.showCategory == FAVOURITES) favs else null, sort) {
        val inCategory = when (state.showCategory) {
            ALL -> library.shows
            FAVOURITES -> library.shows.filter { Favourites.key(it) in favs }
            else -> library.shows.filter { it.categoryId == state.showCategory }
        }
        Listing.sorted(inCategory, sort, { it.name }, { it.added })
    }
    // A series with an episode part way through: how far through that episode, as a bar on its poster.
    val partWay = remember(Positions.changes) {
        Positions.watching(context).filter { it.lengthMs > 0 }.mapNotNull { w ->
            w.name.substringBefore(" · S", "").takeIf { it.isNotEmpty() }?.let { it to (w.positionMs.toFloat() / w.lengthMs).coerceIn(0f, 1f) }
        }.distinctBy { it.first }.toMap()
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
        ListingChips(watchedToo = false)
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
                    progress = partWay[show.name],
                )
            }
        }
    }
}

@Composable
private fun Pane.EpisodePage(
    show: Show,
    account: Account?,
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
                when {
                    // A series from a catalogue: its episodes come from the film index.
                    show.id.startsWith(Catalogue.PREFIX) -> AddOns.episodes(show.id.removePrefix(Catalogue.PREFIX))?.let { Result.success(it) }
                        ?: Result.failure(ProviderProblem("Couldn't load the episodes for this series. Check the internet and try again."))
                    account == null -> Result.failure(ProviderProblem("Couldn't load the episodes for this series."))
                    else -> Result.success(Provider.episodes(account, show.id))
                }
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
            result == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentInk) }
            episodes == null -> Note(result.exceptionOrNull()?.message ?: "Couldn't load the episodes for this series.")
            episodes.isEmpty() -> Note(if (show.id.startsWith(Catalogue.PREFIX)) "No episodes are listed for this series." else "Your provider listed no episodes for this series.")
            else -> {
                val label: (Episode) -> String = { e ->
                    val plain = "Episode " + e.number
                    if (e.title.isBlank() || e.title.equals(plain, ignoreCase = true)) plain else e.number.toString() + ". " + e.title
                }
                val playables = remember(episodes, finder) {
                    episodes.map { Playable(show.name + " · S" + it.season + " E" + it.number, it.url, find = finder("series", show.name, it.season, it.number), poster = show.poster) }
                }
                val context = LocalContext.current
                val watchedNow = Watched.changes
                val placesNow = Positions.changes
                // Oldest first, as the provider lists them, or newest first: chosen for each series.
                val newest = remember(Listing.changes, show.id) { EpisodeOrder.newestFirst(context, show.id) }
                val inOrder = remember(episodes, newest) {
                    episodes.withIndex().let { all -> if (newest) all.sortedWith(compareByDescending<IndexedValue<Episode>> { it.value.season }.thenByDescending { it.value.number }) else all.toList() }
                }
                LazyColumn(Modifier.fillMaxSize(), state = state.episodeList) {
                    item(key = "order") {
                        FocusRow("Order: " + if (newest) "newest first" else "oldest first", { EpisodeOrder.set(context, show.id, !newest) }, look = Look.OTHER, below = "OK to change")
                    }
                    // The series' trailer, when the provider gives one.
                    val trailer = show.about.trailer
                    if (!trailer.isNullOrBlank()) {
                        item(key = "trailer") {
                            FocusRow("▶ Watch the trailer", {
                                if (!openTrailer(context, trailer)) kueToast(context, "Nothing on this device can play YouTube trailers.")
                            }, look = Look.OTHER)
                        }
                    }
                    inOrder.forEachIndexed { at, (i, episode) ->
                        if (at == 0 || inOrder[at - 1].value.season != episode.season) {
                            item(key = "season:" + episode.season) {
                                Box(Modifier.padding(top = if (at == 0) 0.dp else 14.dp)) { Heading("Season " + episode.season) }
                            }
                        }
                        item(key = episode.id) {
                            val key = "episode:" + episode.id
                            val watched = watchedNow >= 0 && Watched.isWatched(context, episode.url)
                            val part = if (placesNow >= 0) Positions.progress(context, episode.url) else null
                            FocusRow(
                                (if (watched) "✓ " else "") + label(episode),
                                { onPlay(playables, i, false, key) },
                                (if (at == 0) Modifier.focusRequester(first) else Modifier)
                                    .then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                                leading = if (episode.image.isBlank()) null else ({ EpisodePicture(episode.image) }),
                                // Hold OK: marks it watched, or not watched.
                                onHold = {
                                    Watched.set(context, episode.url, !watched)
                                    kueToast(context, if (watched) "Marked as not watched." else "Marked as watched.", long = false)
                                },
                                below = listOfNotNull(
                                    episode.aired.takeIf { it.isNotBlank() }?.let { "First aired " + EpisodeOrder.airedWords(it) },
                                    when {
                                        watched -> "Watched · hold OK to mark as not watched"
                                        part != null -> (part * 100).toInt().toString() + "% watched"
                                        else -> null
                                    },
                                ).joinToString(" · ").ifEmpty { null },
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
    onCat: (CatItem, String) -> Unit,
    onOpenShow: (Show) -> Unit,
) {
    val found = remember(library, state.query) { Finder.search(library, state.query) }
    val asked = state.query.trim()
    // The catalogues are asked too, once the typing has stopped for a moment. Null while they are being asked.
    val more by produceState<Pair<List<CatItem>, List<CatItem>>?>(null, asked) {
        value = null
        if (asked.length < 3) { value = emptyList<CatItem>() to emptyList(); return@produceState }
        delay(700)
        value = withContext(Dispatchers.IO) { runCatching { AddOns.search("movie", asked).take(20) to AddOns.search("series", asked).take(20) }.getOrDefault(emptyList<CatItem>() to emptyList()) }
    }
    val moreFilms = more?.first.orEmpty()
    val moreShows = more?.second.orEmpty()
    // One row for each channel found, as in Live TV, whichever of its versions matched.
    val channelsFound = remember(found) { found.channels.mapNotNull { library.groupOf[it.id] }.distinct() }
    // Programmes on TV by their title: in the guides read so far (your provider's, and the free ones).
    val onTv = remember(library, asked, FreeGuides.changes) { searchGuides(library, asked, System.currentTimeMillis()) }
    val context = LocalContext.current
    val clockFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK) }
    val dayFmt = remember { java.text.SimpleDateFormat("EEE", java.util.Locale.UK) }
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
            found.isEmpty && onTv.isEmpty() && more == null -> Note("Nothing in your provider's lists matches. Looking in the catalogues…")
            found.isEmpty && onTv.isEmpty() && moreFilms.isEmpty() && moreShows.isEmpty() -> Note("Nothing matches \"" + state.query.trim() + "\".")
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
                if (onTv.isNotEmpty()) item(key = "h:tv") { Box(Modifier.padding(top = 14.dp)) { Heading("On TV") } }
                itemsIndexed(onTv, key = { _, (g, p) -> "tv:" + g.id + ":" + p.startMs }) { _, (group, programme) ->
                    val now = System.currentTimeMillis()
                    val key = "tv:" + group.id + ":" + programme.startMs
                    val first = group.versions[group.start].channel
                    val onNow = programme.startMs <= now && programme.stopMs > now
                    val whenText = when {
                        onNow -> "On now"
                        dayName(now, ((midnightOf(programme.startMs, 0) - midnightOf(now, 0)) / DAY_MS).toInt()).let { it == "Today" || it == "Tomorrow" || it == "Yesterday" } ->
                            dayName(now, ((midnightOf(programme.startMs, 0) - midnightOf(now, 0)) / DAY_MS).toInt()) + " " + clockFmt.format(java.util.Date(programme.startMs))
                        else -> dayFmt.format(java.util.Date(programme.startMs)) + " " + clockFmt.format(java.util.Date(programme.startMs))
                    }
                    FocusRow(
                        programme.title,
                        {
                            val account = library.account
                            val kept = group.versions.firstOrNull { it.channel.catchupDays > 0 }?.channel
                            when {
                                onNow -> onPlay(listOf(livePlayable(group, account, library.serverAheadSec)), 0, true, key)
                                programme.startMs > now -> {
                                    val plan = Timed.make(group.name, programme.title.ifBlank { "Programme" }, first.url, programme.startMs, programme.stopMs)
                                    val remind = Reminder.make(first.id, group.name, programme.title.ifBlank { "Programme" }, programme.startMs, programme.stopMs)
                                    stepLater(
                                        context, plan, remind, Reminders.all(context).any { it.id == remind.id }, TimedStore.all(context).any { it.id == plan.id },
                                        clockFmt.format(java.util.Date(programme.startMs)), first.id,
                                    )
                                }
                                account != null && kept != null && programme.startMs >= now - kept.catchupDays * DAY_MS ->
                                    onPlay(listOf(catchupPlayable(group.name, account, kept.id, programme, library.serverAheadSec)), 0, false, key)
                                else -> kueToast(context, "Your provider keeps no catch-up for this.")
                            }
                        },
                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                        leading = { Logo(group.logo) },
                        below = group.name + " · " + whenText + if (onNow) "" else if (programme.startMs > now) " · OK for a reminder, or to record" else " · OK for catch-up",
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
                if (more == null) item(key = "h:looking") { Box(Modifier.padding(top = 14.dp)) { Heading("Looking in the catalogues…") } }
                if (moreFilms.isNotEmpty()) item(key = "h:catfilm") { Box(Modifier.padding(top = 14.dp)) { Heading("More films, from the catalogues") } }
                itemsIndexed(moreFilms, key = { _, f -> "catfilm:" + f.id }) { _, film ->
                    val key = Catalogue.PREFIX + film.id
                    FocusRow(
                        film.name + if (film.about.year.isBlank()) "" else " (" + film.about.year + ")",
                        { onCat(film, key) },
                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                    )
                }
                if (moreShows.isNotEmpty()) item(key = "h:catshow") { Box(Modifier.padding(top = 14.dp)) { Heading("More series, from the catalogues") } }
                itemsIndexed(moreShows, key = { _, s -> "catshow:" + s.id }) { _, show ->
                    FocusRow(
                        show.name + if (show.about.year.isBlank()) "" else " (" + show.about.year + ")",
                        { state.resumeKey = Catalogue.PREFIX + show.id; onOpenShow(catShow(show)) },
                    )
                }
            }
        }
    }
}

/**
 * Programmes whose title has [asked] in it, in the guides read so far: the provider's, and the free UK and Irish ones.
 * From the start of yesterday to a week ahead, soonest first, each channel's showing once; at most 40.
 */
internal fun searchGuides(library: Library, asked: String, now: Long): List<Pair<ChannelGroup, Programme>> {
    if (asked.trim().length < 3) return emptyList()
    val word = asked.trim()
    val from = midnightOf(now, -1)
    val out = ArrayList<Pair<ChannelGroup, Programme>>()
    for ((id, list) in NowNext.everyKept()) {
        val group = library.groupOf[id] ?: continue
        list.filter { it.title.contains(word, ignoreCase = true) && it.stopMs > from }.forEach { out += group to it }
    }
    val byKey = library.groups.groupBy { FreeGuides.key(it.name) }
    for ((key, list) in FreeGuides.everything()) {
        val group = byKey[key]?.firstOrNull() ?: continue
        list.filter { it.title.contains(word, ignoreCase = true) && it.stopMs > from }.forEach { out += group to it }
    }
    // Showings now and later first, then those already gone (for catch-up).
    return out.distinctBy { it.first.id + ":" + it.second.startMs }
        .sortedWith(compareBy({ it.second.stopMs <= now }, { if (it.second.stopMs <= now) -it.second.startMs else it.second.startMs }))
        .take(40)
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
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentInk) }
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
                        if (!opened) kueToast(context, "Couldn't open the app or the app store.")
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
            .focusRing(focused)
            .background(if (focused) Accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Looks.faint), contentAlignment = Alignment.Center) {
            if (icon != null) {
                val picture = remember(icon) { icon.asImageBitmap() }
                Image(bitmap = picture, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text("▶", color = if (focused) Color.Black else Soft, fontSize = 24.sp)
            }
        }
        Column {
            Text(title, color = if (focused) Color.Black else Ink, fontSize = Sizes.ROW, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val tell: (String) -> Unit = { kueToast(context, it) }
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
                                kueToast(context, why)
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
        val line: @Composable (String) -> Unit = { Text(it, color = Soft, fontSize = Sizes.BODY, modifier = Modifier.padding(start = 16.dp)) }
        val big: @Composable (String) -> Unit = {
            Text(it, color = AccentInk, fontSize = Sizes.CODE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 14.dp))
        }
        Text("Google Drive", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 22.dp))
        when {
            done -> {
                line("Signed in" + if (drive.email.isBlank()) "." else " as " + drive.email + ".")
                line("Recordings will be copied to the folder \"" + Drive.FOLDER + "\".")
                Spacer(Modifier.height(22.dp))
                FocusRow("Done", onDone, Modifier.focusRequester(first), look = Look.MAIN)
            }
            problem != null -> {
                Text(problem.orEmpty(), color = Ink, fontSize = Sizes.ROW, modifier = Modifier.padding(start = 16.dp, bottom = 26.dp))
                FocusRow("Try again", { attempt++ }, Modifier.focusRequester(first), look = Look.MAIN)
                Spacer(Modifier.height(8.dp))
                FocusRow("Back", onDone, look = Look.OTHER)
            }
            shown == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentInk) }
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
        Text(name, color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
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
    var status by remember { mutableStateOf<Status?>(null) }
    // The add-on opened, by its link; null for the list.
    var opened by remember { mutableStateOf<String?>(null) }
    val backTo = remember { arrayOfNulls<String>(1) }
    BackHandler { onDone() }
    val checkAll: () -> Unit = { if (!Health.running) scope.launch(Dispatchers.IO) { runCatching { Health.runAddOns(context) } } }
    // Checked once when the screen opens.
    LaunchedEffect(Unit) { checkAll() }

    val one = addOns.firstOrNull { it.base == opened }
    if (one != null) {
        AddOnPage(one, onBack = { backTo[0] = one.base; opened = null }, onRemoved = { said -> status = said; opened = null })
        return
    }
    val first = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { if (backTo[0] != null) back.requestFocus() else first.requestFocus() }
    }
    Page("My add-ons", "Each add-on and how it did when last checked. OK on one opens it.", status) {
        FocusRow(
            if (Health.running) "Checking each add-on…" else "Check them all again", checkAll, Modifier.focusRequester(first), look = Look.OTHER,
            below = "✓ working   ! answers, with a gap   ✗ not working",
        )
        if (addOns.isEmpty()) Note("No add-ons yet. Sign in to Stremio, or add one by its link, from Settings › Films and series.")
        addOns.forEach { addOn ->
            val check = Health.results.firstOrNull { it.name == Health.addOnName(addOn) }
            FocusRow(
                markOf(check) + addOn.name, { opened = addOn.base },
                if (addOn.base == backTo[0]) Modifier.focusRequester(back) else Modifier,
                look = Look.OTHER, below = gives(addOn) + "  ·  " + verdict(check),
            )
        }
        FocusRow("‹ Back", onDone, look = Look.OTHER)
    }
}

/** One add-on on a page of its own: what it gives, each part's state, check it again, remove it. */
@Composable
private fun AddOnPage(addOn: AddOn, onBack: () -> Unit, onRemoved: (Status) -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Status?>(null) }
    BackHandler { if (confirming) confirming = false else onBack() }
    val check = Health.results.firstOrNull { it.name == Health.addOnName(addOn) }
    val inStremio = remember(Stremio.changes) { Stremio.key(context).isNotEmpty() }
    val first = remember { FocusRequester() }
    LaunchedEffect(confirming) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Page(addOn.name, "Gives: " + gives(addOn) + ".", status) {
        if (confirming) {
            Note("Remove " + addOn.name + "?" + if (inStremio) " It also comes out of your Stremio account, so it won't come back." else "")
            FocusRow("Yes, remove it", {
                confirming = false
                status = Status("Removing…", Status.BUSY)
                removeAddOn(context, scope, addOn) { said -> onRemoved(Status(said, if (said.startsWith("Couldn't") || said.contains("Couldn't")) Status.FAIL else Status.OK)) }
            }, Modifier.focusRequester(first), look = Look.MAIN)
            FocusRow("No, keep it", { confirming = false }, look = Look.OTHER)
            return@Page
        }
        Heading("How each part did when last checked")
        when {
            check == null && Health.running -> PartLine(Status.BUSY, "Being checked now…")
            check == null -> PartLine(Status.BUSY, "Not checked yet. Press Check again.")
            else -> check.detail.lines().filter { it.isNotBlank() }.forEach { line ->
                val kind = when { line.startsWith("✗") -> Status.FAIL; line.startsWith("!") -> Status.WARN; else -> Status.OK }
                PartLine(kind, line.drop(2))
            }
        }
        Spacer(Modifier.height(8.dp))
        FocusRow(
            if (Health.running) "Checking…" else "Check again",
            { if (!Health.running) scope.launch(Dispatchers.IO) { runCatching { Health.runAddOns(context) } } },
            Modifier.focusRequester(first), look = Look.OTHER,
        )
        FocusRow("Remove " + addOn.name, { confirming = true }, look = Look.OTHER, below = if (inStremio) "From KueTV and from your Stremio account" else "From KueTV")
        FocusRow("‹ Back to my add-ons", onBack, look = Look.OTHER)
    }
}

/** Adding an add-on (its manifest.json link) or a free channel list (.m3u) by its link. */
@Composable
private fun AddLinkScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Status?>(null) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone() }
    val add: (String) -> Unit = { typed ->
        if (!busy) {
            busy = true
            status = Status("Reading the link…", Status.BUSY)
            scope.launch {
                val result = withContext(Dispatchers.IO) { Links.add(context, typed) }
                if (result.ok) link = ""
                status = Status(result.said, if (result.ok) Status.OK else Status.FAIL)
                // An add-on added here goes into the Stremio account too, when signed in.
                val added = result.addOn
                if (added != null && Stremio.key(context).isNotEmpty()) {
                    val more = withContext(Dispatchers.IO) { try { Stremio.addToAccount(context, added) } catch (e: AddOnProblem) { "Couldn't add it to your Stremio account. " + e.message } }
                    status = Status(result.said + " " + more, if (more.startsWith("Couldn't")) Status.WARN else Status.OK)
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
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    Page("Add by link", "An add-on's link (it ends in manifest.json) or a channel list's (it ends in .m3u).", status) {
        phoneAddress?.let { address -> InfoPanel("Easiest: on your phone, on the same Wi-Fi, open " + address + " and paste the link there.") }
        Field("Link", link, onDone = { add(link) }, modifier = Modifier.focusRequester(first)) { link = it }
        FocusRow(if (busy) "Adding…" else "Add", { add(link) }, look = Look.MAIN)
        FocusRow("‹ Back", onDone, look = Look.OTHER)
    }
}

// --------------------------------------------------------------------------------------------- page parts

/** A message about what has just happened, shown in one place near the top of a page. */
private class Status(val text: String, val kind: Int) {
    companion object {
        const val OK = 0
        const val WARN = 1
        const val FAIL = 2
        const val BUSY = 3
        const val INFO = 4
    }
}

private fun statusColour(kind: Int) = when (kind) {
    Status.OK -> Color(0xFF4CAF50)
    Status.WARN -> Color(0xFFFFB300)
    Status.FAIL -> Color(0xFFFF6B5E)
    Status.INFO -> Color(0xFFB9C2D3)
    else -> Color(0xFF64B5F6)
}

/** A page: its title, one line saying what it is for, the message box when there is something to say, then its rows. */
@Composable
private fun Page(title: String, intro: String, status: Status?, rows: @Composable ColumnScope.() -> Unit) {
    val phone = LocalPhone.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 200.dp, vertical = if (phone) 12.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
        Text(intro, color = Soft, fontSize = Sizes.BODY, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        if (status != null) StatusBox(status)
        rows()
    }
}

/** The message box: white words on the dark panel, with a coloured dot for how it went. */
@Composable
private fun StatusBox(status: Status) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(12.dp).background(statusColour(status.kind), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(status.text, color = Ink, fontSize = Sizes.BODY)
    }
}

/** A plain panel of information (not a message about something that happened). */
@Composable
private fun InfoPanel(text: String) {
    Text(text, color = Ink, fontSize = Sizes.BODY, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp))
}

/** One part of an add-on, with a coloured dot for how it did. */
@Composable
private fun PartLine(kind: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(10.dp).background(statusColour(kind), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ink, fontSize = 18.sp)
    }
}

/** "✓ ", "! " or "✗ " for an add-on's last check; nothing when it hasn't been checked. */
private fun markOf(check: Check?) = when (check?.state) { Check.WORKING -> "✓  "; Check.DOUBT -> "!  "; Check.DEAD -> "✗  "; else -> "" }

/** In a few words, how an add-on did: "All working", "Not working: streams", "Has a gap: subtitles". */
private fun verdict(check: Check?): String {
    if (check == null) return if (Health.running) "Being checked…" else "Not checked yet"
    val lines = check.detail.lines()
    val part: (String) -> String = { it.drop(2).substringBefore(':').lowercase() }
    val dead = lines.filter { it.startsWith("✗") }.map(part)
    val gaps = lines.filter { it.startsWith("!") }.map(part)
    return when {
        dead.isNotEmpty() -> "Not working: " + dead.joinToString(", ")
        gaps.isNotEmpty() -> "Has a gap: " + gaps.joinToString(", ")
        else -> "All working"
    }
}

/** For the Settings row: "4 working, 1 not working". */
private fun addOnsSummary(addOns: List<AddOn>): String {
    if (addOns.isEmpty()) return "None yet"
    val checks = addOns.mapNotNull { a -> Health.results.firstOrNull { it.name == Health.addOnName(a) } }
    if (checks.isEmpty()) return addOns.size.toString() + " added, not checked yet"
    return listOfNotNull(
        checks.count { it.state == Check.WORKING }.takeIf { it > 0 }?.let { "$it working" },
        checks.count { it.state == Check.DOUBT }.takeIf { it > 0 }?.let { "$it with a gap" },
        checks.count { it.state == Check.DEAD }.takeIf { it > 0 }?.let { "$it not working" },
    ).joinToString(", ")
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
            { SetUp(account, favourites.keys, AddOnStore.all(app), Choices.smallTv(app), listOf("live", "film", "show").associateWith { Choices.hidden(app, it) }, FreeStore.all(app), Sync.keyOrMake(app)).pack() },
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
        Text("Send this set-up", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 18.dp))
        when {
            ready == false -> Note("This device isn't on a home network, so it can't send its set-up. Join the Wi-Fi and try again.")
            taken -> Note("Sent. The other device now has your provider details, favourites and add-ons. From now on the two keep in step by themselves when KueTV is open on both, on the same Wi-Fi.")
            else -> {
                Note("On the other device, on the same Wi-Fi, open KueTV and choose \"Copy set-up from another device\". Then type this code there:")
                Text(code, color = AccentInk, fontSize = Sizes.CODE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp))
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
        Text("Copy set-up from another device", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 18.dp))
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
        // What happened, in the message box: white words, never yellow on yellow.
        said?.let { Box(Modifier.padding(top = 10.dp)) { StatusBox(Status(it, Status.INFO)) } }
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
    // An add-on's TV channel: played through the add-ons.
    if (first.url.startsWith(AddOns.TV)) return AddOns.tvPlayable(group.name, first.url, AddOns.lang)
    val kept = group.versions.firstOrNull { it.channel.catchupDays > 0 }?.channel
    return Playable(
        group.name, first.url, group.versions.map { Stream(it.label, it.channel.url) }, group.start,
        guide = Combined.accountFor(account, first.id)?.let { a -> { NowNext.get(a, first.id, group.name) } },
        fromStart = kept?.let { k -> Combined.accountFor(account, k.id) }?.let { a ->
            { programme -> catchupPlayable(group.name, a, kept.id, programme, serverAheadSec) }
        },
    )
}

/**
 * A programme played through catch-up. Once it ends, the programme that followed it on the channel plays on,
 * if that one has finished airing too; one still on air is left, as the provider can't give all of it yet.
 */
internal fun catchupPlayable(channelName: String, account: Account, channelId: String, programme: Programme, serverAheadSec: Int): Playable =
    Playable(
        channelName + " · " + programme.title.ifBlank { "No title" },
        // The provider's archive goes by its own times, before any move made to the guide in Settings.
        Provider.catchupUrl(account, channelId, GuideOffset.ms.let { off -> if (off == 0L) programme else programme.copy(startMs = programme.startMs - off, stopMs = programme.stopMs - off) }, serverAheadSec),
        airedFromMs = programme.startMs,
        then = {
            val listing = NowNext.get(account, channelId)
            val next = listing.filter { it.startMs >= programme.stopMs - 60_000 && it.startMs > programme.startMs }.minByOrNull { it.startMs }
            if (next == null || next.stopMs > System.currentTimeMillis()) null else catchupPlayable(channelName, account, channelId, next, serverAheadSec)
        },
    )

/**
 * What is on a channel now and next, for the line under its name in a list; null until it is known, and
 * when the provider has no guide for it. A row has to stay on screen for half a second before it asks,
 * so rows that are only passing by while the list is scrolled don't.
 */
@Composable
private fun nowLine(primary: Account?, channelId: String, name: String = ""): String? {
    val account = Combined.accountFor(primary, channelId)
    // With no provider login, only the free guides can say what is on.
    if (account == null) return remember(channelId, FreeGuides.changes) { NowNext.line(FreeGuides.lookup(name), System.currentTimeMillis()) }
    val line by keyedState(channelId, { NowNext.cached(channelId)?.let { NowNext.line(it, System.currentTimeMillis()) } }) {
        if (value != null) return@keyedState
        delay(500)
        value = try {
            NowNext.line(NowNext.get(account, channelId, name), System.currentTimeMillis())
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
    // The day shown: 0 today, from two hours ago; another day from its midnight. Up to a week ahead.
    var day by remember { mutableIntStateOf(pick.guideDay) }
    var toNow by remember { mutableIntStateOf(0) }
    val start = remember(day) { guideStart(opened, day) }
    val end = start + GRID_SPAN_MIN * 60_000L
    val total = (GRID_SPAN_MIN * GRID_DP_PER_MIN).dp
    val across: (Long) -> Dp = { ms -> (ms / 60_000f * GRID_DP_PER_MIN).dp }
    val strip = rememberScrollState()
    LaunchedEffect(day, toNow) {
        pick.guideDay = day
        // Once the grid has been laid out (until then it can't be moved along): today opens on now, with a little
        // of what came before showing; another day at the same time of day.
        withFrameNanos { }
        withFrameNanos { }
        val target = if (day == 0) opened - start - 20 * 60_000L else (opened - midnightOf(opened, 0)) - 20 * 60_000L
        strip.scrollTo(with(density) { across(target.coerceAtLeast(0L)).roundToPx() })
    }
    val timed = remember(TimedStore.changes) { TimedStore.all(context) }
    val reminders = remember(Reminders.changes) { Reminders.all(context) }
    val linksNow = SeriesLinks.changes
    val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val time: (Long) -> String = { clock.format(java.util.Date(it)) }
    var about by remember { mutableStateOf<Pair<String, Programme>?>(null) }
    // The channel row the remote is on: its picture plays in the top right corner.
    var onChannel by remember { mutableStateOf<ChannelGroup?>(null) }
    val nameWidth = 230.dp * scale
    val tell: (String) -> Unit = { kueToast(context, it) }

    Column(fill()) {
        // The standard guide layout: what the remote is on, described along the top, with the channel's picture in
        // the top right corner. The grid runs the full width of the screen underneath.
        Row(Modifier.fillMaxWidth().height(GUIDE_PICTURE_WIDTH * 9f / 16f + 41.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GuideDetails(about, onChannel, account, time, Modifier.weight(1f).fillMaxHeight())
            PreviewPicture(onChannel, Modifier.width(GUIDE_PICTURE_WIDTH).testTag("guide picture"))
        }
        Spacer(Modifier.height(8.dp))
        // Jump to now, and the days: yesterday (for catch-up) to six days ahead.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideChip("Jump to now", false) { day = 0; toNow++ }
            for (d in GUIDE_DAYS) GuideChip(dayName(opened, d), d == day) { day = d }
        }
        Row {
            // The day at the left edge of the grid, as it moves sideways: Today, Tomorrow, or the day's name.
            val leftMs = start + (with(density) { strip.value.toDp() }.value / GRID_DP_PER_MIN * 60_000L).toLong()
            val dayOf: (Long) -> Long = { ms -> (ms + java.util.TimeZone.getDefault().getOffset(ms)) / DAY_MS }
            val day = when (dayOf(leftMs) - dayOf(opened)) {
                0L -> "Today"
                1L -> "Tomorrow"
                -1L -> "Yesterday"
                else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.UK).format(java.util.Date(leftMs))
            }
            Text(day, color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(nameWidth).padding(start = 8.dp))
            Box(Modifier.weight(1f).horizontalScroll(strip, enabled = false)) {
                Row(Modifier.width(total)) {
                    for (i in 0 until GRID_SPAN_MIN / 30) {
                        Text(time(start + i * 30 * 60_000L), color = Soft, fontSize = 15.sp, maxLines = 1, modifier = Modifier.width((30 * GRID_DP_PER_MIN).dp).padding(start = 8.dp))
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("guide grid"), state = pick.channelList) {
            itemsIndexed(shown, key = { _, c -> c.id }) { _, channel ->
                val first = channel.versions[channel.start].channel
                // null while it is being asked for. A row asks only after staying on screen for half a second.
                val guide by keyedState(first.id, { NowNext.cached(first.id) }) {
                    if (value != null) return@keyedState
                    delay(500)
                    value = try {
                        NowNext.get(account, first.id, channel.name)
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
                        nameWidth, false, { onOpen(channel) }, { about = null; onChannel = channel },
                        if (nameKey == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                        logo = channel.logo,
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
                                    val every = linksNow >= 0 && SeriesLinks.has(context, channel.name, programme.title)
                                    GridCell(
                                        (if (every) "●● " else if (set) "● " else "") + (if (reminded) "◆ " else "") + programme.title.ifBlank { "No title" }, time(programme.startMs),
                                        across(to - from), programme.startMs <= opened && programme.stopMs > opened,
                                        {
                                            val now = System.currentTimeMillis()
                                            val kept = channel.versions.firstOrNull { it.channel.catchupDays > 0 }?.channel
                                            when {
                                                programme.startMs <= now && programme.stopMs > now -> onPlay(listOf(livePlayable(channel, account, library.serverAheadSec)), 0, true, key)
                                                programme.startMs > now -> stepLater(context, plan, remind, reminded, set, time(programme.startMs), first.id)
                                                kept == null || programme.startMs < now - kept.catchupDays * DAY_MS -> tell("Your provider keeps no catch-up for this.")
                                                else -> onPlay(
                                                    listOf(catchupPlayable(channel.name, account, kept.id, programme, library.serverAheadSec)), 0, false, key,
                                                )
                                            }
                                        },
                                        { about = channel.name to programme; onChannel = channel },
                                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                                        strip = strip, startsAt = with(density) { across(from - start).roundToPx() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The days the TV Guide offers, from today: yesterday, for catch-up, to six days ahead. */
private val GUIDE_DAYS = -1..6

/** Where the TV Guide's grid starts for [day] (0 today): today two hours before now, on the half hour; another day at its midnight. */
internal fun guideStart(now: Long, day: Int): Long =
    if (day == 0) now - now % (30 * 60_000L) - GRID_EARLIER_MIN * 60_000L else midnightOf(now, day)

/** Midnight at the start of the day [day] days from the one [now] is in, on this device's clock. */
internal fun midnightOf(now: Long, day: Int): Long {
    return java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_YEAR, day)
    }.timeInMillis
}

/** "Yesterday", "Today", "Tomorrow", or "Fri 20". */
internal fun dayName(now: Long, day: Int): String = when (day) {
    -1 -> "Yesterday"
    0 -> "Today"
    1 -> "Tomorrow"
    else -> java.text.SimpleDateFormat("EEE d", java.util.Locale.UK).format(java.util.Date(midnightOf(now, day)))
}

/** A small button above the TV Guide's grid: a day, or Jump to now. */
@Composable
private fun GuideChip(text: String, chosen: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        color = if (focused || chosen) Color.Black else Ink,
        fontSize = 15.sp,
        fontWeight = if (focused || chosen) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .focusRing(focused, RoundedCornerShape(16.dp))
            .background(if (focused) Accent else if (chosen) Color(0xFFB9C2D3) else Panel)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

/** The width of the picture in the TV Guide's top right corner. */
private val GUIDE_PICTURE_WIDTH = 256.dp

/**
 * Along the top of the TV Guide: the programme the remote is on, in full. On a channel's name: what is on
 * that channel now and next. The keys' meanings sit underneath, on one line.
 */
@Composable
private fun GuideDetails(about: Pair<String, Programme>?, channel: ChannelGroup?, account: Account, time: (Long) -> String, modifier: Modifier) {
    Column(modifier) {
        val panel = Modifier.fillMaxWidth().weight(1f)
        when {
            about != null -> {
                val (name, programme) = about
                Column(panel.background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(name, color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(time(programme.startMs) + " to " + time(programme.stopMs), color = AccentInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(programme.title.ifBlank { "No title" }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (programme.description.isNotBlank()) {
                        Text(programme.description, color = Soft, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(top = 2.dp))
                    }
                }
            }
            channel != null -> ProgrammeDetails(channel, account, panel)
            else -> Box(panel.background(Panel, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("Move onto a programme to see what it is about.", color = Soft, fontSize = 17.sp)
            }
        }
        Text(
            "OK: watch what is on now · on a later one, ◆ reminder, then ● record, then neither · catch up on an earlier one",
            color = Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        )
    }
}

/** One box in the guide's grid: a channel's name, or a programme as wide as it is long. */
@Composable
private fun GridCell(
    title: String, sub: String, width: Dp, onNow: Boolean, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier, logo: String? = null,
    strip: androidx.compose.foundation.ScrollState? = null, startsAt: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    // A channel's name: its logo beside it.
    if (logo != null) {
        Row(
            modifier.width(width).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(8.dp))
                .focusRing(focused, RoundedCornerShape(8.dp))
                .background(if (focused) Accent else Panel)
                .onFocusChanged {
                    if (it.isFocused && !focused) onFocus()
                    focused = it.isFocused
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Logo(logo)
            Text(title, color = if (focused) Color.Black else Ink, fontSize = 16.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .background(if (focused) Accent else if (onNow) Looks.nowCell else Panel)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus()
                focused = it.isFocused
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // A programme that began before the left edge of the guide: its name slides along to stay in view.
        val keep = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.roundToPx() }
        Column(
            Modifier.layout { m, c ->
                val shift = if (strip == null) 0 else (strip.value - startsAt).coerceIn(0, (c.maxWidth - keep).coerceAtLeast(0))
                val p = m.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth - shift).coerceAtLeast(0)))
                layout(c.maxWidth, p.height) { p.place(shift, 0) }
            },
        ) {
            Text(title, color = if (focused) Color.Black else Ink, fontSize = 17.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = if (focused) Color.Black else Soft, fontSize = 13.sp, maxLines = 1)
        }
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
        Text("Report for Claude", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
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
            Text(address.orEmpty(), color = AccentInk, fontSize = Sizes.CODE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
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
                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (about != null && about.plot.isNotBlank()) Text(about.plot, color = Soft, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --------------------------------------------------------------------------------------------- categories to show

/** One kind of thing with categories (live channels, films, series): its categories and how much is filed under each. */
private class Kind(val key: String, val title: String, val categories: List<Category>, val counts: Map<String, Int>)

/**
 * The categories, in one place: which show, and the order they come in. One list: Live TV's categories, then
 * Films', then Series'. OK on a category hides it or shows it again. "Put in order" turns that kind's list into
 * one where OK picks a category up, ▲ ▼ moves it, and OK puts it down; Live TV, the guide, Films and Series
 * then list them in that order. Nothing is deleted: a hidden category shown again brings it all back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoriesScreen(library: Library, onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val changes = Choices.changes
    // The kind being put in order, and the category picked up to move (null when none is).
    var sorting by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    BackHandler {
        when {
            picked != null -> picked = null
            sorting != null -> sorting = null
            else -> onDone()
        }
    }
    val kinds = remember(library, changes) {
        listOf(
            Kind("live", "Live TV", Library.ordered(library.liveCategories, Choices.order(context, "live")), library.channels.groupingBy { it.categoryId }.eachCount()),
            Kind("film", "Films", Library.ordered(library.filmCategories, Choices.order(context, "film")), library.films.groupingBy { it.categoryId }.eachCount()),
            Kind("show", "Series", Library.ordered(library.showCategories, Choices.order(context, "show")), library.shows.groupingBy { it.categoryId }.eachCount()),
        ).filter { it.categories.isNotEmpty() }
    }
    val hidden = remember(changes) { kinds.associate { it.key to Choices.hidden(context, it.key) } }
    @Suppress("UNUSED_VARIABLE") val hiddenChannelsNow = HiddenChannels.changes
    val list = remember { LazyListState() }
    /** Moves the picked category to [to] within its kind, and keeps that order. */
    val moveTo: (Kind, Int) -> Unit = { kind, to ->
        val from = kind.categories.indexOfFirst { it.id == picked }
        if (from >= 0 && to in kind.categories.indices && to != from) {
            val ids = kind.categories.map { it.id }.toMutableList()
            ids.add(to, ids.removeAt(from))
            Choices.order(context, kind.key, ids)
        }
    }
    val first = remember { FocusRequester() }
    val firstOfSort = remember { FocusRequester() }
    LaunchedEffect(sorting) {
        withFrameNanos { }
        withFrameNanos { }
        if (!phone) runCatching { if (sorting != null) firstOfSort.requestFocus() else first.requestFocus() }
    }
    val status = when {
        sorting != null && picked != null -> Status("Moving. " + if (phone) "Tap where it should go." else "▲ ▼ to move it, OK to put it down.", Status.INFO)
        sorting != null -> Status((if (phone) "Tap" else "OK on") + " a category to pick it up. Back when done.", Status.INFO)
        else -> null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 160.dp, vertical = if (phone) 12.dp else 30.dp)) {
        Text("Categories", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Text(
            "Which show, and their order. " + (if (phone) "Tap" else "OK on") + " a category to hide it or show it again.",
            color = Soft, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        if (status != null) StatusBox(status)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).focusRestorer().onPreviewKeyEvent { e ->
                // A picked-up category moves with ▲ ▼, and the remote stays on it.
                val kind = kinds.firstOrNull { it.key == sorting }
                if (kind == null || picked == null || (e.key != Key.DirectionUp && e.key != Key.DirectionDown)) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    val from = kind.categories.indexOfFirst { it.id == picked }
                    moveTo(kind, from + if (e.key == Key.DirectionUp) -1 else 1)
                }
                true
            },
            state = list,
            verticalArrangement = if (phone) Arrangement.spacedBy(6.dp) else Arrangement.Top,
        ) {
            item(key = "done") {
                FocusRow(if (sorting != null) "Done putting in order" else "Done", { if (sorting != null) { sorting = null; picked = null } else onDone() }, Modifier.focusRequester(first), look = Look.MAIN)
            }
            kinds.forEach { kind ->
                // While one kind is being put in order, the others are out of the way.
                if (sorting != null && sorting != kind.key) return@forEach
                val off = hidden[kind.key].orEmpty()
                item(key = "h:" + kind.key) {
                    Box(Modifier.padding(top = 16.dp)) { Heading(kind.title + " · " + (kind.categories.size - kind.categories.count { it.id in off }) + " of " + kind.categories.size + " showing") }
                }
                if (sorting == null && kind.key == "live") {
                    item(key = "group:live") {
                        val on = Choices.groupedLive(context)
                        FocusRow(
                            "Group by topic: " + if (on) "On" else "Off", { Choices.groupedLive(context, !on) }, look = Look.OTHER,
                            below = if (on) "Sport, News, Entertainment and so on, free channels in with the rest" else "Your provider's own categories, and each free list's",
                        )
                    }
                }
                if (sorting == null) {
                    item(key = "sort:" + kind.key) {
                        FocusRow("Put " + kind.title + "'s categories in order", { sorting = kind.key; picked = null }, look = Look.OTHER, below = if (Choices.order(context, kind.key).isEmpty()) "In the order your provider sends them" else "In your own order")
                    }
                    item(key = "all:" + kind.key) { FocusRow("Show all of " + kind.title, { Choices.hidden(context, kind.key, emptySet()) }, look = Look.OTHER) }
                    item(key = "none:" + kind.key) { FocusRow("Hide all of " + kind.title, { Choices.hidden(context, kind.key, kind.categories.mapTo(HashSet()) { it.id }) }, look = Look.OTHER) }
                } else {
                    item(key = "reset:" + kind.key) {
                        FocusRow("Back to the order your provider sends", { Choices.order(context, kind.key, emptyList()); picked = null }, look = Look.OTHER)
                    }
                }
                itemsIndexed(kind.categories, key = { _, c -> kind.key + ":" + c.id }) { i, category ->
                    val isOff = category.id in off
                    if (sorting == kind.key) {
                        val isPicked = picked == category.id
                        FocusRow(
                            (if (isPicked) "↕  " else "") + category.name,
                            {
                                when {
                                    picked == null -> picked = category.id
                                    picked == category.id -> picked = null
                                    // Phone: tapping another category puts the picked one there.
                                    else -> { moveTo(kind, i); picked = null }
                                }
                            },
                            if (i == 0) Modifier.focusRequester(firstOfSort) else Modifier,
                            chosen = isPicked,
                        )
                    } else {
                        FocusRow(
                            (if (isOff) "✗  " else "✓  ") + category.name + " · " + (kind.counts[category.id] ?: 0) + if (isOff) " · hidden" else "",
                            { Choices.hidden(context, kind.key, if (isOff) off - category.id else off + category.id) },
                        )
                    }
                }
            }
            // Channels hidden one by one in Live TV: each can be brought back, or all at once.
            if (sorting == null) {
                val hiddenIds = HiddenChannels.all(context)
                val hiddenNames = library.channels.filter { it.id in hiddenIds }.groupBy { it.name }
                item(key = "h:channels") {
                    Box(Modifier.padding(top = 16.dp)) { Heading("Hidden channels · " + hiddenNames.size) }
                }
                if (hiddenNames.isEmpty()) {
                    item(key = "none:channels") { Note(if (phone) "None. Press and hold a channel in Live TV to hide it." else "None. Hold OK on a channel in Live TV to hide it.") }
                } else {
                    item(key = "all:channels") { FocusRow("Show all hidden channels again", { HiddenChannels.show(context, hiddenIds) }, look = Look.OTHER) }
                    hiddenNames.entries.sortedBy { it.key.lowercase() }.forEach { (name, list) ->
                        item(key = "ch:" + name) { FocusRow("✗  " + name + " · hidden", { HiddenChannels.show(context, list.map { it.id }) }) }
                    }
                }
            }
        }
    }
}

/** For the automatic checks only: the Categories screen as a TV shows it. */
@Composable
internal fun CategoriesForChecks(library: Library) {
    CategoriesScreen(library, onDone = {})
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
    onFilm: (Film) -> Unit = {},
    onShow: (Show) -> Unit = {},
) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val login = library.account != null
    val favs = favourites.keys
    val tiles = Section.entries.filter { it != Section.HOME }
    val channels = remember(library, favs, favourites.order) {
        library.groups.filter { g -> g.versions.any { Favourites.key(it.channel, login) in favs } }
            .sortedBy { g -> favourites.rank(g.versions.map { Favourites.key(it.channel, login) }) }
    }
    val playables = remember(channels) { channels.map { livePlayable(it, library.account, library.serverAheadSec) } }
    val clock by produceState(homeClock()) {
        while (true) {
            delay(10_000)
            value = homeClock()
        }
    }
    // TV: everything down to the section tiles stays put; the rows below scroll in their own space, so
    // nothing is pushed off the top. The margins keep clear of the edges a TV can cut off.
    Column(
        modifier.fillMaxSize().then(if (phone) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = if (phone) 8.dp else 48.dp, vertical = if (phone) 8.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(if (phone) 14.dp else 10.dp),
    ) {
        // The top. Phone: the app's name and the time. TV, as on Freely: what the remote is on (or the channel last
        // watched) in big letters on the left, and the channel last watched still playing on the right.
        val last = LastLive.item
        val smallTv = remember(Choices.changes) { Choices.smallTv(context) }
        var hero by remember { mutableStateOf<HomeHero?>(null) }
        if (phone) {
            Column(Modifier.fillMaxWidth()) {
                KueLogo(36.dp, 30.sp)
                Text(clock, color = Soft, fontSize = 17.sp)
            }
        } else {
            val lastGroup = remember(library, last) {
                last?.let { l -> library.groups.firstOrNull { g -> g.versions.any { it.channel.url == l.url } } ?: library.groups.firstOrNull { it.name == l.name } }
            }
            val shownHero = hero ?: (lastGroup ?: channels.firstOrNull())?.let { HomeHero.OnChannel(it) }
            // The banner of what is trending on Netflix, Prime Video, iPlayer and the rest, in place of Amazon's.
            // Until it has been fetched (or with no internet): the channel last watched, as before.
            LaunchedEffect(Unit) { Trending.load(context) }
            val slides = Trending.slides
            if (slides.isNotEmpty()) TrendingBanner(slides, clock, Modifier.fillMaxWidth().height(184.dp))
            else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KueLogo(30.dp, 24.sp)
                        Spacer(Modifier.width(18.dp))
                        Text(clock, color = Soft, fontSize = 16.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (shownHero != null) HeroWords(shownHero, library.account)
                }
                if (last != null && smallTv) MiniTv(last, Modifier.width(SIDE_WIDTH))
                else if (shownHero != null) HeroPicture(shownHero, Modifier.width(SIDE_WIDTH))
            }
        }

        if (phone) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s ->
                        Tile({ onOpen(s) }, Modifier.weight(1f).aspectRatio(1.1f)) { focused ->
                            SectionIcon(s, if (focused) Color.Black else AccentInk, Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(s.label, color = if (focused) Color.Black else Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // A short last row keeps its tiles the same size as the others.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            // All the tiles are drawn, so the one to land on can always take focus, even when it is off to the right.
            Row(
                Modifier.fillMaxWidth().focusRestorer().focusGroup().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                tiles.forEach { s ->
                    Tile(
                        { onOpen(s) },
                        Modifier.size(width = 150.dp, height = 100.dp).then(if (s == state.cameFrom) Modifier.focusRequester(homeFocus) else Modifier),
                    ) { focused ->
                        SectionIcon(s, if (focused) Color.Black else AccentInk, Modifier.size(38.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(s.label, color = if (focused) Color.Black else Ink, fontSize = 17.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // The rows below the tiles, each with its heading, as on Freely: pictures with their names under them.
        // On a TV they show whole: a row that would be cut off at the bottom stays hidden until the remote moves
        // down to it, and the rows then move up a row at a time.
        val blockSpacing = Arrangement.spacedBy(if (phone) 14.dp else 4.dp)
        val rowGap = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)
        val rowPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        val rows: List<@Composable () -> Unit> = listOf(
        { Column(verticalArrangement = blockSpacing) {
        Heading("Favourite channels")
        if (channels.isEmpty()) {
            Note(if (phone) "Press and hold a channel in Live TV to make it a favourite. Favourites show here." else "Hold OK on a channel in Live TV to make it a favourite. Favourites show here.")
        } else {
            // Opens on the channel that was playing, so coming back from the player lands on it.
            val list = remember { LazyListState(channels.indexOfFirst { "home:" + it.id == state.resumeKey }.coerceAtLeast(0)) }
            LazyRow(Modifier.fillMaxWidth().focusRestorer(), state = list, contentPadding = rowPadding, horizontalArrangement = rowGap) {
                itemsIndexed(channels, key = { _, c -> c.id }) { i, channel ->
                    val key = "home:" + channel.id
                    val onNow = guideFor(library.account, channel.versions[channel.start].channel.id, channel.name)
                        ?.let { g -> val at = System.currentTimeMillis(); g.firstOrNull { it.startMs <= at && it.stopMs > at } }
                    HomeCard(
                        { onPlay(playables, i, true, key) },
                        Modifier.width(if (phone) 150.dp else 176.dp).then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                        16f / 9f,
                        onNow?.title?.ifBlank { null } ?: channel.name,
                        if (onNow?.title.isNullOrBlank()) null else channel.name,
                        onHold = { favourites.toggleAll(channel.versions.map { Favourites.key(it.channel, login) }) },
                        onFocus = { hero = HomeHero.OnChannel(channel) },
                        bar = onNow?.let { ((System.currentTimeMillis() - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    ) {
                        if (channel.logo.isNotBlank()) AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                        else Text(channel.name, color = Soft, fontSize = 15.sp, maxLines = 2, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        } },
        { Column(verticalArrangement = blockSpacing) {
        // Films and episodes left part way through: OK carries on from where it was left.
        val carry = remember(Positions.changes) { Positions.watching(context) }
        if (carry.isNotEmpty()) {
            Heading("Carry on watching")
            val carryList = remember { LazyListState(carry.indexOfFirst { "carry:" + it.url == state.resumeKey }.coerceAtLeast(0)) }
            LazyRow(Modifier.fillMaxWidth().focusRestorer(), state = carryList, contentPadding = rowPadding, horizontalArrangement = rowGap) {
                itemsIndexed(carry, key = { _, w -> w.url }) { _, w ->
                    val key = "carry:" + w.url
                    val left = ((w.lengthMs - w.positionMs) / 60_000).coerceAtLeast(1).toString() + " min left"
                    HomeCard(
                        { onCarryOn(w) },
                        Modifier.width(if (phone) 150.dp else 176.dp).then(if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                        16f / 9f,
                        w.name,
                        left,
                        onFocus = { hero = HomeHero.Plain(w.name, left, "", w.poster) },
                        // How far through.
                        bar = if (w.lengthMs > 0) (w.positionMs.toFloat() / w.lengthMs).coerceIn(0f, 1f) else 0f,
                    ) {
                        if (w.poster.isNotBlank()) AsyncImage(model = w.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(w.name, color = Soft, fontSize = 14.sp, maxLines = 3, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        } },
        // TV: live channels that open straight into their own app's live player, as Freely does.
        { if (!phone) Column(verticalArrangement = blockSpacing) {
            val liveHere = LiveInApps.all
            val aerial by produceState(false) { value = withContext(Dispatchers.Default) { Aerial.available(context) } }
            Heading("Live in their own apps")
            LazyRow(Modifier.fillMaxWidth().focusRestorer(), contentPadding = rowPadding, horizontalArrangement = rowGap) {
                // The TV's own aerial channels, first: they work with no internet.
                if (aerial) item(key = "aerial") {
                    HomeCard({ if (!Aerial.open(context)) kueToast(context, "The TV's aerial channels wouldn't open.") },
                        Modifier.width(150.dp), 16f / 9f, "Aerial TV", "This TV") {
                        Text("📡 Aerial", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                itemsIndexed(liveHere, key = { _, c -> c.link }) { _, c ->
                    HomeCard({ LiveInApps.open(context, c) }, Modifier.width(150.dp), 16f / 9f, c.name, c.app) {
                        Text(c.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        } },
        // TV: RTÉ Player catch-up, two rows: programmes RTÉ lets play anywhere, then those only for Ireland.
        *(listOf(true to "RTÉ Player catch-up", false to "RTÉ Player catch-up · Ireland only").map { (anywhere, title) ->
        @Composable { if (!phone) Column(verticalArrangement = blockSpacing) {
            LaunchedEffect(Unit) { RteCatchUp.load() }
            val shows = RteCatchUp.list.filter { it.anywhere == anywhere }
            if (shows.isNotEmpty()) {
                Heading(title)
                LazyRow(Modifier.fillMaxWidth().focusRestorer(), contentPadding = rowPadding, horizontalArrangement = rowGap) {
                    itemsIndexed(shows, key = { _, c -> c.guid }) { _, c ->
                        HomeCard({ RteCatchUp.open(context, c) }, Modifier.width(150.dp), 16f / 9f, c.title, "RTÉ Player") {
                            if (c.image.isNotBlank()) AsyncImage(model = c.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(c.title, color = Ink, fontSize = 16.sp, maxLines = 2, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        } }
        }).toTypedArray(),
        // TV: Pluto TV's live channels, each opening straight into Pluto TV playing it.
        { if (!phone) Column(verticalArrangement = blockSpacing) {
            LaunchedEffect(Unit) { PlutoChannels.load() }
            val pluto = PlutoChannels.list
            if (pluto.isNotEmpty()) {
                Heading("Pluto TV")
                LazyRow(Modifier.fillMaxWidth().focusRestorer(), contentPadding = rowPadding, horizontalArrangement = rowGap) {
                    itemsIndexed(pluto, key = { _, c -> c.slug }) { _, c ->
                        HomeCard({ PlutoChannels.open(context, c) }, Modifier.width(150.dp), 16f / 9f, c.name, null) {
                            if (c.logo.isNotBlank()) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                            else Text(c.name, color = Ink, fontSize = 16.sp, maxLines = 2, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        } },
        // TV: every app on the TV, with the TV's own settings at the end, as on Freely. KueTV can be the TV's
        // home screen, so this is the way to Freely, Prime Video and the rest.
        // TV: the TV's apps, each sorted into its row by what it is: an app installed later lands in the right row
        // by itself, the next time Home shows. The TV's own settings at the end of the last row.
        *(listOf("tv" to "TV channel apps", "films" to "Films and series apps", "music" to "Music apps", "other" to "Other apps").map { (kind, title) ->
        @Composable { if (!phone) Column(verticalArrangement = blockSpacing) {
            val apps by produceState(emptyList<DeviceApps.App>(), Resumes.count) { value = withContext(Dispatchers.IO) { DeviceApps.list(context) } }
            val these = apps.filter { AppKinds.of(it) == kind }
            if (these.isNotEmpty()) {
            Heading(title)
            LazyRow(Modifier.fillMaxWidth().focusRestorer(), contentPadding = rowPadding, horizontalArrangement = rowGap) {
                itemsIndexed(these, key = { _, a -> a.packageName }) { _, a ->
                    HomeCard({ TvAppsOnDevice.open(context, a.packageName) }, Modifier.width(150.dp), 16f / 9f, a.name, null) {
                        a.picture?.let { androidx.compose.foundation.Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp)) }
                            ?: Text(a.name, color = Soft, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            }
        } }
        }).toTypedArray(),
        // Favourite films and series, each in a row of posters. Hold OK takes one off.
        { Column(verticalArrangement = blockSpacing) {
            val favFilms = remember(library, favs) { library.films.filter { Favourites.key(it, login) in favs } }
            PosterRow("Favourite films", favFilms, { "homefilm:" + it.id }, { it.name }, { it.poster }, state, resume, onFilm,
                hero = { f -> val about = state.filmAbout[f.id]; HomeHero.Plain(f.name, about?.line.orEmpty(), about?.plot.orEmpty(), f.poster) },
                onHero = { hero = it }) { favourites.toggle(Favourites.key(it, login)) }
        } },
        { Column(verticalArrangement = blockSpacing) {
            val favShows = remember(library, favs) { library.shows.filter { Favourites.key(it) in favs } }
            PosterRow("Favourite series", favShows, { "homeshow:" + it.id }, { it.name }, { it.poster }, state, resume, onShow,
                hero = { sh -> HomeHero.Plain(sh.name, sh.about.line, sh.about.plot, sh.poster) },
                onHero = { hero = it }) { favourites.toggle(Favourites.key(it)) }
        } },
        )
        if (phone) Column(verticalArrangement = blockSpacing) { rows.forEach { it() } }
        else WholeRows(rows, Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * Rows stacked down the screen, shown only whole: a row that would be cut off at the bottom is hidden until
 * the remote moves down to it; then the rows move up so it shows in full. Moving up brings rows back.
 */
@Composable
private fun WholeRows(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    var focusedRow by remember { mutableIntStateOf(-1) }
    val top = remember { intArrayOf(0) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    androidx.compose.ui.layout.Layout(
        content = {
            rows.forEachIndexed { i, row ->
                Box(Modifier.onFocusChanged { if (it.hasFocus) focusedRow = i }) { row() }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)) }
        val heights = placeables.map { if (it.height > 0) it.height + gap else 0 }
        val room = constraints.maxHeight
        val f = focusedRow
        var t = top[0].coerceIn(0, (placeables.size - 1).coerceAtLeast(0))
        if (f in placeables.indices) {
            if (f < t) t = f
            while (t < f && (t..f).sumOf { heights[it] } - gap > room) t++
        }
        top[0] = t
        layout(constraints.maxWidth, room) {
            var y = -(0 until t).sumOf { heights[it] }
            placeables.forEachIndexed { i, p ->
                val whole = y >= 0 && y + p.height <= room
                p.placeWithLayer(0, y) { alpha = if (whole || i == t) 1f else 0f }
                y += heights[i]
            }
        }
    }
}

/** A row on Home of favourite films or series: their posters; OK plays the film or opens the series. */
@Composable
private fun <T> PosterRow(
    title: String, items: List<T>, key: (T) -> String, name: (T) -> String, poster: (T) -> String, state: BrowseState, resume: FocusRequester,
    onOpen: (T) -> Unit, hero: (T) -> HomeHero = { HomeHero.Plain(name(it), "", "", poster(it)) }, onHero: (HomeHero) -> Unit = {}, onHold: (T) -> Unit,
) {
    if (items.isEmpty()) return
    val phone = LocalPhone.current
    Heading(title)
    LazyRow(
        Modifier.fillMaxWidth().focusRestorer(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp),
    ) {
        itemsIndexed(items, key = { _, it -> key(it) }) { _, item ->
            HomeCard(
                { state.resumeKey = key(item); onOpen(item) },
                Modifier.width(if (phone) 110.dp else 84.dp).then(if (key(item) == state.resumeKey) Modifier.focusRequester(resume) else Modifier),
                2f / 3f,
                name(item),
                null,
                onHold = { onHold(item) },
                onFocus = { onHero(hero(item)) },
            ) {
                if (poster(item).isNotBlank()) AsyncImage(model = poster(item), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(name(item), color = Soft, fontSize = 14.sp, maxLines = 4, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

/**
 * The banner across the top of Home on a TV, in place of Amazon's: what is trending on each streaming service,
 * a title at a time, with its picture. It moves on by itself every eight seconds; with the remote on it,
 * ◀ ▶ go back and on, and OK opens that service's app.
 */
@Composable
private fun TrendingBanner(slides: List<Trending.Slide>, clock: String, modifier: Modifier) {
    val context = LocalContext.current
    var at by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var moved by remember { mutableIntStateOf(0) }
    LaunchedEffect(slides.size, moved) {
        while (true) {
            delay(8_000)
            at = (at + 1) % slides.size
        }
    }
    val slide = slides[at.coerceIn(0, slides.size - 1)]
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(Panel)
            .then(if (focused) Modifier.border(3.dp, Accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (!focused || (e.key != Key.DirectionLeft && e.key != Key.DirectionRight)) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    at = (at + if (e.key == Key.DirectionLeft) slides.size - 1 else 1) % slides.size
                    moved++
                }
                true
            }
            .press({ Trending.open(context, slide.service) }, null),
    ) {
        androidx.compose.animation.Crossfade(slide, label = "banner") { s ->
            Box(Modifier.fillMaxSize()) {
                // The picture fills the right two thirds and fades into the panel on its left.
                AsyncImage(model = s.picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight().fillMaxWidth(0.66f).align(Alignment.CenterEnd))
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Panel, 0.36f to Panel, 0.62f to Panel.copy(alpha = 0f))))
                Column(Modifier.fillMaxHeight().fillMaxWidth(0.52f).padding(start = 20.dp, top = 14.dp, bottom = 12.dp, end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KueLogo(24.dp, 17.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(clock, color = Soft, fontSize = 14.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Trending · " + s.service, color = AccentInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(s.title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.about.isNotBlank()) Text(s.about, color = Soft, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.weight(1f))
                    if (focused) Text("◀ ▶ more · OK opens the app", color = Soft, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        // Where this one is among them: a dot for each, along the bottom right.
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            slides.indices.forEach { i ->
                Box(Modifier.size(if (i == at) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(if (i == at) Accent else Ink.copy(alpha = 0.5f)))
            }
        }
    }
}

/** What the big space at the top of Home tells about: a channel (what is on it), or a film, series or thing to carry on. */
private sealed interface HomeHero {
    class OnChannel(val group: ChannelGroup) : HomeHero
    class Plain(val title: String, val line: String, val about: String, val picture: String) : HomeHero
}

/** The words at the top of Home: a channel's name, what is on now in big letters, its times and what it is about. */
@Composable
private fun HeroWords(hero: HomeHero, account: Account?) {
    var kicker = ""
    var title = ""
    var line = ""
    var about = ""
    when (hero) {
        is HomeHero.OnChannel -> {
            val g = hero.group
            val guide = guideFor(account, g.versions[g.start].channel.id, g.name)
            val at = System.currentTimeMillis()
            val onNow = guide?.firstOrNull { it.startMs <= at && it.stopMs > at }
            val clock = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
            kicker = g.name
            title = onNow?.title?.ifBlank { null } ?: g.name
            line = if (onNow == null) "" else "Now · " + clock.format(java.util.Date(onNow.startMs)) + " to " + clock.format(java.util.Date(onNow.stopMs))
            about = onNow?.description.orEmpty()
        }
        is HomeHero.Plain -> {
            title = hero.title
            line = hero.line
            about = hero.about
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (kicker.isNotBlank() && kicker != title) Text(kicker, color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line.isNotBlank()) Text(line, color = AccentInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        if (about.isNotBlank()) Text(about, color = Soft, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** With the small TV turned off: the picture of what the remote is on, top right of Home. */
@Composable
private fun HeroPicture(hero: HomeHero, modifier: Modifier) {
    val picture = when (hero) {
        is HomeHero.OnChannel -> hero.group.logo
        is HomeHero.Plain -> hero.picture
    }
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Panel), contentAlignment = Alignment.Center) {
        if (picture.isNotBlank()) AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (hero is HomeHero.OnChannel) 24.dp else 0.dp))
    }
}

/**
 * A picture on Home with its name under it, as on Freely. The remote on it: a yellow edge round the picture,
 * which grows a little, and the name turns yellow. [bar] is how far through, when there is one to show.
 */
@Composable
private fun HomeCard(
    onClick: () -> Unit,
    modifier: Modifier,
    ratio: Float,
    title: String,
    below: String?,
    onHold: (() -> Unit)? = null,
    onFocus: () -> Unit = {},
    bar: Float? = null,
    picture: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.press(onClick, onHold)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).scale(if (focused) 1.05f else 1f).clip(shape).background(Looks.faint)
                .then(if (focused) Modifier.border(3.dp, Accent, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { picture() }
        if (bar != null) {
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(3.dp).background(Looks.faint2, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(bar).fillMaxHeight().background(Accent, RoundedCornerShape(2.dp)))
            }
        }
        Text(
            title, color = if (focused) AccentInk else Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        if (below != null) Text(below, color = Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

/** What is on a channel, as far as is known: the provider's guide, or with no login the free guides. Null while it is being found. */
@Composable
private fun guideFor(primary: Account?, channelId: String, name: String): List<Programme>? {
    val account = Combined.accountFor(primary, channelId)
    if (account == null) return remember(channelId, FreeGuides.changes) { FreeGuides.lookup(name) }
    val guide by keyedState(channelId, { NowNext.cached(channelId) }) {
        if (value != null) return@keyedState
        delay(500)
        value = try {
            NowNext.get(account, channelId, name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    return guide
}

/** An episode's picture, beside its name. */
@Composable
private fun EpisodePicture(url: String) {
    Box(Modifier.size(width = 112.dp, height = 63.dp).clip(RoundedCornerShape(6.dp)).background(Looks.faint)) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private fun barClock(): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date())

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
            .focusRing(focused, RoundedCornerShape(16.dp))
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
        Text("A newer KueTV 2 is ready", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
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
        Text("About KueTV 2", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
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
                        val file = withContext(Dispatchers.IO) { Updates.downloadChecked(context) { p -> scope.launch { said = "Downloading · $p%" } } }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewPicture(on, Modifier.fillMaxWidth())
        // What is on follows the remote straight away; only the picture waits, to save connections.
        if (on != null) ProgrammeDetails(on, account, Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * The small picture: the channel last watched, still playing. It doesn't follow the remote down the list.
 */
@Composable
private fun PreviewPicture(on: ChannelGroup?, modifier: Modifier) {
    // The channel being watched keeps playing here: moving along the list only changes the details beside it.
    val item = LastLive.item
    if (item != null) {
        MiniTv(item, modifier)
    } else {
        Box(modifier.aspectRatio(16f / 9f).background(Panel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text("OK on a channel to watch it.", color = Soft, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

/** What is on a channel now (with the whole description, as far as there is room) and what is on next. */
@Composable
private fun ProgrammeDetails(group: ChannelGroup, primary: Account?, modifier: Modifier) {
    val id = group.versions[group.start].channel.id
    val account = Combined.accountFor(primary, id)
    val guide by keyedState(id, { NowNext.cached(id) }) {
        if (value != null || account == null) return@keyedState
        value = try {
            NowNext.get(account, id, group.name)
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
        // Whose details these are, so they can't be taken for another channel's.
        Text(group.name, color = Soft, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
        when {
            account == null -> Text("Programme details need a login: server address, username and password.", color = Soft, fontSize = 17.sp)
            guide == null -> Text("Finding what is on…", color = Soft, fontSize = 17.sp)
            onNow == null && next == null -> Text("No programme details for this channel.", color = Soft, fontSize = 17.sp)
        }
        if (onNow != null) {
            Text(
                "Now · " + clock.format(java.util.Date(onNow.startMs)) + " to " + clock.format(java.util.Date(onNow.stopMs)),
                color = AccentInk, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text(onNow.title.ifBlank { "No title" }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onNow.description.isNotBlank()) {
                // Too long for the space above the next programme: it scrolls up slowly by itself, waits, and starts again.
                val scroll = remember(onNow.description) { ScrollState(0) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                LaunchedEffect(onNow.description, scroll.maxValue) {
                    while (scroll.maxValue > 0) {
                        delay(4_000)
                        val ms = (with(density) { scroll.maxValue.toDp().value } * 45).toInt().coerceAtLeast(1_000)
                        scroll.animateScrollTo(scroll.maxValue, androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.LinearEasing))
                        delay(3_000)
                        scroll.scrollTo(0)
                    }
                }
                Box(Modifier.weight(1f, fill = false).padding(top = 4.dp).verticalScroll(scroll, enabled = false)) {
                    Text(onNow.description, color = Soft, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
        }
        if (next != null) {
            Text(
                "Next · " + clock.format(java.util.Date(next.startMs)) + " · " + next.title.ifBlank { "No title" },
                color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
            // A line or two about what is on next.
            if (next.description.isNotBlank()) Text(next.description, color = Soft, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        // Always checked afresh on opening, so what it shows is how things are now (the last results show meanwhile).
        check()
    }
    val at = remember(Health.lastRunMs) {
        if (Health.lastRunMs == 0L) "" else java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK).format(java.util.Date(Health.lastRunMs))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 12.dp else 140.dp, vertical = if (phone) 12.dp else 30.dp)) {
        Text("Health check", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
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
            .background(if (focused) Looks.faint2 else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(title, color = when (state) { Check.WORKING -> Ink; Check.DOUBT -> AccentInk; else -> Color(0xFFFF6B5E) }, fontSize = Sizes.ROW, fontWeight = FontWeight.Bold)
        Text(detail, color = Soft, fontSize = 16.sp)
    }
}

// --------------------------------------------------------------------------------------------- catalogues

/** A series from a catalogue, as the series screens know one. */
private fun catShow(item: CatItem) = Show(Catalogue.PREFIX + item.id, item.name, item.poster, "", item.about)

/**
 * One catalogue's films or series as posters. The first page is fetched when it is opened, and the next
 * each time the remote nears the end of what has been fetched so far.
 */
@Composable
private fun CatalogueGrid(catalogue: Catalogue, state: BrowseState, resume: FocusRequester, onOk: (CatItem, String) -> Unit, modifier: Modifier) {
    val phone = LocalPhone.current
    val items = remember(catalogue.key) { state.catItems.getOrPut(catalogue.key) { mutableStateListOf() } }
    val grid = remember(catalogue.key) { state.catGrids.getOrPut(catalogue.key) { LazyGridState() } }
    var wanted by remember(catalogue.key) { mutableIntStateOf(0) }
    var problem by remember(catalogue.key) { mutableStateOf(false) }
    var busy by remember(catalogue.key) { mutableStateOf(false) }
    var on by remember(catalogue.key) { mutableStateOf<CatItem?>(null) }
    LaunchedEffect(catalogue.key, wanted) {
        if (catalogue.key in state.catDone) return@LaunchedEffect
        if (wanted == 0 && items.isNotEmpty()) return@LaunchedEffect
        busy = true
        try {
            val page = withContext(Dispatchers.IO) { runCatching { AddOns.page(catalogue, items.size) }.getOrNull() }
            if (page == null) {
                problem = items.isEmpty()
            } else {
                problem = false
                val have = items.mapTo(HashSet()) { it.id }
                val fresh = page.filter { have.add(it.id) }
                // A page with nothing new is the end of the catalogue.
                if (fresh.isEmpty()) state.catDone += catalogue.key else items.addAll(fresh)
            }
        } finally {
            busy = false
        }
    }
    Column(modifier) {
        Heading(catalogue.name + " · " + items.size + (if (catalogue.key in state.catDone) "" else "+") + (if (catalogue.type == "movie") " films" else " series") + " from the catalogue")
        if (!phone) AboutStrip(on?.name, on?.about)
        when {
            problem -> Note("This catalogue didn't answer. Check the internet, or see Settings › Health check.")
            items.isEmpty() && busy -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentInk) }
            items.isEmpty() -> Note("This catalogue is empty.")
            else -> LazyVerticalGrid(
                columns = if (phone) GridCells.Adaptive(104.dp) else GridCells.Fixed(4),
                state = grid,
                modifier = Modifier.fillMaxSize().focusRestorer(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(items, key = { _, f -> f.id }) { i, item ->
                    val key = Catalogue.PREFIX + item.id
                    // On a phone nothing takes focus, so the next page is asked for as the end scrolls into view.
                    if (phone && i >= items.size - 6) LaunchedEffect(items.size) { wanted = items.size }
                    Poster(
                        item.name, item.poster,
                        { onOk(item, key) },
                        if (key == state.resumeKey) Modifier.focusRequester(resume) else Modifier,
                        onFocus = {
                            on = item
                            if (i >= items.size - 8) wanted = items.size
                        },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------- free channel lists

/**
 * Free channel lists: playlists of channels that are free to watch on the internet, added by their links.
 * A free channel with the same name as one of the provider's becomes that channel's backup. The rest show
 * in Live TV under categories that start "Free:". KueTV comes with none.
 */
@Composable
private fun FreeListsScreen(channelCount: Int, onDone: () -> Unit, onWatch: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val lists = remember(FreeStore.changes) { FreeStore.all(context) }
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
                // A channel list or an add-on, whichever the link is. It is read before it is kept, so a dead link is never added.
                val result = withContext(Dispatchers.IO) { Links.add(context, typed) }
                if (result.ok) link = ""
                said = result.said
                // An add-on added here goes into the Stremio account too, when signed in.
                val added = result.addOn
                if (added != null && Stremio.key(context).isNotEmpty()) {
                    val more = withContext(Dispatchers.IO) { try { Stremio.addToAccount(context, added) } catch (e: AddOnProblem) { "Couldn't add it to your Stremio account. " + e.message } }
                    said = result.said + " " + more
                }
                busy = false
            }
        }
    }
    DisposableEffect(Unit) {
        val main = Handler(Looper.getMainLooper())
        val server = if (phone) null else PhoneSetup(onAddOn = { typed -> main.post { add(typed) } }, freeList = true) { _, _, _, _ -> }
        phoneAddress = server?.start()
        onDispose { server?.stop() }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (phone) 12.dp else 220.dp, vertical = if (phone) 12.dp else 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Free channel lists", color = Ink, fontSize = Sizes.TITLE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        Note("A backup for your provider. If a channel from your provider won't play, a free channel of the same name is tried next.")
        // What has been added can be watched straight away.
        if (lists.isNotEmpty()) {
            FocusRow(
                if (channelCount > 0) "Watch the free channels ($channelCount)" else "Watch the free channels (still loading)",
                onWatch, look = Look.MAIN,
                below = "Also in Live TV, at the top of the categories, under \"Free:\"",
            )
        }
        // One press adds a ready-made list.
        val ready = FreeStore.ready.filter { it.second !in lists }
        if (ready.isNotEmpty()) Box(Modifier.padding(top = 10.dp)) { Heading("Add with one press") }
        ready.forEach { (name, address) -> FocusRow("Add " + name, { add(address) }, look = Look.OTHER) }
        Box(Modifier.padding(top = 10.dp)) { Heading("Or add any list by its link") }
        phoneAddress?.let { address ->
            Note("Easiest: on your phone, on the same Wi-Fi, open")
            Text(address, color = AccentInk, fontSize = Sizes.CODE, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            Note("and paste the link there.")
        }
        Field("Channel list or add-on link", link, onDone = { add(link) }) { link = it }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!phone) runCatching { first.requestFocus() }
        }
        FocusRow(if (busy) "Reading the list…" else "Add", { add(link) }, Modifier.focusRequester(first), look = Look.MAIN)
        said?.let { StatusBox(Status(it, if (it.startsWith("Added") || it.startsWith("Removed")) Status.OK else Status.INFO)) }
        if (lists.isNotEmpty()) Box(Modifier.padding(top = 14.dp)) { Heading("Added") }
        lists.forEach { list ->
            val health = when (Health.stateOf("Free list: " + FreeStore.label(list))) {
                Check.WORKING -> "✓ working · "
                Check.DOUBT -> "! look at Health check · "
                Check.DEAD -> "✗ NOT WORKING · "
                else -> ""
            }
            FocusRow(health + "Remove " + FreeStore.label(list), { FreeStore.remove(context, list); said = "Removed." }, look = Look.OTHER, below = list)
        }
        FocusRow("Done", onDone, look = Look.OTHER)
    }
}

// --------------------------------------------------------------------------------------------- Stremio

/**
 * The Stremio account, on its own page. Signed out: sign in, typing here or on the phone. Signed in: who, bring
 * the add-ons in now, sign out, and how it works. Which add-ons work is on the My add-ons page.
 */
@Composable
private fun StremioScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val key = remember(Stremio.changes) { Stremio.key(context) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Status?>(null) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }
    BackHandler { onDone() }
    val run: (String, suspend () -> String) -> Unit = { doing, work ->
        if (!busy) {
            busy = true
            status = Status(doing, Status.BUSY)
            scope.launch {
                status = try {
                    Status(work(), Status.OK)
                } catch (x: CancellationException) {
                    throw x
                } catch (x: AddOnProblem) {
                    Status(x.message.orEmpty(), Status.FAIL)
                }
                busy = false
                // Then each add-on is checked, for the My add-ons page.
                scope.launch(Dispatchers.IO) { runCatching { Health.runAddOns(context) } }
            }
        }
    }
    val bring: () -> Unit = { run("Asking Stremio…") { withContext(Dispatchers.IO) { Stremio.say(Stremio.bringIn(context)) } } }
    val signIn: (String, String) -> Unit = { e, p ->
        run("Signing in…") {
            withContext(Dispatchers.IO) {
                Stremio.signIn(context, e, p)
                "Signed in. " + Stremio.say(Stremio.bringIn(context))
            }.also { password = "" }
        }
    }
    // While signed out, a TV also answers the phone, so the email and password can be typed there.
    DisposableEffect(key.isEmpty()) {
        val main = Handler(Looper.getMainLooper())
        val server = if (phone || key.isNotEmpty()) null else PhoneSetup(onStremio = { e, p -> main.post { signIn(e, p) } }) { _, _, _, _ -> }
        phoneAddress = server?.start()
        onDispose { server?.stop() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(key) {
        withFrameNanos { }
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    if (key.isNotEmpty()) {
        Page("Stremio account", "Signed in as " + Stremio.email(context) + ".", status) {
            FocusRow(if (busy) "Asking Stremio…" else "Bring in my Stremio add-ons now", bring, Modifier.focusRequester(first), look = Look.MAIN)
            FocusRow("Sign out of Stremio", {
                Stremio.signOut(context)
                status = Status("Signed out. The add-ons already here stay.", Status.OK)
            }, look = Look.OTHER)
            Heading("How it works")
            InfoPanel(
                "Your Stremio account holds the list of add-ons.\n" +
                    "Adding or removing one in KueTV does the same in Stremio.\n" +
                    "Changes made in Stremio come into KueTV each time KueTV opens.\n" +
                    "Your TV and phone share the list and this sign-in when they keep in step.",
            )
            Note("Which add-ons work: Settings › Films and series › My add-ons.")
            FocusRow("‹ Back", onDone, look = Look.OTHER)
        }
    } else {
        Page("Stremio account", "Sign in once, and KueTV brings in every add-on you have in Stremio. Your password goes only to Stremio and isn't kept.", status) {
            phoneAddress?.let { address -> InfoPanel("Easiest: on your phone, on the same Wi-Fi, open " + address + " and type them there.") }
            Field("Email", email, modifier = Modifier.focusRequester(first)) { email = it }
            Field("Password", password, secret = true) { password = it }
            FocusRow(if (busy) "Signing in…" else "Sign in", { signIn(email, password) }, look = Look.MAIN)
            FocusRow("‹ Back", onDone, look = Look.OTHER)
        }
    }
}

/** The engine's step while connecting, in plain words. Empty for a step with nothing useful to say. */
private fun vpnStep(step: String): String = when (step) {
    "RESOLVE" -> "looking up the server"
    "TCP_CONNECT", "CONNECTING" -> "reaching the server"
    "WAIT" -> "waiting for the server to answer"
    "AUTH" -> "checking the username and password"
    "GET_CONFIG" -> "getting settings from the server"
    "ASSIGN_IP", "ADD_ROUTES" -> "setting up the connection"
    "RECONNECTING", "CONNECTRETRY" -> "trying again"
    "NONETWORK" -> "waiting for the internet"
    else -> ""
}

/** VPN Jantit's free Dublin server (Ireland 1): the link to its OpenVPN file. */
private const val JANTIT_IRELAND = "https://www.vpnjantit.com/download-openvpn.php?server=ie1"

/** The VPN in a few words, for the Settings list. */
private fun vpnLine(context: android.content.Context): String = when {
    Vpn.details(context).isEmpty -> "Not set up"
    Vpn.isOn -> "On"
    Vpn.isTrying -> "Connecting…"
    else -> "Off"
} + if (Vpn.connectOnOpen(context)) " · connects when KueTV opens" else ""

/** How the VPN is, in plain words, and whether that is good, under way, or a problem. */
private fun vpnStatus(level: de.blinkt.openvpn.core.ConnectionStatus): Status = when (level) {
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTED -> Status("VPN on.", Status.OK)
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_START,
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> {
        val doing = vpnStep(Vpn.step)
        val stuck = Vpn.startedMs > 0 && System.currentTimeMillis() - Vpn.startedMs > 45_000
        Status(
            "Connecting" + (if (doing.isEmpty()) "…" else ": $doing…") +
                if (stuck) "\nStill not connected after 45 seconds. Send the Report lines starting \"VPN\" so the cause can be found." else "",
            if (stuck) Status.WARN else Status.BUSY,
        )
    }
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> Status("The VPN is asking for a username and password. Add them with the file.", Status.WARN)
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED ->
        Status("The VPN refused the username or password. If the account has run out, make a new one and add it here again.", Status.FAIL)
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NONETWORK -> Status("Waiting for the internet.", Status.WARN)
    de.blinkt.openvpn.core.ConnectionStatus.LEVEL_VPNPAUSED -> Status("The VPN is paused.", Status.WARN)
    else -> Status("VPN off.", Status.INFO)
}

/**
 * Settings › VPN. With no details yet: where to get them, and the boxes for them (easiest from the phone).
 * With details: Connect or Disconnect, where the device appears to be, connecting on opening, and changing
 * or removing the details. The box at the top always says how the VPN is.
 */
@Composable
private fun VpnScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val details = remember(Vpn.changes) { Vpn.details(context) }
    var editing by remember { mutableStateOf(false) }
    val adding = details.isEmpty || editing
    var link by remember { mutableStateOf("") }
    var user by remember(details) { mutableStateOf(details.user) }
    var pass by remember(details) { mutableStateOf(details.pass) }
    var said by remember { mutableStateOf<Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var where by remember { mutableStateOf<String?>(null) }
    var phoneAddress by remember { mutableStateOf<String?>(null) }
    BackHandler { if (editing) editing = false else onDone() }

    /** Keeps a file's text, or a link's, with the login, and says whether it could be read. */
    val keep: (String, String, String, String) -> Unit = { config, typedLink, u, p ->
        if (!busy) {
            busy = true
            said = Status("Reading the file…", Status.BUSY)
            scope.launch {
                said = try {
                    val text = withContext(Dispatchers.IO) { if (config.isNotBlank()) config else Vpn.fetch(typedLink) }
                    val reading = Vpn.keep(context, text, u, p)
                    editing = false
                    link = ""
                    Report.note("VPN", "details added: server " + reading.server + if (reading.wantsLogin) ", with a login" else "")
                    if (reading.wantsLogin && (u.isBlank() || p.isBlank())) {
                        Status("Kept the file for " + reading.server + ". It needs a username and password too: add them before connecting.", Status.WARN)
                    } else {
                        Status("Kept. Server: " + reading.server + ". Choose Connect.", Status.OK)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Vpn.VpnProblem) {
                    Status(e.message.orEmpty(), Status.FAIL)
                }
                busy = false
            }
        }
    }
    val connect: () -> Unit = {
        try {
            Vpn.start(context)
            said = null
            where = null
        } catch (e: Vpn.VpnProblem) {
            said = Status(e.message.orEmpty(), Status.FAIL)
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) connect()
        else said = Status("The device wasn't given permission for KueTV's VPN, so it can't connect. Choose Connect to be asked again.", Status.FAIL)
    }
    val connectOrAsk: () -> Unit = {
        val ask = Vpn.permissionAsk(context)
        if (ask != null) {
            said = Status("The device asks once whether KueTV may run a VPN. Choose OK.", Status.INFO)
            permission.launch(ask)
        } else {
            connect()
        }
    }
    // Phone: the file can be picked from the phone itself.
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { Vpn.fromFile(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }) }
                .onSuccess { keep(it, "", user, pass) }
                .onFailure { said = Status(it.message ?: "Couldn't read that file.", Status.FAIL) }
        }
    }
    val checkWhere: () -> Unit = {
        if (!busy) {
            busy = true
            where = "Checking…"
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    runCatching { Vpn.where().toString() }.getOrElse { "Couldn't check: " + (it.message ?: "no answer") }
                }
                where = found
                Report.note("VPN", "where this device appears to be: $found")
                busy = false
            }
        }
    }
    // When the VPN comes on, check where the device now appears to be.
    LaunchedEffect(Vpn.level) { if (Vpn.isOn) checkWhere() }
    // A TV also answers the phone while the details are being added, so they can be sent from there.
    DisposableEffect(adding) {
        val main = Handler(Looper.getMainLooper())
        val server = if (phone || !adding) null else PhoneSetup(onVpn = { c, l, u, p -> main.post { user = u; pass = p; keep(c, l, u, p) } }) { _, _, _, _ -> }
        phoneAddress = server?.start()
        onDispose { server?.stop() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(adding) {
        withFrameNanos { }
        withFrameNanos { }
        if (!phone) runCatching { first.requestFocus() }
    }
    val device = if (phone) "phone" else "TV"
    if (adding) {
        Page(
            "VPN",
            "Add the OpenVPN file from your VPN company, and the username and password that go with it. While the VPN is on it covers the whole $device.",
            // KueTV's answer shows by the buttons instead, where it is seen.
            if (said != null || details.isEmpty) null else vpnStatus(Vpn.level),
        ) {
            phoneAddress?.let { address -> InfoPanel("Easiest: on your phone, on the same Wi-Fi, open $address. Choose the file there and type the username and password.") }
            Field("VPN username", user, modifier = Modifier.focusRequester(first)) { user = it }
            Field("VPN password", pass, secret = true) { pass = it }
            // One press: KueTV fetches the Dublin server's file itself and keeps it with the login above.
            FocusRow(
                if (busy) "Reading…" else "Use VPN Jantit's Dublin server with this login", { keep("", JANTIT_IRELAND, user, pass) }, look = Look.MAIN,
                below = "Free server in Dublin. Make the login on their page first (below).",
            )
            // KueTV's answer, here by the buttons, so it is seen without going back to the top.
            said?.let { StatusBox(it) }
            Heading("Another VPN")
            if (phone) FocusRow("Choose its OpenVPN file on this phone", { pick.launch("*/*") }, look = Look.OTHER)
            Field("Or a link to its OpenVPN file", link) { link = it }
            FocusRow(if (busy) "Reading…" else "Add that link with this login", { keep("", link, user, pass) }, look = Look.OTHER)
            if (!details.isEmpty) FocusRow("Keep the file I have, with this username and password", { keep(details.config, "", user, pass) }, look = Look.OTHER)
            Heading("Where to get a free Irish one")
            InfoPanel(
                "VPN Jantit has a free server in Dublin. On their page, make an account for the server called Ireland 1:\n" +
                    "https://www.vpnjantit.com/create-free-account?server=ie1&type=OpenVPN\n" +
                    "Type the username and password above exactly as their page shows them (they add -vpnjantit.com to the username), then choose \"Use VPN Jantit's Dublin server\".\n" +
                    "A free account there lasts 7 days. When it runs out, KueTV says so: make a new one and add it here again.",
            )
            FocusRow(if (details.isEmpty) "‹ Back" else "‹ Back, without changing anything", { if (editing) editing = false else onDone() }, look = Look.OTHER)
        }
    } else {
        val server = remember(details) { runCatching { Vpn.read(details.config).server }.getOrDefault("?") }
        val auto = remember(Vpn.changes) { Vpn.connectOnOpen(context) }
        // While connecting, the box is looked at again every few seconds, so a connection that is stuck is said to be.
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Vpn.isTrying) { while (Vpn.isTrying) { delay(5_000); tick++ } }
        val shown = remember(tick, Vpn.level, Vpn.step) { vpnStatus(Vpn.level) }
        Page("VPN", "Server: $server. While the VPN is on it covers the whole $device.", said ?: shown) {
            if (Vpn.isOn || Vpn.isTrying) {
                FocusRow("Disconnect", { Vpn.stop(context); said = null }, Modifier.focusRequester(first), look = Look.MAIN)
            } else {
                FocusRow("Connect", connectOrAsk, Modifier.focusRequester(first), look = Look.MAIN)
            }
            FocusRow("Where the $device appears to be: " + (where ?: "press to check"), checkWhere, look = Look.OTHER)
            FocusRow("Connect when KueTV opens: " + if (auto) "on" else "off", { Vpn.connectOnOpen(context, !auto) }, look = Look.OTHER)
            FocusRow("Change the VPN file or login", { editing = true; said = null }, look = Look.OTHER)
            FocusRow("Remove the VPN details", {
                if (Vpn.isOn || Vpn.isTrying) Vpn.stop(context)
                Vpn.forget(context)
                said = Status("Removed.", Status.OK)
            }, look = Look.OTHER)
            Heading("How it works")
            InfoPanel(
                "While the VPN is on, everything on this $device goes through it, not just KueTV.\n" +
                    "Your home Wi-Fi stays reachable, so the phone page and keeping in step still work.\n" +
                    "A free account that runs out shows here as refused: make a new one and add it again.",
            )
            FocusRow("‹ Back", onDone, look = Look.OTHER)
        }
    }
}

/** For the automatic checks only: the VPN page as a TV shows it. */
@Composable
internal fun VpnForChecks() {
    VpnScreen(onDone = {})
}

/**
 * Like produceState, but the starting value is worked out again for each [key]. (produceState keeps the
 * first key's value when the key changes, so a check for "already have it" saw the old one and stopped.)
 */
@Composable
private fun <T> keyedState(key: Any?, initial: () -> T, producer: suspend androidx.compose.runtime.MutableState<T>.() -> Unit): androidx.compose.runtime.State<T> {
    val state = remember(key) { mutableStateOf(initial()) }
    LaunchedEffect(key) { state.producer() }
    return state
}

/** For the automatic checks only: Live TV as a TV shows it, with the lists given. */
@Composable
internal fun LiveTvForChecks(library: Library, favourites: Favourites) {
    val state = remember { BrowseState().apply { section = Section.LIVE } }
    MainScreen(library, state, favourites, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

/** What Settings needs from the app around it: switching provider, and the Google Drive backup. */
internal class SettingsExtras(
    val switchProvider: (Account) -> Unit = {},
    val addProvider: () -> Unit = {},
    val backUp: () -> Unit = {},
    val restore: () -> Unit = {},
    /** What the last backup or restore said. */
    val driveNote: String = "",
)

/** For the automatic checks only: Home as a TV shows it. */
@Composable
internal fun HomeForChecks(library: Library, favourites: Favourites) {
    val state = remember { BrowseState().apply { section = Section.HOME } }
    MainScreen(library, state, favourites, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

/** For the automatic checks only: Films as a TV shows it. [onPlay] is told each time something is played. */
@Composable
internal fun FilmsForChecks(library: Library, favourites: Favourites, onPlay: () -> Unit) {
    val state = remember { BrowseState().apply { section = Section.FILMS } }
    MainScreen(library, state, favourites, { _, _, _, _ -> onPlay() }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

/** For the automatic checks only: the TV Guide as a TV shows it. */
@Composable
internal fun GuideForChecks(library: Library, favourites: Favourites) {
    val state = remember { BrowseState().apply { section = Section.GUIDE } }
    MainScreen(library, state, favourites, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
}

/** What an add-on gives, in a few words. */
private fun gives(addOn: AddOn): String {
    val films = addOn.catalogues.any { !it.startsWith("tv|") }
    val tv = addOn.catalogues.any { it.startsWith("tv|") }
    return listOfNotNull(
        "streams".takeIf { addOn.streams }, "subtitles".takeIf { addOn.subtitles },
        "films and series to browse".takeIf { films }, "TV channels".takeIf { tv },
    ).joinToString(", ").ifEmpty { "nothing KueTV uses" }
}


/**
 * Removes an add-on from KueTV and, when signed in to Stremio, from the Stremio account too, so it doesn't come
 * back next time. [say] is told what happened, then told again once Stremio has answered.
 */
private fun removeAddOn(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, addOn: AddOn, say: (String) -> Unit) {
    AddOnStore.remove(context, addOn)
    if (Stremio.key(context).isEmpty()) {
        say("Removed " + addOn.name + ".")
        return
    }
    say("Removed " + addOn.name + " from KueTV. Removing it from your Stremio account…")
    scope.launch {
        val more = withContext(Dispatchers.IO) {
            try {
                Stremio.removeFromAccount(context, addOn.base, addOn.name)
            } catch (e: AddOnProblem) {
                "Couldn't remove it from your Stremio account, so it will come back. " + e.message
            }
        }
        say("Removed " + addOn.name + " from KueTV. " + more)
    }
}

/** For the automatic checks only: the My add-ons page as a TV shows it. */
@Composable
internal fun AddOnsForChecks() {
    AddOnsScreen(onDone = {})
}

// --------------------------------------------------------------------------------------------- your things

/**
 * Settings › Your things: History (everything played, newest first; OK plays it again) and Profiles (switch, add,
 * a PIN, a kids profile). [first] lands the remote on the page's first row.
 */
@Composable
private fun YourThingsPage(first: () -> Modifier, sub: @Composable (String) -> Unit, play: (History.Entry) -> Unit) {
    val context = LocalContext.current
    val profiles = remember(Profiles.changes) { Profiles.all(context) }
    val current = remember(Profiles.changes) { Profiles.current(context) }
    // Switching to a profile with a PIN: the PIN is asked for first.
    var pinFor by remember { mutableStateOf<Profiles.Profile?>(null) }
    var typedPin by remember { mutableStateOf("") }
    var said by remember { mutableStateOf<Status?>(null) }
    // Adding a profile: its name, and whether it is a kids profile.
    var adding by remember { mutableStateOf<Boolean?>(null) }
    var newName by remember { mutableStateOf("") }
    var settingPin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    sub("Profiles · using " + current.name)
    said?.let { StatusBox(it) }
    val asking = pinFor
    when {
        asking != null -> {
            Field("PIN for " + asking.name, typedPin, secret = true, modifier = first()) { typedPin = it.filter(Char::isDigit).take(8) }
            FocusRow("Switch to " + asking.name, {
                if (typedPin == asking.pin) {
                    Profiles.switchTo(context, asking.id)
                    pinFor = null
                } else {
                    said = Status("That isn't the PIN for " + asking.name + ".", Status.FAIL)
                }
                typedPin = ""
            }, look = Look.MAIN)
            FocusRow("Cancel", { pinFor = null; typedPin = "" }, look = Look.OTHER)
        }
        adding != null -> {
            Field("Name", newName, modifier = first()) { newName = it.take(24) }
            FocusRow("Kids profile: " + if (adding == true) "on, hides adult categories" else "off", { adding = adding != true }, look = Look.OTHER)
            FocusRow("Add this profile", {
                val made = Profiles.add(context, newName, adding == true)
                said = Status("Added " + made.name + ". Choose it below to switch to it.", Status.OK)
                adding = null
                newName = ""
            }, look = Look.MAIN)
            FocusRow("Cancel", { adding = null; newName = "" }, look = Look.OTHER)
        }
        settingPin -> {
            Field("New PIN for " + current.name + " (numbers only; leave empty for none)", newPin, secret = true, modifier = first()) { newPin = it.filter(Char::isDigit).take(8) }
            FocusRow("Save the PIN", {
                Profiles.setPin(context, current.id, newPin)
                said = Status(if (newPin.isEmpty()) current.name + " has no PIN now." else current.name + " now asks for its PIN.", Status.OK)
                settingPin = false
                newPin = ""
            }, look = Look.MAIN)
            FocusRow("Cancel", { settingPin = false; newPin = "" }, look = Look.OTHER)
        }
        else -> {
            var given = false
            for (p in profiles) {
                val using = p.id == current.id
                FocusRow(
                    (if (using) "● " else "") + (if (p.picture.isEmpty()) "" else p.picture + " ") + p.name + (if (p.kids) " · kids" else "") + (if (p.pin.isNotEmpty()) " · PIN" else ""),
                    {
                        when {
                            using -> Unit
                            p.pin.isEmpty() -> Profiles.switchTo(context, p.id)
                            else -> { pinFor = p; said = null }
                        }
                    },
                    if (!given) { given = true; first() } else Modifier,
                    look = Look.OTHER,
                    below = when {
                        using -> "In use"
                        p.id.isEmpty() -> "OK to switch to it"
                        else -> "OK to switch to it · hold OK to remove it and everything kept for it"
                    },
                    onHold = if (using || p.id.isEmpty()) null else ({ Profiles.remove(context, p.id); said = Status("Removed " + p.name + ".", Status.OK) }),
                )
            }
            FocusRow("Picture for " + current.name + ": " + current.picture.ifEmpty { "none" }, { Profiles.nextPicture(context, current.id) }, look = Look.OTHER, below = "OK for the next one")
            FocusRow("Add a profile", { adding = false; said = null }, look = Look.OTHER)
            FocusRow("Add a kids profile", { adding = true; newName = "Kids"; said = null }, look = Look.OTHER)
            FocusRow(if (current.pin.isEmpty()) "Set a PIN for " + current.name else "Change or remove " + current.name + "'s PIN", { settingPin = true; said = null }, look = Look.OTHER)
        }
    }

    val history = remember(History.changes, Profiles.changes) { History.all(context) }
    sub("History" + if (history.isEmpty()) "" else " · " + history.size)
    if (history.isEmpty()) Note("Nothing played yet in this profile.")
    val clock = remember { java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK) }
    for (e in history.take(40)) {
        FocusRow(
            e.name, { play(e) }, look = Look.OTHER,
            below = when (e.kind) { History.LIVE -> "Live TV"; History.FILM -> "Film or episode"; else -> "Catch-up or recording" } + " · " + clock.format(java.util.Date(e.atMs)),
        )
    }
    if (history.isNotEmpty()) FocusRow("Clear history", { History.clear(context) }, look = Look.OTHER, below = "Only this profile's")
}

/**
 * Hold OK on a channel in Live TV: what can be done with it. Favourite or not; in or out of each of the user's own
 * categories, or a new one made with it; or a name of the user's own. Back, or a choice, closes it.
 */
@Composable
private fun ChannelMenu(channel: ChannelGroup, login: Boolean, favourites: Favourites, mine: List<MyCategories.Mine>, onClose: () -> Unit, onPickToHide: () -> Unit = {}) {
    val context = LocalContext.current
    val favKeys = channel.versions.map { Favourites.key(it.channel, login) }
    val favourite = favKeys.any { it in favourites.keys }
    // Typing a name: for a new category (true), or for the channel (false); null while not typing.
    var naming by remember { mutableStateOf<Boolean?>(null) }
    var typed by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        LaunchedEffect(naming) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        // The OK that opened this is still held down when it appears: only a fresh press counts.
        val armed = remember { booleanArrayOf(false) }
        Column(
            Modifier.width(500.dp).background(Color(0xFF0E1626), RoundedCornerShape(18.dp)).padding(14.dp)
                .verticalScroll(rememberScrollState())
                .onPreviewKeyEvent { e ->
                    if (armed[0]) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) { armed[0] = true; false } else true
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(channel.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            when (naming) {
                null -> {
                    FocusRow(if (favourite) "★ Remove from favourites" else "★ Add to favourites", { favourites.toggleAll(favKeys); onClose() }, Modifier.focusRequester(first), look = Look.OTHER)
                    for (m in mine) {
                        val inIt = channel.id in m.channels
                        FocusRow((if (inIt) "Take out of " else "Add to ") + "📁 " + m.name, { MyCategories.toggle(context, m.id, channel.id); onClose() }, look = Look.OTHER)
                    }
                    FocusRow("New category with this channel", { typed = ""; naming = true }, look = Look.OTHER)
                    FocusRow("Rename this channel", { typed = channel.name; naming = false }, look = Look.OTHER)
                    FocusRow("Hide this channel", { HiddenChannels.hide(context, channel.versions.map { it.channel.id }); onClose() }, look = Look.OTHER)
                    FocusRow("Pick channels to hide", { onPickToHide(); onClose() }, look = Look.OTHER, below = "OK picks each one; then hide them all at once")
                    FocusRow("Close", onClose, look = Look.OTHER)
                }
                else -> {
                    val forCategory = naming == true
                    Field(if (forCategory) "The new category's name" else "The channel's name (empty puts your provider's back)", typed, modifier = Modifier.focusRequester(first)) { typed = it.take(40) }
                    FocusRow(if (forCategory) "Make the category" else "Save the name", {
                        if (forCategory) {
                            val made = MyCategories.make(context, typed, channel.id)
                            kueToast(context, "Made 📁 " + made.name + ". It is in Live TV's categories.")
                        } else {
                            Renames.set(context, channel.versions.map { it.channel.id }, typed)
                        }
                        onClose()
                    }, look = Look.MAIN)
                    FocusRow("Cancel", { naming = null }, look = Look.OTHER)
                }
            }
        }
    }
}
