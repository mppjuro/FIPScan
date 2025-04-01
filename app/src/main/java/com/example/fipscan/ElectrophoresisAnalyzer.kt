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

    data class FipRiskResult(
        val fipRiskComment: String,
        val furtherTestsAdvice: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String
    )

    /**
     * Ocenia ryzyko FIP na podstawie danych elektroforezy białek.
     * @param labData Mapa danych liczbowych z elektroforezy (albumina, frakcje globulin: alfa1, alfa2, beta, gamma, ewentualnie białko całkowite).
     * @return [FipRiskResult] z komentarzem o ryzyku FIP, zaleceniami dalszych badań oraz ewentualnymi sugestiami suplementacji i konsultacji.
     */
    fun assessFipRisk(labData: Map<String, Any>): FipRiskResult {
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
        val albuminKey = findKeyContains("Albumin") ?: findKeyContains("Albumina")
        val totalProteinKey = findKeyContains("Białko całkowite") ?: findKeyContains("Total Protein")
        val globulinKey = findKeyContains("Globulin")  // całkowite globuliny, jeśli dostępne
        var albuminVal = albuminKey?.let { toDoubleValue(labData[it] as? String) }
        var globulinsVal = globulinKey?.let { toDoubleValue(labData[it] as? String) }
        if (globulinsVal == null && totalProteinKey != null && albuminVal != null) {
            val totalProtVal = toDoubleValue(labData[totalProteinKey] as? String)
            if (totalProtVal != null) globulinsVal = totalProtVal - albuminVal
        }
        val agRatio = if (albuminVal != null && globulinsVal != null && globulinsVal > 0) albuminVal / globulinsVal else null

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
        val gammaKey = findKeyContains("Gamma")  // np. "Globuliny gamma"
        if (gammaKey != null) {
            val gammaVal = toDoubleValue(labData[gammaKey] as? String)
            val gammaMax = toDoubleValue(labData["${gammaKey}RangeMax"] as? String)
            if (gammaVal != null && gammaMax != null) {
                if (gammaVal > gammaMax) {
                    comments.add("Gamma-globuliny znacznie podwyższone – wyraźna hipergammaglobulinemia (często spotykana u kotów z FIP).")
                } else {
                    comments.add("Gamma-globuliny w normie – brak hipergammaglobulinemii typowej dla FIP.")
                }
            }
        }

        // Charakter gammapatii
        val fractionKeys = labData.keys.filter {
            it.contains("globulin", ignoreCase = true) || it.contains("globulin", ignoreCase = true)
        }
        var highFractionCount = 0
        var highGammaOnly = false
        for (key in fractionKeys) {
            if (key.equals(globulinKey, ignoreCase = true)) continue  // pomiń całkowite globuliny
            val valD = toDoubleValue(labData[key] as? String)
            val maxD = toDoubleValue(labData["${key}RangeMax"] as? String)
            if (valD != null && maxD != null && valD > maxD) {
                highFractionCount++
                if (gammaKey != null && key.equals(gammaKey, ignoreCase = true)) {
                    highGammaOnly = true
                }
            }
        }
        if (highFractionCount > 1) {
            comments.add("Wzrost wielokrotny frakcji globulin – **gammapatia poliklonalna** (typowa dla FIP i przewlekłych zapaleń).")
        } else if (highFractionCount == 1 && highGammaOnly) {
            comments.add("Dominuje wzrost jedynie frakcji gamma – możliwa **gammapatia monoklonalna** (nietypowa dla FIP, sugeruje np. szpiczaka).")
        }

        // Ogólny komentarz o ryzyku FIP
        val fipRiskComment = when {
            (agRatio != null && agRatio < 0.6) && highFractionCount > 1 -> {
                "Profil elektroforezy jest **silnie podejrzany** w kierunku FIP (bardzo niski A/G, poliklonalna hipergammaglobulinemia)."
            }
            (agRatio != null && agRatio < 0.8) || (gammaKey != null && highFractionCount > 0) -> {
                "Profil elektroforezy wykazuje odchylenia, które **mogą wskazywać na FIP**, ale nie są jednoznaczne. Zalecana jest dalsza diagnostyka."
            }
            else -> {
                "Profil elektroforezy **nie wskazuje na FIP** – wartości frakcji białkowych są w granicach normy lub brak charakterystycznych zmian."
            }
        }

        // Zalecenia dalszych badań
        if (fipRiskComment.contains("silnie podejrzany", ignoreCase = true) || fipRiskComment.contains("wskazywać na FIP", ignoreCase = true)) {
            furtherTests.add("Dalsza diagnostyka w kierunku FIP jest wskazana:")
            furtherTests.add("- **Test Rivalta** na płynie (jeśli występuje wysięk) – potwierdzenie charakteru zapalenia.")
            furtherTests.add("- **PCR na FCoV** (z krwi lub płynu) – wykrycie materiału genetycznego koronawirusa.")
            furtherTests.add("- **USG jamy brzusznej** – poszukiwanie zmian w narządach (powiększone węzły, zmiany w wątrobie, nerkach).")
            furtherTests.add("- **Badanie cytologiczne/histopatologiczne** (biopsja) zmienionych tkanek lub węzłów – dla ostatecznego potwierdzenia FIP, jeśli możliwe.")
        } else if (fipRiskComment.contains("odchylenia, które mogą wskazywać", ignoreCase = true)) {
            furtherTests.add("Zaleca się obserwację i ewentualne **powtórzenie elektroforezy** za kilka tygodni w celu oceny trendu zmian.")
            furtherTests.add("Jeśli objawy kliniczne się utrzymują lub nasilają, rozważyć wykonanie badań w kierunku FIP (Rivalta, PCR, badania obrazowe).")
        } else {
            furtherTests.add("Na tym etapie **dalsze specjalistyczne testy** pod kątem FIP nie są konieczne – profil białek nie wskazuje na FIP.")
            furtherTests.add("Kontynuuj monitorowanie stanu kota; w razie pojawienia się niepokojących objawów rozważ ponowną ocenę.")
        }

        // Suplementy / postępowanie wspomagające
        if (fipRiskComment.contains("silnie podejrzany", ignoreCase = true)) {
            supplements.add("Brak specyficznych suplementów – konieczne będzie **leczenie przyczynowe** (terapia FIP pod nadzorem weterynarza) po potwierdzeniu diagnozy.")
            vetConsultationNeeded = true
        } else if (fipRiskComment.contains("wskazują", ignoreCase = true) || fipRiskComment.contains("wskazywać", ignoreCase = true)) {
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

        // Złożenie wyników w strukturę FipRiskResult
        val testsAdviceText = furtherTests.joinToString("\n")
        val supplementText = supplements.joinToString(" ")
        return FipRiskResult(fipRiskComment, testsAdviceText, supplementText, vetConsultationAdvice)
    }
}
