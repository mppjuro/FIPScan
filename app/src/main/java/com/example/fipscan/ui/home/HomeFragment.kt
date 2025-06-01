package com.example.fipscan.ui.home

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.fipscan.ExtractData
import com.example.fipscan.databinding.FragmentHomeBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.fipscan.AppDatabase
import com.example.fipscan.ResultEntity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.Locale
import com.example.fipscan.PdfChartExtractor
import com.google.gson.Gson
import com.example.fipscan.BarChartLevelAnalyzer

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private lateinit var pdfChartExtractor: PdfChartExtractor
    private var diagnosis: String? = null
    var uploaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        PDFBoxResourceLoader.init(requireContext())
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.buttonLoadPdf.setOnClickListener { openFilePicker() }

        pdfChartExtractor = PdfChartExtractor(requireContext())

        arguments?.let {
            val args = HomeFragmentArgs.fromBundle(it)
            args.result?.let { result ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    displayExistingResult(result)
                } else {
                    Toast.makeText(requireContext(), "Funkcja historii wyników wymaga Androida Q+", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return binding.root
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
                val chartFilename = "chart_$timestamp.png"

                val pdfFile = savePdfLocally(uri, pdfFilename)
                if (pdfFile != null) {
                    Thread { uploadFileToFTP(pdfFile) }.start()
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Nie udało się zapisać pliku PDF lokalnie.", Toast.LENGTH_LONG).show()
                    }
                    return@extractTablesWithTabula
                }

                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)
                    var finalChartFileToSave: File? = null

                    try {
                        val extractionResult = pdfChartExtractor.extractChartFromPDF(pdfFile)
                        if (extractionResult != null && extractionResult.imagePaths.isNotEmpty()) {
                            val originalChartPathFromExtractor = extractionResult.imagePaths[0]
                            val barChartImagePath = extractionResult.imagePaths.getOrNull(1)

                            val chartToDisplayAndSavePath = originalChartPathFromExtractor
                            finalChartFileToSave = File(chartToDisplayAndSavePath)


                            // Log bar sections if available
                            extractionResult.barSections?.let { sections ->
                                Log.d("BAR_SECTIONS", "Sekcja 1: ${sections.section1}")
                                Log.d("BAR_SECTIONS", "Sekcja 2: ${sections.section2}")
                                Log.d("BAR_SECTIONS", "Sekcja 3: ${sections.section3}")
                                Log.d("BAR_SECTIONS", "Sekcja 4: ${sections.section4}")
                                this.diagnosis = BarChartLevelAnalyzer.analyzeGammapathy(sections.section1, sections.section4) //
                            } ?: run { this.diagnosis = "Brak danych z sekcji wykresu" }
                            Log.d("GAMMAPATHY", "Obliczona diagnoza z wykresu: ${this.diagnosis}")

                            if(finalChartFileToSave.exists()) {
                                Thread { uploadFileToFTP(finalChartFileToSave) }.start()
                            }

                            if (barChartImagePath != null) {
                                val barChartFile = File(barChartImagePath)
                                if (barChartFile.exists()) {
                                    Thread {
                                        uploadFileToFTP(barChartFile)
                                        try {
                                            val bitmap = BitmapFactory.decodeFile(barChartImagePath)
                                            val origBitmap = BitmapFactory.decodeFile(chartToDisplayAndSavePath) // use the main chart as original

                                            if (bitmap == null || origBitmap == null) {
                                                Log.e("BAR_LEVELS", "Nie udało się załadować bitmapy do analizy słupków! bitmap=$bitmap, origBitmap=$origBitmap")
                                            } else {
                                                val analysisResult = BarChartLevelAnalyzer.analyzeBarHeights(bitmap, origBitmap) //

                                                Log.d("BAR_LEVELS", "Poziomy słupków (% wysokości):")
                                                analysisResult.barHeights.forEachIndexed { index, value ->
                                                    Log.d("BAR_LEVELS", "Słupek ${index + 1}: %.2f%%".format(value))
                                                }
                                                Log.d("BAR_LEVELS", "Wykryte kolumny z czerwonymi pikselami: ${analysisResult.redColumnIndices}")
                                                Log.d("BAR_LEVELS", "Wymiary obrazu: szerokość=${analysisResult.imageWidth}, wysokość=${analysisResult.imageHeight}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("BAR_LEVELS", "Błąd analizy słupków", e)
                                        }
                                    }.start()
                                }
                            }
                        } else {
                            Log.w("CHART_EXTRACT", "Nie znaleziono wykresu lub wystąpił błąd ekstrakcji.")
                            this.diagnosis = "Brak wykresu"
                        }
                    } catch (e: Exception) {
                        Log.e("CHART_EXTRACT", "Błąd przetwarzania wykresu", e)
                        this.diagnosis = "Błąd analizy wykresu"
                    }

                    val (tablesData, _) = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        activity?.runOnUiThread {
                            _binding?.resultsTextView?.text = "Nie znaleziono tabel!"
                        }
                        return@use
                    }

                    val csvFile = saveAsCSV(tablesData, csvFilename)
                    Thread {
                        uploadFileToFTP(csvFile)
                        analyzeCSVFile(csvFile, finalChartFileToSave?.absolutePath, pdfFile)
                    }.start()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    _binding?.resultsTextView?.text = "Błąd przetwarzania pliku PDF!"
                }
                Log.e("PDF_PROCESSING_ERROR", "Błąd główny przetwarzania PDF", e)
                this.diagnosis = "Błąd przetwarzania PDF"
            }
        } ?: run {
            Toast.makeText(requireContext(), "Nie wybrano pliku PDF", Toast.LENGTH_SHORT).show()
        }
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
        val ftpClient = FTPClient()
        var loggedIn = false

        try {
            val properties = Properties().apply {
                requireContext().assets.open("ftp_config.properties").use { load(it) }
            }

            val port = properties.getProperty("ftp.port", "21").toIntOrNull() ?: 21
            ftpClient.connect(properties.getProperty("ftp.host"), port)
            ftpClient.enterLocalPassiveMode()

            if (!ftpClient.login(properties.getProperty("ftp.user"), properties.getProperty("ftp.pass"))) {
                Log.e("FTP_LOGIN", "Logowanie nieudane: ${ftpClient.replyString}")
                return false
            }
            loggedIn = true

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
            Log.d("FTP_UPLOAD", "Aktualny katalog FTP: ${ftpClient.printWorkingDirectory()}")

            val storedSuccessfully = FileInputStream(file).use { fis ->
                if (!ftpClient.storeFile(file.name, fis)) {
                    Log.e("FTP_UPLOAD", "Nie udało się wysłać pliku ${file.name}: ${ftpClient.replyString}")
                    return@use false
                }
                true
            }

            if (!storedSuccessfully) {
                return false
            }

            Log.d("FTP_UPLOAD", "Plik ${file.name} wysłany poprawnie.")
            return true

        } catch (ex: Exception) {
            Log.e("FTP_UPLOAD", "Błąd wysyłania pliku FTP ${file.name}", ex)
            return false
        } finally {
            try {
                if (ftpClient.isConnected) {
                    if (loggedIn) {
                        ftpClient.logout()
                        Log.d("FTP_LOGOUT", "Wylogowano z FTP.")
                    }
                    ftpClient.disconnect()
                    Log.d("FTP_DISCONNECT", "Rozłączono z FTP.")
                }
            } catch (ioe: IOException) {
                Log.e("FTP_CLEANUP", "Błąd podczas zamykania połączenia FTP: ${ioe.message}", ioe)
            }
        }
    }


    private fun analyzeCSVFile(csvFile: File, chartImagePath: String?, pdfFile: File?) {
        val csvLines = csvFile.readLines()
        val extractedData = ExtractData.parseLabResults(csvLines)

        val collectionDate = extractedData["Data"] as? String ?: "Brak daty"
        val patient = extractedData["Pacjent"] as? String ?: "Nieznany"
        val age = extractedData["Wiek"] as? String ?: "Nieznany"
        val rawJson = Gson().toJson(extractedData)

        val abnormalResults = mutableListOf<String>()
        val metadataKeys = setOf("Data", "Właściciel", "Pacjent", "Gatunek", "Rasa", "Płeć", "Wiek", "Lecznica", "Lekarz", "Rodzaj próbki", "Umaszczenie", "Mikrochip", "results")


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
                abnormalResults.add("$testName: $value $unit (norma: $minRange - $maxRange)")
            }
        }

        activity?.runOnUiThread {
            val currentBinding = _binding ?: return@runOnUiThread

            val patientInfo = """
                📆 Data: $collectionDate
                🐱 Pacjent: $patient
                🐾 Gatunek: ${extractedData["Gatunek"] ?: "nie podano"}
                🏷️ Rasa: ${extractedData["Rasa"] ?: "nie podano"}
                ⚥ Płeć: ${extractedData["Płeć"] ?: "nie podano"}
                📅 Wiek: $age
                🎨 Umaszczenie: ${extractedData["Umaszczenie"] ?: "nie podano"}
            """.trimIndent()

            currentBinding.resultsTextView.text = if (abnormalResults.isNotEmpty()) {
                val resultsText = abnormalResults.joinToString("\n")
                "$patientInfo\n\n📊 Wyniki poza normą:\nBadanie: wynik (norma) jednostka\n$resultsText\n"
            } else {
                "$patientInfo\n\n✅ Wszystkie wyniki w normie"
            }

            currentBinding.scrollView.post {
                _binding?.takeIf { isAdded }?.let { cb ->
                    if (cb.resultsTextView.text.isNotEmpty()) {
                        cb.scrollView.smoothScrollTo(0, 0)
                    }
                }
            }
            currentBinding.textHome.text = "Wyniki: $patient"
            currentBinding.textScanResult.text = "Diagnoza z wykresu: ${this.diagnosis ?: "Brak danych"}\n\n\n"
        }

        displayImage(chartImagePath)

        Log.d("AnalyzeCSV", "Przekazuję do saveResultToDatabase - wartość zmiennej członkowskiej 'diagnosis': ${this.diagnosis}")
        saveResultToDatabase(
            patient, age, abnormalResults.joinToString("\n"),
            pdfFile, chartImagePath, collectionDate, rawJson,
            this.diagnosis
        )
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    pdfUri = it
                    extractTablesWithTabula()
                    uploaded = true
                }
            }
        }

    private fun isOutOfRange(valueStr: String, minStr: String, maxStr: String): Boolean {
        try {
            val v = valueStr.replace(Regex("[<>]"), "").replace(",", ".").toDouble()

            val minVal = minStr.replace(",", ".").toDoubleOrNull()
            val maxVal = maxStr.replace(",", ".").toDoubleOrNull()

            if (minVal == null && maxVal == null) {
                return false
            }

            var outOfBounds = false
            if (minVal != null && v < minVal) {
                outOfBounds = true
            }
            if (maxVal != null && v > maxVal) {
                outOfBounds = true
            }
            return outOfBounds
        } catch (e: NumberFormatException) {
            return false
        } catch (e: Exception) {
            Log.e("isOutOfRange_ERROR", "Error in isOutOfRange for value:$valueStr, min:$minStr, max:$maxStr", e)
            return false
        }
    }


    private fun saveResultToDatabase(
        patient: String,
        age: String,
        results: String,
        pdfFile: File?,
        imagePath: String?,
        collectionDate: String?,
        rawDataJson: String?,
        diagnosisValueToSave: String?
    ) {
        val pdfFilePath = pdfFile?.absolutePath

        val result = ResultEntity(
            patientName = patient,
            age = age,
            testResults = results,
            pdfFilePath = pdfFilePath,
            imagePath = imagePath,
            collectionDate = collectionDate,
            rawDataJson = rawDataJson,
            diagnosis = diagnosisValueToSave
        )

        Log.d("SaveEntity", "Próba zapisu/zastąpienia ResultEntity: $result")

        val currentContext = context ?: return
        val db = AppDatabase.getDatabase(currentContext) //
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("SaveEntity", "Próba usunięcia duplikatów dla: $patient, $age")
                db.resultDao().deleteDuplicates(patient, age)

                Log.d("SaveEntity", "Wywołanie insertResult dla pacjenta: $patient (strategia: REPLACE)")
                db.resultDao().insertResult(result)
                Log.i("SaveEntity", "Rekord dla pacjenta '$patient' zapisany/zastąpiony pomyślnie.")

            } catch (e: Exception) {
                Log.e("SaveEntity", "!!! BŁĄD podczas operacji na bazie danych !!!", e)
                activity?.runOnUiThread {
                    context?.let { ctx ->
                        Toast.makeText(ctx, "Błąd zapisu do bazy danych!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun savePdfToDownloadsUsingMediaStore(filePath: String, fileName: String) {
        val currentContext = context ?: return
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                Toast.makeText(currentContext, "Plik źródłowy nie istnieje", Toast.LENGTH_SHORT).show()
                return
            }

            val resolver = currentContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()
                    val fullPath = File(downloadsDir, "$fileName.pdf")
                    put(MediaStore.MediaColumns.DATA, fullPath.absolutePath)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Nie można utworzyć pliku w MediaStore")

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            val pathHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${Environment.DIRECTORY_DOWNLOADS}/$fileName.pdf"
            } else {
                "Pobrane"
            }
            Toast.makeText(currentContext, "Zapisano w: $pathHint", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(currentContext, "Błąd zapisu PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("PDF_EXPORT", "Błąd zapisu PDF do pobranych", e)
        }
    }


    private fun displayImage(imagePath: String?) {
        activity?.runOnUiThread {
            val currentBinding = _binding ?: return@runOnUiThread // Guard

            imagePath?.let { path ->
                Log.d("IMAGE_DISPLAY", "Próbuję wczytać wykres: $path")
                val file = File(path)
                if (!file.exists()) {
                    Log.e("IMAGE_DISPLAY", "Błąd: Plik nie istnieje! $path")
                    currentBinding.chartImageView.visibility = View.GONE
                    return@let
                }
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    Log.e("IMAGE_DISPLAY", "Błąd: Nie udało się załadować bitmapy! $path")
                    currentBinding.chartImageView.visibility = View.GONE
                    return@let
                }
                currentBinding.chartImageView.visibility = View.VISIBLE
                currentBinding.chartImageView.setImageBitmap(bitmap)
                Log.d("IMAGE_DISPLAY", "Wykres poprawnie wczytany")
            } ?: run {
                Log.e("IMAGE_DISPLAY", "Nie znaleziono ścieżki do obrazu!")
                currentBinding.chartImageView.visibility = View.GONE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun displayExistingResult(result: ResultEntity) {
        val currentBinding = _binding ?: return

        currentBinding.textHome.text = "Wyniki: ${result.patientName}, ${result.age}"
        currentBinding.resultsTextView.text = "Wyniki poza normą:\n${result.testResults}"
        currentBinding.textScanResult.text = "Diagnoza z wykresu: ${result.diagnosis ?: "brak danych"}\n\n\n"

        result.imagePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            currentBinding.chartImageView.setImageBitmap(bitmap)
            currentBinding.chartImageView.visibility = View.VISIBLE
        } ?: run {
            currentBinding.chartImageView.visibility = View.GONE
        }

        currentBinding.buttonSaveOriginal.visibility = View.VISIBLE
        currentBinding.buttonSaveOriginal.setOnClickListener {
            _binding?.let {
                result.pdfFilePath?.let { filePath ->
                    val fileName = "${result.patientName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
                    savePdfToDownloadsUsingMediaStore(filePath, fileName)
                } ?: Toast.makeText(context, "Brak ścieżki do pliku PDF.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}