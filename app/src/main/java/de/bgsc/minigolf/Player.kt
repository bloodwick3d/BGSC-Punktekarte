package de.bgsc.minigolf

import androidx.compose.ui.graphics.Color

data class Player(
    val name: String,
    val color: Color,
    val roundScores: List<List<Int?>> = listOf(List(18) { null })
)
