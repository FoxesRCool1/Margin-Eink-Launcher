package io.github.foxesrcool1.margin.core.apps

import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException

private const val TAG = "AppFoldersRepository"

/**
 * The app folders, on top of the data folder.
 *
 * They are a plain file, `apps/folders.json`, and not a setting. The user
 * makes them by hand, one app at a time, and that work should be in a backup
 * and come back on a new tablet. The pinned apps are eight taps to make again,
 * so they stay where they were, in the settings.
 */
class AppFoldersRepository(private val data: DataRepository) {

    /**
     * Every change reads the file, changes it and writes it back, and each tap
     * runs on a thread of its own. The changes take turns, so two quick taps
     * cannot both start from the old file.
     */
    private companion object {
        val LOCK = Any()
        const val RESCUE_PATH = "${StorageLayout.APPS}/folders.unreadable.json"
    }

    /** Set when the file exists but could not be read. Changes are refused until a load reads it. */
    @Volatile
    private var readOnly = false

    /** The folders read, and whether the file exists but its read failed. */
    private class Loaded(val folders: AppFolders, val readFailed: Boolean)

    fun load(): AppFolders {
        val loaded = loadChecked()
        readOnly = loaded.readFailed
        return loaded.folders
    }

    /** Reads the file. The result says whether this read failed, so a caller does not depend on the shared [readOnly] flag. */
    private fun loadChecked(): Loaded {
        val text = try {
            data.store.readTextOrThrow(StorageLayout.appFoldersPath())
        } catch (e: IOException) {
            AppLog.e(TAG, "folders.json exists but could not be read. Changes are off until it can be.", e)
            return Loaded(AppFolders(), readFailed = true)
        }
        if (text == null) return Loaded(AppFolders(), readFailed = false)
        val parsed = AppFoldersFile.parseCounting(text)
        if (parsed == null) {
            AppLog.w(TAG, "folders.json could not be read. Starting with no folders.")
            // The next change writes a new file over this one. Keep what the
            // user had, typo and all.
            data.keepUnreadable(StorageLayout.appFoldersPath(), RESCUE_PATH)
            return Loaded(AppFolders(), readFailed = false)
        }
        if (parsed.dropped > 0) {
            AppLog.w(TAG, "folders.json has ${parsed.dropped} entries that could not be read. The rest are kept.")
            // The next change writes the kept folders over the file. Keep the copy with the bad entries.
            data.keepUnreadable(StorageLayout.appFoldersPath(), RESCUE_PATH)
        }
        return Loaded(parsed.folders, readFailed = false)
    }

    /** Loads, applies [transform], and saves when something changed. Returns what is on the disk now. */
    fun change(transform: (AppFolders) -> AppFolders): AppFolders = synchronized(LOCK) {
        val loaded = loadChecked()
        readOnly = loaded.readFailed
        val current = loaded.folders
        if (loaded.readFailed) {
            AppLog.w(TAG, "folders.json could not be read, so the change was not saved.")
            return current
        }
        val next = transform(current)
        if (next == current) return current
        if (data.store.writeText(StorageLayout.appFoldersPath(), AppFoldersFile.serialise(next))) {
            next
        } else {
            AppLog.e(TAG, "Could not save the app folders")
            current
        }
    }
}
