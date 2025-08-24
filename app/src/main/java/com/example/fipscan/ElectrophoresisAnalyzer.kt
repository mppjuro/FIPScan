package com.example.fipscan

import java.util.regex.Pattern
import android.util.Log

/**
 * Analizator wyników badań pod kątem ryzyka FIP (Feline Infectious Peritonitis).
 * Wykorzystuje ważony system punktowy oparty na wytycznych ABCD (Advisory Board on Cat Diseases).
 */
object ElectrophoresisAnalyzer {

    /**
     * Struktura przechowująca wynik analizy ryzyka FIP.
     * @param riskPercentage Procentowe ryzyko FIP (0-100%).
     * @param fipRiskComment Komentarz tekstowy podsumowujący ryzyko.
     * @param scoreBreakdown Lista ciągów znaków, szczegółowo opisująca, które czynniki wpłynęły na wynik.
     * @param furtherTestsAdvice Sugestie dotyczące dalszych badań.
     * @param supplementAdvice Zalecenia dotyczące suplementacji.
     * @param vetConsultationAdvice Sugestia konsultacji weterynaryjnej.
     */
    data class FipRiskResult(
        val riskPercentage: Int,
        val fipRiskComment: String,
        val scoreBreakdown: List<String>,
        val furtherTestsAdvice: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String
    )

    // --- Funkcje pomocnicze ---
    private fun toDoubleValue(str: String?): Double? {
        if (str == null) return null
        val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun findKeyContains(name: String, data: Map<String, Any>): String? {
        return data.keys.find { it.contains(name, ignoreCase = true) }
    }

    private fun isValueHigh(value: Double?, maxNorm: Double?): Boolean {
        return value != null && maxNorm != null && value > maxNorm
    }

    private fun isValueLow(value: Double?, minNorm: Double?): Boolean {
        return value != null && minNorm != null && value < minNorm
    }

    private fun parseAgeInYears(ageString: String?): Int? {
        if (ageString == null) return null
        val pattern = Pattern.compile("(\\d+)\\s*(lat|lata|rok)")
        val matcher = pattern.matcher(ageString)
        return if (matcher.find()) {
            matcher.group(1).toIntOrNull()
        } else {
            // Jeśli nie ma lat, może to być kot poniżej roku, zwracamy 0
            if (ageString.contains("miesiąc") || ageString.contains("miesiące")) 0 else null
        }
    }


    /**
     * Ocenia ryzyko FIP na podstawie danych laboratoryjnych i statusu próby Rivalta.
     * Implementuje ważony system punktowy na podstawie wytycznych ABCD.
     *
     * @param labData Mapa z wynikami laboratoryjnymi.
     * @param rivaltaStatus Status próby Rivalta ("pozytywna", "negatywna", "nie wykonano").
     * @return [FipRiskResult] zawierający ocenę ryzyka i szczegółowe uzasadnienie.
     */
    fun assessFipRisk(labData: Map<String, Any>, rivaltaStatus: String): FipRiskResult {
        var totalScore = 0
        var maxScore = 0
        val breakdown = mutableListOf<String>()

        // Mapowanie wag ABCD na punkty
        val pointsMap = mapOf(
            "++++" to 40, "+++" to 30, "++" to 20, "+" to 10,
            "-" to -10, "--" to -20
        )

        // 1. Wiek (< 2 lata) -> waga ++++
        val agePoints = pointsMap["++"]!!
        maxScore += agePoints
        val ageInYears = parseAgeInYears(labData["Wiek"] as? String)
        if (ageInYears != null && ageInYears < 2) {
            totalScore += agePoints
            breakdown.add("✅ Wiek poniżej 2 lat: <b>+$agePoints pkt</b> (bardzo sugestywny)")
        } else {
            breakdown.add("❌ Wiek ≥ 2 lata: <b>+0 pkt</b>")
        }

        // 2. Próba Rivalta -> waga ++
        val rivaltaPoints = pointsMap["++++"]!!
        maxScore += rivaltaPoints
        when (rivaltaStatus) {
            "pozytywna" -> {
                totalScore += rivaltaPoints
                breakdown.add("✅ Próba Rivalta pozytywna: <b>+$rivaltaPoints pkt</b> (umiarkowanie sugestywna)")
            }
            "negatywna / brak płynu" -> breakdown.add("❌ Próba Rivalta negatywna: <b>+0 pkt</b>")
            else -> {
                // Za brak wykonania dodajemy połowę punktów jako "podejrzenie"
                totalScore += rivaltaPoints / 2
                breakdown.add("❓ Próba Rivalta nie wykonano: <b>+${rivaltaPoints/2} pkt</b> (brak danych osłabia diagnozę różnicową)")
            }
        }

        // 3. Hiperglobulinemia -> waga +++
        val hyperglobPoints = pointsMap["+++"]!!
        maxScore += hyperglobPoints
        val globulinKey = findKeyContains("Globulin", labData)
        if (globulinKey != null) {
            val globVal = toDoubleValue(labData[globulinKey] as? String)
            val globMax = toDoubleValue(labData["${globulinKey}RangeMax"] as? String)
            if (isValueHigh(globVal, globMax)) {
                totalScore += hyperglobPoints
                breakdown.add("✅ Hiperglobulinemia: <b>+$hyperglobPoints pkt</b> (silnie sugestywna)")
            } else {
                breakdown.add("❌ Globuliny w normie: <b>+0 pkt</b>")
            }
        }

        // 4. Stosunek A/G < 0.4 -> waga ++ (zastosujemy gradację)
        val agRatioPoints = pointsMap["++++"]!!
        maxScore += agRatioPoints
        var agRatio: Double? = null
        val agRatioKey = findKeyContains("Stosunek", labData)
        if (agRatioKey != null && agRatioKey.contains("albumin", ignoreCase = true)) {
            agRatio = toDoubleValue(labData[agRatioKey] as? String)
        } else {
            val albuminKey = findKeyContains("Albumin", labData)
            val totalProteinKey = findKeyContains("Białko całkowite", labData)
            val albuminVal = albuminKey?.let { toDoubleValue(labData[it] as? String) }
            val totalProtVal = totalProteinKey?.let { toDoubleValue(labData[it] as? String) }
            if (albuminVal != null && totalProtVal != null) {
                val globVal = totalProtVal - albuminVal
                if (globVal > 0) agRatio = albuminVal / globVal
            }
        }
        if (agRatio != null) {
            when {
                agRatio < 0.4 -> {
                    totalScore += agRatioPoints
                    breakdown.add("✅ Stosunek A/G < 0.4: <b>+$agRatioPoints pkt</b> (silnie sugestywny)")
                }
                agRatio < 0.6 -> {
                    totalScore += agRatioPoints / 2
                    breakdown.add("⚠️ Stosunek A/G < 0.6: <b>+${agRatioPoints/2} pkt</b> (umiarkowanie sugestywny)")
                }
                agRatio > 0.8 -> {
                    totalScore += pointsMap["-"]!! // Punkty ujemne
                    breakdown.add("❌ Stosunek A/G > 0.8: <b>${pointsMap["-"]!!} pkt</b> (przemawia przeciw FIP)")
                }
                else -> {
                    breakdown.add("❔ Stosunek A/G w 'szarej strefie' (0.6-0.8): <b>+0 pkt</b>")
                }
            }
        } else {
            breakdown.add("❓ Brak stosunku A/G: <b>+0 pkt</b>")
        }

        // 5. Limfopenia -> waga ++
        val lymphopeniaPoints = pointsMap["++"]!!
        maxScore += lymphopeniaPoints
        val lymphKey = findKeyContains("LYM", labData)
        if (lymphKey != null && !lymphKey.contains("%")) { // Upewnij się, że to wartość bezwzględna
            val lymphVal = toDoubleValue(labData[lymphKey] as? String)
            val lymphMin = toDoubleValue(labData["${lymphKey}RangeMin"] as? String)
            if (isValueLow(lymphVal, lymphMin)) {
                totalScore += lymphopeniaPoints
                breakdown.add("✅ Limfopenia: <b>+$lymphopeniaPoints pkt</b> (umiarkowanie sugestywna)")
            } else {
                breakdown.add("❌ Limfocyty w normie: <b>+0 pkt</b>")
            }
        }

        // 6. Neutrofilia -> waga ++
        val neutrophiliaPoints = pointsMap["++"]!!
        maxScore += neutrophiliaPoints
        val neutKey = findKeyContains("NEU", labData)
        if (neutKey != null && !neutKey.contains("%")) {
            val neutVal = toDoubleValue(labData[neutKey] as? String)
            val neutMax = toDoubleValue(labData["${neutKey}RangeMax"] as? String)
            if (isValueHigh(neutVal, neutMax)) {
                totalScore += neutrophiliaPoints
                breakdown.add("✅ Neutrofilia: <b>+$neutrophiliaPoints pkt</b> (umiarkowanie sugestywna)")
            } else {
                breakdown.add("❌ Neutrofile w normie: <b>+0 pkt</b>")
            }
        }

        // 7. Niedokrwistość (łagodna, nieregeneratywna) -> waga ++
        val anemiaPoints = pointsMap["++"]!!
        maxScore += anemiaPoints
        val hctKey = findKeyContains("HCT", labData)
        if (hctKey != null) {
            val hctVal = toDoubleValue(labData[hctKey] as? String)
            val hctMin = toDoubleValue(labData["${hctKey}RangeMin"] as? String)
            // Zakładamy, że anemia w chorobie przewlekłej jest nieregeneratywna
            if (isValueLow(hctVal, hctMin)) {
                totalScore += anemiaPoints
                breakdown.add("✅ Niedokrwistość: <b>+$anemiaPoints pkt</b> (umiarkowanie sugestywna)")
            } else {
                breakdown.add("❌ Hematokryt w normie: <b>+0 pkt</b>")
            }
        }

        // 8. Hiperbilirubinemia -> waga +++
        val hyperbiliPoints = pointsMap["+++"]!!
        maxScore += hyperbiliPoints
        val biliKey = findKeyContains("Bilirubina", labData)
        if (biliKey != null) {
            val biliVal = toDoubleValue(labData[biliKey] as? String)
            val biliMax = toDoubleValue(labData["${biliKey}RangeMax"] as? String)
            if (isValueHigh(biliVal, biliMax)) {
                totalScore += hyperbiliPoints
                breakdown.add("✅ Hiperbilirubinemia: <b>+$hyperbiliPoints pkt</b> (silnie sugestywna)")
            } else {
                breakdown.add("❌ Bilirubina w normie: <b>+0 pkt</b>")
            }
        }

        // 9. Gammapatia poliklonalna -> waga ++++
        val gammopathyPoints = pointsMap["++++"]!!
        maxScore += gammopathyPoints
        val gammopathyResult = labData["GammopathyResult"] as? String ?: "brak danych"

        Log.d("FIP_ANALYSIS", "Wynik gammapatii: $gammopathyResult")

        when {
            gammopathyResult.contains("poliklonalna", ignoreCase = true) -> {
                totalScore += gammopathyPoints
                breakdown.add("✅ Gammapatia poliklonalna: <b>+$gammopathyPoints pkt</b> (bardzo sugestywna dla FIP)")
            }
            gammopathyResult.contains("monoklonalna", ignoreCase = true) -> {
                // Gammapatia monoklonalna jest mniej typowa dla FIP
                totalScore += gammopathyPoints / 2
                breakdown.add("⚠️ Gammapatia monoklonalna: <b>+${gammopathyPoints/2} pkt</b> (może występować przy FIP, ale mniej typowa)")
            }
            else -> {
                breakdown.add("❌ Brak gammapatii: <b>+0 pkt</b>")
            }
        }


        // Finalne obliczenia - wystarczy 2/3 objawów, żeby uznać FIP za pewny -
        // - nigdy nie ma całkiem wszystkich objawów
        var riskPercentage = if (maxScore > 0) ((totalScore.coerceIn(0, maxScore) * 150) / maxScore) else 0
        if (riskPercentage > 100) riskPercentage = 100;

        val fipRiskComment = when {
            riskPercentage >= 75 -> "<b><font color='#D32F2F'>BARDZO WYSOKIE RYZYKO FIP (${riskPercentage}%)</font></b>. Wyniki silnie wskazują na zakaźne zapalenie otrzewnej. Należy pilnie skonsultować się z lekarzem weterynarii w celu potwierdzenia diagnozy i wdrożenia leczenia."
            riskPercentage >= 50 -> "<b><font color='#FFA000'>WYSOKIE RYZYKO FIP (${riskPercentage}%)</font></b>. Istnieje duże prawdopodobieństwo FIP. Wymagana dalsza diagnostyka."
            riskPercentage >= 25 -> "<b><font color='#FBC02D'>ŚREDNIE RYZYKO FIP (${riskPercentage}%)</font></b>. FIP jest jedną z możliwych diagnoz. Należy rozważyć diagnostykę różnicową dla innych chorób zapalnych."
            else -> "<b><font color='#388E3C'>NISKIE RYZYKO FIP (${riskPercentage}%)</font></b>. Na podstawie przedstawionych wyników FIP jest mało prawdopodobny, ale nie można go w 100% wykluczyć."
        }

        val furtherTests = "USG jamy brzusznej (ocena węzłów chłonnych krezkowych, nerek, wątroby), badanie płynu z jam ciała (jeśli obecny), RT-PCR w kierunku FCoV z płynu lub materiału z biopsji."
        val supplements = "W zależności od stanu klinicznego: witaminy z grupy B, preparaty wspomagające odporność (np. beta-glukan), suplementy wspierające wątrobę (jeśli ALT/bilirubina podniesione)."
        val consultation = "Pilna konsultacja z lekarzem weterynarii jest wskazana przy ryzyku średnim i wyższym. Rozważ konsultację u specjalisty chorób wewnętrznych lub chorób zakaźnych."


        return FipRiskResult(
            riskPercentage = riskPercentage,
            fipRiskComment = fipRiskComment,
            scoreBreakdown = breakdown,
            furtherTestsAdvice = furtherTests,
            supplementAdvice = supplements,
            vetConsultationAdvice = consultation
        )
    }
}