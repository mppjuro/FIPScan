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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.util.Locale
import com.example.fipscan.PdfChartExtractor

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private lateinit var pdfChartExtractor: PdfChartExtractor

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
                displayExistingResult(result)
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
                // Generuj wspólny timestamp dla wszystkich plików
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val pdfFilename = "input_$timestamp.pdf"
                val csvFilename = "data_$timestamp.csv"
                val chartFilename = "chart_$timestamp.png"

                // Zapisz PDF z nową nazwą
                val pdfFile = savePdfLocally(uri, pdfFilename)
                if (pdfFile != null) {
                    Thread { uploadFileToFTP(pdfFile) }.start()
                }

                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)

                    // Ekstrakcja i zapis wykresu
                    var chartImagePath: String? = null
                    var newChartFile: File? = null
                    try {
                        chartImagePath = pdfChartExtractor.extractChartFromPDF(pdfFile)
                        if (chartImagePath != null) {
                            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                            newChartFile = File(storageDir, chartFilename)

                            // Kopiuj do nowej lokalizacji z timestampem
                            File(chartImagePath).copyTo(newChartFile!!, overwrite = true)
                            File(chartImagePath).delete() // Usuń stary plik

                            // Wyślij wykres na FTP
                            Thread { uploadFileToFTP(newChartFile) }.start()
                        }
                    } catch (e: Exception) {
                        Log.e("CHART_EXTRACT", "Błąd przetwarzania wykresu", e)
                    }

                    val (tablesData, _) = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        binding.resultsTextView.text = "Nie znaleziono tabel!"
                        return
                    }

                    // Zapisz i wyślij CSV
                    val csvFile = saveAsCSV(tablesData, csvFilename)
                    Thread {
                        uploadFileToFTP(csvFile)
                        analyzeCSVFile(csvFile, newChartFile?.absolutePath, pdfFile)
                    }.start()
                }
            } catch (e: Exception) {
                binding.resultsTextView.text = "Błąd przetwarzania tabel!"
                Log.e("TABULA_ERROR", "Błąd", e)
            }
        }
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): Pair<List<List<String>>, String?> {
        val outputData = mutableListOf<List<String>>()
        var chartImagePath: String? = null
        var bestMatchScore = 0.0

        try {
            val extractor = technology.tabula.ObjectExtractor(pdfDocument)
            val algorithm = technology.tabula.extractors.BasicExtractionAlgorithm()

            for (pageIndex in 0 until pdfDocument.numberOfPages) {
                val page = extractor.extract(pageIndex + 1)
                val pdfBoxPage = pdfDocument.getPage(pageIndex)

                // Przetwarzanie tabel
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
            Log.e("PDF_PROCESSING", "Błąd", e)
        }
        return Pair(outputData, chartImagePath)
    }

    private fun savePdfLocally(uri: Uri, filename: String): File? {
        return try {
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(storageDir, filename)

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file
        } catch (e: IOException) {
            null
        }
    }

    private fun saveAsCSV(data: List<List<String>>, filename: String): File {
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(storageDir, filename)
        file.writeText(data.joinToString("\n") { row -> row.joinToString(";") })
        return file
    }

    private fun uploadFileToFTP(file: File): Boolean {
        val ftpClient = FTPClient()
        return try {
            val properties = Properties().apply {
                requireContext().assets.open("ftp_config.properties").use { load(it) }
            }

            ftpClient.connect(properties.getProperty("ftp.host"), 21)
            ftpClient.enterLocalPassiveMode()

            if (!ftpClient.login(properties.getProperty("ftp.user"), properties.getProperty("ftp.pass"))) {
                Log.e("FTP_LOGIN", "Logowanie nieudane: ${ftpClient.replyString}")
                ftpClient.disconnect()
                return false
            }

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

            Log.d("FTP_UPLOAD", "Aktualny katalog FTP: ${ftpClient.printWorkingDirectory()}")

            FileInputStream(file).use { fis ->
                if (!ftpClient.storeFile(file.name, fis)) {
                    Log.e("FTP_UPLOAD", "Nie udało się wysłać pliku ${file.name}: ${ftpClient.replyString}")
                    ftpClient.logout()
                    ftpClient.disconnect()
                    return false
                }
            }

            Log.d("FTP_UPLOAD", "Plik ${file.name} wysłany poprawnie")

            ftpClient.logout()
            ftpClient.disconnect()

            true

        } catch (ex: Exception) {
            Log.e("FTP_UPLOAD", "Błąd wysyłania pliku FTP", ex)
            if (ftpClient.isConnected) {
                ftpClient.disconnect()
            }
            false
        }
    }

    private fun analyzeCSVFile(csvFile: File, chartImagePath: String?, pdfFile: File?) {        val csvLines = csvFile.readLines()
        val extractedData = ExtractData.parseLabResults(csvLines)

        val patient = extractedData["Pacjent"] as? String ?: "Nieznany"
        val species = extractedData["Gatunek"] as? String ?: "Nieznany"
        val breed = extractedData["Rasa"] as? String ?: "Nieznana"
        val sex = extractedData["Płeć"] as? String ?: "Nieznana"
        val age = extractedData["Wiek"] as? String ?: "Nieznany"
        val color = extractedData["Umaszczenie"] as? String ?: "Nieznane"
        val microchip = extractedData["Mikrochip"] as? String

        val catInfo = """
        🐱 Pacjent: $patient
        🐾 Gatunek: $species
        🏷️ Rasa: $breed
        ⚥ Płeć: $sex
        📅 Wiek: $age
        🎨 Umaszczenie: $color
    """.trimIndent()

        val finalInfo = if (!microchip.isNullOrEmpty()) {
            "$catInfo\n🔍 Chip: $microchip"
        } else {
            catInfo
        }

        // Lista wyników badań poza normą
        val abnormalResults = mutableListOf<String>()

        // Iteracja po danych i wyszukiwanie wyników badań
        for (key in extractedData.keys) {
            if (key.endsWith("Unit") || key.endsWith("RangeMin") || key.endsWith("RangeMax")) {
                continue // Pomijamy jednostki i zakresy norm
            }

            val testName = key
            val value = extractedData[testName] as? String ?: continue
            val unit = extractedData["${testName}Unit"] as? String ?: ""
            val minRange = extractedData["${testName}RangeMin"] as? String ?: continue
            val maxRange = extractedData["${testName}RangeMax"] as? String ?: minRange // Jeśli nie ma max, traktujemy min jako granicę

            if (isOutOfRange(value, minRange, maxRange)) {
                abnormalResults.add("$testName: $value $unit ($minRange - $maxRange)")
            }
        }

        activity?.runOnUiThread {
            binding.resultsTextView.text = if (abnormalResults.isNotEmpty()) {
                val resultsText = abnormalResults.joinToString("\n")
                "$finalInfo\n\n📊 Wyniki poza normą:\nBadanie: wynik (norma) jednostka\n$resultsText\n\n\n"
            } else {
                "$finalInfo\n\n✅ Wszystkie wyniki w normie"
            }
        }

        binding.scrollView.postDelayed({
            if (binding.resultsTextView.text.isNotEmpty()) {
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }, 200) // Krótkie opóźnienie, aby UI się odświeżył

        binding.textHome.text = "Wyniki: ${patient}"
        displayImage(chartImagePath)
        saveResultToDatabase(patient, age, abnormalResults.joinToString("\n"), pdfFile, chartImagePath)
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

    private fun isOutOfRange(value: String, min: String, max: String): Boolean {
        return try {
            val v = value.replace(Regex("[<>]"), "").replace(",", ".").toDouble()
            val minVal = min.replace(",", ".").toDoubleOrNull() ?: return false
            val maxVal = max.replace(",", ".").toDoubleOrNull() ?: return false
            v < minVal || v > maxVal
        } catch (e: Exception) {
            Log.e("ERROR", "Błąd", e)
            false
        }
    }

    private fun saveResultToDatabase(
        patient: String,
        age: String,
        results: String,
        pdfFile: File?,  // Zmień typ parametru
        imagePath: String?
    ) {
        val pdfUri = pdfFile?.let { file ->
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            ).toString()
        }

        val result = ResultEntity(
            patientName = patient,
            age = age,
            testResults = results,
            pdfFilePath = pdfUri,
            imagePath = imagePath
        )
        val db = AppDatabase.getDatabase(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            db.resultDao().deleteDuplicates(patient, age)
            db.resultDao().insertResult(result)
        }
    }

    private fun savePdfToDownloadsUsingMediaStore(uriString: String, fileName: String) {
        try {
            val sourceUri = Uri.parse(uriString)
            val resolver = requireContext().contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val destinationUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Nie można utworzyć pliku")

            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(destinationUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(
                requireContext(),
                "Zapisano w folderze Downloads",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Błąd zapisu: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("PDF_SAVE", "Błąd", e)
        }
    }

    private fun displayImage(imagePath: String?) {
        imagePath?.let {
            Log.d("IMAGE_DISPLAY", "Próbuję wczytać wykres: $it")

            val file = File(it)
            if (!file.exists()) {
                Log.e("IMAGE_DISPLAY", "Błąd: Plik nie istnieje!")
                return
            }

            val bitmap = BitmapFactory.decodeFile(it)
            if (bitmap == null) {
                Log.e("IMAGE_DISPLAY", "Błąd: Nie udało się załadować bitmapy!")
                return
            }

            activity?.runOnUiThread {
                binding.chartImageView.visibility = View.VISIBLE
                binding.chartImageView.setImageBitmap(bitmap)
                Log.d("IMAGE_DISPLAY", "Wykres poprawnie wczytany")
            }
        } ?: Log.e("IMAGE_DISPLAY", "Nie znaleziono ścieżki do obrazu!")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun displayExistingResult(result: ResultEntity) {
        binding.textHome.text = "Wyniki: ${result.patientName}, ${result.age}"
        binding.resultsTextView.text = "Wyniki poza normą:\n" + result.testResults + "\n\n"
        result.imagePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            binding.chartImageView.visibility = View.VISIBLE
            binding.chartImageView.setImageBitmap(bitmap)
        }

        // Nowa funkcjonalność
        binding.buttonSaveOriginal.visibility = if (!result.pdfFilePath.isNullOrEmpty()) View.VISIBLE else View.GONE
        binding.buttonSaveOriginal.setOnClickListener {
            result.pdfFilePath?.let { uriString ->
                savePdfToDownloadsUsingMediaStore(uriString, result.patientName)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfToDownloadsUsingMediaStore(sourceUri: Uri, fileName: String) {
        try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Nie można utworzyć pliku")

            resolver.openOutputStream(uri)?.use { outputStream ->
                resolver.openInputStream(sourceUri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Toast.makeText(
                requireContext(),
                "Zapisano w folderze Downloads",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Błąd zapisu: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("PDF_SAVE", "Błąd zapisu", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
