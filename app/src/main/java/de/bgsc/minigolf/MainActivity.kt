package de.bgsc.minigolf

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        enableEdgeToEdge()
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        
        setContent { 
            val viewModel: GolfViewModel = viewModel()
            val context = LocalContext.current
            
            CompositionLocalProvider(
                LocalContext provides LanguageHelper.setLocale(context, viewModel.currentLanguage)
            ) {
                MiniGolfTheme { 
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { 
                        MiniGolfApp(viewModel) 
                    } 
                }
            }
        }
    }
}

data class FlyingScoreInfo(val score: Int?, val start: Offset, val end: Offset, val playerIndex: Int, val roundIndex: Int, val holeIndex: Int)

@Composable
fun MiniGolfApp(viewModel: GolfViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current

    var selectedHolePlayerRound by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    var editPlayerIndex by remember { mutableStateOf<Int?>(null) }
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var showWinnerDialog by remember { mutableStateOf(false) }
    var showSideMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var flyingScore by remember { mutableStateOf<FlyingScoreInfo?>(null) }

    if (viewModel.currentScreen != Screen.Main || showSideMenu) {
        BackHandler { if (showSideMenu) showSideMenu = false else viewModel.onBackPressed() }
    }

    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val titleBarHeight = screenHeight * 0.08f
    val dynamicSystemFontSize = (titleBarHeight.value * 0.22f).sp
    val logoSize = titleBarHeight * 0.7f

    val players = viewModel.players
    val numRounds = remember(players) { players.firstOrNull()?.roundScores?.size ?: 1 }
    val allFilled = remember(players) { players.isNotEmpty() && players.all { p -> p.roundScores.all { rs -> rs.all { it != null } } } }

    LaunchedEffect(allFilled) { if (allFilled) { delay(800); showWinnerDialog = true } }
    
    LaunchedEffect(viewModel.keepScreenOn) {
        (context as? Activity)?.window?.let { window ->
            if (viewModel.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val shadowStyle = remember { 
        TextStyle(
            fontFamily = CalibriFontFamily,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 3f)
        ) 
    }
    val highlightAmber = Color(0xFFFFB300)
    val highlightGold = Color(0xFFFFD54F)

    val currentBlurRadius by animateDpAsState(
        targetValue = if (showSideMenu || showWinnerDialog || editPlayerIndex != null || showAddPlayerDialog || showSettingsDialog || showInfoDialog || viewModel.currentScreen != Screen.Main || viewModel.updateAvailable != null) 15.dp else 0.dp,
        animationSpec = tween(300), label = "blur"
    )

    ProvideSafeSound(soundEnabled = viewModel.soundEnabled) {
        ProvideSafeHaptic(hapticEnabled = viewModel.hapticEnabled) {
            val haptic = LocalHapticFeedback.current
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentBlurRadius > 0.dp) Modifier.blur(currentBlurRadius) else Modifier).clipToBounds()) {
                        Image(painter = painterResource(id = R.drawable.bg_minigolf), contentDescription = null, modifier = Modifier.fillMaxSize().blur(15.dp), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TopAppBar(
                                selectedSystem = viewModel.selectedSystem, onSystemSelected = { viewModel.selectedSystem = it },
                                currentLocation = viewModel.currentLocation, onLocationChanged = { viewModel.currentLocation = it },
                                onLogoClick = { showSideMenu = true }, titleBarHeight = titleBarHeight, logoSize = logoSize,
                                dynamicSystemFontSize = dynamicSystemFontSize, shadowStyle = shadowStyle
                            )
                            ScoreTable(
                                players = players, numRounds = numRounds, selectedSystem = viewModel.selectedSystem,
                                selectedHolePlayerRound = selectedHolePlayerRound,
                                onUpdateScore = { pIdx, rIdx, hIdx, offset -> tapOffset = offset; selectedHolePlayerRound = Triple(pIdx, hIdx, rIdx) },
                                onPlayerClick = { editPlayerIndex = it }, onAddPlayerClick = { showAddPlayerDialog = true },
                                onMovePlayer = { from, to -> viewModel.movePlayer(from, to) }, onRemoveRound = { viewModel.removeRound(it) },
                                onAddRound = { viewModel.addRound() }, shadowStyle = shadowStyle,
                                highlightAmber = highlightAmber, highlightGold = highlightGold, modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!showSideMenu && viewModel.currentScreen == Screen.Main) {
                            Box(modifier = Modifier.fillMaxHeight().width(30.dp).zIndex(1001f).pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount -> if (dragAmount > 10f) { showSideMenu = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
                            })
                        }
                        
                        viewModel.updateAvailable?.let { info ->
                            AlertDialog(
                                onDismissRequest = { if (!viewModel.isDownloadingUpdate) viewModel.updateAvailable = null },
                                title = { Text(stringResource(R.string.update_available_title), style = shadowStyle.copy(fontWeight = FontWeight.Bold)) },
                                text = {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        Text(stringResource(R.string.update_available_message, info.version), style = shadowStyle)
                                        if (!info.releaseNotes.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(stringResource(R.string.update_changes_label), fontWeight = FontWeight.Bold, style = shadowStyle.copy(fontSize = 12.sp))
                                            Text(info.releaseNotes, style = shadowStyle.copy(fontSize = 12.sp))
                                        }
                                        if (viewModel.isDownloadingUpdate) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            LinearProgressIndicator(progress = { viewModel.downloadProgress }, modifier = Modifier.fillMaxWidth())
                                            Text(stringResource(R.string.percentage_format, (viewModel.downloadProgress * 100).toInt()), modifier = Modifier.align(Alignment.End), style = shadowStyle.copy(fontSize = 11.sp))
                                        }
                                    }
                                },
                                confirmButton = {
                                    if (!viewModel.isDownloadingUpdate) {
                                        Button(onClick = golfClick { viewModel.startUpdate() }) { Text(stringResource(R.string.update_download_now), style = shadowStyle) }
                                    }
                                },
                                dismissButton = {
                                    if (!viewModel.isDownloadingUpdate) {
                                        TextButton(onClick = golfClick { viewModel.updateAvailable = null }) { Text(stringResource(R.string.update_download_later), style = shadowStyle) }
                                    }
                                }
                            )
                        }

                        if (showWinnerDialog) {
                            Dialog(onDismissRequest = { showWinnerDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                                WinnerCard(
                                    allPlayers = players, selectedSystem = viewModel.selectedSystem, canAddRound = numRounds < 4,
                                    onRestart = { viewModel.saveGame(); viewModel.restartGame(); showWinnerDialog = false },
                                    onShare = { 
                                        val playerScores = players.map { p -> PlayerScore(p.name, p.color.toArgb(), p.roundScores.flatten().filterNotNull().sum(), p.roundScores.map { it.filterNotNull().sum() }, p.roundScores.map { it.all { s -> s != null } }, p.roundScores) }
                                        val bmp = generateBitmapFromData(context, playerScores, viewModel.selectedSystem, viewModel.currentLocation, System.currentTimeMillis())
                                        shareBitmap(context, bmp)
                                        showWinnerDialog = false 
                                    },
                                    onNextRound = { viewModel.addRound(); showWinnerDialog = false },
                                    onResetAll = { viewModel.saveGame(); viewModel.resetAll(); showWinnerDialog = false },
                                    onDismiss = { showWinnerDialog = false })
                            }
                        }
                        if (selectedHolePlayerRound != null) {
                            val (pIdx, hIdx, rIdx) = selectedHolePlayerRound!!
                            ScoreInputDialog(currentScore = players[pIdx].roundScores[rIdx][hIdx], offset = tapOffset, onDismiss = { selectedHolePlayerRound = null }, onScoreSelected = { score, btnOffset -> flyingScore = FlyingScoreInfo(score, btnOffset, tapOffset, pIdx, rIdx, hIdx); selectedHolePlayerRound = null })
                        }
                        if (editPlayerIndex != null) {
                            EditPlayerDialog(player = players[editPlayerIndex!!], shadowStyle = shadowStyle, onDismiss = { editPlayerIndex = null }, onSave = { name, color -> viewModel.updatePlayer(editPlayerIndex!!, name, color); editPlayerIndex = null }, onRemove = { viewModel.removePlayer(editPlayerIndex!!); editPlayerIndex = null }, canRemove = players.size > 1)
                        }
                        if (showAddPlayerDialog) {
                            AddPlayerDialog(playerCount = players.size, shadowStyle = shadowStyle, onDismiss = { showAddPlayerDialog = false }, onAdd = { name, color -> viewModel.addPlayer(name, color); showAddPlayerDialog = false })
                        }
                        if (showSettingsDialog) {
                            AppSettingsDialog(
                                hapticEnabled = viewModel.hapticEnabled,
                                soundEnabled = viewModel.soundEnabled,
                                keepScreenOn = viewModel.keepScreenOn,
                                currentLanguage = viewModel.currentLanguage,
                                shadowStyle = shadowStyle,
                                onHapticToggle = { viewModel.toggleHaptic(it) },
                                onSoundToggle = { viewModel.toggleSound(it) },
                                onKeepScreenOnToggle = { viewModel.toggleKeepScreenOn(it) },
                                onLanguageChange = { viewModel.setLanguage(it) },
                                onDismiss = { showSettingsDialog = false },
                                onShowInfo = { showSettingsDialog = false; showInfoDialog = true }
                            )
                        }
                        if (showInfoDialog) {
                            AppInfoDialog(
                                appVersion = viewModel.appVersion,
                                shadowStyle = shadowStyle, 
                                onDismiss = { showInfoDialog = false }
                            )
                        }
                        NavigationDrawer(
                            showSideMenu = showSideMenu, 
                            onDismiss = { showSideMenu = false }, 
                            playerCount = players.size, 
                            numRounds = numRounds, 
                            hapticEnabled = viewModel.hapticEnabled, 
                            isTurnierMode = viewModel.isTurnierMode,
                            onAddPlayerClick = { showAddPlayerDialog = true; showSideMenu = false }, 
                            onShowResultsClick = { showWinnerDialog = true; showSideMenu = false }, 
                            onHistoryClick = { viewModel.currentScreen = Screen.History; showSideMenu = false }, 
                            onTournamentClick = { viewModel.currentScreen = Screen.TournamentSelection; showSideMenu = false }, 
                            onNextRoundClick = { viewModel.addRound(); showSideMenu = false }, 
                            onRestartClick = { viewModel.saveGame(); viewModel.restartGame(); showSideMenu = false }, 
                            onEndGameClick = { viewModel.saveGame(); viewModel.resetAll(); showSideMenu = false },
                            onTurnierModeToggle = { viewModel.toggleTurnierMode(it) },
                            onShowSettings = { showSideMenu = false; showSettingsDialog = true }
                        )
                        flyingScore?.let { info -> FlyingScoreElement(info = info, shadowStyle = shadowStyle, onAnimationFinished = { viewModel.updateScore(info.playerIndex, info.roundIndex, info.holeIndex, info.score); flyingScore = null }) }
                    }
                }

                AnimatedVisibility(visible = viewModel.currentScreen == Screen.History, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                    HistoryScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() })
                }

                TournamentThemeWrapper(theme = viewModel.tournamentTheme) {
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentSelection, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentSelectionScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onNewNote = { viewModel.resetTournamentNotes(); viewModel.currentScreen = Screen.TournamentTable }, onShowHistory = { viewModel.currentScreen = Screen.TournamentHistory })
                    }
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentTable, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onSaveFinished = { viewModel.onBackPressed() })
                    }
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentHistory, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentHistoryScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onEdit = { result -> viewModel.loadTournamentNote(result) })
                    }
                }
            }
        }
    }
}

@Composable
fun FlyingScoreElement(info: FlyingScoreInfo, shadowStyle: TextStyle, onAnimationFinished: () -> Unit) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(info) { progress.animateTo(targetValue = 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)); onAnimationFinished() }
    val currentX = info.start.x + (info.end.x - info.start.x) * progress.value
    val currentY = info.start.y + (info.end.y - info.start.y) * progress.value
    val halfSizePx = with(density) { 20.dp.toPx() }
    Box(modifier = Modifier.offset { IntOffset((currentX - halfSizePx).roundToInt(), (currentY - halfSizePx).roundToInt()) }.size(40.dp).alpha(1f - 0.2f * progress.value).scale(1.4f - 0.4f * progress.value).zIndex(2000f), contentAlignment = Alignment.Center) {
        Text(text = info.score?.toString() ?: "", style = shadowStyle.copy(color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
    }
}
