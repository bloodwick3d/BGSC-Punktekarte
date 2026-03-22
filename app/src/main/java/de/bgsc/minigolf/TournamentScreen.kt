package de.bgsc.minigolf

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    onSaveFinished: () -> Unit
) {
    val notes = viewModel.tournamentNotes
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    var expanded by remember { mutableStateOf(false) }
    val systems = listOf(
        "Miniaturgolf (Eternit)",
        "Minigolf (Beton)",
        "Filzgolf",
        "Cobigolf",
        "Sterngolf"
    )

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header mit Save Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.adaptiveDp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = golfClick { onBack() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Zurück",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Spacer(Modifier.width(8.adaptiveDp()))
                    Text(
                        if (viewModel.currentTournamentNoteId == null) "Notiz erstellen" else "Notiz bearbeiten",
                        fontSize = 20.adaptiveSp(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                }
                
                IconButton(onClick = golfClick {
                    viewModel.saveTournamentNotes()
                    Toast.makeText(context, "Notizen gespeichert", Toast.LENGTH_SHORT).show()
                    onSaveFinished()
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.offset(1.dp, 1.dp))
                        Icon(
                            imageVector = Icons.Default.Save, 
                            contentDescription = "Speichern", 
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Eingabebereich für Ort und System (Anlagentyp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.adaptiveDp())
            ) {
                OutlinedTextField(
                    value = viewModel.tournamentLocation,
                    onValueChange = { viewModel.tournamentLocation = it },
                    label = { 
                        Text(
                            "Ort", 
                            fontFamily = CalibriFontFamily,
                            style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                
                Spacer(modifier = Modifier.height(8.adaptiveDp()))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = viewModel.tournamentGameMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { 
                            Text(
                                "Anlagentyp", 
                                fontFamily = CalibriFontFamily,
                                style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) 
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        textStyle = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        systems.forEach { system ->
                            DropdownMenuItem(
                                text = { Text(text = system, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
                                onClick = golfClick {
                                    viewModel.tournamentGameMode = system
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.adaptiveDp()))

            // Tabelle
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.adaptiveDp())
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                    Text("Bälle", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                    Text("Abschlag", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                    Text("Notizen", modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                }

                notes.forEachIndexed { index, note ->
                    TournamentRow(
                        holeNumber = index + 1,
                        note = note,
                        onUpdate = { b, s, n -> viewModel.updateTournamentNote(index, b, s, n) },
                        shadowStyle = shadowStyle
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun TournamentRow(
    holeNumber: Int,
    note: HoleNote,
    onUpdate: (String, String, String) -> Unit,
    shadowStyle: TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Loch Nummer
        Text(
            text = holeNumber.toString(),
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
        )

        // Bälle Column
        TournamentTextField(
            value = note.ball,
            onValueChange = { onUpdate(it, note.startPoint, note.notes) },
            modifier = Modifier.weight(1f),
            shadowStyle = shadowStyle
        )

        // Abschlagspunkt Column
        TournamentTextField(
            value = note.startPoint,
            onValueChange = { onUpdate(note.ball, it, note.notes) },
            modifier = Modifier.weight(1f),
            shadowStyle = shadowStyle
        )

        // Notizen Column
        TournamentTextField(
            value = note.notes,
            onValueChange = { onUpdate(note.ball, note.startPoint, it) },
            modifier = Modifier.weight(1.5f),
            shadowStyle = shadowStyle
        )
    }
}

@Composable
fun TournamentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    shadowStyle: TextStyle
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = shadowStyle.copy(
                fontSize = 13.sp, 
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
