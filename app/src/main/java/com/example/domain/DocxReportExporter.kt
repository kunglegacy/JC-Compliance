package com.example.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.ComplianceReport
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin OOXML (.docx) report generator mirroring `/services/docxExportService.js`.
 * Produces a landscape Word document with institutional header, embedded crest logo,
 * repeating dark-navy header row (`w:tblHeader`), alternating row shading, and page numbers.
 */
object DocxReportExporter {

    private const val DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    // 100% of A4 landscape body width (~15398 dxa for 16838 - 1440 margins)
    // Proportions: [11, 18, 9, 14, 25, 23]% -> [1694, 2772, 1386, 2156, 3849, 3541]
    private val colWidthsDxa = intArrayOf(1694, 2772, 1386, 2156, 3849, 3541)

    suspend fun exportAndShareWithResult(
        context: Context,
        reports: List<ComplianceReport>,
        scope: ReportScope,
        term: String = "ALL",
        startDate: String = "",
        endDate: String = "",
        staffRoster: List<StaffMember> = emptyList()
    ): Result<Pair<File, PreparedExportDataset>> = withContext(Dispatchers.IO) {
        try {
            val prepared = ReportExportData.prepareDataset(
                scope = scope,
                format = "DOCX",
                records = reports,
                staffRoster = staffRoster,
                startDate = startDate,
                endDate = endDate,
                term = term
            )

            val crestPngBytes = loadCrestPngBytes(context)
            val hasCrest = crestPngBytes != null && crestPngBytes.isNotEmpty()

            val outFile = File(context.cacheDir, prepared.filename)
            ZipOutputStream(FileOutputStream(outFile)).use { zip ->
                writeZipText(zip, "[Content_Types].xml", buildContentTypesXml(hasCrest))
                writeZipText(zip, "_rels/.rels", buildRootRelsXml())
                writeZipText(zip, "word/_rels/document.xml.rels", buildDocumentRelsXml(hasCrest))
                if (hasCrest && crestPngBytes != null) {
                    zip.putNextEntry(ZipEntry("word/media/jcbest_crest.png"))
                    zip.write(crestPngBytes)
                    zip.closeEntry()
                }
                writeZipText(zip, "word/styles.xml", buildStylesXml())
                writeZipText(zip, "word/footer1.xml", buildFooterXml())
                writeZipText(zip, "word/document.xml", buildDocumentXml(prepared, hasCrest))
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = DOCX_MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "JCBest Compliance Report - ${scope.label}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Official Report DOCX").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(outFile to prepared)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadCrestPngBytes(context: Context): ByteArray? {
        var bmp: Bitmap? = null
        return try {
            bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_jcbest_crest) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { bmp?.recycle() }
        }
    }

    private fun writeZipText(zip: ZipOutputStream, entryName: String, xml: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(xml.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun xmlEscape(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\n', '\r', '\t' -> sb.append(ch)
                else -> if (ch.code >= 0x20) sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun buildTextRunsXml(
        text: String,
        sizeHalfPt: Int = 20,
        colorHex: String = "1E293B",
        bold: Boolean = false
    ): String {
        val rPr = buildString {
            append("<w:rPr>")
            append("<w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>")
            if (bold) append("<w:b/>")
            append("<w:color w:val=\"$colorHex\"/>")
            append("<w:sz w:val=\"$sizeHalfPt\"/>")
            append("<w:szCs w:val=\"$sizeHalfPt\"/>")
            append("</w:rPr>")
        }
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return buildString {
            lines.forEachIndexed { idx, line ->
                if (idx > 0) append("<w:r>$rPr<w:br/></w:r>")
                append("<w:r>$rPr<w:t xml:space=\"preserve\">${xmlEscape(line)}</w:t></w:r>")
            }
        }
    }

    private fun buildContentTypesXml(hasCrest: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (hasCrest) {
            append("""<Default Extension="png" ContentType="image/png"/>""")
        }
        append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
        append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
        append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildDocumentRelsXml(hasCrest: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
        append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        if (hasCrest) {
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/jcbest_crest.png"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildStylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("""<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="40" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""")
        append("""<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""")
        append("""</w:styles>""")
    }

    private fun buildFooterXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("""<w:p><w:pPr><w:jc w:val="right"/></w:pPr>""")
        append(buildTextRunsXml("JCBest Schools International · Official Compliance Ledger · Page ", sizeHalfPt = 16, colorHex = "64748B"))
        append("""<w:fldSimple w:instr="PAGE"><w:r><w:t>1</w:t></w:r></w:fldSimple>""")
        append(buildTextRunsXml(" of ", sizeHalfPt = 16, colorHex = "64748B"))
        append("""<w:fldSimple w:instr="NUMPAGES"><w:r><w:t>1</w:t></w:r></w:fldSimple>""")
        append("""</w:p></w:ftr>""")
    }

    private fun buildDocumentXml(prepared: PreparedExportDataset, hasCrest: Boolean): String {
        val isTransport = prepared.scope == ReportScope.TRANSPORT
        val headers = if (isTransport) ReportExportData.transportColumns else ReportExportData.standardColumns

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append(
                """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                    """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                    """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                    """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                    """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">"""
            )
            append("<w:body>")

            // 1. Borderless Institutional Header Table (Crest Left + Centered Titles)
            append(buildHeaderTableXml(hasCrest))

            // 2. Metadata Paragraphs
            append("<w:p><w:pPr><w:spacing w:before=\"120\" w:after=\"60\"/></w:pPr>")
            append(buildTextRunsXml("REPORT SCOPE: ", sizeHalfPt = 19, colorHex = "0F172A", bold = true))
            append(buildTextRunsXml(prepared.scope.label, sizeHalfPt = 19, colorHex = "1E3A8A", bold = true))
            append(buildTextRunsXml("          REPORT PERIOD: ", sizeHalfPt = 19, colorHex = "0F172A", bold = true))
            append(buildTextRunsXml(prepared.periodDisplay, sizeHalfPt = 19, colorHex = "334155"))
            append(buildTextRunsXml("          RECORDS: ", sizeHalfPt = 19, colorHex = "0F172A", bold = true))
            append(buildTextRunsXml("${prepared.rows.size} Exception(s)", sizeHalfPt = 19, colorHex = "1E3A8A", bold = true))
            append("</w:p>")

            append("<w:p><w:pPr><w:spacing w:after=\"200\"/></w:pPr>")
            append(buildTextRunsXml("DATE GENERATED: ", sizeHalfPt = 16, colorHex = "64748B", bold = true))
            append(buildTextRunsXml(prepared.generatedDateDisplay, sizeHalfPt = 16, colorHex = "64748B"))
            append(buildTextRunsXml("          STATUS: ", sizeHalfPt = 16, colorHex = "64748B", bold = true))
            append(buildTextRunsXml("Authoritative Statutory Ledger Dossier", sizeHalfPt = 16, colorHex = "16A34A", bold = true))
            append("</w:p>")

            // 3. Body Content: Empty State Box OR Full 6-Column Official Table
            if (prepared.rows.isEmpty()) {
                append(buildEmptyStateTableXml(prepared.scope.label, prepared.periodDisplay))
            } else {
                val sourceCount = prepared.records.size
                var rowsWritten = 0

                append("<w:tbl>")
                append("<w:tblPr>")
                append("<w:tblW w:w=\"15398\" w:type=\"dxa\"/>")
                append("<w:tblBorders>")
                append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CBD5E1\"/>")
                append("</w:tblBorders>")
                append("<w:tblLayout w:type=\"fixed\"/>")
                append("</w:tblPr>")
                append("<w:tblGrid>")
                colWidthsDxa.forEach { append("<w:gridCol w:w=\"$it\"/>") }
                append("</w:tblGrid>")

                // Repeating Table Header Row (#0F2744)
                append("<w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>")
                headers.forEachIndexed { idx, label ->
                    val align = if (idx == 0 || idx == 2) "center" else "left"
                    append("<w:tc><w:tcPr>")
                    append("<w:tcW w:w=\"${colWidthsDxa[idx]}\" w:type=\"dxa\"/>")
                    append("<w:tcBorders>")
                    append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"0F2744\"/>")
                    append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"1E3A8A\"/>")
                    append("<w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"0A192F\"/>")
                    append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"1E3A8A\"/>")
                    append("</w:tcBorders>")
                    append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"0F2744\"/>")
                    append("<w:tcMar><w:top w:w=\"120\" w:type=\"dxa\"/><w:left w:w=\"130\" w:type=\"dxa\"/><w:bottom w:w=\"120\" w:type=\"dxa\"/><w:right w:w=\"130\" w:type=\"dxa\"/></w:tcMar>")
                    append("<w:vAlign w:val=\"center\"/>")
                    append("</w:tcPr>")
                    append("<w:p><w:pPr><w:jc w:val=\"$align\"/></w:pPr>")
                    append(buildTextRunsXml(label, sizeHalfPt = 21, colorHex = "FFFFFF", bold = true))
                    append("</w:p></w:tc>")
                }
                append("</w:tr>")

                // Data Rows
                prepared.rows.forEachIndexed { rowIdx, row ->
                    val fill = if (rowIdx % 2 == 1) "F8FAFC" else "FFFFFF"
                    val cells = listOf(
                        row.date,
                        if (isTransport) row.driverName else row.name,
                        if (isTransport) row.busNumber else row.className,
                        if (isTransport) row.complianceCheck else row.subject,
                        row.exception,
                        row.effect
                    )

                    append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
                    cells.forEachIndexed { colIdx, text ->
                        val align = if (colIdx == 0 || colIdx == 2) "center" else "left"
                        val bold = colIdx == 1
                        val color = when (colIdx) {
                            1 -> "0F172A"
                            4 -> "1E293B"
                            2, 3, 5 -> "475569"
                            else -> "334155"
                        }
                        append("<w:tc><w:tcPr>")
                        append("<w:tcW w:w=\"${colWidthsDxa[colIdx]}\" w:type=\"dxa\"/>")
                        append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$fill\"/>")
                        append("<w:tcMar><w:top w:w=\"110\" w:type=\"dxa\"/><w:left w:w=\"130\" w:type=\"dxa\"/><w:bottom w:w=\"110\" w:type=\"dxa\"/><w:right w:w=\"130\" w:type=\"dxa\"/></w:tcMar>")
                        append("<w:vAlign w:val=\"top\"/>")
                        append("</w:tcPr>")
                        append("<w:p><w:pPr><w:jc w:val=\"$align\"/></w:pPr>")
                        append(buildTextRunsXml(text, sizeHalfPt = 19, colorHex = color, bold = bold))
                        append("</w:p></w:tc>")
                    }
                    append("</w:tr>")
                    rowsWritten++
                }

                if (sourceCount != rowsWritten) {
                    throw IllegalStateException(
                        "Generation Data Integrity Failure: Selected source count ($sourceCount) does not match table rows written ($rowsWritten)."
                    )
                }
                append("</w:tbl>")
            }

            // Landscape Section Properties (A4 Landscape: 16838 x 11906 dxa, 720 dxa margins)
            append("<w:sectPr>")
            append("<w:footerReference w:type=\"default\" r:id=\"rId1\"/>")
            append("<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>")
            append("<w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\" w:header=\"420\" w:footer=\"420\" w:gutter=\"0\"/>")
            append("</w:sectPr>")
            append("</w:body></w:document>")
        }
    }

    private fun buildHeaderTableXml(hasCrest: Boolean): String = buildString {
        append("<w:tbl><w:tblPr>")
        append("<w:tblW w:w=\"15398\" w:type=\"dxa\"/>")
        append("<w:tblBorders>")
        append("<w:top w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("<w:left w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("<w:bottom w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("<w:right w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("<w:insideH w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("<w:insideV w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>")
        append("</w:tblBorders></w:tblPr>")
        append("<w:tblGrid><w:gridCol w:w=\"1800\"/><w:gridCol w:w=\"11798\"/><w:gridCol w:w=\"1800\"/></w:tblGrid>")
        append("<w:tr>")

        // Left Logo Cell
        append("<w:tc><w:tcPr><w:tcW w:w=\"1800\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>")
        if (hasCrest) {
            append("<w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr>")
            append(buildCrestDrawingRunXml())
            append("</w:p>")
        } else {
            append("<w:p/>")
        }
        append("</w:tc>")

        // Center Institutional Titles
        append("<w:tc><w:tcPr><w:tcW w:w=\"11798\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr>")
        append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"40\"/></w:pPr>")
        append(buildTextRunsXml("JCBEST SCHOOLS INTERNATIONAL", sizeHalfPt = 30, colorHex = "0F172A", bold = true))
        append("</w:p>")
        append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"60\"/></w:pPr>")
        append(
            buildTextRunsXml(
                "DIRECTORATE OF ACADEMIC QUALITY ASSURANCE & INSTITUTIONAL COMPLIANCE",
                sizeHalfPt = 18,
                colorHex = "334155",
                bold = true
            )
        )
        append("</w:p>")
        append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"80\"/></w:pPr>")
        append(buildTextRunsXml("OFFICIAL COMPLIANCE EXCEPTION REPORT", sizeHalfPt = 22, colorHex = "1E3A8A", bold = true))
        append("</w:p>")
        append("</w:tc>")

        // Right Spacer Cell (Balances Left Logo so Title Stays Dead-Center)
        append("<w:tc><w:tcPr><w:tcW w:w=\"1800\" w:type=\"dxa\"/><w:vAlign w:val=\"center\"/></w:tcPr><w:p/></w:tc>")
        append("</w:tr></w:tbl>")
    }

    private fun buildCrestDrawingRunXml(): String {
        val emuSize = 685800L // ~72pt square (0.75 in)
        return "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$emuSize\" cy=\"$emuSize\"/>" +
            "<wp:docPr id=\"1\" name=\"JCBest Crest\"/>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic>" +
            "<pic:nvPicPr><pic:cNvPr id=\"0\" name=\"jcbest_crest.png\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$emuSize\" cy=\"$emuSize\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
    }

    private fun buildEmptyStateTableXml(scopeLabel: String, periodDisplay: String): String = buildString {
        append("<w:tbl><w:tblPr>")
        append("<w:tblW w:w=\"15398\" w:type=\"dxa\"/>")
        append("<w:tblBorders>")
        append("<w:top w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        append("<w:left w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        append("<w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        append("<w:right w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"CBD5E1\"/>")
        append("</w:tblBorders></w:tblPr>")
        append("<w:tblGrid><w:gridCol w:w=\"15398\"/></w:tblGrid>")
        append("<w:tr><w:tc><w:tcPr>")
        append("<w:tcW w:w=\"15398\" w:type=\"dxa\"/>")
        append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F8FAFC\"/>")
        append("<w:tcMar><w:top w:w=\"300\" w:type=\"dxa\"/><w:left w:w=\"300\" w:type=\"dxa\"/><w:bottom w:w=\"300\" w:type=\"dxa\"/><w:right w:w=\"300\" w:type=\"dxa\"/></w:tcMar>")
        append("</w:tcPr>")
        append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"80\"/></w:pPr>")
        append(
            buildTextRunsXml(
                "No exception records found for the selected report scope and reporting period.",
                sizeHalfPt = 22,
                colorHex = "475569",
                bold = true
            )
        )
        append("</w:p>")
        append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>")
        append(
            buildTextRunsXml(
                "Audit query executed across $scopeLabel records within the period $periodDisplay. Zero statutory exceptions recorded.",
                sizeHalfPt = 18,
                colorHex = "64748B"
            )
        )
        append("</w:p></w:tc></w:tr></w:tbl>")
    }
}
