package io.github.foxesrcool1.margin.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxesrcool1.margin.BuildConfig
import io.github.foxesrcool1.margin.core.threads.AppDispatchers
import io.github.foxesrcool1.margin.core.update.ApkInstaller
import io.github.foxesrcool1.margin.core.update.AppVersion
import io.github.foxesrcool1.margin.core.update.UpdateManager
import io.github.foxesrcool1.margin.core.update.UpdateState
import io.github.foxesrcool1.margin.core.update.UpdateToken
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.components.TextPromptDialog
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, About, Updates.
 *
 * Opening the screen is the user asking, so it checks at once. After that it
 * only shows what [UpdateManager] is doing. The download goes on if the user
 * leaves, and this screen picks the state up again when they come back.
 */
@Composable
fun UpdateScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsStateWithLifecycle()

    // Both answers come from a binder call or a file, so they are read off
    // the main thread. Until they arrive the footer is not drawn.
    var installAllowed by remember { mutableStateOf(true) }
    var hasToken by remember { mutableStateOf(false) }
    var askForToken by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Also when the user comes back from the system screen where they allowed installs.
    LifecycleResumeEffect(Unit) {
        val read = scope.launch {
            val (allowed, token) = withContext(AppDispatchers.io) {
                ApkInstaller.isAllowed(context) to (UpdateToken.read(context) != null)
            }
            installAllowed = allowed
            hasToken = token
        }
        onPauseOrDispose { read.cancel() }
    }

    // The row that leads here says "Updates", so it checks,
    // also when an answer from an hour ago is still standing. A download or
    // a finished download is left alone.
    LaunchedEffect(Unit) {
        val now = UpdateManager.state.value
        if (now !is UpdateState.Downloading && now !is UpdateState.Ready) UpdateManager.check(context)
    }

    // One press covers "download" and "install". The install only starts from
    // here, while this screen is in front, because Android does not let an
    // app that is out of sight open the install window.
    LaunchedEffect(state, installAllowed) {
        val ready = state as? UpdateState.Ready
        if (installAllowed && ready != null && !ready.handedOver) UpdateManager.install(context)
    }

    UpdateContent(
        state = state,
        installAllowed = installAllowed,
        hasToken = hasToken,
        onBack = onBack,
        onCheck = { UpdateManager.check(context) },
        onDownload = { UpdateManager.download(context) },
        onInstall = { UpdateManager.install(context) },
        onAllowInstalls = { ApkInstaller.openPermissionScreen(context) },
        onToken = { askForToken = true },
        modifier = modifier,
    )

    if (askForToken) {
        TextPromptDialog(
            title = "Access Token",
            // The saved token is not shown again. An empty answer removes it.
            initialValue = "",
            allowEmpty = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            onConfirm = { typed ->
                askForToken = false
                scope.launch {
                    hasToken = withContext(AppDispatchers.io) {
                        UpdateToken.save(context, typed)
                        UpdateToken.read(context) != null
                    }
                    UpdateManager.check(context)
                }
            },
            onDismiss = { askForToken = false },
        )
    }
}

/** The screen with nothing behind it, so a screenshot test can show every state. */
@Composable
fun UpdateContent(
    state: UpdateState,
    installAllowed: Boolean,
    hasToken: Boolean,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    // Which page of the release notes is showing. Starts again on the first page for each release.
    val notesVersion = (state as? UpdateState.Available)?.release?.version
    var notePage by remember(notesVersion) { mutableIntStateOf(0) }
    var notesSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    ScreenScaffold(
        title = "Updates",
        overline = "Margin ${BuildConfig.VERSION_NAME}",
        modifier = modifier,
        onBack = onBack,
        backIcon = Lucide.ArrowLeft,
        backLabel = "Back",
        actions = {
            IconPressButton(
                icon = Lucide.RefreshCw,
                label = "Check again",
                enabled = !busy && state !is UpdateState.Ready,
                bordered = true,
                onClick = onCheck,
            )
        },
    ) {
        // Release notes are cut into pages that fit the room, so no line is lost.
        val notes = (state as? UpdateState.Available)?.let { UpdateStrings.plainNotes(it.release.notes) }.orEmpty()
        // Rows and characters per row come from the measured notes area, so a long line wraps and no row is cut.
        val rowsPerPage = (notesSize.height / with(density) { EinkType.body.lineHeight.toPx() }).toInt().coerceAtLeast(1)
        val charsPerRow = with(density) {
            (notesSize.width.toDp().value / (EinkType.body.fontSize.toDp().value * 0.55f)).toInt().coerceAtLeast(10)
        }
        val pages = notePages(notes.lines().filter { it.isNotEmpty() }, rowsPerPage, charsPerRow)
        val pageIndex = notePage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

        // Cut off at the footer if a long message ever grows too tall. The
        // primary button sits below this, so it is never cut.
        Column(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            EinkText(text = UpdateStrings.headline(state), style = EinkType.rowTitle)
            UpdateStrings.detail(state)?.let { detail ->
                Spacer(modifier = Modifier.height(6.dp))
                // Black, not grey: e-ink rule 4 keeps grey for large inactive
                // text, and the reason an update failed is neither.
                EinkText(text = detail, style = EinkType.body, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }

            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EinkDimens.blockGap))
                CapsLabel(text = "What's New", style = EinkType.capsSmall)
                HairlineDivider(color = EinkColors.Faded)
                Spacer(modifier = Modifier.height(6.dp))
                // Only this box is measured, so the headline and the detail above
                // do not count against the rows a page may have.
                Box(modifier = Modifier.fillMaxWidth().weight(1f).onSizeChanged { notesSize = it }.clipToBounds()) {
                    if (pages.isNotEmpty()) EinkText(text = pages[pageIndex].joinToString("\n"), style = EinkType.body)
                }
            }
        }

        // The primary button and the page controls stay in a fixed area below the notes.
        val hasPrimary = state is UpdateState.Available || state is UpdateState.Ready ||
            (state is UpdateState.Failed && state.tokenProblem)
        if (hasPrimary) {
            Spacer(modifier = Modifier.height(EinkDimens.targetGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is UpdateState.Available -> InvertPressButton(
                        text = "Update",
                        icon = Lucide.Download,
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                    )
                    is UpdateState.Ready -> InvertPressButton(
                        text = "Install",
                        icon = Lucide.Download,
                        onClick = onInstall,
                        modifier = Modifier.weight(1f),
                    )
                    is UpdateState.Failed -> if (state.tokenProblem) {
                        InvertPressButton(text = "Add Token", onClick = onToken, modifier = Modifier.weight(1f))
                    }
                    else -> Unit
                }
                if (pages.size > 1) {
                    IconPressButton(
                        icon = Lucide.ChevronLeft,
                        label = "Previous",
                        onClick = { notePage = pageIndex - 1 },
                        enabled = pageIndex > 0,
                        bordered = true,
                    )
                    IconPressButton(
                        icon = Lucide.ChevronRight,
                        label = "Next",
                        onClick = { notePage = pageIndex + 1 },
                        enabled = pageIndex < pages.size - 1,
                        bordered = true,
                    )
                }
            }
        }

        // The footer shows only when it has something to say. The repository
        // is public, so a token is only ever for a private fork, and the way
        // to enter one is the button that comes with the "not found" failure.
        if (!installAllowed || hasToken) {
            HairlineDivider(color = EinkColors.Faded)
            Spacer(modifier = Modifier.height(10.dp))
            if (!installAllowed) {
                EinkText(
                    text = "Allow installs from Margin, then come back.",
                    style = EinkType.body,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EinkDimens.targetGap)) {
                if (!installAllowed) InvertPressButton(text = "Allow Installs", onClick = onAllowInstalls)
                if (hasToken) InvertPressButton(text = "Change Token", onClick = onToken)
            }
            if (hasToken) {
                Spacer(modifier = Modifier.height(10.dp))
                CapsLabel(
                    text = "Token saved",
                    style = EinkType.capsSmall,
                )
            }
        }
    }
}

/**
 * Cuts the release-note lines into pages. A line costs one row for each
 * [charsPerRow] characters it has (at least one row). Lines are packed in order
 * while the rows fit in [rowsPerPage]. A line too tall for a page gets a page of its own.
 */
internal fun notePages(lines: List<String>, rowsPerPage: Int, charsPerRow: Int): List<List<String>> {
    val pages = mutableListOf<List<String>>()
    var page = mutableListOf<String>()
    var used = 0
    for (line in lines) {
        val cost = maxOf(1, (line.length + charsPerRow - 1) / charsPerRow)
        if (page.isNotEmpty() && used + cost > rowsPerPage) {
            pages += page
            page = mutableListOf()
            used = 0
        }
        page += line
        used += cost
    }
    if (page.isNotEmpty()) pages += page
    return pages
}

/** The words of the update screen. Kept apart so a plain test can read them. */
object UpdateStrings {

    fun headline(state: UpdateState): String = when (state) {
        UpdateState.Idle -> "Not checked"
        UpdateState.Checking -> "Checking"
        is UpdateState.UpToDate -> "Up to date"
        is UpdateState.Available -> "Version ${state.release.version} available"
        is UpdateState.Downloading -> "Downloading ${state.release.version}, ${state.percent}%"
        is UpdateState.Ready ->
            if (state.handedOver) "Downloaded ${state.release.version}" else "Installing"
        is UpdateState.Failed -> "Failed"
    }

    fun detail(state: UpdateState): String? = when (state) {
        UpdateState.Idle, UpdateState.Checking -> null
        is UpdateState.UpToDate ->
            if (state.newest == null) "No release found" else "Latest is ${state.newest}"
        is UpdateState.Available -> "${megabytes(state.asset.sizeBytes)}. Your files stay."
        is UpdateState.Downloading -> "You can leave. It keeps going."
        is UpdateState.Ready -> state.note
        is UpdateState.Failed -> state.message
    }

    fun megabytes(bytes: Long): String = "${(bytes + 524_288) / 1_048_576} MB"

    /**
     * Release notes are Markdown. The screen shows plain text, so this takes
     * off the heading marks, joins a wrapped line back to its start, and
     * drops the blank lines.
     */
    fun plainNotes(markdown: String): String {
        val lines = mutableListOf<String>()
        markdown.lines().forEach { raw ->
            val line = raw.trim().trimStart('#').trim()
            when {
                line.isEmpty() -> Unit
                // A wrapped bullet: the next line starts with spaces, not with a mark.
                raw.startsWith("  ") && !line.startsWith("- ") && lines.isNotEmpty() ->
                    lines[lines.size - 1] = lines.last() + " " + line
                else -> lines += line
            }
        }
        // The headline already names the version. A first line that only
        // repeats it is dropped.
        if (lines.firstOrNull()?.let(AppVersion::parse) != null) lines.removeAt(0)
        return lines.joinToString("\n")
    }
}
