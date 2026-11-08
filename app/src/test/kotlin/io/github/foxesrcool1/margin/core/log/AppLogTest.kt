package io.github.foxesrcool1.margin.core.log

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLogTest {

    @Test
    fun `a burst of lines all reach the file, in order`() {
        AppLog.init(ApplicationProvider.getApplicationContext())
        val threads = (0 until 4).map { thread ->
            Thread { repeat(50) { AppLog.i("Burst", "thread $thread line $it") } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        AppLog.flush()

        val written = AppLog.logFiles().flatMap { it.readLines() }.filter { "Burst" in it }
        assertEquals(200, written.size)
        (0 until 4).forEach { thread ->
            val mine = written.filter { "thread $thread " in it }.map { it.substringAfterLast("line ").toInt() }
            assertEquals((0 until 50).toList(), mine)
        }
    }

    @Test
    fun `only the newest five crash files survive the prune`() {
        AppLog.init(ApplicationProvider.getApplicationContext())
        AppLog.flush()
        val dir = AppLog.logDirectory()!!
        (1..7).forEach { File(dir, "crash-2026-11-0$it-170000000000$it.txt").writeText("boom") }

        AppLog.pruneCrashFiles(dir)

        val crashes = AppLog.logFiles().filter { it.name.startsWith("crash-") }.map { it.name }
        assertEquals((3..7).reversed().map { "crash-2026-11-0$it-170000000000$it.txt" }, crashes)
    }
}
