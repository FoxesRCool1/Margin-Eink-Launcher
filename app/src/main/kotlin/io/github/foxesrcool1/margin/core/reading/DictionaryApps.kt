package io.github.foxesrcool1.margin.core.reading

/**
 * One app that can take the selected words to a dictionary. The two ways an
 * app can take words: ACTION_PROCESS_TEXT with text/plain, and the older
 * ColorDict intent that ColorDict, GoldenDict, Fora and Livio still take.
 */
data class DictionaryApp(
    val packageName: String,
    val className: String,
    val label: String,
    val colorDict: Boolean = false,
)

/**
 * Merges the two lists of dictionary apps into one, with one row per package.
 *
 * An app that answers both intents is kept once, and the row from
 * ACTION_PROCESS_TEXT wins, because it is the newer way and carries the
 * words as an extra the platform fills in. The last chosen package comes
 * first, then the labels in order. This app's own package is left out.
 */
fun mergeDictionaryApps(
    processText: List<DictionaryApp>,
    colorDict: List<DictionaryApp>,
    lastChosen: String?,
    ownPackage: String,
): List<DictionaryApp> {
    val byPackage = LinkedHashMap<String, DictionaryApp>()
    for (app in colorDict) byPackage[app.packageName] = app
    for (app in processText) byPackage[app.packageName] = app
    return byPackage.values
        .filter { it.packageName != ownPackage }
        .sortedWith(
            compareByDescending<DictionaryApp> { it.packageName == lastChosen }
                .thenBy { it.label.lowercase() },
        )
}
