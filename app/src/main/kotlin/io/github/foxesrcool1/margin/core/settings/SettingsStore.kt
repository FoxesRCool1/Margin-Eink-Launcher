package io.github.foxesrcool1.margin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import io.github.foxesrcool1.margin.core.journal.JournalGoal
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The small settings the launcher keeps.
 *
 * Plain files hold the user content (Step 4). DataStore holds only these few
 * values, which are cheap to rebuild if they are ever lost.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    /**
     * Pinned apps, in the order the user pinned them. Stored as one string with
     * newlines, because a preference set does not keep an order.
     */
    val pinnedApps: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_PINNED]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setPinnedApps(keys: List<String>) {
        store.edit { prefs ->
            prefs[KEY_PINNED] = keys.take(MAX_PINNED).joinToString("\n")
        }
    }

    suspend fun togglePinned(key: String) {
        store.edit { prefs ->
            val current = prefs[KEY_PINNED]
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val next = when {
                current.contains(key) -> current - key
                // A full list keeps what it has. Dropping the app the user
                // just chose, or the oldest one, would both be a surprise.
                current.size >= MAX_PINNED -> current
                else -> current + key
            }
            prefs[KEY_PINNED] = next.joinToString("\n")
        }
    }

    /** Section 5: the corner line art can be turned off. */
    val botanicalArt: Flow<Boolean> = store.data.map { it[KEY_ART] ?: true }

    suspend fun setBotanicalArt(enabled: Boolean) {
        store.edit { it[KEY_ART] = enabled }
    }

    /**
     * Who paints the line while the pen is down: "off" (this app), "writing"
     * (ViWoods fast pen path A) or "autodraw" (path B). Off until the device
     * test has shown which path works. See decision 0007.
     */
    val fastPenMode: Flow<String> = store.data.map { it[KEY_FAST_PEN] ?: FAST_PEN_OFF }

    suspend fun setFastPenMode(mode: String) {
        store.edit { it[KEY_FAST_PEN] = mode }
    }

    /** How long after pen-up the real stroke replaces the fast one. */
    val inkRedrawDelayMillis: Flow<Long> = store.data.map { it[KEY_INK_DELAY] ?: DEFAULT_INK_DELAY_MILLIS }

    suspend fun setInkRedrawDelayMillis(millis: Long) {
        store.edit { it[KEY_INK_DELAY] = millis.coerceIn(300L, 3000L) }
    }

    /** E-ink rule 8: a full refresh after a big screen change. It is a setting. */
    val fullRefreshOnBigChange: Flow<Boolean> = store.data.map { it[KEY_FULL_REFRESH] ?: false }

    suspend fun setFullRefreshOnBigChange(enabled: Boolean) {
        store.edit { it[KEY_FULL_REFRESH] = enabled }
    }

    /** Home shows four icons. This puts the name of each one under it. */
    val homeLabels: Flow<Boolean> = store.data.map { it[KEY_HOME_LABELS] ?: false }

    suspend fun setHomeLabels(enabled: Boolean) {
        store.edit { it[KEY_HOME_LABELS] = enabled }
    }

    /**
     * How the window is set up: which way the screen is turned, and whether
     * the Android status bar shows. One flow, because an activity needs all
     * of it at once, before its first frame.
     */
    val window: Flow<WindowSettings> = store.data.map { prefs ->
        WindowSettings(
            landscape = prefs[KEY_LANDSCAPE] ?: false,
            landscapeFlipped = prefs[KEY_LANDSCAPE_FLIPPED] ?: false,
            statusBarHidden = prefs[KEY_STATUS_BAR_HIDDEN] ?: true,
        )
    }

    suspend fun setWindow(settings: WindowSettings) {
        store.edit { prefs ->
            prefs[KEY_LANDSCAPE] = settings.landscape
            prefs[KEY_LANDSCAPE_FLIPPED] = settings.landscapeFlipped
            prefs[KEY_STATUS_BAR_HIDDEN] = settings.statusBarHidden
        }
    }

    /** Everything the reader lets the user change. One flow, so the reader repaints once. */
    val reader: Flow<ReaderSettings> = store.data.map { prefs ->
        ReaderSettings(
            font = prefs[KEY_READER_FONT] ?: ReaderSettings.FONT_LITERATA,
            fontSizePercent = prefs[KEY_READER_SIZE] ?: 100,
            marginPercent = prefs[KEY_READER_MARGIN] ?: 100,
            lineHeightPercent = prefs[KEY_READER_LINE] ?: 140,
            justify = prefs[KEY_READER_JUSTIFY] ?: true,
            underlineHighlights = prefs[KEY_READER_UNDERLINE] ?: true,
            refreshEveryPages = prefs[KEY_READER_REFRESH] ?: 0,
            goalMinutes = prefs[KEY_READER_GOAL] ?: 30,
        )
    }

    suspend fun setReader(settings: ReaderSettings) {
        val clean = settings.clamped()
        store.edit { prefs ->
            prefs[KEY_READER_FONT] = clean.font
            prefs[KEY_READER_SIZE] = clean.fontSizePercent
            prefs[KEY_READER_MARGIN] = clean.marginPercent
            prefs[KEY_READER_LINE] = clean.lineHeightPercent
            prefs[KEY_READER_JUSTIFY] = clean.justify
            prefs[KEY_READER_UNDERLINE] = clean.underlineHighlights
            prefs[KEY_READER_REFRESH] = clean.refreshEveryPages
            prefs[KEY_READER_GOAL] = clean.goalMinutes
        }
    }

    /** The pen width the handwriting screens start with: the last one picked. */
    val penWidth: Flow<Float> = store.data.map { it[KEY_PEN_WIDTH] ?: DEFAULT_PEN_WIDTH }

    suspend fun setPenWidth(width: Float) {
        store.edit { it[KEY_PEN_WIDTH] = width }
    }

    /** Words to write in the journal each day. Zero is Off. */
    val journalGoal: Flow<Int> = store.data.map { prefs ->
        JournalGoal.clean(prefs[KEY_JOURNAL_GOAL] ?: 0)
    }

    suspend fun setJournalGoal(words: Int) {
        store.edit { it[KEY_JOURNAL_GOAL] = JournalGoal.clean(words) }
    }

    companion object {
        private val KEY_READER_FONT = stringPreferencesKey("reader_font")
        private val KEY_READER_SIZE = androidx.datastore.preferences.core.intPreferencesKey("reader_font_size")
        private val KEY_READER_MARGIN = androidx.datastore.preferences.core.intPreferencesKey("reader_margin")
        private val KEY_READER_LINE = androidx.datastore.preferences.core.intPreferencesKey("reader_line_height")
        private val KEY_READER_JUSTIFY = booleanPreferencesKey("reader_justify")
        private val KEY_READER_UNDERLINE = booleanPreferencesKey("reader_underline_highlights")
        private val KEY_READER_REFRESH = androidx.datastore.preferences.core.intPreferencesKey("reader_refresh_every")
        private val KEY_READER_GOAL = androidx.datastore.preferences.core.intPreferencesKey("reader_goal_minutes")
        private val KEY_JOURNAL_GOAL = androidx.datastore.preferences.core.intPreferencesKey("journal_goal_words")
        private val KEY_PEN_WIDTH = androidx.datastore.preferences.core.floatPreferencesKey("pen_width")

        /** The middle of the three widths in `PenWidths`. */
        const val DEFAULT_PEN_WIDTH = 5f

        const val FAST_PEN_OFF = "off"
        const val FAST_PEN_WRITING = "writing"
        const val FAST_PEN_AUTODRAW = "autodraw"
        const val DEFAULT_INK_DELAY_MILLIS = 900L

        private val KEY_FAST_PEN = stringPreferencesKey("fast_pen_mode")
        private val KEY_INK_DELAY = androidx.datastore.preferences.core.longPreferencesKey("ink_redraw_delay")
        private val KEY_FULL_REFRESH = booleanPreferencesKey("full_refresh_on_big_change")

        /** Section 6: up to 8 pinned apps. */
        const val MAX_PINNED = 8

        private val KEY_HOME_LABELS = booleanPreferencesKey("home_labels")
        private val KEY_LANDSCAPE = booleanPreferencesKey("landscape")
        private val KEY_LANDSCAPE_FLIPPED = booleanPreferencesKey("landscape_flipped")
        private val KEY_STATUS_BAR_HIDDEN = booleanPreferencesKey("status_bar_hidden")

        private val KEY_PINNED = stringPreferencesKey("pinned_apps")
        private val KEY_ART = booleanPreferencesKey("botanical_art")
    }
}
