package de.bgsc.minigolf

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    onSaveFinished: () -> Unit,
    fullScreenEnabled: Boolean
) {
    val notes = viewModel.tournamentNotes
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    var hasChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    var expanded by remember { mutableStateOf(false) }
    val galleryHoleIndex = remember { mutableStateOf<Int?>(null) }
    val drawingHoleInfo = remember { mutableStateOf<Pair<Int, Int>?>(null) } // holeIndex to imageIndex
    val croppingHoleInfo = remember { mutableStateOf<Pair<Int, Uri>?>(null) }

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    val handleBack = {
        if (hasChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    if (showExitDialog) {
        val buttonShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Änderungen speichern?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
            text = { Text("Du hast ungespeicherte Änderungen. Möchtest du diese vor dem Verlassen speichern?", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        viewModel.saveTournamentNotes()
                        Toast.makeText(context, "Notizen gespeichert", Toast.LENGTH_SHORT).show()
                        showExitDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Speichern", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { 
                        showExitDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Verwerfen", color = Color.White, style = shadowStyle.copy(color = Color.White))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } } // Klicks abfangen!
            .imePadding()
            .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.adaptiveDp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = golfClick { handleBack() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Text(
                        if (viewModel.currentTournamentNoteId == null) "Notiz erstellen" else "Notiz bearbeiten",
                        fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                }
                IconButton(onClick = golfClick {
                    viewModel.saveTournamentNotes()
                    Toast.makeText(context, "Notizen gespeichert", Toast.LENGTH_SHORT).show()
                    hasChanges = false
                    onSaveFinished()
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Speichern", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.adaptiveDp())) {
                OutlinedTextField(
                    value = viewModel.tournamentLocation, onValueChange = { viewModel.tournamentLocation = it; hasChanges = true },
                    label = { Text("Ort", style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                Spacer(modifier = Modifier.height(8.adaptiveDp()))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = viewModel.tournamentGameMode, onValueChange = {}, readOnly = true,
                        label = { Text("Anlagentyp", style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        textStyle = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Miniaturgolf (Eternit)", "Minigolf (Beton)", "Filzgolf", "Cobigolf", "Sterngolf").forEach { system -> DropdownMenuItem(text = { Text(text = system) }, onClick = { viewModel.tournamentGameMode = system; expanded = false; hasChanges = true }) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.adaptiveDp()))

            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.adaptiveDp())) {
                notes.forEachIndexed { index, note ->
                    key(index) {
                        TournamentRow(
                            holeNumber = index + 1, note = note,
                            onUpdate = { b, s, n, imgs -> 
                                viewModel.updateTournamentNote(index, b, s, n, imgs)
                                hasChanges = true
                            },
                            onImageSelected = { uri -> croppingHoleInfo.value = index to uri },
                            onGalleryRequest = { galleryHoleIndex.value = index },
                            shadowStyle = shadowStyle
                        )
                        Spacer(Modifier.height(8.adaptiveDp()))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.adaptiveDp()))
                    }
                }
            }
        }
    }

    // Galerie Screen
    galleryHoleIndex.value?.let { index ->
        TournamentThemeWrapper(theme = viewModel.tournamentTheme) {
            val note = notes[index]
            HoleGalleryScreen(
                holeNumber = index + 1,
                images = note.getAllImages(),
                fullScreenEnabled = fullScreenEnabled,
                onDismiss = { galleryHoleIndex.value = null },
                onAddImage = { uri -> croppingHoleInfo.value = index to uri },
                onEditImage = { imgIndex -> drawingHoleInfo.value = index to imgIndex },
                onDeleteImage = { imgIndex ->
                    val currentImages = note.getAllImages().toMutableList()
                    if (imgIndex in currentImages.indices) {
                        currentImages.removeAt(imgIndex)
                        viewModel.updateTournamentNote(index, note.ball, note.startPoint, note.notes, currentImages)
                        hasChanges = true
                    }
                },
                onMoveImage = { from, to ->
                    val currentImages = note.getAllImages().toMutableList()
                    if (from in currentImages.indices && to in currentImages.indices) {
                        val img = currentImages.removeAt(from)
                        currentImages.add(to, img)
                        viewModel.updateTournamentNote(index, note.ball, note.startPoint, note.notes, currentImages)
                        hasChanges = true
                    }
                }
            )
        }
    }

    // Zeichnen Screen
    drawingHoleInfo.value?.let { (holeIdx, imgIdx) ->
        TournamentThemeWrapper(theme = viewModel.tournamentTheme) {
            val note = notes[holeIdx]
            val images = note.getAllImages()
            if (imgIdx in images.indices) {
                DrawingScreen(
                    imagePath = images[imgIdx].imagePath,
                    fullScreenEnabled = fullScreenEnabled,
                    onDismiss = { drawingHoleInfo.value = null },
                    onSave = { data ->
                        val path = viewModel.saveByteArrayToInternalStorage(data)
                        val updatedImages = images.toMutableList()
                        if (path != null) {
                            updatedImages[imgIdx] = images[imgIdx].copy(imagePath = path)
                            viewModel.updateTournamentNote(holeIdx, note.ball, note.startPoint, note.notes, updatedImages)
                            hasChanges = true
                        }
                        drawingHoleInfo.value = null
                    },
                    onResetToOriginal = {
                        val updatedImages = images.toMutableList()
                        updatedImages[imgIdx] = images[imgIdx].copy(imagePath = images[imgIdx].originalImagePath)
                        viewModel.updateTournamentNote(holeIdx, note.ball, note.startPoint, note.notes, updatedImages)
                        hasChanges = true
                        drawingHoleInfo.value = null
                        Toast.makeText(context, "Zeichnungen entfernt", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Zuschneiden Screen
    croppingHoleInfo.value?.let { (index, uri) ->
        TournamentThemeWrapper(theme = viewModel.tournamentTheme) {
            ImageCropScreen(
                uri = uri,
                fullScreenEnabled = fullScreenEnabled,
                onDismiss = { croppingHoleInfo.value = null },
                onConfirm = { croppedBitmap ->
                    val stream = ByteArrayOutputStream()
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val path = viewModel.saveByteArrayToInternalStorage(stream.toByteArray())
                    if (path != null) {
                        val note = notes[index]
                        val currentImages = note.getAllImages().toMutableList()
                        currentImages.add(HoleImage(path, path))
                        viewModel.updateTournamentNote(index, note.ball, note.startPoint, note.notes, currentImages)
                        hasChanges = true
                    }
                    croppingHoleInfo.value = null
                }
            )
        }
    }
}

@Composable
fun TournamentRow(
    holeNumber: Int,
    note: HoleNote,
    onUpdate: (String, String, String, List<HoleImage>) -> Unit,
    onImageSelected: (Uri) -> Unit,
    onGalleryRequest: () -> Unit,
    shadowStyle: TextStyle
) {
    val context = LocalContext.current
    val showImageSourceDialog = remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onImageSelected(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val imageDir = File(context.cacheDir, "images")
            val photoFile = imageDir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull { it.name.startsWith("temp_camera") }
            photoFile?.let { onImageSelected(Uri.fromFile(it)) }
        }
    }

    if (showImageSourceDialog.value) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog.value = false },
            title = { Text("Bildquelle wählen", style = shadowStyle.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Möchtest du ein Foto aufnehmen oder ein Bild aus der Galerie wählen?", style = shadowStyle) },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog.value = false
                    try {
                        val imageDir = File(context.cacheDir, "images")
                        if (!imageDir.exists()) imageDir.mkdirs()
                        val photoFile = File(imageDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                        val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                        cameraLauncher.launch(photoUri)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Fehler beim Starten der Kamera", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kamera", style = shadowStyle)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImageSourceDialog.value = false
                    galleryLauncher.launch("image/*") 
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Galerie", style = shadowStyle)
                    }
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = holeNumber.toString(), modifier = Modifier.width(28.adaptiveDp()).padding(top = 8.adaptiveDp()),
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.adaptiveSp(), style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.adaptiveDp())) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    TournamentTextField(value = note.ball, onValueChange = { onUpdate(it, note.startPoint, note.notes, note.getAllImages()) }, placeholder = "Ball", modifier = Modifier.fillMaxWidth(), shadowStyle = shadowStyle)
                    Spacer(Modifier.height(4.adaptiveDp()))
                    TournamentTextField(value = note.startPoint, onValueChange = { onUpdate(note.ball, it, note.notes, note.getAllImages()) }, placeholder = "Abschlag", modifier = Modifier.fillMaxWidth(), shadowStyle = shadowStyle)
                }
                Spacer(Modifier.width(8.adaptiveDp()))

                val allImages = note.getAllImages()
                Box(
                    modifier = Modifier
                        .size(50.adaptiveDp())
                        .clip(RoundedCornerShape(8.adaptiveDp()))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { if (allImages.isEmpty()) showImageSourceDialog.value = true else onGalleryRequest() },
                    contentAlignment = Alignment.Center
                ) {
                    if (allImages.isNotEmpty()) {
                        AsyncImage(model = allImages.first().imagePath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomEnd) {
                            Icon(imageVector = Icons.Default.Brush, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.adaptiveDp()).padding(2.adaptiveDp()))
                        }
                    } else {
                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.adaptiveDp()), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.adaptiveDp()))
            TournamentTextField(value = note.notes, onValueChange = { onUpdate(note.ball, note.startPoint, it, note.getAllImages()) }, placeholder = "Notizen...", modifier = Modifier.fillMaxWidth(), shadowStyle = shadowStyle)
        }
    }
}

@Composable
fun TournamentTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier, shadowStyle: TextStyle) {
    // Lokaler State für sofortiges Feedback beim Tippen
    var localText by remember(value) { mutableStateOf(value) }

    // Synchronisation mit dem ViewModel nach 500ms Inaktivität (Debounce)
    LaunchedEffect(localText) {
        if (localText != value) {
            delay(500)
            onValueChange(localText)
        }
    }

    Box(modifier = modifier
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.adaptiveDp()))
        .padding(vertical = 6.adaptiveDp(), horizontal = 8.adaptiveDp())
    ) {
        if (localText.isEmpty()) {
            Text(
                text = placeholder, 
                fontSize = 12.adaptiveSp(), 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), 
                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            )
        }
        BasicTextField(
            value = localText, 
            onValueChange = { localText = it }, 
            textStyle = shadowStyle.copy(fontSize = 13.adaptiveSp(), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurface), 
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), 
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { 
                    // Sofort-Sync beim Verlassen des Feldes
                    if (!it.isFocused && localText != value) {
                        onValueChange(localText)
                    }
                }
        )
    }
}
