package io.github.foxesrcool1.margin.core.speed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedWatchStartTest {

    private val home = "io.github.foxesrcool1.margin.HomeActivity"

    @Test
    fun homeStartedTheProcessOnlyWhenItWasTheFirstActivity() {
        assertTrue(SpeedWatch.homeStartedProcess(home, home))
        assertFalse(SpeedWatch.homeStartedProcess("io.github.foxesrcool1.margin.EpubReaderActivity", home))
        assertFalse(SpeedWatch.homeStartedProcess(null, home))
    }
}
