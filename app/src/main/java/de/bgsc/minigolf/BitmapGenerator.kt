package de.bgsc.minigolf

import android.content.Context
import android.graphics.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

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
    val calibriNormal = ResourcesCompat.getFont(context, R.font.calibri) ?: Typeface.DEFAULT
    val calibriBold = ResourcesCompat.getFont(context, R.font.calibri_bold) ?: Typeface.DEFAULT_BOLD

    val stickyColumnWidth = 35f * scale
    val playerColumnWidth = 100f * scale
    val playerGap = 2f * scale
    val sidePadding = 10f * scale
    val bottomPadding = 10f * scale
    val tableHeaderHeight = 40f * scale
    val rowHeight = 25f * scale
    val footerHeight = (if (numRounds > 1) 50f else 35f) * scale
    val sectionGap = 2f * scale 
    val logoColumnWidth = 70f * scale
    val headerTextHeight = 60f * scale 

    val tableWidth = stickyColumnWidth + playerGap + (players.size * (playerColumnWidth + playerGap)) + stickyColumnWidth
    val tableTotalHeight = tableHeaderHeight + sectionGap + (18 * rowHeight) + sectionGap + footerHeight
    val tableLeft = logoColumnWidth + sidePadding
    val tableRight = tableLeft + tableWidth
    val totalWidth = tableRight + sidePadding
    val totalHeight = headerTextHeight + tableTotalHeight + bottomPadding
    val tableBottom = headerTextHeight + tableTotalHeight
    
    val bitmap = createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY 
        alpha = 255 
        style = Paint.Style.STROKE
        strokeWidth = 1f * scale
    }
    
    bitmap.applyCanvas {
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bgBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bg_minigolf, options)
            if (bgBitmap != null) {
                val bWidth = bgBitmap.width.toFloat()
                val bHeight = bgBitmap.height.toFloat()
                val scaleFactor = (totalWidth / bWidth).coerceAtLeast(totalHeight / bHeight)
                val finalSrcWidth = totalWidth / scaleFactor
                val finalSrcHeight = totalHeight / scaleFactor
                val srcRect = Rect(((bWidth - finalSrcWidth) / 2f).toInt(), ((bHeight - finalSrcHeight) / 2f).toInt(), ((bWidth + finalSrcWidth) / 2f).toInt(), ((bHeight + finalSrcHeight) / 2f).toInt())
                drawBitmap(bgBitmap, srcRect, RectF(0f, 0f, totalWidth, totalHeight), paint)
            } else { drawColor(Color.WHITE) }
        } catch (_: Exception) { drawColor(Color.WHITE) }
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.argb(180, 0, 0, 0)) }
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(date))
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date))
        textPaint.color = Color.LTGRAY; textPaint.textSize = 8f * scale; textPaint.typeface = calibriNormal; textPaint.textAlign = Paint.Align.LEFT
        drawText(dateStr, sidePadding + 5f * scale + 14f * scale, 20f * scale, textPaint)
        drawText(timeStr, sidePadding + 5f * scale + 14f * scale, 35f * scale, textPaint)
        
        textPaint.color = Color.WHITE; textPaint.textSize = 10f * scale; textPaint.typeface = calibriBold
        val systemText = system.replace("\n", " ")
        drawText(systemText, tableRight - textPaint.measureText(systemText), 25f * scale, textPaint)
        if (location.isNotBlank()) drawText(location, tableRight - textPaint.measureText(location), 40f * scale, textPaint)

        val logoCenterX = sidePadding + logoColumnWidth / 2f
        val tableCenterY = headerTextHeight + tableHeaderHeight + (11 * rowHeight)
        textPaint.color = Color.WHITE; textPaint.textSize = 21f * scale; textPaint.typeface = calibriBold; textPaint.isFakeBoldText = true; textPaint.textAlign = Paint.Align.CENTER
        withRotation(-90f, logoCenterX, tableCenterY) { drawText("BGSC \"Gut Schlag\" Gladbeck e.V.", logoCenterX, tableCenterY + (textPaint.textSize / 3f), textPaint) }
        
        var currentX = tableLeft
        val cornerRadius = 15f * scale
        paint.color = Color.argb(102, 0, 0, 0); paint.style = Paint.Style.FILL
        drawPath(Path().apply { addRoundRect(RectF(currentX, headerTextHeight, currentX + stickyColumnWidth, headerTextHeight + tableHeaderHeight), floatArrayOf(cornerRadius, cornerRadius, 0f, 0f, 0f, 0f, 0f, 0f), Path.Direction.CW) }, paint)
        var currentY = headerTextHeight + tableHeaderHeight + sectionGap
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 1..18) {
            paint.color = Color.BLACK; paint.alpha = if (i % 2 == 0) 102 else 77
            drawRect(currentX, currentY, currentX + stickyColumnWidth, currentY + rowHeight, paint)
            textPaint.color = Color.WHITE; textPaint.textSize = 12f * scale; textPaint.isFakeBoldText = false; textPaint.typeface = calibriBold
            val centerY = currentY + rowHeight / 2f
            val baseline = centerY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
            drawText(i.toString(), currentX + stickyColumnWidth / 2f, baseline, textPaint)
            currentY += rowHeight
        }
        paint.color = Color.argb(102, 0, 0, 0); paint.alpha = 102
        drawPath(Path().apply { addRoundRect(RectF(currentX, currentY + sectionGap, currentX + stickyColumnWidth, tableBottom), floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, cornerRadius, cornerRadius), Path.Direction.CW) }, paint)
        currentX += (stickyColumnWidth + playerGap)

        players.forEach { player ->
            val pColor = Color.rgb(Color.red(player.colorInt), Color.green(player.colorInt), Color.blue(player.colorInt))
            currentY = headerTextHeight
            paint.color = pColor; paint.alpha = 255; drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + tableHeaderHeight, paint)
            textPaint.color = Color.WHITE; textPaint.textSize = 14f * scale; textPaint.typeface = calibriBold
            val shortName = if (player.name.length > 12) player.name.take(10) + ".." else player.name
            val headerCenterY = currentY + tableHeaderHeight / 2f
            val headerBaseline = headerCenterY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
            drawText(shortName, currentX + playerColumnWidth / 2f, headerBaseline, textPaint)
            
            currentY += tableHeaderHeight + sectionGap
            val roundWidth = playerColumnWidth / numRounds
            for (hIdx in 0 until 18) {
                paint.color = Color.WHITE; paint.alpha = 255; drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + rowHeight, paint)
                paint.color = pColor; paint.alpha = if ((hIdx + 1) % 2 == 0) 51 else 25; drawRect(currentX, currentY, currentX + playerColumnWidth, currentY + rowHeight, paint)
                paint.alpha = 255
                
                val cellCenterY = currentY + rowHeight / 2f
                for (rIdx in 0 until numRounds) {
                    player.holeScores.getOrNull(rIdx)?.getOrNull(hIdx)?.let { score ->
                        textPaint.color = Color.BLACK; textPaint.textSize = 12f * scale; textPaint.typeface = calibriNormal
                        val baseline = cellCenterY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
                        drawText(score.toString(), currentX + (rIdx * roundWidth) + roundWidth / 2f, baseline, textPaint)
                    }
                    if (numRounds > 1 && rIdx < numRounds - 1) {
                        val lineX = currentX + (rIdx + 1) * roundWidth
                        drawLine(lineX, currentY, lineX, currentY + rowHeight, linePaint)
                    }
                }
                currentY += rowHeight
            }
            currentY += sectionGap
            paint.color = Color.WHITE; paint.alpha = 255; drawRect(currentX, currentY, currentX + playerColumnWidth, tableBottom, paint)
            paint.color = pColor; paint.alpha = 25; drawRect(currentX, currentY, currentX + playerColumnWidth, tableBottom, paint)
            paint.alpha = 255
            
            if (numRounds == 1) {
                val played = player.holeScores.firstOrNull()?.count { it != null && it > 0 } ?: 0
                val footerCenterY = currentY + (tableBottom - currentY) / 2f
                textPaint.textSize = 16f * scale; textPaint.typeface = calibriBold; textPaint.color = getInternalScoreColor(player.totalScore, system, 1, played)
                val baseline = footerCenterY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
                drawText(player.totalScore.toString(), currentX + playerColumnWidth / 2f, baseline, textPaint)
            } else {
                val halfFooterHeight = (tableBottom - currentY) / 2f
                for (rIdx in 0 until numRounds) {
                    val rSum = player.rounds.getOrNull(rIdx) ?: 0
                    val rPlayed = player.holeScores.getOrNull(rIdx)?.count { it != null && it > 0 } ?: 0
                    textPaint.textSize = 11f * scale; textPaint.typeface = calibriBold; textPaint.color = getInternalScoreColor(rSum, system, 1, rPlayed)
                    val rCenterY = currentY + halfFooterHeight / 2f
                    val rBaseline = rCenterY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
                    drawText(rSum.toString(), currentX + (rIdx * roundWidth) + roundWidth / 2f, rBaseline, textPaint)
                    if (rIdx < numRounds - 1) {
                        val lineX = currentX + (rIdx + 1) * roundWidth
                        drawLine(lineX, currentY, lineX, currentY + halfFooterHeight, linePaint)
                    }
                }
                val hLineY = currentY + halfFooterHeight
                drawLine(currentX, hLineY, currentX + playerColumnWidth, hLineY, linePaint)

                val totalPlayed = player.holeScores.sumOf { round -> round.count { it != null && it > 0 } }
                textPaint.textSize = 14f * scale; textPaint.typeface = calibriBold; textPaint.color = getInternalScoreColor(player.totalScore, system, numRounds, totalPlayed)
                val totalCenterY = hLineY + halfFooterHeight / 2f
                val totalBaseline = totalCenterY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
                drawText(player.totalScore.toString(), currentX + playerColumnWidth / 2f, totalBaseline, textPaint)
            }
            currentX += (playerColumnWidth + playerGap)
        }
        val cXR = currentX; paint.color = Color.argb(102, 0, 0, 0); paint.alpha = 102
        drawPath(Path().apply { addRoundRect(RectF(cXR, headerTextHeight, cXR + stickyColumnWidth, headerTextHeight + tableHeaderHeight), floatArrayOf(0f, 0f, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f), Path.Direction.CW) }, paint)
        currentY = headerTextHeight + tableHeaderHeight + sectionGap
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 1..18) {
            paint.color = Color.BLACK; paint.alpha = if (i % 2 == 0) 102 else 77; drawRect(cXR, currentY, cXR + stickyColumnWidth, currentY + rowHeight, paint)
            textPaint.color = Color.WHITE; textPaint.textSize = 12f * scale; textPaint.typeface = calibriBold
            val centerY = currentY + rowHeight / 2f
            val baseline = centerY - (textPaint.fontMetrics.descent + textPaint.fontMetrics.ascent) / 2f
            drawText(i.toString(), cXR + stickyColumnWidth / 2f, baseline, textPaint)
            currentY += rowHeight
        }
        paint.color = Color.argb(102, 0, 0, 0); paint.alpha = 102
        drawPath(Path().apply { addRoundRect(RectF(cXR, currentY + sectionGap, cXR + stickyColumnWidth, tableBottom), floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, 0f, 0f), Path.Direction.CW) }, paint)
    }
    return bitmap
}

private fun getInternalScoreColor(total: Int, system: String, rounds: Int, playedHoles: Int = 0): Int {
    if (total == 0) return Color.BLACK
    if (rounds <= 0) return Color.WHITE
    val effectiveScore = if (playedHoles > 0) { (rounds * 18) + (total - playedHoles) } else total
    val average = effectiveScore.toFloat() / rounds
    return when {
        system.contains("Eternit") -> when { average < 20f -> "#2196F3".toColorInt(); average < 25f -> "#4CAF50".toColorInt(); average < 30f -> "#F44336".toColorInt(); else -> Color.BLACK }
        system.contains("Beton") -> when { average < 25f -> "#2196F3".toColorInt(); average < 30f -> "#4CAF50".toColorInt(); average < 36f -> "#F44336".toColorInt(); else -> Color.BLACK }
        else -> when { average < 30f -> "#2196F3".toColorInt(); average < 36f -> "#4CAF50".toColorInt(); average < 40f -> "#F44336".toColorInt(); else -> Color.BLACK }
    }
}
