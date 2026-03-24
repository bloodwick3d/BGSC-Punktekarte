package de.bgsc.minigolf

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generiert ein hochauflösendes Ergebnisbild direkt auf einem Canvas mit semi-transparenter Tabelle im ScoreTable-Look.
 */
fun generateResultBitmap(context: Context, result: GameResult): Bitmap {
    val players: List<PlayerScore> = Gson().fromJson(result.playersJson, object : TypeToken<List<PlayerScore>>() {}.type)
    return generateBitmapFromData(context, players, result.system, result.location, result.date)
}

fun generateBitmapFromData(
    context: Context,
    players: List<PlayerScore>,
    system: String,
    location: String,
    date: Long
): Bitmap {
    val numRounds = players.firstOrNull()?.rounds?.size ?: 1
    val scale = 2f 
    
    // Laden der Calibri Typefaces für den Canvas
    val calibriNormal = ResourcesCompat.getFont(context, R.font.calibri) ?: Typeface.DEFAULT
    val calibriBold = ResourcesCompat.getFont(context, R.font.calibri_bold) ?: Typeface.DEFAULT_BOLD

    // --- SYNCHRONISATION MIT SCORE-TABLE UI ---
    val stickyColumnWidth = 35f * scale
    val playerColumnWidth = 100f * scale
    val playerGap = 2f * scale
    val sidePadding = 10f * scale
    val bottomPadding = 10f * scale
    
    val tableHeaderHeight = 40f * scale
    val rowHeight = 25f * scale
    val footerHeight = (if (numRounds > 1) 50f else 30f) * scale
    val sectionGap = 2f * scale 
    
    val logoColumnWidth = 70f * scale
    val headerTextHeight = 60f * scale 
    
    // Gesamte Tabellenbreite berechnen
    val tableWidth = stickyColumnWidth + playerGap + (players.size * (playerColumnWidth + playerGap)) + stickyColumnWidth
    
    // Gesamte Tabellenhöhe berechnen
    val tableTotalHeight = tableHeaderHeight + sectionGap + (18 * rowHeight) + sectionGap + footerHeight
    
    val tableLeft = logoColumnWidth + sidePadding
    val tableRight = tableLeft + tableWidth
    
    // Gesamtmaße des Bildes
    val totalWidth = tableRight + sidePadding
    val totalHeight = headerTextHeight + tableTotalHeight + bottomPadding
    
    val tableBottom = headerTextHeight + tableTotalHeight
    
    val bitmap = createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    bitmap.applyCanvas {
        // --- 0. HINTERGRUND (Center Crop + Blur) ---
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            var bgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bg_minigolf, options)
            if (bgBitmap != null) {
                // 1. Blur anwenden
                bgBitmap = blurBitmap(bgBitmap, context)
                
                // 2. Center Crop Logik berechnen
                val bWidth = bgBitmap.width.toFloat()
                val bHeight = bgBitmap.height.toFloat()
                val scaleFactor = (totalWidth / bWidth).coerceAtLeast(totalHeight / bHeight)
                
                val finalSrcWidth = totalWidth / scaleFactor
                val finalSrcHeight = totalHeight / scaleFactor
                val srcLeft = (bWidth - finalSrcWidth) / 2f
                val srcTop = (bHeight - finalSrcHeight) / 2f
                
                val srcRect = Rect(srcLeft.toInt(), srcTop.toInt(), (srcLeft + finalSrcWidth).toInt(), (srcTop + finalSrcHeight).toInt())
                val destRect = RectF(0f, 0f, totalWidth, totalHeight)
                
                drawBitmap(bgBitmap, srcRect, destRect, paint)
                
                // Dezenterer dunkler Schleier (Alpha 70)
                paint.color = Color.argb(70, 0, 0, 0) 
                drawRect(0f, 0f, totalWidth, totalHeight, paint)
            } else {
                drawColor(Color.WHITE)
            }
        } catch (_: Exception) {
            drawColor(Color.WHITE)
        }
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f * scale
            typeface = calibriNormal
            // --- SCHLAGSCHATTEN HINZUFÜGEN ---
            setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.argb(180, 0, 0, 0))
        }

        // --- 1. HEADER-TEXTE (Datum, System, Ort) ---
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(date))
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date))
        
        textPaint.color = Color.LTGRAY
        textPaint.textSize = 8f * scale
        textPaint.typeface = calibriNormal
        
        val iconSize = 10f * scale
        val iconPadding = 4f * scale
        
        // Kalender Icon + Datum
        try {
            ResourcesCompat.getDrawable(context.resources, R.drawable.ic_calendar, null)?.let { drawable ->
                val iconBitmap = drawable.toBitmap(iconSize.toInt(), iconSize.toInt())
                drawBitmap(iconBitmap, sidePadding + 5f * scale, 20f * scale - iconSize + 2f * scale, textPaint)
            }
        } catch (_: Exception) {}
        drawText(dateStr, sidePadding + 5f * scale + iconSize + iconPadding, 20f * scale, textPaint)
        
        // Uhr Icon + Uhrzeit
        try {
            ResourcesCompat.getDrawable(context.resources, R.drawable.ic_clock, null)?.let { drawable ->
                val iconBitmap = drawable.toBitmap(iconSize.toInt(), iconSize.toInt())
                drawBitmap(iconBitmap, sidePadding + 5f * scale, 35f * scale - iconSize + 2f * scale, textPaint)
            }
        } catch (_: Exception) {}
        drawText(timeStr, sidePadding + 5f * scale + iconSize + iconPadding, 35f * scale, textPaint)
        
        textPaint.color = Color.WHITE
        textPaint.textSize = 10f * scale
        textPaint.typeface = calibriBold
        val systemText = system.replace("\n", " ")
        drawText(systemText, tableRight - textPaint.measureText(systemText), 25f * scale, textPaint)
        
        if (location.isNotBlank()) {
            textPaint.typeface = calibriNormal
            val locTextWidth = textPaint.measureText(location)
            val totalLocWidth = iconSize + iconPadding + locTextWidth
            
            try {
                ResourcesCompat.getDrawable(context.resources, R.drawable.ic_location, null)?.let { drawable ->
                    val iconBitmap = drawable.toBitmap(iconSize.toInt(), iconSize.toInt())
                    drawBitmap(iconBitmap, tableRight - totalLocWidth, 40f * scale - iconSize + 2f * scale, textPaint)
                }
            } catch (_: Exception) {}
            
            drawText(location, tableRight - locTextWidth, 40f * scale, textPaint)
        }

        // --- 2. LOGO & VEREINSNAME ---
        val logoCenterX = sidePadding + logoColumnWidth / 2f
        val tableCenterY = headerTextHeight + tableHeaderHeight + (11 * rowHeight)
        
        textPaint.color = Color.WHITE
        textPaint.textSize = 21f * scale
        textPaint.typeface = calibriBold
        textPaint.isFakeBoldText = true 
        
        val clubName = "BGSC \"Gut Schlag\" Gladbeck e.V."
        val textWidth = textPaint.measureText(clubName)
        withRotation(-90f, logoCenterX, tableCenterY) {
            drawText(clubName, logoCenterX - textWidth / 2f, tableCenterY + (textPaint.textSize / 3f), textPaint)
        }
        
        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bgsc_logo)
            if (logoBitmap != null) {
                val logoSize = 50f * scale 
                val scaledLogo = logoBitmap.scale(logoSize.toInt(), logoSize.toInt())
                val textBottomY = tableCenterY + (textWidth / 2f)
                val logoY = textBottomY + (12f * scale) 
                
                withRotation(-90f, logoCenterX, logoY + logoSize / 2f) {
                    drawBitmap(scaledLogo, logoCenterX - logoSize / 2f, logoY, null)
                }
            }
        } catch (_: Exception) {}

        // --- 3. LOCH-SPALTE LINKS (Sticky) ---
        var currentX = tableLeft
        val cornerRadius = 15f * scale
        
        paint.color = Color.argb(102, 0, 0, 0)
        val headerPathL = Path().apply {
            addRoundRect(RectF(currentX, headerTextHeight, currentX + stickyColumnWidth, headerTextHeight + tableHeaderHeight),
                floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        drawPath(headerPathL, paint)
        
        var currentY = headerTextHeight + tableHeaderHeight + sectionGap
        for (i in 1..18) {
            val isEven = i % 2 == 0
            paint.color = if (isEven) Color.argb(77, 0, 0, 0) else Color.argb(102, 0, 0, 0)
            drawRect(currentX, currentY, currentX + stickyColumnWidth, currentY + rowHeight, paint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 12f * scale
            textPaint.isFakeBoldText = false 
            textPaint.typeface = calibriBold
            drawText(i.toString(), currentX + stickyColumnWidth / 2f - textPaint.measureText(i.toString()) / 2f, currentY + 17f * scale, textPaint)
            currentY += rowHeight
        }
        
        paint.color = Color.argb(102, 0, 0, 0)
        val footerPathL = Path().apply {
            val footerTop = currentY + sectionGap
            addRoundRect(RectF(currentX, footerTop, currentX + stickyColumnWidth, tableBottom),
                floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius), Path.Direction.CW)
        }
        drawPath(footerPathL, paint)
        
        currentX += (stickyColumnWidth + playerGap)

        // --- 4. SPIELER SPALTEN ---
        players.forEach { player ->
            val pColor = player.colorInt
            currentY = headerTextHeight
            
            // Spieler Header
            paint.color = pColor
            drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + tableHeaderHeight, paint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 14f * scale
            textPaint.typeface = calibriBold
            val shortName = if (player.name.length > 12) player.name.take(10) + ".." else player.name
            drawText(shortName, currentX + playerColumnWidth / 2f - textPaint.measureText(shortName) / 2f, currentY + 25f * scale, textPaint)
            
            currentY += tableHeaderHeight + sectionGap
            val roundWidth = playerColumnWidth / numRounds
            
            // Spieler Body (Löcher)
            for (hIdx in 0 until 18) {
                val isEven = (hIdx + 1) % 2 == 0
                paint.color = if (isEven) Color.WHITE else "#F5F5F5".toColorInt()
                paint.alpha = 200 
                drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + rowHeight, paint)
                
                paint.color = pColor
                paint.alpha = if (isEven) 25 else 51 
                drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + rowHeight, paint)
                paint.alpha = 255
                
                for (rIdx in 0 until numRounds) {
                    val score = player.holeScores.getOrNull(rIdx)?.getOrNull(hIdx)
                    if (score != null) {
                        textPaint.color = Color.BLACK
                        textPaint.textSize = 12f * scale
                        textPaint.typeface = calibriNormal
                        drawText(score.toString(), currentX + (rIdx * roundWidth) + roundWidth / 2f - textPaint.measureText(score.toString()) / 2f, currentY + 17f * scale, textPaint)
                    }
                    if (numRounds > 1 && rIdx < numRounds - 1) {
                        paint.color = Color.LTGRAY
                        paint.alpha = 51 
                        drawLine(currentX + (rIdx + 1) * roundWidth, currentY, currentX + (rIdx + 1) * roundWidth, currentY + rowHeight, paint)
                        paint.alpha = 255
                    }
                }
                currentY += rowHeight
            }
            
            // Spieler Footer (Summen)
            currentY += sectionGap
            paint.color = "#E0E0E0".toColorInt()
            paint.alpha = 200
            drawRect(currentX, currentY, currentX + playerColumnWidth, tableBottom, paint)
            paint.color = pColor
            paint.alpha = 102 
            drawRect(currentX, currentY, currentX + playerColumnWidth, tableBottom, paint)
            paint.alpha = 255
            
            if (numRounds == 1) {
                val isFull = player.roundIsFull.firstOrNull() ?: true
                textPaint.textSize = 16f * scale
                textPaint.typeface = calibriBold
                textPaint.color = if (isFull) getInternalScoreColor(player.totalScore, system, 1) else Color.WHITE
                drawText(player.totalScore.toString(), currentX + playerColumnWidth / 2f - textPaint.measureText(player.totalScore.toString()) / 2f, currentY + (tableBottom - currentY) / 2f + 6f * scale, textPaint)
            } else {
                for (rIdx in 0 until numRounds) {
                    val rSum = player.rounds.getOrNull(rIdx) ?: 0
                    val isFull = player.roundIsFull.getOrNull(rIdx) ?: true
                    textPaint.textSize = 11f * scale
                    textPaint.typeface = calibriBold
                    textPaint.color = if (isFull) getInternalScoreColor(rSum, system, 1) else Color.WHITE
                    drawText(rSum.toString(), currentX + (rIdx * roundWidth) + roundWidth / 2f - textPaint.measureText(rSum.toString()) / 2f, currentY + 20f * scale, textPaint)
                }
                val allRoundsFull = player.roundIsFull.isNotEmpty() && player.roundIsFull.all { it }
                textPaint.textSize = 14f * scale
                textPaint.typeface = calibriBold
                textPaint.color = if (allRoundsFull) getInternalScoreColor(player.totalScore, system, numRounds) else Color.WHITE
                drawText(player.totalScore.toString(), currentX + playerColumnWidth / 2f - textPaint.measureText(player.totalScore.toString()) / 2f, tableBottom - 8f * scale, textPaint)
            }
            currentX += (playerColumnWidth + playerGap)
        }

        // --- 5. LOCH-SPALTE RECHTS (Sticky) ---
        val currentXRight = currentX
        paint.color = Color.argb(102, 0, 0, 0)
        val headerPathR = Path().apply {
            addRoundRect(RectF(currentXRight, headerTextHeight, currentXRight + stickyColumnWidth, headerTextHeight + tableHeaderHeight),
                floatArrayOf(0f, 0f, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        drawPath(headerPathR, paint)
        
        currentY = headerTextHeight + tableHeaderHeight + sectionGap
        for (i in 1..18) {
            val isEven = i % 2 == 0
            paint.color = if (isEven) Color.argb(77, 0, 0, 0) else Color.argb(102, 0, 0, 0)
            drawRect(currentXRight, currentY, currentXRight + stickyColumnWidth, currentY + rowHeight, paint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 12f * scale
            textPaint.typeface = calibriBold
            drawText(i.toString(), currentXRight + stickyColumnWidth / 2f - textPaint.measureText(i.toString()) / 2f, currentY + 17f * scale, textPaint)
            currentY += rowHeight
        }
        
        paint.color = Color.argb(102, 0, 0, 0)
        val footerPathR = Path().apply {
            val footerTop = currentY + sectionGap
            addRoundRect(RectF(currentXRight, footerTop, currentXRight + stickyColumnWidth, tableBottom),
                floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, 0f, 0f), Path.Direction.CW)
        }
        drawPath(footerPathR, paint)
    }

    return bitmap
}

/**
 * Hilfsfunktion zum Weichzeichnen eines Bitmaps.
 */
@Suppress("DEPRECATION")
private fun blurBitmap(bitmap: Bitmap, context: Context, radius: Float = 25f): Bitmap {
    val downscale = 4
    val width = (bitmap.width / downscale).coerceAtLeast(1)
    val height = (bitmap.height / downscale).coerceAtLeast(1)
    val input = bitmap.scale(width, height)
    
    val config = input.config ?: Bitmap.Config.ARGB_8888
    val output = createBitmap(input.width, input.height, config)
    val rs = android.renderscript.RenderScript.create(context)
    val blurScript = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs))
    val allIn = android.renderscript.Allocation.createFromBitmap(rs, input)
    val allOut = android.renderscript.Allocation.createFromBitmap(rs, output)
    blurScript.setRadius(radius)
    blurScript.setInput(allIn)
    blurScript.forEach(allOut)
    allOut.copyTo(output)
    rs.destroy()
    
    return output.scale(bitmap.width, bitmap.height)
}

private fun getInternalScoreColor(total: Int, system: String, rounds: Int): Int {
    return when {
        system.contains("Eternit") -> {
            when (total) {
                in (18 * rounds)..(19 * rounds) -> "#2196F3".toColorInt()
                in (20 * rounds)..(24 * rounds) -> "#4CAF50".toColorInt()
                in (25 * rounds)..(29 * rounds) -> "#F44336".toColorInt()
                else -> Color.WHITE
            }
        }
        system.contains("Beton") -> {
            when (total) {
                in (18 * rounds)..(24 * rounds) -> "#2196F3".toColorInt()
                in (25 * rounds)..(29 * rounds) -> "#4CAF50".toColorInt()
                in (30 * rounds)..(35 * rounds) -> "#F44336".toColorInt()
                else -> Color.WHITE
            }
        }
        else -> {
            when (total) {
                in (18 * rounds)..(29 * rounds) -> "#2196F3".toColorInt()
                in (30 * rounds)..(35 * rounds) -> "#4CAF50".toColorInt()
                in (36 * rounds)..(39 * rounds) -> "#F44336".toColorInt()
                else -> Color.WHITE
            }
        }
    }
}
