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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.*
import com.tom_roush.pdfbox.cos.COSName
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import technology.tabula.ObjectExtractor
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import technology.tabula.extractors.BasicExtractionAlgorithm
import technology.tabula.RectangularTextContainer
import technology.tabula.Page
import technology.tabula.Table
import java.io.FileInputStream
import java.io.IOException

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

                    val font: PDFont = try {
                        val fontStream = requireContext().assets.open("fonts/LiberationSans-Regular.ttf")
                        PDType0Font.load(pdfDocument, fontStream, true)
                    } catch (e: Exception) {
                        Log.e("FONT_ERROR", "Błąd ładowania czcionki, używam Helvetica.", e)
                        PDType1Font.HELVETICA
                    }

                    forceReplaceFonts(pdfDocument, font)

                    val tablesData = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        binding.textHome.text = "Nie znaleziono tabel!"
                        return
                    }

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val csvFilename = "data_$timestamp.csv"
                    val csvFile = saveAsCSV(tablesData, csvFilename)

                    Thread {
                        if (uploadFileToFTP(csvFile)) {
                            analyzeCSVFile(csvFile)
                        }
                    }.start()
                }
            } catch (e: Exception) {
                binding.textHome.text = "Błąd przetwarzania tabel!"
                Log.e("TABULA_ERROR", "Błąd", e)
            }
        }
    }

    private fun forceReplaceFonts(pdfDocument: PDDocument, font: PDFont) {
        try {
            for (page in 0 until pdfDocument.numberOfPages) {
                val resources = pdfDocument.getPage(page).resources
                for (fontName in resources.fontNames) {
                    val existingFont = resources.getFont(fontName)
                    if (existingFont != null && !existingFont.isEmbedded) {
                        resources.put(fontName, font)
                        Log.d("PDF_FONTS", "Zamiana czcionki: ${existingFont.name} -> ${font.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PDF_FONTS", "Błąd wymuszania zamiany czcionek", e)
        }
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): List<List<String>> {
        val outputData = mutableListOf<List<String>>()

        try {
            val extractor = ObjectExtractor(pdfDocument)
            val algorithm = BasicExtractionAlgorithm()  // 🔵 Zmieniamy algorytm na BasicExtractionAlgorithm

            for (pageIndex in 0 until pdfDocument.numberOfPages) {
                val page: Page = extractor.extract(pageIndex + 1) // Tabula używa indeksowania 1-based
                val tables: List<Table> = algorithm.extract(page)

                for (table in tables) {
                    for (row in table.rows) {
                        val rowData = row.map {
                            (it as? RectangularTextContainer<*>)?.text?.replace("\r", " ")?.trim() ?: ""
                        }.filter { it.isNotBlank() }  // filtrowanie pustych pól

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

    private fun loggedInSuccessfully(ftpClient: FTPClient, loggedIn: Boolean): Boolean {
        return if (!loggedIn) {
            Log.e("FTP_LOGIN", "Logowanie nieudane: ${ftpClient.replyString}")
            ftpClient.disconnect()
            false
        } else {
            Log.d("FTP_LOGIN", "Logowanie powiodło się.")
            true
        }
    }

    private fun analyzeCSVFile(csvFile: File) {
        val tableData = mutableListOf<List<String>>()
        csvFile.forEachLine { line ->
            tableData.add(line.split(","))
        }
        Log.d("CSV_ANALYSIS", "Załadowano ${tableData.size} wierszy z CSV")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
