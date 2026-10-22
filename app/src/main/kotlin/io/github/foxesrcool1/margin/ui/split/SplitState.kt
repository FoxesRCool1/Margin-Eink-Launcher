package io.github.foxesrcool1.margin.ui.split

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.ui.home.LauncherRoute

private const val TAG = "SplitScreen"

/**
 * The split screen of one activity: whether it is open, which side the second
 * half is on, and what that half shows.
 *
 * The second half keeps a short trail of pages, so a note opened from the
 * Write page there goes back to the Write page, and the Write page goes back
 * to the choice. The first half is the activity's own screen and is never
 * touched by this.
 *
 * [mainPage] says what the first half shows right now, so the second half can
 * refuse the same page. [firstPage] is where the second half starts.
 */
class SplitState(
    private val mainPage: () -> PanePage? = { null },
    private val firstPage: () -> PanePage = { PanePage.Choose },
) {
    var isOpen by mutableStateOf(false)
        private set

    /** False: the second half is under the first, or right of it. True: above it, or left of it. */
    var swapped by mutableStateOf(false)
        private set

    /** A short line for the user, such as why a page did not open. Cleared by the next move. */
    var notice by mutableStateOf<String?>(null)
        private set

    private val trail = mutableStateListOf<PanePage>()

    /** When the last change was asked for, for its time budget. Not state: nothing redraws for it. */
    var changedAt = 0L
        private set

    /**
     * True when the last change came from the main half, not from a tap in
     * the second half. The main half cleans the screen for that change
     * itself, and one tap must not clean it twice.
     */
    var changedByMain = false
        private set

    private fun touched() {
        changedAt = android.os.SystemClock.uptimeMillis()
        changedByMain = false
    }

    /** What the second half shows. */
    val page: PanePage get() = trail.lastOrNull() ?: PanePage.Choose

    /** What the main half shows, or null for a book or the Home screen. */
    fun main(): PanePage? = mainPage()

    fun toggle() = if (isOpen) close() else open()

    fun open(page: PanePage = firstPage(), swapped: Boolean = this.swapped) {
        touched()
        trail.clear()
        trail.add(if (page.clashesWith(mainPage())) PanePage.Choose else page)
        this.swapped = swapped
        notice = null
        isOpen = true
        AppLog.i(TAG, "Open: ${this.page}, swapped $swapped")
    }

    fun close() {
        if (!isOpen) return
        touched()
        isOpen = false
        trail.clear()
        notice = null
        AppLog.i(TAG, "Closed")
    }

    /** Shows [next] in the second half. False, with a notice, when the main half has it open already. */
    fun show(next: PanePage): Boolean {
        if (next == PanePage.Choose || next == PanePage.Tab(LauncherRoute.Home)) {
            home()
            return true
        }
        if (next.clashesWith(mainPage())) {
            notice = SplitStrings.ALREADY_OPEN
            AppLog.i(TAG, "Refused $next: the other half has it open")
            return false
        }
        notice = null
        touched()
        if (trail.lastOrNull() != next) trail.add(next)
        // A long walk through folders must not grow without end.
        while (trail.size > MAX_TRAIL) trail.removeAt(0)
        return true
    }

    /** One page back in the second half. From its first page, back to the choice. */
    fun back() {
        touched()
        notice = null
        if (trail.size > 1) {
            trail.removeAt(trail.size - 1)
        } else {
            home()
        }
    }

    /**
     * Back on the Home screen, which has nothing behind it. The second half
     * steps back to its choice first, and the split closes from the choice.
     */
    fun backAtHome() = if (page == PanePage.Choose) close() else home()

    /** Back to the choice of what goes in the second half. */
    fun home() {
        touched()
        notice = null
        trail.clear()
        trail.add(PanePage.Choose)
    }

    /**
     * The Write page deleted [path], a note or a folder. A second half that
     * shows a note in it goes back to its choice, so that editor cannot write
     * the file back with its next save. True when the main half shows such a
     * note: only the screen around it can close that.
     */
    fun noteDeleted(path: String): Boolean {
        if (isOpen && page.isNoteAtOrInside(path)) home()
        return mainPage().isNoteAtOrInside(path)
    }

    /** Shows [text] at the top of the second half until the next move. */
    fun say(text: String) {
        notice = text
    }

    fun swap() {
        touched()
        swapped = !swapped
        AppLog.i(TAG, "Swapped: $swapped")
    }

    /**
     * The main half now shows something else. If that is what the second half
     * shows, the second half goes back to its choice, so one page is never
     * open twice.
     */
    fun mainChanged() {
        if (isOpen && page.clashesWith(mainPage())) {
            home()
            changedByMain = true
            notice = SplitStrings.ALREADY_OPEN
        }
    }

    /** The page and the side, for a new screen that takes the split screen with it. */
    fun carry(): SplitCarry? = if (isOpen) SplitCarry(page, swapped) else null

    private companion object {
        const val MAX_TRAIL = 12
    }
}

/** The words of the split screen, in one place for the tests. */
object SplitStrings {
    const val ALREADY_OPEN = "That is open in the other half already"
}
