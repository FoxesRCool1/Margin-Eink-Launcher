package io.github.foxesrcool1.margin.ui.split

import android.app.Activity
import io.github.foxesrcool1.margin.ui.home.LauncherRoute

/**
 * The way back for a split screen that went to a new screen.
 *
 * Home gives its split screen to a note, a book or the Continue row, and
 * lets go of it: one page must not be open in two places. When that screen
 * closes, Back should land where the user was. So Home says which tab was on
 * show ([leftFrom]), the screen says what stands in its second half as it
 * closes ([handBack]), and Home asks for both when it comes back ([take]).
 *
 * The screens are separate activities, so this lives in the process. It
 * holds a few plain values, is only touched from the main thread, and is
 * gone with the process: after that Home starts plain, as it always did.
 */
object SplitReturn {
    private var route: LauncherRoute? = null
    private var carry: SplitCarry? = null
    private var closed = false

    /** Home gave its split screen away while [route] was on show. */
    fun leftFrom(route: LauncherRoute) {
        this.route = route
        carry = null
        closed = false
    }

    /**
     * The screen that took the split screen along is closing. [carry] is what
     * stands in its second half, or null when the user closed the split screen
     * there. Ignored when Home gave nothing away. The last screen to close
     * wins, which is right when one screen opens a book and closes itself.
     */
    fun handBack(carry: SplitCarry?) {
        if (route == null) return
        this.carry = carry
        closed = true
    }

    /** Home was asked for as a whole, by the Home key: nothing is waiting for it. */
    fun forget() {
        route = null
        carry = null
        closed = false
    }

    /** What Home puts back, once. Null while the screen is still open, or when Home gave nothing away. */
    fun take(): SplitRestore? {
        val from = route
        if (from == null || !closed) return null
        return SplitRestore(from, carry).also { forget() }
    }
}

/** The tab Home goes back to, and the page for its second half. Null: the user closed the split screen. */
data class SplitRestore(val route: LauncherRoute, val carry: SplitCarry?) {

    /** True when [resolve] has to look at a file, so it must not run on the main thread. */
    val needsFiles: Boolean get() = carry?.page.let { it is PanePage.TypedNote || it is PanePage.InkNote }

    /**
     * What Home really shows. A note that is gone is not restored: the second
     * half opens on the choice instead. A page that the tab would show too
     * stays in the second half, and Home goes back to Home: one page is never
     * open twice. [exists] says whether the file of a note is still there.
     */
    fun resolve(exists: (PanePage) -> Boolean): SplitRestore {
        val taken = carry ?: return this
        val page = if (taken.page.isNote() && !exists(taken.page)) PanePage.Choose else taken.page
        val tab = if (page.clashesWith(PanePage.Tab(route))) LauncherRoute.Home else route
        return SplitRestore(tab, SplitCarry(page, taken.swapped))
    }

    private fun PanePage.isNote() = this is PanePage.TypedNote || this is PanePage.InkNote
}

/**
 * Call this from `onPause` of a screen that can be started with a split
 * screen. When it closes while it was started with one, Home gets the page of
 * the second half back, or nothing if the split screen was closed there.
 */
fun Activity.handSplitBack(split: SplitState) {
    if (isFinishing && intent.splitCarry() != null) SplitReturn.handBack(split.carry())
}
