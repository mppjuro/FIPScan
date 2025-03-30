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

class DiagnosisFragment : Fragment() {
    private var _binding: FragmentDiagnosisBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisBinding.inflate(inflater, container, false)

        val extractedMap = ExtractData.lastExtracted

        if (extractedMap.isNullOrEmpty()) {
            binding.textDiagnosticComment.text = "Brak dostępnych danych do analizy."
            return binding.root
        }

        // Nagłówek z nazwą kota i datą
        val patient = extractedMap["Pacjent"] as? String ?: "Nieznany"
        val date = extractedMap["Data"] as? String ?: ""
        binding.textHeader.text = "📄 Diagnoza: $patient  ${if (date.isNotBlank()) "📅 $date" else ""}"

        val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
        val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap)

        binding.textDiagnosticComment.text = labResult.diagnosticComment
        binding.textSupplements.text = "Suplementy: ${labResult.supplementAdvice}\n"
        binding.textVetConsult.text = "Konsultacja: ${labResult.vetConsultationAdvice}\n"

        binding.textRiskComment.text = electroResult.fipRiskComment
        binding.textFurtherTests.text = "Dalsze badania:\n${electroResult.furtherTestsAdvice}\n"
        binding.textRiskSupplements.text = "Suplementy: ${electroResult.supplementAdvice}\n"
        binding.textRiskConsult.text = "Konsultacja: ${electroResult.vetConsultationAdvice}\n"

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
