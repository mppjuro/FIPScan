package com.example.fipscan.ui.diagnosis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fipscan.ElectrophoresisAnalyzer
import com.example.fipscan.ExtractData
import com.example.fipscan.LabResultAnalyzer
import com.example.fipscan.databinding.FragmentDiagnosisBinding
import com.google.gson.Gson
import com.example.fipscan.ResultEntity
import android.util.Log

class DiagnosisFragment : Fragment() {
    private var _binding: FragmentDiagnosisBinding? = null
    private val binding get() = _binding!!
    private var result: ResultEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisBinding.inflate(inflater, container, false)

        arguments?.let {
            result = DiagnosisFragmentArgs.fromBundle(it).result
            result?.rawDataJson?.let { json ->
                ExtractData.lastExtracted = Gson().fromJson(json, Map::class.java) as? Map<String, Any> ?: emptyMap()
            }
        }
        Log.d("NavigateToDiagnosis", "Przekazywana diagnoza z wykresu: ${result?.diagnosis}")
        setupUI()
        return binding.root
    }

    private fun setupUI() {
        val extractedMap = ExtractData.lastExtracted ?: emptyMap()
        val diagnosisText = result?.diagnosis ?: "brak danych"

        // Nagłówek
        val patient = extractedMap["Pacjent"] as? String ?: "Nieznany"
        val date = extractedMap["Data"] as? String ?: ""
<<<<<<< HEAD

        if (patient == "Nieznany") {
            with(binding) {
                binding.textHeader.text = "📄 Wczytaj dane pacjenta"

                // Analizy
                val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
                val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap)

                textDiagnosticComment.text = "Wczytaj dane pacjenta"
                textSupplements.text = ""
                textVetConsult.text = ""

=======
        if (patient == "Nieznany") {
            binding.textHeader.text = "📄 Wczytaj dane pacjenta"

            // Analizy
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap)

            // Wyświetlanie wyników
            with(binding) {
                textDiagnosticComment.text = "Wczytaj dane pacjenta"
                textSupplements.text = ""
                textVetConsult.text = ""

>>>>>>> 9b288a25f89f68977e1deff6e8921b602853953d
                textRiskComment.text = ""
                textFurtherTests.text = ""
                textRiskSupplements.text = ""
                textRiskConsult.text = ""
            }
        } else {
<<<<<<< HEAD
            with(binding) {
                binding.textHeader.text = "📄 Diagnoza: $patient  ${if (date.isNotBlank()) "📅 $date" else ""}"

                // Analizy
                val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
                val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap)

=======
            binding.textHeader.text = "📄 Diagnoza: $patient  ${if (date.isNotBlank()) "📅 $date" else ""}"

            // Analizy
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap)

            // Wyświetlanie wyników
            with(binding) {
>>>>>>> 9b288a25f89f68977e1deff6e8921b602853953d
                textDiagnosticComment.text = labResult.diagnosticComment
                textSupplements.text = "Suplementy: ${labResult.supplementAdvice}"
                textVetConsult.text = "Konsultacja: ${labResult.vetConsultationAdvice}"

                textRiskComment.text = electroResult.fipRiskComment
                textFurtherTests.text = "Dalsze badania:\n${electroResult.furtherTestsAdvice}"
                textRiskSupplements.text = "Suplementy: ${electroResult.supplementAdvice}"
                textRiskConsult.text = "Konsultacja: ${electroResult.vetConsultationAdvice}\n\n\n"
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}