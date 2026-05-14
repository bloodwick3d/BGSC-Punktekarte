package de.bgsc.minigolf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Hilfsklasse zum Speichern eines Pfads mit seiner individuellen Farbe.
 */
data class DrawnPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun DrawingScreen(
    imagePath: String?,
    fullScreenEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (ByteArray) -> Unit,
    onResetToOriginal: () -> Unit
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawnPath>() }
    val currentPath = remember { mutableStateOf<Path?>(null) }
    val currentColor = remember { mutableStateOf(Color.Red) }
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    
    val showResetConfirm = remember { mutableStateOf(false) }
    
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )
    
    val drawTrigger = remember { mutableLongStateOf(0L) }
    val baseBitmap = remember(imagePath) { 
        imagePath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)
            } catch (_: Exception) {
                null
            }
        }
    }

    BackHandler { onDismiss() }

    if (showResetConfirm.value) {
        AlertDialog(
            onDismissRequest = { showResetConfirm.value = false },
            title = { Text("Zurücksetzen?", color = onSurfaceColor, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = onSurfaceColor)) },
            text = { 
                Text(
                    if (paths.isNotEmpty()) "Möchtest du die aktuellen Zeichnungen verwerfen?" 
                    else "Möchtest du das Bild auf den Originalzustand zurücksetzen?",
                    color = onSurfaceColor,
                    style = shadowStyle.copy(color = onSurfaceColor)
                ) 
            },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        showResetConfirm.value = false
                        if (paths.isNotEmpty()) paths.clear() else onResetToOriginal()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Zurücksetzen", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { showResetConfirm.value = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Abbrechen", color = onSurfaceColor, style = shadowStyle.copy(color = onSurfaceColor))
                }
            },
            containerColor = surfaceColor
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize().zIndex(100f),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.adaptiveDp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = golfClick { onDismiss() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = onBackgroundColor)
                        }
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Text(
                        "Bild bearbeiten",
                        fontSize = 20.adaptiveSp(),
                        fontWeight = FontWeight.Bold,
                        color = onBackgroundColor,
                        style = shadowStyle.copy(color = onBackgroundColor)
                    )
                }
                
                IconButton(onClick = golfClick {
                    val currentCanvasSize = canvasSize.value
                    if (currentCanvasSize.width > 0 && currentCanvasSize.height > 0) {
                        val resultBitmap = createBitmap(currentCanvasSize.width, currentCanvasSize.height, Bitmap.Config.ARGB_8888)
                        resultBitmap.applyCanvas {
                            baseBitmap?.let { bmp ->
                                val scaledBmp = bmp.scale(currentCanvasSize.width, currentCanvasSize.height, true)
                                drawBitmap(scaledBmp, 0f, 0f, null)
                            }
                            paths.forEach { drawnPath ->
                                val paint = Paint().apply {
                                    color = drawnPath.color.toArgb()
                                    style = Paint.Style.STROKE
                                    strokeWidth = drawnPath.strokeWidth
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    isAntiAlias = true
                                }
                                drawPath(drawnPath.path.asAndroidPath(), paint)
                            }
                        }
                        val stream = ByteArrayOutputStream()
                        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        onSave(stream.toByteArray())
                        Toast.makeText(context, "Bild gespeichert", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.5f), modifier = Modifier.offset(1.5.dp, 1.5.dp))
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Speichern", tint = onBackgroundColor)
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .onGloballyPositioned { canvasSize.value = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val newPath = Path().apply { moveTo(down.position.x, down.position.y) }
                                    currentPath.value = newPath
                                    drag(down.id) { change ->
                                        newPath.lineTo(change.position.x, change.position.y)
                                        drawTrigger.longValue++ 
                                        change.consume()
                                    }
                                    currentPath.value?.let { paths.add(DrawnPath(it, currentColor.value, with(this@pointerInput) { 4.dp.toPx() })) }
                                    currentPath.value = null
                                }
                            }
                    ) {
                        drawTrigger.longValue 
                        baseBitmap?.let { bmp ->
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
                            )
                        }
                        paths.forEach { drawnPath ->
                            drawPath(
                                path = drawnPath.path,
                                color = drawnPath.color,
                                style = Stroke(width = drawnPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        currentPath.value?.let { path ->
                            drawPath(
                                path = path,
                                color = currentColor.value,
                                style = Stroke(width = with(this) { 4.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(currentColor.value).border(2.dp, onBackgroundColor.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(Modifier.width(16.dp))
                HueBar(onColorSelected = { currentColor.value = it }, modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = golfClick { showResetConfirm.value = true }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = onBackgroundColor)
                    }
                }
                IconButton(
                    onClick = golfClick { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                    enabled = paths.isNotEmpty()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Rückgängig", tint = if (paths.isNotEmpty()) onBackgroundColor else onBackgroundColor.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCropScreen(
    uri: Uri, 
    fullScreenEnabled: Boolean,
    onDismiss: () -> Unit, 
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var currentBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val containerSize = remember { mutableStateOf(IntSize.Zero) }
    
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 3f)
    )

    LaunchedEffect(uri) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val original = BitmapFactory.decodeStream(inputStream)
            if (original != null) {
                val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
                
                if (rotation != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotation.toFloat())
                    currentBitmap = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                } else {
                    currentBitmap = original
                }
            }
        }
    }

    if (currentBitmap == null) return

    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale.floatValue *= zoomChange
        offset.value += panChange
    }

    BackHandler { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize().zIndex(100f), 
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.adaptiveDp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = golfClick { onDismiss() }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = onBackgroundColor)
                    }
                }
                Spacer(Modifier.width(8.adaptiveDp()))
                Text(
                    "Bildausschnitt wählen",
                    fontSize = 20.adaptiveSp(),
                    fontWeight = FontWeight.Bold,
                    color = onBackgroundColor,
                    style = shadowStyle.copy(color = onBackgroundColor)
                )
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clipToBounds()
                        .onGloballyPositioned { containerSize.value = it.size }
                        .border(2.dp, onBackgroundColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale.floatValue, scaleY = scale.floatValue, translationX = offset.value.x, translationY = offset.value.y).transformable(state = state),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = golfClick { onDismiss() }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Abbrechen", tint = onBackgroundColor)
                    }
                }

                IconButton(onClick = golfClick {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(90f)
                    currentBitmap = Bitmap.createBitmap(currentBitmap!!, 0, 0, currentBitmap!!.width, currentBitmap!!.height, matrix, true)
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Drehen", tint = onBackgroundColor)
                    }
                }

                IconButton(onClick = golfClick {
                    if (containerSize.value.width > 0) {
                        val cropped = performCrop(currentBitmap!!, scale.floatValue, offset.value, containerSize.value.width, containerSize.value.height)
                        onConfirm(cropped)
                    }
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = onBackgroundColor.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Bestätigen", tint = onBackgroundColor)
                    }
                }
            }
            
            Text(
                "Bild mit zwei Fingern zoomen und schieben", 
                color = onBackgroundColor.copy(alpha = 0.5f), 
                fontSize = 12.sp, 
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                style = shadowStyle.copy(color = onBackgroundColor.copy(alpha = 0.5f))
            )
        }
    }
}

private fun performCrop(bitmap: Bitmap, userScale: Float, userOffset: Offset, viewWidth: Int, viewHeight: Int): Bitmap {
    val bmpWidth = bitmap.width.toFloat()
    val bmpHeight = bitmap.height.toFloat()
    val scale0 = minOf(viewWidth / bmpWidth, viewHeight / bmpHeight)
    val tx0 = (viewWidth - bmpWidth * scale0) / 2f
    val ty0 = (viewHeight - bmpHeight * scale0) / 2f
    val targetWidth = 1200
    val targetHeight = 1600
    val result = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val matrix = android.graphics.Matrix()
    matrix.postScale(scale0, scale0)
    matrix.postTranslate(tx0, ty0)
    matrix.postScale(userScale, userScale, viewWidth / 2f, viewHeight / 2f)
    matrix.postTranslate(userOffset.x, userOffset.y)
    val finalScaleFactor = targetWidth.toFloat() / viewWidth.toFloat()
    matrix.postScale(finalScaleFactor, finalScaleFactor)
    canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return result
}

@Composable
fun HueBar(
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier
                .height(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    )
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitFirstDown()
                            val progress = (event.position.x / width).coerceIn(0f, 1f)
                            onColorSelected(Color.hsv(progress * 360f, 1f, 1f))
                            drag(event.id) { change ->
                                val p = (change.position.x / width).coerceIn(0f, 1f)
                                onColorSelected(Color.hsv(p * 360f, 1f, 1f))
                                change.consume()
                            }
                        }
                    }
                }
        )
    }
}
