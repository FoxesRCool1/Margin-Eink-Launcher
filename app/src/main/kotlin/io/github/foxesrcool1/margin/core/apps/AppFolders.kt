package io.github.foxesrcool1.margin.core.apps

import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonArray
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.json.jsonArrayOf
import io.github.foxesrcool1.margin.core.json.jsonOf
import java.util.Locale

/** One folder on the Apps tab: a name, and the keys of the apps in it, in the order they were added. */
data class AppFolder(
    val name: String,
    val apps: List<String> = emptyList(),
)

/**
 * The folders the user sorts apps into, and the rules for changing them.
 *
 * An app is known by its key, `package/activity`, the same one a pin uses. An
 * app can be in more than one folder: a folder here is a label, not a place,
 * and "Reading" and "Offline" can both be true of one app. An app that is
 * uninstalled stays in the file. It comes back into its folders when it is
 * installed again, which is what happens on a restore to a new tablet.
 *
 * The hidden apps live here too, in the same file: an app the user does not
 * want on the A to Z page. It is still installed, still in its folders and
 * still pinned if it was pinned. Hiding is for the twenty preinstalled apps
 * nobody opens, not for a secret.
 *
 * Nothing here touches Android, so plain unit tests check every rule.
 */
data class AppFolders(
    val folders: List<AppFolder> = emptyList(),
    /** The keys of the apps kept off the A to Z page, in the order they were hidden. */
    val hidden: List<String> = emptyList(),
) {

    fun isHidden(appKey: String): Boolean = appKey in hidden

    /** Hides the app when it is shown, and shows it when it is hidden. */
    fun hiddenToggled(appKey: String): AppFolders =
        copy(hidden = if (appKey in hidden) hidden - appKey else hidden + appKey)

    fun folder(name: String): AppFolder? = folders.firstOrNull { it.name.sameNameAs(name) }

    /** The folders that hold [appKey], by name. */
    fun foldersOf(appKey: String): List<String> = folders.filter { appKey in it.apps }.map { it.name }

    /**
     * Adds an empty folder. A name that is blank, or that is already there in
     * any mix of capitals, changes nothing: two folders called "Books" and
     * "books" would be one mistake waiting to be tapped.
     */
    fun withFolder(name: String): AppFolders {
        val clean = name.cleanName()
        if (clean.isEmpty() || folder(clean) != null) return this
        return copy(folders = (folders + AppFolder(clean)).sortedByName())
    }

    fun without(name: String): AppFolders = copy(folders = folders.filterNot { it.name.sameNameAs(name) })

    fun renamed(from: String, to: String): AppFolders {
        val clean = to.cleanName()
        val taken = folder(clean)
        if (clean.isEmpty() || (taken != null && !taken.name.sameNameAs(from))) return this
        return copy(
            folders = folders
                .map { if (it.name.sameNameAs(from)) it.copy(name = clean) else it }
                .sortedByName(),
        )
    }

    /** Puts the app into the folder when it is not there, and takes it out when it is. */
    fun toggled(folderName: String, appKey: String): AppFolders = copy(
        folders = folders.map { folder ->
            when {
                !folder.name.sameNameAs(folderName) -> folder
                appKey in folder.apps -> folder.copy(apps = folder.apps - appKey)
                else -> folder.copy(apps = folder.apps + appKey)
            }
        },
    )

    private fun List<AppFolder>.sortedByName() = sortedBy { it.name.lowercase(Locale.ROOT) }

    companion object {
        const val MAX_NAME_LENGTH = 40

        private fun String.cleanName(): String = trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim()

        private fun String.sameNameAs(other: String): Boolean = cleanName().equals(other.cleanName(), ignoreCase = true)
    }
}

/**
 * Reads and writes `apps/folders.json`.
 *
 * Like `habits.json`, the file is meant to be readable and editable by hand.
 */
object AppFoldersFile {

    const val VERSION = 1

    fun serialise(folders: AppFolders): String = Json.write(
        JsonObject.of(
            "version" to jsonOf(VERSION),
            "folders" to jsonArrayOf(
                folders.folders.map { folder ->
                    JsonObject.of(
                        "name" to jsonOf(folder.name),
                        "apps" to jsonArrayOf(folder.apps.map(::jsonOf)),
                    )
                },
            ),
            "hidden" to jsonArrayOf(folders.hidden.map(::jsonOf)),
        ),
    )

    /** The folders read from the file, and how many entries were dropped while reading them. */
    data class Parsed(val folders: AppFolders, val dropped: Int)

    /**
     * Returns null when the text is not this file at all. One bad folder is
     * dropped and the others are kept, for the same reason as in the habits
     * file: one stray comma must not cost the user everything else.
     */
    fun parse(text: String): AppFolders? = parseCounting(text)?.folders

    /**
     * Like [parse], and also counts what was dropped: a folders value that is
     * not a list, and each entry that is not an object, has no name, or
     * repeats a name of an earlier folder.
     */
    fun parseCounting(text: String): Parsed? {
        val root = Json.parseOrNull(text) as? JsonObject ?: return null
        if (root.int("version") == null) return null

        val rawFolders = root["folders"]
        val entries = (rawFolders as? JsonArray)?.items.orEmpty()
        val folders = entries
            .mapNotNull { entry ->
                val folder = entry as? JsonObject ?: return@mapNotNull null
                val name = folder.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val apps = folder.array("apps")?.strings().orEmpty().filter { it.isNotBlank() }.distinct()
                AppFolder(name.take(AppFolders.MAX_NAME_LENGTH), apps)
            }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
        val notAList = if (rawFolders != null && rawFolders !is JsonArray) 1 else 0
        val dropped = entries.size - folders.size + notAList

        // A file from before hiding existed has no "hidden" list. That is fine.
        val hidden = root.array("hidden")?.strings().orEmpty().filter { it.isNotBlank() }.distinct()

        return Parsed(AppFolders(folders, hidden), dropped)
    }
}
