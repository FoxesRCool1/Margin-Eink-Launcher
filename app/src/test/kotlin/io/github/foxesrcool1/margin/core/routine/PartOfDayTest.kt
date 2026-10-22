package io.github.foxesrcool1.margin.core.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PartOfDayTest {

    private val boundary = 4

    @Test
    fun `any time and morning have started at every hour of the day`() {
        for (hour in 0..23) {
            val time = LocalTime.of(hour, 0)
            assertTrue(PartOfDay.AnyTime.hasStarted(time, boundary))
            assertTrue(PartOfDay.Morning.hasStarted(time, boundary))
        }
    }

    @Test
    fun `afternoon starts at noon and keeps going into the small hours`() {
        assertFalse(PartOfDay.Afternoon.hasStarted(LocalTime.of(11, 59), boundary))
        assertFalse(PartOfDay.Afternoon.hasStarted(LocalTime.of(4, 0), boundary))
        assertTrue(PartOfDay.Afternoon.hasStarted(LocalTime.of(12, 0), boundary))
        assertTrue(PartOfDay.Afternoon.hasStarted(LocalTime.of(23, 59), boundary))
        assertTrue(PartOfDay.Afternoon.hasStarted(LocalTime.of(3, 59), boundary))
    }

    @Test
    fun `evening starts at five in the afternoon and keeps going into the small hours`() {
        assertFalse(PartOfDay.Evening.hasStarted(LocalTime.of(16, 59), boundary))
        assertTrue(PartOfDay.Evening.hasStarted(LocalTime.of(17, 0), boundary))
        assertTrue(PartOfDay.Evening.hasStarted(LocalTime.of(3, 59), boundary))
        assertFalse(PartOfDay.Evening.hasStarted(LocalTime.of(4, 0), boundary))
    }

    @Test
    fun `with the boundary at midnight the evening ends at midnight`() {
        assertFalse(PartOfDay.Evening.hasStarted(LocalTime.of(16, 59), 0))
        assertTrue(PartOfDay.Evening.hasStarted(LocalTime.of(17, 0), 0))
        assertTrue(PartOfDay.Evening.hasStarted(LocalTime.of(23, 59), 0))
    }

    @Test
    fun `fromKey reads each key back to its part`() {
        assertEquals(PartOfDay.Morning, PartOfDay.fromKey("morning"))
        assertEquals(PartOfDay.Afternoon, PartOfDay.fromKey("afternoon"))
        assertEquals(PartOfDay.Evening, PartOfDay.fromKey("evening"))
    }

    @Test
    fun `fromKey reads an empty key and a missing key as any time`() {
        assertEquals(PartOfDay.AnyTime, PartOfDay.fromKey(""))
        assertEquals(PartOfDay.AnyTime, PartOfDay.fromKey(null))
    }

    @Test
    fun `fromKey reads a key that is not known as any time`() {
        assertEquals(PartOfDay.AnyTime, PartOfDay.fromKey("nonsense"))
    }
}
