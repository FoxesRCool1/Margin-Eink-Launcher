package io.github.foxesrcool1.margin.ui.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressMarkTest {

    @Test
    fun notStartedIsNothing() {
        assertNull(progressMark(0.0))
    }

    @Test
    fun tinyProgressRoundsToOnePercent() {
        assertEquals("1%", progressMark(0.004))
    }

    @Test
    fun midProgressShowsPercent() {
        assertEquals("12%", progressMark(0.12))
    }

    @Test
    fun justUnderDoneShowsPercent() {
        assertEquals("99%", progressMark(0.994))
    }

    @Test
    fun roundsUpToDone() {
        assertEquals("Done", progressMark(0.996))
    }

    @Test
    fun fullProgressIsDone() {
        assertEquals("Done", progressMark(1.0))
    }
}
