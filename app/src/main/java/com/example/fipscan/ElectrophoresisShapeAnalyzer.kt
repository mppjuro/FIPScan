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

object ElectrophoresisShapeAnalyzer {

    private const val PATTERN_MONOCLONAL = "monoclonal"
    private const val PATTERN_POLYCLONAL = "polyclonal"
    private const val PATTERN_BRIDGING = "bridging"
    private const val PATTERN_NORMAL = "normal"
    private const val PATTERN_NONSPECIFIC = "nonspecific"

    // --- ZMIENNE STANU ---
    // Przechowuje zanalizowane dane: indeks to X, wartość to Y (wysokość)
    private var curveData: IntArray = IntArray(0)
    // Przechowuje współrzędną Y linii bazowej (osi X)
    private var baselineY: Int = 0
    // Przechowuje dynamicznie znalezione zakresy 4 frakcji (Albuminy, Alfa, Beta, Gamma)
    private var fractionRanges: List<IntRange> = emptyList()

    // Stałe punktacji dla lepszej czytelności i łatwiejszych modyfikacji
    const val SHAPE_ANALYSIS_MAX_POINTS = 30
    const val PATTERN_ANALYSIS_MAX_POINTS = 30

    // Minimalny odstęp między czerwonymi liniami (w pikselach)
    private const val MIN_SEPARATOR_GAP = 20

    // --- DATA CLASSES ---

    data class GammaAnalysisResult(
        val peakHeight: Int,          // Maksymalna wysokość piku
        val peakIndex: Int,           // Pozycja (X) maksymalnej wysokości
        val meanIndex: Double,        // Obliczony środek ciężkości piku
        val variance: Double,         // Wariancja (miara "rozlania")
        val stdDev: Double,           // Odchylenie standardowe
        val totalMass: Double         // Całkowita "masa" piku (suma wysokości)
    )

    data class PeakCharacteristics(
        val height: Float,            // Wysokość piku w pikselach
        val width25: Float,           // Szerokość na 25% wysokości (jako % zakresu)
        val width50: Float,           // Szerokość na 50% wysokości (FWHM) (jako % zakresu)
        val width75: Float,           // Szerokość na 75% wysokości (jako % zakresu)
        val symmetry: Float,          // Symetria piku (0.0 - 1.0)
        val sharpness: Float,         // Stosunek wysokości do szerokości (width50)
        val position: Int             // Pozycja (indeks X) piku
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

    // --- GŁÓWNA LOGIKA EKSTRAKCJI ---

    /**
     * Główna funkcja wywoływana po uzyskaniu bitmapy z PdfChartExtractor.
     * Wypełnia wewnętrzne pola 'curveData' i 'fractionRanges'.
     * @param chartBitmap Bitmapa zawierająca WYŁĄCZNIE obszar wykresu.
     * @return Zwraca "this" dla wygody (chaining).
     */
    fun analyzeChartBitmap(chartBitmap: Bitmap): ElectrophoresisShapeAnalyzer {
        baselineY = findBaselineY(chartBitmap)
        // Ekstrakcja danych wykresu (niebieskich i czerwonych pikseli)
        curveData = extractCurveData(chartBitmap, baselineY)
        // Znalezienie dynamicznych granic frakcji na podstawie czerwonych linii
        fractionRanges = findFractionRanges(chartBitmap)
        return this
    }

    private fun findBaselineY(bitmap: Bitmap): Int {
        // TODO: Ulepszyć logikę znajdowania linii bazowej
        // Uproszczona implementacja: zakładamy, że linia bazowa jest 5% od dolnej krawędzi.
        val height = bitmap.height
        return (height - (height * 0.05f)).toInt().coerceAtMost(height - 2)
    }

    private fun isChartColor(pixelColor: Int): Boolean {
        val red = Color.red(pixelColor)
        val green = Color.green(pixelColor)
        val blue = Color.blue(pixelColor)
        // Logika: kolor jest "głównie niebieski" i nie jest ani czarny, ani biały.
        return blue > (red + 20) && blue > (green + 20) && blue > 50 && red < 200 && green < 200
    }

    /**
     * NOWA funkcja pomocnicza do wykrywania czerwonych linii podziału.
     */
    private fun isRedLineColor(pixelColor: Int): Boolean {
        val red = Color.red(pixelColor)
        val green = Color.green(pixelColor)
        val blue = Color.blue(pixelColor)
        // Logika: kolor jest "głównie czerwony"
        return red > 180 && green < 100 && blue < 100
    }

    /**
     * Wyodrębnia profil krzywej (niebieskie piksele) jako tablicę wysokości.
     * Zgodnie z sugestią, czerwone linie są traktowane jako część wykresu.
     */
    private fun extractCurveData(bitmap: Bitmap, baselineY: Int): IntArray {
        val width = bitmap.width
        val data = IntArray(width)

        for (x in 0 until width) {
            data[x] = 0 // Domyślnie brak piku
            // Skanuj kolumnę od góry (y=0) w dół do linii bazowej
            for (y in 0..baselineY) {
                val pixelColor = bitmap.getPixel(x, y)
                // Zliczamy *zarówno* niebieski wykres, jak i czerwone linie
                if (isChartColor(pixelColor) || isRedLineColor(pixelColor)) {
                    val height = baselineY - y
                    data[x] = height
                    break // Znaleźliśmy górną krawędź wykresu w tej kolumnie
                }
            }
        }
        return data
    }

    /**
     * NOWA LOGIKA
     * Skanuje obraz poziomo, aby znaleźć linię Y, która zawiera 3 czerwone
     * klastry pikseli, oddzielone o co najmniej MIN_SEPARATOR_GAP.
     */
    private fun findFractionRanges(bitmap: Bitmap): List<IntRange> {
        val width = bitmap.width
        val height = baselineY // Skanuj tylko do linii bazowej
        if (width == 0 || height == 0) return emptyList()

        // Skanuj każdą linię poziomą
        for (y in 0 until height) {
            val redClustersOnThisLine = mutableListOf<IntRange>()
            var inCluster = false
            var clusterStart = 0

            // Przejdź przez linię Y w poszukiwaniu czerwonych klastrów
            for (x in 0 until width) {
                val isRed = isRedLineColor(bitmap.getPixel(x, y))
                if (isRed && !inCluster) {
                    inCluster = true
                    clusterStart = x
                } else if (!isRed && inCluster) {
                    inCluster = false
                    redClustersOnThisLine.add(clusterStart until x) // (x-1) jest w 'until'
                }
            }
            // Zamknij ostatni klaster, jeśli linia kończy się na czerwono
            if (inCluster) {
                redClustersOnThisLine.add(clusterStart until width)
            }

            // Sprawdź, czy ta linia ma dokładnie 3 klastry z odpowiednimi odstępami
            if (redClustersOnThisLine.size == 3) {
                val c1 = redClustersOnThisLine[0]
                val c2 = redClustersOnThisLine[1]
                val c3 = redClustersOnThisLine[2]

                val gap1 = c2.first - c1.last
                val gap2 = c3.first - c2.last

                if (gap1 >= MIN_SEPARATOR_GAP && gap2 >= MIN_SEPARATOR_GAP) {
                    // Znaleźliśmy! Oblicz środki i zwróć zakresy.
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

        // Jeśli pętla się zakończy, nie znaleziono pasującej linii
        Log.e("ShapeAnalyzer", "Nie znaleziono linii z 3 czerwonymi separatorami. Analiza kształtu nie będzie dokładna.")
        return emptyList()
    }

    // --- FUNKCJE ANALIZY NUMERYCZNEJ (dla sekcji "Zaawansowana Analiza") ---

    /**
     * Analizuje kształt piku w rejonie gamma (dla sekcji PDF "Analiza Numeryczna").
     * Musi być wywołana PO `analyzeChartBitmap`.
     */
    fun analyzeGammaPeak(): GammaAnalysisResult? {
        if (fractionRanges.size < 4) return null // Nie udało się znaleźć granic

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

    /**
     * Zwraca obliczone AUC dla wszystkich frakcji (dla sekcji PDF "Analiza Numeryczna").
     * Musi być wywołana PO `analyzeChartBitmap`.
     */
    fun getFractionsAUC(): Map<String, Double> {
        if (fractionRanges.size < 4) return emptyMap() // Nie udało się znaleźć granic

        val totalAUC = calculateAUC(curveData, 0 until curveData.size)
        if (totalAUC == 0.0) return emptyMap()

        val albuminAUC = calculateAUC(curveData, fractionRanges[0])
        val alphaAUC = calculateAUC(curveData, fractionRanges[1])
        val betaAUC = calculateAUC(curveData, fractionRanges[2])
        val gammaAUC = calculateAUC(curveData, fractionRanges[3])

        // Zwracamy wartości procentowe
        return mapOf(
            "Albumin" to (albuminAUC / totalAUC) * 100.0,
            "Alpha" to (alphaAUC / totalAUC) * 100.0, // Złączone Alfa
            "Beta" to (betaAUC / totalAUC) * 100.0,
            "Gamma" to (gammaAUC / totalAUC) * 100.0,
            "TotalAUC_Pixels" to totalAUC
        )
    }

    // --- FUNKCJE ANALIZY KSZTAŁTU (dla "starej" oceny FIPShapeScore) ---

    /**
     * Główna funkcja do analizy kształtu na potrzeby oceny ryzyka FIP.
     * Musi być wywołana PO `analyzeChartBitmap`.
     */
    fun analyzeElectrophoresisShape(context: Context): ShapeAnalysisResult? {

        if (fractionRanges.size < 4) {
            Log.e("ShapeAnalyzer", "Brak granic frakcji. Wywołanie 'analyzeElectrophoresisShape' nie powiodło się.")
            return null
        }

        // 1. Przeanalizuj charakterystykę każdego piku używając dynamicznych zakresów
        val albuminPeak = analyzePeak(fractionRanges[0])
        val alphaPeak = analyzePeak(fractionRanges[1])
        val betaPeak = analyzePeak(fractionRanges[2])
        val gammaPeak = analyzePeak(fractionRanges[3])

        // 2. Przeanalizuj mostki
        val betaGammaBridge = analyzeBridge(fractionRanges[2], fractionRanges[3])
        val alphaBetaBridge = analyzeBridge(fractionRanges[1], fractionRanges[2])

        // 3. Sklasyfikuj wzorzec
        val internalPattern = classifyPattern(albuminPeak, alphaPeak, gammaPeak, betaGammaBridge)

        // 4. Oblicz wynik FIP
        val fipScore = calculateFIPShapeScore(
            albuminPeak, alphaPeak, gammaPeak,
            betaGammaBridge, internalPattern
        )

        // 5. Wygeneruj opis
        val description = generateShapeDescription(
            albuminPeak, gammaPeak,
            betaGammaBridge, internalPattern, fipScore, context
        )

        // 6. Przetłumacz nazwę wzorca na potrzeby UI
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

    /**
     * Zrefaktoryzowana funkcja analizująca pik na podstawie `curveData` i `IntRange`.
     * Oblicza szerokość na 3 poziomach: 25%, 50% (FWHM) i 75%.
     */
    private fun analyzePeak(range: IntRange): PeakCharacteristics {
        if (curveData.isEmpty() || range.first < 0 || range.last >= curveData.size || range.first >= range.last) {
            return PeakCharacteristics(0f, 0f, 0f, 0f, 0f, 0f, 0)
        }

        val profileSize = (range.last - range.first + 1).coerceAtLeast(1)
        var maxHeight = 0
        var maxIndex = range.first

        // Znajdź wysokość i pozycję piku w danym zakresie
        for (i in range) {
            if (curveData[i] > maxHeight) {
                maxHeight = curveData[i]
                maxIndex = i
            }
        }

        if (maxHeight == 0) {
            return PeakCharacteristics(0f, 0f, 0f, 0f, 0f, 0f, 0)
        }

        // Oblicz szerokości na 3 poziomach
        val height75 = maxHeight * 0.75
        val height50 = maxHeight * 0.50
        val height25 = maxHeight * 0.25

        var leftIndex75 = maxIndex
        var rightIndex75 = maxIndex
        var leftIndex50 = maxIndex
        var rightIndex50 = maxIndex
        var leftIndex25 = maxIndex
        var rightIndex25 = maxIndex

        // Skanuj w lewo od piku
        for (i in maxIndex downTo range.first) {
            val h = curveData[i]
            if (h >= height75) leftIndex75 = i
            if (h >= height50) leftIndex50 = i
            if (h >= height25) leftIndex25 = i
        }
        // Skanuj w prawo od piku
        for (i in maxIndex..range.last) {
            val h = curveData[i]
            if (h >= height75) rightIndex75 = i
            if (h >= height50) rightIndex50 = i
            if (h >= height25) rightIndex25 = i
        }

        val widthPx75 = (rightIndex75 - leftIndex75).coerceAtLeast(1)
        val widthPx50 = (rightIndex50 - leftIndex50).coerceAtLeast(1)
        val widthPx25 = (rightIndex25 - leftIndex25).coerceAtLeast(1)

        // Szerokość jako % całego zakresu danej frakcji
        val width75 = widthPx75.toFloat() / profileSize.toFloat()
        val width50 = widthPx50.toFloat() / profileSize.toFloat()
        val width25 = widthPx25.toFloat() / profileSize.toFloat()

        val symmetry = calculateSymmetry(range, maxIndex)
        val sharpness = if (width50 > 0.001f) maxHeight.toFloat() / width50 else 0f

        return PeakCharacteristics(
            height = maxHeight.toFloat(),
            width25 = width25,
            width50 = width50,
            width75 = width75,
            symmetry = symmetry,
            sharpness = sharpness,
            position = maxIndex
        )
    }

    /**
     * Zrefaktoryzowana funkcja do obliczania symetrii na podstawie `curveData`.
     */
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

    /**
     * Zrefaktoryzowana funkcja do analizy mostków na podstawie `curveData`.
     */
    private fun analyzeBridge(leftRange: IntRange, rightRange: IntRange): BridgeCharacteristics {
        // Określ "dolinę" jako mały obszar między dwoma zakresami
        // Używamy 5 pikseli lub mniej, jeśli zakresy są bardzo wąskie
        val valleyWidth = min(5, (leftRange.last - leftRange.first) / 10).coerceAtLeast(1)
        val valleyStart = (leftRange.last - valleyWidth).coerceAtLeast(0)
        val valleyEnd = (rightRange.first + valleyWidth).coerceAtMost(curveData.size - 1)

        if (curveData.isEmpty() || valleyStart >= valleyEnd) {
            return BridgeCharacteristics(false, 0f, 0f)
        }

        // Znajdź minimalną wysokość w dolinie
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

    // --- LOGIKA OCENY (bez zmian, ale zaktualizowana o nowe nazwy pól) ---

    private fun classifyPattern(
        albumin: PeakCharacteristics,
        alpha: PeakCharacteristics,
        gamma: PeakCharacteristics,
        betaGammaBridge: BridgeCharacteristics
    ): String {
        // Używamy 'sharpness', która jest teraz wysokością/szerokością
        if (gamma.sharpness > 200f && gamma.height > albumin.height * 1.5f) {
            return PATTERN_MONOCLONAL
        }
        // Używamy width50 (FWHM)
        if (gamma.width50 > 0.3f && gamma.height > albumin.height * 1.2f) {
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

        // Używamy width50 (FWHM)
        if (gamma.width50 > 0.35f) score += 15f
        else if (gamma.width50 > 0.25f) score += 10f

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

    // --- FUNKCJE POMOCNICZE ---

    private fun isWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 230 && g > 230 && b > 230
    }

    /**
     * Wewnętrzna funkcja do obliczania pola pod wykresem (AUC) dla danego zakresu danych
     * używając metody trapezów.
     * @param data Tablica z wysokościami wykresu.
     * @param range Zakres indeksów (X) do obliczeń.
     * @return Obliczone pole (AUC).
     */
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
}