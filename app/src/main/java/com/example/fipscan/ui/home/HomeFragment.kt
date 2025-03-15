package com.example.fipscan.ui.home

import android.app.Activity
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
import android.widget.ScrollView
import com.example.fipscan.R
import com.example.fipscan.AppDatabase
import com.example.fipscan.ResultEntity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        PDFBoxResourceLoader.init(requireContext())
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.buttonLoadPdf.setOnClickListener { openFilePicker() }

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
                val pdfFile = savePdfLocally(uri)  // Zapisujemy plik PDF lokalnie
                if (pdfFile != null) {
                    Thread { uploadFileToFTP(pdfFile) }.start()  // Wysyłamy plik PDF na serwer FTP
                }

                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)

                    val tablesData = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        binding.resultsTextView.text = "Nie znaleziono tabel!"
                        return
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val csvFilename = "data_$timestamp.csv"
                    val csvFile = saveAsCSV(tablesData, csvFilename)

                    Thread {
                        uploadFileToFTP(csvFile)  // Wysyłamy plik CSV na serwer FTP
                        analyzeCSVFile(csvFile)
                    }.start()
                }
            } catch (e: Exception) {
                binding.resultsTextView.text = "Błąd przetwarzania tabel!"
                Log.e("TABULA_ERROR", "Błąd", e)
            }
        }
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): List<List<String>> {
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
            Log.e("TABULA_ERROR", "Błąd podczas ekstrakcji tabeli", e)
        }
        return outputData
    }

    private fun savePdfLocally(uri: Uri): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFilename = "input_$timestamp.pdf"
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(storageDir, pdfFilename)

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("PDF_SAVE", "Plik PDF zapisany: ${file.absolutePath}")
            file
        } catch (e: IOException) {
            Log.e("PDF_SAVE_ERROR", "Błąd zapisu pliku PDF", e)
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

    private fun analyzeCSVFile(csvFile: File) {
        val csvLines = csvFile.readLines()
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

        // Wyświetlanie wyników w UI
        activity?.runOnUiThread {
            binding.resultsTextView.text = if (abnormalResults.isNotEmpty()) {
                val resultsText = abnormalResults.joinToString("\n")
                "$finalInfo\n\n📊 Wyniki poza normą:\nBadanie: wynik (norma) jednostka\n$resultsText\n\n\n"
            } else {
                "$finalInfo\n\n✅ Wszystkie wyniki w normie"
            }
        }

        // **🔹 Przewinięcie na górę tylko raz, gdy dane zostaną załadowane**
        binding.scrollView.postDelayed({
            if (binding.resultsTextView.text.isNotEmpty()) {
                //binding.scrollView.fullScroll(View.FOCUS_UP)
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }, 200) // Krótkie opóźnienie, aby UI się odświeżył

        saveResultToDatabase(patient, age, abnormalResults.joinToString("\n"), pdfUri?.path)
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
            if (value.startsWith("<") || value.startsWith(">")) return false // Dla wartości "<2.00" pomijamy sprawdzanie
            val v = value.toDouble()
            val minVal = min.replace(",", ".").toDoubleOrNull() ?: return false
            val maxVal = max.replace(",", ".").toDoubleOrNull() ?: return false
            v < minVal || v > maxVal
        } catch (e: Exception) {
            Log.e("ERROR", "Błąd", e)
            false
        }
    }

    private fun saveResultToDatabase(patient: String, age: String, results: String, pdfPath: String?) {
        val db = AppDatabase.getDatabase(requireContext())
        val result = ResultEntity(patientName = patient, age = age, testResults = results, pdfFilePath = pdfPath, imagePath = null)

        lifecycleScope.launch(Dispatchers.IO) {
            db.resultDao().insertResult(result)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
