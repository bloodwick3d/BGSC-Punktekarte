package de.bgsc.minigolf

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

@Entity(tableName = "tournament_note_results")
data class TournamentNoteResult(
    @PrimaryKey(autoGenerate = true) 
    @SerializedName("id")
    val id: Long = 0,
    
    @SerializedName("date")
    val date: Long,
    
    @SerializedName("location")
    val location: String,
    
    @SerializedName("system")
    val system: String,
    
    @SerializedName("notesJson")
    val notesJson: String
)

class TournamentConverters {
    @TypeConverter
    fun fromHoleNoteList(value: List<HoleNote>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toHoleNoteList(value: String): List<HoleNote> {
        val listType = object : TypeToken<List<HoleNote>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
