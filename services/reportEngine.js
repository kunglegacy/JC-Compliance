/**
 * JC-Best Compliance Hub — Mathematics Calculation Engine (JavaScript / Node Service)
 * Authoritative report calculation module for web analytics and report payload generation.
 * 
 * Rules & Mathematics Contract:
 * 1. PER HEAD:
 *    perHead(totalExceptions, affectedTeachers) -> if affectedTeachers === 0 return 0; else round(totalExceptions / affectedTeachers, 2)
 * 2. UNIQUE AFFECTED STAFF:
 *    uniqueAffectedStaff(exceptions) -> counts unique non-CDS staff IDs.
 * 3. CLASS TOTALS:
 *    classTotals(streamCounts) -> stream_a + stream_b + stream_c
 * 4. SECTION TOTALS:
 *    sectionTotals(counts) -> sum(counts)
 * 5. GRAND TOTALS:
 *    grandTotals(rows) -> sum(rows)
 * 6. NUMBER WITH WORDS:
 *    numberWithWords(168) -> "168(One Hundred and Sixty Eight)"
 */

import { normalizeAcademicDepartment } from './analysisEngine.js';

const ONES = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
  'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen',
  'Seventeen', 'Eighteen', 'Nineteen'];

const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

export function numberToWords(n) {
  const num = Math.floor(Number(n) || 0);
  if (num === 0) return 'Zero';
  if (num < 0) return 'Minus ' + numberToWords(Math.abs(num));

  function _convert(val) {
    if (val < 20) return ONES[val];
    if (val < 100) {
      const t = TENS[Math.floor(val / 10)];
      const rem = ONES[val % 10];
      return `${t} ${rem}`.trim();
    }
    if (val < 1000) {
      const h = ONES[Math.floor(val / 100)] + ' Hundred';
      const rem = val % 100;
      return rem > 0 ? `${h} and ${_convert(rem)}` : h;
    }
    if (val < 1000000) {
      const th = _convert(Math.floor(val / 1000)) + ' Thousand';
      const rem = val % 1000;
      if (rem > 0) {
        return rem < 100 ? `${th} and ${_convert(rem)}` : `${th} ${_convert(rem)}`;
      }
      return th;
    }
    return String(val);
  }

  return _convert(num).trim();
}

export function numberWithWords(n) {
  const val = Math.floor(Number(n) || 0);
  return `${val}(${numberToWords(val)})`;
}

export function perHead(totalExceptions, affectedTeachers) {
  const tot = Number(totalExceptions) || 0;
  const aff = Number(affectedTeachers) || 0;
  if (!aff || aff <= 0) return 0.0;
  return Math.round((tot / aff) * 100) / 100;
}

export function uniqueAffectedStaff(exceptions = []) {
  const uniqueSet = new Set();
  (exceptions || []).forEach((e) => {
    const div = String(e.division || '').toUpperCase();
    if (div === 'CDS') return;
    const key = e.staff_id || e.staff_code || e.staff_name;
    if (key) {
      uniqueSet.add(String(key).trim().toLowerCase());
    }
  });
  return uniqueSet.size;
}

export function classTotals(streamCounts = {}) {
  let sum = 0;
  Object.values(streamCounts || {}).forEach((v) => {
    const n = Number(v) || 0;
    sum += n;
  });
  return sum;
}

export function sectionTotals(counts = []) {
  let sum = 0;
  (counts || []).forEach((c) => {
    sum += Number(c) || 0;
  });
  return sum;
}

export function disciplineTotals(disciplineRows = []) {
  let totalExceptions = 0;
  const allTeachers = new Set();

  (disciplineRows || []).forEach((r) => {
    totalExceptions += Number(r.exception_count || 0);
    const tIds = r.teacher_ids || [];
    tIds.forEach((t) => {
      if (t) allTeachers.add(String(t).trim().toLowerCase());
    });
  });

  const affectedCount = allTeachers.size || disciplineRows.reduce((acc, r) => acc + Number(r.affected_teachers || 0), 0);
  return {
    exception_count: totalExceptions,
    affected_teacher_count: affectedCount,
    per_head: perHead(totalExceptions, affectedCount),
  };
}

export function exceptionCategoryTotals(matrixRows = []) {
  const columnKeys = ['jnr_coll', 'snr_col', 'jnr_pri', 'snr_pri', 'nur'];
  const colTotals = { jnr_coll: 0, snr_col: 0, jnr_pri: 0, snr_pri: 0, nur: 0 };
  const processedRows = [];

  (matrixRows || []).forEach((row) => {
    let rowSum = 0;
    const rCopy = { ...row };
    columnKeys.forEach((k) => {
      const val = Number(rCopy[k] || 0);
      rowSum += val;
      colTotals[k] += val;
    });
    rCopy.row_total = rowSum;
    processedRows.push(rCopy);
  });

  const grandTotal = Object.values(colTotals).reduce((a, b) => a + b, 0);
  colTotals.grand_total = grandTotal;

  return {
    rows: processedRows,
    column_totals: colTotals,
    grand_total: grandTotal,
  };
}

export function buildComplianceReportPayload(exceptions = [], staffList = [], periodInfo = {}) {
  const session = periodInfo.session || '2026/2027';
  const term = periodInfo.term || 'First Term';

  const academicExceptions = (exceptions || []).filter(e => {
    const d = String(e.division || e.department || '').toUpperCase().trim();
    return d === 'ACADEMIC';
  });
  const operationsExceptions = (exceptions || []).filter(e => {
    const d = String(e.division || e.department || '').toUpperCase().trim();
    return d === 'OPERATION' || d === 'OPERATIONS';
  });
  const disciplineExceptions = (exceptions || []).filter(e => {
    const d = String(e.division || e.department || '').toUpperCase().trim();
    return d === 'DISCIPLINE';
  });
  const cdsExceptions = (exceptions || []).filter(e => {
    const d = String(e.division || e.department || '').toUpperCase().trim();
    return d === 'CDS' || (e.target_type === 'division' && (!d || d === 'CDS')) || e.isCDS;
  });

  // Grand Totals
  const totAcadExc = academicExceptions.length;
  const totAcadStaff = uniqueAffectedStaff(academicExceptions);
  const perHeadAcad = perHead(totAcadExc, totAcadStaff);

  const totOpsExc = operationsExceptions.length;
  const totOpsStaff = uniqueAffectedStaff(operationsExceptions);
  const perHeadOps = perHead(totOpsExc, totOpsStaff);

  const totDiscExc = disciplineExceptions.length;
  const totDiscStaff = uniqueAffectedStaff(disciplineExceptions);
  const perHeadDisc = perHead(totDiscExc, totDiscStaff);

  const totCdsExc = cdsExceptions.length;
  const totAllExc = totAcadExc + totOpsExc + totDiscExc + totCdsExc;

  // Comparative helper
  function makeTermStat(exList = []) {
    const cnt = exList.length;
    const stCnt = uniqueAffectedStaff(exList);
    return {
      exception_count: cnt,
      affected_teacher_count: stCnt,
      per_head: perHead(cnt, stCnt),
    };
  }

  const jnrPriEx = academicExceptions.filter(e => String(e.school_section || e.class_section || '').toUpperCase() === 'JUNIOR_PRIMARY');
  const snrPriEx = academicExceptions.filter(e => String(e.school_section || e.class_section || '').toUpperCase() === 'SENIOR_PRIMARY');
  const nurEx = academicExceptions.filter(e => String(e.school_section || e.class_section || '').toUpperCase() === 'NURSERY');

  const jnrSecEx = academicExceptions.filter(e => String(e.school_section || e.class_section || '').toUpperCase() === 'JUNIOR_SECONDARY');
  const snrSecEx = academicExceptions.filter(e => String(e.school_section || e.class_section || '').toUpperCase() === 'SENIOR_SECONDARY');

  // Discipline Groupings
  const discMap = {};
  academicExceptions.forEach((e) => {
    const sec = String(e.school_section || e.class_section || '').toUpperCase();
    if (sec.includes('PRIMARY') || sec.includes('NURSERY')) {
      const disc = normalizeAcademicDepartment(e.discipline_group, e.subject_name || e.subject, e.staff_group || e.role);
      if (!discMap[disc]) discMap[disc] = [];
      discMap[disc].push(e);
    }
  });

  const primaryDisciplineRows = Object.entries(discMap).map(([disc, exList]) => {
    const cnt = exList.length;
    const tCounts = {};
    exList.forEach((ex) => {
      const tName = ex.staff_name || ex.teacher_name || 'Teacher';
      tCounts[tName] = (tCounts[tName] || 0) + 1;
    });
    const formattedTeachers = Object.entries(tCounts).map(([name, c]) => `${name}(${c})`).join(', ');
    const affTeachers = Object.keys(tCounts).length;
    return {
      discipline: disc,
      teacher_names: formattedTeachers,
      exception_count: cnt,
      affected_teachers: affTeachers,
      per_head: perHead(cnt, affTeachers),
      teacher_ids: Object.keys(tCounts),
    };
  });

  const priDiscTotals = disciplineTotals(primaryDisciplineRows);

  // Class Counts
  const classCounts = {};
  academicExceptions.forEach((e) => {
    const cName = e.class_name || 'Unassigned';
    classCounts[cName] = (classCounts[cName] || 0) + 1;
  });

  function getStreamCount(cBase, arm) {
    return classCounts[`${cBase}${arm}`] || classCounts[`${cBase} ${arm}`] || 0;
  }

  const juniorPrimaryClasses = ['Basic 1', 'Basic 2', 'Basic 3'].map((grade) => {
    const a = getStreamCount(grade, 'A');
    const b = getStreamCount(grade, 'B');
    const c = getStreamCount(grade, 'C');
    return {
      grade,
      stream_a: a,
      stream_b: b,
      stream_c: c,
      total: classTotals({ a, b, c }),
    };
  });

  const seniorPrimaryClasses = ['Basic 4', 'Basic 5', 'Basic 6'].map((grade) => {
    const a = getStreamCount(grade, 'A');
    const b = getStreamCount(grade, 'B');
    const c = getStreamCount(grade, 'C');
    return {
      grade,
      stream_a: a,
      stream_b: b,
      stream_c: c,
      total: classTotals({ a, b, c }),
    };
  });

  const jnrPriTotal = sectionTotals(juniorPrimaryClasses.map(r => r.total));
  const snrPriTotal = sectionTotals(seniorPrimaryClasses.map(r => r.total));
  const nurTotal = nurEx.length;

  // Secondary Discipline
  const secDiscMap = {};
  academicExceptions.forEach((e) => {
    const sec = String(e.school_section || e.class_section || '').toUpperCase();
    if (sec.includes('SECONDARY') || sec.includes('JSS') || sec.includes('SSS') || (!sec.includes('PRIMARY') && !sec.includes('NURSERY'))) {
      const disc = normalizeAcademicDepartment(e.discipline_group, e.subject_name || e.subject, e.staff_group || e.role);
      if (!secDiscMap[disc]) secDiscMap[disc] = [];
      secDiscMap[disc].push(e);
    }
  });

  const secondaryDisciplineRows = Object.entries(secDiscMap).map(([disc, exList]) => {
    const cnt = exList.length;
    const tCounts = {};
    exList.forEach((ex) => {
      const tName = ex.staff_name || ex.teacher_name || 'Teacher';
      tCounts[tName] = (tCounts[tName] || 0) + 1;
    });
    const formattedTeachers = Object.entries(tCounts).map(([name, c]) => `${name}(${c})`).join(', ');
    const affTeachers = Object.keys(tCounts).length;
    return {
      discipline: disc,
      teacher_names: formattedTeachers,
      exception_count: cnt,
      affected_teachers: affTeachers,
      per_head: perHead(cnt, affTeachers),
      teacher_ids: Object.keys(tCounts),
    };
  });

  const secDiscTotals = disciplineTotals(secondaryDisciplineRows);

  // Exception Matrix
  const categoryMap = {};
  academicExceptions.forEach((e) => {
    const cat = e.check_name || e.compliance_check || e.category || 'General';
    const sec = String(e.school_section || e.class_section || '').toUpperCase();
    if (!categoryMap[cat]) {
      categoryMap[cat] = { jnr_coll: 0, snr_col: 0, jnr_pri: 0, snr_pri: 0, nur: 0 };
    }
    if (sec.includes('JUNIOR_SECONDARY') || sec.includes('JSS')) categoryMap[cat].jnr_coll += 1;
    else if (sec.includes('SENIOR_SECONDARY') || sec.includes('SSS')) categoryMap[cat].snr_col += 1;
    else if (sec.includes('JUNIOR_PRIMARY')) categoryMap[cat].jnr_pri += 1;
    else if (sec.includes('SENIOR_PRIMARY')) categoryMap[cat].snr_pri += 1;
    else if (sec.includes('NURSERY')) categoryMap[cat].nur += 1;
    else categoryMap[cat].snr_col += 1;
  });

  const matrixInput = Object.entries(categoryMap).map(([cat, counts]) => ({ category: cat, ...counts }));
  const matrixAnalysis = exceptionCategoryTotals(matrixInput);

  return {
    period: {
      session,
      term,
      report_month_year: periodInfo.report_month_year || 'JULY, 2026',
      scheduled_date: periodInfo.scheduled_date || 'JULY, 2026',
      venue: periodInfo.venue || 'ACADEMIC CONFERENCE HALL',
      presented_by: periodInfo.presented_by || 'DIRECTORATE OF QUALITY ASSURANCE',
    },
    grand_totals: {
      academic_exceptions: totAcadExc,
      academic_exceptions_text: numberWithWords(totAcadExc),
      academic_affected_staff: totAcadStaff,
      academic_affected_staff_text: numberWithWords(totAcadStaff),
      academic_per_head: perHeadAcad,
      operations_exceptions: totOpsExc,
      operations_exceptions_text: numberWithWords(totOpsExc),
      operations_affected_staff: totOpsStaff,
      operations_affected_staff_text: numberWithWords(totOpsStaff),
      operations_per_head: perHeadOps,
      discipline_exceptions: totDiscExc,
      discipline_exceptions_text: numberWithWords(totDiscExc),
      discipline_affected_staff: totDiscStaff,
      discipline_affected_staff_text: numberWithWords(totDiscStaff),
      discipline_per_head: perHeadDisc,
      cds_exceptions: totCdsExc,
      cds_exceptions_text: numberWithWords(totCdsExc),
      total_all_exceptions: totAllExc,
      total_all_exceptions_text: numberWithWords(totAllExc),
    },
    comparative_analysis: {
      junior_primary: makeTermStat(jnrPriEx),
      senior_primary: makeTermStat(snrPriEx),
      primary_combined: makeTermStat([...jnrPriEx, ...snrPriEx, ...nurEx]),
      junior_secondary: makeTermStat(jnrSecEx),
      senior_secondary: makeTermStat(snrSecEx),
      secondary_combined: makeTermStat([...jnrSecEx, ...snrSecEx]),
    },
    primary_discipline: {
      rows: primaryDisciplineRows,
      total_exceptions: priDiscTotals.exception_count,
      total_affected_teachers: priDiscTotals.affected_teacher_count,
      total_per_head: priDiscTotals.per_head,
    },
    secondary_discipline: {
      rows: secondaryDisciplineRows,
      total_exceptions: secDiscTotals.exception_count,
      total_affected_teachers: secDiscTotals.affected_teacher_count,
      total_per_head: secDiscTotals.per_head,
    },
    primary_classes: {
      nursery_total: nurTotal,
      junior_primary: juniorPrimaryClasses,
      junior_primary_total: jnrPriTotal,
      senior_primary: seniorPrimaryClasses,
      senior_primary_total: snrPriTotal,
      primary_grand_total: nurTotal + jnrPriTotal + snrPriTotal,
    },
    exception_matrices: matrixAnalysis,
    academic_details: academicExceptions.map((e, idx) => ({
      s_n: idx + 1,
      date: e.date || e.incident_date || '',
      staff_name: e.staff_name || e.teacher_name || 'Staff Member',
      staff_code: e.staff_code || '',
      class_name: e.class_name || 'Unassigned',
      subject_name: e.subject_name || e.subject || 'General',
      check_name: e.check_name || e.compliance_check || 'General',
      exception_text: e.exception_text || e.exception || '',
      effect_text: e.effect_text || e.effect || '',
      evidence_url: e.evidence_url || '',
    })),
    operations_details: operationsExceptions.map((e, idx) => ({
      s_n: idx + 1,
      date: e.date || e.incident_date || '',
      staff_name: e.staff_name || 'Operations',
      staff_code: e.staff_code || '',
      location: e.assigned_location || e.class_name || 'Station',
      check_name: e.check_name || e.compliance_check || 'General Operations',
      operation_exception: e.exception_text || e.exception || '',
      effect_text: e.effect_text || e.effect || '',
      evidence_url: e.evidence_url || '',
    })),
    discipline_details: disciplineExceptions.map((e, idx) => ({
      s_n: idx + 1,
      date: e.date || e.incident_date || '',
      staff_name: e.staff_name || 'Discipline Unit',
      staff_code: e.staff_code || '',
      class_name: e.class_name || 'Campus Wide',
      check_name: e.check_name || e.compliance_check || 'Student Discipline',
      exception_text: e.exception_text || e.exception || '',
      effect_text: e.effect_text || e.effect || '',
      evidence_url: e.evidence_url || '',
    })),
    cds_details: cdsExceptions.map((e, idx) => ({
      s_n: idx + 1,
      date: e.date || e.incident_date || '',
      cds_area: e.check_group || e.category || 'CBT',
      check_name: e.check_name || e.compliance_check || 'CDS System',
      exception_text: e.exception_text || e.exception || '',
      effect_text: e.effect_text || e.effect || '',
      evidence_url: e.evidence_url || '',
    })),
  };
}
