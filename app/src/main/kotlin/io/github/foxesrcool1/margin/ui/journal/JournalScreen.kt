package io.github.foxesrcool1.margin.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import io.github.foxesrcool1.margin.ui.split.rememberPageOpener
import io.github.foxesrcool1.margin.design.components.EinkTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.core.ink.PageTemplate
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import io.github.foxesrcool1.margin.ui.ink.InkNoteActivity
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.habits.DayBoundary
import io.github.foxesrcool1.margin.core.habits.HabitSummary
import io.github.foxesrcool1.margin.core.habits.HabitsRepository
import io.github.foxesrcool1.margin.core.journal.JournalGoal
import io.github.foxesrcool1.margin.core.journal.saveJournalEntry
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.notes.NoteText
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.routine.PartOfDay
import io.github.foxesrcool1.margin.core.routine.RoutineRepository
import io.github.foxesrcool1.margin.core.routine.RoutineStatus
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.components.einkFieldBorder
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.LocalWideScreen
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.TextPromptDialog
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "JournalScreen"
private const val HABITS_PER_PAGE = 3

/** The word count waits for the typing to pause, like the one in the note editor. */
private const val COUNT_QUIET_MILLIS = 400L

private enum class JournalMode { Day, Month, Routine }

/**
 * The Journal tab: one entry per day, a month view, and the habits.
 *
 * A day can hold a typed entry, a handwritten one, or both. The handwritten
 * one opens in the ink screen, as a file of its own next to the typed one.
 * Nothing on this screen scrolls: the day fits, and the month is one page by
 * definition.
 */
@Composable
fun JournalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fixedToday: LocalDate? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = remember(context) { DataRoot.repository(context) }
    val opener = rememberPageOpener()
    val habits = remember(data) { HabitsRepository(data) }
    val routine = remember(data) { RoutineRepository(data) }
    val settings = remember(context) { SettingsStore(context) }
    val goal by settings.journalGoal.collectAsStateWithLifecycle(initialValue = 0)

    var boundaryHour by remember { mutableIntStateOf(DayBoundary.DEFAULT_HOUR) }
    var refresh by remember { mutableIntStateOf(0) }

    // Worked out again each time the screen comes back and after each change.
    // A tablet that is left on this screen overnight wakes up on it in the
    // morning, and with a date worked out only once, the first ticks of the
    // new day went onto yesterday. A tap works it out once more, at the moment
    // of the tap: the screen may have been awake across the day boundary.
    fun dateNow(): LocalDate =
        fixedToday ?: DayBoundary(boundaryHour).dateOf(Instant.now(), ZoneId.systemDefault())
    val today = fixedToday ?: remember(boundaryHour, refresh) { dateNow() }

    var mode by remember { mutableStateOf(JournalMode.Day) }
    var viewDate by remember { mutableStateOf(today) }
    var shownToday by remember { mutableStateOf(today) }
    // The day of the month the user chose. Month stepping clamps to short
    // months, and this brings the 31st back after a February.
    var anchorDay by remember { mutableIntStateOf(today.dayOfMonth) }

    var entryText by remember { mutableStateOf("") }
    var savedText by remember { mutableStateOf("") }
    // The day the two texts above belong to. The day on show changes first and
    // the entry of the new day arrives a moment later; a save in between must
    // not write the old text into the new day.
    var textDate by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf(false) }
    // The text when the edit began, for Cancel. A pause saves the edit on the
    // way, so Cancel cannot just forget it.
    var editStartText by remember { mutableStateOf("") }
    var saveProblem by remember { mutableStateOf(false) }
    var words by remember { mutableIntStateOf(0) }

    // A new day has started. The view follows it if it was on "today", and if
    // nothing is being written: an open edit stays on the day it belongs to.
    LaunchedEffect(today) {
        if (today != shownToday) {
            if (!editing && viewDate == shownToday) {
                viewDate = today
                anchorDay = today.dayOfMonth
            }
            shownToday = today
        }
    }
    var summaries by remember { mutableStateOf<List<HabitSummary>>(emptyList()) }
    var routineRows by remember { mutableStateOf<List<RoutineStatus>>(emptyList()) }
    var daysWithEntries by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var hasInkEntry by remember { mutableStateOf(false) }

    // Coming back from the ink screen has to show that the day now has a
    // handwritten page, in the day view and in the month view.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    var optionsFor by remember { mutableStateOf<HabitSummary?>(null) }
    var addingHabit by remember { mutableStateOf(false) }
    var addingRoutineItem by remember { mutableStateOf(false) }
    var routineOptionsFor by remember { mutableStateOf<RoutineStatus?>(null) }
    var renaming by remember { mutableStateOf<HabitSummary?>(null) }

    LaunchedEffect(viewDate, refresh) {
        val date = viewDate
        val loaded = withContext(AppDispatchers.io) {
            val document = habits.load()
            LoadedDay(
                entry = data.journalEntry(date).orEmpty(),
                summaries = habits.summaries(today),
                monthDays = data.journalDatesIn(date.year).toSet(),
                boundaryHour = document.dayBoundaryHour,
                routine = routine.statuses(today),
            ) to data.hasInkJournalEntry(date)
        }.let { (day, ink) ->
            hasInkEntry = ink
            day
        }
        // An edit in progress is not thrown away by coming back to the screen.
        if (editing) {
            summaries = loaded.summaries
            daysWithEntries = loaded.monthDays
            routineRows = loaded.routine
            return@LaunchedEffect
        }
        entryText = loaded.entry
        savedText = loaded.entry
        textDate = date
        summaries = loaded.summaries
        daysWithEntries = loaded.monthDays
        boundaryHour = loaded.boundaryHour
        routineRows = loaded.routine
    }

    // The word count waits for the typing to pause, and the next key cancels
    // the wait. At rest it is worked out at once. Either way it runs off the
    // main thread, so a long entry never holds up a key.
    LaunchedEffect(entryText, editing) {
        if (editing) delay(COUNT_QUIET_MILLIS)
        val now = entryText
        words = withContext(AppDispatchers.io) { NoteText.wordCount(now) }
    }

    // Two saves of one day can cross on the background threads, and the
    // older text must not land last. So each save notes the newest text of
    // its day here, and the writes take turns and write that. See NoteSaver.
    val newestText = remember { mutableMapOf<String, String>() }
    val writeLock = remember { Any() }
    fun writeNewest(date: java.time.LocalDate, text: String): Boolean = synchronized(writeLock) {
        data.saveJournalEntry(date, newestText[date.toString()] ?: text)
    }

    /**
     * Writes the text on show to the day it belongs to, and then runs
     * [onSaved]. A write that fails runs nothing, reloads nothing and leaves
     * the edit open with the words in it: the reload would put the old file
     * over them.
     */
    fun saveEntry(onSaved: () -> Unit = {}) {
        // Both taken now. The text belongs to the day it was written for.
        val text = entryText
        val date = textDate
        newestText[date.toString()] = text
        scope.launch {
            val written = withContext(AppDispatchers.io) { writeNewest(date, text) }
            if (written) {
                if (date == textDate) {
                    savedText = text
                    saveProblem = false
                }
                AppLog.i(TAG, "Saved the entry for $date")
                onSaved()
                refresh++
            } else {
                AppLog.e(TAG, "Could not save the entry for $date")
                if (date == textDate) saveProblem = true
            }
        }
    }

    /**
     * Ends an edit by saving it, and then runs [then]. Every control that
     * shows another day or another view goes through this. Without it,
     * Previous and Next moved the day under an open edit, and Save then wrote
     * today's text over the entry of another day. When the save fails the edit
     * stays open on its own day and [then] does not run.
     */
    fun commitEdit(then: () -> Unit) {
        if (editing && entryText != savedText) {
            saveEntry {
                editing = false
                then()
            }
        } else {
            editing = false
            then()
        }
    }

    // The Home key, Back and "Today" all take this screen away with no
    // warning, and an entry that was being written must not go with it. The
    // coroutine scope is already gone by then, so this write is a plain one.
    //
    // It does not look at the editing flag: Save sets that flag off before
    // its write has run, and if the screen goes away in between, this is the
    // write that keeps the words.
    fun saveOnTheWayOut(reason: String) {
        if (entryText == savedText) return
        val date = textDate
        val text = entryText
        newestText[date.toString()] = text
        runCatching { writeNewest(date, text) }
            .onSuccess { written ->
                if (written) savedText = text
                AppLog.i(TAG, "Saved the entry for $date ($reason): $written")
            }
            .onFailure { AppLog.e(TAG, "Lost the entry for $date ($reason)", it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { saveOnTheWayOut("the screen closed") }
    }
    // Another app in front: Android may stop this process without a word.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        saveOnTheWayOut("the app went to the back")
    }
    // The split screen can hand the Journal on to the next screen, which reads
    // the entry as it starts, before this one stops. A pause comes first.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        saveOnTheWayOut("the screen paused")
    }

    fun openHandwriting() {
        runCatching {
            opener.openInkNote(
                StorageLayout.journalPath(viewDate, handwritten = true),
                JournalStrings.dayTitle(viewDate),
                PageTemplate.Lined,
            )
        }.onFailure { AppLog.e(TAG, "Could not open the handwritten entry", it) }
    }

    fun step(by: Long) = commitEdit {
        if (mode == JournalMode.Day) {
            viewDate = viewDate.plusDays(by)
            anchorDay = viewDate.dayOfMonth
        } else {
            viewDate = JournalStrings.stepMonth(viewDate, anchorDay, by)
        }
    }

    ScreenScaffold(
        title = when (mode) {
            JournalMode.Day -> JournalStrings.dayTitle(viewDate)
            JournalMode.Month -> JournalStrings.monthTitle(viewDate)
            JournalMode.Routine -> "Routine"
        },
        shortTitle = if (mode == JournalMode.Day) JournalStrings.shortDayTitle(viewDate) else null,
        overline = if (mode == JournalMode.Routine) "Journal" else JournalStrings.dayOverline(viewDate, today),
        plant = Plants.Journal,
        modifier = modifier,
        // The routine is a page inside the Journal, so its way back leads to
        // the day, and only the day leads Home.
        onBack = if (mode == JournalMode.Routine) ({ mode = JournalMode.Day }) else onBack,
        backIcon = if (mode == JournalMode.Routine) Lucide.ArrowLeft else Lucide.House,
        backLabel = if (mode == JournalMode.Routine) "Back to the day" else "Home",
        actions = {
            if (mode == JournalMode.Routine) {
                IconPressButton(
                    icon = Lucide.Plus,
                    label = "Add a routine item",
                    bordered = true,
                    onClick = { addingRoutineItem = true },
                )
            } else {
                IconPressButton(
                    icon = Lucide.ChevronLeft,
                    label = if (mode == JournalMode.Day) "The day before" else "The month before",
                    onClick = { step(-1) },
                )
                IconPressButton(
                    icon = Lucide.CalendarDays,
                    label = "Month",
                    selected = mode == JournalMode.Month,
                    onClick = {
                        commitEdit {
                            mode = if (mode == JournalMode.Day) JournalMode.Month else JournalMode.Day
                        }
                    },
                )
                IconPressButton(
                    icon = Lucide.ChevronRight,
                    label = if (mode == JournalMode.Day) "The day after" else "The month after",
                    onClick = { step(1) },
                )
            }
        },
    ) {
        val wide = LocalWideScreen.current

        when (mode) {
            JournalMode.Routine -> PagedList(
                items = routineRows,
                pageSize = 4,
                rowHeight = RoutineRowHeight,
                emptyText = "No routine yet. Press the plus sign to add the first item.",
                modifier = Modifier.weight(1f),
            ) { index, status ->
                RoutineRow(
                    status = status,
                    isFirst = index == 0,
                    isLast = index == routineRows.size - 1,
                    onToggle = {
                        val day = dateNow()
                        scope.launch {
                            withContext(AppDispatchers.io) {
                                routine.toggle(status.item.id, day)
                            }
                            refresh++
                        }
                    },
                    onMove = { by ->
                        scope.launch {
                            withContext(AppDispatchers.io) { routine.move(status.item.id, by) }
                            refresh++
                        }
                    },
                    onOptions = { routineOptionsFor = status },
                )
            }

            JournalMode.Month -> MonthView(
                month = viewDate,
                daysWithEntries = daysWithEntries,
                today = today,
                selected = viewDate,
                onSelectDay = { day ->
                    viewDate = day
                    anchorDay = day.dayOfMonth
                    mode = JournalMode.Day
                },
                modifier = Modifier.weight(1f),
            )

            JournalMode.Day -> {
                val entry: @Composable (Modifier) -> Unit = { entryModifier ->
                    DayEntry(
                        text = entryText,
                        savedText = savedText,
                        editing = editing,
                        hasInkEntry = hasInkEntry,
                        words = words,
                        goal = goal,
                        saveProblem = saveProblem,
                        onText = { entryText = it },
                        onEdit = {
                            editStartText = savedText
                            saveProblem = false
                            editing = true
                        },
                        onSave = { saveEntry { editing = false } },
                        onCancel = {
                            val original = editStartText
                            entryText = original
                            saveProblem = false
                            // A pause may have saved the edit already. Then the
                            // text from before the edit has to be written back.
                            if (savedText != original) saveEntry { editing = false } else editing = false
                        },
                        onGoal = {
                            scope.launch { settings.setJournalGoal(JournalGoal.next(goal)) }
                        },
                        onHandwrite = { openHandwriting() },
                        modifier = entryModifier,
                    )
                }
                val habitList: @Composable (Modifier) -> Unit = { listModifier ->
                    Column(modifier = listModifier) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CapsLabel(text = "Habits", style = EinkType.capsSmall, modifier = Modifier.weight(1f))
                            IconPressButton(icon = Lucide.ListPlus, label = "Add a habit", onClick = { addingHabit = true })
                            IconPressButton(
                                icon = Lucide.ListChecks,
                                label = "Routine",
                                onClick = { commitEdit { mode = JournalMode.Routine } },
                            )
                        }
                        HairlineDivider(color = EinkColors.Faded)
                        PagedList(
                            items = summaries,
                            pageSize = HABITS_PER_PAGE,
                            rowHeight = HabitRowHeight,
                            emptyText = "No habits yet",
                            modifier = Modifier.weight(1f),
                        ) { _, summary ->
                            HabitRow(
                                summary = summary,
                                onToggle = {
                                    val day = dateNow()
                                    scope.launch {
                                        withContext(AppDispatchers.io) {
                                            habits.toggle(summary.habit.id, day)
                                        }
                                        refresh++
                                    }
                                },
                                onOptions = { optionsFor = summary },
                            )
                        }
                    }
                }

                if (wide) {
                    // On its side the tablet has room for both halves of the
                    // day next to each other, and not for one under the other.
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(EinkDimens.blockGap),
                    ) {
                        entry(Modifier.weight(1f))
                        habitList(Modifier.weight(1f))
                    }
                } else {
                    entry(Modifier.fillMaxWidth())
                    // While the entry is being typed it has the page to itself.
                    // The keyboard covers the habits then anyway.
                    if (!editing) {
                        Spacer(modifier = Modifier.height(EinkDimens.targetGap))
                        habitList(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }

    val selected = optionsFor
    if (selected != null) {
        OptionsDialog(
            title = selected.habit.name,
            onDismiss = { optionsFor = null },
            options = listOf(
                DialogOption(label = "Rename", icon = Lucide.Pencil) {
                    renaming = selected
                    optionsFor = null
                },
                DialogOption(label = "Archive", icon = Lucide.Archive) {
                    scope.launch {
                        withContext(AppDispatchers.io) {
                            habits.setArchived(selected.habit.id, true)
                        }
                        refresh++
                    }
                    optionsFor = null
                },
                DialogOption(
                    label = "Longest streak: ${selected.longestStreak}",
                    enabled = false,
                    icon = Lucide.Flame,
                    onSelect = { },
                ),
            ),
        )
    }

    val routineItem = routineOptionsFor?.item
    if (routineItem != null) {
        OptionsDialog(
            title = routineItem.label,
            onDismiss = { routineOptionsFor = null },
            options = PartOfDay.entries.map { part ->
                DialogOption(
                    label = part.title,
                    icon = if (part == routineItem.part) Lucide.Check else null,
                ) {
                    scope.launch {
                        withContext(AppDispatchers.io) { routine.setPart(routineItem.id, part) }
                        refresh++
                    }
                    routineOptionsFor = null
                }
            } + DialogOption(label = "Remove", icon = Lucide.Trash2) {
                scope.launch {
                    withContext(AppDispatchers.io) { routine.remove(routineItem.id) }
                    refresh++
                }
                routineOptionsFor = null
            },
        )
    }

    if (addingRoutineItem) {
        TextPromptDialog(
            title = "New routine item",
            confirmText = "Add",
            onConfirm = { label ->
                addingRoutineItem = false
                scope.launch {
                    withContext(AppDispatchers.io) { routine.add(label) }
                    refresh++
                }
            },
            onDismiss = { addingRoutineItem = false },
        )
    }

    if (addingHabit) {
        TextPromptDialog(
            title = "New habit",
            confirmText = "Add",
            onConfirm = { name ->
                addingHabit = false
                scope.launch {
                    withContext(AppDispatchers.io) { habits.add(name, today) }
                    refresh++
                }
            },
            onDismiss = { addingHabit = false },
        )
    }

    val beingRenamed = renaming
    if (beingRenamed != null) {
        TextPromptDialog(
            title = "Rename habit",
            initialValue = beingRenamed.habit.name,
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    withContext(AppDispatchers.io) {
                        habits.rename(beingRenamed.habit.id, name)
                    }
                    refresh++
                }
            },
            onDismiss = { renaming = null },
        )
    }
}

/** Everything one load of the screen needs, read in one trip to the disk. */
private data class LoadedDay(
    val entry: String,
    val summaries: List<HabitSummary>,
    val monthDays: Set<LocalDate>,
    val boundaryHour: Int,
    val routine: List<RoutineStatus>,
)

/** A habit row: the name, the dots under it, and the rule. */
private val HabitRowHeight = 74.dp

/**
 * A routine row: the name and the two arrows, the part of the day and where it
 * leads under them, and the rule. At 86 dp the small line under the name lost
 * its bottom half.
 */
private val RoutineRowHeight = 96.dp

/**
 * The written entry of one day.
 *
 * At rest it is the first lines of the entry with two icons beside them: one
 * to type, one to write by hand. While it is being typed it is a text box with
 * a tick to save and a cross to give up.
 *
 * The label row also holds the word count, "212 / 750" against the daily goal.
 * Pressing it moves to the next goal. A day that has reached the goal gets a
 * check in front of the count, and nothing else.
 */
@Composable
private fun DayEntry(
    text: String,
    savedText: String,
    editing: Boolean,
    hasInkEntry: Boolean,
    words: Int,
    goal: Int,
    saveProblem: Boolean,
    onText: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onGoal: () -> Unit,
    onHandwrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // The count sits in the label row. There is no room for a row of its
        // own under the entry: upright it pushed a habit off the page, and in
        // half of a split screen the page was already full. The row is 56 dp
        // tall, so the count is a target big enough to press. In a narrow
        // column the word "Entry" gives way to the count, and in the narrowest
        // the count gets small. The icons on the right always keep their room.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val roomy = maxWidth >= 300.dp
            val tight = maxWidth < 240.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (roomy) CapsLabel(text = "Entry", style = EinkType.capsSmall)
                    InvertPressButton(
                        text = JournalGoal.shortCountText(words, goal),
                        onClick = onGoal,
                        bordered = false,
                        compact = true,
                        icon = if (JournalGoal.reached(words, goal)) Lucide.Check else null,
                        iconSize = if (tight) 16.dp else 20.dp,
                        iconGap = if (tight) 4.dp else 10.dp,
                        contentPadding = PaddingValues(horizontal = if (tight) 4.dp else 8.dp, vertical = 8.dp),
                    )
                }
                if (editing) {
                    IconPressButton(icon = Lucide.X, label = "Cancel", onClick = onCancel)
                    IconPressButton(icon = Lucide.Check, label = "Save", bordered = true, onClick = onSave)
                } else {
                    IconPressButton(
                        icon = Lucide.Keyboard,
                        label = if (savedText.isBlank()) "Type an entry" else "Edit the entry",
                        onClick = onEdit,
                    )
                    IconPressButton(
                        icon = Lucide.Signature,
                        label = if (hasInkEntry) "Open the handwritten entry" else "Write by hand",
                        selected = hasInkEntry,
                        onClick = onHandwrite,
                    )
                }
            }
        }
        HairlineDivider(color = EinkColors.Faded)
        Spacer(modifier = Modifier.height(10.dp))

        if (editing) {
            EinkTextField(
                value = text,
                onValueChange = onText,
                textStyle = EinkType.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .einkFieldBorder()
                    .padding(14.dp),
            )
        } else {
            EinkText(
                text = savedText.ifBlank { "Nothing written yet." },
                style = EinkType.body.copy(
                    color = if (savedText.isBlank()) EinkColors.Faded else EinkColors.Ink,
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            )
        }
        if (saveProblem) {
            Spacer(modifier = Modifier.height(6.dp))
            CapsLabel(text = "Could not save", style = EinkType.capsSmall)
        }
    }
}
