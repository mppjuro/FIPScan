package com.example.fipscan

import android.content.Context
import java.util.Locale

object LabResultAnalyzer {
    data class AnalysisResult(
        val diagnosticComment: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String
    )

    fun analyzeLabData(labData: Map<String, Any>, context: Context): AnalysisResult {
        // Funkcje pomocnicze
        fun toDoubleValue(str: String?): Double? {
            if (str == null) return null
            return try {
                str.replace(Regex("[<>]"), "").replace(",", ".").trim().toDoubleOrNull()
            } catch (_: Exception) {
                null
            }
        }

        fun findKeyContains(name: String): String? {
            return labData.keys.find { it.contains(name, ignoreCase = true) }
        }

        val diagnosticComments = mutableListOf<String>()
        val supplementRecommendations = mutableListOf<String>()
        var vetConsultationNeeded = false
        var specialistTypeResId: Int? = null

        // 1. Stosunek A/G
        var agRatio: Double? = null
        val agRatioKey = findKeyContains("Stosunek")
        if (agRatioKey != null && agRatioKey.contains("albumin", ignoreCase = true)) {
            agRatio = toDoubleValue(labData[agRatioKey] as? String)
        }

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

        // Ta sekcja była już poprawna - formatowała liczbę do stringa
        if (agRatio != null) {
            val formattedRatio = String.format(Locale.getDefault(), "%.2f", agRatio)
            if (agRatio < 0.6) {
                diagnosticComments.add(context.getString(R.string.lab_ag_ratio_low, formattedRatio))
            } else if (agRatio < 0.8) {
                diagnosticComments.add(context.getString(R.string.lab_ag_ratio_moderate, formattedRatio))
            }
        }

        // 2. Hipergammaglobulinemia
        val gammaKey = findKeyContains("Gamma")
        val gammaVal = toDoubleValue(labData[gammaKey] as? String)
        val gammaMax = toDoubleValue(labData["${gammaKey}RangeMax"] as? String)
        if (gammaVal != null && gammaMax != null && gammaVal > gammaMax) {
            diagnosticComments.add(context.getString(R.string.lab_hypergammaglobulinemia))
        }

        // 3. Pojedyncze odchylenia
        // ALT
        val altKey = findKeyContains("ALT") ?: findKeyContains("AlAT")
        if (altKey != null) {
            val altVal = toDoubleValue(labData[altKey] as? String)
            val altMax = toDoubleValue(labData["${altKey}RangeMax"] as? String)
            val unit = labData["${altKey}Unit"] as? String ?: ""
            if (altVal != null && altMax != null && altVal > altMax) {
                val fold = if (altMax > 0) altVal / altMax else Double.POSITIVE_INFINITY

                // --- POPRAWKA TUTAJ ---
                // Konwertujemy liczbę (Double) na String przed przekazaniem do getString()
                val altStr = String.format(Locale.getDefault(), "%.1f", altVal)

                if (fold <= 2) {
                    diagnosticComments.add(context.getString(R.string.lab_alt_mild, altStr, unit))
                    supplementRecommendations.add(context.getString(R.string.supp_hepatiale_forte))
                } else {
                    diagnosticComments.add(context.getString(R.string.lab_alt_severe, altStr, unit))
                    supplementRecommendations.add(context.getString(R.string.supp_hepatiale_forte_advanced))
                    vetConsultationNeeded = true
                    specialistTypeResId = R.string.specialist_hepatologist
                }
            }
        }

        // Bilirubina
        val biliKey = findKeyContains("Bilirubina") ?: findKeyContains("Bilirubin")
        if (biliKey != null) {
            val biliVal = toDoubleValue(labData[biliKey] as? String)
            val biliMax = toDoubleValue(labData["${biliKey}RangeMax"] as? String)
            val unit = labData["${biliKey}Unit"] as? String ?: ""
            if (biliVal != null && biliMax != null && biliVal > biliMax) {

                // --- POPRAWKA TUTAJ ---
                val biliStr = String.format(Locale.getDefault(), "%.1f", biliVal)
                diagnosticComments.add(context.getString(R.string.lab_bilirubin_high, biliStr, unit))

                if (biliVal / biliMax > 2) {
                    vetConsultationNeeded = true
                    if (specialistTypeResId == null) specialistTypeResId = R.string.specialist_internist
                }
            }
        }

        // WBC
        val wbcKey = findKeyContains("Leukocyty") ?: findKeyContains("WBC")
        if (wbcKey != null) {
            val wbcVal = toDoubleValue(labData[wbcKey] as? String)
            val wbcMax = toDoubleValue(labData["${wbcKey}RangeMax"] as? String)
            val wbcMin = toDoubleValue(labData["${wbcKey}RangeMin"] as? String)

            // --- POPRAWKA TUTAJ ---
            // Konwertujemy liczbę (Double) na String
            // Zasoby stringów dla WBC oczekują JEDNEGO argumentu typu String
            if (wbcVal != null) {
                val wbcStr = String.format(Locale.getDefault(), "%.1f", wbcVal)
                if (wbcMax != null && wbcVal > wbcMax) {
                    diagnosticComments.add(context.getString(R.string.lab_leukocytosis, wbcStr))
                } else if (wbcMin != null && wbcVal < wbcMin) {
                    diagnosticComments.add(context.getString(R.string.lab_leukopenia, wbcStr))
                }
            }
        }

        // Neutrofile i limfocyty (bez zmian, ten string nie przyjmuje argumentów)
        val neutKey = findKeyContains("Neutro")
        val lymphKey = findKeyContains("Limfocy")
        if (neutKey != null && lymphKey != null) {
            val neutVal = toDoubleValue(labData[neutKey] as? String)
            val neutMax = toDoubleValue(labData["${neutKey}RangeMax"] as? String)
            val lymphVal = toDoubleValue(labData[lymphKey] as? String)
            val lymphMin = toDoubleValue(labData["${lymphKey}RangeMin"] as? String)

            if (neutVal != null && neutMax != null && neutVal > neutMax &&
                lymphVal != null && lymphMin != null && lymphVal < lymphMin) {
                diagnosticComments.add(context.getString(R.string.lab_stress_leukogram))
            }
        }

        // HCT
        val hctKey = findKeyContains("Hematokryt") ?: findKeyContains("HCT")
        if (hctKey != null) {
            val hctVal = toDoubleValue(labData[hctKey] as? String)
            val hctMin = toDoubleValue(labData["${hctKey}RangeMin"] as? String)
            if (hctVal != null && hctMin != null && hctVal < hctMin) {

                // --- POPRAWKA TUTAJ ---
                // Konwertujemy liczbę (Double) na String
                val hctStr = String.format(Locale.getDefault(), "%.1f", hctVal)
                diagnosticComments.add(context.getString(R.string.lab_anemia, hctStr))
            }
        }

        // 4. FCoV ELISA (bez zmian, ta sekcja była już poprawna - przekazywała Stringa 'fcovValue')
        val fcovKey = labData.keys.find { it.contains("FCoV", ignoreCase = true) && it.contains("ELISA", ignoreCase = true) }
        if (fcovKey != null) {
            val fcovValue = labData[fcovKey] as? String
            // Sprawdzamy też wartość referencyjną, bo czasem tam jest opis wyniku
            val fcovResultText = (labData["${fcovKey}RangeMax"] as? String ?: "") + (fcovValue ?: "")

            if (!fcovValue.isNullOrBlank()) {
                val resultLower = fcovResultText.lowercase(Locale.getDefault())
                val isPositive = resultLower.contains("dodatni") || resultLower.contains("pozytywny") || resultLower.contains("positive")
                val isNegative = resultLower.contains("ujemny") || resultLower.contains("negatywny") || resultLower.contains("negative")

                when {
                    isPositive -> {
                        diagnosticComments.add(context.getString(R.string.lab_fcov_positive))
                    }
                    isNegative -> {
                        diagnosticComments.add(context.getString(R.string.lab_fcov_negative))
                    }
                    fcovValue.contains(":") -> {
                        val titerValue = try {
                            fcovValue.split(":").last().replace(",", ".").trim().toDoubleOrNull()
                        } catch (_: Exception) { null }

                        if (titerValue != null) {
                            if (titerValue >= 400) {
                                diagnosticComments.add(context.getString(R.string.lab_fcov_titer_high, fcovValue))
                            } else if (titerValue >= 100) {
                                diagnosticComments.add(context.getString(R.string.lab_fcov_titer_moderate, fcovValue))
                            } else {
                                diagnosticComments.add(context.getString(R.string.lab_fcov_titer_low, fcovValue))
                            }
                        } else {
                            diagnosticComments.add(context.getString(R.string.lab_fcov_result, fcovValue))
                        }
                    }
                    else -> {
                        diagnosticComments.add(context.getString(R.string.lab_fcov_result, fcovValue))
                    }
                }
            }
        }

        // 5. Podsumowanie
        if (diagnosticComments.isEmpty()) {
            diagnosticComments.add(context.getString(R.string.lab_results_normal))
        } else {
            // Sprawdzamy, czy któryś komentarz zawiera "FIP" (w dowolnej wielkości liter)
            val fipMentioned = diagnosticComments.any { it.contains("FIP", ignoreCase = true) }
            if (fipMentioned) {
                diagnosticComments.add(context.getString(R.string.lab_disclaimer_other_diseases))
            }
        }

        val vetAdvice = if (vetConsultationNeeded) {
            if (specialistTypeResId != null) {
                val specialist = context.getString(specialistTypeResId)
                context.getString(R.string.lab_consult_specialist, specialist)
            } else {
                context.getString(R.string.lab_consult_general)
            }
        } else {
            context.getString(R.string.lab_consult_none)
        }

        val commentText = diagnosticComments.joinToString(" ")
        val supplementText = if (supplementRecommendations.isNotEmpty())
            supplementRecommendations.joinToString("; ")
        else
            context.getString(R.string.lab_supplements_none)

        return AnalysisResult(commentText, supplementText, vetAdvice)
    }
}