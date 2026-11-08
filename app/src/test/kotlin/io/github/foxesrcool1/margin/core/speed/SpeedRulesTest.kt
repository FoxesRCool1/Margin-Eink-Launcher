package io.github.foxesrcool1.margin.core.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules in `CLAUDE.md` that keep the app quick and calm, checked on the
 * source itself, so a new screen cannot quietly break one.
 *
 * Each rule is a pattern that must not appear in the app's code, and a short
 * list of the files that may use it on purpose, each for a reason written
 * there. A failure names the file and the line. Fix the code, or, when the
 * use really is on purpose, add the file here with the reason.
 */
class SpeedRulesTest {

    private companion object {
        const val PACKAGE_PATH = "io/github/foxesrcool1/margin/"
    }

    /** The source root that holds `main/kotlin`, found from the test's working directory. */
    private val src: File by lazy {
        listOf(File("src"), File("app/src")).first { File(it, "main/kotlin").isDirectory }
    }

    /**
     * Every source set that goes into an APK: main, and the debug and release
     * parts. The tablet runs the debug build, so its own code counts too.
     */
    private val sources: List<Pair<String, List<String>>> by lazy {
        listOf("main", "debug", "release", "viwoods", "generic")
            .map { File(src, "$it/kotlin") }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it.relativeTo(root).invariantSeparatorsPath.removePrefix(PACKAGE_PATH) to it.readLines() }
                    .toList()
            }
    }

    /**
     * The code part of a line: everything before a `//` comment. A `//` inside a
     * string literal, or right after a `:` (as in "https://"), is not a comment.
     */
    private fun codeOf(line: String): String {
        var quoted = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"' && (i == 0 || line[i - 1] != '\\') && !line.startsWith("'\"'", i - 1)) quoted = !quoted
            if (!quoted && line.startsWith("//", i) && (i == 0 || line[i - 1] != ':')) return line.substring(0, i)
        }
        return line
    }

    private fun breaks(pattern: Regex, allowed: Set<String> = emptySet(), only: (String) -> Boolean = { true }): List<String> =
        sources
            .filter { (path, _) -> only(path) && allowed.none { path.endsWith(it) } }
            .flatMap { (path, lines) ->
                lines.mapIndexedNotNull { index, line ->
                    val code = codeOf(line).trim()
                    val comment = code.startsWith("*") || code.startsWith("/*")
                    if (!comment && pattern.containsMatchIn(code)) "$path:${index + 1}: ${line.trim()}" else null
                }
            }

    private fun assertNone(rule: String, found: List<String>) {
        assertTrue("$rule\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `the sources are there to check`() {
        assertTrue(sources.size > 100)
        // Paths start at the package, so the rules below can name folders.
        assertTrue(sources.any { it.first == "HomeActivity.kt" })
        assertTrue(sources.any { it.first == "core/window/ScreenWindow.kt" })
        // The debug build's own code is read too.
        assertTrue(sources.any { it.first == "ui/dev/DevPanel.kt" })
    }

    @Test
    fun `nothing blocks the main thread on a coroutine`() = assertNone(
        "runBlocking stalls the thread it runs on. Use a coroutine and AppDispatchers.io.",
        breaks(
            Regex("""\brunBlocking\b"""),
            // Reads the window settings once, before the first frame, so the
            // window does not turn or flash after it is up. Timed in the log.
            allowed = setOf("core/window/ScreenWindow.kt"),
        ),
    )

    @Test
    fun `nothing sleeps`() = assertNone(
        "Thread.sleep, SystemClock.sleep and TimeUnit.*.sleep hold a thread for nothing. Post a delayed runnable, or use delay() in a coroutine.",
        breaks(Regex("""Thread\.sleep\(|SystemClock\.sleep\(|TimeUnit\.\w+\.sleep\(""")),
    )

    @Test
    fun `screens use the app dispatcher for slow work`() = assertNone(
        "Screens use AppDispatchers.io, not Dispatchers.IO. See core/threads/AppDispatchers.kt.",
        breaks(Regex("""Dispatchers\.IO\b"""), only = { !it.startsWith("core/") }),
    )

    @Test
    fun `nothing scrolls`() = assertNone(
        "E-ink rule 2: paginate, do not scroll. Use PagedList.",
        breaks(Regex("""\b(LazyColumn|LazyRow|LazyVerticalGrid|verticalScroll|horizontalScroll)\b""")),
    )

    @Test
    fun `nothing moves`() = assertNone(
        "E-ink rule 1: no animations, no transitions, no crossfades.",
        breaks(
            Regex(
                """\b(AnimatedVisibility|AnimatedContent|Crossfade|animateContentSize|animate[A-Z]\w*AsState|""" +
                    """rememberInfiniteTransition|updateTransition|animateScrollTo|animateScrollBy)\b""",
            ),
        ),
    )

    @Test
    fun `no Material`() = assertNone(
        "The app does not depend on Compose Material: it brings ripples, elevation and animated indication.",
        breaks(Regex("""import androidx\.compose\.material""")),
    )

    @Test
    fun `the start of Home does not load a book engine`() {
        // The files Home is built from, before any book is opened. Readium
        // and the PDF renderer are large, and loading them here would slow
        // down every start of the launcher.
        val homeStart = listOf(
            "MarginApp.kt",
            "HomeActivity.kt",
            "ui/home/",
            "ui/common/",
            "ui/split/",
            "design/",
            "core/window/",
            "core/log/",
            "core/settings/",
            "core/speed/",
        )
        assertNone(
            "Home must start without Readium or the PDF renderer. Keep them behind the reader activities.",
            breaks(
                Regex("""import (org\.readium|android\.graphics\.pdf)"""),
                only = { path -> homeStart.any { path == it || path.startsWith(it) } },
            ),
        )
    }

    @Test
    fun `every activity in the manifest keeps its orientation and screen size on a turn`() {
        val manifest = File(src, "main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val tags = Regex("""<activity[\s>][^>]*>""").findAll(manifest).map { it.value }.toList()
        assertTrue("The manifest declares activities to check", tags.isNotEmpty())
        val bad = tags.filter { tag ->
            val changes = Regex("""android:configChanges="([^"]*)"""").find(tag)?.groupValues?.get(1).orEmpty()
            !(changes.contains("orientation") && changes.contains("screenSize"))
        }
        assertNone(
            "Every <activity> in the manifest must declare android:configChanges with orientation and screenSize, so a turn does not restart it.",
            bad,
        )
    }

    @Test
    fun `every activity attaches the screen window in onCreate`() {
        val main = File(src, "main/kotlin/$PACKAGE_PATH")
        val activityClass = Regex("""class\s+\w+[^{\n]*:\s*(\w+\.)?(Component)?Activity\(\)""")
        val files = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val activities = files.filter { activityClass.containsMatchIn(it.readText()) }
        assertTrue("The main source has activities to check", activities.isNotEmpty())
        val missing = activities
            .filter { !it.readText().contains("ScreenWindow.attach(this)") }
            .map { it.relativeTo(main).invariantSeparatorsPath }
        assertNone("Every Activity must call ScreenWindow.attach(this) in onCreate.", missing)
    }

    @Test
    fun `startActivity is called only from the files that may`() {
        // Pages open notes, books and apps through the page opener. The rest start
        // a screen of this app, a system panel or a link, none of them a note, book or app.
        val allowed = setOf(
            "ui/split/PageOpener.kt", "ui/split/SplitPane.kt", "ui/split/AdjacentApps.kt", "ui/split/BesideActivity.kt",
            "ui/apps/AppsRepository.kt", "ui/settings/SettingsScreen.kt", "ui/common/WebLinks.kt",
            "ui/devicetest/DeviceTestActivity.kt", "ui/home/Clock.kt", "ui/reading/epub/EpubReaderActivity.kt",
        )
        val callers = breaks(Regex("""startActivity\("""), only = { it.startsWith("ui/") })
            .map { it.substringBefore(":") }
            .toSet()
        assertTrue("startActivity is allowed only in $allowed, but is also in: ${callers - allowed}", allowed.containsAll(callers))
    }

    @Test
    fun `the first own frame of a report names the place in this app`() {
        val frames = listOf(
            "android.os.StrictMode.onReadFromDisk",
            "io.github.foxesrcool1.margin.core.speed.SpeedWatch.report",
            "io.github.foxesrcool1.margin.ui.writing.NoteEditorScreenKt.rememberNoteEditor\$saveOnTheWayOut",
            "io.github.foxesrcool1.margin.HomeActivity.onPause",
        )
        assertEquals("ui.writing.NoteEditorScreenKt.rememberNoteEditor\$saveOnTheWayOut", SpeedWatch.firstOwnFrame(frames))
        assertEquals("android.os.Foo.bar", SpeedWatch.firstOwnFrame(listOf("android.os.Foo.bar")))
        assertEquals("unknown", SpeedWatch.firstOwnFrame(emptyList()))
    }

    @Test
    fun `an on purpose name further down the stack is found`() {
        val frames = listOf(
            "android.os.StrictMode.onReadFromDisk",
            "io.github.foxesrcool1.margin.core.storage.LocalFileStore.write\$lambda\$0",
            "io.github.foxesrcool1.margin.ui.writing.NoteEditorScreenKt.rememberNoteEditor\$saveOnTheWayOut",
            "io.github.foxesrcool1.margin.HomeActivity.onPause",
        )
        assertTrue(SpeedWatch.onPurpose(frames))
    }

    @Test
    fun `an on purpose onPause further down the stack is found`() {
        val frames = listOf(
            "android.os.StrictMode.onReadFromDisk",
            "io.github.foxesrcool1.margin.core.storage.LocalFileStore.readText",
            "io.github.foxesrcool1.margin.ui.reading.epub.EpubReaderActivity.onPause",
        )
        assertTrue(SpeedWatch.onPurpose(frames))
    }

    @Test
    fun `a stack without an on purpose name is not on purpose`() {
        val frames = listOf(
            "android.os.StrictMode.onReadFromDisk",
            "io.github.foxesrcool1.margin.ui.writing.WritingScreenKt.WritingScreen\$lambda\$1",
        )
        assertEquals(false, SpeedWatch.onPurpose(frames))
    }

    @Test
    fun `an empty stack is not on purpose`() {
        assertEquals(false, SpeedWatch.onPurpose(emptyList()))
    }

    @Test
    fun `a time over its budget is told, and one within it is not`() {
        assertTrue(SpeedWatch.check("A test", 300, 250))
        assertEquals(false, SpeedWatch.check("A test", 250, 250))
    }
}
