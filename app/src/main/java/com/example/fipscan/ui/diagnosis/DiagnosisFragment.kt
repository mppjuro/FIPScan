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
        result?.let { res ->
            Log.d("DiagnosisFragment", "Konfigurowanie UI dla: ${res.patientName}")

            binding.textDiagnosis.text = "Diagnoza: ${res.patientName}"

            // Pokaż przyciski raportu
            binding.reportButtonsContainer.visibility = View.VISIBLE

            val patientInfo = """
                🐱 Pacjent: ${res.patientName}
                📅 Wiek: ${res.age}
                🐾 Gatunek: ${res.species ?: "nie podano"}
                🏷️ Rasa: ${res.breed ?: "nie podano"}
                ⚥ Płeć: ${res.gender ?: "nie podano"}
                🎨 Umaszczenie: ${res.coat ?: "nie podano"}
                📆 Data badania: ${res.collectionDate ?: "brak daty"}
            """.trimIndent()
            binding.textPatientInfo.text = patientInfo
            binding.textPatientInfo.visibility = View.VISIBLE

            val extractedMap = res.rawDataJson?.let {
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
            val rivaltaStatus = res.rivaltaStatus ?: "nie wykonano, płyn obecny"
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

            // Przechowaj dane do generowania raportu
            currentRiskPercentage = electroResult.riskPercentage
            currentRiskComment = electroResult.fipRiskComment
            currentScoreBreakdown = electroResult.scoreBreakdown
            currentDiagnosticComment = labResult.diagnosticComment
            currentSupplementAdvice = electroResult.supplementAdvice
            currentVetConsultationAdvice = electroResult.vetConsultationAdvice
            currentFurtherTestsAdvice = electroResult.furtherTestsAdvice
            currentGammopathyResult = res.diagnosis

            // Przygotuj listę nieprawidłowych wyników
            currentAbnormalResults = prepareAbnormalResults(extractedMap)

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

        } ?: run {
            Log.d("DiagnosisFragment", "Brak danych o wyniku, pokazuję wiadomość domyślną.")
            showNoDataMessage()
        }
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
        result?.let { res ->
            val generator = PdfReportGenerator(requireContext())

            lifecycleScope.launch(Dispatchers.IO) {
                val (fileName, localPath) = generator.generateReport(
                    patientName = res.patientName,
                    age = res.age,
                    species = res.species,
                    breed = res.breed,
                    gender = res.gender,
                    coat = res.coat,
                    collectionDate = res.collectionDate,
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
        } ?: run {
            Toast.makeText(
                requireContext(),
                "Brak danych do wygenerowania raportu",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun printPdfReport() {
        result?.let { res ->
            val generator = PdfReportGenerator(requireContext())

            lifecycleScope.launch(Dispatchers.IO) {
                // Najpierw generuj PDF
                val (fileName, localPath) = generator.generateReport(
                    patientName = res.patientName,
                    age = res.age,
                    species = res.species,
                    breed = res.breed,
                    gender = res.gender,
                    coat = res.coat,
                    collectionDate = res.collectionDate,
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
                        val jobName = "Raport FIP - ${res.patientName}"

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
        } ?: run {
            Toast.makeText(
                requireContext(),
                "Brak danych do wydrukowania raportu",
                Toast.LENGTH_SHORT
            ).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}