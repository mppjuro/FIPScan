package com.example.fipscan.ui.home

import androidx.lifecycle.ViewModel
import java.io.File

class HomeViewModel : ViewModel() {
    var diagnosisText: String? = null
    var chartImagePath: String? = null
    var pdfFilePath: String? = null
    var patientName: String? = null
    var patientAge: String? = null
    var collectionDate: String? = null
    var patientSpecies: String? = null
    var patientBreed: String? = null
    var patientGender: String? = null
    var patientCoat: String? = null
    var abnormalResults: String? = null
    var rawDataJson: String? = null
    var pdfFile: File? = null
    var currentRivaltaStatus: String = "nie wykonano"
}