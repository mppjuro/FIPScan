package com.example.fipscan

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.*

/**
 * Zaawansowany analizator kształtu krzywej elektroforezy.
 * Analizuje nie tylko wysokość pików, ale także szerokość, symetrię,
 * obecność mostków między frakcjami oraz inne cechy morfologiczne.
 */
object ElectrophoresisShapeAnalyzer {

    data class PeakCharacteristics(
        val height: Float,
        val width: Float,
        val symmetry: Float,  // 1.0 = idealnie symetryczny
        val sharpness: Float, // Ostrość piku (stosunek wysokości do szerokości)
        val position: Int     // Pozycja na osi X
    )

    data class BridgeCharacteristics(
        val present: Boolean,
        val depth: Float,     // Głębokość doliny między pikami (0-1, gdzie 1 = brak doliny)
        val width: Float      // Szerokość mostka
    )

    data class ShapeAnalysisResult(
        val albumin: PeakCharacteristics,
        val alpha: PeakCharacteristics,
        val beta: PeakCharacteristics,
        val gamma: PeakCharacteristics,
        val betaGammaBridge: BridgeCharacteristics,
        val alphaBetaBridge: BridgeCharacteristics,
        val overallPattern: String,  // "monoklonalny", "poliklonalny", "normalny", "mostkowaty"
        val fipShapeScore: Float,    // 0-100, wyższa wartość = bardziej typowy dla FIP
        val shapeDescription: String
    )

    fun analyzeElectrophoresisShape(
        bitmap: Bitmap,
        redColumnIndices: List<Int>
    ): ShapeAnalysisResult? {

        if (redColumnIndices.size < 3) {
            Log.e("ShapeAnalyzer", "Za mało kolumn podziału")
            return null
        }

        // Wyodrębnij profile wysokości dla każdej sekcji
        val sections = extractSectionProfiles(bitmap, redColumnIndices)

        // Analizuj charakterystyki każdego piku
        val albuminPeak = analyzePeak(sections[0], "albumin")
        val alphaPeak = analyzePeak(sections[1], "alpha")
        val betaPeak = analyzePeak(sections[2], "beta")
        val gammaPeak = analyzePeak(sections[3], "gamma")

        // Analizuj mostki między frakcjami
        val betaGammaBridge = analyzeBridge(sections[2], sections[3])
        val alphaBetaBridge = analyzeBridge(sections[1], sections[2])

        // Określ wzorzec ogólny
        val pattern = classifyPattern(albuminPeak, alphaPeak, betaPeak, gammaPeak, betaGammaBridge)

        // Oblicz wynik kształtu typowego dla FIP
        val fipScore = calculateFIPShapeScore(
            albuminPeak, alphaPeak, betaPeak, gammaPeak,
            betaGammaBridge, alphaBetaBridge, pattern
        )

        // Generuj opis tekstowy
        val description = generateShapeDescription(
            albuminPeak, alphaPeak, betaPeak, gammaPeak,
            betaGammaBridge, pattern, fipScore
        )

        return ShapeAnalysisResult(
            albumin = albuminPeak,
            alpha = alphaPeak,
            beta = betaPeak,
            gamma = gammaPeak,
            betaGammaBridge = betaGammaBridge,
            alphaBetaBridge = alphaBetaBridge,
            overallPattern = pattern,
            fipShapeScore = fipScore,
            shapeDescription = description
        )
    }

    private fun extractSectionProfiles(bitmap: Bitmap, redColumns: List<Int>): List<List<Float>> {
        val height = bitmap.height
        val width = bitmap.width
        val sections = mutableListOf<List<Float>>()

        val boundaries = listOf(
            0 to redColumns[0],
            redColumns[0] to redColumns[1],
            redColumns[1] to redColumns[2],
            redColumns[2] to width
        )

        for ((start, end) in boundaries) {
            val profile = mutableListOf<Float>()
            val sectionWidth = end - start

            // Próbkuj co 2% szerokości sekcji
            val step = max(1, sectionWidth / 50)

            for (x in start until end step step) {
                var columnHeight = 0
                for (y in 0 until height) {
                    if (!isWhite(bitmap.getPixel(x, y))) {
                        columnHeight++
                    }
                }
                profile.add(columnHeight.toFloat() / height)
            }

            sections.add(profile)
        }

        return sections
    }

    private fun analyzePeak(profile: List<Float>, name: String): PeakCharacteristics {
        if (profile.isEmpty()) {
            return PeakCharacteristics(0f, 0f, 0f, 0f, 0)
        }

        // Znajdź maksimum
        val maxHeight = profile.maxOrNull() ?: 0f
        val maxIndex = profile.indexOf(maxHeight)

        // Oblicz szerokość w połowie wysokości (FWHM)
        val halfHeight = maxHeight / 2
        var leftIndex = maxIndex
        var rightIndex = maxIndex

        while (leftIndex > 0 && profile[leftIndex] > halfHeight) leftIndex--
        while (rightIndex < profile.size - 1 && profile[rightIndex] > halfHeight) rightIndex++

        val width = (rightIndex - leftIndex).toFloat() / profile.size

        // Oblicz symetrię
        val symmetry = calculateSymmetry(profile, maxIndex)

        // Oblicz ostrość piku
        val sharpness = if (width > 0) maxHeight / width else 0f

        return PeakCharacteristics(
            height = maxHeight,
            width = width,
            symmetry = symmetry,
            sharpness = sharpness,
            position = maxIndex
        )
    }

    private fun calculateSymmetry(profile: List<Float>, peakIndex: Int): Float {
        if (profile.isEmpty() || peakIndex == 0 || peakIndex == profile.size - 1) {
            return 0f
        }

        val leftSide = profile.subList(0, peakIndex)
        val rightSide = profile.subList(peakIndex, profile.size)

        val minSize = min(leftSide.size, rightSide.size)
        if (minSize == 0) return 0f

        var symmetrySum = 0f
        for (i in 0 until minSize) {
            val leftVal = leftSide[leftSide.size - 1 - i]
            val rightVal = rightSide[i]
            val diff = abs(leftVal - rightVal)
            symmetrySum += 1f - (diff / max(leftVal, rightVal).coerceAtLeast(0.01f))
        }

        return (symmetrySum / minSize).coerceIn(0f, 1f)
    }

    private fun analyzeBridge(leftSection: List<Float>, rightSection: List<Float>): BridgeCharacteristics {
        if (leftSection.isEmpty() || rightSection.isEmpty()) {
            return BridgeCharacteristics(false, 0f, 0f)
        }

        // Znajdź minimum w dolinie między sekcjami
        val leftEnd = leftSection.takeLast(10).average().toFloat()
        val rightStart = rightSection.take(10).average().toFloat()
        val valleyDepth = min(leftEnd, rightStart)

        // Znajdź maksima w obu sekcjach
        val leftMax = leftSection.maxOrNull() ?: 0f
        val rightMax = rightSection.maxOrNull() ?: 0f
        val avgMax = (leftMax + rightMax) / 2

        // Oblicz głębokość doliny względem średniej wysokości pików
        val relativeDepth = if (avgMax > 0) valleyDepth / avgMax else 0f

        // Mostek istnieje, jeśli dolina nie schodzi poniżej 30% średniej wysokości
        val bridgePresent = relativeDepth > 0.3f

        // Szerokość mostka (proporcja sekcji z podwyższonymi wartościami)
        val bridgeWidth = if (bridgePresent) {
            val threshold = avgMax * 0.3f
            val leftBridge = leftSection.count { it > threshold }.toFloat() / leftSection.size
            val rightBridge = rightSection.count { it > threshold }.toFloat() / rightSection.size
            (leftBridge + rightBridge) / 2
        } else 0f

        return BridgeCharacteristics(
            present = bridgePresent,
            depth = relativeDepth,
            width = bridgeWidth
        )
    }

    private fun classifyPattern(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        beta: PeakCharacteristics,
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics
    ): String {

        // Sprawdź czy gamma jest dominująca i ma ostry pik (monoklonalny)
        if (gamma.sharpness > 2f && gamma.height > albumin.height * 1.5f) {
            return "monoklonalny"
        }

        // Sprawdź czy gamma jest szeroka i podwyższona (poliklonalny)
        if (gamma.width > 0.3f && gamma.height > albumin.height * 1.2f) {
            return "poliklonalny"
        }

        // Sprawdź obecność mostka beta-gamma
        if (betaGammaBridge.present && betaGammaBridge.depth > 0.5f) {
            return "mostkowaty"
        }

        // Sprawdź czy albumina dominuje (normalny)
        if (albumin.height > gamma.height * 1.5f && albumin.height > alpha.height * 1.5f) {
            return "normalny"
        }

        return "niespecyficzny"
    }

    private fun calculateFIPShapeScore(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        beta: PeakCharacteristics,
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        alphaBetaBridge: BridgeCharacteristics,
        pattern: String
    ): Float {
        var score = 0f

        // Punkty za wzorzec
        when (pattern) {
            "poliklonalny" -> score += 40f
            "mostkowaty" -> score += 35f
            "monoklonalny" -> score += 20f  // Może występować przy FIP, ale rzadziej
            "normalny" -> score -= 20f
        }

        // Punkty za niską albuminę względem gamma
        val agRatio = if (gamma.height > 0) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> score += 30f
            agRatio < 0.8f -> score += 20f
            agRatio < 1.0f -> score += 10f
        }

        // Punkty za szerokość piku gamma (szerszy = bardziej poliklonalny)
        if (gamma.width > 0.35f) score += 15f
        else if (gamma.width > 0.25f) score += 10f

        // Punkty za mostek beta-gamma
        if (betaGammaBridge.present) {
            score += betaGammaBridge.depth * 20f  // Max 20 punktów
        }

        // Punkty za asymetrię gamma (typowa dla FIP)
        if (gamma.symmetry < 0.7f) score += 10f

        // Punkty za podwyższone frakcje alfa
        if (alpha.height > albumin.height * 0.5f) score += 5f

        return score.coerceIn(0f, 100f)
    }

    private fun generateShapeDescription(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        beta: PeakCharacteristics,
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        pattern: String,
        fipScore: Float
    ): String {

        val descriptions = mutableListOf<String>()

        // Opis wzorca
        when (pattern) {
            "poliklonalny" -> descriptions.add("Wykryto wzorzec poliklonalny z szerokim pikiem gamma")
            "monoklonalny" -> descriptions.add("Wykryto ostry pik monoklonalny w regionie gamma")
            "mostkowaty" -> descriptions.add("Obecny mostek beta-gamma charakterystyczny dla stanów zapalnych")
            "normalny" -> descriptions.add("Krzywą elektroforezy zbliżona do normy")
            else -> descriptions.add("Niespecyficzny wzorzec elektroforezy")
        }

        // Opis stosunku albumin/globulin
        val agRatio = if (gamma.height > 0) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> descriptions.add("Znacznie obniżony stosunek albumin do gamma (A/G < 0.6)")
            agRatio < 0.8f -> descriptions.add("Obniżony stosunek albumin do gamma (A/G < 0.8)")
        }

        // Opis mostków
        if (betaGammaBridge.present) {
            descriptions.add("Mostek beta-gamma o głębokości ${(betaGammaBridge.depth * 100).toInt()}%")
        }

        // Opis symetrii
        if (gamma.symmetry < 0.7f) {
            descriptions.add("Asymetryczny pik gamma")
        }

        // Podsumowanie
        val risk = when {
            fipScore >= 70 -> "WYSOKO sugestywny"
            fipScore >= 50 -> "umiarkowanie sugestywny"
            fipScore >= 30 -> "słabo sugestywny"
            else -> "niesugestywny"
        }
        descriptions.add("Kształt krzywej $risk dla FIP (wynik: ${fipScore.toInt()}/100)")

        return descriptions.joinToString(". ")
    }

    private fun isWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 240 && g > 240 && b > 240
    }
}