/**
 * JC-Best Compliance Hub — Official PDF Report Generation Engine
 * 
 * Implements authoritative PDF generation for statutory compliance exception reports:
 * - Exact 6 columns: | DATE | NAME | CLASS | SUBJECT | EXCEPTIONS | EFFECTS |
 * - Landscape A4 orientation for spacious, readable columns
 * - Multi-page dynamic pagination with repeating header row on every continued page
 * - Full text preservation with word wrapping (never truncated)
 * - Strict sourceCount === writtenCount data integrity assertion
 * - Professional institutional header and metadata block
 * - Professional empty state document when no records match
 */

import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

import {
  normalizeReportRow,
  getDownloadRecords,
  validateExportData,
  OFFICIAL_REPORT_COLUMNS,
} from './reportExportData';
import { loadInstitutionalLogos } from '../utils/logoAssetLoader';

/**
 * Sanitizes and generates a professional filename based on report scope and date range.
 * 
 * Examples:
 * - Operation_Compliance_Exception_Report_2026-09-01_to_2026-09-30.pdf
 * - CDS_Compliance_Exception_Report_2026-09-01_to_2026-09-30.pdf
 * - Secondary_JS_Compliance_Exception_Report_2026-09-01_to_2026-09-30.pdf
 * - All_Compliance_Exception_Report_All_Dates.pdf
 * 
 * @param {string} scope 
 * @param {string} startDate 
 * @param {string} endDate 
 * @returns {string} Sanitized filename
 */
export function generatePdfFilename(scope, startDate = '', endDate = '') {
  let cleanScope = String(scope || 'All');

  if (cleanScope === 'Secondary (J&S)') {
    cleanScope = 'Secondary_JS';
  } else {
    cleanScope = cleanScope
      .replace(/[^a-zA-Z0-9]/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '');
  }

  let periodStr = 'All_Dates';
  if (startDate && endDate) {
    periodStr = `${startDate}_to_${endDate}`;
  } else if (startDate) {
    periodStr = `From_${startDate}`;
  } else if (endDate) {
    periodStr = `Up_To_${endDate}`;
  }

  return `${cleanScope}_Compliance_Exception_Report_${periodStr}.pdf`;
}

/**
 * Generates an official PDF report blob for the selected scope, period, and exception records.
 * 
 * @param {Object} params
 * @param {string} params.scope - One of the 6 official scopes
 * @param {string} [params.startDate] - Start date filter (YYYY-MM-DD)
 * @param {string} [params.endDate] - End date filter (YYYY-MM-DD)
 * @param {Array} params.records - Full list of exception records
 * @param {Array} [params.staffRoster] - Staff master roster
 * @param {string} [params.institutionName] - School name
 * @returns {Promise<{ blob: Blob, filename: string, recordCount: number }>}
 */
export async function generateOfficialPdfReport({
  scope,
  startDate = '',
  endDate = '',
  records = [],
  staffRoster = [],
  institutionName = 'JCBest Schools International',
}) {
  // 1. Validate export parameters and obtain filtered records
  const validation = validateExportData({
    scope,
    format: 'PDF',
    records,
    startDate,
    endDate,
    staffRoster,
  });

  if (!validation.isValid && !validation.isEmpty) {
    throw new Error(validation.error);
  }

  // Load institutional logos for header block
  const logos = await loadInstitutionalLogos();

  const filename = generatePdfFilename(scope, startDate, endDate);

  // 2. Initialize jsPDF in Landscape A4 (297mm x 210mm)
  const doc = new jsPDF({
    orientation: 'landscape',
    unit: 'mm',
    format: 'a4',
  });

  const pageWidth = doc.internal.pageSize.getWidth(); // 297mm
  const pageHeight = doc.internal.pageSize.getHeight(); // 210mm

  // Format Period Text
  let periodDisplay = 'All available dates';
  if (startDate && endDate) {
    periodDisplay = `${startDate} to ${endDate}`;
  } else if (startDate) {
    periodDisplay = `From ${startDate} onward`;
  } else if (endDate) {
    periodDisplay = `Up to ${endDate}`;
  }

  const generatedDateStr = new Date().toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });

  // Helper to draw document header block on Page 1
  const drawDocumentHeader = (docInstance) => {
    // 1. Draw JCBest Logo on the LEFT (x = 14, y = 7, w = 21, h = 21)
    if (logos.jcbest?.dataUrl) {
      try {
        docInstance.addImage(logos.jcbest.dataUrl, 'PNG', 14, 7, 21, 21);
      } catch (err) {
        console.warn('PDF JCBest logo render error:', err);
      }
    }

    // 2. Draw Cambridge Coat of Arms Logo on the RIGHT (x = 262, y = 7, w = 21, h = 21)
    if (logos.cambridge?.dataUrl) {
      try {
        docInstance.addImage(logos.cambridge.dataUrl, 'PNG', 262, 7, 21, 21);
      } catch (err) {
        console.warn('PDF Cambridge logo render error:', err);
      }
    }

    // School Title
    docInstance.setFont('helvetica', 'bold');
    docInstance.setFontSize(15);
    docInstance.setTextColor(15, 23, 42); // slate-900
    docInstance.text(institutionName.toUpperCase(), pageWidth / 2, 13.5, { align: 'center' });

    // Directorate Subtitle
    docInstance.setFont('helvetica', 'bold');
    docInstance.setFontSize(9.5);
    docInstance.setTextColor(51, 65, 85); // slate-700
    docInstance.text(
      'DIRECTORATE OF ACADEMIC QUALITY ASSURANCE & INSTITUTIONAL COMPLIANCE',
      pageWidth / 2,
      19.5,
      { align: 'center' }
    );

    // Document Name
    docInstance.setFont('helvetica', 'bold');
    docInstance.setFontSize(12);
    docInstance.setTextColor(30, 58, 138); // blue-900
    docInstance.text('OFFICIAL COMPLIANCE EXCEPTION REPORT', pageWidth / 2, 26, { align: 'center' });

    // Metadata Bar
    docInstance.setFont('helvetica', 'bold');
    docInstance.setFontSize(9.5);
    docInstance.setTextColor(15, 23, 42);

    const displayScope = scope;
    docInstance.text('REPORT SCOPE: ', 12, 34);
    docInstance.setFont('helvetica', 'bold');
    docInstance.setTextColor(30, 58, 138);
    docInstance.text(`${displayScope}`, 41, 34);

    docInstance.setFont('helvetica', 'bold');
    docInstance.setTextColor(15, 23, 42);
    docInstance.text('REPORT PERIOD: ', 78, 34);
    docInstance.setFont('helvetica', 'normal');
    docInstance.setTextColor(51, 65, 85);
    docInstance.text(`${periodDisplay}`, 110, 34);

    docInstance.setFont('helvetica', 'bold');
    docInstance.setTextColor(15, 23, 42);
    docInstance.text('DATE GENERATED: ', 182, 34);
    docInstance.setFont('helvetica', 'normal');
    docInstance.setTextColor(51, 65, 85);
    docInstance.text(`${generatedDateStr}`, 216, 34);

    docInstance.setFont('helvetica', 'bold');
    docInstance.setTextColor(15, 23, 42);
    docInstance.text('STATUS: ', 248, 34);
    docInstance.setFont('helvetica', 'bold');
    docInstance.setTextColor(22, 163, 74); // emerald-600
    docInstance.text('Statutory Archive', 264, 34);

    // Divider Line
    docInstance.setDrawColor(203, 213, 225); // slate-300
    docInstance.setLineWidth(0.3);
    docInstance.line(12, 37.5, pageWidth - 12, 37.5);
  };

  // 3. Handle Empty State cleanly
  if (validation.isEmpty || validation.records.length === 0) {
    drawDocumentHeader(doc);

    // Empty Notice Box
    doc.setFillColor(248, 250, 252); // slate-50
    doc.setDrawColor(203, 213, 225); // slate-300
    doc.setLineWidth(0.3);
    doc.roundedRect(12, 45, pageWidth - 24, 38, 3, 3, 'FD');

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(51, 65, 85);
    doc.text(
      'No exception records found for the selected report scope and reporting period.',
      pageWidth / 2,
      59,
      { align: 'center' }
    );

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(100, 116, 139);
    doc.text(
      `Audit query executed across ${scope} records within the period ${periodDisplay}. Zero statutory exceptions recorded.`,
      pageWidth / 2,
      67,
      { align: 'center' }
    );

    // Bottom Footer
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(100, 116, 139);
    doc.text(
      `${institutionName} · Official Compliance Ledger · Page 1 of 1`,
      pageWidth - 12,
      pageHeight - 8,
      { align: 'right' }
    );

    const blob = doc.output('blob');
    return { blob, filename, recordCount: 0 };
  }

  // 4. Data Integrity Check:
  // Before building the table, capture exact source count
  const sourceCount = validation.records.length;
  let numberOfDataRowsWritten = 0;

  // 5. Construct Table Rows using exact 6 columns
  const isTransport = scope === 'Transport';
  const tableHeaders = isTransport
    ? ['DATE', 'DRIVER NAME', 'BUS NO.', 'COMPLIANCE CHECK', 'EXCEPTIONS', 'EFFECTS']
    : ['DATE', 'NAME', 'CLASS', 'SUBJECT', 'EXCEPTIONS', 'EFFECTS'];

  const tableRows = validation.records.map((rawRec) => {
    const row = normalizeReportRow(rawRec, staffRoster);
    numberOfDataRowsWritten += 1;

    return [
      row.date,
      isTransport ? row.driverName : row.name,
      isTransport ? row.busNumber : row.className,
      isTransport ? row.complianceCheck : row.subject,
      row.exception,
      row.effect,
    ];
  });

  // Verify Data Integrity: sourceCount === writtenCount
  if (sourceCount !== numberOfDataRowsWritten) {
    throw new Error(
      `Generation Data Integrity Failure: Selected source count (${sourceCount}) does not match table rows written (${numberOfDataRowsWritten}). Aborting export to prevent silent record loss.`
    );
  }

  // Draw Header on Page 1
  drawDocumentHeader(doc);

  // 6. Generate dynamic multi-page table with autoTable
  // Total usable width = 297mm - 24mm (margins) = 273mm
  // Column distribution:
  // DATE: 26mm, NAME: 46mm, CLASS: 24mm, SUBJECT: 36mm, EXCEPTIONS: 73mm, EFFECTS: 68mm
  autoTable(doc, {
    head: [tableHeaders],
    body: tableRows,
    startY: 41,
    margin: { top: 16, bottom: 16, left: 12, right: 12 },
    showHead: 'everyPage', // Repeat header row automatically across every continued page!
    theme: 'grid',
    headStyles: {
      fillColor: [15, 39, 68], // Dark Navy (#0F2744)
      textColor: [255, 255, 255],
      fontStyle: 'bold',
      fontSize: 9.5,
      halign: 'left',
      cellPadding: { top: 3.5, bottom: 3.5, left: 3, right: 3 },
    },
    styles: {
      font: 'helvetica',
      fontSize: 9,
      textColor: [30, 41, 59], // slate-800
      lineColor: [203, 213, 225], // slate-300
      lineWidth: 0.15,
      cellPadding: { top: 3.5, bottom: 3.5, left: 3, right: 3 },
      valign: 'top',
      overflow: 'linebreak', // Preserves all exception and effect text without truncation
    },
    alternateRowStyles: {
      fillColor: [248, 250, 252], // slate-50 alternating rows
    },
    columnStyles: {
      0: { cellWidth: 26, halign: 'center' }, // DATE (compact, centered)
      1: { cellWidth: 46, fontStyle: 'bold', textColor: [15, 23, 42] }, // NAME (clear identity)
      2: { cellWidth: 24, halign: 'center' }, // CLASS (compact, centered)
      3: { cellWidth: 36 }, // SUBJECT
      4: { cellWidth: 73 }, // EXCEPTIONS (largest area, wrapped)
      5: { cellWidth: 68 }, // EFFECTS (largest area, wrapped)
    },
    didDrawPage: (data) => {
      // Draw running footer on every generated page
      const totalPages = doc.internal.getNumberOfPages();
      const currentPage = data.pageNumber;

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(7.5);
      doc.setTextColor(100, 116, 139);

      // Left footer
      doc.text(
        `${institutionName} · Official Statutory Compliance Dossier`,
        12,
        pageHeight - 7
      );

      // Right footer with dynamic total page count
      doc.text(
        `Page ${currentPage} of ${totalPages}`,
        pageWidth - 12,
        pageHeight - 7,
        { align: 'right' }
      );
    },
  });

  // 7. Output PDF Blob
  const blob = doc.output('blob');

  return {
    blob,
    filename,
    recordCount: sourceCount,
  };
}
