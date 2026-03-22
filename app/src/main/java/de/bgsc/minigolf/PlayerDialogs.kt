package de.bgsc.minigolf

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlayerDialog(
    player: Player,
    shadowStyle: TextStyle,
    onDismiss: () -> Unit,
    onSave: (String, Color) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    var editName by remember { mutableStateOf(player.name) }
    val initialHue = remember {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(player.color.toArgb(), hsv)
        hsv[0]
    }
    var editHue by remember { mutableFloatStateOf(initialHue) }
    val editColor = Color.hsv(editHue, 0.8f, 0.6f)
    val buttonShape = RoundedCornerShape(20.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spieler bearbeiten", color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
        containerColor = Color.White.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
        text = {
            val tfColors = TextFieldDefaults.colors(
                focusedContainerColor = editColor,
                unfocusedContainerColor = editColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f)
            )
            Column {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    placeholder = { Text("Name", fontFamily = CalibriFontFamily) },
                    singleLine = true,
                    colors = tfColors,
                    textStyle = shadowStyle.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                HueSlider(hue = editHue, onHueChange = { editHue = it })
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = golfClick { onSave(editName, editColor) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).offset(1.dp, 1.dp))
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Speichern", style = shadowStyle, fontSize = 14.sp)
                    }
                }
                if (canRemove) {
                    Button(
                        onClick = golfClick { onRemove() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = buttonShape,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).offset(1.dp, 1.dp))
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Entfernen", style = shadowStyle, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlayerDialog(
    playerCount: Int,
    shadowStyle: TextStyle,
    onDismiss: () -> Unit,
    onAdd: (String, Color) -> Unit
) {
    var addName by remember { mutableStateOf("") }
    var addHue by remember { mutableFloatStateOf(kotlin.random.Random.nextFloat() * 360f) }
    val addColor = Color.hsv(addHue, 0.8f, 0.6f)
    val buttonShape = RoundedCornerShape(20.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spieler hinzufügen", color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
        containerColor = Color.White.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
        text = {
            val tfColors = TextFieldDefaults.colors(
                focusedContainerColor = addColor,
                unfocusedContainerColor = addColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.7f)
            )
            Column {
                TextField(
                    value = addName,
                    onValueChange = { addName = it },
                    placeholder = { Text("Name", fontFamily = CalibriFontFamily) },
                    singleLine = true,
                    colors = tfColors,
                    textStyle = shadowStyle.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                HueSlider(hue = addHue, onHueChange = { editHue -> addHue = editHue })
            }
        },
        confirmButton = {
            Button(
                onClick = golfClick {
                    val name = addName.ifBlank { "Spieler ${playerCount + 1}" }
                    onAdd(name, addColor)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = buttonShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).offset(1.dp, 1.dp))
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Hinzufügen", style = shadowStyle)
                }
            }
        }
    )
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                        Color.Blue, Color.Magenta, Color.Red
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            val xPos = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(xPos, size.height / 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
}
