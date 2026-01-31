package com.example.fipscan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResult(result: ResultEntity)

    @Query("SELECT * FROM results ORDER BY timestamp DESC")
    suspend fun getAllResults(): List<ResultEntity>

    @Query("SELECT * FROM results WHERE id = :id")
    suspend fun getResultById(id: Int): ResultEntity?

    @Query("SELECT * FROM results WHERE patientName = :name AND age = :age")
    suspend fun getResultByNameAge(name: String, age: String): ResultEntity?

    @Query("DELETE FROM results WHERE patientName = :name AND age = :age")
    suspend fun deleteDuplicates(name: String, age: String)

    @Query("DELETE FROM results WHERE patientName = 'Nieznany'")
    suspend fun deleteUnknownPatients()

    @Query("SELECT * FROM results ORDER BY timestamp DESC LIMIT 1")
    fun getLatestResult(): Flow<ResultEntity?>

    @Query("SELECT * FROM results WHERE patientName != 'Nieznany' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestResults(limit: Int): List<ResultEntity>
}