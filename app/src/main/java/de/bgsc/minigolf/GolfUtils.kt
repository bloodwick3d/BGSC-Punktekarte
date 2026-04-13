package de.bgsc.minigolf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import java.util.zip.GZIPOutputStream

/**
 * Berechnet die Farbe basierend auf dem Score.
 * Unterstützt Live-Prognose, wenn playedHoles > 0 übergeben wird.
 */
fun getScoreColor(
    total: Int,
    system: String,
    defaultColor: Color,
    rounds: Int = 1,
    playedHoles: Int = 0
): Color {
    if (rounds <= 0) return defaultColor

    // Falls live gespielt wird, berechnen wir die Prognose (Basis 18 + Fehler)
    val effectiveScore = if (playedHoles > 0) {
        val totalPlannedHoles = rounds * 18
        val misses = total - playedHoles
        totalPlannedHoles + misses
    } else {
        total
    }

    val average = effectiveScore.toFloat() / rounds

    return when {
        system.contains("Eternit") -> {
            when {
                average < 18f -> defaultColor
                average < 20f -> Color(0xFF2196F3) // Blau
                average < 25f -> Color(0xFF4CAF50) // Grün
                average < 30f -> Color(0xFFF44336) // Rot
                else -> Color.Black                // Schwarz
            }
        }
        system.contains("Beton") -> {
            when {
                average < 18f -> defaultColor
                average < 25f -> Color(0xFF2196F3) // Blau
                average < 30f -> Color(0xFF4CAF50) // Grün
                average < 36f -> Color(0xFFF44336) // Rot
                else -> Color.Black                // Schwarz
            }
        }
        else -> { // Filzgolf, Cobigolf und Sterngolf
            when {
                average < 18f -> defaultColor
                average < 30f -> Color(0xFF2196F3) // Blau
                average < 36f -> Color(0xFF4CAF50) // Grün
                average < 40f -> Color(0xFFF44336) // Rot
                else -> Color.Black                // Schwarz
            }
        }
    }
}

/**
 * Teilt ein GameResult, indem es zuerst das Bitmap generiert und dann den Share-Intent startet.
 */
fun shareGameResult(context: Context, result: GameResult) {
    val bitmap = generateResultBitmap(context, result)
    shareBitmap(context, bitmap)
}

fun shareBitmap(context: Context, bitmap: Bitmap) {
    val cachePath = File(context.cacheDir, "images")
    if (!cachePath.exists()) cachePath.mkdirs()
    val imageFile = File(cachePath, "score_table.png")
    val stream = FileOutputStream(imageFile)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.close()
    
    // Dynamische Authority-ID nutzen (funktioniert für Debug und Release)
    val authority = "${context.packageName}.fileprovider"
    val contentUri: Uri = FileProvider.getUriForFile(context, authority, imageFile)
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, contentUri)
        type = "image/png"
        // WICHTIG für die Vorschau:
        clipData = ClipData.newRawUri("Ergebnis", contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(shareIntent, "Ergebnis teilen"))
}

/**
 * Exportiert eine einzelne Turniernotiz als .bgsc Datei und teilt sie.
 * Jetzt inklusive eingebetteter Bilddaten.
 */
fun shareTournamentNote(context: Context, result: TournamentNoteResult) {
    try {
        val converters = TournamentConverters()
        val holeNotes = converters.toHoleNoteList(result.notesJson)
        
        // Bilder in Base64 umwandeln und in die JSON-Struktur einbetten
        val holeNotesWithImages = holeNotes.map { holeNote ->
            val imagesWithData = holeNote.getAllImages().map { holeImage ->
                val imageFile = File(holeImage.imagePath)
                if (imageFile.exists()) {
                    val bytes = imageFile.readBytes()
                    val base64Data = Base64.encodeToString(bytes, Base64.DEFAULT)
                    holeImage.copy(imageData = base64Data)
                } else {
                    holeImage
                }
            }
            holeNote.copy(images = imagesWithData)
        }
        
        val updatedResult = result.copy(notesJson = converters.fromHoleNoteList(holeNotesWithImages))
        val wrapper = TournamentExportWrapper(notes = listOf(updatedResult))
        val json = Gson().toJson(wrapper)

        val cachePath = File(context.cacheDir, "exports")
        if (!cachePath.exists()) cachePath.mkdirs()

        // Bereinigung des Dateinamens: Alle Sonderzeichen und Punkte durch Unterstriche ersetzen
        val safeLocation = result.location.ifBlank { "Export" }
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            
        val safeSystem = result.system
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

        val fileName = "${safeLocation}_${safeSystem}.bgsc"

        val file = File(cachePath, fileName)
        FileOutputStream(file).use { fos ->
            GZIPOutputStream(fos).use { gzip ->
                gzip.write(json.toByteArray(Charsets.UTF_8))
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            // Wir nutzen application/octet-stream für maximale Kompatibilität mit WhatsApp
            type = "application/octet-stream"
            clipData = ClipData.newRawUri(fileName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Turniernotiz teilen"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
