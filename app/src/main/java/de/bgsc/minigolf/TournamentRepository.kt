package de.bgsc.minigolf

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

class TournamentRepository(
    private val tournamentNoteDao: TournamentNoteDao,
    private val context: Context
) {
    private val converters = TournamentConverters()

    val allTournamentResults: Flow<List<TournamentNoteResult>> = tournamentNoteDao.getAllResults()

    suspend fun insert(result: TournamentNoteResult): Long {
        return tournamentNoteDao.insert(result)
    }

    suspend fun deleteById(id: Long) {
        tournamentNoteDao.deleteById(id)
    }

    suspend fun exportNotes(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val results = tournamentNoteDao.getAllResults().first()
            
            val resultsWithImages = results.map { result ->
                val holeNotes = converters.toHoleNoteList(result.notesJson)
                val updatedHoleNotes = holeNotes.map { note ->
                    val imagesWithData = note.getAllImages().map { img ->
                        val file = File(img.imagePath)
                        if (file.exists()) {
                            try {
                                val bytes = file.readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                                img.copy(imageData = base64)
                            } catch (e: Exception) {
                                Log.e("TournamentRepository", "Bild konnte nicht für Export gelesen werden", e)
                                img
                            }
                        } else {
                            img
                        }
                    }
                    note.copy(images = imagesWithData, legacyImagePath = null, legacyOriginalImagePath = null)
                }
                result.copy(notesJson = converters.fromHoleNoteList(updatedHoleNotes))
            }

            val wrapper = TournamentExportWrapper(notes = resultsWithImages)
            val json = Gson().toJson(wrapper)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                GZIPOutputStream(outputStream).use { gzip ->
                    gzip.write(json.toByteArray(Charsets.UTF_8))
                }
            }
            true
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Export fehlgeschlagen", e)
            false
        }
    }

    suspend fun importNotes(uri: Uri): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
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
                    var notesJson = findField(noteObj, "notesJson", "e")
                    
                    if (notesJson.isNotBlank()) {
                        val holeNotes = converters.toHoleNoteList(notesJson)
                        val updatedHoleNotes = holeNotes.map { holeNote ->
                            val updatedImages = holeNote.getAllImages().mapNotNull { holeImage ->
                                if (!holeImage.imageData.isNullOrBlank()) {
                                    try {
                                        val data = Base64.decode(holeImage.imageData, Base64.DEFAULT)
                                        val newPath = saveByteArrayToInternalStorage(data, prefix = "import_${Random.nextInt(10000)}_")
                                        if (newPath != null) {
                                            holeImage.copy(imagePath = newPath, originalImagePath = newPath, imageData = null)
                                        } else null
                                    } catch (e: Exception) {
                                        Log.e("TournamentRepository", "Bild-Dekodierung fehlgeschlagen", e)
                                        null
                                    }
                                } else null
                            }
                            holeNote.copy(images = updatedImages)
                        }
                        notesJson = converters.fromHoleNoteList(updatedHoleNotes)
                    }

                    val date = when {
                        noteObj.has("date") -> noteObj.get("date").asLong
                        noteObj.has("b") -> noteObj.get("b").asLong
                        noteObj.has("timestamp") -> noteObj.get("timestamp").asLong
                        else -> System.currentTimeMillis()
                    }

                    val cleanNote = TournamentNoteResult(
                        id = 0, date = date, location = location, system = system, notesJson = notesJson
                    )
                    tournamentNoteDao.insert(cleanNote)
                    importedCount++
                } catch (e: Exception) {
                    Log.e("TournamentRepository", "Eintrag fehlerhaft", e)
                }
            }
            true to importedCount
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Import fehlgeschlagen: ${e.message}", e)
            false to 0
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

    fun saveByteArrayToInternalStorage(data: ByteArray, prefix: String = ""): String? {
        val fileName = "${prefix}img_${System.currentTimeMillis()}.jpg"
        return try {
            val file = File(context.filesDir, fileName)
            file.writeBytes(data)
            file.absolutePath
        } catch (e: Exception) {
            Log.e("TournamentRepository", "Bild konnte nicht gespeichert werden", e)
            null
        }
    }
}
