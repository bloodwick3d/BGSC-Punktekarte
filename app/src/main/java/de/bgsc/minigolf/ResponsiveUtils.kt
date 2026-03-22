package de.bgsc.minigolf

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Berechnet einen DP-Wert proportional zur Breite des aktuellen Fensters.
 * Referenz ist eine Breite von 360dp.
 */
@Composable
fun Int.adaptiveDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return (this * (screenWidthDp.value / 360f)).dp
}

@Composable
fun Double.adaptiveDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return (this.toFloat() * (screenWidthDp.value / 360f)).dp
}

/**
 * Berechnet einen SP-Wert proportional zur Breite des aktuellen Fensters.
 */
@Composable
fun Int.adaptiveSp(): TextUnit {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return (this * (screenWidthDp.value / 360f)).sp
}
