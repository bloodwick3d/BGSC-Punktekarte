package de.bgsc.minigolf

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentHistoryScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    onEdit: (TournamentNoteResult) -> Unit,
    fullScreenEnabled: Boolean
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
            offset = Offset(2f, 2f), blurRadius = 3f
        )
    )

    // Automatisch Suche beenden
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
            title = { Text("Notiz löschen?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
            text = { 
                val locationName = result.location.ifBlank { "diesen Ort" }
                Text("Möchtest du die Notizen für \"$locationName\" wirklich unwiderruflich löschen?", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
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
                    Text("Löschen", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
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
                    Text("Abbrechen", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
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
            .pointerInput(Unit) { detectTapGestures { } } // Klicks abfangen!
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && selectedResultForDetails != null) Modifier.blur(15.dp) else Modifier)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                                Spacer(Modifier.width(8.adaptiveDp()))
                                Text("Gespeicherte Notizen", fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground))
                            }
                            IconButton(onClick = golfClick { isSearchExpanded = true }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Suchen", tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        } else {
                            IconButton(onClick = golfClick { isSearchExpanded = false; searchQuery = "" }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            OutlinedTextField(
                                value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                placeholder = { Text("Suchen...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))) },
                                trailingIcon = { IconButton(onClick = golfClick { if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchExpanded = false }) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp)); Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onBackground) } } },
                                singleLine = true, textStyle = shadowStyle, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = RoundedCornerShape(12.adaptiveDp()),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchExpanded && history.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val suggestions = remember(history, searchQuery) {
                            val allLocations = history.map { it.location }.filter { it.isNotBlank() }
                            val allSystems = history.map { it.system }.filter { it.isNotBlank() }
                            
                            (allLocations + allSystems)
                                .distinct()
                                .filter { it.contains(searchQuery, ignoreCase = true) && !it.equals(searchQuery, ignoreCase = true) }
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = if (searchQuery.isEmpty()) Icons.Default.History else Icons.Default.Search, null, modifier = Modifier.size(64.adaptiveDp()).offset(2.dp, 2.dp), tint = Color.Black.copy(alpha = 0.1f))
                                Icon(imageVector = if (searchQuery.isEmpty()) Icons.Default.History else Icons.Default.Search, null, modifier = Modifier.size(64.adaptiveDp()), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
                            }
                            Spacer(Modifier.height(16.adaptiveDp()))
                            Text(text = if (searchQuery.isEmpty()) "Noch keine Notizen gespeichert" else "Keine passenden Notizen gefunden", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.adaptiveDp()), verticalArrangement = Arrangement.spacedBy(12.adaptiveDp())) {
                        items(filteredHistory, key = { it.id }) { result ->
                            SwipeableTournamentItem(result = result, onDeleteRequest = { itemToDeleteState.value = result }, onEdit = { onEdit(result) }, onShowDetails = { selectedResultForDetailsState.value = result }, shadowStyle = shadowStyle)
                        }
                    }
                }
            }
        }

        // Details-Screen Overlay
        AnimatedVisibility(
            visible = selectedResultForDetails != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            selectedResultForDetails?.let { result ->
                TournamentDetailsView(
                    result = result,
                    shadowStyle = shadowStyle,
                    onDismiss = { selectedResultForDetailsState.value = null },
                    fullScreenEnabled = fullScreenEnabled
                )
            }
        }
    }
}

@Composable
fun TournamentDetailsView(
    result: TournamentNoteResult,
    shadowStyle: TextStyle,
    onDismiss: () -> Unit,
    fullScreenEnabled: Boolean
) {
    val context = LocalContext.current
    val notes = remember(result.notesJson) {
        val listType = object : TypeToken<List<HoleNote>>() {}.type
        Gson().fromJson<List<HoleNote>>(result.notesJson, listType) ?: emptyList()
    }
    val dateStr = remember(result.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date)) }
    val scrollState = rememberScrollState()
    
    // Preview States
    var previewImages by remember { mutableStateOf<List<HoleImage>?>(null) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }

    val isPreviewActive = previewImages != null

    BackHandler { 
        if (isPreviewActive) previewImages = null else onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }, // Klicks abfangen!
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPreviewActive) Modifier.blur(15.dp) else Modifier)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = golfClick { onDismiss() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, null, modifier = Modifier.size(16.adaptiveDp()), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(text = result.location.ifBlank { "Unbekannter Ort" }, fontSize = 18.adaptiveSp(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, style = shadowStyle)
                        }
                        Text(text = result.system, fontSize = 12.adaptiveSp(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = shadowStyle, modifier = Modifier.padding(start = 24.adaptiveDp()))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.adaptiveDp())) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(12.adaptiveDp()), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            Spacer(Modifier.width(4.adaptiveDp()))
                            Text(text = dateStr, fontSize = 12.adaptiveSp(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = shadowStyle)
                        }
                    }
                    
                    // Share Button
                    IconButton(onClick = golfClick { shareTournamentNote(context, result) }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, null, tint = Color.Black.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                            Icon(imageVector = Icons.Default.Share, null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.adaptiveDp())) {
                    notes.forEachIndexed { index, note ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.adaptiveDp()), verticalAlignment = Alignment.Top) {
                            Text(text = (index + 1).toString(), modifier = Modifier.width(28.adaptiveDp()).padding(top = 8.adaptiveDp()), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.adaptiveSp(), color = MaterialTheme.colorScheme.onBackground, style = shadowStyle)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.adaptiveDp())) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        ReadOnlyTournamentBox(text = note.ball, placeholder = "Ball", shadowStyle = shadowStyle)
                                        Spacer(Modifier.height(4.adaptiveDp()))
                                        ReadOnlyTournamentBox(text = note.startPoint, placeholder = "Abschlag", shadowStyle = shadowStyle)
                                    }
                                    Spacer(Modifier.width(8.adaptiveDp()))
                                    val allImages = note.getAllImages()
                                    Box(modifier = Modifier.size(50.adaptiveDp()).clip(RoundedCornerShape(8.adaptiveDp())).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = allImages.isNotEmpty()) { 
                                        if (allImages.isNotEmpty()) {
                                            previewImages = allImages
                                            previewInitialIndex = 0
                                        } 
                                    }, contentAlignment = Alignment.Center) {
                                        if (allImages.isNotEmpty()) AsyncImage(model = allImages.first().imagePath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(16.adaptiveDp()), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                    }
                                }
                                Spacer(Modifier.height(4.adaptiveDp()))
                                ReadOnlyTournamentBox(text = note.notes, placeholder = "Notizen...", shadowStyle = shadowStyle)
                            }
                        }
                        Spacer(Modifier.height(8.adaptiveDp()))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.adaptiveDp()))
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }

            // Image Preview Overlay
            AnimatedVisibility(visible = isPreviewActive, enter = fadeIn(), exit = fadeOut()) {
                previewImages?.let { images ->
                    ImagePreviewOverlay(
                        images = images,
                        initialIndex = previewInitialIndex,
                        onDismiss = { previewImages = null },
                        fullScreenEnabled = fullScreenEnabled,
                        shadowStyle = shadowStyle
                    )
                }
            }
        }
    }
}

@Composable
fun ImagePreviewOverlay(
    images: List<HoleImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    fullScreenEnabled: Boolean,
    shadowStyle: TextStyle
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale > 1f) {
            val maxX = (boxSize.width * (newScale - 1f)) / 2f
            val maxY = (boxSize.height * (newScale - 1f)) / 2f
            offset = Offset((offset.x + offsetChange.x).coerceIn(-maxX, maxX), (offset.y + offsetChange.y).coerceIn(-maxY, maxY))
        } else offset = Offset.Zero
        scale = newScale
    }

    // Zoom zurücksetzen wenn das Bild gewechselt wird
    LaunchedEffect(currentIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Image
        AsyncImage(
            model = images[currentIndex].imagePath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { onDismiss() }
                    )
                }
        )

        // Close Button
        IconButton(
            onClick = { onDismiss() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .then(if (!fullScreenEnabled) Modifier.statusBarsPadding() else Modifier)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onSurface)
        }

        // Navigation and Counter
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .then(if (!fullScreenEnabled) Modifier.navigationBarsPadding() else Modifier)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = { 
                    currentIndex = if (currentIndex > 0) currentIndex - 1 else images.size - 1
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                
                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface, shadow = null)
                )

                IconButton(onClick = { 
                    currentIndex = if (currentIndex < images.size - 1) currentIndex + 1 else 0
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun ReadOnlyTournamentBox(text: String, placeholder: String, shadowStyle: TextStyle) {
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.adaptiveDp())).padding(vertical = 6.adaptiveDp(), horizontal = 8.adaptiveDp()), contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) Text(text = placeholder, fontSize = 12.adaptiveSp(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
        else Text(text = text, fontSize = 13.adaptiveSp(), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface), softWrap = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTournamentItem(result: TournamentNoteResult, onDeleteRequest: () -> Unit, onEdit: () -> Unit, onShowDetails: () -> Unit, shadowStyle: TextStyle) {
    val haptic = LocalHapticFeedback.current
    val sound = LocalSoundFeedback.current
    var isFingerDown by remember { mutableStateOf(false) }
    var targetWhileDown by remember { mutableStateOf(SwipeToDismissBoxValue.Settled) }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.5f })
    LaunchedEffect(dismissState.targetValue, isFingerDown) { if (isFingerDown) targetWhileDown = dismissState.targetValue }
    LaunchedEffect(isFingerDown) {
        if (!isFingerDown) {
            if (targetWhileDown == SwipeToDismissBoxValue.EndToStart) { sound.playClick(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDeleteRequest() }
            else if (targetWhileDown == SwipeToDismissBoxValue.StartToEnd) { 
                sound.playClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEdit() 
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
                val color = when (direction) { SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50); SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336); else -> Color.Transparent }
                val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Edit else Icons.Default.Delete
                Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(16.adaptiveDp())).padding(horizontal = 24.adaptiveDp()), contentAlignment = alignment) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
                    }
                }
            },
            content = { TournamentHistoryItem(result = result, shadowStyle = shadowStyle, onClick = onShowDetails) }
        )
    }
}

@Composable
fun TournamentHistoryItem(result: TournamentNoteResult, shadowStyle: TextStyle, onClick: () -> Unit) {
    val dateStr = remember(result.date) { SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.getDefault()).format(Date(result.date)) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.adaptiveDp()), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shadowElevation = 4.adaptiveDp(), onClick = golfClick { onClick() }) {
        Column(modifier = Modifier.padding(16.adaptiveDp())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Place, null, modifier = Modifier.size(16.adaptiveDp()).offset(1.dp, 1.dp), tint = Color.Black.copy(alpha = 0.5f))
                    Icon(imageVector = Icons.Default.Place, null, modifier = Modifier.size(16.adaptiveDp()), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(4.adaptiveDp()))
                Text(text = result.location.ifBlank { "Unbekannter Ort" }, fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = shadowStyle)
            }
            Text(text = result.system, fontSize = 14.adaptiveSp(), modifier = Modifier.padding(start = 24.adaptiveDp()), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = shadowStyle)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.adaptiveDp())) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.CalendarMonth, null, modifier = Modifier.size(12.adaptiveDp()).offset(1.dp, 1.dp), tint = Color.Black.copy(alpha = 0.5f))
                    Icon(imageVector = Icons.Default.CalendarMonth, null, modifier = Modifier.size(12.adaptiveDp()), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                Spacer(Modifier.width(4.adaptiveDp()))
                Text(text = dateStr, fontSize = 12.adaptiveSp(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = shadowStyle)
            }
        }
    }
}
