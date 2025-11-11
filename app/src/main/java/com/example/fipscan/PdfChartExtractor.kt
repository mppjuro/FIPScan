package com.example.fipscan

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

data class BarChartSectionHeights(
    val section1: List<Float>,
    val section2: List<Float>,
    val section3: List<Float>,
    val section4: List<Float>
)

// 1. ZMODYFIKOWANA DATA CLASS
data class ChartExtractionResult(
    val imagePaths: List<String>,
    val barSections: BarChartSectionHeights?,
    // NOWE POLA
    val gammaAnalysis: ElectrophoresisShapeAnalyzer.GammaAnalysisResult?,
    val aucMetrics: Map<String, Double>?
)

class PdfChartExtractor(private val context: Context) {
    private val topMarginPercent = 0.2
    private val paddingPercent = 0.01
    fun extractChartFromPDF(pdfFile: File?): ChartExtractionResult? {
        if (pdfFile == null || !pdfFile.exists()) {
            Log.e("PDF_PROCESSING", "Plik PDF nie istnieje")
            return null
        }

        try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            var bestMatchPageIndex = -1
            var maxBluePixels = 0
            val storageDir = context.getExternalFilesDir("Pictures")

            if (!storageDir?.exists()!!) storageDir.mkdirs()

            for (pageIndex in 0 until pdfRenderer.pageCount) {
                val bitmap = removeTopMargin(renderPage(pdfRenderer, pageIndex))
                val bluePixels = countBluePixels(bitmap)
                Log.d("COLOR_ANALYSIS", "Strona $pageIndex - Niebieskie piksele: $bluePixels")

                if (bluePixels > maxBluePixels) {
                    maxBluePixels = bluePixels
                    bestMatchPageIndex = pageIndex
                }
            }

            if (bestMatchPageIndex != -1) {
                val bestBitmap = removeTopMargin(renderPage(pdfRenderer, bestMatchPageIndex))
                val cropInfo = findCropInfo(bestBitmap)
                val paddedInfo = addPadding(cropInfo, bestBitmap)
                val finalBitmap = Bitmap.createBitmap(
                    bestBitmap,
                    paddedInfo.left,
                    paddedInfo.top,
                    max(1, paddedInfo.right - paddedInfo.left),
                    max(1, paddedInfo.bottom - paddedInfo.top)
                )

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val outputFile = File(storageDir, "chart_$timestamp.png")

                FileOutputStream(outputFile).use { fos ->
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                // --- 2. NOWA LOGIKA ANALIZY KSZTAŁTU ---
                Log.d("SHAPE_ANALYSIS", "Rozpoczęcie zaawansowanej analizy kształtu...")
                // Używamy 'finalBitmap' - to jest oryginalny, przycięty wykres
                // Wywołujemy metody z obiektu ElectrophoresisShapeAnalyzer
                val analyzer = ElectrophoresisShapeAnalyzer.analyzeChartBitmap(finalBitmap)
                val gammaAnalysis = analyzer.analyzeGammaPeak()
                val aucMetrics = analyzer.getFractionsAUC()

                if (gammaAnalysis != null && aucMetrics.isNotEmpty()) {
                    Log.d("SHAPE_ANALYSIS", "✅ Analiza numeryczna zakończona.")
                    Log.d("SHAPE_ANALYSIS", "Wariancja Gamma: ${gammaAnalysis.variance}")
                    Log.d("SHAPE_ANALYSIS", "AUC Gamma %: ${aucMetrics["Gamma"]}")
                } else {
                    Log.w("SHAPE_ANALYSIS", "⚠️ Analiza numeryczna nie zwróciła wyników.")
                }
                // --- KONIEC NOWEJ LOGIKI ---


                val croppedBitmap = cropAboveDominantLine(finalBitmap)
                val dominantColor = getDominantNonWhiteColor(croppedBitmap)
                val recoloredBitmap = replaceNonWhiteWithDominant(croppedBitmap, dominantColor)
                val barChartBitmap = convertToBarChartLike(recoloredBitmap)
                val barChartFile = File(storageDir, "bar_chart_$timestamp.png")

                FileOutputStream(barChartFile).use { fos ->
                    barChartBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }

                Log.d("IMAGE_SAVE", "✅ Zapisano wykres: ${outputFile.absolutePath}")
                Log.d("IMAGE_SIZE", "Wymiary obrazu: szerokość = ${finalBitmap.width}, wysokość = ${finalBitmap.height}")

                val analysisResult = BarChartLevelAnalyzer.analyzeBarHeights(barChartBitmap, finalBitmap)

                Log.d("BAR_LEVELS", "Poziomy słupków (% wysokości):")
                analysisResult.barHeights.forEachIndexed { index, value ->
                    Log.d("BAR_LEVELS", "Słupek ${index + 1}: %.2f%%".format(value))
                }

                Log.d("BAR_LEVELS", "Wykryte kolumny z czerwonymi pikselami (oddalone o ≥5% szerokości):")
                analysisResult.redColumnIndices.forEachIndexed { index, col ->
                    Log.d("BAR_LEVELS", "Kolumna ${index + 1}: $col")
                }

                val sectionHeights = convertToBarChartList(barChartBitmap, analysisResult.redColumnIndices)

                pdfRenderer.close()
                fileDescriptor.close()

                // 3. ZMODYFIKOWANY RETURN
                return ChartExtractionResult(
                    imagePaths = listOf(outputFile.absolutePath, barChartFile.absolutePath),
                    barSections = sectionHeights,
                    // Przekazanie nowych wyników
                    gammaAnalysis = gammaAnalysis,
                    aucMetrics = aucMetrics
                )
            }

            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            Log.e("PDF_PROCESSING", "Błąd przetwarzania PDF", e)
        }
        return null
    }

    data class CropInfo(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun convertToBarChartList(
        barChartBitmap: Bitmap,
        redColumnIndices: List<Int>
    ): BarChartSectionHeights {
        val height = barChartBitmap.height
        val width = barChartBitmap.width

        val sortedRedCols = redColumnIndices.sorted().distinct()

        if (sortedRedCols.size < 3) {
            Log.e("BAR_CHART_LIST", "Za mało kolumn podziału (potrzeba co najmniej 3), obecnie: ${sortedRedCols.size}")
            return BarChartSectionHeights(emptyList(), emptyList(), emptyList(), emptyList())
        }

        val boundaries = listOf(
            0 to sortedRedCols[0],
            sortedRedCols[0] to sortedRedCols[1],
            sortedRedCols[1] to sortedRedCols[2],
            sortedRedCols[2] to width
        )

        val numCols = listOf(7, 15, 5, 9)

        val sections = mutableListOf<List<Float>>()

        for ((range, colCount) in boundaries.zip(numCols)) {
            val (xStart, xEnd) = range
            val sectionWidth = xEnd - xStart
            val colWidth = sectionWidth / colCount

            val heights = mutableListOf<Float>()

            for (i in 0 until colCount) {
                val colXStart = xStart + i * colWidth
                val colXEnd = min(xStart + (i + 1) * colWidth, xEnd)

                var pixelsInColumn = 0

                for (x in colXStart until colXEnd) {
                    for (y in 0 until height) {
                        if (!isWhite(barChartBitmap.getPixel(x, y))) {
                            pixelsInColumn++
                        }
                    }
                }

                val averageHeight = pixelsInColumn.toFloat() / ((colXEnd - colXStart).coerceAtLeast(1))
                val heightPercent = (averageHeight / height) * 100f
                heights.add(heightPercent)
            }

            sections.add(heights)
        }
        return BarChartSectionHeights(
            section1 = sections[0],
            section2 = sections[1],
            section3 = sections[2],
            section4 = sections[3]
        )
    }

    fun findCropInfo(bitmap: Bitmap): CropInfo {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = 0
        var maxY = 0

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (isInBlueRange(bitmap.getPixel(x, y))) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        return CropInfo(minX, minY, maxX, maxY)
    }

    fun addPadding(cropInfo: CropInfo, bitmap: Bitmap): CropInfo {
        if (paddingPercent < 0.01) {
            return CropInfo(cropInfo.left, cropInfo.top, cropInfo.right, cropInfo.bottom)
        }
        val paddingX = ((cropInfo.right - cropInfo.left) * paddingPercent).toInt()
        val paddingY = ((cropInfo.bottom - cropInfo.top) * paddingPercent).toInt()

        val newLeft = max(0, cropInfo.left - paddingX)
        val newTop = max(0, cropInfo.top - paddingY)
        val newRight = min(bitmap.width, cropInfo.right + paddingX)
        val newBottom = min(bitmap.height, cropInfo.bottom + paddingY)

        return CropInfo(newLeft, newTop, newRight, newBottom)
    }

    fun renderPage(pdfRenderer: PdfRenderer, pageIndex: Int): Bitmap {
        val page = pdfRenderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    fun removeTopMargin(bitmap: Bitmap): Bitmap {
        val cutHeight = (bitmap.height * topMarginPercent).toInt()
        return Bitmap.createBitmap(bitmap, 0, cutHeight, bitmap.width, bitmap.height - cutHeight)
    }

    fun countBluePixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width step 5) {
            for (y in 0 until bitmap.height step 5) {
                if (isInBlueRange(bitmap.getPixel(x, y))) count++
            }
        }
        return count
    }

    private fun isInBlueRange(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (r in 70..130 && g in 119..179 && b in 207..255)
    }

    fun cropAboveDominantLine(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val width = bitmap.width

        var maxLineLength = 0
        var yOfMaxLine = 0

        for (y in 0 until height) {
            var lineLength = 0
            var currentLength = 0
            for (x in 0 until width) {
                val color = bitmap.getPixel(x, y)
                if (!isWhite(color)) {
                    currentLength++
                    if (currentLength > lineLength) {
                        lineLength = currentLength
                    }
                } else {
                    currentLength = 0
                }
            }

            if (lineLength > maxLineLength) {
                maxLineLength = lineLength
                yOfMaxLine = y
            }
        }

        // Przytnij obraz powyżej tej linii
        return Bitmap.createBitmap(bitmap, 0, 0, width, max(1, yOfMaxLine))
    }

    private fun isWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 240 && g > 240 && b > 240
    }

    fun getDominantNonWhiteColor(bitmap: Bitmap): Int {
        val colorCount = mutableMapOf<Int, Int>()

        for (x in 0 until bitmap.width step 5) {
            for (y in 0 until bitmap.height step 5) {
                val color = bitmap.getPixel(x, y)
                if (!isWhite(color)) {
                    colorCount[color] = colorCount.getOrDefault(color, 0) + 1
                }
            }
        }

        return colorCount.maxByOrNull { it.value }?.key ?: Color.BLUE // fallback: niebieski
    }

    fun replaceNonWhiteWithDominant(bitmap: Bitmap, targetColor: Int): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        for (x in 0 until result.width) {
            for (y in 0 until result.height) {
                val color = result.getPixel(x, y)
                if (!isAlmostPureWhite(color)) {
                    result.setPixel(x, y, targetColor)
                }
            }
        }
        return result
    }

    private fun isAlmostPureWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 250 && g > 250 && b > 250
    }

    fun convertToBarChartLike(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val n = max(1, width / 50) // liczba kolumn w grupie
        val paint = Paint().apply { color = Color.rgb(70, 130, 180) } // niebieski słupkowy

        for (xStart in 0 until width step n) {
            var pixelCount = 0

            for (x in xStart until min(xStart + n, width)) {
                for (y in 0 until height) {
                    val color = bitmap.getPixel(x, y)
                    if (!isAlmostPureWhite(color)) {
                        pixelCount++
                    }
                }
            }

            val averagePixelsPerColumn = pixelCount / n

            val xEnd = min(xStart + n, width)
            for (x in xStart until xEnd) {
                for (y in height - averagePixelsPerColumn until height) {
                    result.setPixel(x, y, paint.color)
                }
            }
        }

        return result
    }
}