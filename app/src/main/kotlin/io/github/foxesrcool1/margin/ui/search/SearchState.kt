package io.github.foxesrcool1.margin.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.foxesrcool1.margin.core.search.SearchHit

/**
 * The search and its results, held on the launcher so they stay on show when
 * the route moves to another tab and back. A search that finds a journal
 * entry opens that day in the Journal tab; Back from there comes back here
 * with the same words and the same results.
 */
class SearchState {
    var query by mutableStateOf("")
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
    // The results stay hidden until the first search, so the screen does not
    // open with "Nothing found" on it.
    var searched by mutableStateOf(false)

    /** Back to an empty search, for the next time Search opens from Home. */
    fun clear() {
        query = ""
        hits = emptyList()
        searched = false
    }
}
