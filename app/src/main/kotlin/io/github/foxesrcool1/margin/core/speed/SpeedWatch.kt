package io.github.foxesrcool1.margin.core.speed

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import io.github.foxesrcool1.margin.core.log.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val TAG = "Speed"

/**
 * Keeps the app quick, by telling on anything that makes it slower.
 *
 * The owner finds the app quick and wants it to stay that way. There is no
 * profiler on the tablet, so the log is where slowness has to show up, the
 * first time it happens and not after someone notices. Two things write there:
 *
 * - A time budget for each moment the user waits for: the start of Home, a
 *   change of tab, opening a book or a note. A moment over its budget writes
 *   one line that starts with "Slow:". See [check] and [Budget].
 * - In a debug build, Android's StrictMode watches the main thread. Disk or
 *   network work there stalls the screen, and each place in this app that
 *   does it writes one line, once per run. The few places that do it on
 *   purpose are listed in [ON_PURPOSE] and write at a lower level.
 *
 * The rules that keep new code from adding slowness are in `CLAUDE.md`, and
 * `SpeedRulesTest` fails the build when code breaks one of them.
 * `docs/decisions/0017-speed-budget.md` has the reasoning and the numbers.
 */
object SpeedWatch {

    /** The time budgets, in milliseconds. Over budget writes a "Slow:" line. */
    object Budget {
        /** From the start of the process to the first frame of Home. */
        const val COLD_START = 1_500L

        /** From a tap on a tab, or on the split screen, to the frame that shows it. */
        const val SCREEN_CHANGE = 250L

        /** Reading a note from the disk, typed or handwritten. */
        const val OPEN_NOTE = 400L

        /** Opening a book until its first page can show. */
        const val OPEN_BOOK = 1_500L

        /** Reading the window settings before the first frame. It blocks the main thread. */
        const val WINDOW_SETTINGS = 60L
    }

    /** Writes a "Slow:" line when [millis] is over [budget], and nothing when it is not. */
    fun check(what: String, millis: Long, budget: Long): Boolean {
        val over = millis > budget
        if (over) AppLog.w(TAG, "Slow: $what took $millis ms, the budget is $budget ms")
        return over
    }

    @Volatile
    private var coldStartChecked = false

    /**
     * The start of Home, once per run of the app. Home can be built again
     * later in the same run, and the time since the process started means
     * nothing then.
     */
    fun coldStart(millis: Long) {
        if (coldStartChecked) return
        coldStartChecked = true
        check("The start of Home", millis, Budget.COLD_START)
    }

    /**
     * Code that does disk work on the main thread on purpose, each for a
     * reason written where it happens. Matched against every frame of a
     * StrictMode report, not only the first frame of this app.
     */
    private val ON_PURPOSE = listOf(
        // Before the first frame, so the window does not turn or flash after it.
        "ScreenWindow.settings",
        // The log folder, once per start, before anything can log.
        "AppLog.init",
        // A fast pen path that crashed must be known before any screen tries it.
        "FastPenGuard",
        "EinkDevices",
        // The last words of a note or an entry, when the screen goes. A thread
        // could be too late: Android may end the app right after.
        "saveOnTheWayOut",
        "EpubReaderActivity.onPause",
    )

    private val seen = ConcurrentHashMap.newKeySet<String>()

    /**
     * Turns StrictMode on in a debug build. The build the tablet runs while
     * the app is tested is a debug build, so this is on there too. It costs
     * nothing until something breaks a rule.
     */
    fun watchMainThread(debug: Boolean) {
        if (!debug || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            val reporter = Executors.newSingleThreadExecutor { Thread(it, "eink-speed").apply { isDaemon = true } }
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyListener(reporter) { report(it, leak = false) }
                    .build(),
            )
            // A file or a PDF that is never closed holds memory and a file
            // handle, and 4 GB is not much for big scanned books.
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyListener(reporter) { report(it, leak = true) }
                    .build(),
            )
            AppLog.i(TAG, "Watching the main thread")
        }.onFailure { AppLog.w(TAG, "Could not watch the main thread", it) }
    }

    private fun report(violation: Violation, leak: Boolean) {
        val kind = violation.javaClass.simpleName.removeSuffix("Violation")
        val frames = violation.stackTrace.map { "${it.className}.${it.methodName}" }
        val frame = firstOwnFrame(frames)
        // Once per place and kind, or a busy loop would fill the log.
        if (!seen.add("$kind@$frame")) return
        when {
            leak -> AppLog.w(TAG, "Leak: $kind, made at $frame")
            onPurpose(frames) -> AppLog.d(TAG, "Main thread $kind, on purpose: $frame")
            else -> AppLog.w(TAG, "Slow: main thread $kind at $frame")
        }
    }

    /** The first frame of this app in a stack, or of the platform when none is ours. */
    internal fun firstOwnFrame(frames: List<String>): String {
        val own = frames.firstOrNull { it.startsWith(PACKAGE) && !it.contains(".core.speed.") }
        return (own ?: frames.firstOrNull() ?: "unknown").removePrefix("$PACKAGE.")
    }

    /** True when any frame of the stack names a place that does it on purpose. */
    internal fun onPurpose(frames: List<String>): Boolean =
        ON_PURPOSE.any { name -> frames.any { it.contains(name) } }

    private const val PACKAGE = "io.github.foxesrcool1.margin"
}
