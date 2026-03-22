package de.bgsc.minigolf

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun NavigationDrawer(
    showSideMenu: Boolean,
    onDismiss: () -> Unit,
    playerCount: Int,
    numRounds: Int,
    hapticEnabled: Boolean,
    isTurnierMode: Boolean,
    onAddPlayerClick: () -> Unit,
    onShowResultsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onTournamentClick: () -> Unit,
    onNextRoundClick: () -> Unit,
    onRestartClick: () -> Unit,
    onEndGameClick: () -> Unit,
    onTurnierModeToggle: (Boolean) -> Unit,
    onShowSettings: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val sound = LocalSoundFeedback.current
    var devClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var currentToast by remember { mutableStateOf<Toast?>(null) }

    val shadowStyle = TextStyle(
        fontFamily = CalibriFontFamily,
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.5f),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
    )

    AnimatedVisibility(
        visible = showSideMenu,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.zIndex(1000f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            AnimatedVisibility(
                visible = showSideMenu,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .background(Color.White.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Konsumiert Klicks */ }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = 20.adaptiveDp())
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.padding(horizontal = 20.adaptiveDp(), vertical = 10.adaptiveDp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.bgsc_logo),
                                contentDescription = null,
                                modifier = Modifier.size(40.adaptiveDp())
                            )
                            Spacer(Modifier.width(12.adaptiveDp()))
                            @Suppress("SpellCheckingInspection")
                            Text(
                                text = "BGSC \"Gut Schlag\"\nGladbeck e.V.",
                                color = Color.Black,
                                fontSize = 18.adaptiveSp(),
                                lineHeight = 20.adaptiveSp(),
                                fontWeight = FontWeight.Bold,
                                style = shadowStyle
                            )
                        }

                        HorizontalDivider(
                            color = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 10.adaptiveDp())
                        )

                        // Menu Items
                        SideMenuItem(
                            icon = Icons.Default.Add,
                            text = "Spieler hinzufügen",
                            onClick = { if (playerCount < 10) onAddPlayerClick() },
                            contentColor = Color.Black
                        )
                        if (numRounds < 4) {
                            SideMenuItem(
                                icon = Icons.Default.AddCircleOutline,
                                text = "Nächste Runde",
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNextRoundClick()
                                },
                                contentColor = Color.Black
                            )
                        }
                        SideMenuItem(
                            icon = Icons.Default.Refresh,
                            text = "Neu starten",
                            onClick = {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRestartClick()
                            },
                            contentColor = Color.Black
                        )
                        SideMenuItem(
                            icon = Icons.Default.Stop,
                            text = "Spiel beenden",
                            onClick = {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEndGameClick()
                            },
                            contentColor = Color.Black
                        )

                        Spacer(Modifier.weight(1f))

                        if (isTurnierMode) {
                            SideMenuItem(
                                icon = Icons.Default.MilitaryTech,
                                text = "Turnier-Modus",
                                onClick = onTournamentClick,
                                contentColor = Color(0xFFD4AF37)
                            )
                        }
                        
                        SideMenuItem(
                            icon = Icons.Default.EmojiEvents,
                            text = "Ergebniskarte",
                            onClick = onShowResultsClick,
                            contentColor = Color.Black
                        )
                        SideMenuItem(
                            icon = Icons.Default.History,
                            text = "Historie",
                            onClick = onHistoryClick,
                            contentColor = Color.Black
                        )
                        
                        Spacer(Modifier.height(8.adaptiveDp()))

                        HorizontalDivider(
                            color = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 10.adaptiveDp())
                        )

                        // Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.adaptiveDp(), end = 20.adaptiveDp(), bottom = 10.adaptiveDp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Settings Button (Ganz links)
                            IconButton(
                                onClick = {
                                    sound.playClick()
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShowSettings()
                                },
                                modifier = Modifier.size(40.adaptiveDp())
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.2f),
                                        modifier = Modifier.offset(1.dp, 1.dp).size(26.adaptiveDp())
                                    )
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Einstellungen",
                                        tint = Color.Black,
                                        modifier = Modifier.size(26.adaptiveDp())
                                    )
                                }
                            }

                            // Copyright & Info (Rechts daneben zentriert)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !isTurnierMode
                                    ) {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastClickTime > 1000) { devClickCount = 1 } else { devClickCount++ }
                                        lastClickTime = currentTime
                                        if (devClickCount in 2..6) {
                                            val stepsLeft = 7 - devClickCount
                                            currentToast?.cancel()
                                            currentToast = Toast.makeText(context, "Noch $stepsLeft Schritte...", Toast.LENGTH_SHORT)
                                            currentToast?.show()
                                        } else if (devClickCount >= 7) {
                                            onTurnierModeToggle(true)
                                            currentToast?.cancel()
                                            Toast.makeText(context, "Turnier-Modus aktiviert!", Toast.LENGTH_SHORT).show()
                                            devClickCount = 0
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "© BGSC \"Gut Schlag\" Gladbeck e.V.",
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontSize = 10.adaptiveSp(),
                                    fontFamily = CalibriFontFamily
                                )
                                Text(
                                    text = "Created by Patrick",
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontSize = 10.adaptiveSp(),
                                    fontFamily = CalibriFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
