package io.github.foxesrcool1.margin.core.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.settings.WindowSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "ScreenWindow"

/**
 * Turns the screen, and hides the Android status bar, for every activity.
 *
 * Both have to be right before the first frame. A window that opens upright
 * and then turns is two full repaints on e-ink, and a status bar that shows
 * and then goes is two more. So the settings are read once, blocking, the
 * first time a window asks, and kept in memory after that. The read is one
 * small file and the log says how long it took.
 *
 * Every activity declares `orientation|screenSize` in the manifest, so a turn
 * does not build the activity again. The open book and the half written note
 * stay as they are, and Compose lays the screen out again at the new size.
 */
object ScreenWindow {

    @Volatile
    private var cached: WindowSettings? = null

    /** One thread, so saves land in the order they were made. */
    private val saver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ScreenWindowSaver").apply { isDaemon = true }
    }

    private val writer = CoroutineScope(SupervisorJob() + saver.asCoroutineDispatcher())

    @Volatile
    private var lastSave: Job? = null

    /** What the windows are set to right now. */
    fun settings(context: Context): WindowSettings {
        cached?.let { return it }
        val started = System.currentTimeMillis()
        val failed = AtomicBoolean(false)
        val loaded = runCatching {
            runBlocking { SettingsStore(context).window.first() }
        }.getOrElse {
            AppLog.w(TAG, "Could not read the window settings, using the defaults", it)
            failed.set(true)
            WindowSettings()
        }
        val took = System.currentTimeMillis() - started
        AppLog.i(TAG, "Read the window settings in $took ms: $loaded")
        io.github.foxesrcool1.margin.core.speed.SpeedWatch.check(
            "Reading the window settings",
            took,
            io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.WINDOW_SETTINGS,
        )
        // A failed read is not cached, so the next call tries the file again.
        if (!failed.get()) cached = loaded
        return loaded
    }

    /**
     * Call from `onCreate`, before the content is set. Sets the window up now,
     * and again each time the activity comes back: the user may have turned
     * the screen in the reader, and Home has to follow.
     */
    fun attach(activity: ComponentActivity) {
        apply(activity)
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) apply(activity)
            },
        )
    }

    fun apply(activity: Activity) {
        val now = settings(activity)
        val wanted = orientationOf(now)
        if (activity.requestedOrientation != wanted) {
            runCatching { activity.requestedOrientation = wanted }
                .onFailure { AppLog.w(TAG, "The screen would not turn", it) }
        }
        applyStatusBar(activity.window, now.statusBarHidden)
    }

    /** Also used by the dialogs: a dialog is a window of its own, with a status bar of its own. */
    fun applyStatusBar(window: Window?, hidden: Boolean) {
        if (window == null) return
        runCatching {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (hidden) {
                // A swipe down from the top edge still brings the bar back for
                // a moment. The tablet's own panel for the front light and
                // Wi-Fi hangs off that bar, so it must stay in reach.
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }.onFailure { AppLog.w(TAG, "Could not change the status bar", it) }
    }

    /** The small icon at the top of every screen calls this. */
    fun toggleLandscape(activity: Activity) {
        change(activity) { it.copy(landscape = !it.landscape) }
    }

    /** Changes the settings, shows the change at once, and saves it in the background. */
    fun change(activity: Activity, transform: (WindowSettings) -> WindowSettings) {
        val next = transform(settings(activity))
        cached = next
        AppLog.i(TAG, "Window settings are now $next")
        apply(activity)
        val store = SettingsStore(activity)
        lastSave = writer.launch {
            runCatching { store.setWindow(next) }
                .onFailure { AppLog.e(TAG, "Could not save the window settings", it) }
        }
    }

    fun orientationOf(settings: WindowSettings): Int = when {
        !settings.landscape -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        settings.landscapeFlipped -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    /** For the tests, which share one process and must not share a setting. */
    internal fun forget() {
        cached = null
    }

    /** Waits until the saves made so far are on disk. For the tests. */
    internal fun flush() {
        val done = CountDownLatch(1)
        lastSave?.invokeOnCompletion { done.countDown() } ?: done.countDown()
        done.await(2, TimeUnit.SECONDS)
    }
}

/** The activity a composable lives in, or null in a preview. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
