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

        // Jeśli nie mamy danych z argumentów, sprawdź SharedViewModel
        if (result == null) {
            sharedViewModel.selectedResult.observe(viewLifecycleOwner) { selectedResult ->
                if (selectedResult != null) {
                    result = selectedResult
                    Log.d("DiagnosisFragment", "Otrzymano wynik z SharedViewModel: ${result?.patientName}")
                    setupUI()
                } else {
                    loadLatestResult()
                }
            }
        } else {
            setupUI()
        }

        return binding.root
    }

    private fun loadLatestResult() {
        // Nie ładuj automatycznie danych z bazy - pokaż tylko komunikat
        showNoDataMessage()
    }

    private fun showNoDataMessage() {
        binding.textDiagnosis.text = "Diagnoza"
        binding.textPatientInfo.text = "Wprowadź dane do analizy\n\nAby zobaczyć diagnozę:\n• Przejdź do zakładki 'Skanuj' i wgraj plik PDF z wynikami\n• Lub wybierz rekord z 'Historii' badań"
        binding.textPatientInfo.visibility = View.VISIBLE

        // Ukryj pozostałe pola - ustaw ich tekst na pusty i ukryj
        binding.textDiagnosticComment.text = ""
        binding.textDiagnosticComment.visibility = View.GONE

        binding.textSupplements.text = ""
        binding.textSupplements.visibility = View.GONE

        binding.textVetConsult.text = ""
        binding.textVetConsult.visibility = View.GONE

        binding.textFurtherTests.text = ""
        binding.textFurtherTests.visibility = View.GONE

        binding.textRiskSupplements.text = ""
        binding.textRiskSupplements.visibility = View.GONE

        binding.textRiskConsult.text = ""
        binding.textRiskConsult.visibility = View.GONE

        binding.textRiskBreakdown.text = ""
        binding.textRiskBreakdown.visibility = View.GONE
    }

    private fun setupUI() {
        result?.let { res ->
            Log.d("DiagnosisFragment", "Konfigurowanie UI dla: ${res.patientName}")

            // Ustaw tytuł ekranu z nazwą pacjenta
            binding.textDiagnosis.text = "Diagnoza: ${res.patientName}"

            // Wyświetl informacje o pacjencie
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

            // Przygotuj dane do analizy
            val extractedMap = if (res.rawDataJson != null) {
                try {
                    Gson().fromJson(res.rawDataJson, Map::class.java) as? Map<String, Any> ?: emptyMap()
                } catch (e: Exception) {
                    Log.e("DiagnosisFragment", "Błąd parsowania JSON", e)
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            Log.d("DiagnosisFragment", "Dane do analizy: ${extractedMap.keys}")

            // Przeprowadź analizy
            val labResult = LabResultAnalyzer.analyzeLabData(extractedMap)
            val rivaltaStatus = res.rivaltaStatus ?: "nie wykonano"
            val electroResult = ElectrophoresisAnalyzer.assessFipRisk(extractedMap, rivaltaStatus)

            // Wyświetl ryzyko FIP z kolorami
            val riskText = Html.fromHtml(electroResult.fipRiskComment, Html.FROM_HTML_MODE_COMPACT)
            binding.textDiagnosticComment.text = riskText

            // Wyświetl szczegółowe uzasadnienie ryzyka
            val riskBreakdown = generateRiskBreakdown(extractedMap, rivaltaStatus, electroResult)
            binding.textRiskBreakdown.text = riskBreakdown
            binding.textRiskBreakdown.visibility = View.VISIBLE

            // Ustaw pozostałe wyniki analiz
            binding.textSupplements.text = "💊 Suplementy (wyniki krwi): ${labResult.supplementAdvice}"
            binding.textVetConsult.text = "🏥 Konsultacja (wyniki krwi): ${labResult.vetConsultationAdvice}"

            binding.textFurtherTests.text = "🔬 Zalecane dalsze badania:\n${electroResult.furtherTestsAdvice}"
            binding.textRiskSupplements.text = "💊 Suplementy (ryzyko FIP): ${electroResult.supplementAdvice}"
            binding.textRiskConsult.text = "🏥 Konsultacja (ryzyko FIP): ${electroResult.vetConsultationAdvice}"

            // Pokaż wszystkie pola
            listOf(
                binding.textDiagnosticComment,
                binding.textSupplements,
                binding.textVetConsult,
                binding.textFurtherTests,
                binding.textRiskSupplements,
                binding.textRiskConsult,
                binding.textRiskBreakdown
            ).forEach { it.visibility = View.VISIBLE }

        } ?: run {
            Log.d("DiagnosisFragment", "Brak danych o wyniku")
            showNoDataMessage()
        }
    }

    private fun generateRiskBreakdown(
        extractedMap: Map<String, Any>,
        rivaltaStatus: String,
        electroResult: ElectrophoresisAnalyzer.FipRiskResult
    ): String {
        val breakdown = StringBuilder()
        breakdown.append("📊 Szczegółowa analiza składowych ryzyka FIP:\n\n")

        // Funkcje pomocnicze
        fun toDoubleValue(str: String?): Double? {
            if (str == null) return null
            val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
            return cleaned.toDoubleOrNull()
        }

        fun findKeyContains(name: String): String? {
            return extractedMap.keys.find { it.contains(name, ignoreCase = true) }
        }

        // 1. Status próby Rivalta
        breakdown.append("🧪 Próba Rivalta: ")
        when(rivaltaStatus) {
            "pozytywna" -> breakdown.append("✅ POZYTYWNA - silnie wspiera FIP (+25 pkt)\n")
            "negatywna" -> breakdown.append("❌ NEGATYWNA - przeciw FIP (0 pkt)\n")
            else -> breakdown.append("❓ NIE WYKONANO - brak informacji (+12.5 pkt)\n")
        }

        // 2. Analiza gammapatii z wykresu
        val gammopathyResult = extractedMap["GammopathyResult"] as? String ?: "brak gammapatii"
        breakdown.append("\n📈 Elektroforeza (wykres): ")
        when {
            gammopathyResult.contains("poliklonalna") ->
                breakdown.append("✅ GAMMAPATIA POLIKLONALNA - typowa dla FIP (+20 pkt)\n")
            gammopathyResult.contains("monoklonalna") ->
                breakdown.append("❌ GAMMAPATIA MONOKLONALNA - nietypowa dla FIP (0 pkt)\n")
            else ->
                breakdown.append("❓ BRAK GAMMAPATII - słabiej wspiera FIP (+10 pkt)\n")
        }

        // 3. Stosunek albumina/globuliny (A/G)
        // Najpierw szukamy bezpośrednio obliczonego stosunku A/G w wynikach
        val agRatioKey = findKeyContains("Stosunek")
        var agRatio: Double? = null

        // Sprawdź czy mamy bezpośrednio stosunek A/G
        if (agRatioKey != null && agRatioKey.contains("albumin", ignoreCase = true)) {
            agRatio = toDoubleValue(extractedMap[agRatioKey] as? String)
        }

        // Jeśli nie znaleziono bezpośredniego stosunku, oblicz go
        if (agRatio == null) {
            val albuminKey = findKeyContains("Albumin") ?: findKeyContains("Albumina")
            val totalProteinKey = findKeyContains("Białko całkowite") ?: findKeyContains("Total Protein")
            val globulinKey = findKeyContains("Globulin")

            var albuminVal = albuminKey?.let { toDoubleValue(extractedMap[it] as? String) }
            var globulinsVal = globulinKey?.let { toDoubleValue(extractedMap[it] as? String) }

            if (globulinsVal == null && totalProteinKey != null && albuminVal != null) {
                val totalProtVal = toDoubleValue(extractedMap[totalProteinKey] as? String)
                if (totalProtVal != null) globulinsVal = totalProtVal - albuminVal
            }

            if (albuminVal != null && globulinsVal != null && globulinsVal > 0)
                agRatio = albuminVal / globulinsVal
        }

        breakdown.append("\n⚖️ Stosunek A/G: ")
        if (agRatio != null) {
            val formattedRatio = "%.2f".format(agRatio)
            when {
                agRatio < 0.6 -> breakdown.append("✅ $formattedRatio - BARDZO NISKI, silnie wspiera FIP (+15 pkt)\n")
                agRatio < 0.8 -> breakdown.append("⚠️ $formattedRatio - OBNIŻONY, umiarkowanie wspiera FIP (+7.5 pkt)\n")
                else -> breakdown.append("❌ $formattedRatio - NORMALNY, przeciw FIP (0 pkt)\n")
            }
        } else {
            breakdown.append("❓ BRAK DANYCH - nie można ocenić (0 pkt)\n")
        }

        // 4. Gamma-globuliny
        val gammaKey = findKeyContains("Gamma")
        val gammaVal = gammaKey?.let { toDoubleValue(extractedMap[it] as? String) }
        val gammaMax = gammaKey?.let { toDoubleValue(extractedMap["${it}RangeMax"] as? String) }

        breakdown.append("\n🔵 Gamma-globuliny: ")
        if (gammaVal != null && gammaMax != null) {
            when {
                gammaVal > gammaMax -> breakdown.append("✅ PODWYŻSZONE ($gammaVal > $gammaMax) - hipergammaglobulinemia wspiera FIP (+10 pkt)\n")
                else -> breakdown.append("❌ W NORMIE ($gammaVal ≤ $gammaMax) - przeciw FIP (0 pkt)\n")
            }
        } else {
            breakdown.append("❓ BRAK DANYCH - nie można ocenić (0 pkt)\n")
        }

        // 5. Test FCoV ELISA (jeśli dostępny)
        val fcovKey = extractedMap.keys.find { it.contains("FCoV", ignoreCase = true) && it.contains("ELISA", ignoreCase = true) }
        if (fcovKey != null) {
            val fcovValue = extractedMap[fcovKey] as? String
            val fcovResultText = extractedMap["${fcovKey}Range"] as? String

            breakdown.append("\n🦠 Test FCoV ELISA: ")
            if (!fcovValue.isNullOrBlank()) {
                val resultText = (fcovResultText ?: fcovValue).lowercase()
                when {
                    resultText.contains("dodatni") || resultText.contains("pozytywny") ->
                        breakdown.append("⚠️ DODATNI - zwiększa podejrzenie FIP (dodatkowy wskaźnik)\n")
                    resultText.contains("ujemny") || resultText.contains("negatywny") ->
                        breakdown.append("❌ UJEMNY - FIP mało prawdopodobny (dodatkowy wskaźnik)\n")
                    fcovValue.contains(":") -> {
                        val titerValue = runCatching { fcovValue.split(":").last().replace(",", ".").toDouble() }.getOrNull()
                        when {
                            titerValue != null && titerValue >= 400 ->
                                breakdown.append("⚠️ WYSOKIE MIANO ($fcovValue) - często przy FIP (dodatkowy wskaźnik)\n")
                            titerValue != null && titerValue >= 100 ->
                                breakdown.append("❓ UMIARKOWANE MIANO ($fcovValue) - wymaga kontekstu (dodatkowy wskaźnik)\n")
                            else ->
                                breakdown.append("❌ NISKIE MIANO ($fcovValue) - FIP mniej prawdopodobny (dodatkowy wskaźnik)\n")
                        }
                    }
                }
            }
        }

        // 6. Podsumowanie
        breakdown.append("\n" + "=".repeat(50))
        breakdown.append("\n🎯 KOŃCOWE RYZYKO FIP: ${electroResult.riskPercentage}%")
        breakdown.append("\n📝 Status: ${electroResult.rivaltaStatus}")

        val riskLevel = when {
            electroResult.riskPercentage >= 70 -> "🔴 WYSOKIE"
            electroResult.riskPercentage >= 30 -> "🟡 ŚREDNIE"
            else -> "🟢 NISKIE"
        }
        breakdown.append("\n📊 Poziom ryzyka: $riskLevel")

        return breakdown.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}