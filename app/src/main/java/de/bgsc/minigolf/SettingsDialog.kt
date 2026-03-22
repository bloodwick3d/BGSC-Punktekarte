package de.bgsc.minigolf

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
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
    currentLanguage: String,
    shadowStyle: TextStyle,
    onHapticToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowInfo: () -> Unit
) {
    val buttonShape = RoundedCornerShape(20.dp)

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
        containerColor = Color.White.copy(alpha = 0.4f),
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
                
                // Language Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Language, null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(24.dp).offset(1.dp, 1.dp))
                            Icon(Icons.Default.Language, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_language), style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Medium), fontSize = 16.sp)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "DE", 
                            modifier = Modifier.golfClickable { onLanguageChange("de") },
                            color = if (currentLanguage == "de") Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.5f),
                            fontWeight = if (currentLanguage == "de") FontWeight.Bold else FontWeight.Normal,
                            style = shadowStyle.copy(color = if (currentLanguage == "de") Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.5f))
                        )
                        Text(" / ", style = shadowStyle.copy(color = Color.Black.copy(alpha = 0.3f)))
                        Text(
                            "EN", 
                            modifier = Modifier.golfClickable { onLanguageChange("en") },
                            color = if (currentLanguage == "en") Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.5f),
                            fontWeight = if (currentLanguage == "en") FontWeight.Bold else FontWeight.Normal,
                            style = shadowStyle.copy(color = if (currentLanguage == "en") Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.5f))
                        )
                    }
                }
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.info_title), color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
        containerColor = Color.White.copy(alpha = 0.4f),
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
