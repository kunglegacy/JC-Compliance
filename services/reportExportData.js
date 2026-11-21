/**
 * JC-Best Compliance Hub — Official Download Report Data Mapping Service
 * 
 * Defines authoritative mapping between existing compliance exception records
 * and the official 6-column downloaded report structure:
 * | DATE | NAME | CLASS | SUBJECT | EXCEPTIONS | EFFECTS |
 * 
 * Official Download Scopes:
 * 1. Operation — Campus facilities, logistics, minders, transport, security
 * 2. CDS — Corporate Digital Services technological infrastructure
 * 3. Primary — Basic 1 to 6 (Junior & Senior Primary)
 * 4. Secondary (J&S) — JSS 1-3 & SSS 1-3 (Junior & Senior Secondary)
 * 5. Nursery — Creche & Nursery 1-3
 * 6. All — Comprehensive institutional archive across all scopes
 */

import {
  OFFICIAL_REPORT_SCOPES as CANONICAL_REPORT_SCOPES,
  getRecordScope as canonicalGetRecordScope,
  recordMatchesScope as canonicalRecordMatchesScope,
  buildStaffLookupMap as canonicalBuildStaffLookupMap,
  resolveRecordClassification,
} from './compliance/classificationService.js';

export const OFFICIAL_REPORT_COLUMNS = [
  { key: 'date', label: 'DATE' },
  { key: 'name', label: 'NAME' },
  { key: 'className', label: 'CLASS' },
  { key: 'subject', label: 'SUBJECT' },
  { key: 'exception', label: 'EXCEPTIONS' },
  { key: 'effect', label: 'EFFECTS' },
];

export const OFFICIAL_REPORT_SCOPES = CANONICAL_REPORT_SCOPES;

/**
 * Builds a fast lookup map for staff records by staff_code, name, and id.
 * Delegates to authoritative classification service.
 * 
 * @param {Array} staffRoster 
 * @returns {Map}
 */
export function buildStaffLookupMap(staffRoster = []) {
  return canonicalBuildStaffLookupMap(staffRoster);
}

/**
 * Resolves the authoritative scope of an exception record based on existing project data fields.
 * Delegates to authoritative classification service.
 * 
 * @param {Object} record - Raw or normalized exception record
 * @param {Map|Array} staffRosterOrMap - Staff roster or staff map
 * @returns {string} One of the official scope names
 */
export function getRecordScope(record, staffRosterOrMap = null) {
  return canonicalGetRecordScope(record, staffRosterOrMap);
}

export const recordMatchesScope = canonicalRecordMatchesScope;
export { resolveRecordClassification };

/**
 * Normalizes an exception record into the official 6-column report table row.
 * Produces ONLY: date, name, className, subject, exception, effect.
 * When data is non-applicable or unavailable, uses '—'.
 * 
 * NEVER fabricates fake staff for CDS or fake classroom assignments for operations.
 * 
 * @param {Object} record - Raw or normalized exception record
 * @param {Map|Array} staffRosterOrMap - Optional staff roster for data resolution
 * @returns {{ date: string, name: string, className: string, subject: string, exception: string, effect: string }}
 */
export function normalizeReportRow(record, staffRosterOrMap = null) {
  if (!record) {
    return {
      date: '—',
      name: '—',
      unit: '—',
      className: '—',
      subject: '—',
      exception: '—',
      effect: '—',
    };
  }

  const raw = record.rawRecord || record;

  // Resolve staff reference if available
  const staffMap = staffRosterOrMap instanceof Map
    ? staffRosterOrMap
    : buildStaffLookupMap(Array.isArray(staffRosterOrMap) ? staffRosterOrMap : []);

  // Determine authoritative record scope / unit
  const recordScope = getRecordScope(record, staffMap);

  let staffPerson = null;
  const staffCode = record.staff_code || raw.staff_code;
  const staffName = record.staff_name || raw.staff_name || raw.teacher_name;
  const staffId = raw.staff_id;

  if (staffCode) staffPerson = staffMap.get(String(staffCode).trim().toLowerCase());
  if (!staffPerson && staffName) staffPerson = staffMap.get(String(staffName).trim().toLowerCase());
  if (!staffPerson && staffId) staffPerson = staffMap.get(String(staffId).trim().toLowerCase());

  // 1. DATE
  let date = record.date || raw.incident_date || raw.date;
  if (!date && raw.created_at) {
    date = String(raw.created_at).split('T')[0];
  }
  const cleanDate = date ? String(date).trim() : '—';

  // 2. NAME & UNIT
  // CDS is not an individual person; represent as the official Directorate entity
  const isCDS = recordScope === 'CDS';
  const isTransport = recordScope === 'Transport';

  // Extract transport specific attributes
  const rawDriverName = (
    record.driver_name ||
    raw.driver_name ||
    (isTransport ? (record.staff_name || raw.staff_name) : '') ||
    ''
  ).trim();

  const rawBusNumber = (
    record.bus_number ||
    raw.bus_number ||
    (isTransport ? (record.class_name || raw.class_name) : '') ||
    ''
  ).trim();

  const rawComplianceCheck = (
    record.compliance_check ||
    record.category ||
    raw.compliance_check ||
    raw.category ||
    record.check_name ||
    ''
  ).trim();

  let rawStaffName = '—';
  if (isCDS) {
    rawStaffName = 'Corporate Digital Services (CDS)';
  } else if (isTransport && rawDriverName) {
    rawStaffName = rawDriverName;
  } else {
    const fetchedName = (
      record.staff_name ||
      raw.staff_name ||
      raw.teacher_name ||
      record.staff?.name ||
      raw.staff?.name ||
      staffPerson?.name ||
      ''
    ).trim();

    rawStaffName = fetchedName || '—';
  }

  // Attach Unit descriptor cleanly to Name if not already present
  let cleanName = rawStaffName;
  if (rawStaffName !== '—' && !rawStaffName.toLowerCase().includes(recordScope.toLowerCase()) && !isCDS && !isTransport) {
    cleanName = `${rawStaffName} (${recordScope})`;
  }

  // 3. CLASS
  // Operations, CDS, and pure Discipline do not have classes; use '—'
  const isOps = [
    'Maintenance',
    'Security',
    'Transport',
    'Minders',
    'Hostel/Kitchen',
    'Hostel / Kitchen',
    'Operation'
  ].includes(recordScope);
  const isDiscipline = recordScope === 'Discipline' || recordScope === 'Mentorship';

  let cleanClass = '—';
  if (isTransport && rawBusNumber) {
    cleanClass = rawBusNumber;
  } else if (!isCDS && !isOps) {
    const rawClass = (
      record.class_name ||
      raw.class_name ||
      raw.class?.name ||
      ''
    ).trim();

    if (rawClass && !/campus|office|depot|clinic|station|main/i.test(rawClass) && !rawClass.includes(';') && !rawClass.includes('. ')) {
      cleanClass = rawClass;
    } else if (
      staffPerson?.assigned_location &&
      /^(Basic|JSS|SSS|SS|Primary|Nursery|Creche|Grade)\s*\d+\s*[A-Z]?$/i.test(staffPerson.assigned_location.trim())
    ) {
      cleanClass = staffPerson.assigned_location.trim();
    }
  }

  // 4. SUBJECT
  // Operations, CDS, and pure Discipline do not have academic subjects unless explicitly recorded; use '—'
  let cleanSubject = '—';
  if (isTransport && rawComplianceCheck) {
    cleanSubject = rawComplianceCheck;
  } else if (!isCDS && !isOps && !isDiscipline) {
    const rawSubject = (
      record.subject_name ||
      record.subject ||
      raw.subject_name ||
      raw.subject ||
      raw.subject?.name ||
      ''
    ).trim();

    if (rawSubject && rawSubject !== 'N/A' && rawSubject !== 'NA' && rawSubject !== 'General') {
      cleanSubject = rawSubject;
    } else if (staffPerson?.subjects && staffPerson.subjects !== 'N/A' && staffPerson.subjects !== 'NA') {
      cleanSubject = staffPerson.subjects.trim();
    }
  } else if (isOps && rawComplianceCheck) {
    cleanSubject = rawComplianceCheck;
  }

  // 5. EXCEPTIONS
  const cleanException = (
    record.note ||
    record.exception ||
    record.exception_text ||
    raw.exception_text ||
    raw.exception ||
    '—'
  ).trim() || '—';

  // 6. EFFECTS
  const cleanEffect = (
    record.effect ||
    record.effect_text ||
    raw.effect_text ||
    raw.effect ||
    '—'
  ).trim() || '—';

  return {
    date: cleanDate,
    name: cleanName,
    unit: recordScope,
    className: cleanClass,
    subject: cleanSubject,
    exception: cleanException,
    effect: cleanEffect,
    driverName: rawDriverName || cleanName,
    busNumber: rawBusNumber || cleanClass,
    complianceCheck: rawComplianceCheck || cleanSubject,
  };
}

/**
 * Authoritative function to filter and sort records for official report download.
 * 
 * - Scope: 'Operation' | 'CDS' | 'Primary' | 'Secondary (J&S)' | 'Nursery' | 'All'
 * - Date Range: Respects startDate and endDate (YYYY-MM-DD)
 * - Complete Dataset: Always uses full dataset (bypasses UI pagination)
 * - Order: Strictly LATEST DATE FIRST (chronological descending)
 * 
 * @param {string} scope - Official scope name
 * @param {Array} records - Full list of exception records
 * @param {Object} options - { startDate, endDate, staffRoster }
 * @returns {Array} Filtered and sorted exception records
 */
export function getDownloadRecords(scope, records = [], options = {}) {
  if (!Array.isArray(records) || records.length === 0) {
    return [];
  }

  const { startDate = '', endDate = '', staffRoster = [] } = options;
  const staffMap = buildStaffLookupMap(staffRoster);

  // 1. Filter by Scope and Date Range
  const filtered = records.filter((r) => {
    if (!r) return false;

    // Resolve date
    const raw = r.rawRecord || r;
    const recDate = r.date || raw.incident_date || raw.date || (raw.created_at ? raw.created_at.split('T')[0] : '');

    // Date range filter
    if (startDate && recDate && recDate < startDate) return false;
    if (endDate && recDate && recDate > endDate) return false;

    // Scope filter (supports individual scopes and combined scopes like Academic or All)
    if (scope && scope !== 'All') {
      if (!recordMatchesScope(r, scope, staffMap)) return false;
    }

    return true;
  });

  // 2. Order: LATEST DATE FIRST
  return filtered.sort((a, b) => {
    const rawA = a.rawRecord || a;
    const rawB = b.rawRecord || b;
    const dateA = a.date || rawA.incident_date || rawA.date || '';
    const dateB = b.date || rawB.incident_date || rawB.date || '';
    return dateB.localeCompare(dateA);
  });
}

/**
 * Validates report export parameters and prepares matching records.
 * Ensures the pipeline uses the complete dataset and respects all constraints.
 * 
 * @param {Object} params - { scope, format, records, startDate, endDate, staffRoster }
 * @returns {Object} { isValid, isEmpty, error, scope, format, records, normalizedRows, count, period }
 */
export function validateExportData({
  scope,
  format,
  records,
  startDate = '',
  endDate = '',
  staffRoster = []
}) {
  if (!scope || !OFFICIAL_REPORT_SCOPES.includes(scope)) {
    return {
      isValid: false,
      isEmpty: false,
      error: `Invalid report scope: "${scope}". Must be one of: ${OFFICIAL_REPORT_SCOPES.join(', ')}.`,
      records: [],
      normalizedRows: [],
      count: 0,
    };
  }

  const cleanFormat = (format || '').toUpperCase();
  if (cleanFormat !== 'PDF' && cleanFormat !== 'DOCX') {
    return {
      isValid: false,
      isEmpty: false,
      error: `Invalid report format: "${format}". Must be "PDF" or "DOCX".`,
      records: [],
      normalizedRows: [],
      count: 0,
    };
  }

  if (!Array.isArray(records)) {
    return {
      isValid: false,
      isEmpty: false,
      error: 'Records dataset is unavailable or has not loaded yet.',
      records: [],
      normalizedRows: [],
      count: 0,
    };
  }

  // Filter full dataset (independent of any pagination)
  const filtered = getDownloadRecords(scope, records, { startDate, endDate, staffRoster });

  if (filtered.length === 0) {
    return {
      isValid: false,
      isEmpty: true,
      error: 'No exception records found for the selected report scope and period.',
      records: [],
      normalizedRows: [],
      count: 0,
    };
  }

  // Normalize each record into the official 6-column shape
  const normalizedRows = filtered.map((rec) => normalizeReportRow(rec, staffRoster));

  return {
    isValid: true,
    isEmpty: false,
    error: null,
    scope,
    format: cleanFormat,
    records: filtered,
    normalizedRows,
    count: filtered.length,
    period: {
      startDate: startDate || null,
      endDate: endDate || null,
      label: (startDate || endDate)
        ? `From ${startDate || 'earliest record'} to ${endDate || 'latest record'}`
        : 'All available dates',
    },
  };
}
