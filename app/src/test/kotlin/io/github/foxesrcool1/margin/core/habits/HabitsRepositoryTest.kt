package io.github.foxesrcool1.margin.core.habits

import io.github.foxesrcool1.margin.core.storage.UnreadableStore
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class HabitsRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var store: LocalFileStore
    private lateinit var habits: HabitsRepository

    private val today = LocalDate.of(2026, 9, 19)

    @Before
    fun setUp() {
        store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store)
        data.ensureFolders()
        habits = HabitsRepository(data)
    }

    @Test
    fun `an empty data folder gives an empty list, not a failure`() {
        val document = habits.load()
        assertTrue(document.habits.isEmpty())
        assertEquals(DayBoundary.DEFAULT_HOUR, document.dayBoundaryHour)
    }

    @Test
    fun `a habit is added and comes back after a reload`() {
        habits.add("Read 30 minutes", today)
        val reloaded = habits.load()
        assertEquals(1, reloaded.habits.size)
        assertEquals("read-30-minutes", reloaded.habits.single().id)
        assertEquals(today, reloaded.habits.single().createdOn)
    }

    @Test
    fun `two habits with the same name get different ids`() {
        habits.add("Read", today)
        habits.add("Read", today)
        assertEquals(listOf("read", "read-2"), habits.load().habits.map { it.id })
    }

    @Test
    fun `a habit is renamed but keeps its id and its history`() {
        habits.add("Read", today)
        habits.toggle("read", today)

        habits.rename("read", "Read a book")

        assertEquals("Read a book", habits.load().habits.single().name)
        assertEquals(setOf(today), habits.datesFor("read"))
    }

    @Test
    fun `archiving keeps the habit and its history`() {
        habits.add("Read", today)
        habits.toggle("read", today)

        habits.setArchived("read", true)

        assertEquals(1, habits.load().habits.size)
        assertTrue(habits.load().active().isEmpty())
        assertEquals(setOf(today), habits.datesFor("read"))

        habits.setArchived("read", false)
        assertEquals(1, habits.load().active().size)
    }

    @Test
    fun `a day is marked and unmarked`() {
        habits.add("Read", today)

        assertTrue(habits.toggle("read", today))
        assertEquals(setOf(today), habits.datesFor("read"))

        assertFalse(habits.toggle("read", today))
        assertTrue(habits.datesFor("read").isEmpty())
    }

    @Test
    fun `marking the same day twice does not write it twice`() {
        habits.add("Read", today)
        habits.toggle("read", today)
        habits.toggle("read", today)
        habits.toggle("read", today)

        assertEquals(listOf(HabitMark("read", today)), habits.loadMarks())
    }

    @Test
    fun `two habits keep separate histories`() {
        habits.add("Read", today)
        habits.add("Walk", today)
        habits.toggle("read", today)
        habits.toggle("walk", today.minusDays(1))

        assertEquals(setOf(today), habits.datesFor("read"))
        assertEquals(setOf(today.minusDays(1)), habits.datesFor("walk"))
    }

    @Test
    fun `the day boundary is saved`() {
        habits.add("Read", today)
        habits.setDayBoundaryHour(6)
        assertEquals(6, habits.load().dayBoundaryHour)
    }

    @Test
    fun `a summary has everything one row needs`() {
        habits.add("Read", today)
        listOf(0L, 1L, 2L).forEach { back -> habits.toggle("read", today.minusDays(back)) }

        val summary = habits.summaries(today).single()
        assertEquals("Read", summary.habit.name)
        assertTrue(summary.doneToday)
        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.longestStreak)
        assertEquals(HabitStreaks.DOT_COUNT, summary.lastDays.size)
        assertEquals(listOf(true, true, true), summary.lastDays.takeLast(3))
    }

    @Test
    fun `a summary for another day shows that day and counts the streak from today`() {
        habits.add("Read", today)
        // Done yesterday and the day before, not today.
        listOf(1L, 2L).forEach { back -> habits.toggle("read", today.minusDays(back)) }

        val past = habits.summaries(today, day = today.minusDays(2)).single()
        assertTrue(past.doneToday)
        assertEquals(listOf(true), past.lastDays.takeLast(1))
        assertEquals(2, past.currentStreak)

        val now = habits.summaries(today).single()
        assertFalse(now.doneToday)
        assertEquals(listOf(true, true, false), now.lastDays.takeLast(3))
    }

    @Test
    fun `an archived habit is left out of the summaries`() {
        habits.add("Read", today)
        habits.add("Walk", today)
        habits.setArchived("walk", true)

        assertEquals(listOf("read"), habits.summaries(today).map { it.habit.id })
    }

    @Test
    fun `a damaged habits file does not lose the log`() {
        habits.add("Read", today)
        habits.toggle("read", today)

        store.writeText(StorageLayout.habitsPath(), "this is not json")

        assertTrue(habits.load().habits.isEmpty())
        assertEquals(setOf(today), habits.datesFor("read"))
    }

    @Test
    fun `the files land where the plan says they do`() {
        habits.add("Read", today)
        habits.toggle("read", today)

        assertTrue(store.exists("habits/habits.json"))
        assertTrue(store.exists("habits/log.csv"))
        assertTrue(store.readText("habits/log.csv")!!.startsWith("date,habit"))
    }

    @Test
    fun `adding an archived habit again brings it back with its days`() {
        habits.add("Read", today)
        habits.toggle("read", today.minusDays(1))
        habits.setArchived("read", true)

        habits.add(" read ", today)

        val document = habits.load()
        assertEquals(listOf("read"), document.habits.map { it.id })
        assertFalse(document.habits.single().archived)
        assertEquals(setOf(today.minusDays(1)), habits.datesFor("read"))
    }

    @Test
    fun `a log that cannot be read is not written over`() {
        habits.toggle("read", today)
        val before = store.readText(StorageLayout.habitLogPath())

        val blind = HabitsRepository(DataRepository(UnreadableStore(store, StorageLayout.habitLogPath())))
        blind.toggle("read", today.minusDays(1))

        assertEquals(before, store.readText(StorageLayout.habitLogPath()))
    }

    @Test
    fun `a habit list that cannot be read is not written over`() {
        habits.add("Read", today)
        val before = store.readText(StorageLayout.habitsPath())

        val blind = HabitsRepository(DataRepository(UnreadableStore(store, StorageLayout.habitsPath())))
        blind.add("Walk", today)

        assertEquals(before, store.readText(StorageLayout.habitsPath()))
    }
}
