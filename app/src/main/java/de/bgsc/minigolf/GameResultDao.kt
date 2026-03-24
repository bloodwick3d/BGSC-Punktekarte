package de.bgsc.minigolf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gameResult: GameResult): Long

    @Query("SELECT * FROM game_results WHERE isCompleted = 1 ORDER BY date DESC")
    fun getAllCompletedResults(): Flow<List<GameResult>>

    @Query("SELECT * FROM game_results WHERE isCompleted = 0 ORDER BY date DESC")
    fun getAllActiveResults(): Flow<List<GameResult>>

    @Query("DELETE FROM game_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Suppress("unused")
    @Query("DELETE FROM game_results")
    suspend fun deleteAll()
}
