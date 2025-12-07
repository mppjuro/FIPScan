package com.example.fipscan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExtractionTest {

    @Test
    fun testPdfExtraction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        val testContext = instrumentation.context
        val appContext = instrumentation.targetContext

        val inputStream = testContext.assets.open("5.pdf")
        val tempFile = File(appContext.cacheDir, "temp_test_chart.pdf")

        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        val extractor = PdfChartExtractor(appContext)
        val result = extractor.extractChartFromPDF(tempFile)

        assertNotNull("Ekstrakcja nie powiodła się - wynik jest null", result)
        assertNotNull("Nie wykryto sekcji słupków", result?.barSections)
        tempFile.delete()
    }
}