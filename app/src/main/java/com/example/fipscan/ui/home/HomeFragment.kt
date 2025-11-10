package com.example.fipscan.ui.home

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fipscan.AppDatabase
import com.example.fipscan.BarChartLevelAnalyzer
import com.example.fipscan.ElectrophoresisAnalyzer
import com.example.fipscan.ExtractData
import com.example.fipscan.PdfChartExtractor
import com.example.fipscan.R
import com.example.fipscan.ResultEntity
import com.example.fipscan.SharedResultViewModel
import com.example.fipscan.databinding.FragmentHomeBinding
import com.example.fipscan.ui.history.HistoryFragmentDirections
import com.google.gson.Gson
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlin.math.min

class HomeFragment : Fragment() {
    private val sharedViewModel: SharedResultViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private lateinit var pdfChartExtractor: PdfChartExtractor
    private val viewModel: HomeViewModel by viewModels()
    private var recentHistoryAdapter: RecentHistoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        // Używamy resources.getStringArray do pobrania przetłumaczonych opcji
        val rivaltaOptions = resources.getStringArray(R.array.rivalta_options)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            rivaltaOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.rivaltaSpinner.adapter = adapter

        PDFBoxResourceLoader.init(requireContext())
        binding.buttonLoadPdf.setOnClickListener { openFilePicker() }
        binding.buttonLoadPdfLarge.setOnClickListener { openFilePicker() }

        pdfChartExtractor = PdfChartExtractor(requireContext())

        binding.rivaltaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newStatus = parent?.getItemAtPosition(position).toString()
                viewModel.currentRivaltaStatus = newStatus
                recalculateRiskAndUpdateUI()
                saveCurrentResult()

                sharedViewModel.selectedResult.value?.let { currentResult ->
                    val updatedResult = currentResult.copy(rivaltaStatus = newStatus)
                    sharedViewModel.setSelectedResult(updatedResult)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        var dataRestored = false
        // Domyślny status Rivalta z zasobów (pierwsza opcja)
        val defaultRivaltaStatus = resources.getStringArray(R.array.rivalta_options)[0]

        arguments?.let {
            val args = HomeFragmentArgs.fromBundle(it)
            args.result?.let { result ->
                viewModel.apply {
                    patientName = result.patientName
                    rawDataJson = result.rawDataJson
                    currentRivaltaStatus = result.rivaltaStatus ?: defaultRivaltaStatus
                }

                sharedViewModel.setSelectedResult(result)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showResultsState()
                    displayExistingResult(result)
                    dataRestored = true
                }
            }
        }

        if (!dataRestored) {
            sharedViewModel.selectedResult.value?.let { result ->
                showResultsState()
                restoreUIFromResult(result)
                dataRestored = true
            }
        }

        if (!dataRestored) {
            showInitialState()
            loadRecentHistory()
        }

        binding.buttonSaveOriginal.setOnClickListener {
            viewModel.pdfFile?.absolutePath?.let { filePath ->
                val fileName = "${viewModel.patientName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                savePdfToDownloadsUsingMediaStore(filePath, fileName)
            } ?: Toast.makeText(requireContext(), getString(R.string.toast_no_pdf), Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    private fun showInitialState() {
        binding.buttonLoadPdf.visibility = View.GONE
        binding.rivaltaContainer.visibility = View.GONE
        binding.chartImageView.visibility = View.GONE
        binding.riskSaveContainer.visibility = View.GONE
        binding.scrollView.visibility = View.GONE

        binding.buttonLoadPdfLarge.visibility = View.VISIBLE
        binding.recentHistoryLabel.visibility = View.VISIBLE
        binding.recentHistoryRecyclerView.visibility = View.VISIBLE
        binding.disclaimerTextView.visibility = View.VISIBLE
    }

    private fun showResultsState() {
        binding.buttonLoadPdf.visibility = View.VISIBLE
        binding.rivaltaContainer.visibility = View.VISIBLE
        binding.chartImageView.visibility = View.VISIBLE
        binding.riskSaveContainer.visibility = View.VISIBLE
        binding.scrollView.visibility = View.VISIBLE

        binding.buttonLoadPdfLarge.visibility = View.GONE
        binding.recentHistoryLabel.visibility = View.GONE
        binding.recentHistoryRecyclerView.visibility = View.GONE
        binding.disclaimerTextView.visibility = View.GONE

        if (binding.chartImageView.drawable == null) {
            binding.chartImageView.visibility = View.GONE
        }
        if (binding.riskIndicator.text.isNullOrEmpty()) {
            binding.riskSaveContainer.visibility = View.GONE
        }
        if (viewModel.rawDataJson == null) {
            binding.rivaltaContainer.visibility = View.GONE
        }
    }

    private fun loadRecentHistory() {
        binding.recentHistoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val recentResults = AppDatabase.getDatabase(requireContext()).resultDao().getLatestResults(3)
            withContext(Dispatchers.Main) {
                recentHistoryAdapter = RecentHistoryAdapter(recentResults) { result ->
                    result.rawDataJson?.let { json ->
                        val map = Gson().fromJson(json, Map::class.java) as? Map<String, Any>
                        if (map != null) {
                            ExtractData.lastExtracted = map
                        }
                    }
                    val action = HistoryFragmentDirections.actionNavigationHistoryToNavigationHome(result)
                    findNavController().navigate(action)
                }
                binding.recentHistoryRecyclerView.adapter = recentHistoryAdapter
            }
        }
    }

    private fun updateRiskIndicator(percentage: Int? = null) {
        activity?.runOnUiThread {
            if (percentage != null) {
                binding.riskSaveContainer.visibility = View.VISIBLE
                binding.riskIndicator.text = getString(R.string.risk_indicator_format, percentage)
                val bgColor = calculateBackgroundColor(percentage)
                binding.riskIndicator.setBackgroundColor(bgColor)
                binding.riskIndicator.visibility = View.VISIBLE
            } else {
                binding.riskIndicator.visibility = View.GONE
                binding.riskSaveContainer.visibility = View.GONE
            }
        }
    }

    private fun calculateBackgroundColor(percentage: Int): Int {
        val r = min(255, percentage * 255 / 100)
        val g = min(255, (100 - percentage) * 255 / 100)
        val b = 0
        return Color.rgb(min(255, r + 150), min(255, g + 150), min(255, b + 150))
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePicker.launch(intent)
    }

    private fun extractTablesWithTabula() {
        pdfUri?.let { uri ->
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val pdfFilename = "input_$timestamp.pdf"
                val csvFilename = "data_$timestamp.csv"

                val pdfFile = savePdfLocally(uri, pdfFilename)
                if (pdfFile != null) {
                    Thread { uploadFileToFTP(pdfFile) }.start()
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), getString(R.string.error_pdf_local_save), Toast.LENGTH_LONG).show()
                    }
                    return@extractTablesWithTabula
                }

                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)
                    var finalChartFileToSave: File? = null
                    var barChartImagePath: String? = null
                    var chartToDisplayAndSavePath: String? = null

                    try {
                        val extractionResult = pdfChartExtractor.extractChartFromPDF(pdfFile)
                        if (extractionResult != null && extractionResult.imagePaths.isNotEmpty()) {
                            chartToDisplayAndSavePath = extractionResult.imagePaths[0]
                            barChartImagePath = extractionResult.imagePaths.getOrNull(1)
                            finalChartFileToSave = File(chartToDisplayAndSavePath)

                            extractionResult.barSections?.let { sections ->
                                viewModel.diagnosisText = BarChartLevelAnalyzer.analyzeGammapathy(
                                    sections.section1,
                                    sections.section4
                                )
                                Log.d("GAMMOPATHY", "Wynik analizy gammapatii: ${viewModel.diagnosisText}")
                            } ?: run {
                                viewModel.diagnosisText = getString(R.string.diagnosis_no_gammopathy)
                                Log.d("GAMMOPATHY", "Brak danych z sekcji wykresu")
                            }
                        } else {
                            viewModel.diagnosisText = getString(R.string.diagnosis_no_gammopathy)
                            Log.d("GAMMOPATHY", "Brak wykresu")
                        }

                        if (finalChartFileToSave?.exists() == true) {
                            Thread { uploadFileToFTP(finalChartFileToSave) }.start()
                        }

                        barChartImagePath?.let { path ->
                            val barChartFile = File(path)
                            if (barChartFile.exists()) {
                                Thread {
                                    uploadFileToFTP(barChartFile)
                                    try {
                                        val bitmap = BitmapFactory.decodeFile(path)
                                        val origBitmap = BitmapFactory.decodeFile(chartToDisplayAndSavePath)
                                        if (bitmap != null && origBitmap != null) {
                                            BarChartLevelAnalyzer.analyzeBarHeights(bitmap, origBitmap)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BAR_LEVELS", "Błąd analizy słupków", e)
                                    }
                                }.start()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CHART_EXTRACT", "Błąd przetwarzania wykresu", e)
                        viewModel.diagnosisText = getString(R.string.diagnosis_chart_error)
                    }

                    val (tablesData, _) = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        activity?.runOnUiThread {
                            binding.resultsTextView.text = getString(R.string.error_no_tables_found)
                        }
                        return@use
                    }

                    val csvFile = saveAsCSV(tablesData, csvFilename)
                    Thread {
                        uploadFileToFTP(csvFile)
                        analyzeCSVFile(csvFile, chartToDisplayAndSavePath, pdfFile)
                    }.start()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.resultsTextView.text = getString(R.string.error_pdf_processing)
                }
                Log.e("PDF_PROCESSING_ERROR", "Błąd główny przetwarzania PDF", e)
                viewModel.diagnosisText = getString(R.string.error_pdf_processing_short)
            }
        } ?: run {
            Toast.makeText(requireContext(), getString(R.string.error_no_pdf_selected), Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeCSVFile(csvFile: File, chartImagePath: String?, pdfFile: File?) {
        val rivaltaStatus = viewModel.currentRivaltaStatus
        val csvLines = csvFile.readLines()
        val extractedData = ExtractData.parseLabResults(csvLines).toMutableMap()

        extractedData["GammopathyResult"] = viewModel.diagnosisText ?: getString(R.string.no_data)

        // ElectrophoresisAnalyzer używa wewnętrznie hardcodowanych stringów do analizy (np. nazw parametrów z PDF).
        // Zakładamy, że PDFy są zawsze w tym samym języku (polskim), więc klucze "Data", "Pacjent" itp. zostają.
        val electroResult = ElectrophoresisAnalyzer.assessFipRisk(
            extractedData,
            rivaltaStatus,
            requireContext()
        )

        viewModel.collectionDate = extractedData["Data"] as? String ?: getString(R.string.default_no_date)
        viewModel.patientName = extractedData["Pacjent"] as? String ?: getString(R.string.default_unknown)
        viewModel.patientAge = extractedData["Wiek"] as? String ?: getString(R.string.default_unknown)
        viewModel.patientSpecies = extractedData["Gatunek"] as? String ?: getString(R.string.default_not_provided)
        viewModel.patientBreed = extractedData["Rasa"] as? String ?: getString(R.string.default_not_provided)
        viewModel.patientGender = extractedData["Płeć"] as? String ?: getString(R.string.default_not_provided)
        viewModel.patientCoat = extractedData["Umaszczenie"] as? String ?: getString(R.string.default_not_provided)
        viewModel.rawDataJson = Gson().toJson(extractedData)

        val abnormalResults = mutableListOf<String>()
        // Klucze metadanych muszą pozostać zgodne z tym co zwraca parser PDF (ExtractData)
        val metadataKeys = setOf("Data", "Właściciel", "Pacjent", "Gatunek", "Rasa", "Płeć", "Wiek", "Lecznica", "Lekarz", "Rodzaj próbki", "Umaszczenie", "Mikrochip", "results", "GammopathyResult")

        for (key in extractedData.keys) {
            if (key.endsWith("Unit") || key.endsWith("RangeMin") || key.endsWith("RangeMax") || key.endsWith("Flag") || key in metadataKeys) {
                continue
            }

            val testName = key
            val value = extractedData[testName] as? String ?: continue
            val unit = extractedData["${testName}Unit"] as? String ?: ""

            val minRangeStr = extractedData["${testName}RangeMin"] as? String
            val maxRangeStr = extractedData["${testName}RangeMax"] as? String

            if (minRangeStr == null && maxRangeStr == null) {
                continue
            }
            val minRange = minRangeStr ?: "-"
            val maxRange = maxRangeStr ?: minRange

            if (isOutOfRange(value, minRange, maxRange)) {
                abnormalResults.add(getString(R.string.abnormal_result_format, testName, value, unit, minRange, maxRange))
            }
        }

        viewModel.results = abnormalResults.joinToString("\n")
        viewModel.pdfFile = pdfFile
        viewModel.chartImagePath = chartImagePath

        activity?.runOnUiThread {
            showResultsState()

            val patientInfo = getString(
                R.string.patient_info_full,
                viewModel.collectionDate,
                viewModel.patientName,
                viewModel.patientSpecies,
                viewModel.patientBreed,
                viewModel.patientGender,
                viewModel.patientAge,
                viewModel.patientCoat
            )

            val resultsText = if (abnormalResults.isNotEmpty()) {
                getString(R.string.results_abnormal_header, abnormalResults.joinToString("\n"))
            } else {
                getString(R.string.results_normal_header)
            }

            binding.buttonSaveOriginal.visibility =
                if (pdfFile != null) View.VISIBLE else View.GONE

            binding.buttonSaveOriginal.setOnClickListener {
                pdfFile?.absolutePath?.let { filePath ->
                    val fileName = "${viewModel.patientName}_${
                        SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(Date())
                    }"
                    savePdfToDownloadsUsingMediaStore(filePath, fileName)
                } ?: Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_no_pdf),
                    Toast.LENGTH_SHORT
                ).show()
            }

            binding.resultsTextView.text = patientInfo + resultsText
            binding.textHome.text = getString(R.string.home_results_title, viewModel.patientName)

            recalculateRiskAndUpdateUI()

            val tempResult = ResultEntity(
                patientName = viewModel.patientName ?: getString(R.string.default_unknown),
                age = viewModel.patientAge ?: getString(R.string.default_unknown),
                testResults = abnormalResults.joinToString("\n"),
                pdfFilePath = pdfFile?.absolutePath,
                imagePath = chartImagePath,
                collectionDate = viewModel.collectionDate,
                rawDataJson = viewModel.rawDataJson,
                diagnosis = viewModel.diagnosisText,
                rivaltaStatus = viewModel.currentRivaltaStatus,
                species = viewModel.patientSpecies,
                breed = viewModel.patientBreed,
                gender = viewModel.patientGender,
                coat = viewModel.patientCoat
            )
            sharedViewModel.setSelectedResult(tempResult)
        }
        displayImage(chartImagePath)
        saveCurrentResult()
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): Pair<List<List<String>>, String?> {
        val outputData = mutableListOf<List<String>>()
        try {
            val extractor = technology.tabula.ObjectExtractor(pdfDocument)
            val algorithm = technology.tabula.extractors.BasicExtractionAlgorithm()
            for (pageIndex in 0 until pdfDocument.numberOfPages) {
                val page = extractor.extract(pageIndex + 1)
                val tables = algorithm.extract(page)
                for (table in tables) {
                    for (row in table.rows) {
                        val rowData = row.map {
                            (it as? technology.tabula.RectangularTextContainer<*>)?.text?.replace("\r", " ")?.trim() ?: ""
                        }.filter { it.isNotBlank() }
                        if (rowData.isNotEmpty()) {
                            outputData.add(rowData)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PDF_PROCESSING", "Błąd ekstrakcji tabel z PDF", e)
        }
        return Pair(outputData, null)
    }

    private fun savePdfLocally(uri: Uri, filename: String): File? {
        return try {
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            storageDir?.mkdirs()
            val file = File(storageDir, filename)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: IOException) {
            Log.e("SAVE_PDF_LOCALLY", "Błąd zapisu PDF lokalnie", e)
            null
        }
    }

    private fun saveAsCSV(data: List<List<String>>, filename: String): File {
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        storageDir?.mkdirs()
        val file = File(storageDir, filename)
        file.writeText(data.joinToString("\n") { row -> row.joinToString(";") })
        return file
    }

    private fun uploadFileToFTP(file: File): Boolean {
        // ... (Logika FTP bez zmian, logi zostają po angielsku dla developerów)
        val ftpClient = FTPClient()
        var loggedIn = false
        try {
            val properties = Properties().apply {
                requireContext().assets.open("ftp_config.properties").use { load(it) }
            }
            val port = properties.getProperty("ftp.port", "21").toIntOrNull() ?: 21
            ftpClient.connect(properties.getProperty("ftp.host"), port)
            ftpClient.enterLocalPassiveMode()
            if (!ftpClient.login(properties.getProperty("ftp.user"), properties.getProperty("ftp.pass"))) return false
            loggedIn = true
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            FileInputStream(file).use { fis ->
                if (!ftpClient.storeFile(file.name, fis)) return@use false
                else true
            }
            return true
        } catch (ex: Exception) {
            Log.e("FTP_UPLOAD", "Error uploading file", ex)
            return false
        } finally {
            try {
                if (ftpClient.isConnected) {
                    if (loggedIn) ftpClient.logout()
                    ftpClient.disconnect()
                }
            } catch (ioe: IOException) { }
        }
    }

    private fun saveCurrentResult() {
        saveResultToDatabase(
            viewModel.patientName ?: getString(R.string.default_unknown),
            viewModel.patientAge ?: getString(R.string.default_unknown),
            viewModel.results,
            viewModel.pdfFile,
            viewModel.chartImagePath,
            viewModel.collectionDate,
            viewModel.rawDataJson,
            viewModel.diagnosisText,
            viewModel.currentRivaltaStatus
        )
    }
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    pdfUri = it
                    extractTablesWithTabula()
                }
            }
        }

    private fun isOutOfRange(valueStr: String, minStr: String, maxStr: String): Boolean {
        try {
            val v = valueStr.replace(Regex("[<>]"), "").replace(",", ".").toDouble()
            val minVal = minStr.replace(",", ".").toDoubleOrNull()
            val maxVal = maxStr.replace(",", ".").toDoubleOrNull()
            if (minVal == null && maxVal == null) return false
            if (minVal != null && v < minVal) return true
            if (maxVal != null && v > maxVal) return true
            return false
        } catch (e: Exception) { return false }
    }

    private fun saveResultToDatabase(
        patient: String, age: String, results: String?, pdfFile: File?,
        imagePath: String?, collectionDate: String?, rawDataJson: String?,
        diagnosisValueToSave: String?, rivaltaStatus: String
    ) {
        val pdfFilePath = pdfFile?.absolutePath
        val currentContext = context ?: return
        val db = AppDatabase.getDatabase(currentContext)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var r = db.resultDao().getResultByNameAge(patient, age)
                var result = ResultEntity(
                    patientName = patient,
                    age = age,
                    testResults = if (results.isNullOrEmpty()) r.testResults else results,
                    pdfFilePath = pdfFilePath,
                    imagePath = imagePath,
                    collectionDate = collectionDate,
                    rawDataJson = rawDataJson,
                    diagnosis = diagnosisValueToSave,
                    rivaltaStatus = rivaltaStatus,
                    species = viewModel.patientSpecies,
                    breed = viewModel.patientBreed,
                    gender = viewModel.patientGender,
                    coat = viewModel.patientCoat
                )
                db.resultDao().deleteDuplicates(patient, age)
                db.resultDao().insertResult(result)
                withContext(Dispatchers.Main) {
                    sharedViewModel.setSelectedResult(result)
                }
            } catch (e: Exception) {
                Log.e("SaveEntity", "DB Save Error", e)
                activity?.runOnUiThread {
                    Toast.makeText(currentContext, getString(R.string.error_db_save), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun savePdfToDownloadsUsingMediaStore(filePath: String, fileName: String) {
        val currentContext = context ?: return
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                Toast.makeText(currentContext, getString(R.string.error_source_file_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            val resolver = currentContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Cannot create file in MediaStore")
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input -> input.copyTo(output) }
            }
            val pathHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${Environment.DIRECTORY_DOWNLOADS}/$fileName.pdf"
            } else {
                getString(R.string.downloads_folder_name)
            }
            Toast.makeText(currentContext, getString(R.string.toast_pdf_saved, pathHint), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(currentContext, getString(R.string.error_pdf_save_generic, e.message), Toast.LENGTH_SHORT).show()
            Log.e("PDF_EXPORT", "Error saving PDF", e)
        }
    }

    private fun displayImage(imagePath: String?) {
        activity?.runOnUiThread {
            imagePath?.let { path ->
                val file = File(path)
                if (!file.exists()) {
                    binding.chartImageView.visibility = View.GONE
                    return@let
                }
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    binding.chartImageView.visibility = View.GONE
                    return@let
                }
                binding.chartImageView.visibility = View.VISIBLE
                binding.chartImageView.setImageBitmap(bitmap)
            } ?: run {
                binding.chartImageView.visibility = View.GONE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun displayExistingResult(result: ResultEntity) {
        showResultsState()
        _binding?.let { binding ->
            binding.buttonSaveOriginal.visibility =
                if (result.pdfFilePath != null) View.VISIBLE else View.GONE

            viewModel.patientName = result.patientName
            viewModel.patientAge = result.age
            viewModel.collectionDate = result.collectionDate
            viewModel.patientSpecies = result.species
            viewModel.patientBreed = result.breed
            viewModel.patientGender = result.gender
            viewModel.patientCoat = result.coat
            // Pobierz domyślną opcję jeśli null
            val defaultStatus = resources.getStringArray(R.array.rivalta_options)[0]
            viewModel.currentRivaltaStatus = result.rivaltaStatus ?: defaultStatus
            viewModel.diagnosisText = result.diagnosis ?: ""
            viewModel.chartImagePath = result.imagePath
            viewModel.pdfFilePath = result.pdfFilePath

            binding.textHome.text = getString(R.string.home_results_title_age, result.patientName, result.age)

            val patientInfo = getString(R.string.patient_info_short,
                result.collectionDate,
                result.patientName,
                result.species ?: getString(R.string.default_not_provided),
                result.breed ?: getString(R.string.default_not_provided),
                result.gender ?: getString(R.string.default_not_provided),
                result.age,
                result.coat ?: getString(R.string.default_not_provided)
            )

            binding.resultsTextView.text = buildString {
                append(patientInfo)
                append("\n\n")
                append(result.testResults ?: getString(R.string.no_result_data))
                append("\n\n")
            }

            // Ustawienie spinnera na podstawie zapisanej wartości.
            // UWAGA: Jeśli język aplikacji został zmieniony po zapisaniu wyniku,
            // tekst może nie pasować do opcji w nowym języku.
            val rivaltaOptions = resources.getStringArray(R.array.rivalta_options).toList()
            val position = rivaltaOptions.indexOf(viewModel.currentRivaltaStatus)
            if (position >= 0) {
                binding.rivaltaSpinner.setSelection(position)
            }

            val extractedData = Gson().fromJson(result.rawDataJson, Map::class.java) as? Map<String, Any>
                ?: emptyMap()

            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(
                extractedData,
                viewModel.currentRivaltaStatus,
                requireContext()
            )

            updateRiskIndicator(electroResult.riskPercentage)
            recalculateRiskAndUpdateUI()

            result.pdfFilePath?.let { path -> viewModel.pdfFile = File(path) }
            result.imagePath?.let { displayImage(it) }
        }
    }

    private fun recalculateRiskAndUpdateUI() {
        val rawDataJson = viewModel.rawDataJson
        if (rawDataJson != null) {
            val extractedData = Gson().fromJson(rawDataJson, Map::class.java) as? MutableMap<String, Any>
            if (extractedData != null) {
                extractedData["GammopathyResult"] = viewModel.diagnosisText ?: getString(R.string.no_data)
                val electroResult = ElectrophoresisAnalyzer.assessFipRisk(
                    extractedData,
                    viewModel.currentRivaltaStatus,
                    requireContext()
                )
                updateRiskIndicator(electroResult.riskPercentage)
                viewModel.rawDataJson = Gson().toJson(extractedData)
            }
        }
    }

    private fun restoreUIFromResult(result: ResultEntity) {
        showResultsState()
        val defaultStatus = resources.getStringArray(R.array.rivalta_options)[0]
        viewModel.apply {
            patientName = result.patientName
            patientAge = result.age
            collectionDate = result.collectionDate
            patientSpecies = result.species
            patientBreed = result.breed
            patientGender = result.gender
            patientCoat = result.coat
            currentRivaltaStatus = result.rivaltaStatus ?: defaultStatus
            diagnosisText = result.diagnosis
            chartImagePath = result.imagePath
            pdfFilePath = result.pdfFilePath
            rawDataJson = result.rawDataJson
            results = result.testResults
            pdfFilePath?.let { path -> pdfFile = File(path) }
        }

        val patientInfo = getString(R.string.patient_info_short,
            viewModel.collectionDate,
            viewModel.patientName,
            viewModel.patientSpecies ?: getString(R.string.default_not_provided),
            viewModel.patientBreed ?: getString(R.string.default_not_provided),
            viewModel.patientGender ?: getString(R.string.default_not_provided),
            viewModel.patientAge,
            viewModel.patientCoat ?: getString(R.string.default_not_provided)
        )

        val resultsText = if (!viewModel.results.isNullOrEmpty()) {
            getString(R.string.results_abnormal_header, viewModel.results)
        } else {
            getString(R.string.results_normal_header)
        }

        binding.resultsTextView.text = patientInfo + resultsText
        binding.textHome.text = getString(R.string.home_results_title, viewModel.patientName)

        binding.rivaltaSpinner.post {
            val rivaltaOptions = resources.getStringArray(R.array.rivalta_options).toList()
            val position = rivaltaOptions.indexOf(viewModel.currentRivaltaStatus)
            if (position >= 0) {
                binding.rivaltaSpinner.setSelection(position)
            }
        }

        binding.buttonSaveOriginal.visibility =
            if (viewModel.pdfFile != null || viewModel.pdfFilePath != null) View.VISIBLE else View.GONE

        viewModel.chartImagePath?.let { displayImage(it) }
        recalculateRiskAndUpdateUI()
    }

    override fun onResume() {
        super.onResume()
        val currentResult = sharedViewModel.selectedResult.value
        if (currentResult != null) {
            if (binding.resultsTextView.text.isNullOrEmpty() || viewModel.patientName != currentResult.patientName || viewModel.collectionDate != currentResult.collectionDate) {
                restoreUIFromResult(currentResult)
            }
        } else {
            showInitialState()
            loadRecentHistory()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}