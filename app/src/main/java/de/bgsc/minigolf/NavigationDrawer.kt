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
import androidx.compose.ui.res.stringResource
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
    activeGamesCount: Int,
    hapticEnabled: Boolean,
    fullScreenEnabled: Boolean,
    isTurnierMode: Boolean,
    onAddPlayerClick: () -> Unit,
    onShowResultsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onActiveGamesClick: () -> Unit,
    onTournamentClick: () -> Unit,
    onNextRoundClick: () -> Unit,
    onNewGameClick: () -> Unit,
    onEndGameClick: () -> Unit,
    onTurnierModeToggle: (Boolean) -> Unit,
    onShowSettings: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val sound = LocalSoundFeedback.current
    var devClickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    
    // Referenz auf den aktuellen Toast, um ihn abbrechen zu können
    var currentToast by remember { mutableStateOf<Toast?>(null) }
    
    // Texte für das Easter Egg
    val easterEggStepsText = stringResource(R.string.tournament_easter_egg_steps)
    val easterEggActivatedText = stringResource(R.string.tournament_easter_egg_activated)

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
                            .then(if (fullScreenEnabled) Modifier.displayCutoutPadding() else Modifier.systemBarsPadding())
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
                            Text(
                                text = stringResource(R.string.drawer_club_name),
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
                            text = stringResource(R.string.menu_add_player),
                            onClick = { if (playerCount < 10) onAddPlayerClick() },
                            contentColor = Color.Black
                        )

                        if (numRounds < 4) {
                            SideMenuItem(
                                icon = Icons.Default.AddCircleOutline,
                                text = stringResource(R.string.menu_next_round),
                                onClick = {
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNextRoundClick()
                                },
                                contentColor = Color.Black
                            )
                        }
                        SideMenuItem(
                            icon = Icons.Default.AddCircle,
                            text = stringResource(R.string.menu_new_game),
                            onClick = {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNewGameClick()
                            },
                            contentColor = Color.Black
                        )
                        SideMenuItem(
                            icon = Icons.Default.Stop,
                            text = stringResource(R.string.menu_end_game),
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
                                text = stringResource(R.string.menu_tournament_mode),
                                onClick = onTournamentClick,
                                contentColor = Color(0xFFD4AF37)
                            )
                        }
                        
                        SideMenuItem(
                            icon = Icons.Default.EmojiEvents,
                            text = stringResource(R.string.menu_results_card),
                            onClick = onShowResultsClick,
                            contentColor = Color.Black
                        )
                        
                        SideMenuItem(
                            icon = Icons.Default.PlayCircleOutline,
                            text = stringResource(R.string.menu_active_games),
                            badge = if (activeGamesCount > 0) activeGamesCount.toString() else null,
                            onClick = onActiveGamesClick,
                            contentColor = Color.Black
                        )

                        SideMenuItem(
                            icon = Icons.Default.History,
                            text = stringResource(R.string.menu_finished_games),
                            onClick = onHistoryClick,
                            contentColor = Color.Black
                        )
                        
                        Spacer(Modifier.height(8.adaptiveDp()))

                        HorizontalDivider(
                            color = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 10.adaptiveDp())
                        )

                        // Footer (Settings & Info)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.adaptiveDp(), end = 20.adaptiveDp(), bottom = 10.adaptiveDp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                        contentDescription = stringResource(R.string.settings_icon_desc),
                                        tint = Color.Black,
                                        modifier = Modifier.size(26.adaptiveDp())
                                    )
                                }
                            }

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
                                        
                                        if (devClickCount >= 7) {
                                            onTurnierModeToggle(true)
                                            currentToast?.cancel()
                                            currentToast = Toast.makeText(context, easterEggActivatedText, Toast.LENGTH_SHORT)
                                            currentToast?.show()
                                            devClickCount = 0
                                        } else if (devClickCount >= 3) {
                                            val remaining = 7 - devClickCount
                                            currentToast?.cancel()
                                            currentToast = Toast.makeText(context, easterEggStepsText.format(remaining), Toast.LENGTH_SHORT)
                                            currentToast?.show()
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.drawer_copyright),
                                    color = Color.Black.copy(alpha = 0.4f),
                                    fontSize = 10.adaptiveSp(),
                                    fontFamily = CalibriFontFamily
                                )
                                Text(
                                    text = stringResource(R.string.drawer_credits),
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
