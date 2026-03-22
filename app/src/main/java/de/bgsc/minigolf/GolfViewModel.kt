package de.bgsc.minigolf

import android.app.Application
import android.content.Context
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
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

data class HoleNote(
    @SerializedName("ball") val ball: String = "",
    @SerializedName("startPoint") val startPoint: String = "",
    @SerializedName("notes") val notes: String = ""
)

data class TournamentExportWrapper(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("appIdentifier") val appIdentifier: String = "BGSC_Punktekarte",
    @SerializedName("exportDate") val exportDate: Long = System.currentTimeMillis(),
    @SerializedName("notes") val notes: List<TournamentNoteResult>
)

enum class TournamentTheme {
    LIGHT, DARK, SYSTEM
}

sealed class Screen {
    data object Main : Screen()
    data object History : Screen()
    data object TournamentSelection : Screen()
    data object TournamentTable : Screen()
    data object TournamentHistory : Screen()
}

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String?
)

class GolfViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.gameResultDao()
    private val tournamentNoteDao = database.tournamentNoteDao()
    private val prefs = application.getSharedPreferences("minigolf_prefs", Context.MODE_PRIVATE)
    private val updateManager = UpdateManager(application)
    
    val gameHistory: StateFlow<List<GameResult>> = dao.getAllResults()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tournamentHistory: StateFlow<List<TournamentNoteResult>> = tournamentNoteDao.getAllResults()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI States
    var currentScreen by mutableStateOf<Screen>(Screen.Main)
    
    var players by mutableStateOf(listOf(Player("Spieler 1", Color.hsv(Random.nextFloat() * 360f, 0.8f, 0.6f))))
        private set

    var selectedSystem by mutableStateOf("Miniaturgolf\n(Eternit)")
    var currentLocation by mutableStateOf("")
    
    // Settings
    var hapticEnabled by mutableStateOf(prefs.getBoolean("haptic_enabled", true))
    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", false))
    var soundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
    var isTurnierMode by mutableStateOf(prefs.getBoolean("turnier_mode", false))
    var tournamentTheme by mutableStateOf(
        TournamentTheme.entries.getOrElse(prefs.getInt("tournament_theme", TournamentTheme.SYSTEM.ordinal)) { TournamentTheme.SYSTEM }
    )

    // App Info - Holt sich die Version sicher vom System, ohne BuildConfig zu benötigen
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateManager.checkForUpdates(
                    appVersion,
                    onUpdateAvailable = { latest, url, notes ->
                        viewModelScope.launch(Dispatchers.Main) {
                            updateAvailable = UpdateInfo(latest, url, notes)
                            onFinished?.invoke(null)
                        }
                    },
                    onNoUpdate = {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (manual) onFinished?.invoke("App ist auf dem neuesten Stand.")
                        }
                    },
                    onError = { error ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (manual) onFinished?.invoke("Fehler beim Update-Check: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("GolfViewModel", "Fehler im Update-Check Prozess", e)
            }
        }
    }

    fun startUpdate() {
        updateAvailable?.let { info ->
            isDownloadingUpdate = true
            updateManager.downloadAndInstallApk(info.downloadUrl) { progress ->
                viewModelScope.launch(Dispatchers.Main) {
                    downloadProgress = progress
                }
            }
        }
    }

    // Tournament Data
    var tournamentNotes by mutableStateOf(List(18) { HoleNote() })
        private set
    var tournamentLocation by mutableStateOf("")
    var tournamentGameMode by mutableStateOf("Miniaturgolf (Eternit)")
    var currentTournamentNoteId by mutableStateOf<Long?>(null)
        private set

    fun onBackPressed() {
        when (currentScreen) {
            Screen.History -> currentScreen = Screen.Main
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

    fun toggleTurnierMode(enabled: Boolean) {
        isTurnierMode = enabled
        prefs.edit { putBoolean("turnier_mode", enabled) }
    }

    fun setTournamentDesign(theme: TournamentTheme) {
        tournamentTheme = theme
        prefs.edit { putInt("tournament_theme", theme.ordinal) }
    }

    // Tournament Export / Import
    fun exportTournamentNotes(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = tournamentNoteDao.getAllResults().first()
                val wrapper = TournamentExportWrapper(notes = results)
                val json = Gson().toJson(wrapper)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    GZIPOutputStream(outputStream).use { gzip ->
                        gzip.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                withContext(Dispatchers.Main) { onResult(true) }
            } catch (e: Exception) {
                Log.e("GolfViewModel", "Export fehlgeschlagen", e)
                withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun importTournamentNotes(context: Context, uri: Uri, onResult: (Boolean, Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var json = ""
                context.contentResolver.openInputStream(uri)?.use { testStream ->
                    if (isGzipped(testStream)) {
                        context.contentResolver.openInputStream(uri)?.use { actualStream ->
                            json = GZIPInputStream(actualStream).bufferedReader(Charsets.UTF_8).readText()
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { actualStream ->
                            json = actualStream.bufferedReader(Charsets.UTF_8).readText()
                        }
                    }
                }

                if (json.isBlank()) throw Exception("Datei leer")

                val jsonElement = JsonParser.parseString(json)
                val notesToImport = mutableListOf<JsonObject>()

                if (jsonElement.isJsonObject) {
                    val obj = jsonElement.asJsonObject
                    if (obj.has("notes") && obj.get("notes").isJsonArray) {
                        val array = obj.getAsJsonArray("notes")
                        for (i in 0 until array.size()) {
                            if (array.get(i).isJsonObject) notesToImport.add(array.get(i).asJsonObject)
                        }
                    } else {
                        notesToImport.add(obj)
                    }
                } else if (jsonElement.isJsonArray) {
                    val array = jsonElement.asJsonArray
                    for (i in 0 until array.size()) {
                        if (array.get(i).isJsonObject) notesToImport.add(array.get(i).asJsonObject)
                    }
                }

                if (notesToImport.isEmpty()) throw Exception("Keine Notizen gefunden")

                var importedCount = 0
                notesToImport.forEach { noteObj ->
                    try {
                        val location = findField(noteObj, "location", "c") 
                        val system = findField(noteObj, "system", "d")
                        val notesJson = findField(noteObj, "notesJson", "e")
                        
                        val date = when {
                            noteObj.has("date") -> noteObj.get("date").asLong
                            noteObj.has("b") -> noteObj.get("b").asLong
                            noteObj.has("timestamp") -> noteObj.get("timestamp").asLong
                            else -> System.currentTimeMillis()
                        }

                        val cleanNote = TournamentNoteResult(
                            id = 0,
                            date = date,
                            location = location,
                            system = system,
                            notesJson = notesJson
                        )
                        tournamentNoteDao.insert(cleanNote)
                        importedCount++
                    } catch (e: Exception) {
                        Log.e("GolfViewModel", "Eintrag fehlerhaft", e)
                    }
                }
                withContext(Dispatchers.Main) { onResult(true, importedCount) }
            } catch (e: Exception) {
                Log.e("GolfViewModel", "Import fehlgeschlagen: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult(false, 0) }
            }
        }
    }

    private fun findField(obj: JsonObject, originalName: String, proguardName: String): String {
        return when {
            obj.has(originalName) -> {
                val el = obj.get(originalName)
                if (el.isJsonPrimitive) el.asString else el.toString()
            }
            obj.has(proguardName) -> {
                val el = obj.get(proguardName)
                if (el.isJsonPrimitive) el.asString else el.toString()
            }
            else -> ""
        }
    }

    private fun isGzipped(inputStream: InputStream): Boolean {
        return try {
            val signature = ByteArray(2)
            val read = inputStream.read(signature)
            if (read != 2) return false
            val head = (signature[0].toInt() and 0xff) or ((signature[1].toInt() and 0xff) shl 8)
            head == GZIPInputStream.GZIP_MAGIC
        } catch (_: Exception) {
            false
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
    }

    fun updateTournamentNote(index: Int, ball: String, startPoint: String, notes: String) {
        val updated = tournamentNotes.toMutableList()
        updated[index] = HoleNote(ball, startPoint, notes)
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
        tournamentGameMode = "Miniaturgolf (Eternit)"
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
            val newId = tournamentNoteDao.insert(result)
            if (existingId == null) {
                currentTournamentNoteId = newId
            }
        }
    }

    fun deleteTournamentNoteEntry(id: Long) {
        viewModelScope.launch {
            tournamentNoteDao.deleteById(id)
            if (currentTournamentNoteId == id) {
                currentTournamentNoteId = null
            }
        }
    }

    fun restartGame() {
        players = players.map { it.copy(roundScores = listOf(List(18) { null })) }
    }

    fun resetAll() {
        players = listOf(Player("Spieler 1", Color.hsv(Random.nextFloat() * 360f, 0.8f, 0.6f)))
        currentLocation = ""
        resetTournamentNotes()
    }

    fun saveGame() {
        val currentPlayers = players
        val system = selectedSystem
        val location = currentLocation
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
                date = System.currentTimeMillis(),
                system = system,
                location = location,
                playersJson = Converters().fromPlayerScoreList(playerScores),
                isFullGame = isFullGame
            )
            dao.insert(result)
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }
}
