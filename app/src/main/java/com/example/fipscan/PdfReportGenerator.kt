package com.example.fipscan

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f
    private val bottomMargin = 70f
    private val contentWidth = pageWidth - (2 * margin)

    private val primaryColor = Color.parseColor("#1976D2")
    private val dangerColor = Color.parseColor("#D32F2F")
    private val warningColor = Color.parseColor("#FFA000")
    private val successColor = Color.parseColor("#388E3C")
    private val backgroundColor = Color.parseColor("#F9F9F9")

    fun generateReport(
        patientName: String,
        age: String,
        species: String?,
        breed: String?,
        gender: String?,
        coat: String?,
        collectionDate: String?,
        riskPercentage: Int,
        riskComment: String,
        scoreBreakdown: List<String>,
        diagnosticComment: String,
        supplementAdvice: String,
        vetConsultationAdvice: String,
        furtherTestsAdvice: String,
        abnormalResults: List<String>,
        gammopathyResult: String?,
        shapeAnalysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult? = null,
        gammaAnalysisDetails: ElectrophoresisShapeAnalyzer.GammaAnalysisResult? = null,
        aucMetrics: Map<String, Double>? = null,
        widthRatios: ElectrophoresisShapeAnalyzer.WidthRatioAnalysis? = null, // <-- DODANY PARAMETR
        patternAnalysis: FipPatternAnalyzer.PatternAnalysisResult? = null,
        shapeAnalysisPoints: Int = 0,
        patternAnalysisPoints: Int = 0,
        maxShapePoints: Int = 30,
        maxPatternPoints: Int = 30
    ): Pair<String?, String?> {
        val pdfDocument = PdfDocument()
        var currentPage: PdfDocument.Page?
        var canvas: Canvas
        var yPosition: Float
        var pageNumber = 1

        try {
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = currentPage.canvas

            drawHeader(canvas, patientName)
            yPosition = 120f

            yPosition = drawPatientInfo(
                canvas, yPosition, patientName, age, species,
                breed, gender, coat, collectionDate
            )

            // Dodano gammopathyResult do oceny ryzyka
            yPosition = drawRiskAssessment(
                canvas, yPosition, riskPercentage, riskComment, gammopathyResult
            )

            if (shapeAnalysis != null) {
                yPosition = drawShapeAnalysisSection(
                    canvas, yPosition, shapeAnalysis,
                    shapeAnalysisPoints, maxShapePoints
                )
            }

            if (patternAnalysis != null) {
                yPosition = drawPatternAnalysisSection(
                    canvas, yPosition, patternAnalysis,
                    patternAnalysisPoints, maxPatternPoints
                )
            }

            // SEKCJA DLA AUC I WARIANCJI
            if (gammaAnalysisDetails != null || aucMetrics != null || widthRatios != null) { // <-- ZMODYFIKOWANY WARUNEK
                if (yPosition > pageHeight - bottomMargin - 200) { // Zwiększono margines sprawdzania miejsca
                    drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = currentPage.canvas
                    yPosition = margin
                }
                yPosition = drawAdvancedAnalysisSection(
                    canvas, yPosition,
                    gammaAnalysisDetails,
                    aucMetrics,
                    widthRatios
                )
            }

            if (yPosition > pageHeight - bottomMargin - 200) {
                drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            yPosition = drawScoreBreakdown(canvas, yPosition, scoreBreakdown)

            if (yPosition > pageHeight - bottomMargin - 250) {
                drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            // Dodano diagnosticComment przed zaleceniami
            yPosition = drawRecommendations(
                canvas, yPosition, diagnosticComment, furtherTestsAdvice,
                supplementAdvice, vetConsultationAdvice
            )

            if (yPosition > pageHeight - bottomMargin - 200) {
                drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            if (abnormalResults.isNotEmpty()) {
                if (yPosition > pageHeight - bottomMargin - 200) {
                    drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = currentPage.canvas
                    yPosition = margin
                }

                val sectionPaint = TextPaint().apply {
                    color = primaryColor
                    textSize = 16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText(context.getString(R.string.pdf_section_abnormal_results), margin, yPosition, sectionPaint)
                yPosition += 30f

                val resultPaint = TextPaint().apply {
                    color = Color.BLACK
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    isAntiAlias = true
                }

                val headerPaint = TextPaint(resultPaint).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                canvas.drawText(context.getString(R.string.pdf_table_header), margin, yPosition, headerPaint)
                yPosition += 20f

                val linePaint = Paint().apply {
                    color = Color.GRAY
                    strokeWidth = 1f
                }
                canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, linePaint)
                yPosition += 10f

                for (result in abnormalResults) {
                    if (yPosition > pageHeight - bottomMargin - 30) {
                        drawFooter(canvas, pageNumber) // Dodajemy stopkę przed zamknięciem
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        currentPage = pdfDocument.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                        canvas = currentPage.canvas
                        yPosition = margin

                        canvas.drawText(context.getString(R.string.pdf_section_abnormal_results_cont), margin, yPosition, sectionPaint)
                        yPosition += 30f
                        canvas.drawText(context.getString(R.string.pdf_table_header), margin, yPosition, headerPaint)
                        yPosition += 20f
                        canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, linePaint)
                        yPosition += 10f
                    }

                    canvas.drawText(result, margin, yPosition, resultPaint)
                    yPosition += 15f
                }
                yPosition += 20f
            }

            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(currentPage)

            val prefix = context.getString(R.string.pdf_filename_prefix)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}${patientName}_$timeStamp"

            return savePdfToDownloads(pdfDocument, fileName)

        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "Error generating PDF", e)
            return Pair(null, null)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawHeader(canvas: Canvas, patientName: String) {
        val paint = Paint().apply {
            color = Color.parseColor("#1A237E")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)

        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val title = context.getString(R.string.pdf_report_title)
        val titleWidth = titlePaint.measureText(title)
        canvas.drawText(title, (pageWidth - titleWidth) / 2, 40f, titlePaint)

        val subtitlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 16f
            isAntiAlias = true
        }
        val subtitle = context.getString(R.string.pdf_patient_prefix, patientName)
        val subtitleWidth = subtitlePaint.measureText(subtitle)
        canvas.drawText(subtitle, (pageWidth - subtitleWidth) / 2, 70f, subtitlePaint)

        val datePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 10f
            isAntiAlias = true
        }
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(calendar.time)
        val generatedText = context.getString(R.string.pdf_generated_date, dateStr)
        canvas.drawText(generatedText, margin, 90f, datePaint)
    }

    private fun drawPatientInfo(
        canvas: Canvas, startY: Float, name: String, age: String,
        species: String?, breed: String?, gender: String?, coat: String?, date: String?
    ): Float {
        var y = startY
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_patient_data), margin, y, sectionPaint)
        y += 30f

        val boxPaint = Paint().apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(margin, y - 20f, pageWidth - margin, y + 120f, 10f, 10f, boxPaint)

        val infoPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val boldPaint = TextPaint(infoPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val leftColumn = margin + 20f
        val rightColumn = pageWidth / 2f
        val notProvided = context.getString(R.string.pdf_value_not_provided)

        canvas.drawText(context.getString(R.string.pdf_label_name), leftColumn, y, boldPaint)
        canvas.drawText(name, leftColumn + 100f, y, infoPaint)
        canvas.drawText(context.getString(R.string.pdf_label_species), rightColumn, y, boldPaint)
        canvas.drawText(species ?: notProvided, rightColumn + 100f, y, infoPaint)
        y += 25f

        canvas.drawText(context.getString(R.string.pdf_label_age), leftColumn, y, boldPaint)
        canvas.drawText(age, leftColumn + 100f, y, infoPaint)
        canvas.drawText(context.getString(R.string.pdf_label_breed), rightColumn, y, boldPaint)
        canvas.drawText(breed ?: notProvided, rightColumn + 100f, y, infoPaint)
        y += 25f

        canvas.drawText(context.getString(R.string.pdf_label_gender), leftColumn, y, boldPaint)
        canvas.drawText(gender ?: notProvided, leftColumn + 100f, y, infoPaint)
        canvas.drawText(context.getString(R.string.pdf_label_coat), rightColumn, y, boldPaint)
        canvas.drawText(coat ?: notProvided, rightColumn + 100f, y, infoPaint)
        y += 25f

        canvas.drawText(context.getString(R.string.pdf_label_date), leftColumn, y, boldPaint)
        canvas.drawText(date ?: context.getString(R.string.pdf_value_no_date), leftColumn + 100f, y, infoPaint)

        return y + 40f
    }

    private fun drawRiskAssessment(
        canvas: Canvas, startY: Float, riskPercentage: Int, riskComment: String, gammopathyResult: String?
    ): Float {
        var y = startY
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_risk_assessment), margin, y, sectionPaint)
        y += 35f

        val barHeight = 40f
        val barWidth = contentWidth
        val bgPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 15f, 15f, bgPaint)

        val fillColor = when {
            riskPercentage >= 75 -> dangerColor
            riskPercentage >= 50 -> warningColor
            riskPercentage >= 25 -> Color.parseColor("#FBC02D")
            else -> successColor
        }
        val fillPaint = Paint().apply { color = fillColor; style = Paint.Style.FILL }
        val fillWidth = (barWidth * riskPercentage / 100f)
        canvas.drawRoundRect(margin, y, margin + fillWidth, y + barHeight, 15f, 15f, fillPaint)

        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("$riskPercentage%", margin + 25f, y + 26f, percentPaint)
        y += barHeight + 25f

        // Wyświetlanie gammapatii jeśli jest dostępna
        if (!gammopathyResult.isNullOrEmpty()) {
            val gammopathyPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val label = try { context.getString(R.string.pdf_gammopathy_label) } catch (e: Exception) { "Gammapatia:" }
            canvas.drawText("$label $gammopathyResult", margin, y, gammopathyPaint)
            y += 20f
        }

        val commentPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val cleanComment = riskComment.replace(Regex("<[^>]*>"), "")
        val commentLayout = StaticLayout.Builder.obtain(
            cleanComment, 0, cleanComment.length, commentPaint, contentWidth.toInt()
        ).build()

        canvas.save()
        canvas.translate(margin, y)
        commentLayout.draw(canvas)
        canvas.restore()

        return y + commentLayout.height + 30f
    }

    private fun drawScoreBreakdown(
        canvas: Canvas, startY: Float, breakdown: List<String>
    ): Float {
        var y = startY
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_detailed_analysis), margin, y, sectionPaint)
        y += 30f

        val itemPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }

        for (item in breakdown) {
            val cleanItem = item.replace(Regex("<[^>]*>"), "").replace("✅", "✓").replace("❌", "✗").replace("⚠️", "!").replace("?", "?")
            val layout = StaticLayout.Builder.obtain(cleanItem, 0, cleanItem.length, itemPaint, (contentWidth - 20f).toInt()).build()
            canvas.save()
            canvas.translate(margin + 20f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 10f
        }
        return y + 20f
    }

    private fun drawRecommendations(
        canvas: Canvas, startY: Float, diagnosticComment: String, furtherTests: String,
        supplements: String, consultation: String
    ): Float {
        var y = startY
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_recommendations), margin, y, sectionPaint)
        y += 30f

        val textPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }

        // Dodano diagnosticComment
        if (diagnosticComment.isNotEmpty()) {
            val cleanDiagnostic = diagnosticComment.replace(Regex("<[^>]*>"), "")
            val diagnosticLayout = StaticLayout.Builder.obtain(
                cleanDiagnostic, 0, cleanDiagnostic.length, textPaint, contentWidth.toInt()
            ).build()
            canvas.save()
            canvas.translate(margin, y)
            diagnosticLayout.draw(canvas)
            canvas.restore()
            y += diagnosticLayout.height + 25f
        }

        val titlePaint = TextPaint().apply {
            color = primaryColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText(context.getString(R.string.pdf_rec_further_tests), margin, y, titlePaint)
        y += 20f
        val cleanTests = furtherTests.replace(Regex("<[^>]*>"), "")
        val testsLayout = StaticLayout.Builder.obtain(cleanTests, 0, cleanTests.length, textPaint, (contentWidth - 20f).toInt()).build()
        canvas.save()
        canvas.translate(margin + 20f, y)
        testsLayout.draw(canvas)
        canvas.restore()
        y += testsLayout.height + 20f

        canvas.drawText(context.getString(R.string.pdf_rec_supplements), margin, y, titlePaint)
        y += 20f
        val cleanSupplements = supplements.replace(Regex("<[^>]*>"), "")
        val supplementsLayout = StaticLayout.Builder.obtain(cleanSupplements, 0, cleanSupplements.length, textPaint, (contentWidth - 20f).toInt()).build()
        canvas.save()
        canvas.translate(margin + 20f, y)
        supplementsLayout.draw(canvas)
        canvas.restore()
        y += supplementsLayout.height + 20f

        canvas.drawText(context.getString(R.string.pdf_rec_consultation), margin, y, titlePaint)
        y += 20f
        val cleanConsultation = consultation.replace(Regex("<[^>]*>"), "")
        val consultationLayout = StaticLayout.Builder.obtain(cleanConsultation, 0, cleanConsultation.length, textPaint, (contentWidth - 20f).toInt()).build()
        canvas.save()
        canvas.translate(margin + 20f, y)
        consultationLayout.draw(canvas)
        canvas.restore()

        return y + consultationLayout.height + 30f
    }

    private fun drawAdvancedAnalysisSection(
        canvas: Canvas, startY: Float,
        gammaAnalysis: ElectrophoresisShapeAnalyzer.GammaAnalysisResult?,
        aucMetrics: Map<String, Double>?,
        widthRatios: ElectrophoresisShapeAnalyzer.WidthRatioAnalysis?
    ): Float {
        var y = startY
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val title = context.getString(R.string.pdf_advanced_chart_analysis_title)
        canvas.drawText(title, margin, y, sectionPaint)
        y += 30f

        val detailsPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val boldDetailsPaint = TextPaint(detailsPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // 1. Rysowanie wszystkich danych analizy piku Gamma (Używa stringów)
        if (gammaAnalysis != null && gammaAnalysis.totalMass > 0) {

            val varianceLabel = context.getString(R.string.pdf_gamma_peak_variance)
            val varianceValue = String.format(Locale.getDefault(), "%.2f", gammaAnalysis.variance)
            canvas.drawText("$varianceLabel $varianceValue", margin + 10f, y, detailsPaint)
            y += 15f

            val stdDevLabel = context.getString(R.string.pdf_gamma_peak_std_dev)
            val stdDevValue = String.format(Locale.getDefault(), "%.2f", gammaAnalysis.stdDev)
            canvas.drawText("$stdDevLabel $stdDevValue", margin + 10f, y, detailsPaint)
            y += 15f

            val meanIndexLabel = context.getString(R.string.pdf_gamma_peak_mean_index)
            val meanIndexValue = String.format(Locale.getDefault(), "%.1f", gammaAnalysis.meanIndex)
            canvas.drawText("$meanIndexLabel $meanIndexValue", margin + 10f, y, detailsPaint)
            y += 15f

            val peakHeightLabel = context.getString(R.string.pdf_gamma_peak_height)
            val peakHeightValue = "${gammaAnalysis.peakHeight}"
            canvas.drawText("$peakHeightLabel $peakHeightValue", margin + 10f, y, detailsPaint)

            y += 20f // Dodatkowy odstęp
        }

        // 2. Rysowanie danych AUC z tłumaczeniem (Używa stringów)
        if (aucMetrics != null && aucMetrics.isNotEmpty()) {
            canvas.drawText(context.getString(R.string.pdf_calculated_fractions_title), margin, y, boldDetailsPaint)
            y += 18f

            aucMetrics.forEach { (fractionKey, percentage) ->
                if (fractionKey != "TotalAUC_Pixels") {
                    val translatedName = getTranslatedFractionName(fractionKey)
                    val formattedLine = "$translatedName: ${String.format(Locale.getDefault(), "%.1f", percentage)} %"
                    canvas.drawText(formattedLine, margin + 20f, y, detailsPaint)
                    y += 15f
                }
            }
        }

        // 3. Rysowanie proporcji szerokości
        if (widthRatios != null) {
            canvas.drawText(context.getString(R.string.pdf_width_ratios_title), margin, y, boldDetailsPaint)
            y += 18f

            val locale = Locale.getDefault()
            val val70b = String.format(locale, "%.1f", widthRatios.gamma70ToBeta)
            val val50b = String.format(locale, "%.1f", widthRatios.gamma50ToBeta)
            val val30b = String.format(locale, "%.1f", widthRatios.gamma30ToBeta)
            val val7030 = String.format(locale, "%.1f", widthRatios.gamma70ToGamma30)

            canvas.drawText(context.getString(R.string.pdf_ratio_g70_beta, val70b), margin + 20f, y, detailsPaint)
            y += 15f
            canvas.drawText(context.getString(R.string.pdf_ratio_g50_beta, val50b), margin + 20f, y, detailsPaint)
            y += 15f
            canvas.drawText(context.getString(R.string.pdf_ratio_g30_beta, val30b), margin + 20f, y, detailsPaint)
            y += 15f
            canvas.drawText(context.getString(R.string.pdf_ratio_g70_g30, val7030), margin + 20f, y, detailsPaint)
            y += 15f
        }
        return y + 20f
    }


    private fun drawFooter(canvas: Canvas?, pageNumber: Int) {
        val footerPaint = TextPaint().apply { color = Color.GRAY; textSize = 10f; isAntiAlias = true }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val footerText = context.getString(R.string.pdf_footer_page, pageNumber, year)
        val footerWidth = footerPaint.measureText(footerText)
        canvas?.drawText(footerText, (pageWidth - footerWidth) / 2, pageHeight - 20f, footerPaint)
        val disclaimerPaint = TextPaint().apply { color = Color.GRAY; textSize = 8f; isAntiAlias = true }
        val disclaimer = context.getString(R.string.pdf_footer_disclaimer)
        val disclaimerWidth = disclaimerPaint.measureText(disclaimer)
        canvas?.drawText(disclaimer, (pageWidth - disclaimerWidth) / 2, pageHeight - 35f, disclaimerPaint)
    }

    // --- ZAKTUALIZOWANA FUNKCJA POMOCNICZA ---
    private fun getTranslatedFractionName(fractionKey: String): String {
        // Ta funkcja mapuje klucze ("Albumin", "Alpha") na zasoby string
        val resId = when (fractionKey) {
            "Albumin" -> R.string.pdf_fraction_albumin
            "Alpha"   -> R.string.pdf_fraction_alpha1 // Używamy stringa "Alpha-1" dla połączonej frakcji "Alpha"
            "Beta"    -> R.string.pdf_fraction_beta
            "Gamma"   -> R.string.pdf_fraction_gamma
            else -> 0
        }
        return if (resId != 0) context.getString(resId) else fractionKey
    }
    // --- KONIEC ZAKTUALIZOWANEJ FUNKCJI ---

    private fun savePdfToDownloads(pdfDocument: PdfDocument, fileName: String): Pair<String?, String?> {
        return try {
            val localDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            localDir?.mkdirs()
            val localFile = File(localDir, "$fileName.pdf")
            FileOutputStream(localFile).use { pdfDocument.writeTo(it) }
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: throw Exception("Cannot create file")
            FileInputStream(localFile).use { input -> resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) } }
            Pair("$fileName.pdf", localFile.absolutePath)
        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "Error saving PDF", e)
            Pair(null, null)
        }
    }

    private fun drawShapeAnalysisSection(canvas: Canvas, startY: Float, analysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult, points: Int, maxPoints: Int): Float {
        var y = startY
        val sectionPaint = TextPaint().apply { color = primaryColor; textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText(context.getString(R.string.pdf_section_shape_analysis), margin, y, sectionPaint)
        y += 40f
        val scoreText = context.getString(R.string.pdf_score_format, points, maxPoints)
        val scorePaint = TextPaint().apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f
        val barHeight = 20f; val barWidth = contentWidth; val percentage = (analysis.fipShapeScore).toInt()
        val bgPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 10f, 10f, bgPaint)
        val fillColor = when { percentage >= 70 -> Color.parseColor("#F44336"); percentage >= 50 -> Color.parseColor("#FF9800"); percentage >= 30 -> Color.parseColor("#FFC107"); else -> Color.parseColor("#4CAF50") }
        val fillPaint = Paint().apply { color = fillColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, y, margin + (barWidth * percentage / 100f), y + barHeight, 10f, 10f, fillPaint)
        val percentPaint = TextPaint().apply { color = Color.WHITE; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText("$percentage%", margin + 10f, y + 14f, percentPaint)
        y += barHeight + 20f
        val detailsPaint = TextPaint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true }
        val bridgeStatus = if (analysis.betaGammaBridge.present) context.getString(R.string.pdf_bridge_present) else context.getString(R.string.pdf_bridge_absent)


        // --- POCZĄTEK POPRAWKI BŁĘDU (naprawa width50) ---
        // Obliczamy procentową szerokość gamma (FWHM) względem jej zakresu
        val gammaWidthPx50 = analysis.gamma.widthPxMap[50]?.toFloat() ?: 0f
        // Zabezpieczenie przed dzieleniem przez zero, jeśli zakres ma 0px
        val gammaRangeSize = analysis.gamma.rangeSize.toFloat().coerceAtLeast(1f)
        val gammaWidth50Percent = (gammaWidthPx50 / gammaRangeSize) * 100f

        // Zabezpieczenie przed dzieleniem przez zero dla A/G Ratio
        val agRatioValue = if (analysis.gamma.height > 0.01f) {
            analysis.albumin.height / analysis.gamma.height
        } else {
            0.0f // Zwracamy 0.0 lub inną domyślną wartość, gdy gamma = 0
        }

        val details = buildString {
            append(context.getString(R.string.pdf_shape_pattern, analysis.overallPattern)).append("\n")
            append(context.getString(R.string.pdf_shape_ag_ratio, String.format(Locale.getDefault(), "%.2f", agRatioValue))).append("\n")
            append(context.getString(R.string.pdf_shape_gamma_width, gammaWidth50Percent.toInt())).append("\n")
            append(context.getString(R.string.pdf_shape_bridge, bridgeStatus))
        }

        val detailsLayout = StaticLayout.Builder.obtain(details, 0, details.length, detailsPaint, contentWidth.toInt()).build()
        canvas.save(); canvas.translate(margin, y); detailsLayout.draw(canvas); canvas.restore()
        return y + detailsLayout.height + 30f
    }


    private fun drawPatternAnalysisSection(canvas: Canvas, startY: Float, analysis: FipPatternAnalyzer.PatternAnalysisResult, points: Int, maxPoints: Int): Float {
        var y = startY
        val sectionPaint = TextPaint().apply { color = primaryColor; textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText(context.getString(R.string.pdf_section_pattern_analysis), margin, y, sectionPaint)
        y += 30f
        val scoreText = context.getString(R.string.pdf_score_format, points, maxPoints)
        val scorePaint = TextPaint().apply { color = Color.BLACK; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f
        val barHeight = 20f; val percentage = analysis.patternStrength.toInt()
        val bgPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 10f, 10f, bgPaint)
        val fillColor = when { percentage >= 70 -> Color.parseColor("#F44336"); percentage >= 50 -> Color.parseColor("#FF9800"); else -> Color.parseColor("#4CAF50") }
        val fillPaint = Paint().apply { color = fillColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(margin, y, margin + (contentWidth * percentage / 100f), y + barHeight, 10f, 10f, fillPaint)
        val percentPaint = TextPaint().apply { color = Color.WHITE; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText("$percentage%", margin + 10f, y + 14f, percentPaint)
        y += barHeight + 20f
        val profileNameResId = when (analysis.primaryProfile) {
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_ACUTE -> R.string.pdf_profile_acute
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_CHRONIC -> R.string.pdf_profile_chronic
            FipPatternAnalyzer.FipProfile.EFFUSIVE_CLASSIC -> R.string.pdf_profile_effusive
            FipPatternAnalyzer.FipProfile.DRY_NEUROLOGICAL -> R.string.pdf_profile_neuro
            FipPatternAnalyzer.FipProfile.MIXED_PATTERN -> R.string.pdf_profile_mixed
            FipPatternAnalyzer.FipProfile.ATYPICAL -> R.string.pdf_profile_atypical
            FipPatternAnalyzer.FipProfile.NON_FIP -> R.string.pdf_profile_non_fip
        }
        val profilePaint = TextPaint().apply { color = Color.BLACK; textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        canvas.drawText(context.getString(R.string.pdf_profile_label, context.getString(profileNameResId)), margin, y, profilePaint)
        y += 25f
        if (analysis.keyFindings.isNotEmpty()) {
            val findingsPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
            canvas.drawText(context.getString(R.string.pdf_key_findings), margin, y, findingsPaint)
            y += 20f
            for (finding in analysis.keyFindings.take(3)) {
                canvas.drawText(finding.replace(Regex("[✅❌⚠️🔸❓]"), "•"), margin + 10f, y, findingsPaint)
                y += 18f
            }
        }
        return y + 30f
    }
}