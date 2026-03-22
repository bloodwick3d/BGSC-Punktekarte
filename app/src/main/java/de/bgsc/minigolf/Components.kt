package de.bgsc.minigolf

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Definition der Calibri FontFamily
val CalibriFontFamily = FontFamily(
    Font(R.font.calibri, FontWeight.Normal),
    Font(R.font.calibri_bold, FontWeight.Bold)
)

// Erstellt ein Typography-Objekt, das Calibri als Standard verwendet
val CalibriTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = CalibriFontFamily),
        displayMedium = displayMedium.copy(fontFamily = CalibriFontFamily),
        displaySmall = displaySmall.copy(fontFamily = CalibriFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = CalibriFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = CalibriFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = CalibriFontFamily),
        titleLarge = titleLarge.copy(fontFamily = CalibriFontFamily),
        titleMedium = titleMedium.copy(fontFamily = CalibriFontFamily),
        titleSmall = titleSmall.copy(fontFamily = CalibriFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = CalibriFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = CalibriFontFamily),
        bodySmall = bodySmall.copy(fontFamily = CalibriFontFamily),
        labelLarge = labelLarge.copy(fontFamily = CalibriFontFamily),
        labelMedium = labelMedium.copy(fontFamily = CalibriFontFamily),
        labelSmall = labelSmall.copy(fontFamily = CalibriFontFamily)
    )
}

/**
 * Ein verallgemeinerter Click-Modifier, der automatisch Sound und Haptik abspielt,
 * sofern dies in den Einstellungen aktiviert ist.
 */
@Composable
fun Modifier.golfClickable(
    hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
    enabled: Boolean = true,
    viewModel: GolfViewModel = viewModel(),
    onClick: () -> Unit
): Modifier {
    val sound = LocalSoundFeedback.current
    val haptic = LocalHapticFeedback.current
    
    return this.clickable(
        enabled = enabled,
        onClick = {
            if (viewModel.soundEnabled) sound.playClick()
            if (viewModel.hapticEnabled) haptic.performHapticFeedback(hapticType)
            onClick()
        }
    )
}

/**
 * Ein Modifier, der sowohl Klick als auch Long-Press unterstützt (mit Sound & Haptik).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.golfCombinedClickable(
    enabled: Boolean = true,
    viewModel: GolfViewModel = viewModel(),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val sound = LocalSoundFeedback.current
    val haptic = LocalHapticFeedback.current
    
    return this.combinedClickable(
        enabled = enabled,
        onClick = {
            if (viewModel.soundEnabled) sound.playClick()
            if (viewModel.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        onLongClick = onLongClick?.let {
            {
                if (viewModel.soundEnabled) sound.playClick()
                if (viewModel.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                it()
            }
        }
    )
}

/**
 * Eine Hilfsfunktion für onClick-Lambdas, die Sound und Haptik auslöst,
 * sofern dies in den Einstellungen aktiviert ist.
 */
@Composable
fun golfClick(
    hapticType: HapticFeedbackType = HapticFeedbackType.LongPress,
    viewModel: GolfViewModel = viewModel(),
    onClick: () -> Unit
): () -> Unit {
    val sound = LocalSoundFeedback.current
    val haptic = LocalHapticFeedback.current
    return {
        if (viewModel.soundEnabled) sound.playClick()
        if (viewModel.hapticEnabled) haptic.performHapticFeedback(hapticType)
        onClick()
    }
}

@Composable
fun MiniGolfTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color.Black, surface = Color.White, onSurface = Color.Black,
        surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color.Black,
        background = Color.White, onBackground = Color.Black, surfaceTint = Color.White
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CalibriTypography,
        content = content
    )
}

@Composable
fun TournamentThemeWrapper(theme: TournamentTheme, content: @Composable () -> Unit) {
    val darkTheme = when (theme) {
        TournamentTheme.LIGHT -> false
        TournamentTheme.DARK -> true
        TournamentTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color.White,
            surface = Color(0xFF1C1C1C),
            onSurface = Color.White,
            background = Color(0xFF121212),
            onBackground = Color.White,
            surfaceVariant = Color(0xFF2C2C2C),
            onSurfaceVariant = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            background = Color.White,
            onBackground = Color.Black,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CalibriTypography,
        content = content
    )
}

@Composable
fun SideMenuItem(icon: ImageVector, text: String, onClick: () -> Unit, contentColor: Color = Color.White) {
    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    Row(modifier = Modifier.fillMaxWidth().golfClickable {
        onClick()
    }.padding(vertical = 10.adaptiveDp(), horizontal = 20.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = Color.Black.copy(alpha = 0.3f), 
                modifier = Modifier
                    .size(24.adaptiveDp())
                    .offset(1.5.dp, 1.5.dp)
            )
            Icon(
                icon, 
                contentDescription = null, 
                tint = contentColor, 
                modifier = Modifier.size(24.adaptiveDp())
            )
        }
        Spacer(Modifier.width(16.adaptiveDp()))
        Text(
            text = text, 
            color = contentColor, 
            fontSize = 14.adaptiveSp(), 
            fontWeight = FontWeight.Bold,
            style = shadowStyle
        )
    }
}

@Composable
fun FireworkEffect(visible: Boolean) {
    if (!visible) return
    val infiniteTransition = rememberInfiniteTransition(label = "fireworks")
    val animProgress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "progress")
    val bursts = remember {
        List(6) {
            val centerX = Random.nextFloat()
            val centerY = Random.nextFloat() * 0.5f + 0.1f
            val color = Color.hsv(Random.nextFloat() * 360f, 0.8f, 1f)
            val startTime = Random.nextFloat()
            val particles = List(40) { (Random.nextFloat() * 2f * PI.toFloat()) to (Random.nextFloat() * 0.6f + 0.4f) }
            object { val x = centerX; val y = centerY; val color = color; val startTime = startTime; val particles = particles }
        }
    }
    ComposeCanvas(modifier = Modifier.fillMaxSize().zIndex(10f)) {
        bursts.forEach { burst ->
            var localProgress = animProgress - burst.startTime
            if (localProgress < 0) localProgress += 1f
            if (localProgress < 0.4f) {
                val t = localProgress / 0.4f
                val alpha = 1f - t
                val distanceBase = t * size.width * 0.35f
                burst.particles.forEach { (angle, speed) ->
                    val distance = distanceBase * speed
                    val px = burst.x * size.width + cos(angle.toDouble()).toFloat() * distance
                    val py = burst.y * size.height + sin(angle.toDouble()).toFloat() * distance + (t * t * 200f)
                    drawCircle(color = burst.color.copy(alpha = alpha), center = Offset(px, py), radius = 3.dp.toPx() * (1f - t * 0.5f))
                }
            }
        }
    }
}

@Composable
fun TickerText(value: Int, style: TextStyle, color: Color) {
    var displayValue by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        val startValue = displayValue
        val duration = 1000L
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < duration) {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / duration
            displayValue = startValue + ((value - startValue) * progress).toInt()
            delay(16)
        }
        displayValue = value
    }
    Text(text = "$displayValue Pkt.", style = style.copy(fontFamily = CalibriFontFamily), color = color)
}

@Composable
fun WinnerCard(
    allPlayers: List<Player>,
    selectedSystem: String,
    isSharing: Boolean = false,
    canAddRound: Boolean = true,
    onShare: () -> Unit = {},
    onNextRound: () -> Unit = {},
    onRestart: () -> Unit = {},
    onResetAll: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onGloballyPositioned: (LayoutCoordinates) -> Unit = {}
) {
    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )
    val buttonShape = RoundedCornerShape(20.dp)

    val sortedPlayers = allPlayers.sortedBy { it.roundScores.flatten().filterNotNull().sum() }
    val winners = sortedPlayers.filter { it.roundScores.flatten().filterNotNull().sum() == sortedPlayers.first().roundScores.flatten().filterNotNull().sum() }
    val numRounds = allPlayers.firstOrNull()?.roundScores?.size ?: 1
    val appearAnim = remember { Animatable(0f) }
    var showFireworks by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appearAnim.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
        if (!isSharing) { showFireworks = true; delay(5000); showFireworks = false }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() })
        FireworkEffect(visible = showFireworks)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .scale(if (isSharing) 1f else 0.8f + 0.2f * appearAnim.value)
                .alpha(if (isSharing) 1f else appearAnim.value)
                .onGloballyPositioned { coords -> onGloballyPositioned(coords) }
                .zIndex(1f)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(28.adaptiveDp()),
            color = Color.White,
            contentColor = Color.Black,
            shadowElevation = if (isSharing) 0.dp else 12.adaptiveDp()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!isSharing) {
                    IconButton(
                        onClick = golfClick { onShare() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.adaptiveDp())
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                            Icon(Icons.Default.Share, contentDescription = "Teilen", tint = Color.Black)
                        }
                    }
                }

                Column(modifier = Modifier.padding(24.adaptiveDp()).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val trophyScale by rememberInfiniteTransition("t").animateFloat(1f, 1.2f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "s")
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.EmojiEvents, 
                            null, 
                            tint = Color.Black.copy(alpha = 0.2f), 
                            modifier = Modifier.size(80.adaptiveDp()).scale(if (isSharing) 1.1f else trophyScale).offset(2.dp, 2.dp)
                        )
                        Icon(
                            Icons.Default.EmojiEvents, 
                            null, 
                            tint = Color(0xFFFFD700), 
                            modifier = Modifier.size(80.adaptiveDp()).scale(if (isSharing) 1.1f else trophyScale)
                        )
                    }
                    Spacer(Modifier.height(8.adaptiveDp()))
                    Text("Herzlichen Glückwunsch!", fontWeight = FontWeight.Bold, fontSize = 20.adaptiveSp(), textAlign = TextAlign.Center, style = shadowStyle)
                    Text(selectedSystem.replace("\n", " "), fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                    Spacer(Modifier.height(16.adaptiveDp()))
                    winners.forEach { Text(it.name, fontSize = 24.adaptiveSp(), fontWeight = FontWeight.ExtraBold, color = it.color, textAlign = TextAlign.Center, style = shadowStyle.copy(color = it.color)) }
                    Text(if(winners.size > 1) "haben gewonnen!" else "hat gewonnen!", fontSize = 16.adaptiveSp(), style = shadowStyle)
                    Spacer(Modifier.height(24.adaptiveDp())); Text("Rangliste:", fontWeight = FontWeight.Bold, fontSize = 18.adaptiveSp(), style = shadowStyle)
                    sortedPlayers.forEachIndexed { idx, player ->
                        var itemVisible by remember { mutableStateOf(isSharing) }
                        LaunchedEffect(Unit) { if(!isSharing) { delay(idx * 200L + 500L); itemVisible = true } }
                        AnimatedVisibility(visible = itemVisible, enter = slideInHorizontally() + fadeIn()) {
                            val total = player.roundScores.flatten().filterNotNull().sum()
                            val isFullGame = player.roundScores.all { rs -> rs.all { it != null } }
                            val totalColor = if (isFullGame) getScoreColor(total, selectedSystem, Color.Black, numRounds) else Color.Black
                            
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.adaptiveDp()), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${idx + 1}. ${player.name}", fontWeight = FontWeight.Bold, color = player.color, fontSize = 18.adaptiveSp(), style = shadowStyle.copy(color = player.color))
                                    if (numRounds > 1) {
                                        Row {
                                            Text("Runden: ", fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                                            player.roundScores.forEachIndexed { rIdx, round ->
                                                val rSum = round.filterNotNull().sum()
                                                val isRoundFull = round.all { it != null }
                                                val rColor = if (isRoundFull) getScoreColor(rSum, selectedSystem, Color.DarkGray, 1) else Color.DarkGray
                                                Text(rSum.toString(), fontSize = 12.adaptiveSp(), color = rColor, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = rColor))
                                                if (rIdx < numRounds - 1) Text(" | ", fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                                            }
                                        }
                                    }
                                }
                                if (isSharing) Text("$total Pkt.", fontWeight = FontWeight.ExtraBold, color = totalColor, fontSize = 18.adaptiveSp(), style = shadowStyle.copy(color = totalColor))
                                else TickerText(total, shadowStyle.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.adaptiveSp()), totalColor)
                            }
                        }
                    }
                    if (!isSharing) {
                        Spacer(Modifier.height(24.adaptiveDp()))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canAddRound) {
                                Button(
                                    onClick = golfClick { onNextRound() }, 
                                    modifier = Modifier.weight(1f), 
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)), 
                                    contentPadding = PaddingValues(horizontal = 4.adaptiveDp()),
                                    shape = buttonShape,
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                                ) { 
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.dp, 1.dp))
                                        Icon(Icons.Default.AddCircleOutline, null)
                                    }
                                    Spacer(Modifier.width(4.adaptiveDp())); Text("Nächste Runde", fontSize = 12.adaptiveSp(), style = shadowStyle) 
                                }
                            }
                            Button(
                                onClick = golfClick { onRestart() }, 
                                modifier = Modifier.weight(1f), 
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), 
                                contentPadding = PaddingValues(horizontal = 4.adaptiveDp()),
                                shape = buttonShape,
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                                ) { 
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Refresh, null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.dp, 1.dp))
                                        Icon(Icons.Default.Refresh, null)
                                    }
                                    Spacer(Modifier.width(4.adaptiveDp())); Text("Neu starten", fontSize = 12.adaptiveSp(), style = shadowStyle) 
                                }
                        }
                        Spacer(Modifier.height(12.adaptiveDp()))
                        Button(
                            onClick = golfClick(hapticType = HapticFeedbackType.LongPress) { onResetAll() },
                            modifier = Modifier.fillMaxWidth(), 
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(20.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                        ) { 
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Stop, null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.dp, 1.dp))
                                Icon(Icons.Default.Stop, null)
                            }
                            Spacer(Modifier.width(8.dp)); Text("Spiel beenden", style = shadowStyle)
                        }
                    } else { Spacer(Modifier.height(16.adaptiveDp())); Image(painterResource(R.drawable.bgsc_logo), null, Modifier.size(60.adaptiveDp())) }
                }
            }
        }
    }
}

@Composable
fun ScoreInputDialog(currentScore: Int?, offset: Offset, onDismiss: () -> Unit, onScoreSelected: (Int?, Offset) -> Unit) {
    ScoreCircleMenu(currentScore = currentScore, menuOffset = offset, onScoreSelected = onScoreSelected, onDismiss = onDismiss)
}

@Composable
fun GolfSuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.golfClickable { onClick() },
        shape = RoundedCornerShape(16.adaptiveDp()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.adaptiveDp(), vertical = 6.adaptiveDp()),
            fontSize = 12.adaptiveSp(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = CalibriFontFamily
        )
    }
}
