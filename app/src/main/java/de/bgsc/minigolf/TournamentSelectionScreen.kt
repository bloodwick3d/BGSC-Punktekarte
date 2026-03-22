package de.bgsc.minigolf

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Hilfs-Modifier für den "Text-Style" Schlagschatten
fun Modifier.tournamentTextStyleShadow(
    color: Color = Color.Black.copy(alpha = 0.5f),
    offset: Offset = Offset(2f, 2f),
    blurRadius: Float = 3f,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp)
) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        if (blurRadius > 0f) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        frameworkPaint.color = color.toArgb()

        val left = offset.x
        val top = offset.y
        val right = size.width + offset.x
        val bottom = size.height + offset.y

        canvas.drawRoundRect(
            left, top, right, bottom,
            shape.topStart.toPx(size, this),
            shape.topStart.toPx(size, this),
            paint
        )
    }
}

// Hilfs-Modifier für runden "Text-Style" Schlagschatten
fun Modifier.roundTextStyleShadow(
    color: Color = Color.Black.copy(alpha = 0.5f),
    offset: Offset = Offset(2f, 2f),
    blurRadius: Float = 3f
) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        if (blurRadius > 0f) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        frameworkPaint.color = color.toArgb()

        canvas.drawCircle(
            center = Offset(size.width / 2 + offset.x, size.height / 2 + offset.y),
            radius = size.width / 2,
            paint = paint
        )
    }
}

@Composable
fun TournamentSelectionScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    onNewNote: () -> Unit,
    onShowHistory: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val showExitConfirmationState = remember { mutableStateOf(false) }

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    // Launcher für Export (.bgsc Format)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            viewModel.exportTournamentNotes(context, it) { success ->
                if (success) Toast.makeText(context, "Export erfolgreich!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Export fehlgeschlagen.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher für Import (.bgsc Format)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importTournamentNotes(context, it) { success, count ->
                if (success) Toast.makeText(context, "$count Notizen importiert!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Import fehlgeschlagen (Datei ungültig?).", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showExitConfirmationState.value) {
        val buttonShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { showExitConfirmationState.value = false },
            title = { Text("Turnier-Modus deaktivieren?", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) },
            text = { Text("Möchtest du den Turnier-Modus wirklich deaktivieren? Du kannst ihn jederzeit über das Menü wieder aktivieren.", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
            confirmButton = {
                Button(
                    onClick = golfClick {
                        viewModel.toggleTurnierMode(false)
                        showExitConfirmationState.value = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = buttonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Deaktivieren", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                }
            },
            dismissButton = {
                Button(
                    onClick = golfClick { 
                        showExitConfirmationState.value = false 
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.adaptiveDp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = golfClick { onBack() }
                ) {
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
                    "Turnier-Modus",
                    fontSize = 20.adaptiveSp(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                )
            }

            // Haupt-Karten Bereich
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.adaptiveDp(), vertical = 12.adaptiveDp()),
                verticalArrangement = Arrangement.spacedBy(20.adaptiveDp()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.adaptiveDp()))
                
                SelectionCard(
                    title = "Notiz erstellen",
                    subtitle = "Erstelle eine neue Strategie",
                    icon = Icons.Default.Add,
                    color = Color(0xFF4CAF50),
                    onClick = { onNewNote() },
                    shadowStyle = shadowStyle
                )

                SelectionCard(
                    title = "Gespeicherte Notizen",
                    subtitle = "Deine Strategien ansehen",
                    icon = Icons.Default.Description,
                    color = Color(0xFF2196F3),
                    onClick = { onShowHistory() },
                    shadowStyle = shadowStyle
                )
            }

            // Unterer Icon-Bereich
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.adaptiveDp(), start = 12.adaptiveDp(), end = 12.adaptiveDp()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (themeIcon, themeName) = when (viewModel.tournamentTheme) {
                    TournamentTheme.LIGHT -> Icons.Default.LightMode to "Hell"
                    TournamentTheme.DARK -> Icons.Default.DarkMode to "Dunkel"
                    TournamentTheme.SYSTEM -> Icons.Default.BrightnessAuto to "System"
                }

                SmallOptionButton(
                    icon = themeIcon,
                    label = themeName,
                    color = Color(0xFF9C27B0),
                    onClick = {
                        val nextTheme = when (viewModel.tournamentTheme) {
                            TournamentTheme.SYSTEM -> TournamentTheme.LIGHT
                            TournamentTheme.LIGHT -> TournamentTheme.DARK
                            TournamentTheme.DARK -> TournamentTheme.SYSTEM
                        }
                        viewModel.setTournamentDesign(nextTheme)
                    },
                    shadowStyle = shadowStyle
                )

                SmallOptionButton(
                    icon = Icons.Default.FileUpload,
                    label = "Export",
                    color = Color(0xFFFF9800),
                    onClick = { exportLauncher.launch("turnier_strategien.bgsc") },
                    shadowStyle = shadowStyle
                )

                SmallOptionButton(
                    icon = Icons.Default.FileDownload,
                    label = "Import",
                    color = Color(0xFF00BCD4),
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    shadowStyle = shadowStyle
                )

                SmallOptionButton(
                    icon = Icons.Default.PowerSettingsNew,
                    label = "Aus",
                    color = Color(0xFFF44336),
                    onClick = { showExitConfirmationState.value = true },
                    shadowStyle = shadowStyle
                )
            }
        }
    }
}

@Composable
fun SmallOptionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    shadowStyle: TextStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.golfClickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(52.adaptiveDp())
                .roundTextStyleShadow(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.padding(13.adaptiveDp()).fillMaxSize().offset(1.5.dp, 1.5.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.padding(13.adaptiveDp()).fillMaxSize(),
                    tint = color
                )
            }
        }
        Spacer(Modifier.height(8.adaptiveDp()))
        Text(
            text = label,
            fontSize = 10.adaptiveSp(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            style = shadowStyle.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        )
    }
}

@Composable
fun SelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    shadowStyle: TextStyle
) {
    val cardShape = RoundedCornerShape(24.adaptiveDp())
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.adaptiveDp())
            .tournamentTextStyleShadow(shape = cardShape)
            .clip(cardShape)
            .golfClickable { onClick() },
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.adaptiveDp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.adaptiveDp()),
                shape = RoundedCornerShape(14.adaptiveDp()),
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.3f), modifier = Modifier.padding(12.adaptiveDp()).fillMaxSize().offset(1.5.dp, 1.5.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(12.adaptiveDp()).fillMaxSize(),
                        tint = color
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.adaptiveDp()))
            Column {
                Text(
                    text = title,
                    fontSize = 17.adaptiveSp(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.adaptiveSp(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                )
            }
        }
    }
}
