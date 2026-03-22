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
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    shadowStyle: TextStyle,
    onHapticToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onKeepScreenOnToggle: (Boolean) -> Unit,
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
                    "Einstellungen",
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
                            contentDescription = "Info", 
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
                    text = "Vibration",
                    checked = hapticEnabled,
                    onCheckedChange = { onHapticToggle(it) },
                    shadowStyle = shadowStyle
                )
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    text = "Touch-Sound",
                    checked = soundEnabled,
                    onCheckedChange = { onSoundToggle(it) },
                    shadowStyle = shadowStyle
                )
                SettingsSwitchRow(
                    icon = Icons.Default.BrightnessHigh,
                    text = "Wachbleiben",
                    checked = keepScreenOn,
                    onCheckedChange = { onKeepScreenOnToggle(it) },
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
                    Text("Fertig", style = shadowStyle)
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
        title = { Text("App-Info", color = Color.Black, style = shadowStyle.copy(color = Color.Black, fontWeight = FontWeight.Bold)) },
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
                    title = "Impressum", 
                    content = "Angaben gemäß § 5 DDG\n\n" +
                            "BGSC \"Gut Schlag\" Gladbeck e.V.\n" +
                            "Bohmertstraße 283\n" +
                            "45964 Gladbeck\n\n" +
                            "Vertreten durch den Vorstand:\n" +
                            "1. Vorsitzender: \n" +
                            "2. Vorsitzender: \n\n" +
                            "Kontakt:\n" +
                            "Ansprechpartner: Patrick Kempken\n" +
                            "E-Mail: bloodwick3d@gmail.com\n" +
                            "Telefon: \n\n" +
                            "Registereintrag:\n" +
                            "Eintragung im Vereinsregister.\n" +
                            "Registergericht: \n" +
                            "Registernummer: \n\n" +
                            "Verantwortlich für den Inhalt nach § 18 Abs. 2 MStV:\n" +
                            "Patrick Kempken", 
                    shadowStyle = shadowStyle
                )
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                
                ExpandableInfoSection(
                    title = "Datenschutz", 
                    content = "Diese App arbeitet lokal. Alle Spielstände und Notizen werden ausschließlich auf deinem Gerät gespeichert. Es werden keine persönlichen Daten erfasst, analysiert oder an Dritte weitergegeben. Lediglich beim Prüfen auf Updates wird eine Verbindung zu GitHub hergestellt, um die neueste Version zu finden.",
                    shadowStyle = shadowStyle
                )
                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                
                ExpandableInfoSection(
                    title = "Lizenzen & Open Source", 
                    content = "Diese App nutzt die folgenden Open-Source-Bibliotheken:\n\n" +
                            "• Android Jetpack & Compose (Apache 2.0)\n" +
                            "• Material Design Components 3 (Apache 2.0)\n" +
                            "• Room Persistence Library (Apache 2.0)\n" +
                            "• Kotlin Standard Library (Apache 2.0)\n" +
                            "• Google Gson (Apache 2.0)\n" +
                            "• Square OkHttp (Apache 2.0)\n" +
                            "• AndroidX Core KTX & Lifecycle (Apache 2.0)\n\n" +
                            "Ein besonderer Dank geht an die Open-Source-Community für die Bereitstellung dieser großartigen Werkzeuge.", 
                    shadowStyle = shadowStyle
                )
                
                Spacer(Modifier.height(16.dp))
                Text(
                    "App-Version: $appVersion",
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
                Text("Schließen", style = shadowStyle)
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
                contentDescription = if (expanded) "Einklappen" else "Ausklappen",
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
