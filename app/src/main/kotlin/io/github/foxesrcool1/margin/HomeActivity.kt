package io.github.foxesrcool1.margin

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.foxesrcool1.margin.core.books.LibraryBook
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.habits.DayBoundary
import io.github.foxesrcool1.margin.core.habits.HabitsRepository
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.routine.PartOfDay
import io.github.foxesrcool1.margin.core.routine.RoutineRepository
import io.github.foxesrcool1.margin.core.routine.RoutineStatus
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.ui.apps.AppsScreen
import io.github.foxesrcool1.margin.ui.home.ContinueFinder
import io.github.foxesrcool1.margin.ui.home.ContinueTarget
import io.github.foxesrcool1.margin.ui.home.LauncherRoute
import io.github.foxesrcool1.margin.ui.home.HomeScreen
import io.github.foxesrcool1.margin.ui.home.rememberMinuteClock
import io.github.foxesrcool1.margin.ui.journal.JournalScreen
import io.github.foxesrcool1.margin.ui.reading.ReadingScreen
import io.github.foxesrcool1.margin.ui.ink.InkCanvasView
import io.github.foxesrcool1.margin.ui.writing.WritingScreen
import io.github.foxesrcool1.margin.ui.log.LogViewerScreen
import io.github.foxesrcool1.margin.ui.settings.SettingsTab
import io.github.foxesrcool1.margin.ui.settings.SettingsScreen
import io.github.foxesrcool1.margin.ui.split.BesideActivity
import io.github.foxesrcool1.margin.ui.split.LocalPageOpener
import io.github.foxesrcool1.margin.ui.split.PageOpener
import io.github.foxesrcool1.margin.ui.split.PaneHost
import io.github.foxesrcool1.margin.ui.split.PaneInk
import io.github.foxesrcool1.margin.ui.split.PanePage
import io.github.foxesrcool1.margin.ui.split.ScreenPageOpener
import io.github.foxesrcool1.margin.ui.split.SplitLayout
import io.github.foxesrcool1.margin.ui.split.SplitPane
import io.github.foxesrcool1.margin.ui.split.SplitState
import io.github.foxesrcool1.margin.ui.split.openBookBeside
import io.github.foxesrcool1.margin.ui.split.rememberPageOpener
import io.github.foxesrcool1.margin.ui.split.watchAndroidSplit
import io.github.foxesrcool1.margin.ui.update.UpdateScreen
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val TAG = "HomeActivity"

/**
 * The home screen.
 *
 * `singleTask` in the manifest means the Home key brings this same task
 * forward instead of starting a second copy. [onNewIntent] then fires, and
 * that is the moment to go back to Home, whatever screen was open.
 *
 * The reader and the editors will run in their own activities from Step 6 on,
 * so the Home key and Recents keep working the way the user expects.
 */
class HomeActivity : ComponentActivity() {

    /**
     * Held on the activity, not inside the composition, so [onNewIntent] can
     * reach it.
     */
    private val route: MutableState<LauncherRoute> = mutableStateOf(LauncherRoute.Home)

    /** Ctrl+N on a Bluetooth keyboard bumps this, and the Writing tab notices. */
    private val newNoteRequests = mutableIntStateOf(0)

    /** True from Ctrl+N until the Writing tab takes the request, so it is acted on once. */
    private var newNoteWanted = false

    private var startLogged = false

    /** When the last change of tab was asked for, for its time budget. */
    private var routeChangedAt = 0L

    /**
     * The split screen. The main half is whatever tab is open, and it cannot
     * show the same page as the second half.
     */
    private val split = SplitState(
        mainPage = { route.value.takeIf { it != LauncherRoute.Home }?.let(PanePage::Tab) },
    )

    /**
     * Opens notes and books from the tabs. With the split screen open, the new
     * screen takes the second half with it, and Home lets go of its own copy:
     * one page must not be open in two places.
     */
    private val opener: PageOpener by lazy {
        ScreenPageOpener(this, carry = { split.carry() }, afterCarry = { resetToHome() })
    }

    private lateinit var paneInk: PaneInk

    private val paneHost = object : PaneHost {
        override fun openBook(book: LibraryBook) = openBookBeside(this@HomeActivity, split, book, this)

        override fun paneInk(canvas: InkCanvasView?) = paneInk.serve(canvas)

        override fun keeper(): Intent = BesideActivity.intent(
            this@HomeActivity,
            route.value.takeIf { it != LauncherRoute.Home }?.let(PanePage::Tab) ?: PanePage.Choose,
        )

        override fun leave() = resetToHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i(TAG, "onCreate")
        ScreenWindow.attach(this)
        logStartTime()
        paneInk = PaneInk(this, lifecycleScope)
        watchAndroidSplit(split)

        setContent {
            val context = LocalContext.current
            val settings = remember(context) { SettingsStore(context) }
            val artOn by settings.botanicalArt.collectAsStateWithLifecycle(initialValue = true)

            EinkTheme(botanicalArt = artOn) {
                SplitLayout(
                    split = split,
                    main = {
                        CompositionLocalProvider(LocalPageOpener provides opener) {
                            LauncherHost(
                                route = route.value,
                                onRoute = { next ->
                                    routeChangedAt = android.os.SystemClock.uptimeMillis()
                                    route.value = next
                                    split.mainChanged()
                                },
                                onOpenDemo = { openDemo() },
                                onOpenNote = opener::openTypedNote,
                                onOpenInkNote = { path, title -> opener.openInkNote(path, title) },
                                onQuickNote = { quickNote() },
                                onBackAtHome = split::close,
                                newNoteRequests = newNoteRequests.intValue,
                                takeNewNoteRequest = { newNoteWanted.also { newNoteWanted = false } },
                                takeRouteChangedAt = {
                                    routeChangedAt.also { routeChangedAt = 0L }
                                },
                            )
                        }
                    },
                    pane = { SplitPane(split, paneHost) },
                )
            }
        }
    }

    /** Home, with no second half. After a page left for a screen of its own. */
    private fun resetToHome() {
        routeChangedAt = 0L
        route.value = LauncherRoute.Home
        split.close()
    }

    override fun onResume() {
        super.onResume()
        paneInk.resume()
    }

    override fun onPause() {
        // The tablet must stop drawing on the glass before another screen shows.
        paneInk.pause()
        super.onPause()
    }

    /**
     * How long the home screen took, from the start of the process to the
     * first frame. There is no profiler on this tablet, so the log is where
     * a slow start shows up. Step 10 asks for this number.
     */
    private fun logStartTime() {
        if (startLogged) return
        startLogged = true
        window.decorView.post {
            val sinceProcessStart = android.os.SystemClock.elapsedRealtime() -
                android.os.Process.getStartElapsedRealtime()
            AppLog.i(TAG, "Home screen up $sinceProcessStart ms after the process started")
            io.github.foxesrcool1.margin.core.speed.SpeedWatch.coldStart(sinceProcessStart)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home key sends us this. Always land on Home, whole.
        AppLog.i(TAG, "onNewIntent: back to Home")
        resetToHome()
    }

    /**
     * Ctrl+N makes a note from anywhere, which is what a writer with a
     * keyboard expects. It lands in the Writing tab, so the new note goes into
     * the folder the user is looking at.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N && event?.isCtrlPressed == true) {
            route.value = LauncherRoute.Writing
            split.mainChanged()
            newNoteWanted = true
            newNoteRequests.intValue++
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * One tap from Home makes a note and opens it. The name is the moment it
     * was made, because a note the user has not titled yet still needs to be
     * findable tomorrow.
     */
    private fun quickNote() {
        val notes = NotesRepository(DataRoot.repository(this))
        kotlin.concurrent.thread(name = "eink-quick-note", isDaemon = true) {
            val folder = notes.quickNotePath()
            val stamp = java.time.LocalDateTime.now()
                .withNano(0)
                .toString()
                .replace(':', '-')
            val path = runCatching { notes.createNote(folder, stamp) }.getOrNull()
            runOnUiThread {
                if (path != null) {
                    opener.openTypedNote(path)
                } else {
                    AppLog.e(TAG, "Could not make a quick note")
                }
            }
        }
    }

    private fun openDemo() {
        runCatching {
            startActivity(Intent(this, DemoActivity::class.java))
        }.onFailure { AppLog.e(TAG, "Could not open the demo screen", it) }
    }
}

@Composable
private fun LauncherHost(
    route: LauncherRoute,
    onRoute: (LauncherRoute) -> Unit,
    onOpenDemo: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenInkNote: (path: String, title: String) -> Unit,
    onQuickNote: () -> Unit,
    /** Back on Home itself. Home has nothing behind it, so this only closes the split screen. */
    onBackAtHome: () -> Unit,
    newNoteRequests: Int,
    /** True once for each Ctrl+N. A tab opened later does not see an old request. */
    takeNewNoteRequest: () -> Boolean,
    /** When the user asked for the tab on show, once, and then 0. 0 when the app changed it, not the user. */
    takeRouteChangedAt: () -> Long = { 0L },
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val opener = rememberPageOpener()
    var next by remember { mutableStateOf<RoutineStatus?>(null) }
    // The book or the note to go back to. The old one stays on show until the
    // new one has been read, so the row never goes blank and fills in again.
    var continueTarget by remember { mutableStateOf<ContinueTarget?>(null) }

    // Home is read again every time it comes back, so ticking something off
    // in the Journal, or reading a few pages, shows up here at once. It is
    // also read again when the tablet wakes up on Home: a new day has a new
    // list, and the home screen is where a tablet spends the night. And it is
    // read again each hour, because a part of the day starts on the hour and
    // an Evening item has to turn up in the evening with nobody touching it.
    var wakeUps by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        wakeUps++
        onPauseOrDispose { }
    }
    val minuteClock = rememberMinuteClock()
    val hour by remember { derivedStateOf { minuteClock.value.hour } }
    LaunchedEffect(route, wakeUps, hour) {
        if (route != LauncherRoute.Home) return@LaunchedEffect
        val loaded = kotlinx.coroutines.withContext(io.github.foxesrcool1.margin.core.threads.AppDispatchers.io) {
            val data = DataRoot.repository(context)
            runCatching { nextRoutine(data) }.getOrNull() to
                runCatching { ContinueFinder(data).find() }.getOrNull()
        }
        next = loaded.first
        continueTarget = loaded.second
    }

    val homeLabels by remember(context) { SettingsStore(context).homeLabels }
        .collectAsStateWithLifecycle(initialValue = false)

    // E-ink rule 8: a full refresh after a big screen change, when the user
    // has turned it on. A change of tab is the big change of this activity.
    val fullRefreshOn by remember(context) { SettingsStore(context).fullRefreshOnBigChange }
        .collectAsStateWithLifecycle(initialValue = false)
    // How long a change of tab took to show, from the tap to the frame after
    // it. A launcher that feels quick is one where this stays short.
    LaunchedEffect(route) {
        val asked = takeRouteChangedAt()
        if (asked == 0L) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        io.github.foxesrcool1.margin.core.speed.SpeedWatch.check(
            "The change to ${route.title}",
            android.os.SystemClock.uptimeMillis() - asked,
            io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.SCREEN_CHANGE,
        )
    }

    // Only a real change of tab counts. The first screen after a start, and
    // the setting being turned on, are not one. The refresh waits for the
    // frame of the new tab, so the black is taken away from the new screen.
    var refreshedFor by remember { mutableStateOf(route) }
    LaunchedEffect(route) {
        if (route == refreshedFor) return@LaunchedEffect
        refreshedFor = route
        if (!fullRefreshOn) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        context.findActivity()?.let(io.github.foxesrcool1.margin.core.eink.ScreenRefresh::run)
    }

    // The page of Settings the log or the updates were opened from, so the
    // way back from them lands there and not on the Settings menu. Anywhere
    // outside Settings it is forgotten, so Settings opens on its menu.
    var settingsPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(route) {
        if (route != LauncherRoute.Settings && route.parent != LauncherRoute.Settings) settingsPage = 0
    }

    // Back goes one level up: from the log and the updates to Settings, from
    // a tab to Home. On Home it closes the split screen, and does nothing
    // else, because a home screen has nowhere behind it.
    BackHandler(enabled = true) {
        if (route != LauncherRoute.Home) onRoute(route.parent) else onBackAtHome()
    }

    when (route) {
        LauncherRoute.Home -> HomeScreen(
            showLabels = homeLabels,
            onOpenTab = onRoute,
            onOpenSettings = { onRoute(LauncherRoute.Settings) },
            onQuickNote = onQuickNote,
            nextRoutineLabel = next?.item?.label,
            nextRoutinePart = next?.item?.part ?: PartOfDay.AnyTime,
            continueTarget = continueTarget,
            onContinue = { target ->
                when (target) {
                    is ContinueTarget.Book -> opener.openBook(target.book)
                    is ContinueTarget.TypedNote -> opener.openTypedNote(target.path)
                    is ContinueTarget.InkNote -> opener.openInkNote(target.path, target.title)
                }
            },
            onStartNext = {
                val item = next?.item ?: return@HomeScreen
                val tab = when (item.target) {
                    "read" -> LauncherRoute.Reading
                    "write" -> LauncherRoute.Writing
                    "journal" -> LauncherRoute.Journal
                    "apps" -> LauncherRoute.Apps
                    else -> null
                }
                val component = item.appComponent()
                when {
                    tab != null -> onRoute(tab)
                    component != null -> startApp(context, component.first, component.second)
                    // An item with nothing to open is just something to tick
                    // off, so Start marks it done and moves to the next one.
                    else -> scope.launch {
                        val done = kotlinx.coroutines.withContext(io.github.foxesrcool1.margin.core.threads.AppDispatchers.io) {
                            runCatching { nextRoutine(DataRoot.repository(context), tickOff = item.id) }
                                .onFailure { AppLog.e(TAG, "Could not mark the routine item ${item.id} done", it) }
                        }
                        // A failure leaves the card as it was.
                        done.onSuccess { next = it }
                    }
                }
            },
        )

        LauncherRoute.Reading -> ReadingScreen(
            onBack = { onRoute(LauncherRoute.Home) },
        )

        LauncherRoute.Writing -> WritingScreen(
            onBack = { onRoute(LauncherRoute.Home) },
            onOpenNote = onOpenNote,
            newNoteRequests = newNoteRequests,
            takeNewNoteRequest = takeNewNoteRequest,
            onOpenInkNote = onOpenInkNote,
        )

        LauncherRoute.Journal -> JournalScreen(
            onBack = { onRoute(LauncherRoute.Home) },
        )

        LauncherRoute.Apps -> AppsScreen(
            onBack = { onRoute(LauncherRoute.Home) },
        )

        LauncherRoute.Settings -> SettingsScreen(
            onBack = { onRoute(LauncherRoute.Home) },
            onOpenLog = {
                settingsPage = SettingsTab.About.ordinal
                onRoute(LauncherRoute.Log)
            },
            onOpenDemo = onOpenDemo,
            onOpenUpdates = {
                settingsPage = SettingsTab.About.ordinal
                onRoute(LauncherRoute.Update)
            },
            initialPage = settingsPage,
        )

        LauncherRoute.Log -> LogViewerScreen(
            onBack = { onRoute(route.parent) },
        )

        LauncherRoute.Update -> UpdateScreen(
            onBack = { onRoute(route.parent) },
        )
    }
}

/**
 * The routine item Home offers right now, read from disk. [tickOff] ticks that
 * item off first, for an item with nothing to open.
 */
private fun nextRoutine(data: DataRepository, tickOff: String? = null): RoutineStatus? {
    val hour = HabitsRepository(data).load().dayBoundaryHour
    val now = LocalDateTime.now()
    val today = DayBoundary(hour).dateOf(now)
    val routine = RoutineRepository(data)
    if (tickOff != null) routine.toggle(tickOff, today)
    return routine.next(today, now.toLocalTime(), hour)
}

/** Starts another app from a routine item. */
private fun startApp(context: android.content.Context, packageName: String, className: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(packageName, className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { AppLog.e(TAG, "Could not start $packageName from the routine", it) }
}
