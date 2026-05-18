package de.bgsc.minigolf

import com.google.gson.annotations.SerializedName

data class HoleImage(
    @SerializedName("imagePath") val imagePath: String,
    @SerializedName("originalImagePath") val originalImagePath: String,
    @SerializedName("imageData") var imageData: String? = null // Base64 kodierte Bilddaten für Export/Import
)

data class HoleNote(
    @SerializedName("ball") val ball: String = "",
    @SerializedName("startPoint") val startPoint: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("images") val images: List<HoleImage> = emptyList(),
    // Veraltete Felder für Migration (werden automatisch in 'images' überführt)
    @SerializedName("imagePath") val legacyImagePath: String? = null,
    @SerializedName("originalImagePath") val legacyOriginalImagePath: String? = null
) {
    /**
     * Gibt alle Bilder zurück, inkl. migrierter Bilder aus alten Versionen.
     */
    fun getAllImages(): List<HoleImage> {
        val result = images.toMutableList()
        if (legacyImagePath != null && legacyOriginalImagePath != null) {
            val legacy = HoleImage(legacyImagePath, legacyOriginalImagePath)
            if (!result.contains(legacy)) result.add(0, legacy)
        }
        return result
    }
}

data class TournamentExportWrapper(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("appIdentifier") val appIdentifier: String = "MiniGolf_Punktekarte",
    @SerializedName("exportDate") val exportDate: Long = System.currentTimeMillis(),
    @SerializedName("notes") val notes: List<TournamentNoteResult>
)

enum class TournamentTheme {
    LIGHT, DARK, SYSTEM
}

sealed class Screen {
    data object Main : Screen()
    data object History : Screen()
    data object ActiveGames : Screen()
    data object TournamentSelection : Screen()
    data object TournamentTable : Screen()
    data object TournamentHistory : Screen()
}

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String?
)
