package io.github.foxesrcool1.margin.core.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressTest {

    @Test
    fun notStartedIsNothing() {
        assertNull(ReadingProgress.mark(0.0))
    }

    @Test
    fun tinyProgressRoundsToOnePercent() {
        assertEquals("1%", ReadingProgress.mark(0.004))
    }

    @Test
    fun midProgressShowsPercent() {
        assertEquals("12%", ReadingProgress.mark(0.12))
    }

    @Test
    fun floatingPointHalfPercentStillShowsFiftySeven() {
        assertEquals("57%", ReadingProgress.mark(0.57))
    }

    @Test
    fun roundsDownNeverUp() {
        assertEquals("91%", ReadingProgress.mark(0.919))
    }

    @Test
    fun justUnderDoneShowsPercent() {
        assertEquals("99%", ReadingProgress.mark(0.994))
    }

    @Test
    fun doneFromNineNineFive() {
        assertEquals("Done", ReadingProgress.mark(0.995))
    }

    @Test
    fun fullProgressIsDone() {
        assertEquals("Done", ReadingProgress.mark(1.0))
    }

    @Test
    fun percentRoundsDown() {
        assertEquals(57, ReadingProgress.percent(0.57))
    }

    @Test
    fun percentOfNothingIsZero() {
        assertEquals(0, ReadingProgress.percent(0.0))
    }

    @Test
    fun percentOfFullIsHundred() {
        assertEquals(100, ReadingProgress.percent(1.0))
    }

    @Test
    fun percentAboveFullStaysAtHundred() {
        assertEquals(100, ReadingProgress.percent(1.5))
    }
}
