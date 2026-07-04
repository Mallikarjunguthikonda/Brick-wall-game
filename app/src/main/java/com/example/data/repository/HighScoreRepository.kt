package com.example.data.repository

import com.example.data.database.HighScoreDao
import com.example.data.database.HighScoreEntity
import kotlinx.coroutines.flow.Flow

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScoreEntity>> = highScoreDao.getTopScores()

    suspend fun insertScore(score: HighScoreEntity) {
        highScoreDao.insertScore(score)
    }

    suspend fun clearAllScores() {
        highScoreDao.clearAllScores()
    }
}
