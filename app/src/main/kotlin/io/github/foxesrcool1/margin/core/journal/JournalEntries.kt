package io.github.foxesrcool1.margin.core.journal

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.time.LocalDate

/**
 * Saves the typed entry for a day.
 *
 * An emptied entry must not leave an empty .md file behind, because the
 * month view marks every day that has a file. So a blank text deletes the
 * day's file (that returns true when the file is not there), and real text
 * is written through the usual path.
 */
fun DataRepository.saveJournalEntry(date: LocalDate, text: String): Boolean {
    return if (text.isBlank()) {
        store.delete(StorageLayout.journalPath(date))
    } else {
        writeJournalEntry(date, text)
    }
}
