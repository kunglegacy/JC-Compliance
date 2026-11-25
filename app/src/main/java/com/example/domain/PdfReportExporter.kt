package com.example.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfReportExporter {

    private const val PAGE_WIDTH = 842  // A4 Landscape
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 32f

    suspend fun exportAndShare(
        context: Context,
        reports: List<ComplianceReport>,
        scope: ReportScope,
        term: String,
        dateLabel: String,
        staffRoster: List<StaffMember> = emptyList(),
        startDate: String = "",
        endDate: String = ""
    ): Result<File> {
        return exportAndShareWithResult(
            context = context,
            reports = reports,
            scope = scope,
            term = term,
            dateLabel = dateLabel,
            staffRoster = staffRoster,
            startDate = startDate,
            endDate = endDate
        ).map { it.first }
    }

    suspend fun exportAndShareWithResult(
        context: Context,
        reports: List<ComplianceReport>,
        scope: ReportScope,
        term: String = "ALL",
        dateLabel: String = "",
        staffRoster: List<StaffMember> = emptyList(),
        startDate: String = "",
        endDate: String = ""
    ): Result<Pair<File, PreparedExportDataset>> = withContext(Dispatchers.IO) {
        var document: PdfDocument? = null
        var crestBitmap: Bitmap? = null
        try {
            val prepared = ReportExportData.prepareDataset(
                scope = scope,
                format = "PDF",
                records = reports,
                staffRoster = staffRoster,
                startDate = startDate,
                endDate = endDate,
                term = term
            )

            val displayPeriod = when {
                startDate.isNotBlank() || endDate.isNotBlank() -> prepared.periodDisplay
                dateLabel.isNotBlank() -> dateLabel
                else -> prepared.periodDisplay
            }

            crestBitmap = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.ic_jcbest_crest)
            }.getOrNull()

            document = PdfDocument()

            val schoolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 14.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val directoratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 8.8f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val docTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E3A8A")
                textSize = 11f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E3A8A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 8.5f
            }
            val metaStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#16A34A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 0.9f
            }
            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#0F2744")
            }
            val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val stripePaint = Paint().apply {
                color = Color.parseColor("#F8FAFC")
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 0.7f
                style = Paint.Style.STROKE
            }
            val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 8.5f
            }
            val nameCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val footerLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 7.8f
            }
            val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 7.8f
                textAlign = Paint.Align.RIGHT
            }

            // Column proportions matching pdfExportService.js (273mm usable -> 778pt usable)
            val tableWidth = PAGE_WIDTH - (MARGIN * 2)
            val colWidths = floatArrayOf(
                tableWidth * 0.095f, // DATE
                tableWidth * 0.175f, // NAME / DRIVER NAME
                tableWidth * 0.090f, // CLASS / BUS NO.
                tableWidth * 0.135f, // SUBJECT / COMPLIANCE CHECK
                tableWidth * 0.265f, // EXCEPTIONS
                tableWidth * 0.240f  // EFFECTS
            )
            val isTransport = scope == ReportScope.TRANSPORT
            val colHeaders = if (isTransport) {
                ReportExportData.transportColumns
            } else {
                ReportExportData.standardColumns
            }

            fun drawTableHeader(c: Canvas, topY: Float): Float {
                val hHeight = 23f
                c.drawRect(MARGIN, topY, MARGIN + tableWidth, topY + hHeight, headerBgPaint)
                var x = MARGIN
                colHeaders.forEachIndexed { idx, label ->
                    val centered = idx == 0 || idx == 2
                    if (centered) {
                        val centerPaint = Paint(headerTextPaint).apply { textAlign = Paint.Align.CENTER }
                        c.drawText(label, x + colWidths[idx] / 2f, topY + 14.5f, centerPaint)
                    } else {
                        c.drawText(label, x + 6f, topY + 14.5f, headerTextPaint)
                    }
                    x += colWidths[idx]
                }
                return topY + hHeight
            }

            fun drawFirstPageHeader(c: Canvas): Float {
                var y = 24f
                crestBitmap?.let { bmp ->
                    val dest = RectF(MARGIN, y, MARGIN + 52f, y + 52f)
                    c.drawBitmap(bmp, null, dest, null)
                }

                val centerX = PAGE_WIDTH / 2f
                c.drawText("JCBEST SCHOOLS INTERNATIONAL", centerX, y + 15f, schoolPaint)
                c.drawText(
                    "DIRECTORATE OF ACADEMIC QUALITY ASSURANCE & INSTITUTIONAL COMPLIANCE",
                    centerX,
                    y + 30f,
                    directoratePaint
                )
                c.drawText("OFFICIAL COMPLIANCE EXCEPTION REPORT", centerX, y + 46f, docTitlePaint)

                y += 66f
                // Metadata row
                c.drawText("REPORT SCOPE:", MARGIN, y, metaLabelPaint)
                c.drawText(scope.label, MARGIN + 74f, y, metaAccentPaint)

                c.drawText("PERIOD:", MARGIN + 195f, y, metaLabelPaint)
                c.drawText(displayPeriod, MARGIN + 234f, y, metaValuePaint)

                c.drawText("RECORDS:", MARGIN + 430f, y, metaLabelPaint)
                c.drawText("${prepared.rows.size} Exception(s)", MARGIN + 478f, y, metaAccentPaint)

                c.drawText("GENERATED:", MARGIN + 575f, y, metaLabelPaint)
                c.drawText(prepared.generatedDateDisplay, MARGIN + 632f, y, metaValuePaint)

                c.drawText("Statutory Archive", MARGIN + 704f, y, metaStatusPaint)

                y += 8f
                c.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
                return y + 10f
            }

            fun drawContinuedPageHeader(c: Canvas): Float {
                c.drawText(
                    "JCBest Schools International · ${scope.label} Compliance Report ($displayPeriod)",
                    MARGIN,
                    28f,
                    footerLeftPaint
                )
                return drawTableHeader(c, 36f)
            }

            // Pre-build pages so we know total page count for "Page X of Y"
            val finishedPagesInfo = mutableListOf<Int>()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            var currentY = drawFirstPageHeader(canvas)

            fun drawFooter(c: Canvas, num: Int) {
                c.drawText(
                    "JCBest Schools International · Official Statutory Compliance Dossier",
                    MARGIN,
                    PAGE_HEIGHT - 14f,
                    footerLeftPaint
                )
                c.drawText(
                    "Page $num",
                    PAGE_WIDTH - MARGIN,
                    PAGE_HEIGHT - 14f,
                    footerRightPaint
                )
            }

            if (prepared.rows.isEmpty()) {
                val boxTop = currentY + 8f
                val boxBottom = boxTop + 76f
                canvas.drawRoundRect(
                    RectF(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxBottom),
                    8f,
                    8f,
                    stripePaint
                )
                canvas.drawRoundRect(
                    RectF(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxBottom),
                    8f,
                    8f,
                    borderPaint
                )
                val emptyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#334155")
                    textSize = 11f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val emptySubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#64748B")
                    textSize = 9f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "No exception records found for the selected report scope and reporting period.",
                    PAGE_WIDTH / 2f,
                    boxTop + 32f,
                    emptyTitlePaint
                )
                canvas.drawText(
                    "Audit query executed across ${scope.label} records within $displayPeriod. Zero statutory exceptions recorded.",
                    PAGE_WIDTH / 2f,
                    boxTop + 50f,
                    emptySubPaint
                )
                drawFooter(canvas, pageNum)
                document.finishPage(page)
            } else {
                currentY = drawTableHeader(canvas, currentY)

                var rowsWritten = 0
                val maxBodyBottom = PAGE_HEIGHT - 34f

                fun startNewPdfPage() {
                    drawFooter(canvas, pageNum)
                    document.finishPage(page)
                    finishedPagesInfo.add(pageNum)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawContinuedPageHeader(canvas)
                }

                fun buildStaticLayout(
                    text: String,
                    paint: TextPaint,
                    widthPx: Int,
                    alignment: Layout.Alignment
                ): StaticLayout {
                    return StaticLayout.Builder.obtain(
                        text,
                        0,
                        text.length,
                        paint,
                        widthPx.coerceAtLeast(24)
                    )
                        .setAlignment(alignment)
                        .setLineSpacing(1.5f, 1.05f)
                        .build()
                }

                prepared.rows.forEachIndexed { index, row ->
                    if (!isTransport && row.isGroup && row.subRows.isNotEmpty()) {
                        val dateLayout = buildStaticLayout(
                            row.date,
                            cellPaint,
                            (colWidths[0] - 10f).toInt(),
                            Layout.Alignment.ALIGN_CENTER
                        )
                        val exLayout = buildStaticLayout(
                            row.exception,
                            cellPaint,
                            (colWidths[4] - 10f).toInt(),
                            Layout.Alignment.ALIGN_NORMAL
                        )
                        val effLayout = buildStaticLayout(
                            row.effect,
                            cellPaint,
                            (colWidths[5] - 10f).toInt(),
                            Layout.Alignment.ALIGN_NORMAL
                        )

                        val subLayouts = row.subRows.map { sub ->
                            Triple(
                                buildStaticLayout(
                                    sub.name,
                                    nameCellPaint,
                                    (colWidths[1] - 10f).toInt(),
                                    Layout.Alignment.ALIGN_NORMAL
                                ),
                                buildStaticLayout(
                                    sub.className,
                                    cellPaint,
                                    (colWidths[2] - 10f).toInt(),
                                    Layout.Alignment.ALIGN_CENTER
                                ),
                                buildStaticLayout(
                                    sub.subject,
                                    cellPaint,
                                    (colWidths[3] - 10f).toInt(),
                                    Layout.Alignment.ALIGN_NORMAL
                                )
                            )
                        }

                        val subHeights = subLayouts.map { (nL, cL, sL) ->
                            (maxOf(nL.height, cL.height, sL.height) + 12f).coerceAtLeast(22f)
                        }.toMutableList()

                        val minSpannedHeight = (maxOf(dateLayout.height, exLayout.height, effLayout.height) + 12f)
                            .coerceAtLeast(22f)
                        val rawTotalHeight = subHeights.sum()
                        if (rawTotalHeight < minSpannedHeight) {
                            val extraEach = (minSpannedHeight - rawTotalHeight) / subHeights.size
                            for (i in subHeights.indices) {
                                subHeights[i] += extraEach
                            }
                        }
                        val totalGroupHeight = subHeights.sum()
                        val fullFreshPageCapacity = maxBodyBottom - 59f

                        if (totalGroupHeight <= fullFreshPageCapacity) {
                            if (currentY + totalGroupHeight > maxBodyBottom) {
                                startNewPdfPage()
                            }

                            val blockTop = currentY
                            if (index % 2 == 1) {
                                canvas.drawRect(
                                    MARGIN,
                                    blockTop,
                                    MARGIN + tableWidth,
                                    blockTop + totalGroupHeight,
                                    stripePaint
                                )
                            }
                            canvas.drawRect(
                                MARGIN,
                                blockTop,
                                MARGIN + tableWidth,
                                blockTop + totalGroupHeight,
                                borderPaint
                            )

                            // Full-height vertical column borders
                            var vx = MARGIN
                            for (colIdx in 0 until colWidths.lastIndex) {
                                vx += colWidths[colIdx]
                                canvas.drawLine(vx, blockTop, vx, blockTop + totalGroupHeight, borderPaint)
                            }

                            // Horizontal sub-dividers ONLY across Name (col 1), Class (col 2), Subject (col 3)
                            val col1X = MARGIN + colWidths[0]
                            val col2X = col1X + colWidths[1]
                            val col3X = col2X + colWidths[2]
                            val col4X = col3X + colWidths[3]
                            val col5X = col4X + colWidths[4]
                            var divY = blockTop
                            for (subIdx in 0 until subHeights.lastIndex) {
                                divY += subHeights[subIdx]
                                canvas.drawLine(col1X, divY, col4X, divY, borderPaint)
                            }

                            // Col 0 (Date - vertically centered in spanned cell)
                            val dateTopOffset = ((totalGroupHeight - dateLayout.height) / 2f).coerceAtLeast(6f)
                            canvas.save()
                            canvas.translate(MARGIN + 5f, blockTop + dateTopOffset)
                            dateLayout.draw(canvas)
                            canvas.restore()

                            // Col 4 (Exceptions) & Col 5 (Effects) - top-aligned in spanned cell
                            canvas.save()
                            canvas.translate(col4X + 5f, blockTop + 6f)
                            exLayout.draw(canvas)
                            canvas.restore()

                            canvas.save()
                            canvas.translate(col5X + 5f, blockTop + 6f)
                            effLayout.draw(canvas)
                            canvas.restore()

                            // Sub-rows Col 1 (Name), Col 2 (Class), Col 3 (Subject)
                            var subY = blockTop
                            subLayouts.forEachIndexed { subIdx, (nLayout, cLayout, sLayout) ->
                                canvas.save()
                                canvas.translate(col1X + 5f, subY + 6f)
                                nLayout.draw(canvas)
                                canvas.restore()

                                canvas.save()
                                canvas.translate(col2X + 5f, subY + 6f)
                                cLayout.draw(canvas)
                                canvas.restore()

                                canvas.save()
                                canvas.translate(col3X + 5f, subY + 6f)
                                sLayout.draw(canvas)
                                canvas.restore()

                                subY += subHeights[subIdx]
                            }

                            currentY += totalGroupHeight
                        } else {
                            // Oversized group safety fallback: split across pages by sub-row slice
                            var startIdx = 0
                            while (startIdx < subLayouts.size) {
                                if (currentY + subHeights[startIdx] > maxBodyBottom) {
                                    startNewPdfPage()
                                }
                                val avail = maxBodyBottom - currentY
                                var endIdx = startIdx
                                var sliceHeight = 0f
                                while (endIdx < subLayouts.size &&
                                    (endIdx == startIdx || sliceHeight + subHeights[endIdx] <= avail)
                                ) {
                                    sliceHeight += subHeights[endIdx]
                                    endIdx++
                                }

                                val blockTop = currentY
                                if (index % 2 == 1) {
                                    canvas.drawRect(MARGIN, blockTop, MARGIN + tableWidth, blockTop + sliceHeight, stripePaint)
                                }
                                canvas.drawRect(MARGIN, blockTop, MARGIN + tableWidth, blockTop + sliceHeight, borderPaint)

                                var vx = MARGIN
                                for (colIdx in 0 until colWidths.lastIndex) {
                                    vx += colWidths[colIdx]
                                    canvas.drawLine(vx, blockTop, vx, blockTop + sliceHeight, borderPaint)
                                }

                                val col1X = MARGIN + colWidths[0]
                                val col2X = col1X + colWidths[1]
                                val col3X = col2X + colWidths[2]
                                val col4X = col3X + colWidths[3]
                                val col5X = col4X + colWidths[4]

                                var divY = blockTop
                                for (subIdx in startIdx until endIdx - 1) {
                                    divY += subHeights[subIdx]
                                    canvas.drawLine(col1X, divY, col4X, divY, borderPaint)
                                }

                                if (startIdx == 0) {
                                    val dateTopOffset = ((sliceHeight - dateLayout.height) / 2f).coerceAtLeast(6f)
                                    canvas.save(); canvas.translate(MARGIN + 5f, blockTop + dateTopOffset); dateLayout.draw(canvas); canvas.restore()
                                    if (exLayout.height + 12f <= sliceHeight) {
                                        canvas.save(); canvas.translate(col4X + 5f, blockTop + 6f); exLayout.draw(canvas); canvas.restore()
                                    }
                                    if (effLayout.height + 12f <= sliceHeight) {
                                        canvas.save(); canvas.translate(col5X + 5f, blockTop + 6f); effLayout.draw(canvas); canvas.restore()
                                    }
                                }

                                var subY = blockTop
                                for (subIdx in startIdx until endIdx) {
                                    val (nLayout, cLayout, sLayout) = subLayouts[subIdx]
                                    canvas.save(); canvas.translate(col1X + 5f, subY + 6f); nLayout.draw(canvas); canvas.restore()
                                    canvas.save(); canvas.translate(col2X + 5f, subY + 6f); cLayout.draw(canvas); canvas.restore()
                                    canvas.save(); canvas.translate(col3X + 5f, subY + 6f); sLayout.draw(canvas); canvas.restore()
                                    subY += subHeights[subIdx]
                                }

                                currentY += sliceHeight
                                startIdx = endIdx
                            }
                        }

                        rowsWritten++
                    } else {
                        val values = listOf(
                            row.date,
                            if (isTransport) row.driverName else row.name,
                            if (isTransport) row.busNumber else row.className,
                            if (isTransport) row.complianceCheck else row.subject,
                            row.exception,
                            row.effect
                        )

                        val layouts = values.mapIndexed { idx, text ->
                            val paint = if (idx == 1) nameCellPaint else cellPaint
                            val alignment = if (idx == 0 || idx == 2) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
                            buildStaticLayout(text, paint, (colWidths[idx] - 10f).toInt(), alignment)
                        }
                        val rowHeight = (layouts.maxOf { it.height } + 12f).coerceAtLeast(22f)

                        if (currentY + rowHeight > maxBodyBottom) {
                            startNewPdfPage()
                        }

                        if (index % 2 == 1) {
                            canvas.drawRect(MARGIN, currentY, MARGIN + tableWidth, currentY + rowHeight, stripePaint)
                        }
                        canvas.drawRect(MARGIN, currentY, MARGIN + tableWidth, currentY + rowHeight, borderPaint)

                        var x = MARGIN
                        layouts.forEachIndexed { idx, layout ->
                            canvas.save()
                            canvas.translate(x + 5f, currentY + 6f)
                            layout.draw(canvas)
                            canvas.restore()
                            x += colWidths[idx]
                            if (idx < layouts.lastIndex) {
                                canvas.drawLine(x, currentY, x, currentY + rowHeight, borderPaint)
                            }
                        }
                        currentY += rowHeight
                        rowsWritten++
                    }
                }

                if (rowsWritten != prepared.records.size) {
                    throw IllegalStateException(
                        "Generation Data Integrity Failure: Selected source count (${prepared.records.size}) does not match table rows written ($rowsWritten)."
                    )
                }

                drawFooter(canvas, pageNum)
                document.finishPage(page)
            }

            val outFile = File(context.cacheDir, prepared.filename)
            FileOutputStream(outFile).use { document.writeTo(it) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "JCBest Compliance Report - ${scope.label}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Official Report PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(outFile to prepared)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { document?.close() }
            runCatching { crestBitmap?.recycle() }
        }
    }
}
