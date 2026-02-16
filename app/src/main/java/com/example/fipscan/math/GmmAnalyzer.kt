package com.example.fipscan.math

import android.graphics.PointF
import android.util.Log
import org.apache.commons.math3.fitting.GaussianCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoint

class GmmAnalyzer {

    fun fitGaussianCurves(
        rawPoints: List<PointF>,
        separators: List<Int>,
        maxX: Int,
        baseline: Float
    ): List<GaussianComponent> {
        val components = mutableListOf<GaussianComponent>()
        val boundaries = mutableListOf(0)
        boundaries.addAll(separators.sorted())
        boundaries.add(maxX)

        Log.d("GmmAnalyzer", "Baseline for fitting: $baseline")

        for (i in 0 until boundaries.size - 1) {
            val startX = boundaries[i]
            val endX = boundaries[i + 1]

            val regionPoints = rawPoints.filter { point ->
                point.x >= startX && point.x < endX
            }

            Log.d("GmmAnalyzer", "Region $i: ${regionPoints.size} points, X=[$startX, $endX]")

            if (regionPoints.size > 10) {
                val component = fitSingleRegionRobust(regionPoints, i, startX, endX, baseline)
                if (component != null) {
                    components.add(component)
                    Log.d("GmmAnalyzer", "✓ Region $i: A=${component.amplitude.toInt()}, μ=${component.mean.toInt()}, σ=${component.sigma.toInt()}")
                } else {
                    Log.w("GmmAnalyzer", "✗ Region $i: Failed to fit")
                }
            }
        }
        return components
    }

    private fun fitSingleRegionRobust(
        points: List<PointF>,
        regionIndex: Int,
        startX: Int,
        endX: Int,
        baseline: Float
    ): GaussianComponent? {
        try {
            if (points.size < 10) {
                Log.w("GmmAnalyzer", "Region $regionIndex: too few points")
                return null
            }

            // Odejmij baseline od wszystkich punktów
            val adjustedPoints = points.map {
                PointF(it.x, (it.y - baseline).coerceAtLeast(0f))
            }

            val minY = adjustedPoints.minOf { it.y }.toDouble()
            val maxY = adjustedPoints.maxOf { it.y }.toDouble()
            val rangeY = maxY - minY

            if (rangeY < 5) {
                Log.w("GmmAnalyzer", "Region $regionIndex: Y range too small ($rangeY) after baseline removal")
                return null
            }

            // Normalizacja do [0, 1]
            val normalizedPoints = adjustedPoints.map {
                WeightedObservedPoint(
                    1.0,
                    it.x.toDouble(),
                    (it.y - minY) / rangeY
                )
            }

            val maxPoint = adjustedPoints.maxByOrNull { it.y }!!
            val peakX = maxPoint.x.toDouble()

            // Oblicz FWHM
            val halfMax = (maxY + minY) / 2.0
            val pointsAboveHalf = adjustedPoints.filter { it.y >= halfMax }

            val fwhm = if (pointsAboveHalf.size >= 2) {
                (pointsAboveHalf.last().x - pointsAboveHalf.first().x).toDouble()
            } else {
                (endX - startX) / 3.0
            }

            val estimatedSigma = (fwhm / 2.355).coerceIn(5.0, (endX - startX) / 2.0)

            Log.d("GmmAnalyzer", "Region $regionIndex init: peak=$peakX, FWHM=$fwhm, sigma=$estimatedSigma")

            val fitter = GaussianCurveFitter.create()
                .withMaxIterations(500)
                .withStartPoint(doubleArrayOf(
                    1.0,
                    peakX,
                    estimatedSigma
                ))

            val params = fitter.fit(normalizedPoints)

            // Denormalizacja
            val actualAmplitude = params[0] * rangeY + minY
            val actualMean = params[1]
            val actualSigma = params[2]

            // Walidacja
            if (actualAmplitude <= 0 || actualSigma <= 0) {
                Log.w("GmmAnalyzer", "Region $regionIndex: invalid params")
                return null
            }

            if (actualMean < startX || actualMean > endX) {
                Log.w("GmmAnalyzer", "Region $regionIndex: mean outside bounds")
                return null
            }

            val quality = calculateFitQuality(adjustedPoints, actualAmplitude, actualMean, actualSigma)
            Log.d("GmmAnalyzer", "Region $regionIndex fit quality: R²=${"%.3f".format(quality)}")

            return GaussianComponent(
                amplitude = actualAmplitude,
                mean = actualMean,
                sigma = actualSigma
            )

        } catch (e: Exception) {
            Log.e("GmmAnalyzer", "Region $regionIndex error: ${e.message}")
            return null
        }
    }

    private fun calculateFitQuality(
        points: List<PointF>,
        amplitude: Double,
        mean: Double,
        sigma: Double
    ): Double {
        val component = GaussianComponent(amplitude, mean, sigma)

        val yMean = points.map { it.y }.average()
        var ssTotal = 0.0
        var ssResidual = 0.0

        for (point in points) {
            val predicted = component.getValueAt(point.x.toDouble())
            ssResidual += (point.y - predicted) * (point.y - predicted)
            ssTotal += (point.y - yMean) * (point.y - yMean)
        }

        return if (ssTotal > 0) 1.0 - (ssResidual / ssTotal) else 0.0
    }
}