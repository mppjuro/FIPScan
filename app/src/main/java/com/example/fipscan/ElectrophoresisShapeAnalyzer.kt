package com.example.fipscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ElectrophoresisShapeAnalyzer {

    private const val PATTERN_MONOCLONAL = "monoclonal"
    private const val PATTERN_POLYCLONAL = "polyclonal"
    private const val PATTERN_BRIDGING = "bridging"
    private const val PATTERN_NORMAL = "normal"
    private const val PATTERN_NONSPECIFIC = "nonspecific"

    // Stałe punktacji dla lepszej czytelności i łatwiejszych modyfikacji
    const val SHAPE_ANALYSIS_MAX_POINTS = 30
    const val PATTERN_ANALYSIS_MAX_POINTS = 30

    data class PeakCharacteristics(
        val height: Float,
        val width: Float,
        val symmetry: Float,
        val sharpness: Float,
        val position: Int
    )

    data class BridgeCharacteristics(
        val present: Boolean,
        val depth: Float,
        val width: Float
    )

    data class ShapeAnalysisResult(
        val albumin: PeakCharacteristics,
        val alpha: PeakCharacteristics,
        val beta: PeakCharacteristics,
        val gamma: PeakCharacteristics,
        val betaGammaBridge: BridgeCharacteristics,
        val alphaBetaBridge: BridgeCharacteristics,
        val overallPattern: String,
        val fipShapeScore: Float,
        val shapeDescription: String
    )

    fun analyzeElectrophoresisShape(
        bitmap: Bitmap,
        redColumnIndices: List<Int>,
        context: Context
    ): ShapeAnalysisResult? {

        if (redColumnIndices.size < 3) {
            Log.e("ShapeAnalyzer", "Not enough split columns")
            return null
        }

        val sections = extractSectionProfiles(bitmap, redColumnIndices)
        // Zakładamy, że mamy 4 sekcje: Albuminy, Alfa, Beta, Gamma
        if (sections.size < 4) {
            Log.e("ShapeAnalyzer", "Not enough sections extracted")
            return null
        }
        val albuminPeak = analyzePeak(sections[0])
        val alphaPeak = analyzePeak(sections[1])
        val betaPeak = analyzePeak(sections[2])
        val gammaPeak = analyzePeak(sections[3])

        val betaGammaBridge = analyzeBridge(sections[2], sections[3])
        val alphaBetaBridge = analyzeBridge(sections[1], sections[2])

        // Wewnętrzny identyfikator wzorca
        val internalPattern = classifyPattern(albuminPeak, alphaPeak, gammaPeak, betaGammaBridge)

        val fipScore = calculateFIPShapeScore(
            albuminPeak, alphaPeak, gammaPeak,
            betaGammaBridge, internalPattern
        )

        val description = generateShapeDescription(
            albuminPeak, gammaPeak,
            betaGammaBridge, internalPattern, fipScore, context
        )

        // Tłumaczenie nazwy wzorca na potrzeby UI
        val localizedPattern = when (internalPattern) {
            PATTERN_MONOCLONAL -> context.getString(R.string.pattern_name_monoclonal)
            PATTERN_POLYCLONAL -> context.getString(R.string.pattern_name_polyclonal)
            PATTERN_BRIDGING -> context.getString(R.string.pattern_name_bridging)
            PATTERN_NORMAL -> context.getString(R.string.pattern_name_normal)
            else -> context.getString(R.string.pattern_name_nonspecific)
        }

        return ShapeAnalysisResult(
            albumin = albuminPeak,
            alpha = alphaPeak,
            beta = betaPeak,
            gamma = gammaPeak,
            betaGammaBridge = betaGammaBridge,
            alphaBetaBridge = alphaBetaBridge,
            overallPattern = localizedPattern,
            fipShapeScore = fipScore,
            shapeDescription = description
        )
    }

    private fun extractSectionProfiles(bitmap: Bitmap, redColumns: List<Int>): List<List<Float>> {
        val height = bitmap.height
        val width = bitmap.width
        val sections = mutableListOf<List<Float>>()

        // Zabezpieczenie przed wyjściem poza zakres bitmapy
        val safeRedColumns = redColumns.map { it.coerceIn(0, width) }.sorted()

        val boundaries = listOf(
            0 to safeRedColumns[0],
            safeRedColumns[0] to safeRedColumns[1],
            safeRedColumns[1] to safeRedColumns[2],
            safeRedColumns[2] to width
        )

        for ((start, end) in boundaries) {
            val profile = mutableListOf<Float>()
            val sectionWidth = end - start
            if (sectionWidth <= 0) {
                sections.add(emptyList())
                continue
            }
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

    private fun analyzePeak(profile: List<Float>): PeakCharacteristics {
        if (profile.isEmpty()) {
            return PeakCharacteristics(0f, 0f, 0f, 0f, 0)
        }

        val maxHeight = profile.maxOrNull() ?: 0f
        val maxIndex = profile.indexOf(maxHeight)

        val halfHeight = maxHeight / 2
        var leftIndex = maxIndex
        var rightIndex = maxIndex

        while (leftIndex > 0 && profile[leftIndex] > halfHeight) leftIndex--
        while (rightIndex < profile.size - 1 && profile[rightIndex] > halfHeight) rightIndex++

        val widthPx = (rightIndex - leftIndex).coerceAtLeast(1)
        val width = widthPx.toFloat() / profile.size.coerceAtLeast(1)
        val symmetry = calculateSymmetry(profile, maxIndex)
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
            // Używamy coerceAtLeast, aby uniknąć dzielenia przez zero
            symmetrySum += 1f - (diff / max(leftVal, rightVal).coerceAtLeast(0.01f))
        }

        return (symmetrySum / minSize).coerceIn(0f, 1f)
    }

    private fun analyzeBridge(leftSection: List<Float>, rightSection: List<Float>): BridgeCharacteristics {
        if (leftSection.isEmpty() || rightSection.isEmpty()) {
            return BridgeCharacteristics(false, 0f, 0f)
        }

        val leftEnd = leftSection.takeLast(min(10, leftSection.size)).average().toFloat()
        val rightStart = rightSection.take(min(10, rightSection.size)).average().toFloat()
        val valleyDepth = min(leftEnd, rightStart)

        val leftMax = leftSection.maxOrNull() ?: 0f
        val rightMax = rightSection.maxOrNull() ?: 0f
        val avgMax = (leftMax + rightMax) / 2

        val relativeDepth = if (avgMax > 0) valleyDepth / avgMax else 0f
        val bridgePresent = relativeDepth > 0.3f

        val bridgeWidth = if (bridgePresent) {
            val threshold = avgMax * 0.3f
            val leftBridgeCount = leftSection.count { it > threshold }
            val rightBridgeCount = rightSection.count { it > threshold }
            val leftRatio = if (leftSection.isNotEmpty()) leftBridgeCount.toFloat() / leftSection.size else 0f
            val rightRatio = if (rightSection.isNotEmpty()) rightBridgeCount.toFloat() / rightSection.size else 0f
            (leftRatio + rightRatio) / 2
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
        // beta removed as currently unused in simple classification
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics
    ): String {
        if (gamma.sharpness > 2f && gamma.height > albumin.height * 1.5f) {
            return PATTERN_MONOCLONAL
        }
        if (gamma.width > 0.3f && gamma.height > albumin.height * 1.2f) {
            return PATTERN_POLYCLONAL
        }
        if (betaGammaBridge.present && betaGammaBridge.depth > 0.5f) {
            return PATTERN_BRIDGING
        }
        if (albumin.height > gamma.height * 1.5f && albumin.height > alpha.height * 1.5f) {
            return PATTERN_NORMAL
        }
        return PATTERN_NONSPECIFIC
    }

    private fun calculateFIPShapeScore(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        // beta removed
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        // alphaBetaBridge removed
        pattern: String
    ): Float {
        var score = 0f
        when (pattern) {
            PATTERN_POLYCLONAL -> score += 40f
            PATTERN_BRIDGING -> score += 35f
            PATTERN_MONOCLONAL -> score += 20f
            PATTERN_NORMAL -> score -= 20f
        }

        val agRatio = if (gamma.height > 0.01f) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> score += 30f
            agRatio < 0.8f -> score += 20f
            agRatio < 1.0f -> score += 10f
        }

        if (gamma.width > 0.35f) score += 15f
        else if (gamma.width > 0.25f) score += 10f

        if (betaGammaBridge.present) {
            score += betaGammaBridge.depth * 20f
        }

        if (gamma.symmetry < 0.7f) score += 10f

        if (alpha.height > albumin.height * 0.5f) score += 5f

        val multipliedScore = score * 2.75f
        return multipliedScore.coerceIn(0f, 100f)
    }

    private fun generateShapeDescription(
        albumin: PeakCharacteristics,
        // alpha, beta removed as unused in text generation
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        pattern: String,
        fipScore: Float,
        context: Context
    ): String {

        val descriptions = mutableListOf<String>()

        // Opis wzorca
        when (pattern) {
            PATTERN_POLYCLONAL -> descriptions.add(context.getString(R.string.desc_pattern_polyclonal))
            PATTERN_MONOCLONAL -> descriptions.add(context.getString(R.string.desc_pattern_monoclonal))
            PATTERN_BRIDGING -> descriptions.add(context.getString(R.string.desc_pattern_bridging))
            PATTERN_NORMAL -> descriptions.add(context.getString(R.string.desc_pattern_normal))
            else -> descriptions.add(context.getString(R.string.desc_pattern_nonspecific))
        }

        // Opis stosunku albumin/globulin
        val agRatio = if (gamma.height > 0.01f) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> descriptions.add(context.getString(R.string.desc_ag_very_low))
            agRatio < 0.8f -> descriptions.add(context.getString(R.string.desc_ag_low))
        }

        // Opis mostków
        if (betaGammaBridge.present) {
            descriptions.add(context.getString(R.string.desc_bridge_depth, (betaGammaBridge.depth * 100).toInt()))
        }

        // Opis symetrii
        if (gamma.symmetry < 0.7f) {
            descriptions.add(context.getString(R.string.desc_gamma_asymmetry))
        }

        // Podsumowanie
        val risk = when {
            fipScore >= 70 -> context.getString(R.string.risk_high_adj)
            fipScore >= 50 -> context.getString(R.string.risk_moderate_adj)
            fipScore >= 30 -> context.getString(R.string.risk_low_adj)
            else -> context.getString(R.string.risk_none_adj)
        }
        descriptions.add(context.getString(R.string.desc_summary, risk, fipScore.toInt()))

        return descriptions.joinToString(". ")
    }

    private fun isWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 230 && g > 230 && b > 230
    }
}