package com.example.fipscan

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val patientName: String,
    val age: String,
    val testResults: String,
    val pdfFilePath: String?,
    val imagePath: String?,
    val collectionDate: String? = null,
    val rawDataJson: String? = null,
    val diagnosis: String?,
    val rivaltaStatus: String? = null,
    val species: String? = null,
    val breed: String? = null,
    val gender: String? = null,
    val coat: String? = null
) : Parcelable