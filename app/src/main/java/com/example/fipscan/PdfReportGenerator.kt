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
    private val pageWidth = 595 // 595pt = 210mm (A4 width)
    private val pageHeight = 842 // 842pt = 297mm (A4 height)
    private val margin = 36f // 0.5 cala (36pt)
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
        patternAnalysis: FipPatternAnalyzer.PatternAnalysisResult? = null,
        shapeAnalysisPoints: Int = 0,
        patternAnalysisPoints: Int = 0,
        maxShapePoints: Int = 30,
        maxPatternPoints: Int = 30
    ): Pair<String?, String?> {

        val pdfDocument = PdfDocument()
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var yPosition = margin
        var pageNumber = 1

        try {
            // Pierwsza strona
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = currentPage.canvas

            // Nagłówek
            drawHeader(canvas, patientName)
            yPosition = 120f

            // Sekcja informacji o pacjencie
            yPosition = drawPatientInfo(
                canvas, yPosition, patientName, age, species,
                breed, gender, coat, collectionDate
            )

            // Sekcja oceny ryzyka FIP
            yPosition = drawRiskAssessment(
                canvas, yPosition, riskPercentage, riskComment
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

            // Sprawdź czy potrzebna nowa strona
            if (yPosition > pageHeight - bottomMargin - 200) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
                drawFooter(canvas, pageNumber)
            }

            // Szczegółowa analiza
            yPosition = drawScoreBreakdown(canvas, yPosition, scoreBreakdown, pdfDocument, pageNumber)

            // Sprawdź czy potrzebna nowa strona dla zaleceń
            if (yPosition > pageHeight - bottomMargin - 250) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
                drawFooter(canvas, pageNumber)
            }

            // Zalecenia
            yPosition = drawRecommendations(
                canvas, yPosition, furtherTestsAdvice,
                supplementAdvice, vetConsultationAdvice
            )

            // Sprawdź czy potrzebna nowa strona dla wyników
            if (yPosition > pageHeight - bottomMargin - 200) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
                drawFooter(canvas, pageNumber)
            }

            // Wyniki poza normą
            if (abnormalResults.isNotEmpty()) {
                if (yPosition > pageHeight - bottomMargin - 200) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = currentPage.canvas
                    yPosition = margin
                    drawFooter(canvas, pageNumber)
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
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        currentPage = pdfDocument.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                        canvas = currentPage.canvas
                        yPosition = margin
                        drawFooter(canvas, pageNumber)

                        canvas.drawText(context.getString(R.string.pdf_section_abnormal_results_cont), margin, yPosition, sectionPaint)
                        yPosition += 30f
                        canvas.drawText(context.getString(R.string.pdf_table_header), margin, yPosition, headerPaint)
                        yPosition += 20f
                        canvas.drawLine(margin, yPosition, pageWidth - margin, yPosition, linePaint)
                        yPosition += 10f
                    }

                    canvas?.drawText(result, margin, yPosition, resultPaint)
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
            currentPage?.let { pdfDocument.finishPage(it) }
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
        calendar.add(Calendar.HOUR_OF_DAY, 2)
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
        canvas.drawRoundRect(
            margin, y - 20f, pageWidth - margin, y + 120f,
            10f, 10f, boxPaint
        )

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
        canvas: Canvas, startY: Float, riskPercentage: Int, riskComment: String
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

        val bgPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 15f, 15f, bgPaint)

        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 15f, 15f, borderPaint)

        val fillColor = when {
            riskPercentage >= 75 -> dangerColor
            riskPercentage >= 50 -> warningColor
            riskPercentage >= 25 -> Color.parseColor("#FBC02D")
            else -> successColor
        }

        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
        }

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
        canvas: Canvas, startY: Float, breakdown: List<String>,
        pdfDocument: PdfDocument, currentPageNum: Int
    ): Float {
        var y = startY
        var pageNumber = currentPageNum
        var currentCanvas = canvas
        var currentPage: PdfDocument.Page? = null

        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        currentCanvas.drawText(context.getString(R.string.pdf_section_detailed_analysis), margin, y, sectionPaint)
        y += 30f

        val itemPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        for (item in breakdown) {
            if (y > pageHeight - bottomMargin - 100) {
                currentPage?.let { pdfDocument.finishPage(it) }
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                currentCanvas = currentPage.canvas
                y = margin
                drawFooter(currentCanvas, pageNumber)
            }

            val cleanItem = item.replace(Regex("<[^>]*>"), "")
                .replace("✅", "✓")
                .replace("❌", "✗")
                .replace("⚠️", "!")
                .replace("❓", "?")
                .replace("❔", "?")

            val layout = StaticLayout.Builder.obtain(
                cleanItem, 0, cleanItem.length, itemPaint, (contentWidth - 20f).toInt()
            ).build()

            currentCanvas.save()
            currentCanvas.translate(margin + 20f, y)
            layout.draw(currentCanvas)
            currentCanvas.restore()

            y += layout.height + 10f
        }

        return y + 20f
    }

    private fun drawRecommendations(
        canvas: Canvas, startY: Float, furtherTests: String,
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

        val titlePaint = TextPaint().apply {
            color = primaryColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        canvas.drawText(context.getString(R.string.pdf_rec_further_tests), margin, y, titlePaint)
        y += 20f

        val cleanTests = furtherTests.replace(Regex("<[^>]*>"), "")
        val testsLayout = StaticLayout.Builder.obtain(
            cleanTests, 0, cleanTests.length, textPaint, (contentWidth - 20f).toInt()
        ).build()

        canvas.save()
        canvas.translate(margin + 20f, y)
        testsLayout.draw(canvas)
        canvas.restore()
        y += testsLayout.height + 20f

        canvas.drawText(context.getString(R.string.pdf_rec_supplements), margin, y, titlePaint)
        y += 20f

        val cleanSupplements = supplements.replace(Regex("<[^>]*>"), "")
        val supplementsLayout = StaticLayout.Builder.obtain(
            cleanSupplements, 0, cleanSupplements.length, textPaint, (contentWidth - 20f).toInt()
        ).build()

        canvas.save()
        canvas.translate(margin + 20f, y)
        supplementsLayout.draw(canvas)
        canvas.restore()
        y += supplementsLayout.height + 20f

        canvas.drawText(context.getString(R.string.pdf_rec_consultation), margin, y, titlePaint)
        y += 20f

        val cleanConsultation = consultation.replace(Regex("<[^>]*>"), "")
        val consultationLayout = StaticLayout.Builder.obtain(
            cleanConsultation, 0, cleanConsultation.length, textPaint, (contentWidth - 20f).toInt()
        ).build()

        canvas.save()
        canvas.translate(margin + 20f, y)
        consultationLayout.draw(canvas)
        canvas.restore()

        return y + consultationLayout.height + 30f
    }

    private fun drawFooter(canvas: Canvas?, pageNumber: Int) {
        val footerPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }

        val year = Calendar.getInstance().get(Calendar.YEAR)
        val footerText = context.getString(R.string.pdf_footer_page, pageNumber, year)
        val footerWidth = footerPaint.measureText(footerText)
        canvas?.drawText(footerText, (pageWidth - footerWidth) / 2, pageHeight - 20f, footerPaint)

        val disclaimerPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }

        val disclaimer = context.getString(R.string.pdf_footer_disclaimer)
        val disclaimerWidth = disclaimerPaint.measureText(disclaimer)
        canvas?.drawText(disclaimer, (pageWidth - disclaimerWidth) / 2, pageHeight - 35f, disclaimerPaint)
    }

    fun savePdfToDownloads(pdfDocument: PdfDocument, fileName: String): Pair<String?, String?> {
        return try {
            val localDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            localDir?.mkdirs()
            val localFile = File(localDir, "$fileName.pdf")

            FileOutputStream(localFile).use { output ->
                pdfDocument.writeTo(output)
            }

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Cannot create file in MediaStore")

            FileInputStream(localFile).use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            Pair("$fileName.pdf", localFile.absolutePath)
        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "Error saving PDF", e)
            Pair(null, null)
        }
    }

    private fun drawShapeAnalysisSection(
        canvas: Canvas,
        startY: Float,
        analysis: ElectrophoresisShapeAnalyzer.ShapeAnalysisResult,
        points: Int,
        maxPoints: Int
    ): Float {
        var y = startY

        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_shape_analysis), margin, y, sectionPaint)
        y += 40f

        val scoreText = context.getString(R.string.pdf_score_format, points, maxPoints)
        val scorePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f

        val barHeight = 20f
        val barWidth = contentWidth
        val percentage = (analysis.fipShapeScore).toInt()

        val bgPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 10f, 10f, bgPaint)

        val fillColor = when {
            percentage >= 70 -> Color.parseColor("#F44336")
            percentage >= 50 -> Color.parseColor("#FF9800")
            percentage >= 30 -> Color.parseColor("#FFC107")
            else -> Color.parseColor("#4CAF50")
        }

        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
        }

        val fillWidth = (barWidth * percentage / 100f)
        canvas.drawRoundRect(margin, y, margin + fillWidth, y + barHeight, 10f, 10f, fillPaint)

        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("$percentage%", margin + 10f, y + 14f, percentPaint)

        y += barHeight + 20f

        val detailsPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val bridgeStatus = if (analysis.betaGammaBridge.present)
            context.getString(R.string.pdf_bridge_present)
        else
            context.getString(R.string.pdf_bridge_absent)

        val details = buildString {
            append(context.getString(R.string.pdf_shape_pattern, analysis.overallPattern)).append("\n")
            append(context.getString(R.string.pdf_shape_ag_ratio, String.format(Locale.getDefault(), "%.2f", analysis.albumin.height / analysis.gamma.height))).append("\n")
            append(context.getString(R.string.pdf_shape_gamma_width, (analysis.gamma.width * 100).toInt())).append("\n")
            append(context.getString(R.string.pdf_shape_bridge, bridgeStatus))
        }

        val detailsLayout = StaticLayout.Builder.obtain(
            details, 0, details.length, detailsPaint, contentWidth.toInt()
        ).build()

        canvas.save()
        canvas.translate(margin, y)
        detailsLayout.draw(canvas)
        canvas.restore()

        return y + detailsLayout.height + 30f
    }

    private fun drawPatternAnalysisSection(
        canvas: Canvas,
        startY: Float,
        analysis: FipPatternAnalyzer.PatternAnalysisResult,
        points: Int,
        maxPoints: Int
    ): Float {
        var y = startY

        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_section_pattern_analysis), margin, y, sectionPaint)
        y += 30f

        val scoreText = context.getString(R.string.pdf_score_format, points, maxPoints)
        val scorePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f

        val barHeight = 20f
        val percentage = analysis.patternStrength.toInt()

        val bgPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + barHeight, 10f, 10f, bgPaint)

        val fillColor = when {
            percentage >= 70 -> Color.parseColor("#F44336")
            percentage >= 50 -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#4CAF50")
        }

        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
        }

        val fillWidth = (contentWidth * percentage / 100f)
        canvas.drawRoundRect(margin, y, margin + fillWidth, y + barHeight, 10f, 10f, fillPaint)

        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
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
        val profileName = context.getString(profileNameResId)

        val profilePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(context.getString(R.string.pdf_profile_label, profileName), margin, y, profilePaint)
        y += 25f

        if (analysis.keyFindings.isNotEmpty()) {
            val findingsPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 11f
                isAntiAlias = true
            }

            canvas.drawText(context.getString(R.string.pdf_key_findings), margin, y, findingsPaint)
            y += 20f

            for (finding in analysis.keyFindings.take(3)) {
                val cleanFinding = finding.replace(Regex("[✅❌⚠️🔸❓]"), "•")
                canvas.drawText(cleanFinding, margin + 10f, y, findingsPaint)
                y += 18f
            }
        }

        return y + 30f
    }
}