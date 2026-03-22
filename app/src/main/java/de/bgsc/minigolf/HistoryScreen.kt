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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit
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
            title = { Text(stringResource(R.string.history_delete_confirm_title), color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
            text = { Text(stringResource(R.string.history_delete_confirm_text), style = shadowStyle.copy(color = Color.Black)) },
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
                    Text(stringResource(R.string.dialog_delete), color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { itemToDeleteState.value = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text(stringResource(R.string.dialog_cancel), color = Color.Black, style = shadowStyle.copy(color = Color.Black))
                }
            },
            containerColor = Color.White,
            textContentColor = Color.Black,
            titleContentColor = Color.Black
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dialog_cancel))
                                    }
                                }
                                Spacer(Modifier.width(8.adaptiveDp()))
                                Text(stringResource(R.string.history_title), fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, style = shadowStyle)
                            }
                            IconButton(onClick = golfClick { isSearchExpanded = true }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.history_search_icon_desc))
                                }
                            }
                        } else {
                            IconButton(onClick = golfClick { isSearchExpanded = false; searchQuery = "" }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.history_search_close))
                                }
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.history_search_placeholder), fontFamily = CalibriFontFamily) },
                                trailingIcon = {
                                    IconButton(onClick = golfClick { if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchExpanded = false }) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_search_clear))
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
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.adaptiveDp()).offset(2.dp, 2.dp), tint = Color.Black.copy(alpha = 0.1f))
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.adaptiveDp()), tint = Color.Gray.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(16.adaptiveDp()))
                            Text(if (searchQuery.isEmpty()) stringResource(R.string.history_empty_state) else stringResource(R.string.history_no_results), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
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
    val players = remember(result.playersJson) { Converters().toPlayerScoreList(result.playersJson) }
    val dateStr = remember(result.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date)) }
    var showDetails by remember { mutableStateOf(false) }
    val sortedPlayers = remember(players) { players.sortedBy { it.totalScore } }
    val winnerTotal = sortedPlayers.firstOrNull()?.totalScore ?: 0
    val winners = remember(sortedPlayers) { sortedPlayers.filter { it.totalScore == winnerTotal }.map { it.name } }

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
                    Text(result.system.replace("\n", " "), fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), style = shadowStyle.copy(color = Color.Black))
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
                players.forEach { player ->
                    val allRoundsFull = player.roundIsFull.isNotEmpty() && player.roundIsFull.all { it }
                    val totalColor = if (allRoundsFull || result.isFullGame) getScoreColor(player.totalScore, result.system, Color.Black, player.rounds.size) else Color.Black
                    val pktSuffix = stringResource(R.string.history_pkt_suffix)
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.adaptiveDp())) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (winners.contains(player.name)) Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.adaptiveDp()))
                                Spacer(Modifier.width(4.dp))
                                Text(player.name, color = Color(player.colorInt), fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), style = shadowStyle.copy(color = Color(player.colorInt)))
                            }
                            Text(stringResource(R.string.score_with_suffix, player.totalScore, pktSuffix), fontWeight = FontWeight.ExtraBold, color = totalColor, fontSize = 18.adaptiveSp(), style = shadowStyle.copy(color = totalColor))
                        }
                        if (player.rounds.size > 1) {
                            Row(modifier = Modifier.padding(start = 18.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.history_rounds_label), fontSize = 12.adaptiveSp(), color = Color.Gray, style = shadowStyle.copy(color = Color.Gray))
                                player.rounds.forEachIndexed { rIdx, rSum ->
                                    val isThisRoundFull = player.roundIsFull.getOrNull(rIdx) ?: result.isFullGame
                                    val rColor = if (isThisRoundFull) getScoreColor(rSum, result.system, Color.DarkGray, 1) else Color.DarkGray
                                    Text(rSum.toString(), fontSize = 12.adaptiveSp(), color = rColor, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = rColor))
                                    if (rIdx < player.rounds.size - 1) Text(stringResource(R.string.round_separator), fontSize = 12.adaptiveSp(), color = Color.Gray, fontFamily = CalibriFontFamily)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
