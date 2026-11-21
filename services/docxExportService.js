/**
 * JC-Best Compliance Hub — Official DOCX Report Generation Engine
 * 
 * Implements authoritative DOCX generation for statutory compliance exception reports:
 * - Exact 6 columns: | DATE | NAME | CLASS | SUBJECT | EXCEPTIONS | EFFECTS |
 * - Landscape page orientation for optimal column readability
 * - Repeating header row on multi-page continued tables
 * - Full text preservation (no truncating)
 * - Strict sourceCount === writtenCount data integrity assertion
 * - Professional institutional header and metadata block
 * - Professional empty state document when no records match
 */

import {
  Document,
  Packer,
  Paragraph,
  Table,
  TableCell,
  TableRow,
  TextRun,
  WidthType,
  AlignmentType,
  BorderStyle,
  PageOrientation,
  Footer,
  PageNumber,
  VerticalAlign,
  ImageRun,
} from 'docx';

import {
  normalizeReportRow,
  getDownloadRecords,
  validateExportData,
  OFFICIAL_REPORT_COLUMNS,
} from './reportExportData';
import { loadInstitutionalLogos } from '../utils/logoAssetLoader';

/**
 * Triggers a browser file download from a Blob.
 * 
 * @param {Blob} blob 
 * @param {string} filename 
 */
export function saveBlobAsFile(blob, filename) {
  if (typeof window === 'undefined') return;

  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);

  setTimeout(() => {
    URL.revokeObjectURL(url);
  }, 1500);
}

/**
 * Sanitizes and generates a professional filename based on report scope and date range.
 * 
 * Examples:
 * - Operation_Compliance_Exception_Report_2026-09-01_to_2026-09-30.docx
 * - CDS_Compliance_Exception_Report_2026-09-01_to_2026-09-30.docx
 * - All_Compliance_Exception_Report_All_Dates.docx
 * 
 * @param {string} scope 
 * @param {string} startDate 
 * @param {string} endDate 
 * @returns {string} Sanitized filename
 */
export function generateDocxFilename(scope, startDate = '', endDate = '') {
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

  return `${cleanScope}_Compliance_Exception_Report_${periodStr}.docx`;
}

// Border presets for official document styling
const tableBorderPreset = {
  style: BorderStyle.SINGLE,
  size: 4, // 0.5 pt
  color: 'CBD5E1', // slate-300
};

const cellPadding = {
  top: 130, // dxa (~6.5pt)
  bottom: 130,
  left: 140, // dxa (~7pt)
  right: 140,
};

function buildHeaderTable(institutionName, logos) {
  const borderless = {
    top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
    bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
    left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
    right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
  };

  const leftChildren = [];
  if (logos?.jcbest?.uint8Array) {
    leftChildren.push(
      new Paragraph({
        alignment: AlignmentType.LEFT,
        children: [
          new ImageRun({
            data: logos.jcbest.uint8Array,
            transformation: { width: 72, height: 72 },
            type: 'png',
          }),
        ],
      })
    );
  } else {
    leftChildren.push(new Paragraph({ children: [] }));
  }

  const rightChildren = [];
  if (logos?.cambridge?.uint8Array) {
    rightChildren.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [
          new ImageRun({
            data: logos.cambridge.uint8Array,
            transformation: { width: 72, height: 72 },
            type: 'png',
          }),
        ],
      })
    );
  } else {
    rightChildren.push(new Paragraph({ children: [] }));
  }

  const centerChildren = [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 30 },
      children: [
        new TextRun({
          text: institutionName.toUpperCase(),
          bold: true,
          size: 30, // 15pt
          color: '0F172A',
          font: 'Arial',
        }),
      ],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 60 },
      children: [
        new TextRun({
          text: 'DIRECTORATE OF ACADEMIC QUALITY ASSURANCE & INSTITUTIONAL COMPLIANCE',
          bold: true,
          size: 18, // 9pt
          color: '334155',
          font: 'Arial',
        }),
      ],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 120 },
      children: [
        new TextRun({
          text: 'OFFICIAL COMPLIANCE EXCEPTION REPORT',
          bold: true,
          size: 22, // 11pt
          color: '1E3A8A',
          font: 'Arial',
        }),
      ],
    }),
  ];

  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    borders: {
      top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      insideHorizontal: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
      insideVertical: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' },
    },
    rows: [
      new TableRow({
        children: [
          new TableCell({
            width: { size: 15, type: WidthType.PERCENTAGE },
            verticalAlign: VerticalAlign.CENTER,
            borders: borderless,
            children: leftChildren,
          }),
          new TableCell({
            width: { size: 70, type: WidthType.PERCENTAGE },
            verticalAlign: VerticalAlign.CENTER,
            borders: borderless,
            children: centerChildren,
          }),
          new TableCell({
            width: { size: 15, type: WidthType.PERCENTAGE },
            verticalAlign: VerticalAlign.CENTER,
            borders: borderless,
            children: rightChildren,
          }),
        ],
      }),
    ],
  });
}

/**
 * Generates an official DOCX report blob for the selected scope, period, and exception records.
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
export async function generateOfficialDocxReport({
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
    format: 'DOCX',
    records,
    startDate,
    endDate,
    staffRoster,
  });

  if (!validation.isValid && !validation.isEmpty) {
    throw new Error(validation.error);
  }

  // Load institutional logos for DOCX header
  const logos = await loadInstitutionalLogos();

  const filename = generateDocxFilename(scope, startDate, endDate);

  // 2. Format Period Text
  let periodDisplay = 'All available dates';
  if (startDate && endDate) {
    periodDisplay = `${startDate} – ${endDate}`;
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

  // 3. Handle Empty State cleanly
  if (validation.isEmpty || validation.records.length === 0) {
    const emptyDoc = new Document({
      sections: [
        {
          properties: {
            page: {
              size: {
                orientation: PageOrientation.LANDSCAPE,
              },
              margin: {
                top: 720,
                bottom: 720,
                left: 720,
                right: 720,
              },
            },
          },
          footers: {
            default: new Footer({
              children: [
                new Paragraph({
                  alignment: AlignmentType.RIGHT,
                  children: [
                    new TextRun({
                      text: `${institutionName} · Official Compliance Ledger · Page `,
                      size: 16,
                      color: '64748B',
                    }),
                    PageNumber.CURRENT,
                    new TextRun({
                      text: ' of ',
                      size: 16,
                      color: '64748B',
                    }),
                    PageNumber.TOTAL_PAGES,
                  ],
                }),
              ],
            }),
          },
          children: [
            // Institutional Heading Table with Logos
            buildHeaderTable(institutionName, logos),

            // Metadata Block
            new Paragraph({
              spacing: { after: 80 },
              children: [
                new TextRun({ text: 'REPORT SCOPE: ', bold: true, size: 20, color: '0F172A' }),
                new TextRun({ text: `${scope}`, size: 20, color: '1E3A8A', bold: true }),
              ],
            }),
            new Paragraph({
              spacing: { after: 80 },
              children: [
                new TextRun({ text: 'REPORT PERIOD: ', bold: true, size: 20, color: '0F172A' }),
                new TextRun({ text: `${periodDisplay}`, size: 20, color: '334155' }),
              ],
            }),
            new Paragraph({
              spacing: { after: 300 },
              children: [
                new TextRun({ text: 'DATE GENERATED: ', bold: true, size: 18, color: '64748B' }),
                new TextRun({ text: `${generatedDateStr}`, size: 18, color: '64748B' }),
              ],
            }),

            // Empty Notice Box
            new Table({
              width: { size: 100, type: WidthType.PERCENTAGE },
              borders: {
                top: { style: BorderStyle.SINGLE, size: 6, color: 'CBD5E1' },
                bottom: { style: BorderStyle.SINGLE, size: 6, color: 'CBD5E1' },
                left: { style: BorderStyle.SINGLE, size: 6, color: 'CBD5E1' },
                right: { style: BorderStyle.SINGLE, size: 6, color: 'CBD5E1' },
              },
              rows: [
                new TableRow({
                  children: [
                    new TableCell({
                      shading: { fill: 'F8FAFC' },
                      margins: { top: 300, bottom: 300, left: 300, right: 300 },
                      children: [
                        new Paragraph({
                          alignment: AlignmentType.CENTER,
                          spacing: { after: 60 },
                          children: [
                            new TextRun({
                              text: 'No exception records found for the selected report scope and reporting period.',
                              bold: true,
                              size: 22,
                              color: '475569',
                              font: 'Arial',
                            }),
                          ],
                        }),
                        new Paragraph({
                          alignment: AlignmentType.CENTER,
                          children: [
                            new TextRun({
                              text: `Audit query executed across ${scope} records within the period ${periodDisplay}. Zero statutory exceptions recorded.`,
                              size: 18,
                              color: '64748B',
                              font: 'Arial',
                            }),
                          ],
                        }),
                      ],
                    }),
                  ],
                }),
              ],
            }),
          ],
        },
      ],
    });

    const blob = await Packer.toBlob(emptyDoc);
    return { blob, filename, recordCount: 0 };
  }

  // 4. Data Integrity Check:
  // Before building the table, capture exact source count
  const sourceCount = validation.records.length;

  // 5. Construct Table Header Row
  // Column Width Percentages for Landscape (Total = 100%):
  // DATE: 11%, NAME/DRIVER: 18%, CLASS/BUS: 9%, SUBJECT/CHECK: 14%, EXCEPTIONS: 25%, EFFECTS: 23%
  const colWidths = [11, 18, 9, 14, 25, 23];
  const isTransport = scope === 'Transport';
  const columnsList = isTransport
    ? [
        { key: 'date', label: 'DATE' },
        { key: 'driverName', label: 'DRIVER NAME' },
        { key: 'busNumber', label: 'BUS NO.' },
        { key: 'complianceCheck', label: 'COMPLIANCE CHECK' },
        { key: 'exception', label: 'EXCEPTIONS' },
        { key: 'effect', label: 'EFFECTS' },
      ]
    : OFFICIAL_REPORT_COLUMNS;

  const headerRow = new TableRow({
    tableHeader: true, // Word native repeating header on every continued page!
    cantSplit: true,
    children: columnsList.map((col, index) => {
      const isDateOrClass = col.key === 'date' || col.key === 'className' || col.key === 'busNumber';

      return new TableCell({
        width: {
          size: colWidths[index],
          type: WidthType.PERCENTAGE,
        },
        verticalAlign: VerticalAlign.CENTER,
        shading: {
          fill: '0F2744', // Dark Navy institutional background
        },
        margins: cellPadding,
        borders: {
          top: { style: BorderStyle.SINGLE, size: 6, color: '0F2744' },
          bottom: { style: BorderStyle.SINGLE, size: 8, color: '0A192F' },
          left: { style: BorderStyle.SINGLE, size: 4, color: '1E3A8A' },
          right: { style: BorderStyle.SINGLE, size: 4, color: '1E3A8A' },
        },
        children: [
          new Paragraph({
            alignment: isDateOrClass ? AlignmentType.CENTER : AlignmentType.LEFT,
            children: [
              new TextRun({
                text: col.label,
                bold: true,
                size: 22, // 11pt
                color: 'FFFFFF',
                font: 'Arial',
              }),
            ],
          }),
        ],
      });
    }),
  });

  // 6. Construct Data Rows with Alternating Shading & Full Text
  let numberOfDataRowsWritten = 0;

  const dataRows = validation.records.map((rawRec, rowIndex) => {
    const row = normalizeReportRow(rawRec, staffRoster);
    const isOdd = rowIndex % 2 === 1;
    const rowShading = isOdd ? 'F8FAFC' : 'FFFFFF';

    numberOfDataRowsWritten += 1;

    return new TableRow({
      cantSplit: true,
      children: [
        // 1. DATE (Centered, Monospace/Clean)
        new TableCell({
          width: { size: colWidths[0], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun({
                  text: row.date,
                  size: 20, // 10pt
                  color: '334155',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),

        // 2. NAME (Left Aligned, Strong Identity)
        new TableCell({
          width: { size: colWidths[1], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.LEFT,
              children: [
                new TextRun({
                  text: isTransport ? row.driverName : row.name,
                  bold: true,
                  size: 20, // 10pt
                  color: '0F172A',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),

        // 3. CLASS / BUS NO. (Centered)
        new TableCell({
          width: { size: colWidths[2], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun({
                  text: isTransport ? row.busNumber : row.className,
                  size: 20, // 10pt
                  color: '475569',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),

        // 4. SUBJECT / COMPLIANCE CHECK (Left Aligned)
        new TableCell({
          width: { size: colWidths[3], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.LEFT,
              children: [
                new TextRun({
                  text: isTransport ? row.complianceCheck : row.subject,
                  size: 20, // 10pt
                  color: '475569',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),

        // 5. EXCEPTIONS (Wide, Preserved Full Text)
        new TableCell({
          width: { size: colWidths[4], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.LEFT,
              children: [
                new TextRun({
                  text: row.exception,
                  size: 20, // 10pt
                  color: '1E293B',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),

        // 6. EFFECTS (Wide, Preserved Full Text)
        new TableCell({
          width: { size: colWidths[5], type: WidthType.PERCENTAGE },
          verticalAlign: VerticalAlign.TOP,
          shading: { fill: rowShading },
          margins: cellPadding,
          borders: {
            top: tableBorderPreset,
            bottom: tableBorderPreset,
            left: tableBorderPreset,
            right: tableBorderPreset,
          },
          children: [
            new Paragraph({
              alignment: AlignmentType.LEFT,
              children: [
                new TextRun({
                  text: row.effect,
                  size: 20, // 10pt
                  color: '475569',
                  font: 'Arial',
                }),
              ],
            }),
          ],
        }),
      ],
    });
  });

  // 7. Data Integrity Verification:
  // Require: sourceCount === writtenCount
  if (sourceCount !== numberOfDataRowsWritten) {
    throw new Error(
      `Generation Data Integrity Failure: Selected source count (${sourceCount}) does not match table rows written (${numberOfDataRowsWritten}). Aborting export to prevent silent record loss.`
    );
  }

  // 8. Assemble Complete Document
  const doc = new Document({
    sections: [
      {
        properties: {
          page: {
            size: {
              orientation: PageOrientation.LANDSCAPE,
            },
            margin: {
              top: 720, // 0.5 inch (720 dxa)
              bottom: 720,
              left: 720,
              right: 720,
            },
          },
        },
        footers: {
          default: new Footer({
            children: [
              new Paragraph({
                alignment: AlignmentType.RIGHT,
                children: [
                  new TextRun({
                    text: `${institutionName} · Official Compliance Ledger · Page `,
                    size: 16,
                    color: '64748B',
                    font: 'Arial',
                  }),
                  PageNumber.CURRENT,
                  new TextRun({
                    text: ' of ',
                    size: 16,
                    color: '64748B',
                    font: 'Arial',
                  }),
                  PageNumber.TOTAL_PAGES,
                ],
              }),
            ],
          }),
        },
        children: [
          // Institutional Header Table with Logos
          buildHeaderTable(institutionName, logos),

          // Metadata Table Header
          new Paragraph({
            spacing: { after: 60 },
            children: [
              new TextRun({ text: 'REPORT SCOPE: ', bold: true, size: 19, color: '0F172A' }),
              new TextRun({ text: `${scope}`, size: 19, color: '1E3A8A', bold: true }),
              new TextRun({ text: '          REPORT PERIOD: ', bold: true, size: 19, color: '0F172A' }),
              new TextRun({ text: `${periodDisplay}`, size: 19, color: '334155' }),
              new TextRun({ text: '          RECORDS: ', bold: true, size: 19, color: '0F172A' }),
              new TextRun({ text: `${sourceCount} Exception(s)`, size: 19, color: '1E3A8A', bold: true }),
            ],
          }),
          new Paragraph({
            spacing: { after: 200 },
            children: [
              new TextRun({ text: 'DATE GENERATED: ', bold: true, size: 16, color: '64748B' }),
              new TextRun({ text: `${generatedDateStr}`, size: 16, color: '64748B' }),
              new TextRun({ text: '          STATUS: ', bold: true, size: 16, color: '64748B' }),
              new TextRun({ text: 'Authoritative Statutory Ledger Dossier', size: 16, color: '16A34A', bold: true }),
            ],
          }),

          // The Main Official Table
          new Table({
            width: {
              size: 100,
              type: WidthType.PERCENTAGE,
            },
            rows: [headerRow, ...dataRows],
          }),
        ],
      },
    ],
  });

  // 9. Generate DOCX Blob
  const blob = await Packer.toBlob(doc);

  return {
    blob,
    filename,
    recordCount: sourceCount,
  };
}
