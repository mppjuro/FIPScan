package com.example.fipscan.ui.diagnosis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fipscan.ElectrophoresisAnalyzer
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
        }

        setupUI()
        return binding.root
    }

    private fun setupUI() {
        result?.let { res ->
            // Wyświetl informacje o pacjencie
            val patientInfo = """
                🐱 Pacjent: ${res.patientName}
                📅 Wiek: ${res.age}
                🐾 Gatunek: ${res.species ?: "nie podano"}
                🏷️ Rasa: ${res.breed ?: "nie podano"}
                ⚥ Płeć: ${res.gender ?: "nie podano"}
                📆 Data badania: ${res.collectionDate ?: "brak daty"}
            """.trimIndent()

            binding.textPatientInfo.text = patientInfo
            binding.textPatientInfo.visibility = View.VISIBLE

            // Przygotuj dane do analizy
            val extractedMap = if (res.rawDataJson != null) {
                Gson().fromJson(res.rawDataJson, Map::class.java) as? Map<String, Any> ?: emptyMap()
            } else {
                emptyMap()
            }

            // Przeprowadź analizy
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val rivaltaStatus = res.rivaltaStatus ?: "nie wykonano"
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

            // Ustaw wyniki analiz
            binding.textDiagnosticComment.text = labResult.diagnosticComment
            binding.textSupplements.text = "Suplementy: ${labResult.supplementAdvice}"
            binding.textVetConsult.text = "Konsultacja: ${labResult.vetConsultationAdvice}"

            binding.textFurtherTests.text = "Dalsze badania:\n${electroResult.furtherTestsAdvice}"
            binding.textRiskSupplements.text = "Suplementy: ${electroResult.supplementAdvice}"
            binding.textRiskConsult.text = "Konsultacja: ${electroResult.vetConsultationAdvice}"
        } ?: run {
            // Brak danych o pacjencie
            binding.textPatientInfo.visibility = View.GONE
            binding.textDiagnosticComment.text = "Brak danych pacjenta do analizy"

            // Ukryj pozostałe pola
            listOf(
                binding.textSupplements,
                binding.textVetConsult,
                binding.textFurtherTests,
                binding.textRiskSupplements,
                binding.textRiskConsult
            ).forEach { it.visibility = View.GONE }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}