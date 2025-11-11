package com.example.fipscan

import android.content.Context
import android.util.Log
import java.util.regex.Pattern

object ElectrophoresisAnalyzer {
    const val SHAPE_ANALYSIS_MAX_POINTS = 20
    const val PATTERN_ANALYSIS_MAX_POINTS = 20

    data class FipRiskResult(
        val riskPercentage: Int,
        val fipRiskComment: String,
        val scoreBreakdown: List<String>,
        val furtherTestsAdvice: String,
        val supplementAdvice: String,
        val vetConsultationAdvice: String,
        val shapeAnalysisPoints: Int = 0,
        val patternAnalysisPoints: Int = 0,
        val maxShapePoints: Int = SHAPE_ANALYSIS_MAX_POINTS,
        val maxPatternPoints: Int = PATTERN_ANALYSIS_MAX_POINTS
    )

    // --- Funkcje pomocnicze ---
    private fun toDoubleValue(str: String?): Double? {
        if (str == null) return null
        try {
            val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
            return cleaned.toDoubleOrNull()
        } catch (_: Exception) {
            return null
        }
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
        // Zakładamy, że PDF jest po polsku, więc zostawiamy polskie regexy do parsowania wieku z tekstu
        val pattern = Pattern.compile("(\\d+)\\s*(lat|lata|rok)")
        val matcher = pattern.matcher(ageString)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else {
            if (ageString.contains("miesiąc", ignoreCase = true) || ageString.contains("miesiące", ignoreCase = true)) 0 else null
        }
    }

    fun assessFipRisk(
        labData: Map<String, Any>,
        rivaltaStatus: String,
        context: Context,
        shapeAnalysisScore: Float? = null,
        patternAnalysisScore: Float? = null
    ): FipRiskResult {
        var totalScore = 0
        var maxScore = 0
        val breakdown = mutableListOf<String>()

        val pointsMap = mapOf(
            "++++" to 40, "+++" to 30, "++" to 20, "+" to 10,
            "-" to -10, "--" to -20
        )

        // 1. Wiek
        val agePoints = pointsMap["++"]!!
        maxScore += agePoints
        val ageInYears = parseAgeInYears(labData["Wiek"] as? String)
        if (ageInYears != null && ageInYears < 2) {
            totalScore += agePoints
            breakdown.add(context.getString(R.string.breakdown_age_young, agePoints))
        } else {
            breakdown.add(context.getString(R.string.breakdown_age_old))
        }

        // 2. Próba Rivalta
        val rivaltaPoints = pointsMap["++++"]!!
        maxScore += rivaltaPoints

        // Pobierz opcje z zasobów, aby porównać z aktualnym językiem
        val rivaltaOpts = context.resources.getStringArray(R.array.rivalta_options)
        // Zakładamy kolejność w arrays.xml: 0 -> nie wykonano, 1 -> negatywna, 2 -> pozytywna

        val isPositive = rivaltaStatus.equals(rivaltaOpts.getOrNull(2), ignoreCase = true) || rivaltaStatus.contains("pozytywna", true) || rivaltaStatus.contains("positive", true)
        val isNegative = rivaltaStatus.equals(rivaltaOpts.getOrNull(1), ignoreCase = true) || rivaltaStatus.contains("negatywna", true) || rivaltaStatus.contains("negative", true)

        when {
            isPositive -> {
                totalScore += rivaltaPoints
                breakdown.add(context.getString(R.string.breakdown_rivalta_pos, rivaltaPoints))
            }
            isNegative -> {
                breakdown.add(context.getString(R.string.breakdown_rivalta_neg))
            }
            else -> {
                totalScore += rivaltaPoints / 2
                breakdown.add(context.getString(R.string.breakdown_rivalta_unknown, rivaltaPoints / 2))
            }
        }

        // 3. Hiperglobulinemia
        val hyperglobPoints = pointsMap["+++"]!!
        maxScore += hyperglobPoints
        val globulinKey = findKeyContains("Globulin", labData)
        if (globulinKey != null) {
            val globVal = toDoubleValue(labData[globulinKey] as? String)
            val globMax = toDoubleValue(labData["${globulinKey}RangeMax"] as? String)
            if (isValueHigh(globVal, globMax)) {
                totalScore += hyperglobPoints
                breakdown.add(context.getString(R.string.breakdown_globulin_high, hyperglobPoints))
            } else {
                breakdown.add(context.getString(R.string.breakdown_globulin_normal))
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_globulin_missing))
        }

        // 4. Stosunek A/G
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
                    breakdown.add(context.getString(R.string.breakdown_ag_very_low, agRatioPoints))
                }
                agRatio < 0.6 -> {
                    totalScore += agRatioPoints / 2
                    breakdown.add(context.getString(R.string.breakdown_ag_low, agRatioPoints / 2))
                }
                agRatio > 0.8 -> {
                    val negativePoints = pointsMap["-"]!!
                    totalScore += negativePoints
                    breakdown.add(context.getString(R.string.breakdown_ag_high, negativePoints))
                }
                else -> {
                    breakdown.add(context.getString(R.string.breakdown_ag_grey))
                }
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_ag_missing))
        }

        // 5. Limfopenia
        val lymphopeniaPoints = pointsMap["++"]!!
        maxScore += lymphopeniaPoints
        val lymphKey = findKeyContains("LYM", labData)
        if (lymphKey != null && !lymphKey.contains("%")) {
            val lymphVal = toDoubleValue(labData[lymphKey] as? String)
            val lymphMin = toDoubleValue(labData["${lymphKey}RangeMin"] as? String)
            if (isValueLow(lymphVal, lymphMin)) {
                totalScore += lymphopeniaPoints
                breakdown.add(context.getString(R.string.breakdown_lym_low, lymphopeniaPoints))
            } else {
                breakdown.add(context.getString(R.string.breakdown_lym_normal))
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_lym_missing))
        }

        // 6. Neutrofilia
        val neutrophiliaPoints = pointsMap["++"]!!
        maxScore += neutrophiliaPoints
        val neutKey = findKeyContains("NEU", labData)
        if (neutKey != null && !neutKey.contains("%")) {
            val neutVal = toDoubleValue(labData[neutKey] as? String)
            val neutMax = toDoubleValue(labData["${neutKey}RangeMax"] as? String)
            if (isValueHigh(neutVal, neutMax)) {
                totalScore += neutrophiliaPoints
                breakdown.add(context.getString(R.string.breakdown_neu_high, neutrophiliaPoints))
            } else {
                breakdown.add(context.getString(R.string.breakdown_neu_normal))
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_neu_missing))
        }

        // 7. Niedokrwistość
        val anemiaPoints = pointsMap["++"]!!
        maxScore += anemiaPoints
        val hctKey = findKeyContains("HCT", labData)
        if (hctKey != null) {
            val hctVal = toDoubleValue(labData[hctKey] as? String)
            val hctMin = toDoubleValue(labData["${hctKey}RangeMin"] as? String)
            if (isValueLow(hctVal, hctMin)) {
                totalScore += anemiaPoints
                breakdown.add(context.getString(R.string.breakdown_anemia, anemiaPoints))
            } else {
                breakdown.add(context.getString(R.string.breakdown_hct_normal))
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_hct_missing))
        }

        // 8. Hiperbilirubinemia
        val hyperbiliPoints = pointsMap["+++"]!!
        maxScore += hyperbiliPoints
        val biliKey = findKeyContains("Bilirubina", labData)
        if (biliKey != null) {
            val biliVal = toDoubleValue(labData[biliKey] as? String)
            val biliMax = toDoubleValue(labData["${biliKey}RangeMax"] as? String)
            if (isValueHigh(biliVal, biliMax)) {
                totalScore += hyperbiliPoints
                breakdown.add(context.getString(R.string.breakdown_bili_high, hyperbiliPoints))
            } else {
                breakdown.add(context.getString(R.string.breakdown_bili_normal))
            }
        } else {
            breakdown.add(context.getString(R.string.breakdown_bili_missing))
        }

        // 9. Gammapatia
        val gammopathyPoints = pointsMap["++++"]!!
        maxScore += gammopathyPoints
        val gammopathyResult = labData["GammopathyResult"] as? String ?: ""

        Log.d("FIP_ANALYSIS", "Gammapathy internal result: $gammopathyResult")

        // ZMIANA: Używamy 'when' bez argumentu i sprawdzamy 'contains',
        // aby "monoclonal gammopathy" zostało poprawnie wykryte jako RESULT_MONOCLONAL ("monoclonal")
        when {
            gammopathyResult.contains(BarChartLevelAnalyzer.RESULT_POLYCLONAL, ignoreCase = true) -> {
                totalScore += gammopathyPoints
                breakdown.add(context.getString(R.string.breakdown_gamma_poly, gammopathyPoints))
            }
            gammopathyResult.contains(BarChartLevelAnalyzer.RESULT_MONOCLONAL, ignoreCase = true) -> {
                totalScore += gammopathyPoints / 2
                breakdown.add(context.getString(R.string.breakdown_gamma_mono, gammopathyPoints / 2))
            }
            else -> {
                breakdown.add(context.getString(R.string.breakdown_gamma_none))
            }
        }

        // Shape Analysis
        var shapePoints = 0
        if (shapeAnalysisScore != null) {
            shapePoints = ((shapeAnalysisScore / 100f) * SHAPE_ANALYSIS_MAX_POINTS).toInt()
            totalScore += shapePoints
            maxScore += SHAPE_ANALYSIS_MAX_POINTS

            val shapeLevel = when {
                shapePoints >= 25 -> context.getString(R.string.level_very_characteristic)
                shapePoints >= 20 -> context.getString(R.string.level_characteristic)
                shapePoints >= 15 -> context.getString(R.string.level_moderately_suggestive)
                shapePoints >= 10 -> context.getString(R.string.level_weakly_suggestive)
                else -> context.getString(R.string.level_uncharacteristic)
            }
            breakdown.add(context.getString(R.string.breakdown_shape_analysis, shapeLevel, shapePoints, SHAPE_ANALYSIS_MAX_POINTS))
        }

        // Pattern Analysis
        var patternPoints = 0
        if (patternAnalysisScore != null) {
            patternPoints = ((patternAnalysisScore / 100f) * PATTERN_ANALYSIS_MAX_POINTS).toInt()
            totalScore += patternPoints
            maxScore += PATTERN_ANALYSIS_MAX_POINTS

            val patternLevel = when {
                patternPoints >= 25 -> context.getString(R.string.level_very_typical)
                patternPoints >= 20 -> context.getString(R.string.level_typical)
                patternPoints >= 15 -> context.getString(R.string.level_partially_typical)
                patternPoints >= 10 -> context.getString(R.string.level_weakly_typical)
                else -> context.getString(R.string.level_atypical)
            }
            breakdown.add(context.getString(R.string.breakdown_pattern_analysis, patternLevel, patternPoints, PATTERN_ANALYSIS_MAX_POINTS))
        }

        var riskPercentage = if (maxScore > 0) ((totalScore.coerceIn(0, maxScore) * 150) / maxScore) else 0
        if (riskPercentage > 100) riskPercentage = 100

        val fipRiskComment = when {
            riskPercentage >= 75 -> context.getString(R.string.risk_comment_very_high, riskPercentage)
            riskPercentage >= 50 -> context.getString(R.string.risk_comment_high, riskPercentage)
            riskPercentage >= 25 -> context.getString(R.string.risk_comment_medium, riskPercentage)
            else -> context.getString(R.string.risk_comment_low, riskPercentage)
        }

        return FipRiskResult(
            riskPercentage = riskPercentage,
            fipRiskComment = fipRiskComment,
            scoreBreakdown = breakdown,
            furtherTestsAdvice = context.getString(R.string.advice_further_tests),
            supplementAdvice = context.getString(R.string.advice_supplements),
            vetConsultationAdvice = context.getString(R.string.advice_consultation),
            shapeAnalysisPoints = shapePoints,
            patternAnalysisPoints = patternPoints
        )
    }
}