package io.github.foxesrcool1.margin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.foxesrcool1.margin.core.routine.PartOfDay
import io.github.foxesrcool1.margin.design.EinkColors
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkShapes
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.LocalBotanicalArtEnabled
import io.github.foxesrcool1.margin.design.components.CapsLabel
import io.github.foxesrcool1.margin.design.components.EinkIcon
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.PlantArt
import io.github.foxesrcool1.margin.design.components.Plants
import io.github.foxesrcool1.margin.design.einkClickable
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.design.icons.LucideIcon
import io.github.foxesrcool1.margin.ui.common.RotateButton
import io.github.foxesrcool1.margin.ui.split.SplitButton
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** The icon that stands for a tab on Home. */
fun LauncherRoute.icon(): LucideIcon = when (this) {
    LauncherRoute.Reading -> Lucide.BookOpen
    LauncherRoute.Writing -> Lucide.PenLine
    LauncherRoute.Journal -> Lucide.Notebook
    LauncherRoute.Apps -> Lucide.LayoutGrid
    LauncherRoute.Settings -> Lucide.Settings
    else -> Lucide.House
}

/**
 * The home screen.
 *
 * A big clock in the display serif with the date under it. Then the way back
 * to what the user was doing (the book read last or the note edited last),
 * the four large line icons, the next thing in the user's routine, and at the
 * bottom a status line and the way into Settings. One plant in a corner. A
 * lot of white space and nothing that moves.
 *
 * Upright, everything stands in one column and the four icons two by two.
 * With the tablet on its side there are two columns: the clock, Continue and
 * Next on the left, the four icons two by two on the right. The controls
 * across the bottom are never squeezed in either.
 */
@Composable
fun HomeScreen(
    onOpenTab: (LauncherRoute) -> Unit,
    onOpenSettings: () -> Unit,
    onQuickNote: () -> Unit = {},
    nextRoutineLabel: String? = null,
    /** When in the day the next item belongs. Any Time reads "Next", the others "This Evening" and so on. */
    nextRoutinePart: PartOfDay = PartOfDay.AnyTime,
    /**
     * True when the next item opens a tab or an app, and its button says Start.
     * False for an item with nothing to open: its button ticks the item off and says Done.
     */
    nextOpensSomething: Boolean = true,
    onStartNext: () -> Unit = {},
    /** The book or note to go back to. Null leaves the row out. */
    continueTarget: ContinueTarget? = null,
    onContinue: (ContinueTarget) -> Unit = {},
    modifier: Modifier = Modifier,
    now: LocalDateTime? = null,
    /** Null: the way the tablet is set, in its date and time settings. */
    use24Hour: Boolean? = null,
    battery: Int? = null,
    wifi: Boolean? = null,
    /** The name of each tab under its icon. It is a setting, and it starts off. */
    showLabels: Boolean = false,
) {
    val context = LocalContext.current
    val clock by rememberMinuteClock()
    val time = now ?: clock

    // All three are read once a minute, with the clock, and not on a timer of
    // their own. E-ink rule 7: as few screen changes as possible.
    val batteryPercent = remember(time.minute, battery) { battery ?: batteryPercent(context) }
    val onWifi = remember(time.minute, wifi) { wifi ?: onWifi(context) }
    val twentyFourHour = remember(time.minute, use24Hour) {
        use24Hour ?: runCatching { android.text.format.DateFormat.is24HourFormat(context) }.getOrDefault(true)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        // A narrow half of the split screen, or a short one, has no corner to
        // spare: the plant would sit on the clock or the icons, and the clock
        // and the gaps have to be smaller.
        val compact = maxWidth < 420.dp || maxHeight < 400.dp
        val clockStyle = if (compact) ClockSmall else ClockLarge
        val gap = if (compact) 8.dp else 16.dp

        // Top corner. The bottom edge holds the controls, and a drawing under
        // a control makes both harder to read.
        if (!compact) PlantArt(
            plant = Plants.Home,
            size = PLANT_SIZE,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 12.dp, end = 20.dp),
        )
        RotateButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(start = 12.dp, top = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(
                    horizontal = EinkDimens.screenMargin,
                    vertical = if (wide || compact) 16.dp else EinkDimens.screenMargin,
                ),
        ) {
            val next: @Composable () -> Unit = {
                // The next thing in the user's own routine, if they have one.
                // It is left out entirely when the routine is empty or
                // finished, rather than showing an empty box: a blank line on
                // e-ink is a repaint for nothing.
                if (nextRoutineLabel != null) {
                    NextRow(
                        label = nextRoutineLabel,
                        part = nextRoutinePart,
                        opensSomething = nextOpensSomething,
                        onStart = onStartNext,
                    )
                }
            }
            val goOn: @Composable () -> Unit = {
                if (continueTarget != null) {
                    ContinueRow(target = continueTarget, onClick = { onContinue(continueTarget) })
                }
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(EinkDimens.blockGap),
                ) {
                    Column(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                        HomeClock(time, twentyFourHour, clockStyle)
                        Spacer(modifier = Modifier.height(gap))
                        goOn()
                        Spacer(modifier = Modifier.height(gap))
                        Spacer(modifier = Modifier.weight(1f))
                        next()
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // The plant stands over this column, so the icons start below it.
                    val plantRoom = if (!compact && LocalBotanicalArtEnabled.current) PLANT_SIZE else 0.dp
                    TabGrid(
                        showLabels = showLabels,
                        onOpenTab = onOpenTab,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(top = plantRoom),
                    )
                }
            } else {
                if (!compact) Spacer(modifier = Modifier.height(8.dp))
                HomeClock(time, twentyFourHour, clockStyle)
                Spacer(modifier = Modifier.height(gap))
                goOn()
                // The four icons get whatever room is left between the header
                // and the controls, and get smaller when that is not enough
                // for them. The controls at the bottom are never the ones that
                // get squeezed: a home screen with a cut off Settings control
                // is a trap.
                TabGrid(
                    showLabels = showLabels,
                    onOpenTab = onOpenTab,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                if (nextRoutineLabel != null) {
                    next()
                    Spacer(modifier = Modifier.height(EinkDimens.targetGap))
                }
            }

            HairlineDivider(color = EinkColors.Faded)
            Spacer(modifier = Modifier.height(if (wide || compact) 4.dp else EinkDimens.targetGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The status is one target, as wide as it is. The room to its
                // right is not part of it.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    StatusLine(
                        batteryPercent = batteryPercent,
                        onWifi = onWifi,
                        onClick = { openInternetPanel(context) },
                    )
                }
                IconPressButton(icon = Lucide.SquarePen, label = "Quick note", onClick = onQuickNote)
                Spacer(modifier = Modifier.width(4.dp))
                SplitButton()
                Spacer(modifier = Modifier.width(4.dp))
                IconPressButton(icon = Lucide.Settings, label = "Settings", onClick = onOpenSettings)
            }
        }
    }
}

private val PLANT_SIZE = 72.dp

/**
 * The time in Bodoni Moda, on the natural line height of the font. The digits
 * are all one width, so the clock does not shift by a few pixels each minute.
 */
private val ClockLarge = EinkType.title.copy(
    fontSize = 88.sp,
    lineHeight = TextUnit.Unspecified,
    fontFeatureSettings = "tnum",
)
private val ClockSmall = ClockLarge.copy(fontSize = 56.sp)

/**
 * Cuts a part of the height of a thing off the top and the bottom, without
 * clipping it. Bodoni Moda keeps room above its capitals for accents and
 * below the line for tails, and digits have neither: left alone, that room
 * pushes the date far from the time and takes height the screen does not
 * have. The parts are fractions of the height, so they hold at every size.
 */
private fun Modifier.trimmed(top: Float, bottom: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val cutTop = (placeable.height * top).roundToInt()
    val cutBottom = (placeable.height * bottom).roundToInt()
    layout(placeable.width, (placeable.height - cutTop - cutBottom).coerceAtLeast(0)) {
        placeable.place(0, -cutTop)
    }
}

/**
 * The time, large and in the middle, and the date in small capitals under it.
 * For 12 hour time, AM or PM stands small on the line of the digits.
 */
@Composable
private fun HomeClock(
    time: LocalDateTime,
    twentyFourHour: Boolean,
    digits: TextStyle,
    modifier: Modifier = Modifier,
) {
    val spoken = HomeStrings.time(time, twentyFourHour) + ", " + HomeStrings.date(time)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.trimmed(top = 0.2f, bottom = 0.22f)) {
            EinkText(
                text = HomeStrings.digits(time, twentyFourHour),
                style = digits,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            if (!twentyFourHour) {
                Spacer(modifier = Modifier.width(8.dp))
                CapsLabel(
                    text = HomeStrings.meridiem(time),
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        CapsLabel(
            text = HomeStrings.date(time),
            style = EinkType.caps.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The way back to the book or the note the user was last in. The whole row is
 * one target and it inverts while pressed. The percent at the end is only
 * there for a book that has been read into.
 */
@Composable
private fun ContinueRow(
    target: ContinueTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink
    val percent = (target as? ContinueTarget.Book)?.percent.let(HomeStrings::percent)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EinkDimens.touchTarget)
            .clip(EinkShapes.field)
            .background(if (pressed) EinkColors.Ink else EinkColors.Paper)
            .border(EinkDimens.hairline, EinkColors.Ink, EinkShapes.field)
            .einkClickable(interactionSource = interactionSource, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = "Continue", style = EinkType.capsSmall.copy(color = foreground))
            EinkText(
                text = target.title,
                style = EinkType.rowTitle.copy(color = foreground),
                maxLines = 1,
            )
        }
        if (percent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            CapsLabel(text = percent, style = EinkType.capsSmall.copy(color = foreground))
        }
    }
}

/** The next routine item, with a button that starts it, or ticks it off when there is nothing to start. */
@Composable
private fun NextRow(
    label: String,
    part: PartOfDay,
    opensSomething: Boolean,
    onStart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CapsLabel(text = HomeStrings.nextOverline(part), style = EinkType.capsSmall)
            Spacer(modifier = Modifier.height(4.dp))
            EinkText(
                text = label,
                style = EinkType.rowTitle,
                maxLines = 1,
            )
        }
        IconPressButton(
            icon = if (opensSomething) Lucide.Play else Lucide.Check,
            label = if (opensSomething) "Start" else "Done",
            onClick = onStart,
            bordered = true,
        )
    }
}

/**
 * The four icons, two by two, in the middle of the room they are given. The
 * cells get smaller when the room is short, and never larger than 120 dp.
 */
@Composable
private fun TabGrid(
    showLabels: Boolean,
    onOpenTab: (LauncherRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gap = 20.dp
        val cell = minOf((maxHeight - gap) / 2, (maxWidth - gap) / 2, 120.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            LauncherRoute.tabs.chunked(2).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    line.forEach { tab ->
                        HomeTab(
                            route = tab,
                            side = cell,
                            showLabel = showLabels,
                            onClick = { onOpenTab(tab) },
                        )
                    }
                }
            }
        }
    }
}

/** One of the four large icons. The whole cell is the target, and the whole cell inverts. */
@Composable
private fun HomeTab(
    route: LauncherRoute,
    side: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink

    Column(
        modifier = Modifier
            .size(side)
            .clip(EinkShapes.panel)
            .background(if (pressed) EinkColors.Ink else EinkColors.Paper)
            .einkClickable(interactionSource = interactionSource, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = route.title
                role = Role.Button
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EinkIcon(
            icon = route.icon(),
            size = when {
                showLabel && side < 84.dp -> 32.dp
                side < 110.dp -> 44.dp
                else -> EinkDimens.homeIcon
            },
            color = foreground,
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(12.dp))
            CapsLabel(
                text = route.title,
                style = EinkType.capsSmall.copy(color = foreground),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Wi-Fi and battery, as two icons and one number. A part that is not known is
 * left out. The whole line is one target that opens the Wi-Fi panel, so Wi-Fi
 * goes off in one tap, and it inverts while pressed like the other targets.
 */
@Composable
private fun StatusLine(
    batteryPercent: Int?,
    onWifi: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val foreground = if (pressed) EinkColors.Paper else EinkColors.Ink

    Row(
        modifier = modifier
            .heightIn(min = EinkDimens.touchTarget)
            .clip(EinkShapes.field)
            .background(if (pressed) EinkColors.Ink else EinkColors.Paper)
            .einkClickable(interactionSource = interactionSource, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Wi-Fi"
                stateDescription = HomeStrings.status(batteryPercent, onWifi)
                role = Role.Button
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkIcon(
            icon = if (onWifi) Lucide.Wifi else Lucide.WifiOff,
            size = 20.dp,
            color = if (onWifi || pressed) foreground else EinkColors.Faded,
        )
        if (batteryPercent != null) {
            EinkIcon(icon = HomeStrings.batteryIcon(batteryPercent), size = 20.dp, color = foreground)
            CapsLabel(
                text = "$batteryPercent%",
                style = EinkType.capsSmall.copy(color = foreground),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
