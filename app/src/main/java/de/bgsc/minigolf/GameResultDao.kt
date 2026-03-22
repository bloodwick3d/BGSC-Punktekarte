package de.bgsc.minigolf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {
    @Insert
    suspend fun insert(gameResult: GameResult)

    @Query("SELECT * FROM game_results ORDER BY date DESC")
    fun getAllResults(): Flow<List<GameResult>>

    @Query("DELETE FROM game_results WHERE id = :id")
    suspend fun deleteById(id: Long)
}
