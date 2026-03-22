package de.bgsc.minigolf

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen that displays the history of saved tournament notes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentHistoryScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    onEdit: (TournamentNoteResult) -> Unit
) {
    val history by viewModel.tournamentHistory.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    val selectedResultForDetailsState = remember { mutableStateOf<TournamentNoteResult?>(null) }
    val selectedResultForDetails = selectedResultForDetailsState.value

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
                it.location.contains(searchQuery, ignoreCase = true) || 
                it.system.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val itemToDeleteState = remember { mutableStateOf<TournamentNoteResult?>(null) }
    val currentItemToDelete = itemToDeleteState.value
    val buttonShape = RoundedCornerShape(20.dp)

    currentItemToDelete?.let { result ->
        AlertDialog(
            onDismissRequest = { itemToDeleteState.value = null },
            title = { Text(stringResource(R.string.tournament_history_delete_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
            text = { 
                val locationName = result.location.ifBlank { stringResource(R.string.tournament_history_unknown_location) }
                Text(stringResource(R.string.tournament_history_delete_text, locationName), color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
            },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        viewModel.deleteTournamentNoteEntry(result.id)
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
                    onClick = golfClick { 
                        itemToDeleteState.value = null 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text(stringResource(R.string.dialog_cancel), color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.adaptiveDp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!isSearchExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = golfClick { onBack() }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.dialog_cancel),
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.adaptiveDp()))
                                Text(
                                    stringResource(R.string.tournament_history_title),
                                    fontSize = 20.adaptiveSp(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                                )
                            }
                            IconButton(onClick = golfClick { 
                                isSearchExpanded = true 
                            }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.history_search_icon_desc),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = golfClick { 
                                isSearchExpanded = false
                                searchQuery = ""
                            }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.history_search_close),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                placeholder = { 
                                    Text(
                                        stringResource(R.string.history_search_placeholder), 
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                    ) 
                                },
                                trailingIcon = {
                                    IconButton(onClick = golfClick { 
                                        if (searchQuery.isNotEmpty()) searchQuery = ""
                                        else {
                                            isSearchExpanded = false
                                        }
                                    }) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.history_search_clear),
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = shadowStyle,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                shape = RoundedCornerShape(12.adaptiveDp()),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val suggestions = remember(history, searchQuery) {
                            if (searchQuery.isBlank()) emptyList()
                            else {
                                history.asSequence()
                                    .flatMap { listOf(it.location, it.system) }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .filter { it.contains(searchQuery, ignoreCase = true) && !it.equals(searchQuery, ignoreCase = true) }
                                    .take(5)
                                    .toList()
                            }
                        }
                        
                        if (suggestions.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.adaptiveDp()),
                                contentPadding = PaddingValues(horizontal = 16.adaptiveDp()),
                                horizontalArrangement = Arrangement.spacedBy(8.adaptiveDp())
                            ) {
                                items(suggestions) { suggestion ->
                                    SuggestionChip(text = suggestion, onClick = { searchQuery = suggestion })
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).imePadding()) {
                if (filteredHistory.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (searchQuery.isEmpty()) Icons.Default.History else Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.adaptiveDp()).offset(2.dp, 2.dp),
                                    tint = Color.Black.copy(alpha = 0.1f)
                                )
                                Icon(
                                    imageVector = if (searchQuery.isEmpty()) Icons.Default.History else Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.adaptiveDp()),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                            }
                            Spacer(Modifier.height(16.adaptiveDp()))
                            Text(
                                text = if (searchQuery.isEmpty()) stringResource(R.string.tournament_history_empty) 
                                       else stringResource(R.string.history_no_results), 
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.adaptiveDp()),
                        verticalArrangement = Arrangement.spacedBy(12.adaptiveDp())
                    ) {
                        items(filteredHistory, key = { it.id }) { result ->
                            SwipeableTournamentItem(
                                result = result,
                                onDeleteRequest = { itemToDeleteState.value = result },
                                onEdit = { onEdit(result) },
                                onShowDetails = { selectedResultForDetailsState.value = result },
                                shadowStyle = shadowStyle
                            )
                        }
                    }
                }
            }
        }
    }
    
    selectedResultForDetails?.let { result ->
        TournamentDetailsDialog(
            result = result,
            shadowStyle = shadowStyle,
            onDismiss = { selectedResultForDetailsState.value = null }
        )
    }
}

/**
 * A dialog displaying the full details of a saved tournament note result.
 */
@Composable
fun TournamentDetailsDialog(
    result: TournamentNoteResult,
    shadowStyle: TextStyle,
    onDismiss: () -> Unit
) {
    val notes = remember(result.notesJson) { 
        val listType = object : TypeToken<List<HoleNote>>() {}.type
        Gson().fromJson<List<HoleNote>>(result.notesJson, listType) ?: emptyList()
    }
    val dateStr = remember(result.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date)) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.adaptiveDp()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = golfClick {
                        onDismiss()
                    }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.dialog_cancel),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(16.adaptiveDp()).offset(1.dp, 1.dp))
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.adaptiveDp()),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(
                                text = result.location.ifBlank { stringResource(R.string.tournament_history_unknown_location) },
                                fontSize = 18.adaptiveSp(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                            )
                        }
                        Text(
                            text = result.system,
                            fontSize = 12.adaptiveSp(),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(start = 20.adaptiveDp())
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 20.adaptiveDp())
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp))
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.adaptiveDp()),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(
                                text = dateStr,
                                fontSize = 12.adaptiveSp(),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.adaptiveDp())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.column_header_number), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text(stringResource(R.string.tournament_balls), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text(stringResource(R.string.tournament_tee_off), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text(stringResource(R.string.tournament_notes), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                    }

                    notes.forEachIndexed { index, note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                            )

                            ReadOnlyTournamentBox(text = note.ball, modifier = Modifier.weight(1f), shadowStyle = shadowStyle)
                            ReadOnlyTournamentBox(text = note.startPoint, modifier = Modifier.weight(1f), shadowStyle = shadowStyle)
                            ReadOnlyTournamentBox(text = note.notes, modifier = Modifier.weight(1.5f), shadowStyle = shadowStyle)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * A simple non-editable text box for tournament details.
 */
@Composable
fun ReadOnlyTournamentBox(
    text: String, 
    modifier: Modifier, 
    shadowStyle: TextStyle
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            softWrap = true
        )
    }
}

/**
 * A small chip used for showing search suggestions.
 */
@Composable
fun SuggestionChip(text: String, onClick: () -> Unit) {
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

/**
 * A wrapper for [TournamentHistoryItem] that adds swipe-to-dismiss behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTournamentItem(
    result: TournamentNoteResult,
    onDeleteRequest: () -> Unit,
    onEdit: () -> Unit,
    onShowDetails: () -> Unit,
    shadowStyle: TextStyle
) {
    val haptic = LocalHapticFeedback.current
    val sound = LocalSoundFeedback.current
    var isFingerDown by remember { mutableStateOf(false) }
    var targetWhileDown by remember { mutableStateOf(SwipeToDismissBoxValue.Settled) }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.5f })

    LaunchedEffect(dismissState.targetValue, isFingerDown) { 
        if (isFingerDown) targetWhileDown = dismissState.targetValue 
    }
    
    LaunchedEffect(isFingerDown) {
        if (!isFingerDown) {
            if (targetWhileDown == SwipeToDismissBoxValue.EndToStart) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDeleteRequest()
            } else if (targetWhileDown == SwipeToDismissBoxValue.StartToEnd) {
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEdit()
            }
            targetWhileDown = SwipeToDismissBoxValue.Settled
            dismissState.reset()
        }
    }

    Box(modifier = Modifier.pointerInput(Unit) { 
        awaitPointerEventScope { 
            while (true) { 
                isFingerDown = awaitPointerEvent().changes.any { it.pressed } 
            } 
        } 
    }) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                    else -> Color.Transparent
                }
                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Edit else Icons.Default.Delete
                Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(16.adaptiveDp())).padding(horizontal = 24.adaptiveDp()), contentAlignment = alignment) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon, 
                            contentDescription = null, 
                            tint = Color.Black.copy(alpha = 0.3f), 
                            modifier = Modifier.offset(1.5.dp, 1.5.dp)
                        )
                        Icon(
                            imageVector = icon, 
                            contentDescription = null, 
                            tint = Color.White
                        )
                    }
                }
            },
            content = {
                TournamentHistoryItem(
                    result = result, 
                    shadowStyle = shadowStyle,
                    onClick = onShowDetails
                )
            }
        )
    }
}

/**
 * An individual item in the tournament history list.
 */
@Composable
fun TournamentHistoryItem(
    result: TournamentNoteResult, 
    shadowStyle: TextStyle,
    onClick: () -> Unit
) {
    val dateStr = remember(result.date) {
        SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.adaptiveDp()),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 4.adaptiveDp(),
        onClick = golfClick { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.adaptiveDp())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.adaptiveDp()).offset(1.dp, 1.dp),
                        tint = Color.Black.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.adaptiveDp()),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(4.adaptiveDp()))
                Text(
                    text = result.location.ifBlank { stringResource(R.string.tournament_history_unknown_location) },
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.adaptiveSp(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
            Text(
                text = result.system, 
                fontSize = 14.adaptiveSp(), 
                modifier = Modifier.padding(start = 20.adaptiveDp()),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.adaptiveDp())
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp),
                        tint = Color.Black.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(12.adaptiveDp()),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Spacer(Modifier.width(4.adaptiveDp()))
                Text(
                    text = dateStr, 
                    fontSize = 12.adaptiveSp(), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}
