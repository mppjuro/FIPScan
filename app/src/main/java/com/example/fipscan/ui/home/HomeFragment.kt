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
import com.example.fipscan.databinding.FragmentHomeBinding
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import technology.tabula.*
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

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
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)

                    val tablesData = extractTablesFromPDF(pdfDocument)

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val csvFilename = "data_$timestamp.csv"
                    val csvFile = saveAsCSV(tablesData, csvFilename)

                    Thread {
                        if (uploadFileToFTP(csvFile)) {
                            analyzeCSVFile(csvFile)
                        }
                    }.start()

                    pdfDocument.close() // Zamykamy dokument PDF
                }
            } catch (e: Exception) {
                binding.textHome.text = "Błąd przetwarzania tabel!"
                Log.e("TABULA_ERROR", "Błąd", e)
            }
        }
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): List<String> {
        val outputData = mutableListOf<String>()

        try {
            val extractor = ObjectExtractor(pdfDocument) // Poprawiona inicjalizacja

            for (pageIndex in 0 until pdfDocument.numberOfPages) { // pdfbox-android indeksuje od 0
                val page: Page = extractor.extract(pageIndex + 1) // Tabula używa indeksowania 1-based
                val algorithm = SpreadsheetExtractionAlgorithm()
                val tables: List<Table> = algorithm.extract(page)

                for (table in tables) {
                    for (row in table.rows) {
                        val rowData = row.joinToString(",") { cell ->
                            (cell as? RectangularTextContainer<*>)?.text ?: ""
                        }
                        outputData.add(rowData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TABULA_ERROR", "Błąd podczas ekstrakcji tabeli", e)
        }

        return outputData
    }

    private fun saveAsCSV(data: List<String>, filename: String): File {
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(storageDir, filename)
        file.writeText(data.joinToString("\n"))
        return file
    }

    private fun uploadFileToFTP(file: File): Boolean {
        val ftpClient = FTPClient()
        return try {
            val properties = Properties().apply {
                requireContext().assets.open("ftp_config.properties").use { load(it) }
            }

            ftpClient.apply {
                connect(properties.getProperty("ftp.host"), 21)
                enterLocalPassiveMode()
                login(properties.getProperty("ftp.user"), properties.getProperty("ftp.pass"))
                setFileType(FTP.BINARY_FILE_TYPE)
                changeWorkingDirectory("/public_html/")
                changeWorkingDirectory("/pl/")
                storeFile(file.name, file.inputStream())
                logout()
                disconnect()
            }
            Log.d("FTP_UPLOAD", "Plik wysłany poprawnie.")
            true
        } catch (ex: Exception) {
            Log.e("FTP_UPLOAD", "Błąd wysyłania pliku FTP", ex)
            false
        }
    }

    private fun analyzeCSVFile(csvFile: File) {
        val tableData = mutableListOf<List<String>>()
        csvFile.forEachLine { line ->
            tableData.add(line.split(","))
        }
        Log.d("CSV_ANALYSIS", "Załadowano ${tableData.size} wierszy z CSV")
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                pdfUri = it
                extractTablesWithTabula()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
