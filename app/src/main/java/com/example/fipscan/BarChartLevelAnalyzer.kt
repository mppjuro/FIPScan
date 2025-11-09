package com.example.fipscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

object BarChartLevelAnalyzer {

    data class AnalysisResult(
        val barHeights: List<Double>,
        val imageWidth: Int,
        val imageHeight: Int,
        val redColumnIndices: List<Int>
    )

    fun analyzeBarHeights(bitmap: Bitmap, origBitmap: Bitmap): AnalysisResult {
        val width = bitmap.width
        val height = bitmap.height
        val segmentWidth = max(1, width / 50)

        val barHeights = mutableListOf<Double>()

        for (xStart in 0 until width step segmentWidth) {
            val xEnd = min(xStart + segmentWidth, width)
            var totalBarPixels = 0

            for (x in xStart until xEnd) {
                for (y in 0 until height) {
                    val color = bitmap.getPixel(x, y)
                    if (isBarPixel(color)) {
                        totalBarPixels++
                    }
                }
            }

            val pixelsPerColumn = (xEnd - xStart).coerceAtLeast(1)
            val avgBarHeight = totalBarPixels.toDouble() / pixelsPerColumn
            val percentage = avgBarHeight / height.toDouble() * 100.0

            barHeights.add(percentage)
        }

        val redPixelCounts = IntArray(origBitmap.width) { x ->
            (0 until origBitmap.height).count { y -> isRedPixel(origBitmap.getPixel(x, y)) }
        }

        val sortedIndices = redPixelCounts.withIndex()
            .sortedByDescending { it.value }
            .map { it.index }

        val selectedColumns = mutableListOf<Int>()
        val minDistance = (width * 0.05).toInt()

        for (index in sortedIndices) {
            if (selectedColumns.all { abs(it - index) >= minDistance }) {
                selectedColumns.add(index)
                if (selectedColumns.size == 3) break
            }
        }

        return AnalysisResult(
            barHeights = barHeights,
            imageWidth = origBitmap.width,
            imageHeight = origBitmap.height,
            redColumnIndices = selectedColumns.sorted()
        )
    }

    private fun isBarPixel(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r < 250 || g < 250 || b < 250
    }

    private fun isRedPixel(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r > 200 && g < 100 && b < 100
    }

    // Dodano parametr context: Context
    fun analyzeGammapathy(section1: List<Float>, section4: List<Float>, context: Context): String {
        val sum1 = section1.sum()
        val sum4 = section4.sum()
        val ratioThreshold = sum1 * 9f / 7f

        if (sum4 <= ratioThreshold) {
            Log.d("GAMMOPATHY_ANALYSIS", "Suma gamma ($sum4) <= próg ($ratioThreshold) - brak gammapatii")
            return context.getString(R.string.gammopathy_none)
        }

        // Sprawdź, czy jest ostry pik (monoklonalna)
        for (i in section4.indices) {
            val current = section4[i]
            val left = if (i > 0) section4[i - 1] else 0f
            val right = if (i < section4.size - 1) section4[i + 1] else 0f

            val leftDiff = if (left > 0f) current / left else Float.MAX_VALUE
            val rightDiff = if (right > 0f) current / right else Float.MAX_VALUE

            if (leftDiff > 2f && rightDiff > 2f) {
                Log.d("GAMMOPATHY_ANALYSIS", "Wykryto ostry pik - gammapatia monoklonalna")
                return context.getString(R.string.gammopathy_monoclonal)
            }

            // Sprawdź sąsiednie pary
            if (i < section4.size - 1) {
                val next = section4[i + 1]
                val avgSurrounding = listOfNotNull(
                    section4.getOrNull(i - 1),
                    section4.getOrNull(i + 2)
                ).average().toFloat()
                val peakAvg = (current + next) / 2f

                if (avgSurrounding > 0f && peakAvg > avgSurrounding * 2f) {
                    Log.d("GAMMOPATHY_ANALYSIS", "Wykryto lokalny pik - gammapatia monoklonalna")
                    return context.getString(R.string.gammopathy_monoclonal)
                }
            }
        }

        // Jeśli nie ma ostrych pików, ale jest szerokie podniesienie – poliklonalna
        Log.d("GAMMOPATHY_ANALYSIS", "Szerokie podniesienie bez ostrych pików - gammapatia poliklonalna")
        return context.getString(R.string.gammopathy_polyclonal)
    }

}