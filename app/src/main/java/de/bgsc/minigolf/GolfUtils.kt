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
                total in (18 * rounds)..(19 * rounds) -> Color(0xFF2196F3) 
                total in (20 * rounds)..(24 * rounds) -> Color(0xFF4CAF50) 
                total in (25 * rounds)..(29 * rounds) -> Color(0xFFF44336) 
                total >= (30 * rounds) -> Color.Black 
                else -> defaultColor
            }
        }
        system.contains("Beton") -> {
            when {
                total in (18 * rounds)..(24 * rounds) -> Color(0xFF2196F3) 
                total in (25 * rounds)..(29 * rounds) -> Color(0xFF4CAF50) 
                total in (30 * rounds)..(35 * rounds) -> Color(0xFFF44336) 
                total >= (36 * rounds) -> Color.Black 
                else -> defaultColor
            }
        }
        else -> { 
            when {
                total in (18 * rounds)..(29 * rounds) -> Color(0xFF2196F3) 
                total in (30 * rounds)..(35 * rounds) -> Color(0xFF4CAF50) 
                total in (36 * rounds)..(39 * rounds) -> Color(0xFFF44336) 
                total >= (40 * rounds) -> Color.Black 
                else -> defaultColor
            }
        }
    }
}

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
    
    val authority = "${context.packageName}.fileprovider"
    val contentUri: Uri = FileProvider.getUriForFile(context, authority, imageFile)
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, contentUri)
        type = "image/png"
        clipData = ClipData.newRawUri(context.getString(R.string.share_result), contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_title)))
}
