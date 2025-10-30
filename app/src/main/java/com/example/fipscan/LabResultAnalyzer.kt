package com.example.fipscan

object LabResultAnalyzer {
    data class AnalysisResult(
        val diagnosticComment: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String
    )

    fun analyzeLabData(labData: Map<String, Any>): AnalysisResult {
        // Funkcje pomocnicze do konwersji wartości tekstowych (z przecinkami, znakami "<" ">") na liczby
        fun toDoubleValue(str: String?): Double? {
            if (str == null) return null
            val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
            return cleaned.toDoubleOrNull()
        }
        fun isValueHigh(value: Double?, maxNorm: Double?): Boolean {
            return value != null && maxNorm != null && value > maxNorm
        }
        fun isValueLow(value: Double?, minNorm: Double?): Boolean {
            return value != null && minNorm != null && value < minNorm
        }
        fun findKeyContains(name: String): String? {
            return labData.keys.find { it.contains(name, ignoreCase = true) }
        }

        // Przygotowanie struktur wynikowych
        val diagnosticComments = mutableListOf<String>()
        val supplementRecommendations = mutableListOf<String>()
        var vetConsultationNeeded = false
        var specialistType: String? = null  // np. "hepatologiem" gdy wskazana konsultacja u hepatologa

        // 1. Stosunek albumina/globuliny (A/G)
        // Najpierw szukamy bezpośrednio obliczonego stosunku A/G w wynikach
        val agRatioKey = findKeyContains("Stosunek") // np. "Stosunek: albuminy / globuliny"
        var agRatio: Double? = null

        // Sprawdź czy mamy bezpośrednio stosunek A/G
        if (agRatioKey != null && agRatioKey.contains("albumin", ignoreCase = true)) {
            agRatio = toDoubleValue(labData[agRatioKey] as? String)
        }

        // Jeśli nie znaleziono bezpośredniego stosunku, oblicz go
        if (agRatio == null) {
            var albuminVal: Double? = null
            var globulinsVal: Double? = null
            val albuminKey = findKeyContains("Albumin") ?: findKeyContains("Albumina")
            val totalProteinKey = findKeyContains("Białko całkowite") ?: findKeyContains("Total Protein")
            val globulinKey = findKeyContains("Globulin")
            if (albuminKey != null) albuminVal = toDoubleValue(labData[albuminKey] as? String)
            if (globulinKey != null) {
                globulinsVal = toDoubleValue(labData[globulinKey] as? String)
            } else if (totalProteinKey != null && albuminVal != null) {
                val totalProtVal = toDoubleValue(labData[totalProteinKey] as? String)
                if (totalProtVal != null) globulinsVal = totalProtVal - albuminVal
            }
            if (albuminVal != null && globulinsVal != null && globulinsVal > 0) {
                agRatio = albuminVal / globulinsVal
            }
        }

        if (agRatio != null) {
            if (agRatio < 0.6) {
                diagnosticComments.add("Niski stosunek albumina/globuliny (A/G = ${"%.2f".format(agRatio)}) – wynik silnie podejrzany w kierunku FIP.")
            } else if (agRatio < 0.8) {
                diagnosticComments.add("Obniżony stosunek albumina/globuliny (A/G = ${"%.2f".format(agRatio)}) – może wskazywać na przewlekły stan zapalny (np. FIP), choć nie jest jednoznaczny.")
            }
        }

        // 2. Hipergammaglobulinemia (wysokie gamma-globuliny)
        val gammaKey = findKeyContains("Gamma")  // np. "Globuliny gamma"
        val alpha2Key = findKeyContains("Alfa2") ?: findKeyContains("Alpha2")
        val betaKey = findKeyContains("Beta")
        val gammaVal = toDoubleValue(labData[gammaKey] as? String)
        val gammaMax = toDoubleValue(labData["${gammaKey}RangeMax"] as? String)
        if (gammaVal != null && gammaMax != null && gammaVal > gammaMax) {
            diagnosticComments.add("Hipergammaglobulinemia (podwyższone gamma-globuliny) – charakterystyczna dla przewlekłych zapaleń, w tym FIP.")
        }

        // 3. Pojedyncze odchylenia (ALT, bilirubina, morfologia)
        // ALT (AlAT)
        val altKey = findKeyContains("ALT") ?: findKeyContains("AlAT")
        if (altKey != null) {
            val altVal = toDoubleValue(labData[altKey] as? String)
            val altMax = toDoubleValue(labData["${altKey}RangeMax"] as? String)
            if (altVal != null && altMax != null && altVal > altMax) {
                val fold = if (altMax > 0) altVal / altMax else Double.POSITIVE_INFINITY
                if (fold <= 2) {
                    diagnosticComments.add("ALT nieznacznie podwyższony (${altVal}${labData["${altKey}Unit"] ?: ""}) – łagodne uszkodzenie wątroby.")
                    supplementRecommendations.add("Hepatiale Forte – wsparcie wątroby przy niewielkim wzroście ALT.")
                } else {
                    diagnosticComments.add("ALT znacznie podwyższony (${altVal}${labData["${altKey}Unit"] ?: ""}) – wskazuje na poważniejsze uszkodzenie wątroby.")
                    supplementRecommendations.add("Hepatiale Forte **Advanced** – silniejsze wsparcie wątroby przy wysokim ALT.")
                    vetConsultationNeeded = true
                    specialistType = "hepatologiem"
                }
            }
        }
        // Bilirubina
        val biliKey = findKeyContains("Bilirubina") ?: findKeyContains("Bilirubin")
        if (biliKey != null) {
            val biliVal = toDoubleValue(labData[biliKey] as? String)
            val biliMax = toDoubleValue(labData["${biliKey}RangeMax"] as? String)
            if (biliVal != null && biliMax != null && biliVal > biliMax) {
                diagnosticComments.add("Podwyższona bilirubina (${biliVal}${labData["${biliKey}Unit"] ?: ""}) – możliwe uszkodzenie wątroby lub hemoliza (obserwowane m.in. przy FIP).")
                if (biliVal / biliMax > 2) {
                    vetConsultationNeeded = true
                    if (specialistType == null) specialistType = "internistą"
                }
            }
        }
        // Leukocyty (WBC)
        val wbcKey = findKeyContains("Leukocyty") ?: findKeyContains("WBC")
        if (wbcKey != null) {
            val wbcVal = toDoubleValue(labData[wbcKey] as? String)
            val wbcMax = toDoubleValue(labData["${wbcKey}RangeMax"] as? String)
            val wbcMin = toDoubleValue(labData["${wbcKey}RangeMin"] as? String)
            if (wbcVal != null && wbcMax != null && wbcVal > wbcMax) {
                diagnosticComments.add("Leukocytoza (WBC = $wbcVal, powyżej normy) – wskazuje na stan zapalny lub infekcję.")
            } else if (wbcVal != null && wbcMin != null && wbcVal < wbcMin) {
                diagnosticComments.add("Leukopenia (WBC = $wbcVal, poniżej normy) – może świadczyć o immunosupresji lub chorobie szpiku.")
            }
        }
        // Neutrofile i limfocyty
        val neutKey = findKeyContains("Neutro") // "Neutrofile" lub skrót w danych
        val lymphKey = findKeyContains("Limfocy") // "Limfocyty"
        if (neutKey != null && lymphKey != null) {
            val neutVal = toDoubleValue(labData[neutKey] as? String)
            val neutMax = toDoubleValue(labData["${neutKey}RangeMax"] as? String)
            val lymphVal = toDoubleValue(labData[lymphKey] as? String)
            val lymphMin = toDoubleValue(labData["${lymphKey}RangeMin"] as? String)
            if (neutVal != null && neutMax != null && neutVal > neutMax &&
                lymphVal != null && lymphMin != null && lymphVal < lymphMin) {
                diagnosticComments.add("Neutrofilia z limfopenią – podwyższone neutrofile i obniżone limfocyty (częsty obraz przy FIP).")
            }
        }
        // Hematokryt (HCT) / niedokrwistość
        val hctKey = findKeyContains("Hematokryt") ?: findKeyContains("HCT")
        if (hctKey != null) {
            val hctVal = toDoubleValue(labData[hctKey] as? String)
            val hctMin = toDoubleValue(labData["${hctKey}RangeMin"] as? String)
            if (hctVal != null && hctMin != null && hctVal < hctMin) {
                diagnosticComments.add("Obniżony hematokryt (HCT = $hctVal) – niedokrwistość, która często towarzyszy przewlekłym chorobom (np. FIP).")
            }
        }

        // 4. Wynik FCoV (ELISA)
        val fcovKey = labData.keys.find { it.contains("FCoV", ignoreCase = true) && it.contains("ELISA", ignoreCase = true) }
        if (fcovKey != null) {
            val fcovValue = labData[fcovKey] as? String  // np. "1:400" lub "Pozytywny"
            val fcovResultText = labData["${fcovKey}RangeMax"] as? String  // może zawierać opis "dodatni/ujemny"
            if (!fcovValue.isNullOrBlank()) {
                val resultText = (fcovResultText ?: fcovValue).lowercase()
                when {
                    resultText.contains("dodatni") || resultText.contains("pozytywny") -> {
                        diagnosticComments.add("Test FCoV ELISA: **dodatni** – wykryto przeciwciała przeciw koronawirusowi. To zwiększa podejrzenie FIP (choć wiele zdrowych kotów również ma pozytywny wynik).")
                    }
                    resultText.contains("ujemny") || resultText.contains("negatywny") -> {
                        diagnosticComments.add("Test FCoV ELISA: **ujemny** – nie wykryto przeciwciał przeciw FCoV. Ujemny wynik czyni FIP mało prawdopodobnym (choć nie wyklucza go całkowicie).")
                    }
                    fcovValue.contains(":") -> {
                        // Interpretacja miana przeciwciał
                        val titerValue = runCatching { fcovValue.split(":").last().replace(",", ".").toDouble() }.getOrNull()
                        if (titerValue != null) {
                            if (titerValue >= 400) {
                                diagnosticComments.add("Wysokie miano przeciwciał FCoV ($fcovValue) – tak wysoki poziom przeciwciał często występuje u kotów z FIP (należy potwierdzić FIP innymi badaniami).")
                            } else if (titerValue >= 100) {
                                diagnosticComments.add("Umiarkowane miano przeciwciał FCoV ($fcovValue) – kot jest nosicielem koronawirusa; w połączeniu z objawami to umiarkowanie nasila podejrzenie FIP.")
                            } else {
                                diagnosticComments.add("Niskie miano przeciwciał FCoV ($fcovValue) – niski poziom przeciwciał; FIP jest mniej prawdopodobny, choć w szczególnych przypadkach nadal możliwy.")
                            }
                        } else {
                            diagnosticComments.add("Wynik FCoV ELISA: $fcovValue – interpretacja zależy od kontekstu (dodatni wynik wspiera podejrzenie FIP, ujemny czyni FIP mniej prawdopodobnym).")
                        }
                    }
                }
            }
        }

        // 5. Podsumowanie i zalecenia
        if (diagnosticComments.isEmpty()) {
            diagnosticComments.add("Wyniki mieszczą się w granicach normy – brak odchyleń sugerujących FIP lub inne poważne schorzenia.")
        } else {
            if (diagnosticComments.any { it.contains("FIP", ignoreCase = true) }) {
                diagnosticComments.add("Podobne odchylenia mogą wystąpić także przy innych chorobach (np. przewlekłych infekcjach, chorobach autoimmunologicznych lub nowotworach immunologicznych).")
            }
        }
        val vetAdvice = if (vetConsultationNeeded) {
            if (specialistType != null) {
                "Zalecana szybka konsultacja z lekarzem weterynarii (specjalistą $specialistType) w celu dalszej diagnostyki i leczenia."
            } else {
                "Zalecana konsultacja z lekarzem weterynarii w celu potwierdzenia diagnozy i omówienia leczenia."
            }
        } else {
            "Brak pilnej potrzeby konsultacji – zalecana obserwacja i stosowanie się do powyższych zaleceń."
        }

        // Połączenie list w teksty
        val commentText = diagnosticComments.joinToString(" ")
        val supplementText = if (supplementRecommendations.isNotEmpty())
            supplementRecommendations.joinToString("; ") else "Brak zaleceń suplementacji."
        val consultationText = vetAdvice

        return AnalysisResult(commentText, supplementText, consultationText)
    }
}