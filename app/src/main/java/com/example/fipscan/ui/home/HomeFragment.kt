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
import com.example.fipscan.R
import com.google.gson.Gson
import com.example.fipscan.BarChartLevelAnalyzer
import com.example.fipscan.ui.diagnosis.DiagnosisFragment

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private lateinit var pdfChartExtractor: PdfChartExtractor
    private var diagnosis: String? = null
    var uploaded = false;

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
                    var newChartFile: File? = null // Zmienna na plik wykresu

                    // Ekstrakcja i zapis wykresu
                    try {
                        val extractionResult = pdfChartExtractor.extractChartFromPDF(pdfFile)
                        if (extractionResult != null && extractionResult.imagePaths.isNotEmpty()) {
                            val originalChartPath = extractionResult.imagePaths[0]
                            val barChartPath = extractionResult.imagePaths.getOrNull(1)
                            val barSections = extractionResult.barSections

                            barSections?.let {
                                Log.d("BAR_SECTIONS", "Sekcja 1: ${it.section1}")
                                Log.d("BAR_SECTIONS", "Sekcja 2: ${it.section2}")
                                Log.d("BAR_SECTIONS", "Sekcja 3: ${it.section3}")
                                Log.d("BAR_SECTIONS", "Sekcja 4: ${it.section4}")
                            }

                            // Obliczanie diagnozy z wykresu i zapisanie w zmiennej członkowskiej
                            // 'diagnosis' jest teraz 'calculatedChartDiagnosis' dla jasności
                            this.diagnosis = extractionResult.barSections?.let { sections ->
                                BarChartLevelAnalyzer.analyzeGammapathy(sections.section1, sections.section4)
                            } ?: "Brak danych" // Zmieniono domyślną wartość na bardziej opisową
                            Log.d("GAMMAPATHY", "Obliczona diagnoza z wykresu: ${this.diagnosis}") // Logowanie obliczonej wartości

                            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                            val originalChartFile = File(storageDir, chartFilename)
                            File(originalChartPath).copyTo(originalChartFile, overwrite = true)
                            //File(originalChartPath).delete() // Odkomentuj jeśli chcesz usuwać oryginalny plik z cache

                            // Wysyłka oryginalnego wykresu
                            Thread { uploadFileToFTP(originalChartFile) }.start()

                            // Wysyłka przyciętego bar_chart
                            if (barChartPath != null) {
                                val barChartFile = File(barChartPath)
                                Thread {
                                    uploadFileToFTP(barChartFile)
                                    try {
                                        val bitmap = BitmapFactory.decodeFile(barChartPath)
                                        val origBitmap = BitmapFactory.decodeFile(originalChartPath)

                                        if (bitmap == null || origBitmap == null) {
                                            Log.e("BAR_LEVELS", "Nie udało się załadować bitmapy! bitmap=$bitmap, origBitmap=$origBitmap")
                                        } else {
                                            val analysisResult = BarChartLevelAnalyzer.analyzeBarHeights(bitmap, origBitmap)

                                            Log.d("BAR_LEVELS", "Poziomy słupków (% wysokości):")
                                            analysisResult.barHeights.forEachIndexed { index, value ->
                                                Log.d("BAR_LEVELS", "Słupek ${index + 1}: %.2f%%".format(value))
                                            }

                                            Log.d("BAR_LEVELS", "Wykryte kolumny z czerwonymi pikselami (oddalone o ≥5% szerokości):")
                                            analysisResult.redColumnIndices.forEachIndexed { index, col ->
                                                Log.d("BAR_LEVELS", "Kolumna ${index + 1}: $col")
                                            }

                                            Log.d("BAR_LEVELS", "Wymiary obrazu: szerokość=${analysisResult.imageWidth}, wysokość=${analysisResult.imageHeight}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("BAR_LEVELS", "Błąd analizy słupków", e)
                                    }
                                }.start()
                            }
                            newChartFile = originalChartFile // Przypisz plik wykresu
                        } else {
                            Log.w("CHART_EXTRACT", "Nie znaleziono wykresu lub wystąpił błąd ekstrakcji.")
                            this.diagnosis = "Brak wykresu" // Ustaw stan, jeśli wykres nie został znaleziony
                        }
                    } catch (e: Exception) {
                        Log.e("CHART_EXTRACT", "Błąd przetwarzania wykresu", e)
                        this.diagnosis = "Błąd analizy wykresu" // Ustaw stan błędu
                    }

                    // Ekstrakcja tabel
                    val (tablesData, _) = extractTablesFromPDF(pdfDocument)
                    pdfDocument.close()

                    if (tablesData.isEmpty()) {
                        binding.resultsTextView.text = "Nie znaleziono tabel!"
                        return // Zakończ jeśli nie ma tabel
                    }

                    // Zapisz i wyślij CSV
                    val csvFile = saveAsCSV(tablesData, csvFilename)
                    Thread {
                        uploadFileToFTP(csvFile)
                        // Przekazujemy odczytaną diagnozę z ZMIENNEJ CZŁONKOWSKIEJ 'diagnosis'
                        analyzeCSVFile(csvFile, newChartFile?.absolutePath, pdfFile)
                    }.start()
                }
            } catch (e: Exception) {
                binding.resultsTextView.text = "Błąd przetwarzania pliku PDF!" // Bardziej ogólny komunikat
                Log.e("PDF_PROCESSING_ERROR", "Błąd główny przetwarzania PDF", e)
                // Można też ustawić stan diagnozy na błąd
                this.diagnosis = "Błąd przetwarzania PDF"
            }
        } ?: run {
            Toast.makeText(requireContext(), "Nie wybrano pliku PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractTablesFromPDF(pdfDocument: PDDocument): Pair<List<List<String>>, String?> {
        val outputData = mutableListOf<List<String>>()
        var chartImagePath: String? = null

        try {
            val extractor = technology.tabula.ObjectExtractor(pdfDocument)
            val algorithm = technology.tabula.extractors.BasicExtractionAlgorithm()

            for (pageIndex in 0 until pdfDocument.numberOfPages) {
                val page = extractor.extract(pageIndex + 1)

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

            // Tworzenie katalogu jeśli nie istnieje
            storageDir?.takeIf { !it.exists() }?.mkdirs()

            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
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

    private fun analyzeCSVFile(csvFile: File, chartImagePath: String?, pdfFile: File?) {
        val csvLines = csvFile.readLines()
        val extractedData = ExtractData.parseLabResults(csvLines)

        val collectionDate = extractedData["Data"]
        val patient = extractedData["Pacjent"] as? String ?: "Nieznany"
        // ... (reszta kodu do parsowania danych pacjenta) ...
        val age = extractedData["Wiek"] as? String ?: "Nieznany"
        // ... (reszta kodu do formatowania info - finalInfo) ...
        val rawJson = Gson().toJson(extractedData)

        // Lista wyników badań poza normą
        val abnormalResults = mutableListOf<String>()

        // Iteracja po danych i wyszukiwanie wyników badań
        for (key in extractedData.keys) {
            if (key.endsWith("Unit") || key.endsWith("RangeMin") || key.endsWith("RangeMax")) {
                continue
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
                val catInfo = """
                📆 Data: $collectionDate
                🐱 Pacjent: $patient
                🐾 Gatunek: ${extractedData["Gatunek"] ?: "Nieznany"}
                🏷️ Rasa: ${extractedData["Rasa"] ?: "Nieznana"}
                ⚥ Płeć: ${extractedData["Płeć"] ?: "Nieznana"}
                📅 Wiek: $age
                🎨 Umaszczenie: ${extractedData["Umaszczenie"] ?: "Nieznane"}
            """.trimIndent() // Użyj wyekstrahowanych danych
                "$catInfo\n\n📊 Wyniki poza normą:\nBadanie: wynik (norma) jednostka\n$resultsText\n"
            } else {
                val catInfo = """
                📆 Data: $collectionDate
                🐱 Pacjent: $patient
                🐾 Gatunek: ${extractedData["Gatunek"] ?: "Nieznany"}
                🏷️ Rasa: ${extractedData["Rasa"] ?: "Nieznana"}
                ⚥ Płeć: ${extractedData["Płeć"] ?: "Nieznana"}
                📅 Wiek: $age
                🎨 Umaszczenie: ${extractedData["Umaszczenie"] ?: "Nieznane"}
            """.trimIndent()
                "$catInfo\n\n✅ Wszystkie wyniki w normie"
            }
        }

        binding.scrollView.postDelayed({
            if (binding.resultsTextView.text.isNotEmpty()) {
                binding.scrollView.smoothScrollTo(0, 0)
            }
        }, 100)

        binding.textHome.text = "Wyniki: ${patient}"
        displayImage(chartImagePath)

        // Logowanie wartości ZMIENNEJ CZŁONKOWSKIEJ PRZED wywołaniem zapisu do bazy
        Log.d("AnalyzeCSV", "Przekazuję do saveResultToDatabase - wartość zmiennej członkowskiej 'diagnosis': ${this.diagnosis}")
        activity?.runOnUiThread {
            binding.textScanResult.text = "Diagnoza z wykresu: " + this.diagnosis + "\n\n\n"
        }
        // Wywołanie zapisu do bazy z przekazaniem diagnozy odczytanej ze ZMIENNEJ CZŁONKOWSKIEJ 'diagnosis'
        saveResultToDatabase(
            patient, age, abnormalResults.joinToString("\n"),
            pdfFile, chartImagePath, collectionDate as? String, rawJson,
            this.diagnosis
        )
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    pdfUri = it
                    extractTablesWithTabula()
                    uploaded = true;
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
        results: String, // Wyniki poza normą z analizy laboratoryjnej
        pdfFile: File?,
        imagePath: String?,
        collectionDate: String?,
        rawDataJson: String?,
        diagnosisValueToSave: String? // Zmieniona nazwa dla jasności - to jest diagnoza z WYKRESU
    ) {
        val pdfFilePath = pdfFile?.absolutePath

        // Tworzenie encji z przekazaną wartością diagnozy z wykresu
        val result = ResultEntity(
            patientName = patient,
            age = age,
            testResults = results, // Wyniki lab. poza normą trafiają do 'testResults'
            pdfFilePath = pdfFilePath,
            imagePath = imagePath,
            collectionDate = collectionDate,
            rawDataJson = rawDataJson,
            diagnosis = diagnosisValueToSave // Diagnoza z WYKRESU trafia do pola 'diagnosis'
        )

        // Logowanie encji PRZED próbą zapisu
        Log.d("SaveEntity", "Próba zapisu/zastąpienia ResultEntity: $result")

        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Usuwanie duplikatów (jeśli jest taka potrzeba w logice aplikacji)
                Log.d("SaveEntity", "Próba usunięcia duplikatów dla: $patient, $age")
                db.resultDao().deleteDuplicates(patient, age) // Upewnij się, że ta logika jest poprawna i potrzebna

                // Zapis/Zastąpienie rekordu w bazie danych
                Log.d("SaveEntity", "Wywołanie insertResult dla pacjenta: $patient (strategia: REPLACE)")
                db.resultDao().insertResult(result) // Używa strategii REPLACE zdefiniowanej w DAO
                Log.i("SaveEntity", "Rekord dla pacjenta '$patient' zapisany/zastąpiony pomyślnie.")

            } catch (e: Exception) {
                // Logowanie JAKIEGOKOLWIEK błędu podczas operacji na bazie
                Log.e("SaveEntity", "!!! BŁĄD podczas operacji na bazie danych (delete/insert) !!!", e)
                // Można by tu dodać informację dla użytkownika, np. przez Toast na głównym wątku
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Błąd zapisu do bazy danych!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun savePdfToDownloadsUsingMediaStore(filePath: String, fileName: String) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                Toast.makeText(requireContext(), "Plik źródłowy nie istnieje", Toast.LENGTH_SHORT).show()
                return
            }

            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Nie można utworzyć pliku")

            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(
                requireContext(),
                "Zapisano w: ${Environment.DIRECTORY_DOWNLOADS}/$fileName.pdf",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Błąd zapisu: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("PDF_EXPORT", e.toString())
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
        binding.resultsTextView.text = "Wyniki poza normą:\n" + result.testResults
        binding.textScanResult.text = "Diagnoza z wykresu: " + result.diagnosis + "\n\n\n" ?: "Diagnoza z wykresu: brak danych"
        result.imagePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            binding.chartImageView.visibility = View.VISIBLE
            binding.chartImageView.setImageBitmap(bitmap)
        }

        binding.buttonSaveOriginal.visibility = View.VISIBLE
        binding.buttonSaveOriginal.setOnClickListener {
            result.pdfFilePath?.let { filePath ->
                val fileName = "${result.patientName}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"
                savePdfToDownloadsUsingMediaStore(filePath, fileName)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
