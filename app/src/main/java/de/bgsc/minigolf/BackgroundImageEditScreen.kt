package de.bgsc.minigolf

import android.graphics.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

enum class EditMode {
    BRIGHTNESS, CONTRAST, SATURATION, BLUR, FILTERS
}

enum class FilterPreset(val label: String) {
    NONE("Original"),
    BW("S/W"),
    SEPIA("Sepia"),
    COLD("Kalt"),
    WARM("Warm"),
    VINTAGE("Vintage")
}

@Composable
fun BackgroundImageEditScreen(
    imagePath: String,
    fullScreenEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        // Wir laden das Bild mit einem vernünftigen Limit für die Bearbeitung (max 2560px)
        // Das verhindert Abstürze durch zu hohen Speicherverbrauch bei 48MP Fotos.
        val originalFullBitmap = remember(imagePath) { 
            try { 
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imagePath, options)
                val maxDim = 2560f
                val scale = if (maxOf(options.outWidth, options.outHeight) > maxDim) {
                    (maxOf(options.outWidth, options.outHeight) / maxDim).toInt()
                } else 1
                
                val loadOptions = BitmapFactory.Options().apply { inSampleSize = scale }
                BitmapFactory.decodeFile(imagePath, loadOptions)
            } catch (_: Exception) { null }
        }
        
        val previewBitmap = remember(originalFullBitmap) {
            originalFullBitmap?.let { 
                val maxDim = 1280f
                val scale = maxDim / maxOf(it.width, it.height)
                if (scale < 1f) {
                    it.scale((it.width * scale).toInt(), (it.height * scale).toInt(), true)
                } else it
            }
        }

        var brightnessStep by remember { mutableFloatStateOf(0f) }
        var contrastStep by remember { mutableFloatStateOf(0f) }
        var saturationStep by remember { mutableFloatStateOf(0f) }
        var blurStep by remember { mutableFloatStateOf(0f) }
        var selectedFilter by remember { mutableStateOf(FilterPreset.NONE) }

        var currentMode by remember { mutableStateOf(EditMode.BRIGHTNESS) }

        var editedPreviewBitmap by remember { mutableStateOf(previewBitmap) }
        var isDragging by remember { mutableStateOf(false) }
        var isProcessing by remember { mutableStateOf(false) }

        val shadowStyle = TextStyle(
            fontFamily = CalibriFontFamily,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                blurRadius = 3f
            )
        )

        val brightness = brightnessStep * 10f 
        val contrast = 1f + (contrastStep * 0.1f)
        val saturation = 1f + (saturationStep * 0.1f)
        val blurRadius = blurStep

        LaunchedEffect(brightness, contrast, saturation, blurRadius, selectedFilter, isDragging) {
            withContext(Dispatchers.Default) {
                if (!isDragging) {
                    isProcessing = true
                    previewBitmap?.let { 
                        val filtered = applyFilters(it, brightness, contrast, saturation, blurRadius, selectedFilter)
                        withContext(Dispatchers.Main) {
                            editedPreviewBitmap = filtered
                            isProcessing = false
                        }
                    }
                } else {
                    previewBitmap?.let { 
                        val dragBlur = if (currentMode == EditMode.BLUR) blurRadius * 0.3f else 0f
                        val filtered = applyFilters(it, brightness, contrast, saturation, dragBlur, selectedFilter)
                        withContext(Dispatchers.Main) {
                            editedPreviewBitmap = filtered
                        }
                    }
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                editedPreviewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
                        .padding(16.adaptiveDp())
                ) {
                    IconButton(
                        onClick = golfClick { onDismiss() },
                        modifier = Modifier.align(Alignment.CenterStart).background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    
                    Text(
                        stringResource(R.string.image_edit_title),
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 20.adaptiveSp(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = shadowStyle
                    )

                    IconButton(
                        enabled = !isProcessing,
                        onClick = golfClick {
                            isProcessing = true
                            scope.launch {
                                withContext(Dispatchers.Default) {
                                    originalFullBitmap?.let { fullBmp ->
                                        val previewWidth = previewBitmap?.width?.toFloat() ?: 1f
                                        val scaleFactor = fullBmp.width.toFloat() / previewWidth
                                        val scaledBlur = blurRadius * scaleFactor
                                        
                                        val finalBmp = applyFilters(fullBmp, brightness, contrast, saturation, scaledBlur, selectedFilter)
                                        val stream = ByteArrayOutputStream()
                                        finalBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                        withContext(Dispatchers.Main) {
                                            onSave(stream.toByteArray())
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        if (isProcessing && !isDragging) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.image_edit_save), tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(bottom = 32.adaptiveDp())
                        .then(if (!fullScreenEnabled) Modifier.navigationBarsPadding() else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.adaptiveDp(), vertical = 16.adaptiveDp()),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentMode,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
                            },
                            label = "slider_anim"
                        ) { mode ->
                            when (mode) {
                                EditMode.BRIGHTNESS -> EditSlider(stringResource(R.string.image_edit_brightness), brightnessStep, { brightnessStep = it; isDragging = true }, { isDragging = false }, -100f..100f) // Intern gemappt auf -10..10
                                EditMode.CONTRAST -> EditSlider(stringResource(R.string.image_edit_contrast), contrastStep, { contrastStep = it; isDragging = true }, { isDragging = false }, -10f..10f)
                                EditMode.SATURATION -> EditSlider(stringResource(R.string.image_edit_saturation), saturationStep, { saturationStep = it; isDragging = true }, { isDragging = false }, -10f..10f)
                                EditMode.BLUR -> EditSlider(stringResource(R.string.image_edit_blur), blurStep, { blurStep = it; isDragging = true }, { isDragging = false }, 0f..20f)
                                EditMode.FILTERS -> FilterSelector(selectedFilter) { selectedFilter = it }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.adaptiveDp()),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ModeButton(Icons.Default.Brightness6, currentMode == EditMode.BRIGHTNESS) { currentMode = EditMode.BRIGHTNESS }
                        ModeButton(Icons.Default.Contrast, currentMode == EditMode.CONTRAST) { currentMode = EditMode.CONTRAST }
                        ModeButton(Icons.Default.Palette, currentMode == EditMode.SATURATION) { currentMode = EditMode.SATURATION }
                        ModeButton(Icons.Default.BlurOn, currentMode == EditMode.BLUR) { currentMode = EditMode.BLUR }
                        ModeButton(Icons.Default.FilterHdr, currentMode == EditMode.FILTERS) { currentMode = EditMode.FILTERS }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSelector(selectedFilter: FilterPreset, onFilterSelected: (FilterPreset) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(FilterPreset.entries) { preset ->
            val isSelected = preset == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                    .clickable { onFilterSelected(preset) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.adaptiveSp()
                )
            }
        }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f), label = "bg")
    val iconColor by animateColorAsState(if (isSelected) Color.Black else Color.White, label = "icon")
    Box(modifier = Modifier.size(48.adaptiveDp()).clip(CircleShape).background(backgroundColor).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.adaptiveDp()))
    }
}

@Composable
private fun EditSlider(label: String, value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int = 19) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished, valueRange = valueRange, steps = steps, colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F), inactiveTrackColor = Color.White.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth())
    }
}

private fun applyFilters(
    bitmap: Bitmap,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    blurRadius: Float,
    preset: FilterPreset
): Bitmap {
    val result = createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    
    val cm = ColorMatrix()
    
    // 1. Preset Matrix anwenden
    when (preset) {
        FilterPreset.BW -> cm.setSaturation(0f)
        FilterPreset.SEPIA -> {
            cm.setSaturation(0f)
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 10f,
                0f, 0f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterPreset.COLD -> {
            cm.postConcat(ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 0.9f, 0f, 0f, 0f,
                0f, 0.2f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterPreset.WARM -> {
            cm.postConcat(ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1f, 0f, 0f, 5f,
                0f, 0f, 0.8f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        FilterPreset.VINTAGE -> {
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 10f,
                0f, 0.9f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
            cm.postConcat(ColorMatrix().apply { setSaturation(0.8f) })
        }
        else -> {}
    }

    // 2. Manuelle Anpassungen drüberlegen
    val tempCm = ColorMatrix()
    tempCm.setSaturation(saturation)
    val translate = brightness + 128f * (1f - contrast)
    tempCm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, translate, 0f, contrast, 0f, 0f, translate, 0f, 0f, contrast, 0f, translate, 0f, 0f, 0f, 1f, 0f)))
    
    cm.postConcat(tempCm)
    
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return if (blurRadius >= 1f) stackBlur(result, blurRadius.toInt()) else result
}

private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
    val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
    val w = bitmap.width
    val h = bitmap.height
    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)
    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1
    val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh)
    var rsum: Int; var gsum: Int; var bsum: Int
    val vmin = IntArray(maxOf(w, h))
    val dv = IntArray(256 * (radius + 1) * (radius + 1))
    for (i in dv.indices) dv[i] = i / ((radius + 1) * (radius + 1))
    var yw = 0
    var yi = 0
    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int; var stackstart: Int; var sir: IntArray; var rbs: Int
    val r1 = radius + 1
    var routsum: Int; var goutsum: Int; var boutsum: Int
    var rinsum: Int; var ginsum: Int; var binsum: Int

    for (yIdx in 0 until h) {
        rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
        for (i in -radius..radius) {
            val p = pix[yi + minOf(wm, maxOf(i, 0))]
            sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
            rbs = r1 - abs(i); rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
            if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] } 
            else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
        }
        stackpointer = radius
        for (xIdx in 0 until w) {
            r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            stackstart = stackpointer - radius + div; sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (yIdx == 0) vmin[xIdx] = minOf(xIdx + radius + 1, wm)
            val p = pix[yw + vmin[xIdx]]
            sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = (p and 0x0000ff)
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer % div]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi++
        }
        yw += w
    }
    for (xIdx in 0 until w) {
        rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
        var yp = -radius * w
        for (i in -radius..radius) {
            val yiIdx = maxOf(0, yp) + xIdx
            sir = stack[i + radius]; sir[0] = r[yiIdx]; sir[1] = g[yiIdx]; sir[2] = b[yiIdx]
            rbs = r1 - abs(i); rsum += r[yiIdx] * rbs; gsum += g[yiIdx] * rbs; bsum += b[yiIdx] * rbs
            if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] } 
            else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            if (i < hm) yp += w
        }
        yi = xIdx; stackpointer = radius
        for (yIdx in 0 until h) {
            pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            stackstart = stackpointer - radius + div; sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (xIdx == 0) vmin[yIdx] = minOf(yIdx + r1, hm) * w
            val p = xIdx + vmin[yIdx]
            sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div; sir = stack[stackpointer]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi += w
        }
    }
    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return bitmap
}
