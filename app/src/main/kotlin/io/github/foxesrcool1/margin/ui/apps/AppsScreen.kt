package io.github.foxesrcool1.margin.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.ui.split.rememberPageOpener
import io.github.foxesrcool1.margin.core.apps.AppFolder
import io.github.foxesrcool1.margin.core.apps.AppFolders
import io.github.foxesrcool1.margin.core.apps.AppFoldersRepository
import io.github.foxesrcool1.margin.core.apps.AppSearch
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.storage.DataRoot
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.DialogMaxWidth
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkDialog
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.EinkTextField
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.components.TextPromptDialog
import io.github.foxesrcool1.margin.design.components.einkFieldBorder
import io.github.foxesrcool1.margin.design.components.einkPanel
import io.github.foxesrcool1.margin.design.components.rememberPagedListState
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "AppsScreen"
private const val PAGE_SIZE = 6

private enum class AppsPage { Pinned, Folders, All }

// Kept for the life of the process, so Apps comes back on the page the user
// left it on; not saved to disk.
private object LastAppsPage { var page = AppsPage.Pinned }

/** One line on the first page: a pinned app, or one of the ways out. */
private sealed interface FirstPageRow {
    data class Pinned(val entry: LauncherEntry) : FirstPageRow
    data class WayOut(val escape: EscapeEntry) : FirstPageRow
}

/** One line on the A to Z page: an app, or the last line that shows or puts away the hidden apps. */
private sealed interface AllPageRow {
    data class App(val entry: LauncherEntry) : AllPageRow
    data class HiddenToggle(val count: Int, val showing: Boolean) : AllPageRow
}

/**
 * The Apps tab.
 *
 * Three pages, picked with three icons: the pinned apps, the folders, and
 * every app from A to Z. The pinned page also holds the ways out of this
 * launcher, which plan section 3.2 says must always be there.
 *
 * The A to Z page can be searched by typing, and an app can be hidden from
 * it. Both came from what people ask of a launcher on an e-ink tablet: a
 * long list of preinstalled apps, and no way to find one quickly.
 *
 * Every list works out its own page size from the room it has. The first
 * version of this screen asked for eight rows where six fit, and the last two
 * apps of every page could not be seen.
 *
 * App names are text. There are no colour icons. Plan section 5.
 */
@Composable
fun AppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** For the screenshot tests, which have no tablet to list the apps of. */
    previewApps: List<LauncherEntry>? = null,
    previewFolders: AppFolders? = null,
    /** Null: the page the user was on last. */
    initialPage: Int? = null,
    /** Opens the A to Z page with the search field showing this text. */
    initialSearch: String? = null,
    /** Opens the A to Z page with the hidden apps showing. */
    initialShowHidden: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { AppsRepository(context) }
    // An app chosen in the second half of the split screen asks to open
    // beside this screen. Anywhere else it opens the usual way.
    val opener = rememberPageOpener()
    val settings = remember(context) { SettingsStore(context) }
    val folderStore = remember(context) { AppFoldersRepository(DataRoot.repository(context)) }

    var page by remember { mutableStateOf(initialPage?.let { AppsPage.entries[it.coerceIn(0, AppsPage.entries.size - 1)] } ?: LastAppsPage.page) }
    // The page icons choose the page and keep the choice for next time.
    fun showPage(next: AppsPage) {
        page = next
        LastAppsPage.page = next
    }
    var apps by remember { mutableStateOf(previewApps.orEmpty()) }
    var escapes by remember { mutableStateOf<List<EscapeEntry>>(emptyList()) }
    var folders by remember { mutableStateOf(previewFolders ?: AppFolders()) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    // Null when the search field is closed. The empty string is an open field.
    var search by remember { mutableStateOf(initialSearch) }
    var showHidden by remember { mutableStateOf(initialShowHidden) }
    val searchFocus = remember { FocusRequester() }

    var menuFor by remember { mutableStateOf<LauncherEntry?>(null) }
    var foldersFor by remember { mutableStateOf<LauncherEntry?>(null) }
    var folderMenuFor by remember { mutableStateOf<AppFolder?>(null) }
    var renamingFolder by remember { mutableStateOf<AppFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<AppFolder?>(null) }
    var addingFolder by remember { mutableStateOf(false) }

    val pinnedKeys by settings.pinnedApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberPagedListState()

    LaunchedEffect(repository) {
        if (previewApps != null) return@LaunchedEffect
        folders = withContext(AppDispatchers.io) { folderStore.load() }
        repository.packageChanges().collect {
            val loaded = withContext(AppDispatchers.io) { repository.loadAll() }
            val ways = withContext(AppDispatchers.io) { repository.escapeEntries(loaded) }
            apps = loaded
            escapes = ways
            // An uninstalled app would keep a pin: it counts toward the limit
            // and has no row to unpin it from. An empty list is not trusted,
            // it may be a failed query. Folders keep their keys on purpose.
            if (loaded.isNotEmpty()) {
                val installed = loaded.mapTo(HashSet()) { it.key }
                val pinned = settings.pinnedApps.first()
                if (pinned.any { it !in installed }) settings.setPinnedApps(pinned.filter { it in installed })
            }
        }
    }

    // One change at a time, or two quick taps could land on screen out of order.
    val folderChanges = remember { Mutex() }
    fun changeFolders(transform: (AppFolders) -> AppFolders) {
        if (previewFolders != null) {
            folders = transform(folders)
            return
        }
        scope.launch {
            folderChanges.withLock {
                folders = withContext(AppDispatchers.io) { folderStore.change(transform) }
            }
        }
    }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val firstPage = remember(pinnedKeys, byKey, escapes) {
        pinnedKeys.mapNotNull { byKey[it] }.map(FirstPageRow::Pinned) + escapes.map(FirstPageRow::WayOut)
    }
    val inFolder = openFolder?.let(folders::folder)
    val folderApps = remember(inFolder, byKey) { inFolder?.apps?.mapNotNull { byKey[it] }.orEmpty() }

    // The A to Z page: without the hidden apps unless asked, and then only
    // the ones the typed text finds.
    val hiddenCount = remember(apps, folders.hidden) { apps.count { folders.isHidden(it.key) } }
    val listed: List<AllPageRow> = remember(apps, folders.hidden, showHidden, search) {
        val rows = apps
            .filter { showHidden || !folders.isHidden(it.key) }
            .filter { search == null || AppSearch.matches(it.label, search.orEmpty()) }
            .map(AllPageRow::App)
        // The last line of the list shows the hidden apps, or puts them away.
        // It is a line and not a fifth icon at the top, because a narrow half
        // of the split screen has room for four icons.
        if (hiddenCount > 0 && search == null) rows + AllPageRow.HiddenToggle(hiddenCount, showHidden) else rows
    }
    // A new search starts on the first page, or the user could be looking at
    // an empty page three of a list that is now one page long.
    LaunchedEffect(search, showHidden) { listState.page = 0 }
    LaunchedEffect(search != null) { if (search != null) searchFocus.requestFocus() }

    // Back goes one level up, like the arrow does.
    BackHandler(enabled = inFolder != null || search != null) {
        if (inFolder != null) openFolder = null else search = null
    }

    ScreenScaffold(
        title = inFolder?.name ?: "Apps",
        overline = when {
            inFolder != null -> "Folder"
            hiddenCount > 0 && !showHidden -> "${apps.size - hiddenCount} apps, $hiddenCount hidden"
            else -> "${apps.size} apps"
        },
        plant = Plants.Apps,
        modifier = modifier,
        onBack = if (inFolder != null) ({ openFolder = null }) else onBack,
        backIcon = if (inFolder != null) Lucide.ArrowLeft else Lucide.House,
        backLabel = if (inFolder != null) "Back to the folders" else "Home",
        actions = {
            if (inFolder == null) {
                IconPressButton(
                    icon = Lucide.Pin,
                    label = "Pinned apps",
                    selected = page == AppsPage.Pinned,
                    onClick = { showPage(AppsPage.Pinned) },
                )
                IconPressButton(
                    icon = Lucide.Folder,
                    label = "Folders",
                    selected = page == AppsPage.Folders,
                    onClick = { showPage(AppsPage.Folders) },
                )
                IconPressButton(
                    icon = Lucide.ArrowDownAZ,
                    label = "All apps",
                    selected = page == AppsPage.All,
                    onClick = { showPage(AppsPage.All) },
                )
                if (page == AppsPage.Folders) {
                    IconPressButton(
                        icon = Lucide.FolderPlus,
                        label = "New folder",
                        bordered = true,
                        onClick = { addingFolder = true },
                    )
                }
                if (page == AppsPage.All) {
                    IconPressButton(
                        icon = Lucide.Search,
                        label = if (search == null) "Search the apps" else "Close the search",
                        selected = search != null,
                        bordered = true,
                        onClick = { search = if (search == null) "" else null },
                    )
                }
            }
        },
    ) {
        val typed = search
        if (inFolder == null && page == AppsPage.All && typed != null) {
            EinkTextField(
                value = typed,
                onValueChange = { search = it },
                textStyle = EinkType.body,
                singleLine = true,
                // Enter opens the first app the search found, so a keyboard
                // user types three letters and is there.
                onSubmit = {
                    val first = listed.firstNotNullOfOrNull { (it as? AllPageRow.App)?.entry }
                    if (!typed.isBlank() && first != null) opener.openApp(first)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus)
                    .defaultMinSize(minHeight = EinkDimens.touchTarget)
                    .einkFieldBorder()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
        }
        when {
            inFolder != null -> PagedList(
                items = folderApps,
                pageSize = PAGE_SIZE,
                rowHeight = EinkDimens.rowOneLine,
                emptyText = "This folder is empty. Hold an app on the A to Z page and choose Folders.",
                modifier = Modifier.weight(1f),
            ) { _, entry ->
                AppRow(
                    label = entry.label,
                    onOpen = { opener.openApp(entry) },
                    onLongPress = { menuFor = entry },
                )
            }

            page == AppsPage.Pinned -> PagedList(
                items = firstPage,
                pageSize = PAGE_SIZE,
                rowHeight = EinkDimens.rowOneLine,
                emptyText = "Nothing pinned yet. Hold an app on the A to Z page and choose Pin.",
                modifier = Modifier.weight(1f),
            ) { _, row ->
                when (row) {
                    is FirstPageRow.Pinned -> AppRow(
                        label = row.entry.label,
                        onOpen = { opener.openApp(row.entry) },
                        onLongPress = { menuFor = row.entry },
                    )

                    is FirstPageRow.WayOut -> AppRow(
                        label = row.escape.label,
                        hint = row.escape.hint,
                        icon = if (row.escape.kind == EscapeKind.OtherHome) Lucide.House else Lucide.Settings,
                        onOpen = { repository.launchEscape(row.escape) },
                    )
                }
            }

            page == AppsPage.Folders -> PagedList(
                items = folders.folders,
                pageSize = PAGE_SIZE,
                rowHeight = EinkDimens.rowOneLine,
                emptyText = "No folders yet. Press the folder with the plus sign.",
                modifier = Modifier.weight(1f),
            ) { _, folder ->
                val installed = folder.apps.count { it in byKey }
                AppRow(
                    label = folder.name,
                    hint = if (installed == 1) "1 app" else "$installed apps",
                    icon = Lucide.Folder,
                    onOpen = { openFolder = folder.name },
                    onLongPress = { folderMenuFor = folder },
                )
            }

            else -> PagedList(
                items = listed,
                pageSize = PAGE_SIZE,
                rowHeight = EinkDimens.rowOneLine,
                state = listState,
                emptyText = if (!typed.isNullOrBlank()) "No app is called that." else "No apps found",
                modifier = Modifier.weight(1f),
            ) { _, row ->
                when (row) {
                    is AllPageRow.App -> AppRow(
                        label = row.entry.label,
                        hint = if (folders.isHidden(row.entry.key)) "Hidden" else null,
                        mark = if (pinnedKeys.contains(row.entry.key)) Lucide.Pin else null,
                        onOpen = { opener.openApp(row.entry) },
                        onLongPress = { menuFor = row.entry },
                    )

                    is AllPageRow.HiddenToggle -> AppRow(
                        label = when {
                            row.showing -> "Put the hidden apps away"
                            row.count == 1 -> "1 hidden app"
                            else -> "${row.count} hidden apps"
                        },
                        icon = if (row.showing) Lucide.EyeOff else Lucide.Eye,
                        onOpen = { showHidden = !row.showing },
                    )
                }
            }
        }
    }

    val selected = menuFor
    if (selected != null) {
        val isPinned = pinnedKeys.contains(selected.key)
        val pinnedFull = pinnedKeys.size >= SettingsStore.MAX_PINNED
        OptionsDialog(
            title = selected.label,
            onDismiss = { menuFor = null },
            options = listOf(
                DialogOption(
                    label = when {
                        isPinned -> "Unpin"
                        pinnedFull -> "The pinned list is full"
                        else -> "Pin"
                    },
                    icon = if (isPinned) Lucide.PinOff else Lucide.Pin,
                    enabled = isPinned || !pinnedFull,
                    onSelect = {
                        scope.launch { settings.togglePinned(selected.key) }
                        AppLog.i(TAG, "Toggled pin for ${selected.key}")
                        menuFor = null
                    },
                ),
                DialogOption(
                    label = "Folders",
                    icon = Lucide.Folder,
                    onSelect = {
                        foldersFor = selected
                        menuFor = null
                    },
                ),
                DialogOption(
                    label = if (folders.isHidden(selected.key)) "Show on the A to Z page" else "Hide from the A to Z page",
                    icon = if (folders.isHidden(selected.key)) Lucide.Eye else Lucide.EyeOff,
                    onSelect = {
                        changeFolders { it.hiddenToggled(selected.key) }
                        AppLog.i(TAG, "Toggled hidden for ${selected.key}")
                        menuFor = null
                    },
                ),
                DialogOption(
                    label = "App info",
                    icon = Lucide.Info,
                    onSelect = {
                        repository.openAppInfo(selected)
                        menuFor = null
                    },
                ),
                DialogOption(
                    label = "Uninstall",
                    icon = Lucide.Trash2,
                    onSelect = {
                        repository.requestUninstall(selected)
                        menuFor = null
                    },
                ),
            ),
        )
    }

    val sorting = foldersFor
    if (sorting != null) {
        FolderPickerDialog(
            appLabel = sorting.label,
            folders = folders,
            appKey = sorting.key,
            onToggle = { name -> changeFolders { it.toggled(name, sorting.key) } },
            onNewFolder = { addingFolder = true },
            onDismiss = { foldersFor = null },
        )
    }

    if (addingFolder) {
        TextPromptDialog(
            title = "New folder",
            confirmText = "Make",
            onConfirm = { name ->
                addingFolder = false
                // Made from the picker, the folder is for that app, so the app goes in.
                val forApp = foldersFor?.key
                changeFolders { before ->
                    val made = before.withFolder(name)
                    if (forApp != null && before.folder(name) == null) made.toggled(name, forApp) else made
                }
            },
            onDismiss = { addingFolder = false },
        )
    }

    val folderMenu = folderMenuFor
    if (folderMenu != null) {
        OptionsDialog(
            title = folderMenu.name,
            onDismiss = { folderMenuFor = null },
            options = listOf(
                DialogOption(label = "Rename", icon = Lucide.Pencil) {
                    renamingFolder = folderMenu
                    folderMenuFor = null
                },
                DialogOption(label = "Delete the folder", icon = Lucide.Trash2) {
                    deletingFolder = folderMenu
                    folderMenuFor = null
                },
            ),
        )
    }

    val beingRenamed = renamingFolder
    if (beingRenamed != null) {
        TextPromptDialog(
            title = "Rename the folder",
            initialValue = beingRenamed.name,
            onConfirm = { name ->
                renamingFolder = null
                changeFolders { it.renamed(beingRenamed.name, name) }
            },
            onDismiss = { renamingFolder = null },
        )
    }

    val beingDeleted = deletingFolder
    if (beingDeleted != null) {
        ConfirmDialog(
            title = "Delete ${beingDeleted.name}?",
            message = "Only the folder goes. The apps in it stay on the tablet.",
            confirmText = "Delete",
            cancelText = "Keep",
            onConfirm = {
                deletingFolder = null
                changeFolders { it.without(beingDeleted.name) }
            },
            onDismiss = { deletingFolder = null },
        )
    }
}

/** One line: a name, and around it an icon in front, a small word behind, or a small mark behind. */
@Composable
private fun AppRow(
    label: String,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    icon: LucideIcon? = null,
    hint: String? = null,
    mark: LucideIcon? = null,
) {
    EinkRow(onClick = onOpen, onLongClick = onLongPress) { pressed ->
        val colour = if (pressed) EinkColors.Paper else EinkColors.Ink
        val faded = if (pressed) EinkColors.Paper else EinkColors.Faded
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) EinkIcon(icon = icon, color = colour)
            EinkText(
                text = label,
                style = EinkType.rowTitle.copy(color = colour),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (hint != null) CapsLabel(text = hint, style = EinkType.capsSmall.copy(color = faded))
            if (mark != null) EinkIcon(icon = mark, size = 18.dp, color = faded)
        }
    }
    HairlineDivider(color = EinkColors.Faded)
}

/**
 * Which folders one app is in. A tap on a folder puts the app in or takes it
 * out, and the dialog stays open, because sorting one app into two folders
 * should not take two trips through the menu.
 */
@Composable
private fun FolderPickerDialog(
    appLabel: String,
    folders: AppFolders,
    appKey: String,
    onToggle: (String) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    EinkDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth(0.86f)
                .einkPanel()
                .padding(EinkDimens.blockGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    CapsLabel(text = "Folders for", style = EinkType.capsSmall)
                    EinkText(text = appLabel, style = EinkType.title, maxLines = 1)
                }
                IconPressButton(icon = Lucide.X, label = "Close", onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))

            PagedList(
                items = folders.folders,
                pageSize = 4,
                emptyText = "No folders yet",
                modifier = Modifier.height(EinkDimens.rowOneLine * 4 + 70.dp),
            ) { _, folder ->
                val isIn = appKey in folder.apps
                EinkRow(onClick = { onToggle(folder.name) }) { pressed ->
                    val colour = if (pressed) EinkColors.Paper else EinkColors.Ink
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EinkIcon(icon = if (isIn) Lucide.CircleCheck else Lucide.Circle, color = colour)
                        EinkText(text = folder.name, style = EinkType.rowTitle.copy(color = colour), maxLines = 1)
                    }
                }
            }

            IconPressButton(
                icon = Lucide.FolderPlus,
                label = "New folder",
                bordered = true,
                onClick = onNewFolder,
            )
        }
    }
}
