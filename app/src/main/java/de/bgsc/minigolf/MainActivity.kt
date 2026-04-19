package de.bgsc.minigolf

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    private lateinit var golfViewModel: GolfViewModel

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
        
        updateLayoutInDisplayCutoutMode(true)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }

        setContent { 
            golfViewModel = viewModel()
            val isDarkTheme = isSystemInDarkTheme()
            
            var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

            val shadowStyle = remember { 
                TextStyle(
                    fontFamily = CalibriFontFamily,
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 3f)
                ) 
            }

            LaunchedEffect(golfViewModel.fullScreenEnabled, isDarkTheme) {
                updateLayoutInDisplayCutoutMode(golfViewModel.fullScreenEnabled)
                applySystemBarsVisibility(golfViewModel.fullScreenEnabled)
            }

            LaunchedEffect(Unit) {
                if (intent?.data != null) {
                    pendingImportUri = intent.data
                    intent.data = null // Verhindert Re-Import beim Drehen
                }
            }
            
            MiniGolfTheme { 
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { 
                    Box(modifier = Modifier.fillMaxSize()) {
                        MiniGolfApp(golfViewModel)

                        // Import-Bestätigungs-Dialog
                        pendingImportUri?.let { uri ->
                            val buttonShape = RoundedCornerShape(20.dp)
                            AlertDialog(
                                onDismissRequest = { pendingImportUri = null },
                                title = { Text("Notizen importieren?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
                                text = { Text("Möchtest du die Turniernotizen aus der gewählten Datei importieren?", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface)) },
                                confirmButton = {
                                    Button(
                                        onClick = golfClick {
                                            golfViewModel.importTournamentNotes(uri) { success, count ->
                                                if (success) {
                                                    Toast.makeText(this@MainActivity, "$count Notizen importiert!", Toast.LENGTH_SHORT).show()
                                                    golfViewModel.currentScreen = Screen.TournamentHistory
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Import fehlgeschlagen.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            pendingImportUri = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        shape = buttonShape,
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                                    ) {
                                        Text("Importieren", color = Color.White, fontWeight = FontWeight.Bold, style = shadowStyle.copy(color = Color.White))
                                    }
                                },
                                dismissButton = {
                                    Button(
                                        onClick = golfClick { pendingImportUri = null },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = buttonShape,
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                                    ) {
                                        Text("Abbrechen", color = MaterialTheme.colorScheme.onSurface, style = shadowStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                textContentColor = MaterialTheme.colorScheme.onSurface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(24.dp)
                            )
                        }

                        if (!golfViewModel.fullScreenEnabled) {
                            val scrimAlpha = if (isDarkTheme) 0.4f else 0.6f
                            val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = scrimAlpha) else Color.White.copy(alpha = scrimAlpha)

                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(scrimColor)
                                    .align(Alignment.TopCenter)
                                    .zIndex(10000f)
                            )

                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                    .background(scrimColor)
                                    .align(Alignment.BottomCenter)
                                    .zIndex(10000f)
                            )
                        }
                    }
                } 
            } 
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (uri != null) {
            golfViewModel.importTournamentNotes(uri) { success, count ->
                if (success) {
                    Toast.makeText(this, "$count Notizen importiert!", Toast.LENGTH_SHORT).show()
                    golfViewModel.currentScreen = Screen.TournamentHistory
                } else {
                    Toast.makeText(this, "Import fehlgeschlagen.", Toast.LENGTH_SHORT).show()
                }
            }
            intent.data = null
        }
    }

    private fun updateLayoutInDisplayCutoutMode(fullScreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutMode = if (fullScreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes.layoutInDisplayCutoutMode = layoutMode
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val prefs = getSharedPreferences("minigolf_prefs", MODE_PRIVATE)
            val fullScreen = prefs.getBoolean("full_screen_enabled", true)
            applySystemBarsVisibility(fullScreen)
        }
    }

    private fun applySystemBarsVisibility(fullScreen: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (fullScreen) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
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

    val activeGames by viewModel.activeGames.collectAsState()

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

    val isOverlayVisible = showSideMenu || selectedHolePlayerRound != null || editPlayerIndex != null ||
            showAddPlayerDialog || showWinnerDialog || showSettingsDialog || showInfoDialog ||
            viewModel.updateAvailable != null

    val currentBlurRadius by animateDpAsState(
        targetValue = if (isOverlayVisible) 12.dp else 0.dp,
        animationSpec = tween(300), label = "blur"
    )

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isOverlayVisible && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.45f else 0f,
        animationSpec = tween(300), label = "scrim"
    )

    ProvideSafeSound(soundEnabled = viewModel.soundEnabled) {
        ProvideSafeHaptic(hapticEnabled = viewModel.hapticEnabled) {
            val haptic = LocalHapticFeedback.current
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { 
                detectTapGestures(onTap = { 
                    focusManager.clearFocus() 
                }) 
            }) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        Image(painter = painterResource(id = R.drawable.bg_minigolf), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (!viewModel.fullScreenEnabled) Modifier.systemBarsPadding() else Modifier)
                                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(currentBlurRadius) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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

                        if (scrimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = scrimAlpha))
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
                                    onNewGame = { viewModel.saveGame(isCompleted = false); viewModel.restartGame(); showWinnerDialog = false },
                                    onShare = {
                                        val playerScores = players.map { p -> PlayerScore(p.name, p.color.toArgb(), p.roundScores.flatten().filterNotNull().sum(), p.roundScores.map { it.filterNotNull().sum() }, p.roundScores.map { it.all { s -> s != null } }, p.roundScores) }
                                        val bmp = generateBitmapFromData(context, playerScores, viewModel.selectedSystem, viewModel.currentLocation, System.currentTimeMillis())
                                        shareBitmap(context, bmp)
                                        showWinnerDialog = false
                                    },
                                    onNextRound = { viewModel.addRound(); showWinnerDialog = false },
                                    onResetAll = { viewModel.saveGame(true); viewModel.resetAll(); showWinnerDialog = false },
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
                                fullScreenEnabled = viewModel.fullScreenEnabled,
                                shadowStyle = shadowStyle,
                                onHapticToggle = { viewModel.toggleHaptic(it) },
                                onSoundToggle = { viewModel.toggleSound(it) },
                                onKeepScreenOnToggle = { viewModel.toggleKeepScreenOn(it) },
                                onFullScreenToggle = { viewModel.toggleFullScreen(it) },
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
                            activeGamesCount = activeGames.size,
                            hapticEnabled = viewModel.hapticEnabled,
                            fullScreenEnabled = viewModel.fullScreenEnabled,
                            isTurnierMode = viewModel.isTurnierMode,
                            onAddPlayerClick = { showAddPlayerDialog = true; showSideMenu = false },
                            onShowResultsClick = { showWinnerDialog = true; showSideMenu = false },
                            onHistoryClick = { viewModel.currentScreen = Screen.History; showSideMenu = false },
                            onActiveGamesClick = { viewModel.currentScreen = Screen.ActiveGames; showSideMenu = false },
                            onTournamentClick = { viewModel.currentScreen = Screen.TournamentSelection; showSideMenu = false },
                            onNextRoundClick = { viewModel.addRound(); showSideMenu = false },
                            onNewGameClick = { viewModel.saveGame(isCompleted = false); viewModel.restartGame(); showSideMenu = false },
                            onEndGameClick = { viewModel.saveGame(true); viewModel.resetAll(); showSideMenu = false },
                            onTurnierModeToggle = { viewModel.toggleTurnierMode(it) },
                            onShowSettings = { showSideMenu = false; showSettingsDialog = true }
                        )
                        flyingScore?.let { info -> FlyingScoreElement(info = info, shadowStyle = shadowStyle, onAnimationFinished = { viewModel.updateScore(info.playerIndex, info.roundIndex, info.holeIndex, info.score); flyingScore = null }) }
                    }
                }

                AnimatedVisibility(visible = viewModel.currentScreen == Screen.History, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                    HistoryScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, fullScreenEnabled = viewModel.fullScreenEnabled)
                }

                AnimatedVisibility(visible = viewModel.currentScreen == Screen.ActiveGames, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                    ActiveGamesScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, fullScreenEnabled = viewModel.fullScreenEnabled)
                }

                TournamentThemeWrapper(theme = viewModel.tournamentTheme) {
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentSelection, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentSelectionScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onNewNote = { viewModel.resetTournamentNotes(); viewModel.currentScreen = Screen.TournamentTable }, onShowHistory = { viewModel.currentScreen = Screen.TournamentHistory }, fullScreenEnabled = viewModel.fullScreenEnabled)
                    }
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentTable, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onSaveFinished = { viewModel.onBackPressed() }, fullScreenEnabled = viewModel.fullScreenEnabled)
                    }
                    AnimatedVisibility(visible = viewModel.currentScreen == Screen.TournamentHistory, enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(), exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) {
                        TournamentHistoryScreen(viewModel = viewModel, onBack = { viewModel.onBackPressed() }, onEdit = { result -> viewModel.loadTournamentNote(result) }, fullScreenEnabled = viewModel.fullScreenEnabled)
                    }
                }
            }
        }
    }
}

@Composable
fun FlyingScoreElement(info: FlyingScoreInfo, shadowStyle: TextStyle, onAnimationFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(info) { progress.animateTo(targetValue = 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)); onAnimationFinished() }
    val currentX = info.start.x + (info.end.x - info.start.x) * progress.value
    val currentY = info.start.y + (info.end.y - info.start.y) * progress.value
    val halfSizePx = with(density) { 20.dp.toPx() }
    Box(modifier = Modifier.offset { IntOffset((currentX - halfSizePx).roundToInt(), (currentY - halfSizePx).roundToInt()) }.size(40.dp).alpha(1f - 0.2f * progress.value).scale(1.4f - 0.4f * progress.value).zIndex(2000f), contentAlignment = Alignment.Center) {
        Text(text = info.score?.toString() ?: "", style = shadowStyle.copy(color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold))
    }
}
