package com.example.fipscan

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfReportGenerator(private val context: Context) {

    private val pageWidth = 595 // A4 width in points
    private val pageHeight = 842 // A4 height in points
    private val margin = 50f
    private val contentWidth = pageWidth - (2 * margin)

    // Kolory
    private val primaryColor = Color.parseColor("#FF018786")
    private val dangerColor = Color.parseColor("#D32F2F")
    private val warningColor = Color.parseColor("#FFA000")
    private val successColor = Color.parseColor("#388E3C")
    private val backgroundColor = Color.parseColor("#F5F5F5")

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
            if (yPosition > pageHeight - 200) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            // Szczegółowa analiza
            yPosition = drawScoreBreakdown(canvas, yPosition, scoreBreakdown, pdfDocument, pageNumber)

            // Sprawdź czy potrzebna nowa strona dla zaleceń
            if (yPosition > pageHeight - 250) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            // Zalecenia
            yPosition = drawRecommendations(
                canvas, yPosition, furtherTestsAdvice,
                supplementAdvice, vetConsultationAdvice
            )

            // Sprawdź czy potrzebna nowa strona dla wyników
            if (yPosition > pageHeight - 200) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = currentPage.canvas
                yPosition = margin
            }

            // Wyniki poza normą
            if (abnormalResults.isNotEmpty()) {
                yPosition = drawAbnormalResults(canvas, yPosition, abnormalResults, pdfDocument, pageNumber)
            }

            // Stopka na ostatniej stronie
            drawFooter(canvas, pageNumber)

            pdfDocument.finishPage(currentPage)

            // Zapisz PDF
            val fileName = "FIP_Raport_${patientName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            return savePdfToDownloads(pdfDocument, fileName)

        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "Błąd generowania PDF", e)
            currentPage?.let { pdfDocument.finishPage(it) }
            return Pair(null, null)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawHeader(canvas: Canvas, patientName: String) {
        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
        }

        // Tło nagłówka
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)

        // Tytuł
        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val title = "RAPORT DIAGNOSTYCZNY FIP"
        val titleWidth = titlePaint.measureText(title)
        canvas.drawText(title, (pageWidth - titleWidth) / 2, 40f, titlePaint)

        // Podtytuł
        val subtitlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 16f
            isAntiAlias = true
        }

        val subtitle = "Pacjent: $patientName"
        val subtitleWidth = subtitlePaint.measureText(subtitle)
        canvas.drawText(subtitle, (pageWidth - subtitleWidth) / 2, 70f, subtitlePaint)

        // Data generowania z GMT+2
        val datePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 10f
            isAntiAlias = true
        }

        // Dodaj 2 godziny do aktualnego czasu
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, 2)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = dateFormat.format(calendar.time)

        canvas.drawText("Wygenerowano: $date (GMT+2)", margin, 90f, datePaint)
    }

    private fun drawPatientInfo(
        canvas: Canvas, startY: Float, name: String, age: String,
        species: String?, breed: String?, gender: String?, coat: String?, date: String?
    ): Float {
        var y = startY

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DANE PACJENTA", margin, y, sectionPaint)
        y += 30f

        // Ramka dla informacji
        val boxPaint = Paint().apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            margin, y - 20f, pageWidth - margin, y + 120f,
            10f, 10f, boxPaint
        )

        // Tekst informacji
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

        canvas.drawText("Imię:", leftColumn, y, boldPaint)
        canvas.drawText(name, leftColumn + 80f, y, infoPaint)

        canvas.drawText("Gatunek:", rightColumn, y, boldPaint)
        canvas.drawText(species ?: "nie podano", rightColumn + 80f, y, infoPaint)
        y += 25f

        canvas.drawText("Wiek:", leftColumn, y, boldPaint)
        canvas.drawText(age, leftColumn + 80f, y, infoPaint)

        canvas.drawText("Rasa:", rightColumn, y, boldPaint)
        canvas.drawText(breed ?: "nie podano", rightColumn + 80f, y, infoPaint)
        y += 25f

        canvas.drawText("Płeć:", leftColumn, y, boldPaint)
        canvas.drawText(gender ?: "nie podano", leftColumn + 80f, y, infoPaint)

        canvas.drawText("Umaszczenie:", rightColumn, y, boldPaint)
        canvas.drawText(coat ?: "nie podano", rightColumn + 80f, y, infoPaint)
        y += 25f

        canvas.drawText("Data badania:", leftColumn, y, boldPaint)
        canvas.drawText(date ?: "brak daty", leftColumn + 80f, y, infoPaint)

        return y + 40f
    }

    private fun drawRiskAssessment(
        canvas: Canvas, startY: Float, riskPercentage: Int, riskComment: String
    ): Float {
        var y = startY

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 22f  // Większy tytuł dla głównej oceny
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("📊 GŁÓWNA OCENA RYZYKA FIP", margin, y, sectionPaint)
        y += 35f

        // WIĘKSZY PASEK POSTĘPU dla głównego wyniku
        val barHeight = 50f  // Zwiększona wysokość
        val barWidth = contentWidth

        // Tło paska z ramką
        val bgPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            margin, y, pageWidth - margin, y + barHeight,
            15f, 15f, bgPaint
        )

        // Ramka dla lepszej widoczności
        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(
            margin, y, pageWidth - margin, y + barHeight,
            15f, 15f, borderPaint
        )

        // Wypełnienie paska
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
        canvas.drawRoundRect(
            margin, y, margin + fillWidth, y + barHeight,
            15f, 15f, fillPaint
        )

        // Większy tekst procentowy
        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 24f  // Większa czcionka
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val percentText = "$riskPercentage%"
        canvas.drawText(percentText, margin + 25f, y + 32f, percentPaint)

        y += barHeight + 25f

        // Komentarz do ryzyka
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

        return y// + commentLayout.height + 30f
    }

    private fun drawScoreBreakdown(
        canvas: Canvas, startY: Float, breakdown: List<String>,
        pdfDocument: PdfDocument, currentPageNum: Int
    ): Float {
        var y = startY
        var pageNumber = currentPageNum
        var currentCanvas = canvas
        var currentPage: PdfDocument.Page? = null

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        currentCanvas.drawText("SZCZEGÓŁOWA ANALIZA", margin, y, sectionPaint)
        y += 30f

        val itemPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        for (item in breakdown) {
            // Sprawdź czy potrzebna nowa strona
            if (y > pageHeight - 100) {
                currentPage?.let { pdfDocument.finishPage(it) }
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                currentCanvas = currentPage.canvas
                y = margin
            }

            val cleanItem = item.replace(Regex("<[^>]*>"), "")
                .replace("✅", "✓")
                .replace("❌", "✗")
                .replace("⚠️", "!")
                .replace("❓", "?")
                .replace("❔", "?")

            // Rysuj element z wcięciem
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

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("ZALECENIA", margin, y, sectionPaint)
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

        // Dalsze badania
        canvas.drawText("🔬 Dalsze badania:", margin, y, titlePaint)
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

        // Suplementy
        canvas.drawText("💊 Suplementy:", margin, y, titlePaint)
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

        // Konsultacja
        canvas.drawText("🏥 Konsultacja:", margin, y, titlePaint)
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

    private fun drawAbnormalResults(
        canvas: Canvas, startY: Float, results: List<String>,
        pdfDocument: PdfDocument, currentPageNum: Int
    ): Float {
        var y = startY
        var pageNumber = currentPageNum
        var currentCanvas = canvas
        var currentPage: PdfDocument.Page? = null

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Check if we need a new page immediately for the title
        if (y > pageHeight - 50) {
            pdfDocument.finishPage(currentPage)  // Finish current page first
            pageNumber++
            currentPage = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            currentCanvas = currentPage.canvas
            y = margin
        }

        currentCanvas.drawText("WYNIKI POZA NORMĄ", margin, y, sectionPaint)
        y += 30f

        val resultPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        // Nagłówek tabeli
        val headerPaint = TextPaint(resultPaint).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        currentCanvas.drawText("Badanie | Wynik | Norma", margin, y, headerPaint)
        y += 20f

        // Linia separująca
        val linePaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 1f
        }
        currentCanvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 10f

        for (result in results) {
            // Check if we need a new page
            if (y > pageHeight - 50) {
                currentPage?.let { pdfDocument.finishPage(it) }  // Finish current page
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                currentCanvas = currentPage.canvas
                y = margin
            }

            currentCanvas.drawText(result, margin, y, resultPaint)
            y += 15f
        }

        // Finish the last page if we created any new pages
        currentPage?.let {
            if (it !== currentPage) {  // Only finish if it's a different page
                pdfDocument.finishPage(it)
            }
        }

        return y + 20f
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }

        val footerText = "Strona $pageNumber | FIPscan - Raport diagnostyczny"
        val footerWidth = footerPaint.measureText(footerText)
        canvas.drawText(
            footerText,
            (pageWidth - footerWidth) / 2,
            pageHeight - 20f,
            footerPaint
        )

        // Disclaimer
        val disclaimerPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }

        val disclaimer = "Raport ma charakter pomocniczy. Ostateczną diagnozę ustala lekarz weterynarii."
        val disclaimerWidth = disclaimerPaint.measureText(disclaimer)
        canvas.drawText(
            disclaimer,
            (pageWidth - disclaimerWidth) / 2,
            pageHeight - 35f,
            disclaimerPaint
        )
    }

    fun savePdfToDownloads(pdfDocument: PdfDocument, fileName: String): Pair<String?, String?> {
        return try {
            // Najpierw zapisz lokalnie dla FTP
            val localDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            localDir?.mkdirs()
            val localFile = File(localDir, "$fileName.pdf")

            FileOutputStream(localFile).use { output ->
                pdfDocument.writeTo(output)
            }

            // Teraz zapisz do Downloads dla użytkownika
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Nie można utworzyć pliku w MediaStore")

            // Zapisz ponownie do Downloads
            FileInputStream(localFile).use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            Pair("$fileName.pdf", localFile.absolutePath)
        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "Błąd zapisu PDF", e)
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

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("📈 ANALIZA KSZTAŁTU KRZYWEJ ELEKTROFOREZY", margin, y, sectionPaint)
        y += 30f

        // Punkty i pasek postępu
        val scoreText = "Wynik: $points/$maxPoints punktów"
        val scorePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f

        // Pasek postępu dla analizy kształtu
        val barHeight = 25f
        val barWidth = contentWidth
        val percentage = (analysis.fipShapeScore).toInt()

        // Tło paska
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            margin, y, pageWidth - margin, y + barHeight,
            10f, 10f, bgPaint
        )

        // Wypełnienie
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
        canvas.drawRoundRect(
            margin, y, margin + fillWidth, y + barHeight,
            10f, 10f, fillPaint
        )

        // Procent na pasku
        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("$percentage%", margin + 10f, y + 17f, percentPaint)

        y += barHeight + 20f

        // Szczegóły analizy
        val detailsPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val details = """
            Wzorzec: ${analysis.overallPattern}
            Stosunek albumin/gamma: ${String.format("%.2f", analysis.albumin.height / analysis.gamma.height)}
            Szerokość piku gamma: ${(analysis.gamma.width * 100).toInt()}%
            Mostek beta-gamma: ${if (analysis.betaGammaBridge.present) "obecny" else "nieobecny"}
        """.trimIndent()

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

        // Tytuł sekcji
        val sectionPaint = TextPaint().apply {
            color = primaryColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("🔬 PROFIL WZORCÓW LABORATORYJNYCH", margin, y, sectionPaint)
        y += 30f

        // Punkty
        val scoreText = "Wynik: $points/$maxPoints punktów"
        val scorePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(scoreText, margin, y, scorePaint)
        y += 25f

        // Pasek postępu
        val barHeight = 25f
        val percentage = analysis.patternStrength.toInt()

        // Tło paska
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            margin, y, pageWidth - margin, y + barHeight,
            10f, 10f, bgPaint
        )

        // Wypełnienie
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
        canvas.drawRoundRect(
            margin, y, margin + fillWidth, y + barHeight,
            10f, 10f, fillPaint
        )

        // Procent
        val percentPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("$percentage%", margin + 10f, y + 17f, percentPaint)

        y += barHeight + 20f

        // Profil i opis
        val profileName = when (analysis.primaryProfile) {
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_ACUTE -> "Ostry profil zapalny"
            FipPatternAnalyzer.FipProfile.INFLAMMATORY_CHRONIC -> "Przewlekły profil zapalny"
            FipPatternAnalyzer.FipProfile.EFFUSIVE_CLASSIC -> "Klasyczny wysiękowy"
            FipPatternAnalyzer.FipProfile.DRY_NEUROLOGICAL -> "Suchy neurologiczny"
            FipPatternAnalyzer.FipProfile.MIXED_PATTERN -> "Profil mieszany"
            FipPatternAnalyzer.FipProfile.ATYPICAL -> "Profil nietypowy"
            FipPatternAnalyzer.FipProfile.NON_FIP -> "Profil nie-FIP"
        }

        val profilePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Główny profil: $profileName", margin, y, profilePaint)
        y += 25f

        // Kluczowe obserwacje
        if (analysis.keyFindings.isNotEmpty()) {
            val findingsPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 11f
                isAntiAlias = true
            }

            canvas.drawText("Kluczowe obserwacje:", margin, y, findingsPaint)
            y += 20f

            for (finding in analysis.keyFindings.take(3)) { // Max 3 najważniejsze
                val cleanFinding = finding.replace(Regex("[✅❌⚠️🔸❓]"), "•")
                canvas.drawText(cleanFinding, margin + 10f, y, findingsPaint)
                y += 18f
            }
        }

        return y + 30f
    }
}