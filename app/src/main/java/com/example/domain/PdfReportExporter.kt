package com.example.domain

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.local.AcademicWeekEngine
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportScope
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
        dateLabel: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#475569")
                textSize = 10f
            }
            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#1E3A8A")
            }
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val stripePaint = Paint().apply {
                color = Color.parseColor("#F8FAFC")
            }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 0.8f
                style = Paint.Style.STROKE
            }
            val cellPaint = TextPaint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 9f
                isAntiAlias = true
            }

            // 6 Columns: DATE | NAME | CLASS | SUBJECT | EXCEPTIONS | EFFECTS
            val tableWidth = PAGE_WIDTH - (MARGIN * 2)
            val colWidths = floatArrayOf(
                74f,   // DATE
                122f,  // NAME
                86f,   // CLASS
                94f,   // SUBJECT
                210f,  // EXCEPTIONS
                tableWidth - (74f + 122f + 86f + 94f + 210f) // EFFECTS
            )
            val colHeaders = listOf("DATE", "NAME", "CLASS", "SUBJECT", "EXCEPTIONS", "EFFECTS")

            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            fun drawHeader(c: Canvas, isFirstPage: Boolean): Float {
                var y = MARGIN
                c.drawText("JC-BEST SCHOOLS INTERNATIONAL", MARGIN, y + 12f, titlePaint)
                y += 18f
                val termLabel = if (term == "ALL") "All Terms" else term
                c.drawText(
                    "Compliance Audit Report • Scope: ${scope.label} • $termLabel • Period: $dateLabel • Total Records: ${reports.size}",
                    MARGIN,
                    y + 10f,
                    subPaint
                )
                y += 18f
                if (isFirstPage) {
                    val seenCount = reports.count { it.isSeen }
                    val unseenCount = reports.size - seenCount
                    c.drawText(
                        "Audit Summary: $seenCount Reviewed (Seen) | $unseenCount Pending Review (Unseen) | Generated: ${AcademicWeekEngine.todayIso()}",
                        MARGIN,
                        y + 8f,
                        subPaint
                    )
                    y += 16f
                }

                // Table Column Header Row
                c.drawRect(MARGIN, y, MARGIN + tableWidth, y + 22f, headerBgPaint)
                var x = MARGIN
                colHeaders.forEachIndexed { idx, label ->
                    c.drawText(label, x + 6f, y + 14.5f, headerTextPaint)
                    x += colWidths[idx]
                }
                return y + 22f
            }

            fun drawFooter(c: Canvas, num: Int) {
                c.drawText(
                    "JC-Best Directorate Compliance System — Confidential Official Record • Page $num",
                    MARGIN,
                    PAGE_HEIGHT - 16f,
                    subPaint
                )
            }

            var currentY = drawHeader(canvas, true)

            val sorted = reports.sortedByDescending { it.incidentDate }
            sorted.forEachIndexed { index, r ->
                val values = listOf(
                    r.incidentDate.ifBlank { "-" },
                    (r.staffName ?: r.assignedLocation ?: r.division).ifBlank { "-" },
                    r.className ?: r.assignedLocation ?: "-",
                    r.subject ?: r.category.ifBlank { "-" },
                    listOfNotNull(
                        r.complianceCheck.takeIf { it.isNotBlank() },
                        r.exceptionText.takeIf { it.isNotBlank() }
                    ).joinToString(": "),
                    r.effectText.ifBlank { "-" }
                )

                val layouts = values.mapIndexed { idx, text ->
                    StaticLayout.Builder.obtain(
                        text,
                        0,
                        text.length,
                        cellPaint,
                        (colWidths[idx] - 10f).toInt().coerceAtLeast(30)
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(1.5f, 1f)
                        .build()
                }
                val rowHeight = (layouts.maxOf { it.height } + 12f).coerceAtLeast(22f)

                if (currentY + rowHeight > PAGE_HEIGHT - 36f) {
                    drawFooter(canvas, pageNum)
                    document.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = drawHeader(canvas, false)
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
            }

            if (sorted.isEmpty()) {
                canvas.drawText("No exceptions recorded matching this filter.", MARGIN + 8f, currentY + 22f, subPaint)
            }

            drawFooter(canvas, pageNum)
            document.finishPage(page)

            val fileName = "JCBest_${scope.label.replace(Regex("[^a-zA-Z0-9]"), "_")}_${AcademicWeekEngine.todayIso()}.pdf"
            val outFile = File(context.cacheDir, fileName)
            FileOutputStream(outFile).use { document.writeTo(it) }
            document.close()

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
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
