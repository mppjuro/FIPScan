package com.example.fipscan.ui.diagnosis

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fipscan.AppDatabase
import com.example.fipscan.ElectrophoresisAnalyzer
import com.example.fipscan.LabResultAnalyzer
import com.example.fipscan.databinding.FragmentDiagnosisBinding
import com.google.gson.Gson
import com.example.fipscan.ResultEntity
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.activityViewModels
import com.example.fipscan.SharedResultViewModel
import android.widget.Toast
import android.print.PrintAttributes
import android.print.PrintManager
import android.content.Context
import android.content.res.ColorStateList
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.example.fipscan.PdfReportGenerator
import java.io.FileInputStream
import java.io.FileOutputStream
import android.graphics.pdf.PdfDocument
import android.os.Bundle as AndroidBundle
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.IOException
import java.util.Properties
import com.example.fipscan.ElectrophoresisShapeAnalyzer
import androidx.cardview.widget.CardView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.Color
import com.example.fipscan.FipPatternAnalyzer
import android.widget.ProgressBar
import android.graphics.BitmapFactory
import android.util.TypedValue
import androidx.core.content.ContextCompat

class DiagnosisFragment : Fragment() {
    private var _binding: FragmentDiagnosisBinding? = null
    private val binding get() = _binding!!
    private var result: ResultEntity? = null
    private val sharedViewModel: SharedResultViewModel by activityViewModels()

    // Przechowuj dane do generowania raportu
    private var currentRiskPercentage: Int = 0
    private var currentRiskComment: String = ""
    private var currentScoreBreakdown: List<String> = emptyList()
    private var currentDiagnosticComment: String = ""
    private var currentSupplementAdvice: String = ""
    private var currentVetConsultationAdvice: String = ""
    private var currentFurtherTestsAdvice: String = ""
    private var currentAbnormalResults: List<String> = emptyList()
    private var currentGammopathyResult: String? = null

    // Added missing variables
    private var currentShapeAnalysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult? = null
    private var currentPatternAnalysis: FipPatternAnalyzer.PatternAnalysisResult? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisBinding.inflate(inflater, container, false)

        // Ustaw listenery dla przycisków
        binding.buttonDownloadReport.setOnClickListener {
            downloadPdfReport()
        }

        binding.buttonPrintReport.setOnClickListener {
            printPdfReport()
        }

        // Sprawdź czy mamy dane z argumentów
        arguments?.let {
            result = DiagnosisFragmentArgs.fromBundle(it).result
            Log.d("DiagnosisFragment", "Otrzymano wynik z argumentów: ${result?.patientName}")
        }

        // Jeśli nie mamy danych z argumentów, obserwuj SharedViewModel
        sharedViewModel.selectedResult.observe(viewLifecycleOwner) { selectedResult ->
            if (selectedResult != null) {
                result = selectedResult
                Log.d("DiagnosisFragment", "Otrzymano wynik z SharedViewModel: ${result?.patientName}")
                setupUI()
            } else {
                showNoDataMessage()
            }
        }

        // Jeśli dane były już w argumentach (np. nawigacja z historii), ustaw UI od razu
        if (result != null) {
            setupUI()
        }

        return binding.root
    }

    private fun showNoDataMessage() {
        binding.textDiagnosis.text = "Diagnoza"
        binding.textPatientInfo.text = "Wprowadź dane do analizy\n\nAby zobaczyć diagnozę:\n• Przejdź do zakładki 'Skanuj' i wgraj plik PDF z wynikami\n• Lub wybierz rekord z 'Historii' badań"
        binding.textPatientInfo.visibility = View.VISIBLE

        // Ukryj przyciski raportu
        binding.reportButtonsContainer.visibility = View.GONE

        // Ukryj pozostałe pola
        val fieldsToHide = listOf(
            binding.textDiagnosticComment, binding.textSupplements, binding.textVetConsult,
            binding.textFurtherTests, binding.textRiskSupplements, binding.textRiskConsult,
            binding.textRiskBreakdown
        )
        fieldsToHide.forEach {
            it.text = ""
            it.visibility = View.GONE
        }
    }

    private fun setupUI() {
        // Fix smart cast issue by creating local variable
        val currentResult = result ?: return

        Log.d("DiagnosisFragment", "Konfigurowanie UI dla: ${currentResult.patientName}")

        binding.textDiagnosis.text = "Diagnoza: ${currentResult.patientName}"

        // Pokaż przyciski raportu
        binding.reportButtonsContainer.visibility = View.VISIBLE

        val patientInfo = """
            🐱 Pacjent: ${currentResult.patientName}
            📅 Wiek: ${currentResult.age}
            🐾 Gatunek: ${currentResult.species ?: "nie podano"}
            🏷️ Rasa: ${currentResult.breed ?: "nie podano"}
            ⚥ Płeć: ${currentResult.gender ?: "nie podano"}
            🎨 Umaszczenie: ${currentResult.coat ?: "nie podano"}
            📆 Data badania: ${currentResult.collectionDate ?: "brak daty"}
        """.trimIndent()
        binding.textPatientInfo.text = patientInfo
        binding.textPatientInfo.visibility = View.VISIBLE

        val extractedMap = currentResult.rawDataJson?.let {
            try {
                Gson().fromJson(it, Map::class.java) as? Map<String, Any> ?: emptyMap()
            } catch (e: Exception) {
                Log.e("DiagnosisFragment", "Błąd parsowania JSON", e)
                emptyMap()
            }
        } ?: emptyMap()

        if (extractedMap.isEmpty()) {
            Log.w("DiagnosisFragment", "Brak danych do analizy (extractedMap jest pusta).")
            binding.reportButtonsContainer.visibility = View.GONE
            return
        }

        // --- Analizy ---
        val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
        val rivaltaStatus = currentResult.rivaltaStatus ?: "nie wykonano, płyn obecny"
        val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

        // Przechowaj dane do generowania raportu
        currentRiskPercentage = electroResult.riskPercentage
        currentRiskComment = electroResult.fipRiskComment
        currentScoreBreakdown = electroResult.scoreBreakdown
        currentDiagnosticComment = labResult.diagnosticComment
        currentSupplementAdvice = electroResult.supplementAdvice
        currentVetConsultationAdvice = electroResult.vetConsultationAdvice
        currentFurtherTestsAdvice = electroResult.furtherTestsAdvice
        currentGammopathyResult = currentResult.diagnosis

        // Przygotuj listę nieprawidłowych wyników
        currentAbnormalResults = prepareAbnormalResults(extractedMap)

        // Analiza kształtu krzywej elektroforezy
        // Note: Make sure chartImageView exists in your layout, or remove this section if not needed
        try {
            // Check if chartImageView exists in the binding before using it
            val chartImageViewField = binding.javaClass.getDeclaredField("chartImageView")
            chartImageViewField.isAccessible = true
            val chartImageView = chartImageViewField.get(binding) as? View
            chartImageView?.visibility = View.VISIBLE
        } catch (e: NoSuchFieldException) {
            Log.w("DiagnosisFragment", "chartImageView not found in layout, skipping image display")
        }

        currentResult.imagePath?.let { imagePath ->
            val chartFile = File(imagePath)
            if (chartFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imagePath)

                // Pobierz indeksy czerwonych kolumn z poprzedniej analizy
                val redColumns = listOf(100, 300, 500) // Przykładowe wartości, dostosuj do rzeczywistych

                val shapeAnalysis = ElectrophoresisShapeAnalyzer.analyzeElectrophoresisShape(bitmap, redColumns)

                shapeAnalysis?.let { analysis ->
                    // Wyświetl wyniki analizy kształtu
                    val shapeCard = createShapeAnalysisCard(analysis)

                    // Check if analysisContainer exists before adding views
                    try {
                        val analysisContainerField = binding.javaClass.getDeclaredField("analysisContainer")
                        analysisContainerField.isAccessible = true
                        val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
                        analysisContainer?.addView(shapeCard)
                    } catch (e: NoSuchFieldException) {
                        Log.w("DiagnosisFragment", "analysisContainer not found in layout")
                    }

                    // Dodaj do raportu
                    currentShapeAnalysis = analysis
                }
            }
        }

        // Analiza wzorców parametrów
        val patternAnalysis = FipPatternAnalyzer.analyzeParameterPatterns(extractedMap)

        // Wyświetl kartę z analizą wzorców
        val patternCard = createPatternAnalysisCard(patternAnalysis)

        // Check if analysisContainer exists before adding views
        try {
            val analysisContainerField = binding.javaClass.getDeclaredField("analysisContainer")
            analysisContainerField.isAccessible = true
            val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
            analysisContainer?.addView(patternCard)
        } catch (e: NoSuchFieldException) {
            Log.w("DiagnosisFragment", "analysisContainer not found in layout")
        }

        // Przechowaj do raportu
        currentPatternAnalysis = patternAnalysis

        // --- Aktualizacja UI ---

        // 1. Główne podsumowanie ryzyka FIP
        val riskText = Html.fromHtml(electroResult.fipRiskComment, Html.FROM_HTML_MODE_COMPACT)
        binding.textDiagnosticComment.text = riskText

        // 2. Szczegółowe uzasadnienie wyniku FIP
        val breakdownHtml = electroResult.scoreBreakdown.joinToString("<br>")
        binding.textRiskBreakdown.text = Html.fromHtml("<b>Szczegółowa analiza ryzyka FIP:</b><br>$breakdownHtml", Html.FROM_HTML_MODE_COMPACT)
        binding.textRiskBreakdown.visibility = View.VISIBLE

        // 3. Zalecenia na podstawie ryzyka FIP
        binding.textFurtherTests.text = Html.fromHtml("<b>🔬 Dalsze badania:</b> ${electroResult.furtherTestsAdvice}", Html.FROM_HTML_MODE_COMPACT)
        binding.textRiskSupplements.text = Html.fromHtml("<b>💊 Suplementy (kontekst FIP):</b> ${electroResult.supplementAdvice}", Html.FROM_HTML_MODE_COMPACT)
        binding.textRiskConsult.text = Html.fromHtml("<b>🏥 Konsultacja (kontekst FIP):</b> ${electroResult.vetConsultationAdvice}", Html.FROM_HTML_MODE_COMPACT)

        // 4. Analiza ogólna wyników krwi (z LabResultAnalyzer)
        binding.textSupplements.text = Html.fromHtml("<b>💊 Suplementy (ogólne):</b> ${labResult.supplementAdvice}", Html.FROM_HTML_MODE_COMPACT)
        binding.textVetConsult.text = Html.fromHtml("<b>🏥 Konsultacja (ogólna):</b> ${labResult.vetConsultationAdvice}", Html.FROM_HTML_MODE_COMPACT)

        // Pokaż wszystkie pola
        val fieldsToShow = listOf(
            binding.textDiagnosticComment, binding.textSupplements, binding.textVetConsult,
            binding.textFurtherTests, binding.textRiskSupplements, binding.textRiskConsult,
            binding.textRiskBreakdown
        )
        fieldsToShow.forEach { it.visibility = View.VISIBLE }
    }

    private fun prepareAbnormalResults(extractedMap: Map<String, Any>): List<String> {
        val abnormalResults = mutableListOf<String>()
        val metadataKeys = setOf("Data", "Właściciel", "Pacjent", "Gatunek", "Rasa",
            "Płeć", "Wiek", "Lecznica", "Lekarz", "Rodzaj próbki",
            "Umaszczenie", "Mikrochip", "results", "GammopathyResult")

        for (key in extractedMap.keys) {
            if (key.endsWith("Unit") || key.endsWith("RangeMin") ||
                key.endsWith("RangeMax") || key.endsWith("Flag") ||
                key in metadataKeys) {
                continue
            }

            val testName = key
            val value = extractedMap[testName] as? String ?: continue
            val unit = extractedMap["${testName}Unit"] as? String ?: ""

            val minRangeStr = extractedMap["${testName}RangeMin"] as? String
            val maxRangeStr = extractedMap["${testName}RangeMax"] as? String

            if (minRangeStr == null && maxRangeStr == null) {
                continue
            }

            val minRange = minRangeStr ?: "-"
            val maxRange = maxRangeStr ?: minRange

            if (isOutOfRange(value, minRange, maxRange)) {
                abnormalResults.add("$testName: $value $unit (norma: $minRange - $maxRange)")
            }
        }

        return abnormalResults
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
        } catch (e: Exception) {
            return false
        }
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

            Log.d("FTP_UPLOAD", "Plik ${file.name} wysłany poprawnie na FTP.")
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

    private fun downloadPdfReport() {
        val currentResult = result ?: run {
            Toast.makeText(
                requireContext(),
                "Brak danych do wygenerowania raportu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val generator = PdfReportGenerator(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val (fileName, localPath) = generator.generateReport(
                patientName = currentResult.patientName,
                age = currentResult.age,
                species = currentResult.species,
                breed = currentResult.breed,
                gender = currentResult.gender,
                coat = currentResult.coat,
                collectionDate = currentResult.collectionDate,
                riskPercentage = currentRiskPercentage,
                riskComment = currentRiskComment,
                scoreBreakdown = currentScoreBreakdown,
                diagnosticComment = currentDiagnosticComment,
                supplementAdvice = currentSupplementAdvice,
                vetConsultationAdvice = currentVetConsultationAdvice,
                furtherTestsAdvice = currentFurtherTestsAdvice,
                abnormalResults = currentAbnormalResults,
                gammopathyResult = currentGammopathyResult
            )

            withContext(Dispatchers.Main) {
                if (fileName != null && localPath != null) {
                    Toast.makeText(
                        requireContext(),
                        "Raport zapisano: $fileName",
                        Toast.LENGTH_LONG
                    ).show()

                    // Wyślij raport na FTP w tle
                    lifecycleScope.launch(Dispatchers.IO) {
                        val localFile = File(localPath)
                        if (localFile.exists()) {
                            val uploadSuccess = uploadFileToFTP(localFile)
                            withContext(Dispatchers.Main) {
                                if (uploadSuccess) {
                                    Log.d("DiagnosisFragment", "Raport wysłany na serwer FTP")
                                } else {
                                    Log.e("DiagnosisFragment", "Błąd wysyłania raportu na FTP")
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Błąd generowania raportu",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun printPdfReport() {
        val currentResult = result ?: run {
            Toast.makeText(
                requireContext(),
                "Brak danych do wydrukowania raportu",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val generator = PdfReportGenerator(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            // Najpierw generuj PDF
            val (fileName, localPath) = generator.generateReport(
                patientName = currentResult.patientName,
                age = currentResult.age,
                species = currentResult.species,
                breed = currentResult.breed,
                gender = currentResult.gender,
                coat = currentResult.coat,
                collectionDate = currentResult.collectionDate,
                riskPercentage = currentRiskPercentage,
                riskComment = currentRiskComment,
                scoreBreakdown = currentScoreBreakdown,
                diagnosticComment = currentDiagnosticComment,
                supplementAdvice = currentSupplementAdvice,
                vetConsultationAdvice = currentVetConsultationAdvice,
                furtherTestsAdvice = currentFurtherTestsAdvice,
                abnormalResults = currentAbnormalResults,
                gammopathyResult = currentGammopathyResult
            )

            withContext(Dispatchers.Main) {
                if (fileName != null && localPath != null) {
                    // Uruchom drukowanie
                    val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = "Raport FIP - ${currentResult.patientName}"

                    printManager.print(
                        jobName,
                        FipReportPrintAdapter(requireContext(), localPath, fileName),
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .build()
                    )

                    // Wyślij raport na FTP w tle
                    lifecycleScope.launch(Dispatchers.IO) {
                        val localFile = File(localPath)
                        if (localFile.exists()) {
                            uploadFileToFTP(localFile)
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Błąd generowania raportu do druku",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Adapter do drukowania - poprawiony
    inner class FipReportPrintAdapter(
        private val context: Context,
        private val pdfFilePath: String,
        private val fileName: String
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: AndroidBundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }

            val info = PrintDocumentInfo.Builder(fileName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()

            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                // Kopiuj istniejący PDF do strumienia wyjściowego drukarki
                FileInputStream(File(pdfFilePath)).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } > 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                Log.e("PrintAdapter", "Błąd drukowania", e)
                callback.onWriteFailed(e.message)
            }
        }
    }

    private fun createShapeAnalysisCard(analysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult): CardView {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
            setCardBackgroundColor(getThemedColor(requireContext(), android.R.attr.colorBackgroundFloating))
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Tytuł
        val title = TextView(requireContext()).apply {
            text = "📊 ANALIZA KSZTAŁTU KRZYWEJ"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(title)

        // Wzorzec
        val pattern = TextView(requireContext()).apply {
            text = "Wzorzec: ${analysis.overallPattern}"
            textSize = 16f
            setPadding(0, 8, 0, 4)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(pattern)

        // Wynik FIP Shape Score z kolorowym tłem
        val scoreView = TextView(requireContext()).apply {
            text = "Wynik kształtu FIP: ${analysis.fipShapeScore.toInt()}/100"
            textSize = 16f
            setPadding(8, 8, 8, 8)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))

            val bgColor = when {
                analysis.fipShapeScore >= 70 -> {
                    // Czerwone tło z przezroczystością
                    getColorWithAlpha(Color.parseColor("#F44336"), 0.2f)
                }
                analysis.fipShapeScore >= 50 -> {
                    // Żółte tło z przezroczystością
                    getColorWithAlpha(Color.parseColor("#FF9800"), 0.2f)
                }
                analysis.fipShapeScore >= 30 -> {
                    // Zielone tło z przezroczystością
                    getColorWithAlpha(Color.parseColor("#4CAF50"), 0.2f)
                }
                else -> {
                    // Szare tło z przezroczystością
                    getColorWithAlpha(getThemedColor(requireContext(), android.R.attr.textColorSecondary), 0.2f)
                }
            }
            setBackgroundColor(bgColor)
        }
        content.addView(scoreView)

        // Opis
        val description = TextView(requireContext()).apply {
            text = analysis.shapeDescription
            textSize = 14f
            setPadding(0, 8, 0, 0)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(description)

        // Szczegóły pików
        val detailsTitle = TextView(requireContext()).apply {
            text = "\nSzczegóły frakcji:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(detailsTitle)

        val details = """
        Albuminy: wysokość ${(analysis.albumin.height * 100).toInt()}%, symetria ${(analysis.albumin.symmetry * 100).toInt()}%
        Gamma: wysokość ${(analysis.gamma.height * 100).toInt()}%, szerokość ${(analysis.gamma.width * 100).toInt()}%
        Mostek β-γ: ${if (analysis.betaGammaBridge.present) "obecny (głębokość ${(analysis.betaGammaBridge.depth * 100).toInt()}%)" else "nieobecny"}
    """.trimIndent()

        val detailsText = TextView(requireContext()).apply {
            text = details
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorSecondary))
        }
        content.addView(detailsText)

        card.addView(content)
        return card
    }

    // Zastąp metodę createPatternAnalysisCard:
    private fun createPatternAnalysisCard(analysis: FipPatternAnalyzer.PatternAnalysisResult): CardView {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
            setCardBackgroundColor(getThemedColor(requireContext(), android.R.attr.colorBackgroundFloating))
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Tytuł
        val title = TextView(requireContext()).apply {
            text = "🔬 PROFIL WZORCÓW LABORATORYJNYCH"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(title)

        // Główny profil
        val profileText = when (analysis.primaryProfile) {
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_ACUTE -> "⚠️ OSTRY PROFIL ZAPALNY"
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_CHRONIC -> "⚠️ PRZEWLEKŁY PROFIL ZAPALNY"
            FipPatternAnalyzer.FipProfile.EFFUSIVE_CLASSIC -> "🔴 KLASYCZNY WYSIĘKOWY"
            FipPatternAnalyzer.FipProfile.DRY_NEUROLOGICAL -> "🟡 SUCHY NEUROLOGICZNY"
            FipPatternAnalyzer.FipProfile.MIXED_PATTERN -> "🔶 PROFIL MIESZANY"
            FipPatternAnalyzer.FipProfile.ATYPICAL -> "❓ PROFIL NIETYPOWY"
            FipPatternAnalyzer.FipProfile.NON_FIP -> "✅ PROFIL NIE-FIP"
        }

        val profile = TextView(requireContext()).apply {
            text = profileText
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 4)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(profile)

        // Siła wzorca
        val strengthBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            max = 100
            progress = analysis.patternStrength.toInt()

            val tintColor = when {
                analysis.patternStrength >= 70 -> Color.parseColor("#F44336") // Czerwony
                analysis.patternStrength >= 50 -> Color.parseColor("#FF9800") // Pomarańczowy
                else -> Color.parseColor("#4CAF50") // Zielony
            }
            progressTintList = ColorStateList.valueOf(tintColor)
        }
        content.addView(strengthBar)

        val strengthText = TextView(requireContext()).apply {
            text = "Siła dopasowania: ${analysis.patternStrength.toInt()}%"
            textSize = 14f
            setPadding(0, 4, 0, 8)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(strengthText)

        // Kluczowe obserwacje
        if (analysis.keyFindings.isNotEmpty()) {
            val findingsTitle = TextView(requireContext()).apply {
                text = "\nKluczowe obserwacje:"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            }
            content.addView(findingsTitle)

            analysis.keyFindings.forEach { finding ->
                val findingText = TextView(requireContext()).apply {
                    text = finding
                    textSize = 13f
                    setPadding(8, 4, 0, 4)
                    setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
                }
                content.addView(findingText)
            }
        }

        // Opis profilu
        val descTitle = TextView(requireContext()).apply {
            text = "\nOpis profilu:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(descTitle)

        val description = TextView(requireContext()).apply {
            text = analysis.profileDescription
            textSize = 13f
            setPadding(0, 4, 0, 8)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(description)

        // Sugestie postępowania
        val suggestionsTitle = TextView(requireContext()).apply {
            text = "\nSugestie postępowania:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(suggestionsTitle)

        val suggestions = TextView(requireContext()).apply {
            text = analysis.managementSuggestions
            textSize = 13f
            setPadding(0, 4, 0, 0)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(suggestions)

        card.addView(content)
        return card
    }

    private fun getThemedColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return ContextCompat.getColor(context, typedValue.resourceId)
    }

    private fun getColorWithAlpha(color: Int, alpha: Float): Int {
        return Color.argb(
            (alpha * 255).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}