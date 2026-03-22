package de.bgsc.minigolf

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopAppBar(
    selectedSystem: String,
    onSystemSelected: (String) -> Unit,
    currentLocation: String,
    onLocationChanged: (String) -> Unit,
    onLogoClick: () -> Unit,
    titleBarHeight: Dp,
    logoSize: Dp,
    dynamicSystemFontSize: TextUnit,
    shadowStyle: TextStyle
) {
    val focusManager = LocalFocusManager.current
    
    val isKeyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(titleBarHeight)
            .padding(horizontal = 16.adaptiveDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            val infiniteTransition = rememberInfiniteTransition(label = "logo")
            val logoScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "logoScale"
            )
            Image(
                painter = painterResource(id = R.drawable.bgsc_logo),
                contentDescription = stringResource(R.string.top_bar_logo_desc),
                modifier = Modifier
                    .size(logoSize)
                    .scale(logoScale)
                    .golfClickable {
                        focusManager.clearFocus()
                        onLogoClick()
                    }
            )
            
            Spacer(Modifier.width(12.adaptiveDp()))
            
            Box(modifier = Modifier.weight(1f)) {
                if (currentLocation.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Place, null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(14.adaptiveDp()).offset(1.dp, 1.dp))
                            Icon(Icons.Default.Place, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.adaptiveDp()))
                        }
                        Spacer(Modifier.width(4.adaptiveDp()))
                        Text(
                            stringResource(R.string.top_bar_location_placeholder),
                            style = shadowStyle.copy(fontFamily = CalibriFontFamily, color = Color.White.copy(alpha = 0.5f), fontSize = (dynamicSystemFontSize.value * 0.8f).roundToInt().adaptiveSp())
                        )
                    }
                }
                BasicTextField(
                    value = currentLocation,
                    onValueChange = onLocationChanged,
                    textStyle = shadowStyle.copy(color = Color.White, fontWeight = FontWeight.Medium, fontSize = (dynamicSystemFontSize.value * 0.85f).roundToInt().adaptiveSp()),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )
            }
        }
        
        var showSystemMenu by remember { mutableStateOf(false) }
        Box(modifier = Modifier.padding(start = 8.adaptiveDp())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.golfClickable {
                    focusManager.clearFocus()
                    showSystemMenu = true
                }.padding(vertical = 4.adaptiveDp())
            ) {
                Text(
                    text = selectedSystem,
                    style = shadowStyle.copy(
                        color = Color.White,
                        fontSize = dynamicSystemFontSize,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.End,
                    lineHeight = dynamicSystemFontSize * 1.1f
                )
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.size(titleBarHeight * 0.35f).offset(1.5.dp, 1.5.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(titleBarHeight * 0.35f)
                    )
                }
            }
            
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent)) {
                DropdownMenu(
                    expanded = showSystemMenu,
                    onDismissRequest = { showSystemMenu = false },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.8f))
                ) {
                    listOf(
                        stringResource(R.string.system_eternit_newline),
                        stringResource(R.string.system_beton_newline),
                        stringResource(R.string.system_filz),
                        stringResource(R.string.system_cobi),
                        stringResource(R.string.system_stern)
                    ).filter { it != selectedSystem }.forEach { system ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    system, 
                                    color = Color.Black, 
                                    fontSize = 14.adaptiveSp(),
                                    style = shadowStyle.copy(color = Color.Black)
                                ) 
                            },
                            onClick = golfClick {
                                onSystemSelected(system)
                                showSystemMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
