package de.bgsc.minigolf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.Color

fun getScoreColor(total: Int, system: String, defaultColor: Color, rounds: Int = 1): Color {
    return when {
        system.contains("Eternit") -> {
            when {
                total in (18 * rounds)..(19 * rounds) -> Color(0xFF2196F3) // Blau
                total in (20 * rounds)..(24 * rounds) -> Color(0xFF4CAF50) // Grün
                total in (25 * rounds)..(29 * rounds) -> Color(0xFFF44336) // Rot
                total >= (30 * rounds) -> Color.Black // Schwarz
                else -> defaultColor
            }
        }
        system.contains("Beton") -> {
            when {
                total in (18 * rounds)..(24 * rounds) -> Color(0xFF2196F3) // Blau
                total in (25 * rounds)..(29 * rounds) -> Color(0xFF4CAF50) // Grün
                total in (30 * rounds)..(35 * rounds) -> Color(0xFFF44336) // Rot
                total >= (36 * rounds) -> Color.Black // Schwarz
                else -> defaultColor
            }
        }
        else -> { // Filzgolf, Cobigolf und Sterngolf
            when {
                total in (18 * rounds)..(29 * rounds) -> Color(0xFF2196F3) // Blau
                total in (30 * rounds)..(35 * rounds) -> Color(0xFF4CAF50) // Grün
                total in (36 * rounds)..(39 * rounds) -> Color(0xFFF44336) // Rot
                total >= (40 * rounds) -> Color.Black // Schwarz
                else -> defaultColor
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
