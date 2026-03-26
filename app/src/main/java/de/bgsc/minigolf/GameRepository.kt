package de.bgsc.minigolf

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameResultDao: GameResultDao) {

    val allCompletedResults: Flow<List<GameResult>> = gameResultDao.getAllCompletedResults()
    val allActiveResults: Flow<List<GameResult>> = gameResultDao.getAllActiveResults()

    suspend fun insert(gameResult: GameResult): Long {
        return gameResultDao.insert(gameResult)
    }

    suspend fun deleteById(id: Long) {
        gameResultDao.deleteById(id)
    }
}
