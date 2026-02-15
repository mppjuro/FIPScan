package com.example.fipscan.utils

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class SignalExtractor {

    fun extractRawSignal(bitmap: Bitmap): List<PointF> {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val hsvMat = Mat()
        Imgproc.cvtColor(srcMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        val maskBlue = Mat()
        Core.inRange(hsvMat, Scalar(100.0, 50.0, 50.0), Scalar(130.0, 255.0, 255.0), maskBlue)

        val maskRed1 = Mat()
        Core.inRange(hsvMat, Scalar(0.0, 50.0, 50.0), Scalar(10.0, 255.0, 255.0), maskRed1)

        val maskRed2 = Mat()
        Core.inRange(hsvMat, Scalar(170.0, 50.0, 50.0), Scalar(180.0, 255.0, 255.0), maskRed2)

        val maskRed = Mat()
        Core.bitwise_or(maskRed1, maskRed2, maskRed)

        val finalMask = Mat()
        Core.bitwise_or(maskBlue, maskRed, finalMask)

        val signalPoints = scanColumnsForSignal(finalMask)

        srcMat.release()
        hsvMat.release()
        maskBlue.release()
        maskRed1.release()
        maskRed2.release()
        maskRed.release()
        finalMask.release()

        return signalPoints
    }

    private fun scanColumnsForSignal(mask: Mat): List<PointF> {
        val points = mutableListOf<PointF>()
        val height = mask.rows()
        val width = mask.cols()

        for (x in 0 until width) {
            var sumY = 0.0
            var count = 0

            for (y in 0 until height) {
                val pixelValue = mask.get(y, x)[0]
                if (pixelValue > 0) {
                    sumY += (height - y)
                    count++
                }
            }

            if (count > 0) {
                val avgY = sumY / count
                points.add(PointF(x.toFloat(), avgY.toFloat()))
            }
        }
        return points
    }
}