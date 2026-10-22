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

    /**
     * Every source set that goes into an APK: main, and the debug and release
     * parts. The tablet runs the debug build, so its own code counts too.
     */
    private val sources: List<Pair<String, List<String>>> by lazy {
        val src = listOf(File("src"), File("app/src")).first { File(it, "main/kotlin").isDirectory }
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

    private fun breaks(pattern: Regex, allowed: Set<String> = emptySet(), only: (String) -> Boolean = { true }): List<String> =
        sources
            .filter { (path, _) -> only(path) && allowed.none { path.endsWith(it) } }
            .flatMap { (path, lines) ->
                lines.mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//").trim()
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
        "Thread.sleep holds a thread for nothing. Post a delayed runnable, or use delay() in a coroutine.",
        breaks(Regex("""Thread\.sleep\(""")),
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
