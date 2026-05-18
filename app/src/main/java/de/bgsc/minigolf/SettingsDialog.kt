package de.bgsc.minigolf

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSettingsDialog(
    hapticEnabled: Boolean,
    soundEnabled: Boolean,
    keepScreenOn: Boolean,
    fullScreenEnabled: Boolean,
    customBackgroundUri: String?,
    shadowStyle: TextStyle,
    onHapticToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    onFullScreenToggle: (Boolean) -> Unit,
    onSelectBackground: () -> Unit,
    onResetBackground: () -> Unit,
    onDismiss: () -> Unit,
    onShowInfo: () -> Unit
) {
    val buttonShape = RoundedCornerShape(20.dp)
    val dialogBgColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.White
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    color = Color.Black,
                    style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = golfClick {
                    onDismiss()
                    onShowInfo()
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = null, 
                            tint = Color.Black.copy(alpha = 0.2f), 
                            modifier = Modifier.offset(1.dp, 1.dp)
                        )
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = stringResource(R.string.menu_info),
                            tint = Color.Black.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        },
        containerColor = dialogBgColor,
        tonalElevation = 0.dp,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Vibration,
                    text = stringResource(R.string.settings_vibration),
                    checked = hapticEnabled,
                    onCheckedChange = { onHapticToggle(it) },
                    shadowStyle = shadowStyle
                )
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    text = stringResource(R.string.settings_sound),
                    checked = soundEnabled,
                    onCheckedChange = { onSoundToggle(it) },
                    shadowStyle = shadowStyle
                )
                SettingsSwitchRow(
                    icon = Icons.Default.BrightnessHigh,
                    text = stringResource(R.string.settings_keep_screen_on),
                    checked = keepScreenOn,
                    onCheckedChange = { onKeepScreenOnToggle(it) },
                    shadowStyle = shadowStyle
                )
                SettingsSwitchRow(
                    icon = Icons.Default.Fullscreen,
                    text = stringResource(R.string.settings_full_screen),
                    checked = fullScreenEnabled,
                    onCheckedChange = { onFullScreenToggle(it) },
                    shadowStyle = shadowStyle
                )
                
                SettingsBackgroundRow(
                    customBackgroundUri = customBackgroundUri,
                    onSelect = onSelectBackground,
                    onReset = onResetBackground,
                    shadowStyle = shadowStyle
                )
            }
        },
        confirmButton = {
            Button(
                onClick = golfClick { onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = buttonShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).offset(1.dp, 1.dp))
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_btn_done), style = shadowStyle)
                }
            }
        }
    )
}

@Composable
fun AppInfoDialog(appVersion: String, shadowStyle: TextStyle, onDismiss: () -> Unit) {
    val buttonShape = RoundedCornerShape(20.dp)
    val dialogBgColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.White
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info_title), color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
        containerColor = dialogBgColor,
        tonalElevation = 0.dp,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExpandableInfoSection(
                    title = stringResource(R.string.info_imprint_title),
                    content = stringResource(R.string.info_imprint_content),
                    shadowStyle = shadowStyle
                )
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                
                ExpandableInfoSection(
                    title = stringResource(R.string.info_privacy_title),
                    content = stringResource(R.string.info_privacy_content),
                    shadowStyle = shadowStyle
                )
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                
                ExpandableInfoSection(
                    title = stringResource(R.string.info_licenses_title),
                    content = stringResource(R.string.info_licenses_content),
                    shadowStyle = shadowStyle
                )
                
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.info_version, appVersion),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = shadowStyle.copy(color = Color.Gray, fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = golfClick { onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = buttonShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.info_btn_close), style = shadowStyle)
            }
        }
    )
}

@Composable
private fun ExpandableInfoSection(title: String, content: String, shadowStyle: TextStyle) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .golfClickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title, 
                fontWeight = FontWeight.Bold, 
                style = shadowStyle.copy(color = Color.Black, fontSize = 16.sp)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.info_collapse) else stringResource(R.string.info_expand),
                tint = Color.Black.copy(alpha = 0.5f)
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                content, 
                modifier = Modifier.padding(bottom = 12.dp),
                style = shadowStyle.copy(color = Color.Black.copy(alpha = 0.7f), fontSize = 14.sp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shadowStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.05f))
            .golfClickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = Color.Black.copy(alpha = 0.2f), 
                    modifier = Modifier.size(24.dp).offset(1.dp, 1.dp)
                )
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Medium), fontSize = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFD54F),
                checkedTrackColor = Color(0xFFFFD54F).copy(alpha = 0.5f)
            ),
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun SettingsBackgroundRow(
    customBackgroundUri: String?,
    onSelect: () -> Unit,
    onReset: () -> Unit,
    shadowStyle: TextStyle
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Erste Reihe: Icon und Label
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp).offset(1.dp, 1.dp)
                )
                Icon(Icons.Default.Photo, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings_custom_background),
                style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Medium),
                fontSize = 16.sp
            )
        }

        // Zweite Reihe: Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customBackgroundUri != null) {
                // Zurücksetzen Button
                Button(
                    onClick = golfClick { onReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_bg_reset),
                        style = shadowStyle.copy(color = Color.Red.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }

            // Bild wählen Button
            Button(
                onClick = golfClick { onSelect() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F).copy(alpha = 0.8f)),
                contentPadding = PaddingValues(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Text(
                    stringResource(R.string.settings_bg_select),
                    style = shadowStyle.copy(color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}
