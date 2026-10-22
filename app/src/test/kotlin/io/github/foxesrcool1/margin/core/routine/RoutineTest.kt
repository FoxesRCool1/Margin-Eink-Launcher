package io.github.foxesrcool1.margin.core.routine

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonArray
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime

class RoutineTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var store: LocalFileStore
    private lateinit var routine: RoutineRepository

    private val today = LocalDate.of(2026, 9, 19)
    private val noon = LocalTime.NOON

    @Before
    fun setUp() {
        store = LocalFileStore(temporary.newFolder("Margin"))
        val data = DataRepository(store)
        data.ensureFolders()
        routine = RoutineRepository(data)
    }

    // -- The file ---------------------------------------------------------

    @Test
    fun `a routine survives a round trip`() {
        val document = RoutineDocument(
            listOf(
                RoutineItem("journal", "Write the journal", "journal"),
                RoutineItem("read", "Read 30 minutes", "read"),
                RoutineItem("stretch", "Stretch", ""),
            ),
        )
        assertEquals(document, RoutineFile.parse(RoutineFile.serialise(document)))
    }

    @Test
    fun `text that is not this file reads as null`() {
        assertNull(RoutineFile.parse(""))
        assertNull(RoutineFile.parse("not json"))
        assertNull(RoutineFile.parse("""{"items": []}"""))
    }

    @Test
    fun `a broken item is dropped and the rest are kept`() {
        val text = """
            {"version": 1, "items": [
              {"id": "a", "label": "Good"},
              {"id": "", "label": "No id"},
              {"id": "c"},
              {"id": "d", "label": "Also good", "target": "read"}
            ]}
        """.trimIndent()

        assertEquals(listOf("a", "d"), RoutineFile.parse(text)!!.items.map { it.id })
    }

    @Test
    fun `an id is made from the label and never repeats`() {
        assertEquals("read-30-minutes", RoutineFile.idFor("Read 30 minutes", emptySet()))
        assertEquals("read-2", RoutineFile.idFor("Read", setOf("read")))
        assertEquals("item", RoutineFile.idFor("...", emptySet()))
    }

    // -- Targets ----------------------------------------------------------

    @Test
    fun `a target says what to open`() {
        assertTrue(RoutineItem("a", "A", "journal").opensATab)
        assertFalse(RoutineItem("a", "A", "journal").opensAnApp)
        assertFalse(RoutineItem("a", "A", "").opensATab)

        val app = RoutineItem("a", "A", "app:com.example/com.example.Main")
        assertTrue(app.opensAnApp)
        assertEquals("com.example" to "com.example.Main", app.appComponent())
    }

    @Test
    fun `a half written app target opens nothing`() {
        assertNull(RoutineItem("a", "A", "app:").appComponent())
        assertNull(RoutineItem("a", "A", "app:com.example").appComponent())
        assertNull(RoutineItem("a", "A", "app:/com.example.Main").appComponent())
    }

    // -- Order ------------------------------------------------------------

    @Test
    fun `an item moves up and down`() {
        val document = RoutineDocument(
            listOf(
                RoutineItem("a", "A"),
                RoutineItem("b", "B"),
                RoutineItem("c", "C"),
            ),
        )
        assertEquals(listOf("b", "a", "c"), document.move("b", -1).items.map { it.id })
        assertEquals(listOf("a", "c", "b"), document.move("b", 1).items.map { it.id })
    }

    @Test
    fun `moving past the ends does nothing`() {
        val document = RoutineDocument(listOf(RoutineItem("a", "A"), RoutineItem("b", "B")))
        assertEquals(listOf("a", "b"), document.move("a", -1).items.map { it.id })
        assertEquals(listOf("a", "b"), document.move("b", 1).items.map { it.id })
    }

    @Test
    fun `moving something that is not there does nothing`() {
        val document = RoutineDocument(listOf(RoutineItem("a", "A")))
        assertEquals(document, document.move("nothing", 1))
    }

    // -- The repository ---------------------------------------------------

    @Test
    fun `an empty data folder gives an empty routine`() {
        assertTrue(routine.load().items.isEmpty())
        assertNull(routine.next(today, noon))
    }

    @Test
    fun `items are added in order and come back after a reload`() {
        routine.add("Write the journal", "journal")
        routine.add("Read 30 minutes", "read")

        assertEquals(
            listOf("write-the-journal", "read-30-minutes"),
            routine.load().items.map { it.id },
        )
    }

    @Test
    fun `next is the first item that is not ticked off`() {
        routine.add("Journal", "journal")
        routine.add("Read", "read")
        routine.add("Stretch")

        assertEquals("journal", routine.next(today, noon)!!.item.id)

        routine.toggle("journal", today)
        assertEquals("read", routine.next(today, noon)!!.item.id)

        routine.toggle("read", today)
        routine.toggle("stretch", today)
        assertNull(routine.next(today, noon))
    }

    @Test
    fun `next follows the user's order, not the order things were done`() {
        routine.add("First", "")
        routine.add("Second", "")
        routine.toggle("second", today)

        assertEquals("first", routine.next(today, noon)!!.item.id)
    }

    @Test
    fun `a tick can be taken back`() {
        routine.add("Journal", "journal")
        assertTrue(routine.toggle("journal", today))
        assertFalse(routine.toggle("journal", today))
        assertEquals("journal", routine.next(today, noon)!!.item.id)
    }

    @Test
    fun `yesterday's ticks do not carry over`() {
        routine.add("Journal", "journal")
        routine.toggle("journal", today.minusDays(1))

        assertEquals("journal", routine.next(today, noon)!!.item.id)
        assertTrue(routine.statuses(today.minusDays(1)).single().doneToday)
    }

    @Test
    fun `removing an item takes it off the list`() {
        routine.add("Journal", "journal")
        routine.add("Read", "read")
        routine.remove("journal")

        assertEquals(listOf("read"), routine.load().items.map { it.id })
    }

    @Test
    fun `the ticks live in their own file, away from the habits`() {
        routine.add("Journal", "journal")
        routine.toggle("journal", today)

        assertTrue(store.exists("habits/routine.json"))
        assertTrue(store.exists("habits/routine-log.csv"))
        assertFalse(store.exists("habits/log.csv"))
    }

    @Test
    fun `a damaged routine file does not lose the ticks`() {
        routine.add("Journal", "journal")
        routine.toggle("journal", today)

        store.writeText("habits/routine.json", "not json at all")

        assertTrue(routine.load().items.isEmpty())
        routine.add("Journal", "journal")
        assertTrue(routine.statuses(today).single().doneToday)
    }

    // -- Part of the day ----------------------------------------------------

    @Test
    fun `a routine with parts of the day survives a round trip`() {
        val document = RoutineDocument(
            listOf(
                RoutineItem("journal", "Write the journal", "journal", PartOfDay.Morning),
                RoutineItem("read", "Read 30 minutes", "read", PartOfDay.Afternoon),
                RoutineItem("stretch", "Stretch", "", PartOfDay.Evening),
                RoutineItem("any", "Anything", "", PartOfDay.AnyTime),
            ),
        )
        assertEquals(document, RoutineFile.parse(RoutineFile.serialise(document)))
    }

    @Test
    fun `an old file without a part reads as any time and is written back without a part`() {
        val text = """{"version": 1, "items": [{"id": "a", "label": "A", "target": "read"}]}"""

        val parsed = RoutineFile.parse(text)!!
        assertEquals(listOf(PartOfDay.AnyTime), parsed.items.map { it.part })

        val written = RoutineFile.serialise(parsed)
        assertFalse(written.contains("part"))
    }

    @Test
    fun `a part is written into the file and any time is left out`() {
        val evening = RoutineDocument(listOf(RoutineItem("e", "E", "", PartOfDay.Evening)))
        val eveningText = RoutineFile.serialise(evening)
        assertTrue(eveningText.contains("evening"))
        val item = ((Json.parse(eveningText) as JsonObject)["items"] as JsonArray).objects().single()
        assertEquals("evening", item.string("part"))

        val anyTime = RoutineDocument(listOf(RoutineItem("a", "A", "", PartOfDay.AnyTime)))
        assertFalse(RoutineFile.serialise(anyTime).contains("part"))
    }

    @Test
    fun `an unknown part in the file reads as any time`() {
        val text = """{"version": 1, "items": [{"id": "a", "label": "A", "part": "teatime"}]}"""

        assertEquals(listOf(PartOfDay.AnyTime), RoutineFile.parse(text)!!.items.map { it.part })
    }

    @Test
    fun `set part changes one item and survives a reload, and an unknown id changes nothing`() {
        routine.add("Journal", "journal")
        routine.add("Read", "read")

        routine.setPart("journal", PartOfDay.Morning)

        val loaded = routine.load()
        assertEquals(PartOfDay.Morning, loaded.items.first { it.id == "journal" }.part)
        assertEquals(PartOfDay.AnyTime, loaded.items.first { it.id == "read" }.part)

        routine.setPart("no-such-id", PartOfDay.Evening)
        assertEquals(
            listOf(PartOfDay.Morning, PartOfDay.AnyTime),
            routine.load().items.map { it.part },
        )
    }

    @Test
    fun `next waits for the part of the day to start`() {
        routine.add("m")
        routine.add("e")
        routine.add("x")
        routine.setPart("m", PartOfDay.Morning)
        routine.setPart("e", PartOfDay.Evening)

        assertEquals("m", routine.next(today, LocalTime.of(8, 0))!!.item.id)

        routine.toggle("m", today)
        assertEquals("x", routine.next(today, LocalTime.of(8, 0))!!.item.id)

        assertEquals("e", routine.next(today, LocalTime.of(17, 0))!!.item.id)
    }

    @Test
    fun `an earlier part that is still undone stays on offer`() {
        routine.add("a")
        routine.add("x")
        routine.setPart("a", PartOfDay.Afternoon)

        assertEquals("a", routine.next(today, LocalTime.of(20, 0))!!.item.id)
    }

    @Test
    fun `a later part does not block an earlier item behind it`() {
        routine.add("e")
        routine.add("x")
        routine.setPart("e", PartOfDay.Evening)

        assertEquals("x", routine.next(today, LocalTime.of(9, 0))!!.item.id)
    }

    @Test
    fun `next is null when nothing on offer has started`() {
        routine.add("e")
        routine.setPart("e", PartOfDay.Evening)

        assertNull(routine.next(today, LocalTime.of(9, 0)))
    }

    @Test
    fun `the small hours belong to the evening of the day before`() {
        routine.add("e")
        routine.setPart("e", PartOfDay.Evening)

        assertEquals("e", routine.next(today, LocalTime.of(2, 0))!!.item.id)
        assertNull(routine.next(today, LocalTime.of(4, 0)))
    }
}
