package io.github.foxesrcool1.margin.core.reading

/**
 * How far into a book, as the user reads it. The library, Home and the EPUB
 * reader all go through here, so the same book shows the same number.
 */
object ReadingProgress {
    /** From this far on, a book counts as finished. */
    const val DONE_FROM = 0.995

    /** Whole percent, rounded down, 0 to 100. The small sum on top stops 0.57 * 100 = 56.99999999999999 from reading as 56. */
    fun percent(progress: Double): Int = (progress * 100 + 1e-9).toInt().coerceIn(0, 100)

    /** "12%", "Done" from [DONE_FROM] on, and null for a book that was not started. A started book shows at least "1%". */
    fun mark(progress: Double): String? = when {
        progress <= 0.0 -> null
        progress >= DONE_FROM -> "Done"
        else -> "${percent(progress).coerceAtLeast(1)}%"
    }
}
