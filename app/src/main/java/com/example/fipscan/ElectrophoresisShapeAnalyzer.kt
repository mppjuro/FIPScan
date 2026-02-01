package com.example.fipscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.get

object ElectrophoresisShapeAnalyzer {

    private const val PATTERN_MONOCLONAL = "monoclonal"
    private const val PATTERN_POLYCLONAL = "polyclonal"
    private const val PATTERN_BRIDGING = "bridging"
    private const val PATTERN_NORMAL = "normal"
    private const val PATTERN_NONSPECIFIC = "nonspecific"
    private var curveData: IntArray = IntArray(0)
    private var baselineY: Int = 0
    private var fractionRanges: List<IntRange> = emptyList()
    const val SHAPE_ANALYSIS_MAX_POINTS = 30
    const val PATTERN_ANALYSIS_MAX_POINTS = 30
    private const val MIN_SEPARATOR_GAP = 20

    data class GammaAnalysisResult(
        val peakHeight: Int,
        val peakIndex: Int,
        val meanIndex: Double,
        val variance: Double,
        val stdDev: Double,
        val totalMass: Double
    )

    data class WidthRatioAnalysis(
        val gamma70ToBeta: Float,
        val gamma50ToBeta: Float,
        val gamma30ToBeta: Float,
        val gamma70ToGamma30: Float
    )
    data class PeakCharacteristics(
        val height: Float,
        val widthPxMap: Map<Int, Int>,
        val symmetry: Float,
        val sharpness: Float,
        val position: Int,
        val rangeSize: Int
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

    fun analyzeChartBitmap(chartBitmap: Bitmap): ElectrophoresisShapeAnalyzer {
        baselineY = findBaselineY(chartBitmap)
        curveData = extractCurveData(chartBitmap, baselineY)
        fractionRanges = findFractionRanges(chartBitmap)
        return this
    }
    private fun findBaselineY(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height

        for (y in height - 1 downTo 0) {
            var blueCount = 0
            for (x in 0 until width) {
                if (isChartColor(bitmap[x, y])) {
                    blueCount++
                }
            }

            if (blueCount.toFloat() / width.toFloat() > 0.5f) {
                Log.d("ShapeAnalyzer", "Automatycznie wykryto linię bazową na y=$y")
                return y
            }
        }

        Log.w("ShapeAnalyzer", "Nie znaleziono linii bazowej >50% pikseli. Używanie domyślnej (95% wysokości).")
        val fallbackY = (height * 0.95f).toInt().coerceAtMost(height - 2)
        Log.d("ShapeAnalyzer", "Użyto zastępczej linii bazowej na y=$fallbackY")
        return fallbackY
    }

    private fun isChartColor(pixelColor: Int): Boolean {
        val red = Color.red(pixelColor)
        val green = Color.green(pixelColor)
        val blue = Color.blue(pixelColor)
        return blue > (red + 20) && blue > (green + 20) && blue > 50 && red < 200 && green < 200
    }

    private fun isRedLineColor(pixelColor: Int): Boolean {
        val red = Color.red(pixelColor)
        val green = Color.green(pixelColor)
        val blue = Color.blue(pixelColor)
        return red > 180 && green < 100 && blue < 100
    }

    private fun extractCurveData(bitmap: Bitmap, baselineY: Int): IntArray {
        val width = bitmap.width
        val data = IntArray(width)

        for (x in 0 until width) {
            data[x] = 0
            for (y in 0..baselineY) {
                val pixelColor = bitmap[x, y]
                if (isChartColor(pixelColor) || isRedLineColor(pixelColor)) {
                    val height = baselineY - y
                    data[x] = height
                    break
                }
            }
        }
        return data
    }

    private fun findFractionRanges(bitmap: Bitmap): List<IntRange> {
        val width = bitmap.width
        val height = baselineY
        if (width == 0 || height == 0) return emptyList()

        for (y in 0 until height) {
            val redClustersOnThisLine = mutableListOf<IntRange>()
            var inCluster = false
            var clusterStart = 0

            for (x in 0 until width) {
                val isRed = isRedLineColor(bitmap[x, y])
                if (isRed && !inCluster) {
                    inCluster = true
                    clusterStart = x
                } else if (!isRed && inCluster) {
                    inCluster = false
                    redClustersOnThisLine.add(clusterStart until x)
                }
            }
            if (inCluster) {
                redClustersOnThisLine.add(clusterStart until width)
            }

            if (redClustersOnThisLine.size == 3) {
                val c1 = redClustersOnThisLine[0]
                val c2 = redClustersOnThisLine[1]
                val c3 = redClustersOnThisLine[2]

                val gap1 = c2.first - c1.last
                val gap2 = c3.first - c2.last

                if (gap1 >= MIN_SEPARATOR_GAP && gap2 >= MIN_SEPARATOR_GAP) {
                    val center1 = (c1.first + c1.last) / 2
                    val center2 = (c2.first + c2.last) / 2
                    val center3 = (c3.first + c3.last) / 2

                    Log.d("ShapeAnalyzer", "Znaleziono granice frakcji na y=$y. Centra: $center1, $center2, $center3")

                    return listOf(
                        0..center1,
                        (center1 + 1)..center2,
                        (center2 + 1)..center3,
                        (center3 + 1) until width
                    )
                }
            }
        }

        Log.e("ShapeAnalyzer", "Nie znaleziono linii z 3 czerwonymi separatorami. Analiza kształtu nie będzie dokładna.")
        return emptyList()
    }

    fun analyzeGammaPeak(): GammaAnalysisResult? {
        if (fractionRanges.size < 4) return null

        val gammaRange = fractionRanges[3]
        if (gammaRange.first < 0 || gammaRange.last >= curveData.size || gammaRange.first >= gammaRange.last) {
            return null
        }

        var peakHeight = 0
        var peakIndex = gammaRange.first
        for (i in gammaRange) {
            if (curveData[i] > peakHeight) {
                peakHeight = curveData[i]
                peakIndex = i
            }
        }

        var weightedSum = 0.0
        var totalMass = 0.0
        for (i in gammaRange) {
            val height = curveData[i].toDouble()
            weightedSum += i * height
            totalMass += height
        }

        if (totalMass == 0.0) {
            return GammaAnalysisResult(peakHeight, peakIndex, 0.0, 0.0, 0.0, 0.0)
        }
        val meanIndex = weightedSum / totalMass

        var varianceSum = 0.0
        for (i in gammaRange) {
            val height = curveData[i].toDouble()
            varianceSum += height * (i - meanIndex).pow(2)
        }

        val variance = varianceSum / totalMass
        val stdDev = sqrt(variance)

        return GammaAnalysisResult(peakHeight, peakIndex, meanIndex, variance, stdDev, totalMass)
    }

    fun getFractionsAUC(): Map<String, Double> {
        if (fractionRanges.size < 4) return emptyMap()

        val totalAUC = calculateAUC(curveData, 0 until curveData.size)
        if (totalAUC == 0.0) return emptyMap()

        val albuminAUC = calculateAUC(curveData, fractionRanges[0])
        val alphaAUC = calculateAUC(curveData, fractionRanges[1])
        val betaAUC = calculateAUC(curveData, fractionRanges[2])
        val gammaAUC = calculateAUC(curveData, fractionRanges[3])

        return mapOf(
            "Albumin" to (albuminAUC / totalAUC) * 100.0,
            "Alpha" to (alphaAUC / totalAUC) * 100.0,
            "Beta" to (betaAUC / totalAUC) * 100.0,
            "Gamma" to (gammaAUC / totalAUC) * 100.0,
            "TotalAUC_Pixels" to totalAUC
        )
    }

    fun analyzeElectrophoresisShape(context: Context): ShapeAnalysisResult? {

        if (fractionRanges.size < 4) {
            Log.e("ShapeAnalyzer", "Brak granic frakcji. Wywołanie 'analyzeElectrophoresisShape' nie powiodło się.")
            return null
        }

        val albuminPeak = analyzePeak(fractionRanges[0])
        val alphaPeak = analyzePeak(fractionRanges[1])
        val betaPeak = analyzePeak(fractionRanges[2])
        val gammaPeak = analyzePeak(fractionRanges[3])

        val betaGammaBridge = analyzeBridge(fractionRanges[2], fractionRanges[3])
        val alphaBetaBridge = analyzeBridge(fractionRanges[1], fractionRanges[2])

        val internalPattern = classifyPattern(albuminPeak, alphaPeak, gammaPeak, betaGammaBridge)

        val fipScore = calculateFIPShapeScore(
            albuminPeak, alphaPeak, gammaPeak,
            betaGammaBridge, internalPattern
        )

        val description = generateShapeDescription(
            albuminPeak, gammaPeak,
            betaGammaBridge, internalPattern, fipScore, context
        )

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

    private fun calculateSymmetry(range: IntRange, peakIndex: Int): Float {
        if (peakIndex <= range.first || peakIndex >= range.last) {
            return 0f
        }

        val leftSize = peakIndex - range.first
        val rightSize = range.last - peakIndex
        val minSize = min(leftSize, rightSize)

        if (minSize == 0) return 0f

        var symmetrySum = 0f
        for (i in 1..minSize) {
            val leftVal = curveData[peakIndex - i].toFloat()
            val rightVal = curveData[peakIndex + i].toFloat()
            val diff = abs(leftVal - rightVal)
            val maxVal = max(leftVal, rightVal)

            symmetrySum += if (maxVal < 0.01f) 1f else (1f - (diff / maxVal))
        }
        return (symmetrySum / minSize).coerceIn(0f, 1f)
    }

    private fun analyzeBridge(leftRange: IntRange, rightRange: IntRange): BridgeCharacteristics {
        val valleyWidth = min(5, (leftRange.last - leftRange.first) / 10).coerceAtLeast(1)
        val valleyStart = (leftRange.last - valleyWidth).coerceAtLeast(0)
        val valleyEnd = (rightRange.first + valleyWidth).coerceAtMost(curveData.size - 1)

        if (curveData.isEmpty() || valleyStart >= valleyEnd) {
            return BridgeCharacteristics(false, 0f, 0f)
        }

        var valleyMin = Int.MAX_VALUE
        for (i in valleyStart..valleyEnd) {
            valleyMin = min(valleyMin, curveData[i])
        }

        val leftMax = curveData.slice(leftRange).maxOrNull() ?: 0
        val rightMax = curveData.slice(rightRange).maxOrNull() ?: 0
        val avgMax = (leftMax + rightMax) / 2f

        if (avgMax < 0.01f) return BridgeCharacteristics(false, 0f, 0f)

        val relativeDepth = valleyMin.toFloat() / avgMax
        val bridgePresent = relativeDepth > 0.3f

        return BridgeCharacteristics(
            present = bridgePresent,
            depth = relativeDepth,
            width = if (bridgePresent) relativeDepth else 0f
        )
    }

    private fun classifyPattern(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics
    ): String {
        val gammaWidth50Percent = if (gamma.rangeSize > 0) (gamma.widthPxMap[50]?.toFloat() ?: 1f) / gamma.rangeSize.toFloat() else 0f

        if (gamma.sharpness > 200f && gamma.height > albumin.height * 1.5f) {
            return PATTERN_MONOCLONAL
        }
        if (gammaWidth50Percent > 0.3f && gamma.height > albumin.height * 1.2f) {
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
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        pattern: String
    ): Float {
        var score = 0f
        when (pattern) {
            PATTERN_POLYCLONAL -> score += 40f
            PATTERN_BRIDGING -> score += 35f
            PATTERN_MONOCLONAL -> score += 20f
            PATTERN_NORMAL -> score -= 20f
            else -> score += 0f
        }

        val agRatio = if (gamma.height > 0.01f) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> score += 30f
            agRatio < 0.8f -> score += 20f
            agRatio < 1.0f -> score += 10f
        }

        val gammaWidth50Percent = if (gamma.rangeSize > 0) (gamma.widthPxMap[50]?.toFloat() ?: 1f) / gamma.rangeSize.toFloat() else 0f
        if (gammaWidth50Percent > 0.35f) score += 15f
        else if (gammaWidth50Percent > 0.25f) score += 10f

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
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics,
        pattern: String,
        fipScore: Float,
        context: Context
    ): String {

        val descriptions = mutableListOf<String>()

        when (pattern) {
            PATTERN_POLYCLONAL -> descriptions.add(context.getString(R.string.desc_pattern_polyclonal))
            PATTERN_MONOCLONAL -> descriptions.add(context.getString(R.string.desc_pattern_monoclonal))
            PATTERN_BRIDGING -> descriptions.add(context.getString(R.string.desc_pattern_bridging))
            PATTERN_NORMAL -> descriptions.add(context.getString(R.string.desc_pattern_normal))
            else -> descriptions.add(context.getString(R.string.desc_pattern_nonspecific))
        }

        val agRatio = if (gamma.height > 0.01f) albumin.height / gamma.height else 999f
        when {
            agRatio < 0.6f -> descriptions.add(context.getString(R.string.desc_ag_very_low))
            agRatio < 0.8f -> descriptions.add(context.getString(R.string.desc_ag_low))
        }

        if (betaGammaBridge.present) {
            descriptions.add(context.getString(R.string.desc_bridge_depth, (betaGammaBridge.depth * 100).toInt()))
        }

        if (gamma.symmetry < 0.7f) {
            descriptions.add(context.getString(R.string.desc_gamma_asymmetry))
        }

        val risk = when {
            fipScore >= 70 -> context.getString(R.string.risk_high_adj)
            fipScore >= 50 -> context.getString(R.string.risk_moderate_adj)
            fipScore >= 30 -> context.getString(R.string.risk_low_adj)
            else -> context.getString(R.string.risk_none_adj)
        }
        descriptions.add(context.getString(R.string.desc_summary, risk, fipScore.toInt()))

        return descriptions.joinToString(". ")
    }

    internal fun calculateAUC(data: IntArray, range: IntRange): Double {
        var area = 0.0
        if (range.first >= data.size || range.last >= data.size || range.first < 0) {
            return 0.0
        }

        for (i in range.first until range.last) {
            val h1 = data[i].toDouble()
            val h2 = data[i + 1].toDouble()
            area += (h1 + h2) / 2.0
        }
        return area
    }

    fun calculateWidthRatios(): WidthRatioAnalysis? {
        if (fractionRanges.size < 4) {
            Log.e("ShapeAnalyzer", "Brak granic frakcji, nie można obliczyć proporcji szerokości.")
            return null
        }

        val betaRange = fractionRanges[2]
        val gammaRange = fractionRanges[3]

        // Szerokość regionu beta to odległość między czerwonymi liniami
        val betaRegionWidth = (betaRange.last - betaRange.first + 1).toFloat()
        if (betaRegionWidth <= 0f) {
            Log.e("ShapeAnalyzer", "Szerokość regionu Beta wynosi 0, nie można obliczyć proporcji.")
            return null
        }

        val gammaPeak = analyzePeak(gammaRange)
        if (gammaPeak.height == 0f) {
            Log.w("ShapeAnalyzer", "Pik Gamma ma wysokość 0, proporcje będą wynosić 0.")
            return WidthRatioAnalysis(0f, 0f, 0f, 0f)
        }

        val gammaWidthPx70 = gammaPeak.widthPxMap[70]?.toFloat() ?: 1f
        val gammaWidthPx50 = gammaPeak.widthPxMap[50]?.toFloat() ?: 1f
        val gammaWidthPx30 = gammaPeak.widthPxMap[30]?.toFloat() ?: 1f

        val ratio70ToBeta = (gammaWidthPx70 / betaRegionWidth) * 100f
        val ratio50ToBeta = (gammaWidthPx50 / betaRegionWidth) * 100f
        val ratio30ToBeta = (gammaWidthPx30 / betaRegionWidth) * 100f

        val ratio70To30 = if (gammaWidthPx30 > 0.01f) {
            (gammaWidthPx70 / gammaWidthPx30) * 100f
        } else {
            0f
        }

        Log.d("ShapeAnalyzer", "Obliczono proporcje: 70/Beta=${ratio70ToBeta}%, 50/Beta=${ratio50ToBeta}%, 30/Beta=${ratio30ToBeta}%, 70/30=${ratio70To30}%")

        return WidthRatioAnalysis(
            gamma70ToBeta = ratio70ToBeta,
            gamma50ToBeta = ratio50ToBeta,
            gamma30ToBeta = ratio30ToBeta,
            gamma70ToGamma30 = ratio70To30
        )
    }

    private fun analyzePeak(range: IntRange): PeakCharacteristics {
        val defaultPeak = PeakCharacteristics(0f, emptyMap(), 0f, 0f, 0, 0)
        if (curveData.isEmpty() || range.first < 0 || range.last >= curveData.size || range.first >= range.last) {
            return defaultPeak
        }

        val profileSize = (range.last - range.first + 1).coerceAtLeast(1)
        var maxHeight = 0
        var maxIndex = range.first

        for (i in range) {
            if (curveData[i] > maxHeight) {
                maxHeight = curveData[i]
                maxIndex = i
            }
        }

        if (maxHeight == 0) {
            return defaultPeak.copy(rangeSize = profileSize)
        }

        val levels = (10..90 step 10)
        val heightThresholds = levels.associateWith { maxHeight * (it / 100.0) }

        val leftIndices = mutableMapOf<Int, Int>()
        val rightIndices = mutableMapOf<Int, Int>()
        levels.forEach { level ->
            leftIndices[level] = maxIndex
            rightIndices[level] = maxIndex
        }

        for (i in maxIndex downTo range.first) {
            val h = curveData[i]
            for (level in levels) {
                if (h >= heightThresholds[level]!!) {
                    leftIndices[level] = i
                }
            }
        }
        for (i in maxIndex..range.last) {
            val h = curveData[i]
            for (level in levels) {
                if (h >= heightThresholds[level]!!) {
                    rightIndices[level] = i
                }
            }
        }

        val widthPxMap = mutableMapOf<Int, Int>()
        levels.forEach { level ->
            val widthPx = (rightIndices[level]!! - leftIndices[level]!!).coerceAtLeast(1)
            widthPxMap[level] = widthPx
        }

        if (fractionRanges.isNotEmpty() && range == fractionRanges.getOrNull(3)) {
            Log.d("ShapeAnalyzer", "--- Analiza szerokości piku Gamma (w Px) ---")
            widthPxMap.toSortedMap().forEach { (level, width) ->
                Log.d("ShapeAnalyzer", "  Poziom: ${level}% H, Szerokość: ${width}px")
            }
            Log.d("ShapeAnalyzer", "------------------------------------------")
        }
        val widthPx50 = widthPxMap[50] ?: 1
        val width50Percent = widthPx50.toFloat() / profileSize.toFloat()
        val sharpness = if (width50Percent > 0.001f) maxHeight.toFloat() / width50Percent else 0f
        val symmetry = calculateSymmetry(range, maxIndex)

        return PeakCharacteristics(
            height = maxHeight.toFloat(),
            widthPxMap = widthPxMap,
            symmetry = symmetry,
            sharpness = sharpness,
            position = maxIndex,
            rangeSize = profileSize
        )
    }
}