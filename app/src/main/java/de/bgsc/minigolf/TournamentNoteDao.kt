package de.bgsc.minigolf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: TournamentNoteResult): Long

    @Update
    suspend fun update(result: TournamentNoteResult)

    @Query("SELECT * FROM tournament_note_results ORDER BY date DESC")
    fun getAllResults(): Flow<List<TournamentNoteResult>>

    @Query("DELETE FROM tournament_note_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tournament_note_results")
    suspend fun deleteAll()
}
