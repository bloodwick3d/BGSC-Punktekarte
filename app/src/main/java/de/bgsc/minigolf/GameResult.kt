package de.bgsc.minigolf

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

@Keep
@Entity(tableName = "game_results")
data class GameResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @SerializedName("date") val date: Long,
    @SerializedName("system") val system: String,
    @SerializedName("location") val location: String = "",
    @SerializedName("playersJson") val playersJson: String,
    @SerializedName("isFullGame") val isFullGame: Boolean = false 
)

@Keep
data class PlayerScore(
    @SerializedName("name") val name: String,
    @SerializedName("colorInt") val colorInt: Int,
    @SerializedName("totalScore") val totalScore: Int,
    @SerializedName("rounds") val rounds: List<Int>,
    @SerializedName("roundIsFull") val roundIsFull: List<Boolean> = emptyList(),
    @SerializedName("holeScores") val holeScores: List<List<Int?>> = emptyList()
)

class Converters {
    @TypeConverter
    fun fromPlayerScoreList(value: List<PlayerScore>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toPlayerScoreList(value: String): List<PlayerScore> {
        val listType = object : TypeToken<List<PlayerScore>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
