package com.example.fipscan.ui.diagnosis

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Html
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.fipscan.BarChartLevelAnalyzer
import com.example.fipscan.ElectrophoresisAnalyzer
import com.example.fipscan.ElectrophoresisShapeAnalyzer
import com.example.fipscan.FipPatternAnalyzer
import com.example.fipscan.LabResultAnalyzer
import com.example.fipscan.PdfReportGenerator
import com.example.fipscan.R
import com.example.fipscan.ResultEntity
import com.example.fipscan.SharedResultViewModel
import com.example.fipscan.databinding.FragmentDiagnosisBinding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import com.google.gson.reflect.TypeToken
import java.util.Locale

class DiagnosisFragment : Fragment() {
    private var _binding: FragmentDiagnosisBinding? = null
    private val binding get() = _binding!!
    private var result: ResultEntity? = null
    private val sharedViewModel: SharedResultViewModel by activityViewModels()
    private var currentRiskPercentage: Int = 0
    private var currentRiskComment: String = ""
    private var currentScoreBreakdown: List<String> = emptyList()
    private var currentDiagnosticComment: String = ""
    private var currentSupplementAdvice: String = ""
    private var currentVetConsultationAdvice: String = ""
    private var currentFurtherTestsAdvice: String = ""
    private var currentAbnormalResults: List<String> = emptyList()
    private var currentGammopathyResult: String? = null
    private var currentShapeAnalysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult? = null
    private var currentPatternAnalysis: FipPatternAnalyzer.PatternAnalysisResult? = null

    private var currentGammaAnalysis: ElectrophoresisShapeAnalyzer.GammaAnalysisResult? = null
    private var currentAucAnalysis: Map<String, Double>? = null
    private var currentWidthRatios: ElectrophoresisShapeAnalyzer.WidthRatioAnalysis? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisBinding.inflate(inflater, container, false)

        binding.buttonDownloadReport.setOnClickListener {
            downloadPdfReport()
        }

        binding.buttonPrintReport.setOnClickListener {
            printPdfReport()
        }

        arguments?.let {
            result = DiagnosisFragmentArgs.fromBundle(it).result
        }

        sharedViewModel.selectedResult.observe(viewLifecycleOwner) { selectedResult ->
            if (selectedResult != null) {
                result = selectedResult
                setupUI()
            } else {
                showNoDataMessage()
            }
        }

        if (result != null) {
            setupUI()
        }

        return binding.root
    }

    private fun showNoDataMessage() {
        binding.textDiagnosis.text = getString(R.string.diagnosis_title)
        binding.textPatientInfo.text = getString(R.string.patient_info_empty)
        binding.textPatientInfo.visibility = View.VISIBLE

        binding.reportButtonsContainer.visibility = View.GONE

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
        val currentResult = result ?: return
        val context = requireContext()

        binding.textDiagnosis.text = getString(R.string.diagnosis_patient_title, currentResult.patientName)
        binding.reportButtonsContainer.visibility = View.VISIBLE

        val notProvidedStr = getString(R.string.not_provided)
        val noDateStr = getString(R.string.no_date)

        val patientInfo = getString(
            R.string.patient_info_template,
            currentResult.patientName,
            currentResult.age,
            currentResult.species ?: notProvidedStr,
            currentResult.breed ?: notProvidedStr,
            currentResult.gender ?: notProvidedStr,
            currentResult.coat ?: notProvidedStr,
            currentResult.collectionDate ?: noDateStr
        )

        binding.textPatientInfo.text = patientInfo
        binding.textPatientInfo.visibility = View.VISIBLE

        val extractedMap = currentResult.rawDataJson?.let {
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                Gson().fromJson<Map<String, Any>>(it, type) ?: emptyMap()
            } catch (e: Exception) {
                Log.e("DiagnosisFragment", "Error parsing JSON", e)
                emptyMap()
            }
        } ?: emptyMap()

        if (extractedMap.isEmpty()) {
            binding.reportButtonsContainer.visibility = View.GONE
            return
        }

        val labResult = LabResultAnalyzer.analyzeLabData(extractedMap, context)
        val rivaltaStatus = currentResult.rivaltaStatus ?: getString(R.string.rivalta_not_performed)
        val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus, context)

        currentRiskPercentage = electroResult.riskPercentage
        currentRiskComment = electroResult.fipRiskComment
        currentScoreBreakdown = electroResult.scoreBreakdown
        currentDiagnosticComment = labResult.diagnosticComment
        currentSupplementAdvice = electroResult.supplementAdvice
        currentVetConsultationAdvice = electroResult.vetConsultationAdvice
        currentFurtherTestsAdvice = electroResult.furtherTestsAdvice
        currentGammopathyResult = currentResult.diagnosis

        currentAbnormalResults = prepareAbnormalResults(extractedMap)

        try {
            val chartImageViewField = binding.javaClass.getDeclaredField("chartImageView")
            chartImageViewField.isAccessible = true
            val chartImageView = chartImageViewField.get(binding) as? View
            chartImageView?.visibility = View.VISIBLE
        } catch (e: NoSuchFieldException) {
            Log.w("DiagnosisFragment", "chartImageView not found in layout")
        }

        // Usunięcie starych kart, aby uniknąć duplikatów przy ponownym ładowaniu
        try {
            val analysisContainerField = binding.javaClass.getDeclaredField("analysisContainer")
            analysisContainerField.isAccessible = true
            val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
            analysisContainer?.removeAllViews()
        } catch (e: NoSuchFieldException) {
            Log.w("DiagnosisFragment", "analysisContainer not found in layout")
        }

        currentResult.imagePath?.let { imagePath ->
            val chartFile = File(imagePath)
            if (chartFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imagePath)

                // --- ZMODYFIKOWANA LOGIKA ANALIZY ---
                // 1. Ustaw wewnętrzny stan analizatora (curveData i fractionRanges)
                ElectrophoresisShapeAnalyzer.analyzeChartBitmap(bitmap)

                // 2. Pobierz wyniki (AUC i Wariancja)
                currentGammaAnalysis = ElectrophoresisShapeAnalyzer.analyzeGammaPeak()
                currentAucAnalysis = ElectrophoresisShapeAnalyzer.getFractionsAUC()

                // 3. Pobierz "stare" wyniki (ShapeAnalysisResult)
                val shapeAnalysis =
                    ElectrophoresisShapeAnalyzer.analyzeElectrophoresisShape(context)

                // 4. POBIERZ NOWE WYNIKI PROPORCJI SZEROKOŚCI
                currentWidthRatios = ElectrophoresisShapeAnalyzer.calculateWidthRatios()
                // --- KONIEC ZMODYFIKOWANEJ LOGIKI ---

                shapeAnalysis?.let { analysis ->
                    val shapeCard = createShapeAnalysisCard(analysis)
                    try {
                        val analysisContainerField =
                            binding.javaClass.getDeclaredField("analysisContainer")
                        analysisContainerField.isAccessible = true
                        val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
                        analysisContainer?.addView(shapeCard)
                    } catch (e: NoSuchFieldException) {
                        Log.w("DiagnosisFragment", "analysisContainer not found in layout")
                    }
                    currentShapeAnalysis = analysis
                }

                // --- MODYFIKACJA: Przekaż 'currentWidthRatios' do karty ---
                if (currentGammaAnalysis != null || currentAucAnalysis != null || currentWidthRatios != null) {
                    val advancedCard = createAdvancedAnalysisCard(
                        currentGammaAnalysis,
                        currentAucAnalysis,
                        currentWidthRatios // Nowy parametr
                    )
                    try {
                        val analysisContainerField =
                            binding.javaClass.getDeclaredField("analysisContainer")
                        analysisContainerField.isAccessible = true
                        val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
                        analysisContainer?.addView(advancedCard)
                    } catch (e: NoSuchFieldException) {
                        Log.w("DiagnosisFragment", "analysisContainer not found in layout")
                    }
                }
            }
        }

        val patternAnalysis = FipPatternAnalyzer.analyzeParameterPatterns(extractedMap, context)
        val patternCard = createPatternAnalysisCard(patternAnalysis)

        try {
            val analysisContainerField = binding.javaClass.getDeclaredField("analysisContainer")
            analysisContainerField.isAccessible = true
            val analysisContainer = analysisContainerField.get(binding) as? ViewGroup
            analysisContainer?.addView(patternCard)
        } catch (e: NoSuchFieldException) {
            Log.w("DiagnosisFragment", "analysisContainer not found in layout")
        }

        currentPatternAnalysis = patternAnalysis

        binding.textDiagnosticComment.text = Html.fromHtml(electroResult.fipRiskComment, Html.FROM_HTML_MODE_COMPACT)

        val breakdownHtml = electroResult.scoreBreakdown.joinToString("<br>")
        binding.textRiskBreakdown.text = Html.fromHtml(getString(R.string.risk_breakdown_html, breakdownHtml), Html.FROM_HTML_MODE_COMPACT)

        binding.textFurtherTests.text = Html.fromHtml(getString(R.string.further_tests_html, electroResult.furtherTestsAdvice), Html.FROM_HTML_MODE_COMPACT)
        binding.textRiskSupplements.text = Html.fromHtml(getString(R.string.supplements_fip_html, electroResult.supplementAdvice), Html.FROM_HTML_MODE_COMPACT)
        binding.textRiskConsult.text = Html.fromHtml(getString(R.string.consult_fip_html, electroResult.vetConsultationAdvice), Html.FROM_HTML_MODE_COMPACT)

        binding.textSupplements.text = Html.fromHtml(getString(R.string.supplements_general_html, labResult.supplementAdvice), Html.FROM_HTML_MODE_COMPACT)
        binding.textVetConsult.text = Html.fromHtml(getString(R.string.consult_general_html, labResult.vetConsultationAdvice), Html.FROM_HTML_MODE_COMPACT)

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
                abnormalResults.add(getString(R.string.abnormal_result_format, testName, value, unit, minRange, maxRange))
            }
        }

        return abnormalResults
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
                return false
            }
            loggedIn = true
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

            FileInputStream(file).use { fis ->
                if (!ftpClient.storeFile(file.name, fis)) {
                    return@use false
                } else true
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
            } catch (ioe: IOException) {
                Log.e("FTP_CLEANUP", "Error disconnecting", ioe)
            }
        }
    }

    private fun downloadPdfReport() {
        val currentResult = result ?: run {
            Toast.makeText(requireContext(), getString(R.string.error_no_data_report), Toast.LENGTH_SHORT).show()
            return
        }

        val generator = PdfReportGenerator(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val shapePoints = currentShapeAnalysis?.let {
                ((it.fipShapeScore / 100f) * ElectrophoresisShapeAnalyzer.SHAPE_ANALYSIS_MAX_POINTS).toInt()
            } ?: 0

            val patternPoints = currentPatternAnalysis?.let {
                ((it.patternStrength / 100f) * ElectrophoresisShapeAnalyzer.PATTERN_ANALYSIS_MAX_POINTS).toInt()
            } ?: 0

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
                gammopathyResult = currentGammopathyResult,
                shapeAnalysis = currentShapeAnalysis,
                gammaAnalysisDetails = currentGammaAnalysis,
                aucMetrics = currentAucAnalysis,
                widthRatios = currentWidthRatios, // <-- DODANY PARAMETR
                patternAnalysis = currentPatternAnalysis,
                shapeAnalysisPoints = shapePoints,
                patternAnalysisPoints = patternPoints,
                maxShapePoints = ElectrophoresisShapeAnalyzer.SHAPE_ANALYSIS_MAX_POINTS,
                maxPatternPoints = ElectrophoresisShapeAnalyzer.PATTERN_ANALYSIS_MAX_POINTS
            )

            withContext(Dispatchers.Main) {
                if (fileName != null && localPath != null) {
                    Toast.makeText(requireContext(), getString(R.string.report_saved_success, fileName), Toast.LENGTH_LONG).show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val localFile = File(localPath)
                        if (localFile.exists()) {
                            uploadFileToFTP(localFile)
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_report_generation), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun printPdfReport() {
        val currentResult = result ?: run {
            Toast.makeText(requireContext(), getString(R.string.error_no_data_print), Toast.LENGTH_SHORT).show()
            return
        }

        val generator = PdfReportGenerator(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val shapePoints = currentShapeAnalysis?.let {
                ((it.fipShapeScore / 100f) * ElectrophoresisShapeAnalyzer.SHAPE_ANALYSIS_MAX_POINTS).toInt()
            } ?: 0

            val patternPoints = currentPatternAnalysis?.let {
                ((it.patternStrength / 100f) * ElectrophoresisShapeAnalyzer.PATTERN_ANALYSIS_MAX_POINTS).toInt()
            } ?: 0

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
                gammopathyResult = currentGammopathyResult,
                shapeAnalysis = currentShapeAnalysis,
                gammaAnalysisDetails = currentGammaAnalysis,
                aucMetrics = currentAucAnalysis,
                widthRatios = currentWidthRatios, // <-- DODANY PARAMETR
                patternAnalysis = currentPatternAnalysis,
                shapeAnalysisPoints = shapePoints,
                patternAnalysisPoints = patternPoints,
                maxShapePoints = ElectrophoresisShapeAnalyzer.SHAPE_ANALYSIS_MAX_POINTS,
                maxPatternPoints = ElectrophoresisShapeAnalyzer.PATTERN_ANALYSIS_MAX_POINTS
            )

            withContext(Dispatchers.Main) {
                if (fileName != null && localPath != null) {
                    val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = getString(R.string.print_job_name, currentResult.patientName)

                    printManager.print(
                        jobName,
                        FipReportPrintAdapter(requireContext(), localPath, fileName),
                        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        val localFile = File(localPath)
                        if (localFile.exists()) {
                            uploadFileToFTP(localFile)
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_print_generation), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class FipReportPrintAdapter(
        private val context: Context,
        private val pdfFilePath: String,
        private val fileName: String
    ) : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
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

        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
            try {
                FileInputStream(File(pdfFilePath)).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }

    private fun createShapeAnalysisCard(analysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult): CardView {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 16) }
            cardElevation = 4f
            radius = 8f
            setCardBackgroundColor(getThemedColor(requireContext(), android.R.attr.colorBackgroundFloating))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(requireContext()).apply {
            text = getString(R.string.shape_analysis_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(title)

        val pattern = TextView(requireContext()).apply {
            text = getString(R.string.pattern_label, analysis.overallPattern)
            textSize = 16f
            setPadding(0, 8, 0, 4)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(pattern)

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48).apply { setMargins(0, 8, 0, 8) }
            max = 100
            progress = analysis.fipShapeScore.toInt()
            val tintColor = when {
                analysis.fipShapeScore >= 70 -> Color.parseColor("#F44336")
                analysis.fipShapeScore >= 50 -> Color.parseColor("#FF9800")
                analysis.fipShapeScore >= 30 -> Color.parseColor("#FFC107")
                else -> Color.parseColor("#4CAF50")
            }
            progressTintList = ColorStateList.valueOf(tintColor)
        }
        content.addView(progressBar)

        val scoreView = TextView(requireContext()).apply {
            text = getString(R.string.match_strength_label, analysis.fipShapeScore.toInt())
            textSize = 14f
            setPadding(0, 4, 0, 8)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            gravity = Gravity.CENTER
        }
        content.addView(scoreView)

        val description = TextView(requireContext()).apply {
            text = analysis.shapeDescription
            textSize = 14f
            setPadding(0, 8, 0, 0)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(description)

        val detailsTitle = TextView(requireContext()).apply {
            text = getString(R.string.fraction_details_title)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 4)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(detailsTitle)

        val bridgeStatus = if (analysis.betaGammaBridge.present)
            getString(R.string.present_depth, (analysis.betaGammaBridge.depth * 100).toInt())
        else
            getString(R.string.absent)

        // --- POCZĄTEK POPRAWKI (Błąd 1: shape_details_template) ---
        // Obliczamy procentową szerokość gamma (FWHM) względem jej zakresu
        val gammaWidthPx50 = analysis.gamma.widthPxMap[50]?.toFloat() ?: 0f
        // Zabezpieczenie przed dzieleniem przez zero, jeśli zakres ma 0px
        val gammaRangeSize = analysis.gamma.rangeSize.toFloat().coerceAtLeast(1f)
        val gammaWidth50Percent = (gammaWidthPx50 / gammaRangeSize) * 100f

        // POPRAWKA: Usunięto szósty, nadmiarowy argument (A/G ratio).
        // Zasób string `shape_details_template` oczekuje 5 argumentów.
        val details = getString(R.string.shape_details_template,
            (analysis.albumin.height).toInt(), (analysis.albumin.symmetry * 100).toInt(),
            (analysis.gamma.height).toInt(),
            gammaWidth50Percent.toInt(), // Poprawne odwołanie do FWHM 50%
            bridgeStatus // 5-ty i ostatni argument
        )
        // --- KONIEC POPRAWKI ---

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

    private fun createPatternAnalysisCard(analysis: FipPatternAnalyzer.PatternAnalysisResult): CardView {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 16) }
            cardElevation = 4f
            radius = 8f
            setCardBackgroundColor(getThemedColor(requireContext(), android.R.attr.colorBackgroundFloating))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(requireContext()).apply {
            text = getString(R.string.pattern_profile_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(title)

        // Używamy opisów z FipPatternAnalyzer, które teraz są zlokalizowane
        val primaryDesc = when (analysis.primaryProfile) {
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_ACUTE -> getString(R.string.profile_inflammatory_acute)
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_CHRONIC -> getString(R.string.profile_inflammatory_chronic)
            FipPatternAnalyzer.FipProfile.EFFUSIVE_CLASSIC -> getString(R.string.profile_effusive_classic)
            FipPatternAnalyzer.FipProfile.DRY_NEUROLOGICAL -> getString(R.string.profile_dry_neurological)
            FipPatternAnalyzer.FipProfile.MIXED_PATTERN -> getString(R.string.profile_mixed)
            FipPatternAnalyzer.FipProfile.ATYPICAL -> getString(R.string.profile_atypical)
            FipPatternAnalyzer.FipProfile.NON_FIP -> getString(R.string.profile_non_fip)
        }

        val profile = TextView(requireContext()).apply {
            text = primaryDesc
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 4)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(profile)

        val strengthBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            max = 100
            progress = analysis.patternStrength.toInt()
            val tintColor = when {
                analysis.patternStrength >= 70 -> Color.parseColor("#F44336")
                analysis.patternStrength >= 50 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#4CAF50")
            }
            progressTintList = ColorStateList.valueOf(tintColor)
        }
        content.addView(strengthBar)

        val strengthText = TextView(requireContext()).apply {
            text = getString(R.string.match_strength_label, analysis.patternStrength.toInt())
            textSize = 14f
            setPadding(0, 4, 0, 8)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(strengthText)

        if (analysis.keyFindings.isNotEmpty()) {
            val findingsTitle = TextView(requireContext()).apply {
                text = getString(R.string.key_findings_title)
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

        val descTitle = TextView(requireContext()).apply {
            text = getString(R.string.profile_description_title)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 4)
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

        val suggestionsTitle = TextView(requireContext()).apply {
            text = getString(R.string.management_suggestions_title)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 4)
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

    private fun createAdvancedAnalysisCard(
        gammaAnalysis: ElectrophoresisShapeAnalyzer.GammaAnalysisResult?,
        aucMetrics: Map<String, Double>?,
        widthRatios: ElectrophoresisShapeAnalyzer.WidthRatioAnalysis?
    ): CardView {
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 16) }
            cardElevation = 4f
            radius = 8f
            setCardBackgroundColor(getThemedColor(requireContext(), android.R.attr.colorBackgroundFloating))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val title = TextView(requireContext()).apply {
            text = getString(R.string.diag_advanced_analysis_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
        }
        content.addView(title)

        val detailsPaint = TextPaint().apply {
            color = getThemedColor(requireContext(), android.R.attr.textColorPrimary)
            textSize = 14f
            isAntiAlias = true
        }

        // 1. Wyświetl analizę piku Gamma
        if (gammaAnalysis != null && gammaAnalysis.totalMass > 0) {
            val varianceLabel = getString(R.string.pdf_gamma_peak_variance) // Możemy reużyć stringów z PDF
            val varianceValue = String.format(Locale.getDefault(), "%.2f", gammaAnalysis.variance)
            val varianceTv = TextView(requireContext()).apply {
                text = "$varianceLabel $varianceValue"
                textSize = 14f
                setPadding(0, 8, 0, 4)
                setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            }
            content.addView(varianceTv)

            val stdDevLabel = getString(R.string.pdf_gamma_peak_std_dev)
            val stdDevValue = String.format(Locale.getDefault(), "%.2f", gammaAnalysis.stdDev)
            val stdDevTv = TextView(requireContext()).apply {
                text = "$stdDevLabel $stdDevValue"
                textSize = 14f
                setPadding(0, 4, 0, 4)
                setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            }
            content.addView(stdDevTv)
        }

        // 2. Wyświetl analizę AUC
        if (aucMetrics != null && aucMetrics.isNotEmpty()) {
            val aucTitle = TextView(requireContext()).apply {
                text = getString(R.string.diag_auc_analysis_title)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 8)
                setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            }
            content.addView(aucTitle)

            aucMetrics.forEach { (fractionKey, percentage) ->
                if (fractionKey != "TotalAUC_Pixels") {
                    val translatedName = getTranslatedFractionNameForUI(fractionKey)
                    val formattedLine = "$translatedName: ${String.format(Locale.getDefault(), "%.1f", percentage)} %"
                    val aucTv = TextView(requireContext()).apply {
                        text = formattedLine
                        textSize = 14f
                        setPadding(8, 4, 0, 4)
                        setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
                    }
                    content.addView(aucTv)
                }
            }
        }

        // 3. Wyświetl analizę proporcji szerokości
        if (widthRatios != null) {
            val ratiosTitle = TextView(requireContext()).apply {
                text = getString(R.string.diag_width_ratios_title)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 8)
                setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
            }
            content.addView(ratiosTitle)

            // Funkcja pomocnicza do tworzenia wierszy
            fun createRatioTextView(labelResId: Int, value: Float): TextView {
                return TextView(requireContext()).apply {
                    // 1. Pobieramy samą etykietę (np. "Gamma (70% H): ")
                    val label = getString(labelResId)
                    // 2. Formatujemy liczbę do jednego miejsca po przecinku (np. "43.6")
                    val formattedValue = String.format(Locale.getDefault(), "%.1f", value)
                    // 3. Łączymy wszystko w jeden tekst (np. "Gamma (70% H): 43.6 %")
                    text = "$label$formattedValue %"
                    textSize = 14f
                    setPadding(8, 4, 0, 4)
                    setTextColor(getThemedColor(requireContext(), android.R.attr.textColorPrimary))
                }
            }
            content.addView(createRatioTextView(R.string.diag_ratio_g70_beta, widthRatios.gamma70ToBeta))
            content.addView(createRatioTextView(R.string.diag_ratio_g50_beta, widthRatios.gamma50ToBeta))
            content.addView(createRatioTextView(R.string.diag_ratio_g30_beta, widthRatios.gamma30ToBeta))
            content.addView(createRatioTextView(R.string.diag_ratio_g70_g30, widthRatios.gamma70ToGamma30))
        }

        card.addView(content)
        return card
    }

    private fun getTranslatedFractionNameForUI(fractionKey: String): String {
        val context = requireContext()
        val resId = when (fractionKey) {
            "Albumin" -> R.string.pdf_fraction_albumin
            "Alpha" -> R.string.pdf_fraction_alpha1 // Używamy "Alpha-1" dla połączonej "Alpha"
            "Beta" -> R.string.pdf_fraction_beta
            "Gamma" -> R.string.pdf_fraction_gamma
            else -> 0
        }
        return if (resId != 0) context.getString(resId) else fractionKey
    }


    private fun getThemedColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            when (attr) {
                android.R.attr.textColorPrimary -> ContextCompat.getColor(context, android.R.color.black)
                android.R.attr.textColorSecondary -> ContextCompat.getColor(context, android.R.color.darker_gray)
                android.R.attr.colorBackgroundFloating -> Color.parseColor("#F8F8F8")
                else -> Color.BLACK
            }
        }
    }

    fun getLocalizedGammopathyResult(context: Context, internalResult: String?): String {
        return when (internalResult) {
            BarChartLevelAnalyzer.RESULT_MONOCLONAL -> context.getString(R.string.gammopathy_monoclonal)
            BarChartLevelAnalyzer.RESULT_POLYCLONAL -> context.getString(R.string.gammopathy_polyclonal)
            BarChartLevelAnalyzer.RESULT_NONE -> context.getString(R.string.gammopathy_none)
            else -> context.getString(R.string.gammopathy_none)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}