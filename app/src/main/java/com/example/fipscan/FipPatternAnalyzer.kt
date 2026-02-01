package com.example.fipscan

import android.content.Context
import java.util.Locale

object FipPatternAnalyzer {

    enum class FipProfile {
        INFLAMMATORY_ACUTE,
        INFLAMMATORY_CHRONIC,
        EFFUSIVE_CLASSIC,
        DRY_NEUROLOGICAL,
        MIXED_PATTERN,
        ATYPICAL,
        NON_FIP
    }

    data class PatternAnalysisResult(
        val primaryProfile: FipProfile,
        val secondaryProfile: FipProfile?,
        val patternStrength: Float,
        val keyFindings: List<String>,
        val profileDescription: String,
        val managementSuggestions: String
    )

    data class ParameterPattern(
        val name: String,
        val present: Boolean,
        val severity: String
    )

    fun analyzeParameterPatterns(labData: Map<String, Any>, context: Context): PatternAnalysisResult {

        val patterns = detectBasicPatterns(labData)
        val combinations = identifyCombinations(patterns)
        val (primaryProfile, secondaryProfile) = determineProfiles(combinations)
        val strength = calculatePatternStrength(combinations, primaryProfile)
        val findings = generateKeyFindings(patterns, combinations, context)
        val description = generateProfileDescription(primaryProfile, secondaryProfile, patterns, combinations, context)
        val suggestions = generateManagementSuggestions(primaryProfile, strength, context)

        return PatternAnalysisResult(
            primaryProfile = primaryProfile,
            secondaryProfile = secondaryProfile,
            patternStrength = strength,
            keyFindings = findings,
            profileDescription = description,
            managementSuggestions = suggestions
        )
    }

    private fun detectBasicPatterns(labData: Map<String, Any>): Map<String, ParameterPattern> {
        val patterns = mutableMapOf<String, ParameterPattern>()
        analyzeGlobulinemia(labData)?.let { patterns["hyperglobulinemia"] = it }
        analyzeAlbuminemia(labData)?.let { patterns["hypoalbuminemia"] = it }
        analyzeLymphopenia(labData)?.let { patterns["lymphopenia"] = it }
        analyzeNeutrophilia(labData)?.let { patterns["neutrophilia"] = it }
        analyzeAnemia(labData)?.let { patterns["anemia"] = it }
        analyzeBilirubinemia(labData)?.let { patterns["hyperbilirubinemia"] = it }
        analyzeLiverEnzymes(labData)?.let { patterns["liver_enzymes"] = it }
        analyzeAzotemia(labData)?.let { patterns["azotemia"] = it }
        analyzeThrombocytopenia(labData)?.let { patterns["thrombocytopenia"] = it }

        return patterns
    }

    private fun analyzeGlobulinemia(labData: Map<String, Any>): ParameterPattern? {
        val globKey = labData.keys.find { it.contains("Globulin", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[globKey] as? String) ?: return null
        val max = toDoubleValue(labData["${globKey}RangeMax"] as? String) ?: return null
        if (value <= max) return null
        val severity = when {
            value > max * 2.0 -> "severe"
            value > max * 1.5 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("hyperglobulinemia", true, severity)
    }

    private fun analyzeAlbuminemia(labData: Map<String, Any>): ParameterPattern? {
        val albKey = labData.keys.find { it.contains("Albumin", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[albKey] as? String) ?: return null
        val min = toDoubleValue(labData["${albKey}RangeMin"] as? String) ?: return null
        if (value >= min) return null
        val severity = when {
            value < min * 0.5 -> "severe"
            value < min * 0.75 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("hypoalbuminemia", true, severity)
    }

    private fun analyzeLymphopenia(labData: Map<String, Any>): ParameterPattern? {
        val lymphKey = labData.keys.find { it.contains("LYM", ignoreCase = true) && !it.contains("%") } ?: return null
        val value = toDoubleValue(labData[lymphKey] as? String) ?: return null
        val min = toDoubleValue(labData["${lymphKey}RangeMin"] as? String) ?: return null
        if (value >= min) return null
        val severity = when {
            value < min * 0.3 -> "severe"
            value < min * 0.6 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("lymphopenia", true, severity)
    }

    private fun analyzeNeutrophilia(labData: Map<String, Any>): ParameterPattern? {
        val neutKey = labData.keys.find { it.contains("NEU", ignoreCase = true) && !it.contains("%") } ?: return null
        val value = toDoubleValue(labData[neutKey] as? String) ?: return null
        val max = toDoubleValue(labData["${neutKey}RangeMax"] as? String) ?: return null
        if (value <= max) return null
        val severity = when {
            value > max * 1.5 -> "severe"
            value > max * 1.25 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("neutrophilia", true, severity)
    }

    private fun analyzeAnemia(labData: Map<String, Any>): ParameterPattern? {
        val hctKey = labData.keys.find { it.contains("HCT", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[hctKey] as? String) ?: return null
        val min = toDoubleValue(labData["${hctKey}RangeMin"] as? String) ?: return null
        if (value >= min) return null
        val severity = when {
            value < 0.2 -> "severe"
            value < 0.25 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("anemia", true, severity)
    }

    private fun analyzeBilirubinemia(labData: Map<String, Any>): ParameterPattern? {
        val biliKey = labData.keys.find { it.contains("Bilirubina", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[biliKey] as? String) ?: return null
        val max = toDoubleValue(labData["${biliKey}RangeMax"] as? String) ?: return null
        if (value <= max) return null
        val severity = when {
            value > max * 3 -> "severe"
            value > max * 2 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("hyperbilirubinemia", true, severity)
    }

    private fun analyzeLiverEnzymes(labData: Map<String, Any>): ParameterPattern? {
        val altKey = labData.keys.find { it.contains("ALT", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[altKey] as? String) ?: return null
        val max = toDoubleValue(labData["${altKey}RangeMax"] as? String) ?: return null
        if (value <= max) return null
        val severity = when {
            value > max * 3 -> "severe"
            value > max * 2 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("liver_enzymes", true, severity)
    }

    private fun analyzeAzotemia(labData: Map<String, Any>): ParameterPattern? {
        val ureaKey = labData.keys.find { it.contains("Mocznik", ignoreCase = true) }
        val creaKey = labData.keys.find { it.contains("Kreatynina", ignoreCase = true) }
        var azotemia = false
        var severity = "mild"

        if (ureaKey != null) {
            val value = toDoubleValue(labData[ureaKey] as? String) ?: 0.0
            val max = toDoubleValue(labData["${ureaKey}RangeMax"] as? String) ?: 999.0
            if (value > max) {
                azotemia = true
                if (value > max * 2) severity = "severe"
                else if (value > max * 1.5) severity = "moderate"
            }
        }
        if (creaKey != null) {
            val value = toDoubleValue(labData[creaKey] as? String) ?: 0.0
            val max = toDoubleValue(labData["${creaKey}RangeMax"] as? String) ?: 999.0
            if (value > max) {
                azotemia = true
                if (value > max * 2 && severity != "severe") severity = "severe"
                else if (value > max * 1.5 && severity == "mild") severity = "moderate"
            }
        }
        return if (azotemia) ParameterPattern("azotemia", true, severity) else null
    }

    private fun analyzeThrombocytopenia(labData: Map<String, Any>): ParameterPattern? {
        val pltKey = labData.keys.find { it.contains("PLT", ignoreCase = true) } ?: return null
        val value = toDoubleValue(labData[pltKey] as? String) ?: return null
        val min = toDoubleValue(labData["${pltKey}RangeMin"] as? String) ?: return null
        if (value >= min) return null
        val severity = when {
            value < 50 -> "severe"
            value < 100 -> "moderate"
            else -> "mild"
        }
        return ParameterPattern("thrombocytopenia", true, severity)
    }

    private fun identifyCombinations(patterns: Map<String, ParameterPattern>): Map<String, Boolean> {
        val combinations = mutableMapOf<String, Boolean>()
        combinations["classic_triad"] = patterns.containsKey("hyperglobulinemia") &&
                patterns.containsKey("hypoalbuminemia") &&
                patterns.containsKey("lymphopenia")
        combinations["inflammatory"] = patterns.containsKey("hyperglobulinemia") &&
                patterns.containsKey("neutrophilia")
        combinations["wasting"] = patterns.containsKey("hypoalbuminemia") &&
                patterns.containsKey("anemia") &&
                (patterns["hypoalbuminemia"]?.severity == "severe" ||
                        patterns["anemia"]?.severity == "severe")
        combinations["hepatic"] = patterns.containsKey("hyperbilirubinemia") ||
                patterns.containsKey("liver_enzymes")
        combinations["renal"] = patterns.containsKey("azotemia")
        combinations["hematologic"] = (patterns.containsKey("anemia") ||
                patterns.containsKey("thrombocytopenia")) &&
                patterns.containsKey("lymphopenia")
        combinations["stress_leukogram"] = patterns.containsKey("neutrophilia") &&
                patterns.containsKey("lymphopenia")
        return combinations
    }

    private fun determineProfiles(
        combinations: Map<String, Boolean>
    ): Pair<FipProfile, FipProfile?> {
        val profiles = mutableListOf<Pair<FipProfile, Float>>()
        if (combinations["classic_triad"] == true && combinations["inflammatory"] == true) {
            profiles.add(FipProfile.INFLAMMATORY_ACUTE to 85f)
        }
        if (combinations["classic_triad"] == true && combinations["wasting"] == true) {
            profiles.add(FipProfile.INFLAMMATORY_CHRONIC to 80f)
        }
        if (combinations["classic_triad"] == true && combinations["hepatic"] == true) {
            profiles.add(FipProfile.EFFUSIVE_CLASSIC to 90f)
        }
        if (combinations["inflammatory"] == true && combinations["hepatic"] != true &&
            combinations["stress_leukogram"] == true) {
            profiles.add(FipProfile.DRY_NEUROLOGICAL to 70f)
        }
        if (combinations.values.count { it } >= 4) {
            profiles.add(FipProfile.MIXED_PATTERN to 75f)
        }
        if (combinations["classic_triad"] == false &&
            combinations.values.count { it } >= 2) {
            profiles.add(FipProfile.ATYPICAL to 60f)
        }
        if (combinations.values.count { it } < 2) {
            profiles.add(FipProfile.NON_FIP to 90f)
        }
        profiles.sortByDescending { it.second }
        val primary = profiles.firstOrNull()?.first ?: FipProfile.NON_FIP
        val secondary = if (profiles.size > 1 && profiles[1].second > 50f) {
            profiles[1].first
        } else null
        return Pair(primary, secondary)
    }

    private fun calculatePatternStrength(
        combinations: Map<String, Boolean>,
        profile: FipProfile
    ): Float {
        val baseScore = when (profile) {
            FipProfile.INFLAMMATORY_ACUTE -> 80f
            FipProfile.INFLAMMATORY_CHRONIC -> 75f
            FipProfile.EFFUSIVE_CLASSIC -> 85f
            FipProfile.DRY_NEUROLOGICAL -> 70f
            FipProfile.MIXED_PATTERN -> 65f
            FipProfile.ATYPICAL -> 50f
            FipProfile.NON_FIP -> 20f
        }
        var modifier = 0f
        if (combinations["classic_triad"] == true) modifier += 15f
        if (combinations["stress_leukogram"] == true) modifier += 10f
        if (combinations["inflammatory"] == true) modifier += 5f
        if (combinations["classic_triad"] == false && profile != FipProfile.NON_FIP) {
            modifier -= 20f
        }
        val baseResult = baseScore + modifier
        val multipliedScore = baseResult * 2.75f
        return multipliedScore.coerceIn(0f, 100f)
    }

    private fun generateKeyFindings(
        patterns: Map<String, ParameterPattern>,
        combinations: Map<String, Boolean>,
        context: Context
    ): List<String> {
        val findings = mutableListOf<String>()

        if (combinations["classic_triad"] == true) {
            findings.add(context.getString(R.string.finding_classic_triad))
        }
        if (combinations["stress_leukogram"] == true) {
            findings.add(context.getString(R.string.finding_stress_leukogram))
        }

        patterns.filter { it.value.severity == "severe" }.forEach { (key, _) ->
            val resId = when (key) {
                "hyperglobulinemia" -> R.string.finding_severe_hyperglobulinemia
                "hypoalbuminemia" -> R.string.finding_severe_hypoalbuminemia
                "lymphopenia" -> R.string.finding_severe_lymphopenia
                "anemia" -> R.string.finding_severe_anemia
                else -> null
            }
            if (resId != null) {
                findings.add(context.getString(resId))
            }
        }

        if (combinations["hepatic"] == true) {
            findings.add(context.getString(R.string.finding_liver_involvement))
        }
        if (combinations["renal"] == true) {
            findings.add(context.getString(R.string.finding_renal_involvement))
        }

        return findings
    }

    private fun generateProfileDescription(
        primary: FipProfile,
        secondary: FipProfile?,
        patterns: Map<String, ParameterPattern>,
        combinations: Map<String, Boolean>,
        context: Context
    ): String {
        val descriptions = mutableListOf<String>()

        val primaryDescId = when (primary) {
            FipProfile.INFLAMMATORY_ACUTE -> R.string.desc_profile_inflammatory_acute
            FipProfile.INFLAMMATORY_CHRONIC -> R.string.desc_profile_inflammatory_chronic
            FipProfile.EFFUSIVE_CLASSIC -> R.string.desc_profile_effusive_classic
            FipProfile.DRY_NEUROLOGICAL -> R.string.desc_profile_dry_neurological
            FipProfile.MIXED_PATTERN -> R.string.desc_profile_mixed
            FipProfile.ATYPICAL -> R.string.desc_profile_atypical
            FipProfile.NON_FIP -> R.string.desc_profile_non_fip
        }
        descriptions.add(context.getString(primaryDescId))

        if (secondary != null) {
            descriptions.add(context.getString(R.string.desc_secondary_features, getProfileName(secondary, context).lowercase(Locale.getDefault())))
        }

        if (combinations["classic_triad"] == true) {
            descriptions.add(context.getString(R.string.desc_classic_triad_present))
        }

        val severeCount = patterns.values.count { it.severity == "severe" }
        if (severeCount > 0) {
            descriptions.add(context.getString(R.string.desc_severe_abnormalities_count, severeCount))
        }

        return descriptions.joinToString(". ")
    }

    private fun generateManagementSuggestions(profile: FipProfile, strength: Float, context: Context): String {
        val suggestions = mutableListOf<String>()

        when (profile) {
            FipProfile.INFLAMMATORY_ACUTE, FipProfile.EFFUSIVE_CLASSIC -> {
                suggestions.add(context.getString(R.string.sugg_urgent_consult))
                suggestions.add(context.getString(R.string.sugg_ultrasound_rivalta))
                suggestions.add(context.getString(R.string.sugg_gs_immediate))
            }
            FipProfile.INFLAMMATORY_CHRONIC -> {
                suggestions.add(context.getString(R.string.sugg_quick_consult))
                suggestions.add(context.getString(R.string.sugg_imaging))
                suggestions.add(context.getString(R.string.sugg_gs_support))
            }
            FipProfile.DRY_NEUROLOGICAL -> {
                suggestions.add(context.getString(R.string.sugg_neuro_consult))
                suggestions.add(context.getString(R.string.sugg_mri_ct))
                suggestions.add(context.getString(R.string.sugg_gs_neuro_dose))
            }
            FipProfile.MIXED_PATTERN -> {
                suggestions.add(context.getString(R.string.sugg_complex_diagnostics))
                suggestions.add(context.getString(R.string.sugg_comprehensive_imaging))
            }
            FipProfile.ATYPICAL -> {
                suggestions.add(context.getString(R.string.sugg_differential_diagnosis))
                suggestions.add(context.getString(R.string.sugg_repeat_tests))
            }
            FipProfile.NON_FIP -> {
                suggestions.add(context.getString(R.string.sugg_fip_unlikely))
                suggestions.add(context.getString(R.string.sugg_search_other_causes))
            }
        }

        if (strength >= 70 && profile != FipProfile.NON_FIP) {
            suggestions.add(context.getString(R.string.sugg_monitor))
        }

        return suggestions.joinToString("\n")
    }

    private fun getProfileName(profile: FipProfile, context: Context): String {
        return when (profile) {
            FipProfile.INFLAMMATORY_ACUTE -> context.getString(R.string.profile_name_acute)
            FipProfile.INFLAMMATORY_CHRONIC -> context.getString(R.string.profile_name_chronic)
            FipProfile.EFFUSIVE_CLASSIC -> context.getString(R.string.profile_name_effusive)
            FipProfile.DRY_NEUROLOGICAL -> context.getString(R.string.profile_name_neuro)
            FipProfile.MIXED_PATTERN -> context.getString(R.string.profile_name_mixed)
            FipProfile.ATYPICAL -> context.getString(R.string.profile_name_atypical)
            FipProfile.NON_FIP -> context.getString(R.string.profile_name_non_fip)
        }
    }

    private fun toDoubleValue(str: String?): Double? {
        if (str == null) return null
        return try {
            str.replace(Regex("[<>]"), "").replace(",", ".").trim().toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }
}