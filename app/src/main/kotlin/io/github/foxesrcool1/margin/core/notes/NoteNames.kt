package io.github.foxesrcool1.margin.core.notes

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The default name for a note the user left untitled: the time it was made,
 * like the journal titles, "5 November 2026 17.33". No colon or slash, so it
 * is safe in a file name. Callers add their own prefix.
 */
private val NAME = DateTimeFormatter.ofPattern("d MMMM yyyy HH.mm", Locale.ENGLISH)

fun timestampedNoteName(now: LocalDateTime = LocalDateTime.now()): String =
    NAME.format(now)
