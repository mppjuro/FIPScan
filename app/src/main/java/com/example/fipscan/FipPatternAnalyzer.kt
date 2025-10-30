package com.example.fipscan

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

    fun analyzeParameterPatterns(labData: Map<String, Any>): PatternAnalysisResult {

        // Wykryj podstawowe nieprawidłowości
        val patterns = detectBasicPatterns(labData)

        // Zidentyfikuj kombinacje wzorców
        val combinations = identifyCombinations(patterns)

        // Określ profil FIP
        val (primaryProfile, secondaryProfile) = determineProfiles(combinations, labData)

        // Oblicz siłę wzorca
        val strength = calculatePatternStrength(combinations, primaryProfile)

        // Generuj kluczowe obserwacje
        val findings = generateKeyFindings(patterns, combinations, primaryProfile)

        // Generuj opis profilu
        val description = generateProfileDescription(primaryProfile, secondaryProfile, patterns, combinations)

        // Generuj sugestie postępowania
        val suggestions = generateManagementSuggestions(primaryProfile, strength)

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

        // Hiperglobulinemia
        val globulinPattern = analyzeGlobulinemia(labData)
        if (globulinPattern != null) patterns["hyperglobulinemia"] = globulinPattern

        // Hipoalbuminemia
        val albuminPattern = analyzeAlbuminemia(labData)
        if (albuminPattern != null) patterns["hypoalbuminemia"] = albuminPattern

        // Limfopenia
        val lymphPattern = analyzeLymphopenia(labData)
        if (lymphPattern != null) patterns["lymphopenia"] = lymphPattern

        // Neutrofilia
        val neutroPattern = analyzeNeutrophilia(labData)
        if (neutroPattern != null) patterns["neutrophilia"] = neutroPattern

        // Anemia
        val anemiaPattern = analyzeAnemia(labData)
        if (anemiaPattern != null) patterns["anemia"] = anemiaPattern

        // Hiperbilirubinemia
        val biliPattern = analyzeBilirubinemia(labData)
        if (biliPattern != null) patterns["hyperbilirubinemia"] = biliPattern

        // Podwyższone enzymy wątrobowe
        val liverPattern = analyzeLiverEnzymes(labData)
        if (liverPattern != null) patterns["liver_enzymes"] = liverPattern

        // Azotemia
        val azotemiaPattern = analyzeAzotemia(labData)
        if (azotemiaPattern != null) patterns["azotemia"] = azotemiaPattern

        // Trombocytopenia
        val plateletPattern = analyzeThrombocytopenia(labData)
        if (plateletPattern != null) patterns["thrombocytopenia"] = plateletPattern

        return patterns
    }

    private fun analyzeGlobulinemia(labData: Map<String, Any>): ParameterPattern? {
        val globKey = labData.keys.find { it.contains("Globulin", ignoreCase = true) }
        if (globKey == null) return null

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
        val albKey = labData.keys.find { it.contains("Albumin", ignoreCase = true) }
        if (albKey == null) return null

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
        val lymphKey = labData.keys.find {
            it.contains("LYM", ignoreCase = true) && !it.contains("%")
        }
        if (lymphKey == null) return null

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
        val neutKey = labData.keys.find {
            it.contains("NEU", ignoreCase = true) && !it.contains("%")
        }
        if (neutKey == null) return null

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
        val hctKey = labData.keys.find { it.contains("HCT", ignoreCase = true) }
        if (hctKey == null) return null

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
        val biliKey = labData.keys.find { it.contains("Bilirubina", ignoreCase = true) }
        if (biliKey == null) return null

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
        val altKey = labData.keys.find { it.contains("ALT", ignoreCase = true) }
        if (altKey == null) return null

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
        val pltKey = labData.keys.find { it.contains("PLT", ignoreCase = true) }
        if (pltKey == null) return null

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

        // Klasyczna triada FIP
        combinations["classic_triad"] = patterns.containsKey("hyperglobulinemia") &&
                patterns.containsKey("hypoalbuminemia") &&
                patterns.containsKey("lymphopenia")

        // Profil zapalny
        combinations["inflammatory"] = patterns.containsKey("hyperglobulinemia") &&
                patterns.containsKey("neutrophilia")

        // Profil wyniszczający
        combinations["wasting"] = patterns.containsKey("hypoalbuminemia") &&
                patterns.containsKey("anemia") &&
                (patterns["hypoalbuminemia"]?.severity == "severe" ||
                        patterns["anemia"]?.severity == "severe")

        // Profil wątrobowy
        combinations["hepatic"] = patterns.containsKey("hyperbilirubinemia") ||
                patterns.containsKey("liver_enzymes")

        // Profil nerkowy
        combinations["renal"] = patterns.containsKey("azotemia")

        // Profil hematologiczny
        combinations["hematologic"] = (patterns.containsKey("anemia") ||
                patterns.containsKey("thrombocytopenia")) &&
                patterns.containsKey("lymphopenia")

        // Stress leukogram
        combinations["stress_leukogram"] = patterns.containsKey("neutrophilia") &&
                patterns.containsKey("lymphopenia")

        return combinations
    }

    private fun determineProfiles(
        combinations: Map<String, Boolean>,
        labData: Map<String, Any>
    ): Pair<FipProfile, FipProfile?> {

        val profiles = mutableListOf<Pair<FipProfile, Float>>()

        // Ocena profilu ostrego zapalnego
        if (combinations["classic_triad"] == true && combinations["inflammatory"] == true) {
            profiles.add(FipProfile.INFLAMMATORY_ACUTE to 85f)
        }

        // Ocena profilu przewlekłego zapalnego
        if (combinations["classic_triad"] == true && combinations["wasting"] == true) {
            profiles.add(FipProfile.INFLAMMATORY_CHRONIC to 80f)
        }

        // Ocena profilu wysiękowego klasycznego
        if (combinations["classic_triad"] == true && combinations["hepatic"] == true) {
            profiles.add(FipProfile.EFFUSIVE_CLASSIC to 90f)
        }

        // Ocena profilu suchego neurologicznego
        if (combinations["inflammatory"] == true && !combinations["hepatic"]!! &&
            combinations["stress_leukogram"] == true) {
            profiles.add(FipProfile.DRY_NEUROLOGICAL to 70f)
        }

        // Ocena profilu mieszanego
        if (combinations.values.count { it } >= 4) {
            profiles.add(FipProfile.MIXED_PATTERN to 75f)
        }

        // Ocena profilu nietypowego
        if (combinations["classic_triad"] == false &&
            combinations.values.count { it } >= 2) {
            profiles.add(FipProfile.ATYPICAL to 60f)
        }

        // Jeśli brak charakterystycznych kombinacji
        if (combinations.values.count { it } < 2) {
            profiles.add(FipProfile.NON_FIP to 90f)
        }

        // Sortuj według siły dopasowania
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

        // Modyfikatory
        var modifier = 0f

        if (combinations["classic_triad"] == true) modifier += 15f
        if (combinations["stress_leukogram"] == true) modifier += 10f
        if (combinations["inflammatory"] == true) modifier += 5f

        // Kara za brak kluczowych cech
        if (combinations["classic_triad"] == false && profile != FipProfile.NON_FIP) {
            modifier -= 20f
        }

        val baseResult = baseScore + modifier

        // NOWY MNOŻNIK 2.75 - zwiększa wrażliwość systemu
        val multipliedScore = baseResult * 2.75f

        return multipliedScore.coerceIn(0f, 100f)
    }

    private fun generateKeyFindings(
        patterns: Map<String, ParameterPattern>,
        combinations: Map<String, Boolean>,
        profile: FipProfile
    ): List<String> {

        val findings = mutableListOf<String>()

        // Dodaj główne nieprawidłowości
        if (combinations["classic_triad"] == true) {
            findings.add("✅ Klasyczna triada FIP (hiperglobulinemia + hipoalbuminemia + limfopenia)")
        }

        if (combinations["stress_leukogram"] == true) {
            findings.add("✅ Stress leukogram (neutrofilia + limfopenia)")
        }

        // Dodaj ciężkie nieprawidłowości
        patterns.filter { it.value.severity == "severe" }.forEach { (key, pattern) ->
            val name = when (key) {
                "hyperglobulinemia" -> "Ciężka hiperglobulinemia"
                "hypoalbuminemia" -> "Ciężka hipoalbuminemia"
                "lymphopenia" -> "Ciężka limfopenia"
                "anemia" -> "Ciężka anemia"
                else -> key
            }
            findings.add("⚠️ $name")
        }

        // Dodaj zajęcie narządów
        if (combinations["hepatic"] == true) {
            findings.add("🔸 Zajęcie wątroby")
        }

        if (combinations["renal"] == true) {
            findings.add("🔸 Możliwe zajęcie nerek")
        }

        return findings
    }

    private fun generateProfileDescription(
        primary: FipProfile,
        secondary: FipProfile?,
        patterns: Map<String, ParameterPattern>,
        combinations: Map<String, Boolean>
    ): String {

        val descriptions = mutableListOf<String>()

        // Główny profil
        val primaryDesc = when (primary) {
            FipProfile.INFLAMMATORY_ACUTE -> {
                "Ostry profil zapalny charakterystyczny dla aktywnej fazy FIP wysiękowego"
            }
            FipProfile.INFLAMMATORY_CHRONIC -> {
                "Przewlekły profil zapalny z cechami wyniszczenia, typowy dla zaawansowanego FIP"
            }
            FipProfile.EFFUSIVE_CLASSIC -> {
                "Klasyczny profil FIP wysiękowego z zajęciem wątroby"
            }
            FipProfile.DRY_NEUROLOGICAL -> {
                "Profil sugerujący suchą formę FIP z możliwym zajęciem układu nerwowego"
            }
            FipProfile.MIXED_PATTERN -> {
                "Mieszany profil z cechami zarówno formy wysiękowej jak i suchej FIP"
            }
            FipProfile.ATYPICAL -> {
                "Nietypowy profil - niektóre cechy FIP, ale brak klasycznego obrazu"
            }
            FipProfile.NON_FIP -> {
                "Profil niecharakterystyczny dla FIP - rozważ inne choroby zapalne"
            }
        }
        descriptions.add(primaryDesc)

        // Profil drugorzędowy
        if (secondary != null) {
            descriptions.add("Cechy drugorzędowe profilu: ${secondary.name.lowercase().replace('_', ' ')}")
        }

        // Szczegóły kombinacji
        if (combinations["classic_triad"] == true) {
            descriptions.add("Obecna klasyczna triada laboratoryjna FIP")
        }

        val severeCount = patterns.values.count { it.severity == "severe" }
        if (severeCount > 0) {
            descriptions.add("Wykryto $severeCount ciężkich nieprawidłowości laboratoryjnych")
        }

        return descriptions.joinToString(". ")
    }

    private fun generateManagementSuggestions(profile: FipProfile, strength: Float): String {
        val suggestions = mutableListOf<String>()

        when (profile) {
            FipProfile.INFLAMMATORY_ACUTE, FipProfile.EFFUSIVE_CLASSIC -> {
                suggestions.add("🚨 PILNA konsultacja - wysokie podejrzenie aktywnego FIP")
                suggestions.add("📋 Zalecane: USG jamy brzusznej, punkcja płynu (test Rivalta, cytologia)")
                suggestions.add("💊 Rozważ natychmiastowe rozpoczęcie leczenia GS-441524 przy potwierdzeniu")
            }

            FipProfile.INFLAMMATORY_CHRONIC -> {
                suggestions.add("⚠️ Szybka konsultacja - prawdopodobny zaawansowany FIP")
                suggestions.add("📋 Zalecane: pełna diagnostyka obrazowa, ocena stanu ogólnego")
                suggestions.add("💊 Leczenie GS-441524 z intensywnym wsparciem")
            }

            FipProfile.DRY_NEUROLOGICAL -> {
                suggestions.add("⚠️ Konsultacja neurologiczna - możliwa neurologiczna forma FIP")
                suggestions.add("📋 Zalecane: badanie neurologiczne, rozważ MRI/CT")
                suggestions.add("💊 Wyższe dawki GS-441524 przy formie neurologicznej")
            }

            FipProfile.MIXED_PATTERN -> {
                suggestions.add("⚠️ Złożony obraz - wymaga szczegółowej diagnostyki")
                suggestions.add("📋 Kompleksowa diagnostyka: obrazowa + laboratoryjna")
            }

            FipProfile.ATYPICAL -> {
                suggestions.add("❓ Nietypowy obraz - diagnostyka różnicowa")
                suggestions.add("📋 Powtórz badania za 2-4 tygodnie, rozszerz panel")
            }

            FipProfile.NON_FIP -> {
                suggestions.add("✅ FIP mało prawdopodobny")
                suggestions.add("🔍 Szukaj innych przyczyn objawów klinicznych")
            }
        }

        // Dodaj sugestie monitorowania
        if (strength >= 70 && profile != FipProfile.NON_FIP) {
            suggestions.add("📊 Monitoruj: białko całkowite, A/G, hematokryt co 2 tyg.")
        }

        return suggestions.joinToString("\n")
    }

    private fun toDoubleValue(str: String?): Double? {
        if (str == null) return null
        val cleaned = str.replace(Regex("[<>]"), "").replace(",", ".").trim()
        return cleaned.toDoubleOrNull()
    }
}