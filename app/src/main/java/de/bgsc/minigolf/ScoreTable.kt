package de.bgsc.minigolf

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScoreTable(
    players: List<Player>,
    numRounds: Int,
    selectedSystem: String,
    selectedHolePlayerRound: Triple<Int, Int, Int>?,
    onUpdateScore: (playerIdx: Int, roundIdx: Int, holeIdx: Int, offset: Offset) -> Unit,
    onPlayerClick: (Int) -> Unit,
    onAddPlayerClick: () -> Unit,
    onMovePlayer: (Int, Int) -> Unit,
    onRemoveRound: (Int) -> Unit,
    onAddRound: () -> Unit,
    shadowStyle: TextStyle,
    highlightAmber: Color,
    highlightGold: Color,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    
    // RESPONSIVE LOGIK
    val footerFactor = if(numRounds > 1) 1.5f else 1.0f
    val stickyColumnWidth = 35.adaptiveDp()
    val sidePadding = 10.adaptiveDp()
    val bottomPadding = 10.adaptiveDp()
    
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val playerColumnWidth = (screenWidthDp - stickyColumnWidth - (sidePadding * 2)) / 3.2f

    val currentHoleIndex = remember(players) {
        (0 until 18).firstOrNull { hIdx -> 
            players.any { p -> p.roundScores.last().getOrNull(hIdx) == null } 
        }
    }

    // Drag-and-Drop States
    var draggingPlayerIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    
    // State für Bestätigungs-Dialog beim Löschen einer Runde (explizit als MutableState für Linter)
    val roundToDeleteState = remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = sidePadding, end = sidePadding, bottom = bottomPadding), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // HEADER ROW
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val rowHeight = maxHeight
            Row(modifier = Modifier.horizontalScroll(scrollState).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                var showRoundMenu by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .width(stickyColumnWidth)
                        .fillMaxHeight()
                        .zIndex(2f)
                        .graphicsLayer { translationX = scrollState.value.toFloat() }
                        .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(topStart = 15.adaptiveDp()))
                        .clip(RoundedCornerShape(topStart = 15.adaptiveDp()))
                        .golfClickable { showRoundMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(rowHeight * 0.6f).offset(1.5.dp, 1.5.dp))
                        Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(rowHeight * 0.6f))
                    }
                    Text(text = numRounds.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (rowHeight.value * 0.4f).sp, style = shadowStyle)
                    
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent)) {
                        DropdownMenu(expanded = showRoundMenu, onDismissRequest = { showRoundMenu = false }, modifier = Modifier.background(Color.White.copy(alpha = 0.8f))) {
                            (0 until numRounds).forEach { rIdx ->
                                key(rIdx) {
                                    var offsetX by remember { mutableFloatStateOf(0f) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.adaptiveDp())
                                            .background(if (numRounds > 1) Color.Red.copy(alpha = (offsetX.absoluteValue / 300f).coerceIn(0f, 0.8f)) else Color.Transparent)
                                            .pointerInput(numRounds) {
                                                if (numRounds > 1) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = { 
                                                            if (offsetX.absoluteValue > size.width * 0.75f) { 
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                roundToDeleteState.value = rIdx
                                                                showRoundMenu = false
                                                            }
                                                            offsetX = 0f 
                                                        },
                                                        onHorizontalDrag = { _, dragAmount -> offsetX += dragAmount }
                                                    )
                                                }
                                            },
                                        contentAlignment = if(offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        if (offsetX.absoluteValue > 10f && numRounds > 1) { 
                                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.padding(horizontal = 16.adaptiveDp()).scale((offsetX.absoluteValue / 200f).coerceIn(0.5f, 1.2f))) 
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Runde ${rIdx + 1}", color = Color.Black, style = shadowStyle.copy(color = Color.Black)) },
                                            onClick = golfClick { showRoundMenu = false },
                                            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
                                        )
                                    }
                                }
                            }
                            if (numRounds < 4) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) { 
                                            Icon(Icons.Default.Add, null, tint = Color.Black)
                                            Spacer(Modifier.width(8.adaptiveDp()))
                                            Text("Neue Runde", color = Color.Black, style = shadowStyle.copy(color = Color.Black)) 
                                        } 
                                    },
                                    onClick = golfClick { onAddRound(); showRoundMenu = false }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(2.adaptiveDp()).zIndex(2f).graphicsLayer { translationX = scrollState.value.toFloat() })

                players.forEachIndexed { index, player ->
                    val isDragging = draggingPlayerIndex == index
                    val marqueeScrollState = rememberScrollState()
                    
                    LaunchedEffect(player.name, marqueeScrollState.maxValue) {
                        if (marqueeScrollState.maxValue > 0) {
                            while (true) {
                                delay(2000)
                                val speed = with(density) { 30.dp.toPx() }
                                val duration = (marqueeScrollState.maxValue / speed * 1000).toInt()
                                marqueeScrollState.animateScrollTo(marqueeScrollState.maxValue, tween(duration, easing = LinearEasing))
                                delay(2000)
                                marqueeScrollState.animateScrollTo(0, tween(duration, easing = LinearEasing))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(playerColumnWidth)
                            .fillMaxHeight()
                            .zIndex(if (isDragging) 10f else 1f)
                            .graphicsLayer {
                                translationX = if (isDragging) dragOffsetX else 0f
                                alpha = if (isDragging) 0.8f else 1f
                            }
                            .background(player.color)
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingPlayerIndex = index; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragEnd = { draggingPlayerIndex = null; dragOffsetX = 0f },
                                    onDragCancel = { draggingPlayerIndex = null; dragOffsetX = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        val threshold = with(density) { (playerColumnWidth.toPx() + 2.dp.toPx()) }
                                        if (dragOffsetX > threshold && index < players.size - 1) {
                                            onMovePlayer(index, index + 1)
                                            draggingPlayerIndex = index + 1
                                            dragOffsetX -= threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else if (dragOffsetX < -threshold && index > 0) {
                                            onMovePlayer(index, index - 1)
                                            draggingPlayerIndex = index - 1
                                            dragOffsetX += threshold
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                )
                            }
                            .golfClickable { onPlayerClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.adaptiveDp()).horizontalScroll(marqueeScrollState, enabled = false),
                            horizontalArrangement = if (marqueeScrollState.maxValue > 0) Arrangement.Start else Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = player.name, style = shadowStyle.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = (rowHeight.value * 0.45f).sp), maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.width(2.adaptiveDp()))
                }

                Box(
                    modifier = Modifier
                        .width(stickyColumnWidth)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(topEnd = 15.adaptiveDp()))
                        .clip(RoundedCornerShape(topEnd = 15.adaptiveDp()))
                        .golfClickable(enabled = players.size < 10) { onAddPlayerClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (players.size < 10) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(rowHeight * 0.45f).offset(1.dp, 1.dp))
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(rowHeight * 0.45f))
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))

        // Mittelteil (18 Löcher)
        Column(modifier = Modifier.weight(18f)) {
            (1..18).forEach { hole ->
                val hIdx = hole - 1
                val isCurrentHole = hIdx == currentHoleIndex
                val isEven = hole % 2 == 0
                val rowBaseColor = if (isEven) Color.White else Color(0xFFF5F5F5)
                
                val holeColumnColor by animateColorAsState(
                    targetValue = if (isCurrentHole) highlightAmber else (if (isEven) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f)),
                    animationSpec = tween(700), label = "holeHighlight"
                )
                
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val rowHeight = maxHeight
                    Row(modifier = Modifier.horizontalScroll(scrollState).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hole.toString(), 
                            modifier = Modifier.width(stickyColumnWidth).fillMaxHeight().zIndex(2f).graphicsLayer { translationX = scrollState.value.toFloat() }.background(holeColumnColor).wrapContentHeight(Alignment.CenterVertically), 
                            style = shadowStyle.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = if(isCurrentHole) Color.Black else Color.White, fontSize = (rowHeight.value * 0.5f).sp)
                        )
                        
                        Spacer(modifier = Modifier.width(2.adaptiveDp()).zIndex(2f).graphicsLayer { translationX = scrollState.value.toFloat() })

                        players.forEachIndexed { playerIndex, player ->
                            val isPlayerDragging = draggingPlayerIndex == playerIndex
                            Row(modifier = Modifier.width(playerColumnWidth).fillMaxHeight().zIndex(if (isPlayerDragging) 10f else 1f).graphicsLayer { translationX = if (isPlayerDragging) dragOffsetX else 0f; alpha = if (isPlayerDragging) 0.8f else 1f }) {
                                player.roundScores.forEachIndexed { roundIndex, scores ->
                                    val isLastRound = roundIndex == numRounds - 1
                                    val weight = if (numRounds > 1) (if (isLastRound) 1f else 1f / (numRounds - 1)) else 1f
                                    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                    val isCellSelected = selectedHolePlayerRound?.let { it.first == playerIndex && it.second == hIdx && it.third == roundIndex } ?: false
                                    
                                    val cellBgColor by animateColorAsState(targetValue = if(isCurrentHole) highlightAmber else rowBaseColor, animationSpec = tween(700), label = "cellHighlight")

                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(cellBgColor)
                                            .then(if (!isCurrentHole) Modifier.background(player.color.copy(alpha = if (isEven) 0.1f else 0.2f)) else Modifier)
                                            .border(0.5.dp, Color.LightGray.copy(alpha = 0.2f))
                                            .then(if (!isLastRound) Modifier.background(Color.Black.copy(alpha = 0.15f)) else Modifier)
                                            .then(if (isCellSelected) Modifier.border(3.adaptiveDp(), highlightGold) else Modifier)
                                            .onGloballyPositioned { layoutCoordinates = it }
                                            .golfCombinedClickable(
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val offset = layoutCoordinates?.let { 
                                                        val pos = it.positionInRoot()
                                                        pos + Offset(it.size.width / 2f, it.size.height / 2f)
                                                    } ?: Offset.Zero
                                                    onUpdateScore(playerIndex, roundIndex, hIdx, offset)
                                                },
                                                onClick = {
                                                    if (isLastRound) {
                                                        val offset = layoutCoordinates?.let { 
                                                            val pos = it.positionInRoot()
                                                            pos + Offset(it.size.width / 2f, it.size.height / 2f)
                                                        } ?: Offset.Zero
                                                        onUpdateScore(playerIndex, roundIndex, hIdx, offset)
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val scoreValue = scores.getOrNull(hIdx)
                                        val fontSize = if (isLastRound) (rowHeight.value * 0.55f).sp else (rowHeight.value * 0.4f).sp
                                        AnimatedContent(targetState = scoreValue, label = "scoreAnim") { targetScore ->
                                            Text(text = targetScore?.toString() ?: "", style = shadowStyle.copy(fontSize = fontSize, color = Color.Black), softWrap = false)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(2.adaptiveDp()))
                        }
                        Box(modifier = Modifier.width(stickyColumnWidth).fillMaxHeight().background(Color.Black.copy(alpha = 0.4f)))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // FOOTER ROW
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(footerFactor), contentAlignment = Alignment.Center) {
            val rowHeight = maxHeight
            Row(modifier = Modifier.horizontalScroll(scrollState).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(stickyColumnWidth).fillMaxHeight().zIndex(2f).graphicsLayer { translationX = scrollState.value.toFloat() }.background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(bottomStart = 15.adaptiveDp())).clip(RoundedCornerShape(bottomStart = 15.adaptiveDp())), contentAlignment = Alignment.Center) { }
                Spacer(modifier = Modifier.width(2.adaptiveDp()).zIndex(2f).graphicsLayer { translationX = scrollState.value.toFloat() })

                players.forEachIndexed { playerIndex, player ->
                    val isPlayerDragging = draggingPlayerIndex == playerIndex
                    val allRoundsFull = player.roundScores.all { rs -> rs.all { it != null } }
                    val totalScore = player.roundScores.flatten().filterNotNull().sum()
                    val totalColor = if (allRoundsFull) getScoreColor(totalScore, selectedSystem, player.color, rounds = numRounds) else Color.White
                    val footerBgColor = Color(0xFFE0E0E0)
                    
                    Column(modifier = Modifier.width(playerColumnWidth).fillMaxHeight().zIndex(if (isPlayerDragging) 10f else 1f).graphicsLayer { translationX = if (isPlayerDragging) dragOffsetX else 0f; alpha = if (isPlayerDragging) 0.8f else 1f }) {
                        if (numRounds == 1) {
                            Box(modifier = Modifier.fillMaxSize().background(footerBgColor).background(player.color.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = totalScore, 
                                    label = "totalScoreAnim",
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    }
                                ) { targetScore ->
                                    Text(text = targetScore.toString(), style = shadowStyle.copy(fontWeight = FontWeight.Bold, color = totalColor, fontSize = (rowHeight.value * 0.55f).sp), softWrap = false)
                                }
                            }
                        } else {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                player.roundScores.forEachIndexed { roundIndex, scores ->
                                    val isLastRound = roundIndex == numRounds - 1
                                    val weight = if (numRounds > 1) (if (isLastRound) 1f else 1f / (numRounds - 1)) else 1f
                                    val roundTotal = scores.filterNotNull().sum()
                                    val isRoundFull = scores.all { it != null }
                                    val textColor = if (isRoundFull) getScoreColor(roundTotal, selectedSystem, player.color) else if (roundTotal > 0) Color.White else Color.Transparent
                                    Box(modifier = Modifier.weight(weight).fillMaxHeight().background(footerBgColor).background(player.color.copy(alpha = 0.4f)).then(if (!isLastRound) Modifier.background(Color.Black.copy(alpha = 0.15f)) else Modifier).border(0.5.dp, Color.LightGray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        AnimatedContent(
                                            targetState = roundTotal, 
                                            label = "roundTotalAnim",
                                            transitionSpec = {
                                                if (targetState > initialState) {
                                                    (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                                } else {
                                                    (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                                                }.using(SizeTransform(clip = false))
                                            }
                                        ) { targetScore ->
                                            Text(text = targetScore.toString(), style = shadowStyle.copy(fontWeight = FontWeight.Bold, color = if (textColor == Color.Transparent) Color.Black else textColor, fontSize = if (isLastRound) (rowHeight.value * 0.37f).sp else (rowHeight.value * 0.27f).sp), softWrap = false)
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(footerBgColor).background(player.color.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = totalScore, 
                                    label = "totalScoreAnim",
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                                        }.using(SizeTransform(clip = false))
                                    }
                                ) { targetScore ->
                                    Text(text = targetScore.toString(), style = shadowStyle.copy(fontWeight = FontWeight.Bold, color = totalColor, fontSize = (rowHeight.value * 0.37f).sp), softWrap = false)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(2.adaptiveDp()))
                }
                Box(modifier = Modifier.width(stickyColumnWidth).fillMaxHeight().background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(bottomEnd = 15.adaptiveDp())).clip(RoundedCornerShape(bottomEnd = 15.adaptiveDp())))
            }
        }
    }

    // Bestätigungs-Dialog für das Löschen einer Runde
    val currentRoundToDelete = roundToDeleteState.value
    if (currentRoundToDelete != null) {
        AlertDialog(
            onDismissRequest = { roundToDeleteState.value = null },
            title = { Text("Runde löschen?", style = shadowStyle.copy(fontWeight = FontWeight.Bold, color = Color.Black)) },
            text = { Text("Möchtest du Runde ${currentRoundToDelete + 1} wirklich löschen? Alle eingetragenen Punkte dieser Runde gehen verloren.", style = shadowStyle.copy(color = Color.Black)) },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        onRemoveRound(currentRoundToDelete)
                        roundToDeleteState.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Löschen", color = Color.White, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { roundToDeleteState.value = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Abbrechen", color = Color.Black, style = shadowStyle.copy(color = Color.Black))
                }
            },
            containerColor = Color.White
        )
    }
}
