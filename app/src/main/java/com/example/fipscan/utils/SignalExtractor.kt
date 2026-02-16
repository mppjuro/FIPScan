package com.example.fipscan.utils

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

class SignalExtractor {

    data class SignalData(
        val points: List<PointF>,
        val baseline: Float
    )

    fun extractRawSignal(bitmap: Bitmap): SignalData {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val hsvMat = Mat()
        Imgproc.cvtColor(srcMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        val maskBlue = Mat()
        Core.inRange(hsvMat, Scalar(90.0, 40.0, 40.0), Scalar(140.0, 255.0, 255.0), maskBlue)

        val maskRed1 = Mat()
        Core.inRange(hsvMat, Scalar(0.0, 40.0, 40.0), Scalar(15.0, 255.0, 255.0), maskRed1)

        val maskRed2 = Mat()
        Core.inRange(hsvMat, Scalar(165.0, 40.0, 40.0), Scalar(180.0, 255.0, 255.0), maskRed2)

        val maskRed = Mat()
        Core.bitwise_or(maskRed1, maskRed2, maskRed)

        val finalMask = Mat()
        Core.bitwise_or(maskBlue, maskRed, finalMask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
        Imgproc.morphologyEx(finalMask, finalMask, Imgproc.MORPH_CLOSE, kernel)

        val (points, baseline) = scanColumnsForTopEdge(finalMask)

        srcMat.release()
        hsvMat.release()
        maskBlue.release()
        maskRed1.release()
        maskRed2.release()
        maskRed.release()
        finalMask.release()
        kernel.release()

        return SignalData(points, baseline)
    }

    private fun scanColumnsForTopEdge(mask: Mat): Pair<List<PointF>, Float> {
        val points = mutableListOf<PointF>()
        val height = mask.rows()
        val width = mask.cols()

        for (x in 0 until width) {
            var topY = -1

            for (y in 0 until height) {
                val pixelValue = mask.get(y, x)[0]
                if (pixelValue > 0) {
                    topY = y
                    break
                }
            }

            if (topY >= 0) {
                val heightFromBottom = (height - topY).toFloat()
                points.add(PointF(x.toFloat(), heightFromBottom))
            } else {
                if (points.isNotEmpty()) {
                    points.add(PointF(x.toFloat(), points.last().y))
                }
            }
        }

        // Wykryj linię bazową - weź 5% najniższych wartości
        val sortedY = points.map { it.y }.sorted()
        val bottomPercentile = sortedY.take((sortedY.size * 0.05).toInt().coerceAtLeast(1))
        val baseline = if (bottomPercentile.isNotEmpty()) {
            bottomPercentile.average().toFloat()
        } else {
            0f
        }

        Log.d("SignalExtractor", "Extracted ${points.size} points")
        Log.d("SignalExtractor", "Y range: ${points.minOfOrNull { it.y }} to ${points.maxOfOrNull { it.y }}")
        Log.d("SignalExtractor", "Detected baseline: $baseline")

        return Pair(points, baseline)
    }

    fun smoothSignal(points: List<PointF>, windowSize: Int = 5): List<PointF> {
        if (points.size < windowSize) return points

        val smoothed = mutableListOf<PointF>()
        val halfWindow = windowSize / 2

        for (i in points.indices) {
            val start = max(0, i - halfWindow)
            val end = min(points.size - 1, i + halfWindow)

            val windowValues = (start..end).map { points[it].y }.sorted()
            val medianY = windowValues[windowValues.size / 2]

            smoothed.add(PointF(points[i].x, medianY))
        }

        Log.d("SignalExtractor", "Smoothed with median filter, window=$windowSize")
        return smoothed
    }

    fun savitzkyGolaySmooth(points: List<PointF>): List<PointF> {
        if (points.size < 5) return points

        val smoothed = mutableListOf<PointF>()

        val coeffs = listOf(-3f, 12f, 17f, 12f, -3f)
        val norm = coeffs.sum()

        for (i in 2 until points.size - 2) {
            var weightedSum = 0f
            for (j in -2..2) {
                weightedSum += points[i + j].y * coeffs[j + 2]
            }
            smoothed.add(PointF(points[i].x, weightedSum / norm))
        }

        if (points.size >= 5) {
            smoothed.add(0, points[0])
            smoothed.add(1, points[1])
            smoothed.add(points[points.size - 2])
            smoothed.add(points[points.size - 1])
        }

        Log.d("SignalExtractor", "Applied Savitzky-Golay smoothing")
        return smoothed
    }
}