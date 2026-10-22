package io.github.foxesrcool1.margin.ui.writing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import io.github.foxesrcool1.margin.design.components.EinkTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.notes.NoteSaver
import io.github.foxesrcool1.margin.core.notes.NoteText
import io.github.foxesrcool1.margin.core.notes.NotesRepository
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.core.storage.RelativePaths
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.TextPages
import io.github.foxesrcool1.margin.design.components.einkFieldBorder
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.withContext

private const val TAG = "NoteEditorScreen"
private const val AUTOSAVE_QUIET_MILLIS = 1_500L

/** The word count and the title read the whole note, so they wait for the typing to pause. */
private const val COUNT_QUIET_MILLIS = 400L

/** One open typed note: what it holds now, and whether it may be typed into yet. */
class NoteEditor(
    val text: String,
    val loaded: Boolean,
    val status: String,
    val words: Int,
    val title: String,
    val onText: (String) -> Unit,
)

/**
 * Opens one typed note and keeps it saved.
 *
 * It saves itself once the typing stops for a moment and again when the screen
 * goes away, so a note is never lost to a Home key press. The save goes
 * through the storage layer, which writes to a temporary file and renames, so
 * a tablet that runs out of battery keeps the last good version.
 *
 * The full screen editor and the note beside a book in the split screen both
 * stand on this, so there is one place where a note can be lost, and not two.
 *
 * [textWhenNew] is what a note that does not exist yet starts with. Nothing is
 * written until the user types: opening the split screen and closing it again
 * must not leave an empty file behind.
 *
 * [createdWith] is the text of a note that was made a moment ago for this
 * visit, the Home quick note. The file exists from that moment, so when the
 * editor closes with the note still like that (or blank), the file is deleted
 * again. Otherwise every tap on the quick note button would leave a note
 * behind. Null for any other note, and then nothing is ever deleted here.
 */
@Composable
fun rememberNoteEditor(
    notePath: String,
    saveRequests: Int = 0,
    textWhenNew: String = "",
    createdWith: String? = null,
): NoteEditor {
    val context = LocalContext.current
    val notes = remember(context) { NotesRepository(DataRoot.repository(context)) }

    var text by remember(notePath) { mutableStateOf("") }
    var savedText by remember(notePath) { mutableStateOf("") }
    var loaded by remember(notePath) { mutableStateOf(false) }
    var status by remember(notePath) { mutableStateOf("Opening") }

    // True once the file is known to be on the disk: it was there when it
    // was read, or this editor has written it since. A note that was there
    // and is gone was deleted or renamed from the Writing tab beside this
    // editor, and a write now would bring the old file back.
    val onDisk = remember(notePath) { AtomicBoolean(false) }

    // Where this editor writes. It is [notePath] until the file is gone, and
    // then the file the text was saved to instead.
    val current = remember(notePath) { AtomicReference(notePath) }

    // The new file, until the status line has said so.
    val relocated = remember(notePath) { AtomicReference<String?>(null) }

    // Every write of this note goes through here, so the autosave and the
    // save on the way out cannot cross. See NoteSaver.
    val saver = remember(notePath) {
        NoteSaver { content ->
            val target = current.get()
            if (onDisk.get() && !notes.exists(target)) {
                // Typing must not be lost, and the old file must not come
                // back. The text goes to a new file beside where it was.
                val other = notes.freePath(target)
                AppLog.w(TAG, "$target was moved or deleted beside this editor. Saving the text as $other.")
                notes.write(other, content).also {
                    if (it) {
                        current.set(other)
                        relocated.set(other)
                    }
                }
            } else {
                notes.write(target, content).also { if (it) onDisk.set(true) }
            }
        }
    }

    fun savedStatus(): String =
        relocated.getAndSet(null)?.let { "Saved as ${RelativePaths.nameOf(it)}" } ?: "Saved"

    suspend fun save(reason: String) {
        if (text == savedText) return
        val written = withContext(AppDispatchers.io) { saver.save { text } }
        if (written) {
            savedText = saver.savedText ?: savedText
            status = savedStatus()
            AppLog.i(TAG, "Saved ${current.get()} ($reason)")
        } else {
            status = "Could not save"
            AppLog.e(TAG, "Could not save ${current.get()} ($reason)")
        }
    }

    LaunchedEffect(notePath) {
        val started = System.currentTimeMillis()
        val loadedText = withContext(AppDispatchers.io) {
            notes.readOrNull(notePath).also { onDisk.set(notes.exists(notePath)) }
        }
        io.github.foxesrcool1.margin.core.speed.SpeedWatch.check("Opening the note", System.currentTimeMillis() - started, io.github.foxesrcool1.margin.core.speed.SpeedWatch.Budget.OPEN_NOTE)
        if (loadedText == null) {
            // The field stays off. Typing into an empty page here would end
            // with that page saved over a note that is still on the disk.
            status = "This note could not be read"
            AppLog.e(TAG, "Could not read $notePath. The editor stays closed to typing.")
            return@LaunchedEffect
        }
        val start = loadedText.ifEmpty { textWhenNew }
        text = start
        savedText = start
        saver.loaded(start)
        loaded = true
        status = "Ready"
    }

    // Autosave once the typing stops. Every keystroke cancels this and starts
    // it again, so the disk is touched once at the end of a sentence and not
    // once per letter.
    LaunchedEffect(text, loaded) {
        if (!loaded) return@LaunchedEffect
        if (text == savedText) return@LaunchedEffect
        status = "Not saved yet"
        delay(AUTOSAVE_QUIET_MILLIS)
        save("autosave")
    }

    // Another screen may have written this note while this one was stopped: the
    // Writing tab, for a note that also stands beside a book. An editor with no
    // typing in it takes the newer text. One with typing in it keeps it, and the
    // typing is saved as it always was.
    val resumes = remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes.intValue++ }
    LaunchedEffect(resumes.intValue) {
        if (!loaded || text != savedText) return@LaunchedEffect
        val before = text
        val fresh = withContext(AppDispatchers.io) {
            // A note that is gone is the saver's business (it saves beside), not a reason to empty the page.
            if (notes.exists(current.get())) notes.readOrNull(current.get()) else null
        } ?: return@LaunchedEffect
        // Typing may have started while the file was read.
        if (text == before && savedText == before && fresh != before) {
            text = fresh
            savedText = fresh
            saver.loaded(fresh)
            AppLog.i(TAG, "Read ${current.get()} again: it was changed elsewhere")
        }
    }

    // The word count and the title wait for the typing to pause, and the next
    // key cancels the wait. The first count, when the note opens, is at once.
    var words by remember(notePath) { mutableIntStateOf(0) }
    var title by remember(notePath) { mutableStateOf(RelativePaths.nameOf(notePath)) }
    var counted by remember(notePath) { mutableStateOf(false) }
    LaunchedEffect(text, loaded) {
        if (!loaded) return@LaunchedEffect
        if (counted) delay(COUNT_QUIET_MILLIS)
        val now = text
        val fallback = RelativePaths.nameOf(current.get())
        val (count, heading) = withContext(AppDispatchers.io) {
            NoteText.wordCount(now) to NoteText.titleFrom(now, fallback)
        }
        words = count
        title = heading
        counted = true
    }

    LaunchedEffect(saveRequests) {
        if (saveRequests > 0 && loaded) save("keyboard")
    }

    // A just made quick note that nothing was typed into goes away with the
    // editor. The repository checks the file too, see discardUntouched.
    fun discardIfUntouched(): Boolean {
        val made = createdWith ?: return false
        return loaded && current.get() == notePath && notes.discardUntouched(notePath, made, text)
    }

    // The last chance to write, and it does not wait for a thread. A writing
    // app that loses the last sentence is not usable.
    fun saveOnTheWayOut(reason: String, closing: Boolean = false) {
        if (closing && discardIfUntouched()) {
            AppLog.i(TAG, "Nothing was typed into $notePath. The file is deleted.")
            return
        }
        if (!loaded || text == saver.savedText) return
        runCatching { saver.save { text } }
            .onSuccess { written ->
                if (written) {
                    savedText = saver.savedText ?: savedText
                    if (relocated.get() != null) status = savedStatus()
                }
                AppLog.i(TAG, "Saved ${current.get()} ($reason): $written")
            }
            .onFailure { AppLog.e(TAG, "Lost the last edit of ${current.get()} ($reason)", it) }
    }

    // "Done", Back and Escape close the screen, and this runs.
    DisposableEffect(notePath) {
        onDispose { saveOnTheWayOut("the editor closed", closing = true) }
    }
    // The Home key does not close the screen, it only stops it, and Android
    // may end a stopped app without another word. So this is the save that
    // covers the Home key. The one above never runs for it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        saveOnTheWayOut("the editor went to the back")
    }
    // A pause comes before the next screen starts, and a stop only after it
    // is up. The split screen hands a note on to the next screen, which reads
    // it from the disk as it starts, so the note has to be there by then.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        saveOnTheWayOut("the screen paused")
    }

    return NoteEditor(text = text, loaded = loaded, status = status, words = words, title = title, onText = { text = it })
}

/** The full screen editor: one note, and nothing else. */
@Composable
fun NoteEditorScreen(
    notePath: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    saveRequests: Int = 0,
    /** See [rememberNoteEditor]. A note that was just made also opens with the keyboard up. */
    createdWith: String? = null,
) {
    val editor = rememberNoteEditor(notePath, saveRequests, createdWith = createdWith)

    ScreenScaffold(
        title = editor.title,
        overline = RelativePaths.parentOf(notePath),
        modifier = modifier,
        onBack = onClose,
        backIcon = Lucide.ArrowLeft,
        backLabel = "Done",
    ) {
        NoteField(editor = editor, modifier = Modifier.fillMaxWidth().weight(1f), focusOnOpen = createdWith != null)
    }
}

/** The box the note is typed in, with the word count and the save state under it. */
@Composable
fun NoteField(editor: NoteEditor, modifier: Modifier = Modifier, focusOnOpen: Boolean = false) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The field cannot be typed into before the note is read, so the focus waits for that.
    LaunchedEffect(focusOnOpen, editor.loaded) {
        if (focusOnOpen && editor.loaded) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val pages = remember { TextPages() }
    Column(modifier = modifier) {
        EinkTextField(
            value = editor.text,
            onValueChange = editor.onText,
            textStyle = EinkType.reading,
            enabled = editor.loaded,
            pages = pages,
            modifier = Modifier
                .focusRequester(focus)
                .fillMaxWidth()
                .weight(1f)
                .einkFieldBorder(EinkColors.Faded)
                .padding(14.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        val words = editor.words
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(
                text = "$words ${if (words == 1) "word" else "words"}  .  ${editor.status}",
                style = EinkType.capsSmall.copy(color = EinkColors.Faded),
                modifier = Modifier.weight(1f),
            )
            // A note longer than the box is read a page at a time.
            if (pages.count > 1) {
                CapsLabel(
                    text = "${pages.index + 1} / ${pages.count}",
                    style = EinkType.capsSmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
                IconPressButton(
                    icon = Lucide.ChevronLeft,
                    label = "Previous page",
                    onClick = pages::previous,
                    enabled = pages.index > 0,
                    bordered = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconPressButton(
                    icon = Lucide.ChevronRight,
                    label = "Next page",
                    onClick = pages::next,
                    enabled = pages.index < pages.count - 1,
                    bordered = true,
                )
            }
        }
    }
}
