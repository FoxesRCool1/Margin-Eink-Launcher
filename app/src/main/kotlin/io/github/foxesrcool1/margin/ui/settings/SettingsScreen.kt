package io.github.foxesrcool1.margin.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.BuildConfig
import io.github.foxesrcool1.margin.core.eink.EinkDevice
import io.github.foxesrcool1.margin.core.eink.EinkDevices
import io.github.foxesrcool1.margin.core.eink.FastPenPath
import io.github.foxesrcool1.margin.core.eink.ScreenRefresh
import io.github.foxesrcool1.margin.core.launcher.DefaultLauncher
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.settings.SettingsStore
import io.github.foxesrcool1.margin.core.settings.WindowSettings
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.ConfirmDialog
import io.github.foxesrcool1.margin.design.components.DialogOption
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.OptionsDialog
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.LocalWideScreen
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import io.github.foxesrcool1.margin.ui.common.WebLinks
import io.github.foxesrcool1.margin.ui.devicetest.PenTestActivity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SettingsScreen"

/**
 * The settings screen.
 *
 * It opens on a strip of tabs, and each tab is one list of rows. A row has a
 * short name and, at its end, a switch, the value it has now, or an arrow.
 * There is no help line: the name is the help. The whole row is the target.
 * The first version was six tabs of buttons with the state written into the
 * button text, the second a menu of pages with a line of help under every
 * row, and the owner found both hard to use.
 *
 * Nothing scrolls: a tab with more rows than fit gets a second page.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenDemo: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUpdates: () -> Unit = {},
    /** Which tab to open on, as an index into [SettingsTab]. The screenshot tests use it. */
    initialPage: Int = 0,
    /** The panel. The screenshot tests hand in one with vendor control, to draw the Pen tab. */
    device: EinkDevice = EinkDevices.get(LocalContext.current),
    /** Asks whether Margin is the home app. The screenshot tests hand in "no", to draw the first row. */
    isDefaultHome: (android.content.Context) -> Boolean = DefaultLauncher::isDefault,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember(context) { SettingsStore(context) }

    // The Pen tab is for a tablet with a fast pen. Other tablets do not see it.
    val tabs = remember(device) { SettingsTab.entries.filter { it != SettingsTab.Pen || device.hasVendorControl } }
    var openTab by rememberSaveable {
        mutableStateOf(SettingsTab.entries[initialPage.coerceIn(0, SettingsTab.entries.size - 1)])
    }
    val tab = if (openTab in tabs) openTab else SettingsTab.Screen
    var showCredits by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }

    val artOn by settings.botanicalArt.collectAsStateWithLifecycle(initialValue = true)
    val homeLabels by settings.homeLabels.collectAsStateWithLifecycle(initialValue = false)
    val window by settings.window.collectAsStateWithLifecycle(initialValue = ScreenWindow.settings(context))
    val fullRefresh by settings.fullRefreshOnBigChange.collectAsStateWithLifecycle(initialValue = false)
    val fastPen by settings.fastPenMode.collectAsStateWithLifecycle(initialValue = SettingsStore.FAST_PEN_OFF)
    val inkDelay by settings.inkRedrawDelayMillis.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_INK_DELAY_MILLIS)

    // Which fast pen modes closed the app. Read from a small file each time
    // the Pen tab opens, off the main thread.
    val penGuard = remember(context) { EinkDevices.guard(context) }
    var crashedPens by remember { mutableStateOf(emptySet<FastPenPath>()) }
    LaunchedEffect(tab) {
        if (tab != SettingsTab.Pen) return@LaunchedEffect
        crashedPens = withContext(AppDispatchers.io) {
            FastPenPath.entries.filterNot(penGuard::mayTry).toSet()
        }
    }

    // Asking the package manager is a binder call, so it runs off the main
    // thread: when the screen opens, each time it comes back in front (the
    // user may have picked the home app in the tablet settings), and again
    // when the role request comes back. Null until the first answer, so the
    // row is not drawn for a moment and then taken away.
    var isDefault by remember { mutableStateOf<Boolean?>(null) }
    var homeChecks by remember { mutableIntStateOf(0) }
    // True from the press on "Set as Home App" until the answer is shown.
    var asked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(homeChecks) {
        val check = scope.launch {
            val now = withContext(AppDispatchers.io) { isDefaultHome(context) }
            isDefault = now
            if (asked) {
                asked = false
                AppLog.i(TAG, "Home role checked again. Default now: $now")
                status = if (now) "Margin is the home app" else "Not the home app yet"
            }
        }
        onPauseOrDispose { check.cancel() }
    }
    var showManualSteps by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        homeChecks++
    }

    val storage = rememberStorageActions(onStatus = { status = it })

    fun open(next: SettingsTab) {
        status = null
        openTab = next
    }

    fun changeWindow(transform: (WindowSettings) -> WindowSettings) {
        context.findActivity()?.let { ScreenWindow.change(it, transform) }
    }

    // The tabs are not levels, so Back leaves Settings from any of them. Only
    // the credits are a level: Back from there goes to the About tab.
    BackHandler(enabled = showCredits) { showCredits = false }

    val rows: List<Setting> = when (tab) {
        SettingsTab.Screen -> buildList {
            if (isDefault == false) {
                add(
                    Setting(
                        title = "Set as Home App",
                        icon = Lucide.House,
                        onClick = {
                            asked = true
                            status = setAsHome(
                                context = context,
                                startRoleRequest = { intent -> roleLauncher.launch(intent) },
                                showSteps = { showManualSteps = true },
                            )
                        },
                    ),
                )
            }
            add(
                Setting.switch(
                    title = "Plants",
                    icon = Lucide.Sprout,
                    on = artOn,
                    onChange = { scope.launch { settings.setBotanicalArt(it) } },
                ),
            )
            add(
                Setting.switch(
                    title = "Icon Names",
                    icon = Lucide.Type,
                    on = homeLabels,
                    onChange = { scope.launch { settings.setHomeLabels(it) } },
                ),
            )
            add(
                Setting.switch(
                    title = "Status Bar",
                    icon = Lucide.BatteryMedium,
                    on = !window.statusBarHidden,
                    onChange = { show -> changeWindow { it.copy(statusBarHidden = !show) } },
                ),
            )
            add(
                Setting.switch(
                    title = "Landscape Mode",
                    icon = Lucide.RotateCwSquare,
                    on = window.landscape,
                    onChange = { on -> changeWindow { it.copy(landscape = on) } },
                ),
            )
            if (window.landscape) {
                add(
                    Setting.switch(
                        title = "Flip Landscape",
                        icon = Lucide.RefreshCw,
                        on = window.landscapeFlipped,
                        onChange = { on -> changeWindow { it.copy(landscapeFlipped = on) } },
                    ),
                )
            }
            add(
                Setting.switch(
                    title = "Auto Refresh",
                    icon = Lucide.Monitor,
                    on = fullRefresh,
                    onChange = { scope.launch { settings.setFullRefreshOnBigChange(it) } },
                ),
            )
            add(
                Setting(
                    title = "Force Refresh",
                    icon = Lucide.RefreshCw,
                    onClick = { context.findActivity()?.let(ScreenRefresh::run) },
                ),
            )
        }

        SettingsTab.Pen -> buildList {
            add(
                Setting(
                    title = "Pen Mode",
                    icon = Lucide.PenLine,
                    value = penModeLabel(fastPen),
                    onClick = {
                        choice = Choice(
                            title = "Pen Mode",
                            message = PEN_MODE_HELP,
                            options = PEN_MODES.map { mode ->
                                val path = penModePath(mode)
                                val label = penModeLabel(mode) + if (path in crashedPens) " (closed the app)" else ""
                                label to {
                                    scope.launch {
                                        // Both or neither: leaving Settings at once must not
                                        // clear the guard and lose the choice.
                                        withContext(NonCancellable) {
                                            settings.setFastPenMode(mode)
                                            // Picking a mode that crashed is the owner asking for one more try.
                                            if (path != null) withContext(AppDispatchers.io) { penGuard.reset(path) }
                                        }
                                        crashedPens = crashedPens - setOfNotNull(path)
                                    }
                                }
                            },
                            current = PEN_MODES.indexOf(fastPen),
                        )
                    },
                ),
            )
            if (fastPen != SettingsStore.FAST_PEN_OFF) {
                add(
                    Setting(
                        title = "Redraw Delay",
                        icon = Lucide.History,
                        value = "$inkDelay ms",
                        onClick = {
                            choice = Choice(
                                title = "Redraw Delay",
                                message = REDRAW_DELAY_HELP,
                                options = REDRAW_DELAYS.map { millis ->
                                    "$millis ms" to { scope.launch { settings.setInkRedrawDelayMillis(millis) } }
                                },
                                current = REDRAW_DELAYS.indexOf(inkDelay),
                            )
                        },
                    ),
                )
            }
            add(
                Setting(
                    title = "Try the Pen",
                    icon = Lucide.Pencil,
                    trailing = Lucide.ChevronRight,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                PenTestActivity.intent(context, penTestMode(fastPen))
                                    .putExtra(PenTestActivity.EXTRA_DELAY, inkDelay),
                            )
                        }.onFailure { AppLog.e(TAG, "Could not open the pen page", it) }
                    },
                ),
            )
            add(
                Setting(
                    title = "Device Test",
                    icon = Lucide.Wrench,
                    trailing = Lucide.ChevronRight,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(context, io.github.foxesrcool1.margin.ui.devicetest.DeviceTestActivity::class.java),
                            )
                        }.onFailure { AppLog.e(TAG, "Could not open the device test", it) }
                    },
                ),
            )
        }

        SettingsTab.Files -> listOf(
            Setting(
                title = "Back Up",
                icon = Lucide.Upload,
                enabled = !storage.busy,
                onClick = storage.backUp,
            ),
            Setting(
                title = "Restore",
                icon = Lucide.Download,
                enabled = !storage.busy,
                onClick = storage.restore,
            ),
        )

        SettingsTab.About -> buildList {
            add(
                Setting(
                    title = "Updates",
                    icon = Lucide.Download,
                    // The version without the flavour: 1.0.1, not 1.0.1-viwoods.
                    value = BuildConfig.VERSION_NAME.substringBefore('-'),
                    onClick = onOpenUpdates,
                ),
            )
            add(
                Setting(
                    title = "Log",
                    icon = Lucide.ScrollText,
                    trailing = Lucide.ChevronRight,
                    onClick = onOpenLog,
                ),
            )
            add(
                Setting(
                    title = "Credits",
                    icon = Lucide.Info,
                    trailing = Lucide.ChevronRight,
                    onClick = {
                        status = null
                        showCredits = true
                    },
                ),
            )
            add(
                Setting(
                    title = "Support This App",
                    icon = Lucide.Coffee,
                    onClick = { WebLinks.open(context, WebLinks.KO_FI) },
                ),
            )
            add(
                Setting(
                    title = "Source Code",
                    icon = Lucide.FileText,
                    onClick = { WebLinks.open(context, WebLinks.SOURCE) },
                ),
            )
            // A page for the people who build the app, not for the people who use it.
            if (BuildConfig.DEBUG) {
                add(
                    Setting(
                        title = "Design Demo",
                        icon = Lucide.LayoutGrid,
                        trailing = Lucide.ChevronRight,
                        onClick = onOpenDemo,
                    ),
                )
            }
        }
    }

    ScreenScaffold(
        title = if (showCredits) "Credits" else "Settings",
        plant = Plants.Settings,
        modifier = modifier,
        onBack = if (showCredits) ({ showCredits = false }) else onBack,
        backIcon = if (showCredits) Lucide.ArrowLeft else Lucide.House,
        backLabel = if (showCredits) "Back" else "Home",
    ) {
        if (showCredits) {
            AboutSection(modifier = Modifier.weight(1f))
        } else {
            // What a tab shows: the status line, the rows, and for Files, where the files are.
            val body: @Composable ColumnScope.() -> Unit = {
                if (status != null) {
                    CapsLabel(
                        text = status.orEmpty(),
                        style = EinkType.capsSmall.copy(color = EinkColors.Faded),
                        maxLines = 2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                key(tab) { SettingsList(rows = rows, modifier = Modifier.weight(1f)) }

                // Without the start that every tablet shares.
                if (tab == SettingsTab.Files) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EinkIcon(icon = Lucide.HardDrive, size = 18.dp, color = EinkColors.Faded)
                        EinkText(
                            text = storage.folderPath.removePrefix("/storage/emulated/0/"),
                            style = EinkType.help.copy(color = EinkColors.Faded),
                            maxLines = 2,
                        )
                    }
                }
            }

            if (LocalWideScreen.current) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsTabs(tabs = tabs, selected = tab, onSelect = ::open, vertical = true)
                    Column(modifier = Modifier.weight(1f), content = body)
                }
            } else {
                SettingsTabs(tabs = tabs, selected = tab, onSelect = ::open)
                Spacer(modifier = Modifier.height(8.dp))
                body()
            }
        }
    }

    val asking = choice
    if (asking != null) {
        OptionsDialog(
            title = asking.title,
            message = asking.message,
            onDismiss = { choice = null },
            options = asking.options.mapIndexed { index, (label, pick) ->
                DialogOption(label = label, icon = if (index == asking.current) Lucide.Check else Lucide.Minus) {
                    pick()
                    choice = null
                }
            },
        )
    }

    if (storage.confirmRestore != null) {
        ConfirmDialog(
            title = "Restore Backup?",
            message = RESTORE_HELP,
            confirmText = "Restore",
            cancelText = "Cancel",
            onConfirm = storage.confirmRestore,
            onDismiss = storage.cancelRestore,
        )
    }

    if (showManualSteps) {
        ConfirmDialog(
            title = "Set as Home App",
            message = DefaultLauncher.manualSteps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"),
            confirmText = "Check",
            cancelText = "Close",
            onConfirm = {
                showManualSteps = false
                asked = true
                homeChecks++
            },
            onDismiss = { showManualSteps = false },
        )
    }
}

/**
 * A setting with a short list of values to choose from. [current] is the
 * index of the value it has now, which gets a tick, or -1.
 */
private class Choice(
    val title: String,
    val options: List<Pair<String, () -> Unit>>,
    val message: String? = null,
    val current: Int = -1,
)

/**
 * The pen modes, in the order the dialog shows them. The stored names stay
 * the old ones, so a tablet keeps its choice through the update.
 */
internal val PEN_MODES = listOf(
    SettingsStore.FAST_PEN_OFF,
    SettingsStore.FAST_PEN_WRITING,
    SettingsStore.FAST_PEN_AUTODRAW,
)

/** One line above the choice. The dialog does not scroll, and in landscape a long text pushed the last choice off. */
internal const val PEN_MODE_HELP = "Fast modes are a test. Try Fast 1, then Fast 2."

internal val REDRAW_DELAYS = listOf(600L, 900L, 1200L, 1600L)

internal const val REDRAW_DELAY_HELP = "Wait after the pen lifts."

internal const val RESTORE_HELP = "Files with the same name are replaced."

internal fun penModeLabel(mode: String): String = when (mode) {
    SettingsStore.FAST_PEN_WRITING -> "Fast 1"
    SettingsStore.FAST_PEN_AUTODRAW -> "Fast 2"
    else -> "Normal"
}

private fun penModePath(mode: String): FastPenPath? = when (mode) {
    SettingsStore.FAST_PEN_WRITING -> FastPenPath.Writing
    SettingsStore.FAST_PEN_AUTODRAW -> FastPenPath.AutoDraw
    else -> null
}

private fun penTestMode(mode: String): String = when (mode) {
    SettingsStore.FAST_PEN_WRITING -> PenTestActivity.MODE_WRITING
    SettingsStore.FAST_PEN_AUTODRAW -> PenTestActivity.MODE_AUTODRAW
    else -> PenTestActivity.MODE_APP
}

/**
 * The three ways to become the home app, in order. Returns the line to show
 * the user.
 */
private fun setAsHome(
    context: android.content.Context,
    startRoleRequest: (Intent) -> Unit,
    showSteps: () -> Unit,
): String {
    DefaultLauncher.roleRequestIntent(context)?.let { intent ->
        AppLog.i(TAG, "Asking for the home role")
        return runCatching {
            startRoleRequest(intent)
            "Choose Margin"
        }.getOrElse {
            AppLog.w(TAG, "The home role request would not start", it)
            openSettingsOrSteps(context, showSteps)
        }
    }
    return openSettingsOrSteps(context, showSteps)
}

private fun openSettingsOrSteps(
    context: android.content.Context,
    showSteps: () -> Unit,
): String {
    val intent = DefaultLauncher.homeSettingsIntent(context)
        ?: DefaultLauncher.allSettingsIntent(context)

    if (intent != null) {
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (started) {
            AppLog.i(TAG, "Opened a settings screen to pick the home app")
            return "Pick Margin as the home app"
        }
    }

    AppLog.w(TAG, "No settings route worked. Showing the written steps.")
    showSteps()
    return "Follow the steps"
}
