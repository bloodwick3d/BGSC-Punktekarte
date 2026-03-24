package de.bgsc.minigolf

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActiveGamesScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    fullScreenEnabled: Boolean
) {
    val activeGames by viewModel.activeGames.collectAsStateWithLifecycle()

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

    val filteredActiveGames = remember(activeGames, searchQuery) {
        if (searchQuery.isBlank()) activeGames
        else {
            activeGames.filter { 
                it.system.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true) ||
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it.date)).contains(searchQuery) ||
                it.playersJson.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val itemToCompleteState = remember { mutableStateOf<GameResult?>(null) }
    val currentItemToComplete = itemToCompleteState.value
    val buttonShape = RoundedCornerShape(20.dp)

    currentItemToComplete?.let { result ->
        AlertDialog(
            onDismissRequest = { itemToCompleteState.value = null },
            title = { Text("Spiel beenden?", color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
            text = { Text("Möchtest du dieses aktive Spiel beenden? Es wird danach in den beendeten Spielen angezeigt.", style = shadowStyle.copy(color = Color.Black)) },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        viewModel.completeGame(result)
                        itemToCompleteState.value = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Beenden", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { itemToCompleteState.value = null },
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
            .then(if (fullScreenEnabled) Modifier.displayCutoutPadding() else Modifier.systemBarsPadding())
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
                                Text("Aktive Spiele", fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, style = shadowStyle)
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
                    AnimatedVisibility(visible = isSearchExpanded && activeGames.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val suggestions = remember(activeGames, searchQuery) {
                            val converters = Converters()
                            val allLocations = activeGames.map { it.location }.filter { it.isNotBlank() }
                            val allPlayers = activeGames.flatMap { 
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
                if (filteredActiveGames.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.Black.copy(alpha = 0.1f), modifier = Modifier.size(64.adaptiveDp()).offset(2.dp, 2.dp))
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(64.adaptiveDp()))
                            }
                            Spacer(Modifier.height(16.adaptiveDp()))
                            Text(if (searchQuery.isEmpty()) "Keine aktiven Spiele gefunden" else "Keine Ergebnisse", color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.adaptiveDp()),
                        verticalArrangement = Arrangement.spacedBy(12.adaptiveDp())
                    ) {
                        items(filteredActiveGames, key = { it.id }) { game ->
                            SwipeableActiveGameItem(
                                game = game,
                                onCompleteRequest = { itemToCompleteState.value = game },
                                onContinueRequest = { viewModel.loadActiveGame(game) },
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
fun SwipeableActiveGameItem(
    game: GameResult,
    onCompleteRequest: () -> Unit,
    onContinueRequest: () -> Unit,
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
                onCompleteRequest()
            }
            else if (targetWhileDown == SwipeToDismissBoxValue.StartToEnd) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onContinueRequest()
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
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50) // Grün für Fortsetzen
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336) // Rot für Beenden
                    else -> Color.Transparent
                }
                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.PlayArrow else Icons.Default.Check
                Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(16.adaptiveDp())).padding(horizontal = 24.adaptiveDp()), contentAlignment = alignment) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                        Icon(icon, contentDescription = null, tint = Color.White)
                    }
                }
            },
            content = { 
                ActiveGameItem(
                    game = game, 
                    shadowStyle = shadowStyle
                ) 
            }
        )
    }
}

@Composable
fun ActiveGameItem(
    game: GameResult, 
    shadowStyle: TextStyle
) {
    val players = remember(game.playersJson) { Converters().toPlayerScoreList(game.playersJson) }
    val dateStr = remember(game.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(game.date)) }
    var showDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.adaptiveDp()),
        color = Color.White,
        shadowElevation = 4.adaptiveDp(),
        onClick = golfClick { showDetails = !showDetails }
    ) {
        Column(modifier = Modifier.padding(16.adaptiveDp())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(game.system.replace("\n", " "), fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), style = shadowStyle.copy(color = Color.Black))
                    if (game.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp))
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.adaptiveDp()))
                            }
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(game.location, fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
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
            }
            
            if (showDetails) {
                Spacer(Modifier.height(12.adaptiveDp()))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                players.forEach { player ->
                    val allRoundsFull = player.roundIsFull.isNotEmpty() && player.roundIsFull.all { it }
                    val totalScore = player.totalScore
                    val totalColor = if (allRoundsFull) getScoreColor(totalScore, game.system, Color.Black, player.rounds.size) else Color.Black
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.adaptiveDp())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(player.name, color = Color(player.colorInt), fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), style = shadowStyle.copy(color = Color(player.colorInt)))
                            Text("$totalScore Pkt.", fontWeight = FontWeight.ExtraBold, color = totalColor, fontSize = 18.adaptiveSp(), style = shadowStyle.copy(color = totalColor))
                        }
                        if (player.rounds.isNotEmpty()) {
                            Row(modifier = Modifier.padding(start = 0.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
                                Text("Runden: ", fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                                player.rounds.forEachIndexed { rIdx, rSum ->
                                    val isThisRoundFull = player.roundIsFull.getOrNull(rIdx) ?: false
                                    val rColor = if (isThisRoundFull) getScoreColor(rSum, game.system, Color.DarkGray, 1) else Color.DarkGray
                                    Text(rSum.toString(), fontSize = 12.adaptiveSp(), color = rColor, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = rColor))
                                    if (rIdx < player.rounds.size - 1) Text(" | ", fontSize = 12.adaptiveSp(), color = Color.Gray, fontFamily = CalibriFontFamily)
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.adaptiveDp()))
                Row {
                    players.take(3).forEachIndexed { index, player ->
                        Text(
                            player.name, 
                            color = Color(player.colorInt), 
                            fontSize = 11.adaptiveSp(), 
                            fontWeight = FontWeight.Bold,
                            style = shadowStyle.copy(color = Color(player.colorInt))
                        )
                        if (index < players.take(3).size - 1) Text(", ", fontSize = 11.adaptiveSp(), color = Color.Gray)
                    }
                    if (players.size > 3) Text(" ...", fontSize = 11.adaptiveSp(), color = Color.Gray)
                }
            }
        }
    }
}
