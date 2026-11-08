package io.github.foxesrcool1.margin.core.window

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenWindowSaveTest {

    @Test
    fun `two quick changes save in order, so the last one is what is read back`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        ScreenWindow.forget()

        ScreenWindow.change(activity) { it.copy(landscape = false) }
        ScreenWindow.change(activity) { it.copy(landscape = true) }
        ScreenWindow.flush()

        ScreenWindow.forget()
        assertEquals(true, ScreenWindow.settings(ApplicationProvider.getApplicationContext()).landscape)
    }
}
