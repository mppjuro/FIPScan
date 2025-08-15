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
        val patient = result?.patientName ?: "Nieznany"
        val date = result?.collectionDate ?: ""

        with(binding) {
            binding.textHeader.text = "📄 Diagnoza: $patient  ${if (date.isNotBlank()) "📅 $date" else ""}"

            // Analizy
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val rivaltaStatus = result?.rivaltaStatus ?: "nie wykonano"
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

            textDiagnosticComment.text = labResult.diagnosticComment
            textSupplements.text = "Suplementy: ${labResult.supplementAdvice}"
            textVetConsult.text = "Konsultacja: ${labResult.vetConsultationAdvice}"

            textFurtherTests.text = "Dalsze badania:\n${electroResult.furtherTestsAdvice}"
            textRiskSupplements.text = "Suplementy: ${electroResult.supplementAdvice}"
            textRiskConsult.text = "Konsultacja: ${electroResult.vetConsultationAdvice}\n\n\n"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}