package com.example.fipscan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientName: String,
    val age: String,
    val testResults: String,
    val pdfFilePath: String?,
    val imagePath: String?
)
