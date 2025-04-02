package com.example.fipscan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ResultEntity)

    @Query("SELECT * FROM results")
    suspend fun getAllResults(): List<ResultEntity>

    @Query("SELECT * FROM results WHERE id = :id")
    suspend fun getResultById(id: Int): ResultEntity?

    @Query("DELETE FROM results WHERE patientName = :name AND age = :age")
    suspend fun deleteDuplicates(name: String, age: String)

    @Query("DELETE FROM results WHERE patientName = 'Nieznany'")
    suspend fun deleteUnknownPatients()

    @Query("SELECT * FROM results ORDER BY timestamp DESC LIMIT 1")
    fun getLatestResult(): Flow<ResultEntity?>
}
