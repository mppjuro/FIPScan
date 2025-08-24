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

class DiagnosisFragment : Fragment() {
    private var _binding: FragmentDiagnosisBinding? = null
    private val binding get() = _binding!!
    private var result: ResultEntity? = null
    private val sharedViewModel: SharedResultViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisBinding.inflate(inflater, container, false)

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
                // Można tu pokazać komunikat o braku danych
                return
            }

            // --- Analizy ---
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val rivaltaStatus = res.rivaltaStatus ?: "nie wykonano, płyn obecny"
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}