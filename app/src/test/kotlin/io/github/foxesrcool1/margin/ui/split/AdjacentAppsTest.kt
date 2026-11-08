package io.github.foxesrcool1.margin.ui.split

import androidx.activity.ComponentActivity
import io.github.foxesrcool1.margin.ui.apps.LauncherEntry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdjacentAppsTest {

    private val entry = LauncherEntry(packageName = "com.example.notes", className = "com.example.notes.Main", label = "Notes")

    @Test
    fun `an old try is not checked`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        AdjacentApps.noteTried(entry, System.identityHashCode(activity), at = System.currentTimeMillis() - 60_000L)
        assertNull(AdjacentApps.checkResult(activity))
    }

    @Test
    fun `a fresh try on a screen that is not split says so, once`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        AdjacentApps.noteTried(entry, System.identityHashCode(activity))
        val line = AdjacentApps.checkResult(activity)
        assertTrue(line.orEmpty().contains("on its own screen, not beside this one"))
        assertNull(AdjacentApps.checkResult(activity))
    }

    @Test
    fun `a try that worked reports nothing`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        AdjacentApps.noteTried(entry, System.identityHashCode(activity))
        AdjacentApps.triedWorked()
        assertNull(AdjacentApps.checkResult(activity))
    }
}
