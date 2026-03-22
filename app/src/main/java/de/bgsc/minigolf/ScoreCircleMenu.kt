package de.bgsc.minigolf

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ScoreCircleMenu(
    currentScore: Int? = null,
    menuOffset: Offset = Offset.Zero,
    onScoreSelected: (Int?, Offset) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sound = LocalSoundFeedback.current
    
    // Animation states for the background and buttons
    val bgAlpha = remember { Animatable(0f) }
    val buttonAnims = remember { List(8) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        launch { bgAlpha.animateTo(1f, tween(200)) }
        buttonAnims.forEachIndexed { index, anim ->
            launch {
                delay(index * 30L)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (0.4f * bgAlpha.value).coerceIn(0f, 1f)))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        
        // Skalierung auf 85% der Bildschirmbreite (adaptiv)
        val menuSize = maxWidth * 0.85f
        val menuSizePx = with(density) { menuSize.toPx() }
        val halfMenuSize = menuSizePx / 2f
        
        val buttonSize = menuSize * 0.24f
        val radius = (menuSize.value * 0.32f).dp 

        // Horizontal zentrieren, Vertikal am Klick-Offset orientieren
        val posX = (screenWidth - menuSizePx) / 2f
        var posY = menuOffset.y - halfMenuSize

        // Sicherstellen, dass das Menü vertikal im Bildschirm bleibt
        posY = posY.coerceIn(0f, screenHeight - menuSizePx)

        Box(
            modifier = Modifier
                .offset { IntOffset(posX.toInt(), posY.toInt()) }
                .size(menuSize)
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            val centerAnim = buttonAnims[0]
            var centerBtnPos by remember { mutableStateOf(Offset.Zero) }
            
            CircleButton(
                text = "",
                isSelected = currentScore == null,
                buttonSize = buttonSize,
                modifier = Modifier.onGloballyPositioned { centerBtnPos = it.boundsInRoot().center },
                alpha = centerAnim.value,
                scale = 0.8f + 0.2f * centerAnim.value
            ) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onScoreSelected(null, centerBtnPos)
            }

            for (i in 1..7) {
                val anim = buttonAnims[i]
                val currentRadius = radius * anim.value
                val angle = Math.toRadians((-90 + i * 360f / 7).toDouble())
                val x = cos(angle) * currentRadius.value
                val y = sin(angle) * currentRadius.value
                var btnPos by remember { mutableStateOf(Offset.Zero) }

                CircleButton(
                    text = i.toString(),
                    isSelected = currentScore == i,
                    buttonSize = buttonSize,
                    modifier = Modifier
                        .offset(x.dp, y.dp)
                        .onGloballyPositioned { btnPos = it.boundsInRoot().center },
                    alpha = anim.value,
                    scale = 0.8f + 0.2f * anim.value
                ) {
                    sound.playClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onScoreSelected(i, btnPos)
                }
            }
        }
    }
}

@Composable
fun CircleButton(
    text: String,
    isSelected: Boolean,
    buttonSize: Dp,
    modifier: Modifier,
    alpha: Float = 1f,
    scale: Float = 1f,
    onClick: () -> Unit
) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    val backgroundColor = Color.White
    val highlightColor = Color(0xFFF3CB0C)
    val textColor = if (isSelected) highlightColor else Color.Black

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .then(
                if (isSelected) Modifier.border(3.adaptiveDp(), highlightColor, CircleShape)
                else Modifier
            )
            .background(backgroundColor.copy(alpha = safeAlpha), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                fontSize = (buttonSize.value * 0.45f).sp,
                fontFamily = CalibriFontFamily,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = safeAlpha)
            )
        }
    }
}
