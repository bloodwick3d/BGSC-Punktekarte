package de.bgsc.minigolf

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    fullScreenEnabled: Boolean
) {
    val history by viewModel.gameHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && searchQuery.isEmpty() && isSearchExpanded) {
            isSearchExpanded = false
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) focusRequester.requestFocus()
    }

    val filteredHistory = remember(history, searchQuery) {
        if (searchQuery.isBlank()) history
        else {
            history.filter { 
                it.system.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true) ||
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it.date)).contains(searchQuery) ||
                it.playersJson.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val itemToDeleteState = remember { mutableStateOf<GameResult?>(null) }
    val currentItemToDelete = itemToDeleteState.value
    val buttonShape = RoundedCornerShape(20.dp)

    currentItemToDelete?.let { result ->
        AlertDialog(
            onDismissRequest = { itemToDeleteState.value = null },
            title = { Text("Ergebnis löschen?", color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
            text = { Text("Möchtest du dieses Spielergebnis wirklich unwiderruflich löschen?", style = shadowStyle.copy(color = Color.Black)) },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        viewModel.deleteHistoryEntry(result.id)
                        itemToDeleteState.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Löschen", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { itemToDeleteState.value = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Abbrechen", color = Color.Black, style = shadowStyle.copy(color = Color.Black))
                }
            },
            containerColor = Color.White,
            textContentColor = Color.Black,
            titleContentColor = Color.Black
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } } // Klicks abfangen!
            .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, shadowElevation = 4.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.adaptiveDp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!isSearchExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = golfClick { onBack() }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                                    }
                                }
                                Spacer(Modifier.width(8.adaptiveDp()))
                                Text("Beendete Spiele", fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, style = shadowStyle)
                            }
                            IconButton(onClick = golfClick { isSearchExpanded = true }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                                }
                            }
                        } else {
                            IconButton(onClick = golfClick { isSearchExpanded = false; searchQuery = "" }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen")
                                }
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                placeholder = { Text("Suchen...", fontFamily = CalibriFontFamily) },
                                trailingIcon = {
                                    IconButton(onClick = golfClick { if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchExpanded = false }) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                            Icon(Icons.Default.Close, contentDescription = "Suche beenden")
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = shadowStyle,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = RoundedCornerShape(12.adaptiveDp()),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Gray)
                            )
                        }
                    }
                    AnimatedVisibility(visible = isSearchExpanded && history.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val suggestions = remember(history, searchQuery) {
                            val converters = Converters()
                            val allLocations = history.map { it.location }.filter { it.isNotBlank() }
                            val allPlayers = history.flatMap { 
                                try { converters.toPlayerScoreList(it.playersJson).map { p -> p.name } } 
                                catch (_: Exception) { emptyList() }
                            }.filter { it.isNotBlank() }
                            
                            (allLocations + allPlayers)
                                .distinct()
                                .filter { it.contains(searchQuery, ignoreCase = true) && it.equals(searchQuery, ignoreCase = true).not() }
                                .take(8)
                        }

                        if (suggestions.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.adaptiveDp()),
                                contentPadding = PaddingValues(horizontal = 16.adaptiveDp()),
                                horizontalArrangement = Arrangement.spacedBy(8.adaptiveDp())
                            ) {
                                items(suggestions) { suggestion ->
                                    GolfSuggestionChip(text = suggestion, onClick = { searchQuery = suggestion })
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).imePadding()) {
                if (filteredHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Black.copy(alpha = 0.1f), modifier = Modifier.size(64.adaptiveDp()).offset(2.dp, 2.dp))
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(64.adaptiveDp()))
                            }
                            Spacer(Modifier.height(16.adaptiveDp()))
                            Text(if (searchQuery.isEmpty()) "Noch keine Spiele gespeichert" else "Keine Ergebnisse", color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.adaptiveDp()), verticalArrangement = Arrangement.spacedBy(12.adaptiveDp())) {
                        items(filteredHistory, key = { it.id }) { result ->
                            SwipeableHistoryItem(
                                result = result,
                                onDeleteRequest = { itemToDeleteState.value = result },
                                onShareRequest = { 
                                    shareGameResult(context, result)
                                },
                                shadowStyle = shadowStyle
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableHistoryItem(
    result: GameResult,
    onDeleteRequest: () -> Unit,
    onShareRequest: () -> Unit,
    shadowStyle: TextStyle
) {
    val haptic = LocalHapticFeedback.current
    val sound = LocalSoundFeedback.current
    var isFingerDown by remember { mutableStateOf(false) }
    var targetWhileDown by remember { mutableStateOf(SwipeToDismissBoxValue.Settled) }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.5f })

    LaunchedEffect(dismissState.targetValue, isFingerDown) { if (isFingerDown) targetWhileDown = dismissState.targetValue }
    LaunchedEffect(isFingerDown) {
        if (!isFingerDown) {
            if (targetWhileDown == SwipeToDismissBoxValue.EndToStart) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDeleteRequest()
            }
            else if (targetWhileDown == SwipeToDismissBoxValue.StartToEnd) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onShareRequest()
            }
            targetWhileDown = SwipeToDismissBoxValue.Settled
            dismissState.reset()
        }
    }

    Box(modifier = Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { isFingerDown = awaitPointerEvent().changes.any { it.pressed } } } }) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2196F3)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                    else -> Color.Transparent
                }
                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Share else Icons.Default.Delete
                Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(16.adaptiveDp())).padding(horizontal = 24.adaptiveDp()), contentAlignment = alignment) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                        Icon(icon, contentDescription = null, tint = Color.White)
                    }
                }
            },
            content = { HistoryItem(result = result, shadowStyle = shadowStyle) }
        )
    }
}

@Composable
fun HistoryItem(result: GameResult, shadowStyle: TextStyle) {
    val context = LocalContext.current
    val players = remember(result.playersJson) { Converters().toPlayerScoreList(result.playersJson) }
    val dateStr = remember(result.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date)) }
    var showDetails by remember { mutableStateOf(false) }
    val sortedPlayers = remember(players) { players.sortedBy { it.totalScore } }
    val winnerTotal = sortedPlayers.firstOrNull()?.totalScore ?: 0
    val winners = remember(sortedPlayers) { sortedPlayers.filter { it.totalScore == winnerTotal }.map { it.name } }

    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statsBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(false) }
    var showFullscreenIndex by remember { mutableStateOf<Int?>(null) } // null = zu, 0 = Score, 1 = Stats

    LaunchedEffect(showDetails) {
        if (showDetails && resultBitmap == null) {
            isLoadingBitmap = true
            withContext(Dispatchers.IO) {
                resultBitmap = generateResultBitmap(context, result)
                if (result.hasStats) {
                    statsBitmap = generateTrackStatsBitmap(context, result)
                }
            }
            isLoadingBitmap = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.adaptiveDp()),
        color = Color.White,
        shadowElevation = 4.adaptiveDp(),
        onClick = golfClick { showDetails = !showDetails }
    ) {
        Column(modifier = Modifier.padding(16.adaptiveDp())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (result.isFullGame) Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = null,
                            tint = if (result.isFullGame) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(16.adaptiveDp())
                        )
                        Spacer(Modifier.width(6.adaptiveDp()))
                        Text(
                            text = result.system.replace("\n", " "), 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.adaptiveSp(), 
                            style = shadowStyle.copy(color = Color.Black)
                        )
                        if (result.hasStats) {
                            Spacer(Modifier.width(6.adaptiveDp()))
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Statistik aktiv",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.adaptiveDp())
                            )
                        }
                    }
                    if (result.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp))
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.adaptiveDp()))
                            }
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(result.location, fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp))
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.adaptiveDp()))
                        }
                        Spacer(Modifier.width(4.adaptiveDp()))
                        Text(dateStr, fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                    }
                }
                if (!showDetails && winners.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.adaptiveDp()))
                        Spacer(Modifier.width(4.dp))
                        Text(winners.joinToString(", "), fontSize = 12.adaptiveSp(), fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.Black))
                    }
                }
            }
            if (showDetails) {
                Spacer(Modifier.height(12.adaptiveDp()))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                sortedPlayers.forEach { player ->
                    val totalScore = player.totalScore
                    
                    // Live-Farbe für das Gesamtergebnis
                    val totalPlayed = player.holeScores.sumOf { round -> round.count { it != null && it > 0 } }
                    val totalColor = getScoreColor(
                        total = totalScore, 
                        system = result.system, 
                        defaultColor = Color.White, 
                        rounds = player.rounds.size, 
                        playedHoles = totalPlayed
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.adaptiveDp())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (winners.contains(player.name)) Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.adaptiveDp()))
                                Spacer(Modifier.width(4.dp))
                                Text(player.name, color = Color(player.colorInt), fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), style = shadowStyle.copy(color = Color(player.colorInt)))
                            }
                            Text(
                                text = "$totalScore Pkt.", 
                                fontWeight = FontWeight.ExtraBold, 
                                color = totalColor, 
                                fontSize = 18.adaptiveSp(), 
                                style = shadowStyle.copy(color = totalColor)
                            )
                        }
                        if (player.rounds.size > 1) {
                            Row(modifier = Modifier.padding(start = 18.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
                                Text("Runden: ", fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                                player.rounds.forEachIndexed { rIdx, rSum ->
                                    val playedInRound = player.holeScores.getOrNull(rIdx)?.count { it != null && it > 0 } ?: 0
                                    val rColor = getScoreColor(
                                        total = rSum, 
                                        system = result.system, 
                                        defaultColor = Color.White, 
                                        rounds = 1, 
                                        playedHoles = playedInRound
                                    )

                                    Text(
                                        text = rSum.toString(), 
                                        fontSize = 12.adaptiveSp(), 
                                        color = rColor, 
                                        fontWeight = FontWeight.Bold, 
                                        style = shadowStyle.copy(color = rColor)
                                    )
                                    if (rIdx < player.rounds.size - 1) Text(" | ", fontSize = 12.adaptiveSp(), color = Color.Gray, fontFamily = CalibriFontFamily)
                                }
                            }
                        }
                    }
                }

                if (isLoadingBitmap) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.adaptiveDp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.adaptiveDp()), strokeWidth = 2.dp, color = Color.Gray)
                    }
                }

                resultBitmap?.let { bmp ->
                    Spacer(Modifier.height(16.adaptiveDp()))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.adaptiveDp()))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.adaptiveDp()),
                        horizontalArrangement = Arrangement.spacedBy(8.adaptiveDp())
                    ) {
                        // Vorschau Ergebniskarte
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.adaptiveDp()))
                                .background(Color.Black.copy(alpha = 0.05f))
                                .pointerInput(Unit) {
                                    detectTapGestures { showFullscreenIndex = 0 }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Ergebniskarte",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(24.adaptiveDp()))
                            }
                        }

                        // Vorschau Bahnstatistik (falls vorhanden)
                        if (result.hasStats && statsBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.adaptiveDp()))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .pointerInput(Unit) {
                                        detectTapGestures { showFullscreenIndex = 1 }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = statsBitmap!!.asImageBitmap(),
                                    contentDescription = "Bahnstatistik",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.BarChart, null, tint = Color.White, modifier = Modifier.size(24.adaptiveDp()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenIndex != null && resultBitmap != null) {
        val bitmaps = remember(result.hasStats, resultBitmap, statsBitmap) {
            if (result.hasStats && statsBitmap != null) listOf(resultBitmap!!, statsBitmap!!) else listOf(resultBitmap!!)
        }
        val pagerState = rememberPagerState(initialPage = showFullscreenIndex!!.coerceIn(0, bitmaps.size - 1), pageCount = { bitmaps.size })
        var isAnyImageZoomed by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { showFullscreenIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isAnyImageZoomed,
                    beyondViewportPageCount = 1
                ) { page ->
                    FullscreenImageItem(
                        bitmap = bitmaps[page], 
                        onDismiss = { showFullscreenIndex = null }, 
                        context = context,
                        onZoomChanged = { isAnyImageZoomed = it }
                    )
                }

                // Pager Indicator (Punkte unten)
                if (bitmaps.size > 1) {
                    Row(
                        Modifier.height(50.dp).fillMaxWidth().align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(bitmaps.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                            Box(modifier = Modifier.padding(4.dp).clip(CircleShape).background(color).size(8.dp))
                        }
                    }
                }

                // Schließen-Button oben rechts
                IconButton(
                    onClick = { showFullscreenIndex = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.adaptiveDp())
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun FullscreenImageItem(
    bitmap: Bitmap, 
    onDismiss: () -> Unit, 
    context: android.content.Context,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { _, zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        onZoomChanged(newScale > 1.05f)
        if (newScale > 1f) {
            offset += offsetChange * scale
        } else {
            offset = Offset.Zero
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Berechnung der Grenzen für das Verschieben (Panning)
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        
        val scaleFit = min(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
        val displayWidth = bitmapWidth * scaleFit
        val displayHeight = bitmapHeight * scaleFit

        // Effektive Grenzen für den Offset berechnen
        val maxX = max(0f, (displayWidth * scale - containerWidth) / 2f)
        val maxY = max(0f, (displayHeight * scale - containerHeight) / 2f)
        
        val boundedOffset = Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY)
        )

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = boundedOffset.x
                    translationY = boundedOffset.y
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                                onZoomChanged(false)
                            } else {
                                scale = 2.5f
                                onZoomChanged(true)
                            }
                        }
                    )
                }
                .then(if (scale > 1f) Modifier.transformable(state = state) else Modifier),
            contentScale = ContentScale.Fit
        )

        // Speichern-Button oben links
        IconButton(
            onClick = { saveBitmapToGallery(context, bitmap) },
            modifier = Modifier.align(Alignment.TopStart).padding(16.adaptiveDp())
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = "In Galerie speichern", tint = Color.White)
        }
    }
}
