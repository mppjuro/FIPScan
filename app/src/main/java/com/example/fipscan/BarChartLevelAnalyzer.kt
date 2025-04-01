package com.example.fipscan

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min

object BarChartLevelAnalyzer {

    fun analyzeBarHeights(bitmap: Bitmap): List<Double> {
        val width = bitmap.width
        val height = bitmap.height
        val segmentWidth = max(1, width / 50)

        val result = mutableListOf<Double>()

        for (xStart in 0 until width step segmentWidth) {
            val xEnd = min(xStart + segmentWidth, width)
            var totalBarPixels = 0

            // Skanuj każdą kolumnę (x) w danym słupku
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

            result.add(percentage)
        }

        return result
    }

    private fun isBarPixel(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return r < 250 || g < 250 || b < 250
    }
}
