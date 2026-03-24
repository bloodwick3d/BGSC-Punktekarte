package de.bgsc.minigolf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGamesScreen(
    viewModel: GolfViewModel,
    onBack: () -> Unit,
    fullScreenEnabled: Boolean
) {
    val activeGames by viewModel.activeGames.collectAsState()
    val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (fullScreenEnabled) Modifier.statusBarsPadding() else Modifier.systemBarsPadding())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            CenterAlignedTopAppBar(
                title = { Text("Aktive Spiele", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = golfClick { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            if (activeGames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine aktiven Spiele gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeGames) { game ->
                        ActiveGameItem(
                            game = game,
                            dateString = dateFormatter.format(Date(game.date)),
                            onContinue = { viewModel.loadActiveGame(game) },
                            onDelete = { viewModel.deleteHistoryEntry(game.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveGameItem(
    game: GameResult,
    dateString: String,
    onContinue: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.location.ifBlank { "Unbekannter Ort" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = game.system.replace("\n", " "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateString,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row {
                IconButton(onClick = golfClick { onDelete() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = golfClick { onContinue() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Fortsetzen", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
