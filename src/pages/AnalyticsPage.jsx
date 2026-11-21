import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  ShieldCheck,
  Archive,
  Database,
  AlertCircle,
  CheckCircle2,
  Calendar,
} from 'lucide-react';
import {
  fetchStaff,
  fetchComplianceReports,
  fetchSettings,
  fetchTermSnapshots,
  deriveStaffSeverity,
  supabase,
  authService,
} from '../services/supabase';
import {
  AnalysisHeader,
  AnalysisFilters,
  AnalysisSummary,
  CdsAnalysis,
  AnalysisDrillDownModal,
  AnalysisExportCard,
  SlideConfigModal,
  TermComparison,
  SectionAnalysis,
  DisciplineAnalysis,
  ClassAnalysis,
  ExceptionCategoryAnalysis,
  AnalysisTable,
} from '../components/analysis';
import { OverallTrend } from '../components/trend';
import {
  buildTermAnalysis,
  resolveAuthoritativeTermAnalysis,
  normalizeCategory,
  resolveSection,
  normalizeAcademicDepartment,
} from '../services/analysisEngine';
import { generateCompliancePptx } from '../services/pptxExportService';
import { useAuth } from '../contexts/AuthContext';

/**
 * AnalyticsPage (STAGE 6 — Permanent Historical Snapshot Integration)
 * 
 * Single Authoritative Source Contract:
 * - Active or unpurged terms: Strictly uses authoritative raw compliance records.
 * - Purged historical terms: Strictly uses verified durable aggregate snapshots.
 * - Zero double-counting: Never counts raw records and aggregates simultaneously.
 * - Preserves all division totals, section breakdowns, per-head metrics, and PPTX exports.
 */
export default function AnalyticsPage({
  onNavigateTab,
  staffList: propStaffList,
  reports: propReports,
  settings: propSettings,
}) {
  const { isAdmin, isStandard, canGenerateAdminPptx } = useAuth();

  // Core data states
  const [staffList, setStaffList] = useState(propStaffList || []);
  const [reports, setReports] = useState(propReports || []);
  const [settings, setSettings] = useState(propSettings || null);
  const [snapshots, setSnapshots] = useState([]);
  const [selectedTermOverride, setSelectedTermOverride] = useState(null);
  const [loading, setLoading] = useState(!propStaffList || !propReports);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  // Active Analysis Date Filter
  const [datePreset, setDatePreset] = useState('all');
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');

  // Interactive Analytical Drilldown Modal State
  const [drillContext, setDrillContext] = useState(null);

  // Export Slide Modal State
  const [isConfigModalOpen, setIsConfigModalOpen] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [downloadError, setDownloadError] = useState(null);
  const [downloadSuccess, setDownloadSuccess] = useState(false);

  // Sync props
  useEffect(() => {
    if (propStaffList && propStaffList.length > 0) setStaffList(propStaffList);
  }, [propStaffList]);

  useEffect(() => {
    if (propReports && propReports.length > 0) setReports(propReports);
  }, [propReports]);

  useEffect(() => {
    if (propSettings) setSettings(propSettings);
  }, [propSettings]);

  // Load authoritative data
  const loadData = useCallback(async () => {
    setError(null);
    try {
      const [staffRes, repRes, setRes, snapRes] = await Promise.all([
        fetchStaff(),
        fetchComplianceReports(),
        fetchSettings(),
        fetchTermSnapshots(),
      ]);
      if (staffRes?.data) setStaffList(staffRes.data);
      if (repRes?.data) setReports(repRes.data);
      if (setRes) setSettings(setRes);
      if (snapRes?.data) setSnapshots(snapRes.data);
    } catch (err) {
      console.error('Error loading Analysis data:', err);
      setError(err?.message || 'Failed to load analysis records from database');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (!propStaffList || !propReports || propStaffList.length === 0 || !settings) {
      loadData();
    } else {
      // Load snapshots even if props provided
      fetchTermSnapshots().then((res) => {
        if (res?.data) setSnapshots(res.data);
      });
    }
  }, [loadData, propStaffList, propReports, settings]);

  const activeDateBounds = useMemo(() => {
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];

    if (datePreset === 'this_month') {
      const y = today.getFullYear();
      const m = String(today.getMonth() + 1).padStart(2, '0');
      return { startDate: `${y}-${m}-01`, endDate: todayStr, label: `This Month (${y}-${m}-01 to ${todayStr})` };
    }
    if (datePreset === 'last_30_days') {
      const past = new Date(today);
      past.setDate(past.getDate() - 30);
      const pastStr = past.toISOString().split('T')[0];
      return { startDate: pastStr, endDate: todayStr, label: `Last 30 Days (${pastStr} to ${todayStr})` };
    }
    if (datePreset === 'custom') {
      return {
        startDate: customStartDate || null,
        endDate: customEndDate || null,
        label: customStartDate && customEndDate
          ? `${customStartDate} to ${customEndDate}`
          : customStartDate
          ? `From ${customStartDate}`
          : customEndDate
          ? `Until ${customEndDate}`
          : 'Custom Range',
      };
    }
    return { startDate: null, endDate: null, label: 'All Time' };
  }, [datePreset, customStartDate, customEndDate]);

  // Secure staff roster
  const secureStaffList = useMemo(() => {
    if (!isStandard) return staffList;
    return staffList.filter((s) => {
      const sIdStr = s.id ? String(s.id) : null;
      const sCode = s.staff_code ? s.staff_code.trim().toLowerCase() : null;
      const sName = s.name ? s.name.trim().toLowerCase() : null;
      return reports.some((r) =>
        (sIdStr && r.staff_id && String(r.staff_id) === sIdStr) ||
        (sCode && r.staff_code && r.staff_code.trim().toLowerCase() === sCode) ||
        (sName && r.staff_name && r.staff_name.trim().toLowerCase() === sName)
      );
    });
  }, [staffList, reports, isStandard]);

  const activeStaffDenominator = useMemo(() => {
    return secureStaffList.filter((s) => {
      if (s.status === 'Archived') return false;
      const dept = (s.department || (s.category === 'Academic Staff' ? 'Academic' : 'Operations') || '').trim().toLowerCase();
      return dept === 'academic' || dept === 'operations';
    });
  }, [secureStaffList]);

  const totalStaffCount = activeStaffDenominator.length;

  const isReportInDateRange = useCallback((r) => {
    const d = r.incident_date || r.date || (r.created_at ? r.created_at.split('T')[0] : null);
    if (!d) return true;
    if (activeDateBounds.startDate && d < activeDateBounds.startDate) return false;
    if (activeDateBounds.endDate && d > activeDateBounds.endDate) return false;
    return true;
  }, [activeDateBounds]);

  // Canonical Session and Term
  const term = selectedTermOverride || settings?.current_term || 'First Term';
  const session = settings?.current_session || '2026/2027';

  // Strict Term Isolation helper
  const isReportInTerm = useCallback((r) => {
    if (!term || term === 'ALL') return true;
    return String(r.term || '').trim().toLowerCase() === term.toLowerCase();
  }, [term]);

  const filteredStaffExceptions = useMemo(() => {
    return reports.filter((r) => {
      if (r.status === 'Compliant') return false;
      if (!isReportInTerm(r)) return false;
      const isCds = r.division === 'CDS' || (r.target_type === 'division' && (!r.division || r.division === 'CDS'));
      if (isCds) return false;
      return isReportInDateRange(r);
    });
  }, [reports, isReportInTerm, isReportInDateRange]);

  const filteredCdsExceptions = useMemo(() => {
    return reports.filter((r) => {
      if (r.status === 'Compliant') return false;
      if (!isReportInTerm(r)) return false;
      const isCds = r.division === 'CDS' || (r.target_type === 'division' && (!r.division || r.division === 'CDS'));
      if (!isCds) return false;
      return isReportInDateRange(r);
    });
  }, [reports, isReportInTerm, isReportInDateRange]);

  const allFilteredExceptions = useMemo(() => {
    return reports.filter((r) => r.status !== 'Compliant' && isReportInTerm(r) && isReportInDateRange(r));
  }, [reports, isReportInTerm, isReportInDateRange]);

  // Executive Staff Severity Analysis
  const analyzedStaffList = useMemo(() => {
    return activeStaffDenominator.map((person) => {
      const derived = deriveStaffSeverity(person, filteredStaffExceptions, {
        startDate: activeDateBounds.startDate,
        endDate: activeDateBounds.endDate,
      });
      return {
        ...person,
        derivedCount: derived.count,
        derivedSeverity: derived.severity,
      };
    });
  }, [activeStaffDenominator, filteredStaffExceptions, activeDateBounds]);

  const staffWithExceptions = useMemo(() => analyzedStaffList.filter((s) => s.derivedCount > 0), [analyzedStaffList]);
  const staffWithoutExceptions = useMemo(() => analyzedStaffList.filter((s) => s.derivedCount === 0), [analyzedStaffList]);
  const criticalStaff = useMemo(() => analyzedStaffList.filter((s) => s.derivedSeverity === 'Critical'), [analyzedStaffList]);
  const highStaff = useMemo(() => analyzedStaffList.filter((s) => s.derivedSeverity === 'High'), [analyzedStaffList]);
  const mediumStaff = useMemo(() => analyzedStaffList.filter((s) => s.derivedSeverity === 'Medium'), [analyzedStaffList]);
  const lowStaff = useMemo(() => analyzedStaffList.filter((s) => s.derivedSeverity === 'Low'), [analyzedStaffList]);

  const totalExceptionsCount = allFilteredExceptions.length;
  const isSeenRecord = useCallback((r) => Boolean(r.review_status === 'SEEN' || r.is_seen === true || r.seen === true || r.seen_at), []);
  const seenExceptions = useMemo(() => allFilteredExceptions.filter(isSeenRecord), [allFilteredExceptions, isSeenRecord]);
  const unseenExceptions = useMemo(() => allFilteredExceptions.filter((r) => !isSeenRecord(r)), [allFilteredExceptions, isSeenRecord]);

  const totalCdsExceptions = filteredCdsExceptions.length;
  const seenCdsExceptions = useMemo(() => filteredCdsExceptions.filter(isSeenRecord), [filteredCdsExceptions, isSeenRecord]);
  const unseenCdsExceptions = useMemo(() => filteredCdsExceptions.filter((r) => !isSeenRecord(r)), [filteredCdsExceptions, isSeenRecord]);

  const staffWithExceptionsPct = totalStaffCount > 0 ? Math.round((staffWithExceptions.length / totalStaffCount) * 100) : 0;
  const noExceptionsPct = totalStaffCount > 0 ? Math.round((staffWithoutExceptions.length / totalStaffCount) * 100) : 0;

  const reportingPeriod = activeDateBounds.label || `${term} ${session}`;

  // Isolate term-specific raw reports to ensure zero cross-term bleeding in analysis
  const termRawReports = useMemo(() => {
    return reports.filter((r) => {
      if (!r || r.status === 'Compliant') return false;
      if (!term || term === 'ALL') return true;
      return String(r.term || '').trim().toLowerCase() === term.toLowerCase();
    });
  }, [reports, term]);

  // Master Authoritative Analysis Calculation Engine Execution (STAGE 6)
  // Single Authoritative Source Rule:
  // - If raw records exist for this term: strictly uses raw data and buildTermAnalysis.
  // - If term raw records are purged: strictly uses verified durable snapshot.
  // - Never mixes raw records and snapshot aggregates for the same term.
  const termAnalysis = useMemo(() => {
    return resolveAuthoritativeTermAnalysis({
      term,
      session,
      reports: termRawReports,
      staffList,
      snapshots,
      options: {
        session,
        term,
        period: reportingPeriod,
        dateRange: activeDateBounds.startDate || activeDateBounds.endDate ? {
          startDate: activeDateBounds.startDate,
          endDate: activeDateBounds.endDate,
        } : null,
      },
    });
  }, [termRawReports, staffList, session, term, snapshots, reportingPeriod, activeDateBounds]);

  // Derive display summary metrics seamlessly whether active source is Raw Data or Snapshot
  const summaryDisplay = useMemo(() => {
    const gt = termAnalysis?.grandTotals;
    if (termAnalysis?.isHydratedFromDurableSnapshot && gt) {
      return {
        totalStaffCount: gt.staffDenominator || totalStaffCount,
        staffWithExceptionsCount: gt.affectedStaffCount || 0,
        staffWithExceptionsPct: gt.staffWithExceptionsPercentage || 0,
        staffWithoutExceptionsCount: gt.cleanStaffCount || 0,
        noExceptionsPct: gt.cleanStaffPercentage || 0,
        totalExceptionsCount: gt.totalExceptions || 0,
        filteredStaffExceptionsCount: (gt.academicExceptions || 0) + (gt.operationsExceptions || 0) + (gt.disciplineExceptions || 0),
        filteredCdsExceptionsCount: gt.cdsExceptions || 0,
        academicCount: gt.academicExceptions || 0,
        operationsCount: gt.operationsExceptions || 0,
        disciplineCount: gt.disciplineExceptions || 0,
        seenCdsCount: gt.cdsExceptions || 0,
        unseenCdsCount: 0,
      };
    }
    return {
      totalStaffCount,
      staffWithExceptionsCount: staffWithExceptions.length,
      staffWithExceptionsPct,
      staffWithoutExceptionsCount: staffWithoutExceptions.length,
      noExceptionsPct,
      totalExceptionsCount,
      filteredStaffExceptionsCount: filteredStaffExceptions.length,
      filteredCdsExceptionsCount: filteredCdsExceptions.length,
      academicCount: gt?.academicExceptions ?? filteredStaffExceptions.filter((r) => (r.division || '').toLowerCase() === 'academic').length,
      operationsCount: gt?.operationsExceptions ?? filteredStaffExceptions.filter((r) => (r.division || '').toLowerCase().startsWith('operat')).length,
      disciplineCount: gt?.disciplineExceptions ?? filteredStaffExceptions.filter((r) => (r.division || '').toLowerCase() === 'discipline').length,
      seenCdsCount: seenCdsExceptions.length,
      unseenCdsCount: unseenCdsExceptions.length,
    };
  }, [termAnalysis, totalStaffCount, staffWithExceptions, staffWithExceptionsPct, staffWithoutExceptions, noExceptionsPct, totalExceptionsCount, filteredStaffExceptions, filteredCdsExceptions, seenCdsExceptions, unseenCdsExceptions]);

  // PPT Export default slide headings
  const [reportConfig, setReportConfig] = useState({
    slide2Heading: 'PRESENTATION OF DATA ON EXCEPTIONS - COMPARATIVE ANALYSIS',
    slide3Heading: `PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS DISCIPLINE - ${String(term).toUpperCase()}`,
    slide4Heading: `PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS CLASSES - ${String(term).toUpperCase()}`,
    slide5Heading: `PRESENTATION OF EXCEPTIONS ANALYSIS IN VARIOUS DISCIPLINE - ${String(term).toUpperCase()}`,
  });

  useEffect(() => {
    if (settings?.current_term) {
      const upTerm = String(settings.current_term).toUpperCase();
      setReportConfig({
        slide2Heading: 'PRESENTATION OF DATA ON EXCEPTIONS - COMPARATIVE ANALYSIS',
        slide3Heading: `PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS DISCIPLINE - ${upTerm}`,
        slide4Heading: `PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS CLASSES - ${upTerm}`,
        slide5Heading: `PRESENTATION OF EXCEPTIONS ANALYSIS IN VARIOUS DISCIPLINE - ${upTerm}`,
      });
    }
  }, [settings?.current_term]);

  const isHeadingFormValid = useMemo(() => {
    return Boolean(
      reportConfig.slide2Heading?.trim() &&
      reportConfig.slide3Heading?.trim() &&
      reportConfig.slide4Heading?.trim() &&
      reportConfig.slide5Heading?.trim()
    );
  }, [reportConfig]);

  // ---------------------------------------------------------------------------
  // INTERACTIVE ANALYTICAL DRILLDOWNS (NO RAW REPORTS / NO CASE DOSSIERS)
  // ---------------------------------------------------------------------------
  const handleDrillCategory = (categoryName) => {
    const matchingExceptions = allFilteredExceptions.filter((e) => {
      const div = String(e.division || '').toUpperCase();
      return div !== 'CDS' && normalizeCategory(e) === categoryName;
    });
    setDrillContext({
      type: 'category',
      title: `${categoryName}`,
      subtitle: `${matchingExceptions.length} total recorded occurrences across academic divisions`,
      exceptions: matchingExceptions,
      staffMap: staffList,
    });
  };

  const handleDrillSection = (sectionKey, sectionLabel) => {
    const matchingExceptions = allFilteredExceptions.filter((e) => {
      const div = String(e.division || '').toUpperCase();
      return div !== 'CDS' && resolveSection(e) === sectionKey;
    });
    setDrillContext({
      type: 'section',
      title: `${sectionLabel || sectionKey} Section`,
      subtitle: `${matchingExceptions.length} total exceptions in this school section`,
      exceptions: matchingExceptions,
      staffMap: staffList,
    });
  };

  const handleDrillDiscipline = (disciplineName) => {
    const matchingExceptions = allFilteredExceptions.filter((e) => {
      const disc = normalizeAcademicDepartment(e.discipline_group, e.subject_name || e.subject, e.staff_group || e.role);
      return disc.toLowerCase() === String(disciplineName).toLowerCase();
    });
    setDrillContext({
      type: 'discipline',
      title: `${disciplineName} Department`,
      subtitle: `${matchingExceptions.length} exceptions recorded in this subject discipline`,
      exceptions: matchingExceptions,
      staffMap: staffList,
    });
  };

  const handleDrillKpi = (kpiType, label) => {
    let subset = [];
    if (kpiType === 'CRITICAL_STAFF') {
      const critStaffIds = new Set(criticalStaff.map(s => s.id || s.staff_code || s.name));
      subset = allFilteredExceptions.filter(e => critStaffIds.has(e.staff_id || e.staff_code || e.staff_name));
    } else if (kpiType === 'STAFF_WITH_EXCEPTIONS') {
      subset = filteredStaffExceptions;
    } else if (kpiType === 'CDS_TOTAL') {
      subset = filteredCdsExceptions;
    } else {
      subset = allFilteredExceptions;
    }
    setDrillContext({
      type: 'kpi',
      title: label || 'Aggregate Metric Distribution',
      subtitle: `${subset.length} total exceptions represented in this analytical dimension`,
      exceptions: subset,
      staffMap: staffList,
    });
  };

  // PowerPoint Report Download Handler (STAGE 9: Connected to Shared Analysis Engine)
  const handleDownloadComplianceReport = async (overrideHeadings = null) => {
    setIsGenerating(true);
    setDownloadError(null);
    setDownloadSuccess(false);

    if (!isAdmin && !canGenerateAdminPptx) {
      setDownloadError('Unauthorized: Executive presentation export is restricted to Administrative Directorate accounts.');
      setIsGenerating(false);
      return;
    }

    if (!termAnalysis) {
      setDownloadError('Analysis dataset is not yet initialized.');
      setIsGenerating(false);
      return;
    }

    const headingsToUse = overrideHeadings || reportConfig;
    const templateVariables = {
      ACADEMIC_SESSION: session,
      REPORTING_PERIOD: reportingPeriod,
      ACADEMIC_TERM: term,
      SLIDE_02_HEADING: headingsToUse.slide2Heading,
      SLIDE_03_HEADING: headingsToUse.slide3Heading,
      SLIDE_04_HEADING: headingsToUse.slide4Heading,
      SLIDE_05_HEADING: headingsToUse.slide5Heading,
    };

    try {
      const sanitizedPeriod = reportingPeriod.replace(/[^a-zA-Z0-9_-]/g, '_');
      const filename = `JC-BEST-Compliance-Report-${sanitizedPeriod}.pptx`;

      // 1. If term is hydrated from a verified durable snapshot (raw data was purged), generate official presentation directly
      if (termAnalysis?.isHydratedFromDurableSnapshot) {
        const pptx = await generateCompliancePptx({
          analysis: termAnalysis,
          filteredExceptions: [],
          staffList,
          templateVariables,
        });
        const outBlob = await pptx.write({ outputType: 'blob' });
        const url = window.URL.createObjectURL(outBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);

        setDownloadSuccess(true);
        setIsConfigModalOpen(false);
        setTimeout(() => setDownloadSuccess(false), 4000);
        return;
      }

      // 2. Request official PowerPoint presentation generated from public/analytical report template.pptx
      let token = '';
      try {
        const currentSession = await authService.getSession();
        token = currentSession?.access_token || '';
      } catch (_) {
        const { data: authData } = await supabase.auth.getSession();
        token = authData?.session?.access_token || '';
      }

      let downloadBlob = null;

      try {
        const response = await fetch('/api/reports/compliance-pptx', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({
            term,
            session,
            reportingPeriod,
            slide2Heading: headingsToUse.slide2Heading,
            slide3Heading: headingsToUse.slide3Heading,
            slide4Heading: headingsToUse.slide4Heading,
            slide5Heading: headingsToUse.slide5Heading,
            templateVariables,
            exceptions: allFilteredExceptions,
            staff: staffList,
          }),
        });

        if (response.ok) {
          downloadBlob = await response.blob();
        } else {
          // If server template endpoint fails, fallback to client-side presentation generator
          const pptx = await generateCompliancePptx({
            analysis: termAnalysis,
            filteredExceptions: allFilteredExceptions,
            staffList,
            templateVariables,
          });
          downloadBlob = await pptx.write({ outputType: 'blob' });
        }
      } catch (fetchErr) {
        // Fallback to client-side presentation generator
        const pptx = await generateCompliancePptx({
          analysis: termAnalysis,
          filteredExceptions: allFilteredExceptions,
          staffList,
          templateVariables,
        });
        downloadBlob = await pptx.write({ outputType: 'blob' });
      }

      // Download the template-populated PPTX directly
      if (downloadBlob && downloadBlob.size > 0) {
        const url = window.URL.createObjectURL(downloadBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
      } else {
        throw new Error('Generated PowerPoint file from template was empty.');
      }

      setDownloadSuccess(true);
      setIsConfigModalOpen(false);
      setTimeout(() => setDownloadSuccess(false), 4000);
    } catch (err) {
      console.error('PPTX Export error:', err);
      setDownloadError(err.message || 'Failed to generate compliance report presentation.');
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="space-y-8 pb-16 font-sans overflow-x-hidden max-w-7xl mx-auto">
      {/* 1. ANALYSIS HEADER & 2. SESSION / TERM / FILTER CONTROLS */}
      <AnalysisHeader
        onSyncDatabase={() => {
          setRefreshing(true);
          loadData();
        }}
        refreshing={refreshing}
        loading={loading}
      >
        {/* Term Selection Bar & Single Authoritative Source Status */}
        <div className="relative z-10 mt-6 pt-5 border-t border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-slate-300 uppercase tracking-widest">Academic Term:</span>
            <div className="flex items-center p-1 bg-white/5 backdrop-blur-sm rounded-xl border border-white/10">
              {['First Term', 'Second Term', 'Third Term'].map((tName) => {
                const isSelected = term === tName;
                const hasSnap = snapshots.some((s) => s.term === tName && (s.status === 'VERIFIED' || s.reconciliation_status === 'VERIFIED'));
                const hasRaw = reports.some((r) => r.status !== 'Compliant' && String(r.term || '').trim().toLowerCase() === tName.toLowerCase());

                return (
                  <button
                    key={tName}
                    type="button"
                    onClick={() => setSelectedTermOverride(tName)}
                    className={`rounded-lg px-3.5 py-1.5 text-[11px] font-black uppercase tracking-wider transition-all cursor-pointer flex items-center gap-1.5 ${
                      isSelected
                        ? 'bg-white text-slate-900 shadow-sm'
                        : 'text-slate-400 hover:text-white hover:bg-white/5'
                    }`}
                  >
                    <span>{tName}</span>
                    {hasSnap && !hasRaw && (
                      <span className={`inline-block w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-indigo-600' : 'bg-indigo-400'}`} title="Verified Snapshot" />
                    )}
                    {hasRaw && (
                      <span className={`inline-block w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-emerald-600' : 'bg-emerald-400'}`} title="Live Raw Records" />
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs font-semibold">
            {termAnalysis?.isHydratedFromDurableSnapshot ? (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-bold">
                <ShieldCheck size={14} className="text-indigo-400" />
                Verified Permanent Snapshot
              </span>
            ) : termAnalysis?.isFromRawData && termAnalysis.grandTotals?.totalExceptions > 0 ? (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-bold">
                <Database size={14} className="text-emerald-400" />
                Live Raw Records
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-500/20 text-slate-300 border border-slate-500/30 font-bold">
                No Records Recorded
              </span>
            )}
          </div>
        </div>

        <AnalysisFilters
          activeDateLabel={activeDateBounds.label}
          datePreset={datePreset}
          onSelectPreset={(preset) => setDatePreset(preset)}
          customStartDate={customStartDate}
          customEndDate={customEndDate}
          onCustomStartDateChange={setCustomStartDate}
          onCustomEndDateChange={setCustomEndDate}
        />
      </AnalysisHeader>

      {/* STAGE 6: PERMANENT HISTORICAL SNAPSHOT BANNER */}
      {termAnalysis?.isHydratedFromDurableSnapshot && (
        <div className="rounded-2xl border border-indigo-200/90 bg-gradient-to-r from-indigo-50/80 via-blue-50/60 to-indigo-50/80 p-5 shadow-sm text-slate-800">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3.5">
              <div className="p-2.5 rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-200 shrink-0 mt-0.5">
                <ShieldCheck size={22} />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs font-black uppercase tracking-wider text-indigo-700 bg-indigo-100/90 px-2.5 py-0.5 rounded-md border border-indigo-200">
                    Verified Permanent Snapshot
                  </span>
                  <span className="text-xs font-bold text-slate-600">
                    Status: <strong className="text-emerald-700 font-black">{termAnalysis.snapshotStatus || 'VERIFIED'}</strong>
                  </span>
                  {termAnalysis.snapshotVersion && (
                    <span className="text-xs font-semibold text-slate-500">
                      Version {termAnalysis.snapshotVersion}
                    </span>
                  )}
                  {termAnalysis.officialTermEndDate && (
                    <span className="text-xs font-semibold text-slate-500">
                      · Official Term End: <strong className="text-slate-700">{termAnalysis.officialTermEndDate}</strong>
                    </span>
                  )}
                </div>
                <h3 className="text-base font-black text-slate-800 mt-1">
                  Authoritative Historical Snapshot for {term} ({session})
                </h3>
                <p className="text-xs text-slate-600 mt-0.5 leading-relaxed font-medium max-w-4xl">
                  Case-level exemption records and evidence attachments for this completed term were safely purged following archive verification. All institutional metrics, division subtotals, school section breakdowns, per-head teacher ratios, and PowerPoint export payloads are permanently preserved from verified aggregates.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* CORRUPT OR UNVERIFIED SNAPSHOT WARNING */}
      {termAnalysis?.isCorruptOrUnverified && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-xs font-bold text-amber-800 flex items-center gap-3">
          <AlertCircle size={18} className="text-amber-600 shrink-0" />
          <span>{termAnalysis.error || 'Historical snapshot exists but cannot be verified.'}</span>
        </div>
      )}

      {/* 3. EXECUTIVE TERM SUMMARY */}
      <AnalysisSummary
        totalStaffCount={summaryDisplay.totalStaffCount}
        staffWithExceptionsCount={summaryDisplay.staffWithExceptionsCount}
        staffWithExceptionsPct={summaryDisplay.staffWithExceptionsPct}
        staffWithoutExceptionsCount={summaryDisplay.staffWithoutExceptionsCount}
        noExceptionsPct={summaryDisplay.noExceptionsPct}
        totalExceptionsCount={summaryDisplay.totalExceptionsCount}
        filteredStaffExceptionsCount={summaryDisplay.filteredStaffExceptionsCount}
        filteredCdsExceptionsCount={summaryDisplay.filteredCdsExceptionsCount}
        academicCount={summaryDisplay.academicCount}
        operationsCount={summaryDisplay.operationsCount}
        disciplineCount={summaryDisplay.disciplineCount}
        onOpenPanel={(panelType) => handleDrillKpi(panelType, 'Personnel Executive Standing')}
      />

      {/* 4. COMPARATIVE TERM ANALYSIS (STAGE 4) */}
      <TermComparison
        analysis={termAnalysis}
        activeTerm={term}
        activeSession={session}
      />

      {/* 5. SECTION ANALYSIS (STAGE 5) */}
      <SectionAnalysis
        analysis={termAnalysis}
        onDrillSection={handleDrillSection}
      />

      {/* 6. DISCIPLINE / DEPARTMENT ANALYSIS (STAGE 5) */}
      <DisciplineAnalysis
        analysis={termAnalysis}
        onDrillDiscipline={handleDrillDiscipline}
      />

      {/* 7. CLASS ANALYSIS (STAGE 5) */}
      <ClassAnalysis
        analysis={termAnalysis}
      />

      {/* 8. EXCEPTION CATEGORY ANALYSIS & 9. TOP DRIVERS & 10. SECTION CONTRIBUTION (STAGE 6) */}
      <ExceptionCategoryAnalysis
        analysis={termAnalysis}
        onDrillCategory={handleDrillCategory}
        onDrillSection={handleDrillSection}
      />

      {/* 11. HISTORICAL TRENDS OVERALL PROGRESSION (STAGE 7) */}
      {termAnalysis?.trends && (
        <OverallTrend
          overallTrend={termAnalysis.trends.overallTrend}
          availableTerms={termAnalysis.trends.availableTerms}
        />
      )}

      {/* 12. CDS INSTITUTIONAL ANALYSIS */}
      <CdsAnalysis
        totalCdsCount={summaryDisplay.filteredCdsExceptionsCount}
        seenCdsCount={summaryDisplay.seenCdsCount}
        unseenCdsCount={summaryDisplay.unseenCdsCount}
        onOpenPanel={() => handleDrillKpi('CDS_TOTAL', 'Corporate Digital Services Exceptions')}
      />

      {/* 13. MULTIDIMENSIONAL ANALYTICAL TABLES (SLIDE 5 MATRIX) */}
      <AnalysisTable
        analysis={termAnalysis}
        onDrillCategory={handleDrillCategory}
      />

      {/* 14. POWERPOINT EXPORT AREA (Admin Only) */}
      {Boolean(isAdmin || canGenerateAdminPptx) && (
        <AnalysisExportCard
          allExceptions={allFilteredExceptions}
          term={term}
          session={session}
          reportingPeriod={reportingPeriod}
          isGenerating={isGenerating}
          downloadError={downloadError}
          downloadSuccess={downloadSuccess}
          onGenerateSlide={() => handleDownloadComplianceReport()}
          onOpenConfigModal={() => setIsConfigModalOpen(true)}
        />
      )}

      {/* PRE-DOWNLOAD CONFIGURATION MODAL */}
      <SlideConfigModal
        isOpen={isConfigModalOpen}
        onClose={() => setIsConfigModalOpen(false)}
        reportConfig={reportConfig}
        onChangeReportConfig={(key, val) => setReportConfig((p) => ({ ...p, [key]: val }))}
        onDownload={handleDownloadComplianceReport}
        isGenerating={isGenerating}
        isValid={isHeadingFormValid}
      />

      {/* PURE ANALYTICAL DRILL-DOWN DISTRIBUTION MODAL (NO RAW CASE DOSSIERS) */}
      <AnalysisDrillDownModal
        isOpen={Boolean(drillContext)}
        onClose={() => setDrillContext(null)}
        drillContext={drillContext}
      />
    </div>
  );
}
