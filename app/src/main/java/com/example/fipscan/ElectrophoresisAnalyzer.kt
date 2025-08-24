package com.example.fipscan

/**
 * Analizator danych z elektroforezy białek surowicy w kontekście FIP.
 * Ocena obejmuje:
 * - nasilenie hipergammaglobulinemii (wzrost frakcji gamma-globulin),
 * - stosunek albumina/globuliny (A/G),
 * - charakter gammapatii: poliklonalna (szeroki wzrost wielu frakcji) vs monoklonalna (wąski pik jednej frakcji).
 * Wynik analizy to komentarz o ryzyku FIP oraz zalecenia dalszych badań i ewentualnego postępowania.
 */
object ElectrophoresisAnalyzer {

    // Wagi parametrów (można łatwo modyfikować)
    private const val WEIGHT_GAMMOPATHY = 0.4f
    private const val WEIGHT_AG_RATIO = 0.3f
    private const val WEIGHT_GAMMA = 0.2f
    private const val WEIGHT_RIVALTA = 0.6f

    data class FipRiskResult(
        val fipRiskComment: String,
        val furtherTestsAdvice: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String,
        val riskPercentage: Int,
        val rivaltaStatus: String,
        val riskColor: String
    )

    fun assessFipRisk(labData: Map<String, Any>, rivaltaStatus: String): FipRiskResult {
        fun toDoubleValue(str: String?): Double? {
            if (str == null) return null
            val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
            return cleaned.toDoubleOrNull()
        }
        fun findKeyContains(name: String): String? {
            return labData.keys.find { it.contains(name, ignoreCase = true) }
        }

        val comments = mutableListOf<String>()
        val furtherTests = mutableListOf<String>()
        val supplements = mutableListOf<String>()
        var vetConsultationNeeded = false

        // Albumina i globuliny całkowite (dla A/G)
        // Najpierw szukamy bezpośrednio obliczonego stosunku A/G w wynikach
        val agRatioKey = findKeyContains("Stosunek") ?: findKeyContains("A/G")
        var agRatio: Double? = null

        // Sprawdź czy mamy bezpośrednio stosunek A/G
        if (agRatioKey != null && agRatioKey.contains("albumin", ignoreCase = true)) {
            agRatio = toDoubleValue(labData[agRatioKey] as? String)
        }

        // Jeśli nie znaleziono bezpośredniego stosunku, oblicz go
        if (agRatio == null) {
            val albuminKey = findKeyContains("Albumin") ?: findKeyContains("Albumina")
            val totalProteinKey = findKeyContains("Białko całkowite") ?: findKeyContains("Total Protein")
            val globulinKey = findKeyContains("Globulin")
            var albuminVal = albuminKey?.let { toDoubleValue(labData[it] as? String) }
            var globulinsVal = globulinKey?.let { toDoubleValue(labData[it] as? String) }
            if (globulinsVal == null && totalProteinKey != null && albuminVal != null) {
                val totalProtVal = toDoubleValue(labData[totalProteinKey] as? String)
                if (totalProtVal != null) globulinsVal = totalProtVal - albuminVal
            }
            if (albuminVal != null && globulinsVal != null && globulinsVal > 0) {
                agRatio = albuminVal / globulinsVal
            }
        }

        // Ocena A/G
        if (agRatio != null) {
            if (agRatio < 0.6) {
                comments.add("Stosunek A/G = ${"%.2f".format(agRatio)} – **bardzo niski** (<0,6), co silnie wspiera podejrzenie FIP.")
            } else if (agRatio < 0.8) {
                comments.add("Stosunek A/G = ${"%.2f".format(agRatio)} – obniżony (<0,8). Może to wskazywać na przewlekły stan zapalny; FIP jest możliwy, ale nie pewny.")
            } else {
                comments.add("Stosunek A/G = ${"%.2f".format(agRatio)} – w normie (>0,8), co **obniża** prawdopodobieństwo FIP.")
            }
        }

        // Ocena gamma-globulin
        val gammaKey = findKeyContains("Gamma")
        var gammaVal: Double? = null
        var gammaMax: Double? = null
        if (gammaKey != null) {
            gammaVal = toDoubleValue(labData[gammaKey] as? String)
            gammaMax = toDoubleValue(labData["${gammaKey}RangeMax"] as? String)
            if (gammaVal != null && gammaMax != null) {
                if (gammaVal > gammaMax) {
                    comments.add("Gamma-globuliny znacznie podwyższone – wyraźna hipergammaglobulinemia (często spotykana u kotów z FIP).")
                } else {
                    comments.add("Gamma-globuliny w normie – brak hipergammaglobulinemii typowej dla FIP.")
                }
            }
        }

        // Charakter gammapatii (zakładamy że wynik jest w labData)
        val gammopathyResult = labData["GammopathyResult"] as? String ?: "brak gammapatii"

        // Oblicz ryzyko FIP
        val (riskPercentage, riskColor) = calculateRisk(
            gammopathyResult,
            agRatio?.toFloat(),
            gammaVal?.toFloat(),
            gammaMax?.toFloat(),
            rivaltaStatus
        )

        // Sformatuj komentarz o ryzyku
        val riskLevelText = when {
            riskPercentage >= 70 -> "WYSOKIE RYZYKO FIP ($riskPercentage%)"
            riskPercentage >= 30 -> "ŚREDNIE RYZYKO FIP ($riskPercentage%)"
            else -> "NISKIE RYZYKO FIP ($riskPercentage%)"
        }

        val coloredRiskComment = "<font color='$riskColor'>$riskLevelText</font><br><br>" +
                comments.joinToString("<br>")

        // Zalecenia dalszych badań
        if (riskPercentage >= 70) {
            furtherTests.add("Dalsza diagnostyka w kierunku FIP jest wskazana:")
            furtherTests.add("- **Test Rivalta** na płynie (jeśli występuje wysięk) – potwierdzenie charakteru zapalenia.")
            furtherTests.add("- **PCR na FCoV** (z krwi lub płynu) – wykrycie materiału genetycznego koronawirusa.")
            furtherTests.add("- **USG jamy brzusznej** – poszukiwanie zmian w narządach (powiększone węzły, zmiany w wątrobie, nerkach).")
            furtherTests.add("- **Badanie cytologiczne/histopatologiczne** (biopsja) zmienionych tkanek lub węzłów – dla ostatecznego potwierdzenia FIP, jeśli możliwe.")
        } else if (riskPercentage >= 30) {
            furtherTests.add("Zaleca się obserwację i ewentualne **powtórzenie elektroforezy** za kilka tygodni w celu oceny trendu zmian.")
            furtherTests.add("Jeśli objawy kliniczne się utrzymują lub nasilają, rozważyć wykonanie badań w kierunku FIP (Rivalta, PCR, badania obrazowe).")
        } else {
            furtherTests.add("Na tym etapie **dalsze specjalistyczne testy** pod kątem FIP nie są konieczne – profil białek nie wskazuje na FIP.")
            furtherTests.add("Kontynuuj monitorowanie stanu kota; w razie pojawienia się niepokojących objawów rozważ ponowną ocenę.")
        }

        // Suplementy / postępowanie wspomagające
        if (riskPercentage >= 70) {
            supplements.add("Brak specyficznych suplementów – konieczne będzie **leczenie przyczynowe** (terapia FIP pod nadzorem weterynarza) po potwierdzeniu diagnozy.")
            vetConsultationNeeded = true
        } else if (riskPercentage >= 30) {
            supplements.add("Można rozważyć **wspomaganie odporności** (np. beta-glukany, witaminy) w porozumieniu z weterynarzem, oczekując na ostateczną diagnozę.")
            vetConsultationNeeded = true
        } else {
            supplements.add("Nie wymaga specjalnej suplementacji – ważna jest dobra dieta i opieka ogólna. ")
        }

        // Konsultacja weterynaryjna
        val vetConsultationAdvice = if (vetConsultationNeeded) {
            "Wskazana konsultacja z lekarzem weterynarii (specjalistą chorób wewnętrznych/zakaźnych) w celu omówienia wyników i zaplanowania dalszych kroków."
        } else {
            "Specjalistyczna konsultacja weterynaryjna nie jest na razie wymagana na podstawie profilu białek."
        }

        return FipRiskResult(
            fipRiskComment = coloredRiskComment,
            furtherTestsAdvice = furtherTests.joinToString("\n"),
            supplementAdvice = supplements.joinToString(" "),
            vetConsultationAdvice = vetConsultationAdvice,
            riskPercentage = riskPercentage,
            rivaltaStatus = rivaltaStatus,
            riskColor = riskColor
        )
    }

    private fun calculateRisk(
        gammopathyResult: String,
        agRatio: Float?,
        gammaVal: Float?,
        gammaMax: Float?,
        rivaltaStatus: String
    ): Pair<Int, String> {
        var totalWeight = 0f
        var weightedSum = 0f

        // Zawsze uwzględniaj status Rivalta
        val rivaltaScore = when(rivaltaStatus) {
            "pozytywna" -> 1.0f
            "negatywna" -> 0.0f
            else -> 0.5f // "nie wykonano"
        }
        weightedSum += rivaltaScore * WEIGHT_RIVALTA
        totalWeight += WEIGHT_RIVALTA

        // 1. Gammopatia (wykres)
        val gammopathyScore = when {
            gammopathyResult.contains("poliklonalna") -> 1.0f
            gammopathyResult.contains("monoklonalna") -> 0.0f
            else -> 0.5f // 'brak gammapatii' lub inne
        }
        weightedSum += gammopathyScore * WEIGHT_GAMMOPATHY
        totalWeight += WEIGHT_GAMMOPATHY

        // 2. Stosunek A/G
        agRatio?.let {
            val agScore = when {
                it < 0.6f -> 1.0f
                it < 0.8f -> 0.5f
                else -> 0.0f
            }
            weightedSum += agScore * WEIGHT_AG_RATIO
            totalWeight += WEIGHT_AG_RATIO
        }

        // 3. Gamma-globuliny
        if (gammaVal != null && gammaMax != null) {
            val gammaScore = if (gammaVal > gammaMax) 1.0f else 0.0f
            weightedSum += gammaScore * WEIGHT_GAMMA
            totalWeight += WEIGHT_GAMMA
        }

        // 4. Próba Rivalta
        if (rivaltaStatus != "nie wykonano") {
            val rivaltaScore = if (rivaltaStatus == "pozytywna") 1.0f else 0.0f
            weightedSum += rivaltaScore * WEIGHT_RIVALTA
            totalWeight += WEIGHT_RIVALTA
        }

        val riskPercentage = if (totalWeight > 0) {
            (weightedSum / totalWeight * 100).toInt()
        } else {
            0
        }
        return Pair(riskPercentage, getRiskColor(riskPercentage))
    }

    private fun getRiskColor(percentage: Int): String {
        return when {
            percentage >= 70 -> "#FF0000" // Czerwony
            percentage >= 30 -> "#FFA500" // Pomarańczowy
            else -> "#00FF00" // Zielony
        }
    }
}