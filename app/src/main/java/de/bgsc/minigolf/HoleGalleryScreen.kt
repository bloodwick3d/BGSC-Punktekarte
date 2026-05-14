package de.bgsc.minigolf

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoleGalleryScreen(
    holeNumber: Int,
    images: List<HoleImage>,
    fullScreenEnabled: Boolean,
    onDismiss: () -> Unit,
    onAddImage: (android.net.Uri) -> Unit,
    onEditImage: (Int) -> Unit,
    onDeleteImage: (Int) -> Unit,
    onMoveImage: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    
    val previewImageIndex = remember { mutableStateOf<Int?>(null) }
    val imageToDeleteIndex = remember { mutableStateOf<Int?>(null) }
    val showImageSourceDialog = remember { mutableStateOf(false) }

    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableStateOf(Offset.Zero) }
    var potentialTargetIndex by remember { mutableStateOf<Int?>(null) }

    val spacingPx = with(density) { 12.adaptiveDp().toPx() }

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 3f)
    )

    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val moveImageCallback by rememberUpdatedState(onMoveImage)

    BackHandler { if (previewImageIndex.value != null) previewImageIndex.value = null else onDismiss() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { onAddImage(it) } }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val imageDir = File(context.cacheDir, "images")
            val photoFile = imageDir.listFiles()?.sortedByDescending { it.lastModified() }?.firstOrNull { it.name.startsWith("temp_camera") }
            photoFile?.let { onAddImage(android.net.Uri.fromFile(it)) }
        }
    }

    val buttonShape = RoundedCornerShape(20.dp)

    imageToDeleteIndex.value?.let { index ->
        AlertDialog(
            onDismissRequest = { imageToDeleteIndex.value = null },
            title = { 
                Text(
                    text = "Bild entfernen?", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontWeight = FontWeight.Bold, 
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)
                ) 
            },
            text = { 
                Text(
                    text = "Möchtest du dieses Bild wirklich unwiderruflich aus der Galerie löschen?", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)
                ) 
            },
            confirmButton = { 
                Button(
                    onClick = golfClick { 
                        onDeleteImage(index)
                        imageToDeleteIndex.value = null 
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
                        imageToDeleteIndex.value = null 
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

    if (showImageSourceDialog.value) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog.value = false },
            icon = { Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Bildquelle wählen", style = shadowStyle.copy(fontWeight = FontWeight.Bold)) },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog.value = false
                    try {
                        val imageDir = File(context.cacheDir, "images")
                        if (!imageDir.exists()) imageDir.mkdirs()
                        val photoFile = File(imageDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                        val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                        cameraLauncher.launch(photoUri)
                    } catch (_: Exception) { }
                }) { Text("Kamera", style = shadowStyle) }
            },
            dismissButton = { TextButton(onClick = { showImageSourceDialog.value = false; galleryLauncher.launch("image/*") }) { Text("Galerie", style = shadowStyle) } }
        )
    }

    Surface(modifier = Modifier.fillMaxSize().zIndex(100f), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isPreviewActive = previewImageIndex.value != null
            
            Column(modifier = Modifier.fillMaxSize().then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier).then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPreviewActive) Modifier.blur(15.dp) else Modifier)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(16.adaptiveDp()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = golfClick { onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = onBackgroundColor)
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Text("Galerie - Bahn $holeNumber", fontSize = 20.adaptiveSp(), fontWeight = FontWeight.Bold, color = onBackgroundColor, style = shadowStyle)
                }

                // Grid mit fixiertem Drag-Handler
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(images) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { 
                                        offset.x.toInt() in it.offset.x..(it.offset.x + it.size.width) &&
                                        offset.y.toInt() in it.offset.y..(it.offset.y + it.size.height)
                                    }
                                    if (item != null && item.index < images.size) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggedItemKey = images[item.index].imagePath
                                        potentialTargetIndex = item.index
                                    }
                                },
                                onDragEnd = {
                                    val fromIdx = images.indexOfFirst { it.imagePath == draggedItemKey }
                                    val toIdx = potentialTargetIndex
                                    if (fromIdx != -1 && toIdx != null && fromIdx != toIdx) {
                                        moveImageCallback(fromIdx, toIdx)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    draggedItemKey = null
                                    draggingOffset = Offset.Zero
                                    potentialTargetIndex = null
                                },
                                onDragCancel = {
                                    draggedItemKey = null
                                    draggingOffset = Offset.Zero
                                    potentialTargetIndex = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggingOffset += dragAmount

                                    val currentKey = draggedItemKey ?: return@detectDragGesturesAfterLongPress
                                    val currentIndex = images.indexOfFirst { it.imagePath == currentKey }
                                    if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                    val layoutInfo = gridState.layoutInfo
                                    val draggedItem = layoutInfo.visibleItemsInfo.find { it.index == currentIndex } ?: return@detectDragGesturesAfterLongPress

                                    val fingerPos = Offset(
                                        draggedItem.offset.x + draggedItem.size.width / 2f + draggingOffset.x,
                                        draggedItem.offset.y + draggedItem.size.height / 2f + draggingOffset.y
                                    )

                                    val target = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                        item.index < images.size &&
                                        fingerPos.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                                        fingerPos.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
                                    }
                                    
                                    if (target != null && target.index != potentialTargetIndex) {
                                        potentialTargetIndex = target.index
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            )
                        },
                    contentPadding = PaddingValues(16.adaptiveDp()),
                    horizontalArrangement = Arrangement.spacedBy(12.adaptiveDp()),
                    verticalArrangement = Arrangement.spacedBy(12.adaptiveDp())
                ) {
                    itemsIndexed(images, key = { _, image -> image.imagePath }) { index, image ->
                        val isDragged = draggedItemKey == image.imagePath
                        val dragIdx = if (draggedItemKey != null) images.indexOfFirst { it.imagePath == draggedItemKey } else -1
                        val targetIdx = potentialTargetIndex
                        val isTarget = targetIdx == index && !isDragged
                        
                        // Berechnung des visuellen Platzes
                        val visualIndex = if (dragIdx != -1 && targetIdx != null && !isDragged) {
                            if (dragIdx < targetIdx) {
                                if (index in (dragIdx + 1)..targetIdx) index - 1 else index
                            } else {
                                if (index in targetIdx until dragIdx) index + 1 else index
                            }
                        } else index

                        // Physische Maße sicher holen
                        val firstItemInfo = remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.firstOrNull() } }
                        val xOffset: Float
                        val yOffset: Float
                        
                        val firstItem = firstItemInfo.value
                        if (!isDragged && visualIndex != index && firstItem != null) {
                            val itemWidthWithSpacing = firstItem.size.width + spacingPx
                            val itemHeightWithSpacing = firstItem.size.height + spacingPx
                            xOffset = ((visualIndex % 3) - (index % 3)) * itemWidthWithSpacing
                            yOffset = ((visualIndex / 3) - (index / 3)) * itemHeightWithSpacing
                        } else {
                            xOffset = 0f
                            yOffset = 0f
                        }

                        val animatedOffset by animateOffsetAsState(
                            targetValue = if (isDragged) draggingOffset else Offset(xOffset, yOffset),
                            animationSpec = if (draggedItemKey != null) spring() else snap(),
                            label = "shifting"
                        )
                        val scale by animateFloatAsState(if (isDragged) 1.15f else 1f, label = "scale")

                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .animateItem() 
                                .zIndex(if (isDragged) 100f else 1f)
                                .graphicsLayer {
                                    translationX = animatedOffset.x
                                    translationY = animatedOffset.y
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isTarget) 3.dp else if (isDragged) 2.dp else 0.dp,
                                    color = if (isTarget) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .alpha(if (isDragged) 0.8f else 1f)
                        ) {
                            AsyncImage(
                                model = image.imagePath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable(enabled = draggedItemKey == null) { previewImageIndex.value = index }
                            )
                            
                            if (draggedItemKey == null) {
                                Row(modifier = Modifier.fillMaxWidth().padding(4.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton(onClick = golfClick { onEditImage(index) }, modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)) {
                                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = golfClick { imageToDeleteIndex.value = index }, modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(onBackgroundColor.copy(alpha = 0.05f))
                                .border(1.dp, onBackgroundColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable(enabled = draggedItemKey == null) { showImageSourceDialog.value = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, null, tint = onBackgroundColor.copy(alpha = 0.3f), modifier = Modifier.size(32.adaptiveDp()))
                                Spacer(Modifier.height(4.dp))
                                Text("Neu", fontSize = 10.adaptiveSp(), color = onBackgroundColor.copy(alpha = 0.3f), style = shadowStyle.copy(shadow = null))
                            }
                        }
                    }
                }
            }

            // Vorschau-Overlay (Zoom-Funktion)
            AnimatedVisibility(visible = isPreviewActive, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                previewImageIndex.value?.let { index ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    val state = rememberTransformableState { _, zoomChange, panChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                        if (newScale > 1f) {
                            val maxX = (boxSize.width * (newScale - 1f)) / 2f
                            val maxY = (boxSize.height * (newScale - 1f)) / 2f
                            offset = Offset((offset.x + panChange.x).coerceIn(-maxX, maxX), (offset.y + panChange.y).coerceIn(-maxY, maxY))
                        } else offset = Offset.Zero
                        scale = newScale
                    }
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)).onSizeChanged { boxSize = it }.pointerInput(Unit) { detectTapGestures(onTap = { previewImageIndex.value = null }) }, contentAlignment = Alignment.Center) {
                        AsyncImage(model = images[index].imagePath, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y).transformable(state = state).pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }, onTap = { previewImageIndex.value = null }) })
                        IconButton(onClick = { previewImageIndex.value = null }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).then(if (!fullScreenEnabled) Modifier.statusBarsPadding() else Modifier).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)) { Icon(Icons.Default.Close, contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}
