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

    fun extractRawSignal(bitmap: Bitmap): List<PointF> {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val hsvMat = Mat()
        Imgproc.cvtColor(srcMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        // Bardziej tolerancyjne zakresy kolorów
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

        // Morfologia - zamknij małe dziury
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
        Imgproc.morphologyEx(finalMask, finalMask, Imgproc.MORPH_CLOSE, kernel)

        val signalPoints = scanColumnsForTopEdge(finalMask)

        srcMat.release()
        hsvMat.release()
        maskBlue.release()
        maskRed1.release()
        maskRed2.release()
        maskRed.release()
        finalMask.release()
        kernel.release()

        return signalPoints
    }

    private fun scanColumnsForTopEdge(mask: Mat): List<PointF> {
        val points = mutableListOf<PointF>()
        val height = mask.rows()
        val width = mask.cols()

        for (x in 0 until width) {
            var topY = -1

            // Szukamy pierwszego kolorowego piksela od góry
            for (y in 0 until height) {
                val pixelValue = mask.get(y, x)[0]
                if (pixelValue > 0) {
                    topY = y
                    break
                }
            }

            if (topY >= 0) {
                // Konwertuj do układu matematycznego (Y rośnie w górę)
                val heightFromBottom = (height - topY).toFloat()
                points.add(PointF(x.toFloat(), heightFromBottom))
            } else {
                // Jeśli brak piksela, interpoluj z sąsiadów
                if (points.isNotEmpty()) {
                    points.add(PointF(x.toFloat(), points.last().y))
                }
            }
        }

        Log.d("SignalExtractor", "Extracted ${points.size} points")
        Log.d("SignalExtractor", "Y range: ${points.minOfOrNull { it.y }} to ${points.maxOfOrNull { it.y }}")

        return points
    }

    // Medianowe wygładzanie - lepsze od średniej dla ostrych pików
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

    // Dodatkowe wygładzanie Savitzky-Golay (opcjonalne)
    fun savitzkyGolaySmooth(points: List<PointF>): List<PointF> {
        if (points.size < 5) return points

        val smoothed = mutableListOf<PointF>()

        // Współczynniki dla okna 5-punktowego, wielomian 2 stopnia
        val coeffs = listOf(-3f, 12f, 17f, 12f, -3f)
        val norm = coeffs.sum()

        for (i in 2 until points.size - 2) {
            var weightedSum = 0f
            for (j in -2..2) {
                weightedSum += points[i + j].y * coeffs[j + 2]
            }
            smoothed.add(PointF(points[i].x, weightedSum / norm))
        }

        // Dodaj brzegi bez wygładzania
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