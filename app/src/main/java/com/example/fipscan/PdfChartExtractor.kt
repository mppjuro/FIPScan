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

class PdfChartExtractor(private val context: Context) {
    private val TARGET_COLOR = Color.rgb(100, 149, 237)
    private val COLOR_TOLERANCE = 70
    private val BLACK_THRESHOLD = 40
    private val TARGET_PIXELS_COUNT = 2
    private val TOP_MARGIN_PERCENT = 0.2
    private val PADDING_PERCENT = 0.01

    fun extractChartFromPDF(pdfFile: File?): String? {
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

                Log.d("IMAGE_SAVE", "✅ Zapisano wykres: ${outputFile.absolutePath}")
                Log.d("IMAGE_SIZE", "Wymiary obrazu: szerokość = ${finalBitmap.width}, wysokość = ${finalBitmap.height}")

                pdfRenderer.close()
                fileDescriptor.close()
                return outputFile.absolutePath
            }

            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            Log.e("PDF_PROCESSING", "Błąd przetwarzania PDF", e)
        }
        return null
    }

    private data class CropInfo(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun findCropInfo(bitmap: Bitmap): CropInfo {
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

    private fun addPadding(cropInfo: CropInfo, bitmap: Bitmap): CropInfo {
        if (PADDING_PERCENT < 0.01) {
            return CropInfo(cropInfo.left, cropInfo.top, cropInfo.right, cropInfo.bottom)
        }
        val paddingX = ((cropInfo.right - cropInfo.left) * PADDING_PERCENT).toInt()
        val paddingY = ((cropInfo.bottom - cropInfo.top) * PADDING_PERCENT).toInt()

        val newLeft = max(0, cropInfo.left - paddingX)
        val newTop = max(0, cropInfo.top - paddingY)
        val newRight = min(bitmap.width, cropInfo.right + paddingX)
        val newBottom = min(bitmap.height, cropInfo.bottom + paddingY)

        return CropInfo(newLeft, newTop, newRight, newBottom)
    }

    private fun renderPage(pdfRenderer: PdfRenderer, pageIndex: Int): Bitmap {
        val page = pdfRenderer.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    private fun removeTopMargin(bitmap: Bitmap): Bitmap {
        val cutHeight = (bitmap.height * TOP_MARGIN_PERCENT).toInt()
        return Bitmap.createBitmap(bitmap, 0, cutHeight, bitmap.width, bitmap.height - cutHeight)
    }

    private fun countBluePixels(bitmap: Bitmap): Int {
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
}
