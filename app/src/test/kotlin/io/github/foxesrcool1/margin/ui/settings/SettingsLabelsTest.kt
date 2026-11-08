package io.github.foxesrcool1.margin.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLabelsTest {

    @Test
    fun dayBoundaryLabelsNameMidnightAndMorningHours() {
        assertEquals("Midnight", dayBoundaryLabel(0))
        assertEquals("1 AM", dayBoundaryLabel(1))
        assertEquals("6 AM", dayBoundaryLabel(6))
    }
}
