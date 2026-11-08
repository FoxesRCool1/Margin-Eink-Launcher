package io.github.foxesrcool1.margin.core.eink

import java.io.File

/**
 * Remembers whether a fast pen path killed the app last time.
 *
 * `runCatching` stops a Java exception. It cannot stop a crash inside a
 * vendor native library, and the public notes say one of the two paths did
 * exactly that in an older test. So before a risky call this writes
 * "trying" to a small file, and after the call it writes "fine". If the app
 * starts and finds "trying", the call never came back: the process died in
 * the middle. The process may also have died for some other reason in that
 * window, so the first death only raises doubt. Two deaths in a row while
 * trying, with no "fine" between them, and the path is refused until someone
 * resets it by hand.
 *
 * The worst case is one crash, once. For a home app that matters, because a
 * home app that dies on start is a tablet that cannot be used.
 */
class FastPenGuard(private val folder: File) {

    enum class State { Untried, Trying, Fine, Crashed }

    fun state(path: FastPenPath): State {
        return when (text(path)) {
            null, "" -> State.Untried
            TRYING, TRYING_AGAIN -> State.Trying
            FINE -> State.Fine
            CRASHED -> State.Crashed
            else -> State.Untried
        }
    }

    /** True when the path may be tried. */
    fun mayTry(path: FastPenPath): Boolean = state(path) != State.Crashed

    /**
     * Call once at start-up. A "trying" that is still there means the last
     * attempt never returned. The first time that is doubt, the second time
     * in a row it becomes "crashed".
     */
    fun settleAfterStart(): List<FastPenPath> {
        val crashed = mutableListOf<FastPenPath>()
        FastPenPath.entries.forEach { path ->
            when (text(path)) {
                TRYING -> write(path, DOUBT)
                TRYING_AGAIN -> {
                    write(path, CRASHED)
                    crashed += path
                }
            }
        }
        return crashed
    }

    fun markTrying(path: FastPenPath) = write(path, if (text(path) == DOUBT) TRYING_AGAIN else TRYING)

    fun markFine(path: FastPenPath) = write(path, FINE)

    /** The owner asks for another try, for example after a firmware update. */
    fun reset(path: FastPenPath) {
        runCatching { fileFor(path).delete() }
    }

    private fun text(path: FastPenPath): String? = runCatching { fileFor(path).readText().trim() }.getOrNull()

    /** Temporary file, then rename, so a death in the middle never leaves half a word. */
    private fun write(path: FastPenPath, text: String) {
        runCatching {
            folder.mkdirs()
            val target = fileFor(path)
            val temporary = File(folder, target.name + ".tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(target)) temporary.delete()
        }
    }

    private fun fileFor(path: FastPenPath): File = File(folder, "fastpen-${path.name.lowercase()}.state")

    private companion object {
        const val TRYING = "trying"
        const val DOUBT = "doubt"
        const val TRYING_AGAIN = "trying again"
        const val FINE = "fine"
        const val CRASHED = "crashed"
    }
}
