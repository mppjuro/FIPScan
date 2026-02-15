package com.example.fipscan.math

import android.graphics.PointF
import org.apache.commons.math3.fitting.GaussianCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoint

class GmmAnalyzer {

    fun fitGaussianCurves(
        rawPoints: List<PointF>,
        separators: List<Int>,
        maxX: Int
    ): List<GaussianComponent> {
        val components = mutableListOf<GaussianComponent>()
        val boundaries = mutableListOf(0)
        boundaries.addAll(separators.sorted())
        boundaries.add(maxX)

        for (i in 0 until boundaries.size - 1) {
            val startX = boundaries[i]
            val endX = boundaries[i + 1]

            val regionPoints = rawPoints.filter { point ->
                point.x >= startX && point.x < endX
            }

            if (regionPoints.size > 5) {
                val component = fitSingleRegion(regionPoints)
                if (component != null) {
                    components.add(component)
                }
            }
        }
        return components
    }

    private fun fitSingleRegion(points: List<PointF>): GaussianComponent? {
        return try {
            val observations = points.map { point ->
                WeightedObservedPoint(1.0, point.x.toDouble(), point.y.toDouble())
            }

            val maxPoint = points.maxByOrNull { it.y } ?: return null
            val approximateSigma = (points.last().x - points.first().x) / 6.0

            val fitter = GaussianCurveFitter.create()
                .withMaxIterations(1000)
                .withStartPoint(doubleArrayOf(
                    maxPoint.y.toDouble(),
                    maxPoint.x.toDouble(),
                    approximateSigma.toDouble()
                ))

            val params = fitter.fit(observations)

            GaussianComponent(
                amplitude = params[0],
                mean = params[1],
                sigma = params[2]
            )
        } catch (e: Exception) {
            null
        }
    }
}