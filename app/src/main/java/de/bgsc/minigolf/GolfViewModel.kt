package de.bgsc.minigolf

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class GolfViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val gameRepository = GameRepository(database.gameResultDao())
    private val tournamentRepository = TournamentRepository(database.tournamentNoteDao(), application)
    private val prefs = application.getSharedPreferences("minigolf_prefs", android.content.Context.MODE_PRIVATE)
    private val updateManager = UpdateManager(application)

    val gameHistory: StateFlow<List<GameResult>> = gameRepository.allCompletedResults
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeGames: StateFlow<List<GameResult>> = gameRepository.allActiveResults
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tournamentHistory: StateFlow<List<TournamentNoteResult>> = tournamentRepository.allTournamentResults
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI States
    var currentScreen by mutableStateOf<Screen>(Screen.Main)
    
    var players by mutableStateOf(listOf(Player(application.getString(R.string.default_player_name, 1), Color.hsv(Random.nextFloat() * 360f, 0.8f, 0.6f))))
        private set

    var selectedSystem by mutableStateOf(application.getString(R.string.system_eternit_newline))
    var currentLocation by mutableStateOf("")
    
    // Tracking für das aktuell geladene Spiel
    var currentGameId by mutableStateOf<Long?>(null)
        private set

    // Settings
    var hapticEnabled by mutableStateOf(prefs.getBoolean("haptic_enabled", true))
    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", false))
    var soundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
    var fullScreenEnabled by mutableStateOf(prefs.getBoolean("full_screen_enabled", true))
    var isTurnierMode by mutableStateOf(prefs.getBoolean("turnier_mode", false))
    var tournamentTheme by mutableStateOf(
        TournamentTheme.entries.getOrElse(prefs.getInt("tournament_theme", TournamentTheme.SYSTEM.ordinal)) { TournamentTheme.SYSTEM }
    )

    // App Info
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: application.getString(R.string.error_unknown)
    } catch (_: Exception) {
        application.getString(R.string.error_unknown)
    }

    // Update States
    var updateAvailable by mutableStateOf<UpdateInfo?>(null)
    var isDownloadingUpdate by mutableStateOf(false)
    var downloadProgress by mutableFloatStateOf(0f)

    init {
        Log.i("GolfViewModel", "App gestartet. Version: $appVersion")
        checkForUpdates()
    }

    fun checkForUpdates(manual: Boolean = false, onFinished: ((String?) -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = updateManager.checkForUpdates(appVersion)) {
                is UpdateResult.NewVersion -> {
                    updateAvailable = UpdateInfo(result.version, result.url, result.notes)
                    onFinished?.invoke(null)
                }
                is UpdateResult.NoUpdate -> {
                    if (manual) onFinished?.invoke(getApplication<Application>().getString(R.string.update_up_to_date))
                }
                is UpdateResult.Error -> {
                    if (manual) onFinished?.invoke(getApplication<Application>().getString(R.string.update_error_check, result.message))
                }
            }
        }
    }

    fun startUpdate() {
        updateAvailable?.let { info ->
            isDownloadingUpdate = true
            viewModelScope.launch {
                updateManager.downloadAndInstallApk(info.downloadUrl) { progress ->
                    downloadProgress = progress
                }
                isDownloadingUpdate = false
            }
        }
    }

    // Tournament Data
    var tournamentNotes by mutableStateOf(List(18) { HoleNote() })
        private set
    var tournamentLocation by mutableStateOf("")
    var tournamentGameMode by mutableStateOf(application.getString(R.string.system_eternit))
    var currentTournamentNoteId by mutableStateOf<Long?>(null)
        private set

    fun onBackPressed() {
        when (currentScreen) {
            Screen.History -> currentScreen = Screen.Main
            Screen.ActiveGames -> currentScreen = Screen.Main
            Screen.TournamentSelection -> currentScreen = Screen.Main
            Screen.TournamentTable -> {
                currentScreen = if (currentTournamentNoteId != null) Screen.TournamentHistory else Screen.TournamentSelection
                resetTournamentNotes()
            }
            Screen.TournamentHistory -> currentScreen = Screen.TournamentSelection
            Screen.Main -> { }
        }
    }

    fun toggleHaptic(enabled: Boolean) {
        hapticEnabled = enabled
        prefs.edit { putBoolean("haptic_enabled", enabled) }
    }

    fun toggleSound(enabled: Boolean) {
        soundEnabled = enabled
        prefs.edit { putBoolean("sound_enabled", enabled) }
    }

    fun toggleKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled
        prefs.edit { putBoolean("keep_screen_on", enabled) }
    }

    fun toggleFullScreen(enabled: Boolean) {
        fullScreenEnabled = enabled
        prefs.edit { putBoolean("full_screen_enabled", enabled) }
    }

    fun toggleTurnierMode(enabled: Boolean) {
        isTurnierMode = enabled
        prefs.edit { putBoolean("turnier_mode", enabled) }
    }

    fun setTournamentDesign(theme: TournamentTheme) {
        tournamentTheme = theme
        prefs.edit { putInt("tournament_theme", theme.ordinal) }
    }

    // Tournament Export / Import (delegiert an Repository)
    fun exportTournamentNotes(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = tournamentRepository.exportNotes(uri)
            onResult(success)
        }
    }

    fun importTournamentNotes(uri: Uri, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val (success, count) = tournamentRepository.importNotes(uri)
            onResult(success, count)
        }
    }

    fun addPlayer(name: String, color: Color) {
        val currentRounds = players.firstOrNull()?.roundScores?.size ?: 1
        players = players + Player(name, color, List(currentRounds) { List(18) { null } })
    }

    fun updatePlayer(index: Int, name: String, color: Color) {
        val updated = players.toMutableList()
        updated[index] = players[index].copy(name = name, color = color)
        players = updated
    }

    fun removePlayer(index: Int) {
        if (players.size > 1) {
            val updated = players.toMutableList()
            updated.removeAt(index)
            players = updated
        }
    }

    fun movePlayer(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in players.indices || toIndex !in players.indices) return
        val updated = players.toMutableList()
        val player = updated.removeAt(fromIndex)
        updated.add(toIndex, player)
        players = updated
    }

    fun addRound() {
        players = players.map { it.copy(roundScores = it.roundScores + listOf(List(18) { null })) }
    }

    fun removeRound(roundIndex: Int) {
        if ((players.firstOrNull()?.roundScores?.size ?: 0) > 1) {
            players = players.map { it.copy(roundScores = it.roundScores.toMutableList().apply { removeAt(roundIndex) }) }
        }
    }

    fun updateScore(playerIndex: Int, roundIndex: Int, holeIndex: Int, score: Int?) {
        val updatedPlayers = players.toMutableList()
        val player = updatedPlayers[playerIndex]
        val updatedRounds = player.roundScores.toMutableList()
        val updatedScores = updatedRounds[roundIndex].toMutableList()
        updatedScores[holeIndex] = score
        updatedRounds[roundIndex] = updatedScores
        updatedPlayers[playerIndex] = player.copy(roundScores = updatedRounds)
        players = updatedPlayers
        
        saveGame(isCompleted = false)
    }

    fun updateTournamentNote(
        index: Int,
        ball: String,
        startPoint: String,
        notes: String,
        images: List<HoleImage>
    ) {
        val updated = tournamentNotes.toMutableList()
        updated[index] = HoleNote(ball, startPoint, notes, images)
        tournamentNotes = updated
    }

    fun loadTournamentNote(result: TournamentNoteResult) {
        currentTournamentNoteId = result.id
        tournamentLocation = result.location
        tournamentGameMode = result.system
        tournamentNotes = TournamentConverters().toHoleNoteList(result.notesJson)
        currentScreen = Screen.TournamentTable
    }

    fun resetTournamentNotes() {
        tournamentNotes = List(18) { HoleNote() }
        tournamentLocation = ""
        tournamentGameMode = getApplication<Application>().getString(R.string.system_eternit)
        currentTournamentNoteId = null
    }

    fun saveTournamentNotes() {
        val location = tournamentLocation
        val system = tournamentGameMode
        val notes = tournamentNotes
        val existingId = currentTournamentNoteId

        viewModelScope.launch {
            val result = TournamentNoteResult(
                id = existingId ?: 0L,
                date = System.currentTimeMillis(),
                location = location,
                system = system,
                notesJson = TournamentConverters().fromHoleNoteList(notes)
            )
            val newId = tournamentRepository.insert(result)
            if (existingId == null) {
                currentTournamentNoteId = newId
            }
        }
    }

    fun deleteTournamentNoteEntry(id: Long) {
        viewModelScope.launch {
            tournamentRepository.deleteById(id)
            if (currentTournamentNoteId == id) {
                currentTournamentNoteId = null
            }
        }
    }

    fun restartGame() {
        players = players.map { it.copy(roundScores = listOf(List(18) { null })) }
        currentGameId = null
    }

    fun resetAll() {
        players = listOf(Player(getApplication<Application>().getString(R.string.default_player_name, 1), Color.hsv(Random.nextFloat() * 360f, 0.8f, 0.6f)))
        currentLocation = ""
        currentGameId = null
        resetTournamentNotes()
    }

    fun loadActiveGame(result: GameResult) {
        currentGameId = result.id
        currentLocation = result.location
        selectedSystem = result.system
        
        val playerScores = Converters().toPlayerScoreList(result.playersJson)
        players = playerScores.map { ps ->
            Player(
                name = ps.name,
                color = Color(ps.colorInt),
                roundScores = ps.holeScores.ifEmpty { 
                    ps.rounds.map { List(18) { null } } 
                }
            )
        }
        currentScreen = Screen.Main
    }

    fun saveGame(isCompleted: Boolean = true) {
        val currentPlayers = players
        val system = selectedSystem
        val location = currentLocation
        val existingId = currentGameId
        
        val isFullGame = currentPlayers.isNotEmpty() && currentPlayers.all { p ->
            p.roundScores.all { rs -> rs.all { it != null } }
        }
        
        viewModelScope.launch {
            val playerScores = currentPlayers.map { player ->
                PlayerScore(
                    name = player.name,
                    colorInt = player.color.toArgb(),
                    totalScore = player.roundScores.flatten().filterNotNull().sum(),
                    rounds = player.roundScores.map { it.filterNotNull().sum() },
                    roundIsFull = player.roundScores.map { it.all { hole -> hole != null } },
                    holeScores = player.roundScores
                )
            }
            val result = GameResult(
                id = existingId ?: 0L,
                date = System.currentTimeMillis(),
                system = system,
                location = location,
                playersJson = Converters().fromPlayerScoreList(playerScores),
                isFullGame = isFullGame,
                isCompleted = isCompleted
            )
            val newId = gameRepository.insert(result)
            if (existingId == null) {
                currentGameId = newId
            }
            
            if (isCompleted) {
                currentGameId = null
            }
        }
    }

    fun completeGame(game: GameResult) {
        viewModelScope.launch {
            val completedGame = game.copy(isCompleted = true)
            gameRepository.insert(completedGame)
            
            // Fix: Falls das beendete Spiel das aktuell geladene ist, Haupttabelle leeren
            if (game.id == currentGameId) {
                resetAll()
            }
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            gameRepository.deleteById(id)
        }
    }

    fun saveByteArrayToInternalStorage(data: ByteArray, prefix: String = ""): String? {
        return tournamentRepository.saveByteArrayToInternalStorage(data, prefix)
    }
}
