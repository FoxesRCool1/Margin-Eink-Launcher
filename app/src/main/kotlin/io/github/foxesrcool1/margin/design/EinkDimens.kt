package io.github.foxesrcool1.margin.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes used across the app.
 *
 * E-ink rule 5: lines are 1 dp or 2 dp (icons excepted, decision 0026), no
 * shadows, no gradients.
 * E-ink rule 6: a touch target is 56 dp or more, with space around it.
 */
object EinkDimens {
    /** The smallest height of anything the finger or the pen can hit. */
    val touchTarget: Dp = 56.dp

    /** Side margin of a full screen. */
    val screenMargin: Dp = 28.dp

    /** Space between two blocks on a screen. */
    val blockGap: Dp = 24.dp

    /** Space between two targets, so a finger cannot hit both. */
    val targetGap: Dp = 12.dp

    /** A fine rule. */
    val hairline: Dp = 1.dp

    /** A heavy rule. */
    val rule: Dp = 2.dp

    /** A row in a list with one line of text, and the rule under it. */
    val rowOneLine: Dp = 58.dp

    /** A row in a list with a title and one small line under it, and the rule under it. */
    val rowTwoLines: Dp = 68.dp

    /** The side of an icon inside a control. */
    val icon: Dp = 24.dp

    /** The side of one of the four large icons on Home. */
    val homeIcon: Dp = 56.dp
}
