import React, { useState, useMemo } from 'react';
import {
  TrendingUp,
  TrendingDown,
  Minus,
  BarChart3,
  Table as TableIcon,
  Info,
  Calendar,
  ArrowUpRight,
  ArrowDownRight,
  Activity,
  ChevronRight,
  Sparkles
} from 'lucide-react';

/**
 * Segment display definitions aligned with school structure.
 */
const SEGMENT_CONFIGS = [
  { key: 'juniorPrimary', label: 'Junior Primary', category: 'PRIMARY', description: 'Basic 1 to 3' },
  { key: 'seniorPrimary', label: 'Senior Primary', category: 'PRIMARY', description: 'Basic 4 to 5' },
  { key: 'juniorSeniorPrimary', label: 'Junior/Senior Primary', category: 'PRIMARY', isCombined: true, description: 'Combined (1-5)' },
  { key: 'juniorSecondary', label: 'Junior Secondary', category: 'SECONDARY', description: 'JSS 1 to 3' },
  { key: 'seniorSecondary', label: 'Senior Secondary', category: 'SECONDARY', description: 'SS 1 to 3' },
  { key: 'secondaryCombined', label: 'Sec. Combined', category: 'SECONDARY', isCombined: true, description: 'Combined (JSS-SS3)' },
  { key: 'nursery', label: 'Nursery', category: 'NURSERY', description: 'Early Years / KG' },
  { key: 'academicTotal', label: 'Total Academic', category: 'TOTAL', isTotal: true, description: 'All academic divisions' },
];

/**
 * Premium Delta Badge with mathematically sound "No prior baseline" handling
 */
function DeltaBadge({ comparison, invertSentiment = false }) {
  if (!comparison) {
    return <span className="text-slate-400 text-[10px] font-mono">—</span>;
  }

  const { status, absoluteChange, percentageChange, hasBaseline } = comparison;

  if (!hasBaseline || status === 'no_prior_baseline' || status === 'new_exceptions_no_baseline') {
    return (
      <span
        title={comparison.previous === 0 ? `Current: ${comparison.current}, Prior: 0 (No prior baseline)` : 'No prior baseline available'}
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider bg-slate-100 text-slate-600 border border-slate-200 whitespace-nowrap shadow-2xs"
      >
        <Minus size={10} strokeWidth={2.5} /> No prior baseline
      </span>
    );
  }

  if (status === 'unchanged' || absoluteChange === 0) {
    return (
      <span
        title={`Current: ${comparison.current}, Prior: ${comparison.previous}`}
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-mono font-bold bg-slate-100 text-slate-600 border border-slate-200 whitespace-nowrap shadow-2xs"
      >
        <Minus size={10} strokeWidth={2.5} /> 0 (0%)
      </span>
    );
  }

  const isIncrease = absoluteChange > 0;
  const isGood = invertSentiment ? isIncrease : !isIncrease;

  const bgClass = isGood 
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200' 
    : 'bg-rose-50 text-rose-700 border-rose-200';
  
  const Icon = isIncrease ? (isGood ? ArrowUpRight : TrendingUp) : (isGood ? TrendingDown : ArrowDownRight);
  const sign = isIncrease ? '+' : '';
  const pctStr = percentageChange !== null && percentageChange !== undefined ? ` (${sign}${percentageChange}%)` : '';

  return (
    <span
      title={`Current: ${comparison.current} | Prior: ${comparison.previous} | Diff: ${sign}${absoluteChange}`}
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-mono font-bold tracking-wide border whitespace-nowrap ${bgClass}`}
    >
      <Icon size={12} strokeWidth={2.5} />
      <span>{sign}{absoluteChange}{pctStr}</span>
    </span>
  );
}

export default function TermComparison({ analysis, comparisons: propComparisons, activeTerm: propActiveTerm, activeSession: propActiveSession }) {
  const comparisons = analysis?.comparisons || propComparisons;
  const activeTerm = analysis?.meta?.term || propActiveTerm || comparisons?.activeTerm || 'First Term';
  const activeSession = analysis?.meta?.session || propActiveSession || '2026/2027';

  const [segmentScope, setSegmentScope] = useState('PRIMARY');
  const [activeChartMetric, setActiveChartMetric] = useState('exceptions');
  const [hoveredBar, setHoveredBar] = useState(null);
  const [metricView, setMetricView] = useState('multiRow');

  const canonicalTerms = useMemo(() => ['First Term', 'Second Term', 'Third Term'], []);
  const databaseTerms = useMemo(() => {
    if (comparisons?.availableTerms && comparisons.availableTerms.length > 0) return comparisons.availableTerms;
    const fromByTerm = Object.keys(comparisons?.byTerm || {});
    return fromByTerm.length > 0 ? fromByTerm : [activeTerm];
  }, [comparisons, activeTerm]);

  const displayTerms = useMemo(() => Array.from(new Set([...canonicalTerms, ...databaseTerms])), [canonicalTerms, databaseTerms]);
  const hasComparativeHistory = Boolean(comparisons?.hasComparativeHistory ?? (databaseTerms.length >= 2 && Boolean(comparisons?.priorTerm)));

  const filteredSegments = useMemo(() => {
    if (segmentScope === 'PRIMARY') return SEGMENT_CONFIGS.filter((s) => s.category === 'PRIMARY');
    if (segmentScope === 'SECONDARY') return SEGMENT_CONFIGS.filter((s) => s.category === 'SECONDARY');
    return SEGMENT_CONFIGS;
  }, [segmentScope]);

  const termColorMap = useMemo(() => {
    const palette = {
      'First Term': { fill: 'url(#gradFirst)', stroke: '#475569', label: 'First Term', color: '#64748b' },
      'Second Term': { fill: 'url(#gradSecond)', stroke: '#2563eb', label: 'Second Term', color: '#3b82f6' },
      'Third Term': { fill: 'url(#gradThird)', stroke: '#6d28d9', label: 'Third Term', color: '#8b5cf6' },
    };
    const defaultColor = { fill: 'url(#gradDefault)', stroke: '#0284c7', label: 'Other Term', color: '#0ea5e9' };
    const map = {};
    displayTerms.forEach((t) => { map[t] = palette[t] || defaultColor; });
    return map;
  }, [displayTerms]);

  const chartMaxVal = useMemo(() => {
    let max = 0;
    filteredSegments.forEach((seg) => {
      databaseTerms.forEach((termKey) => {
        const stats = comparisons?.segmentStatsByTerm?.[termKey]?.[seg.key];
        if (stats) {
          let val = 0;
          if (activeChartMetric === 'exceptions') val = stats.exceptionCount || 0;
          else if (activeChartMetric === 'staff') val = stats.affectedStaffCount || 0;
          else if (activeChartMetric === 'perHead') val = stats.perHead || 0;
          if (val > max) max = val;
        }
      });
    });
    return Math.max(max, activeChartMetric === 'perHead' ? 3 : 10);
  }, [filteredSegments, databaseTerms, comparisons, activeChartMetric]);

  return (
    <section className="space-y-6 mt-8 animate-in fade-in duration-500 font-sans">
      
      {/* MOBILE SCROLLBAR FIXES */}
      <style>{`
        .custom-scrollbar::-webkit-scrollbar { height: 5px; width: 5px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }
        .hide-scrollbar::-webkit-scrollbar { display: none; }
        .hide-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
      `}</style>

      {/* =========================================
          HEADER & CONTROLS
          ========================================= */}
      <div className="flex flex-col gap-4 border-b border-slate-200/80 dark:border-white/10 pb-5">
        <div className="flex items-start gap-3 sm:gap-4">
          <div className="flex h-10 w-10 sm:h-12 sm:w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 border border-indigo-100 shadow-sm">
            <Activity size={20} className="sm:w-6 sm:h-6" />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-black uppercase tracking-widest text-indigo-600 mb-0.5">
              TERM COMPARISON
            </div>
            <h2 className="text-lg sm:text-xl font-black text-slate-900 dark:text-white tracking-tight truncate">
              Term Comparative Analysis
            </h2>
            <p className="text-[11px] sm:text-xs font-medium text-slate-500 mt-0.5 leading-relaxed max-w-2xl">
              Multi-term exception tracking, personnel impact, and per-head ratio dynamics mapped across academic segments.
            </p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mt-2">
          <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-[11px] font-black text-slate-700 dark:text-slate-300 shadow-sm shrink-0 w-max">
            <Calendar size={13} className="text-indigo-500" />
            <span>{activeSession} <span className="text-slate-300 mx-1">•</span> {activeTerm}</span>
          </div>

          <div className="flex items-center p-1 bg-slate-100/80 dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-inner overflow-x-auto hide-scrollbar w-full sm:w-auto">
            {[
              { id: 'PRIMARY', label: 'Primary' },
              { id: 'SECONDARY', label: 'Secondary' },
              { id: 'ALL', label: 'All Segments' }
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setSegmentScope(tab.id)}
                className={`whitespace-nowrap px-4 py-1.5 rounded-lg text-[10px] sm:text-[11px] font-black tracking-wide transition-all duration-300 flex-1 sm:flex-none text-center cursor-pointer ${
                  segmentScope === tab.id
                    ? 'bg-white text-indigo-700 shadow-sm border border-slate-200/50'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* =========================================
          TRAJECTORY BANNER
          ========================================= */}
      {!hasComparativeHistory ? (
        <div className="p-4 sm:p-5 rounded-2xl border border-slate-200 dark:border-white/10 bg-slate-50/80 dark:bg-slate-850 shadow-2xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-slate-200 text-slate-600 shrink-0">
              <Info size={18} />
            </div>
            <div>
              <div className="text-xs font-black uppercase tracking-wider text-slate-800 dark:text-white">
                Comparative history is not yet available.
              </div>
              <div className="text-[11px] text-slate-500 font-medium mt-1 leading-relaxed max-w-lg">
                Records currently exist for <strong className="font-bold text-slate-800 dark:text-white">{activeTerm}</strong> only. Multi-term trajectories, percentage changes, and shifts will automatically populate once multiple academic terms are recorded in Supabase.
              </div>
            </div>
          </div>
        </div>
      ) : (
        comparisons?.priorTerm && (
          <div className="p-4 sm:p-5 rounded-[24px] border border-indigo-100 dark:border-indigo-500/20 bg-gradient-to-r from-indigo-50/80 via-white to-blue-50/50 dark:from-indigo-950/20 dark:via-slate-900 dark:to-slate-950 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-5 relative overflow-hidden group">
            <div className="absolute -right-10 -top-10 opacity-5 group-hover:scale-150 transition-transform duration-700 pointer-events-none">
              <TrendingUp size={150} />
            </div>
            
            <div className="flex items-center gap-3 sm:gap-4 relative z-10">
              <div className="w-10 h-10 sm:w-12 sm:h-12 shrink-0 rounded-xl sm:rounded-2xl bg-indigo-600 text-white flex items-center justify-center font-black text-lg shadow-md shadow-indigo-500/20">
                Δ
              </div>
              <div>
                <div className="text-[9px] sm:text-[10px] font-black text-indigo-600 uppercase tracking-widest mb-1.5 flex items-center gap-1.5">
                  <Sparkles size={10} /> Term Trajectory
                </div>
                <div className="text-xs sm:text-sm font-black text-slate-800 dark:text-white flex flex-wrap items-center gap-1.5">
                  <span className="bg-white dark:bg-slate-800 px-2 py-0.5 rounded border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-200 shadow-sm whitespace-nowrap">{comparisons.priorTerm}</span>
                  <ChevronRight size={12} className="text-slate-400" />
                  <span className="bg-indigo-50 px-2 py-0.5 rounded border border-indigo-100 text-indigo-800 shadow-sm whitespace-nowrap">{activeTerm}</span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-4 bg-white/70 dark:bg-slate-800/70 backdrop-blur-sm p-2.5 sm:p-3 rounded-2xl border border-white/20 dark:border-white/10 relative z-10 overflow-x-auto">
              <div className="flex flex-col items-start sm:items-end gap-1">
                <span className="text-[9px] font-black uppercase tracking-widest text-slate-400">Total Exceptions</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-mono text-slate-500 font-semibold hidden sm:inline">
                    {comparisons.comparisons?.totalExceptionsChange?.previous ?? 0} → {comparisons.comparisons?.totalExceptionsChange?.current ?? 0}
                  </span>
                  <DeltaBadge comparison={comparisons.comparisons?.totalExceptionsChange} />
                </div>
              </div>
              <div className="w-px h-8 bg-slate-200/80" />
              <div className="flex flex-col items-start sm:items-end gap-1">
                <span className="text-[9px] font-black uppercase tracking-widest text-slate-400">Affected Staff</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-mono text-slate-500 font-semibold hidden sm:inline">
                    {comparisons.comparisons?.affectedStaffChange?.previous ?? 0} → {comparisons.comparisons?.affectedStaffChange?.current ?? 0}
                  </span>
                  <DeltaBadge comparison={comparisons.comparisons?.affectedStaffChange} invertSentiment />
                </div>
              </div>
              <div className="w-px h-8 bg-slate-200/80" />
              <div className="flex flex-col items-start sm:items-end gap-1">
                <span className="text-[9px] font-black uppercase tracking-widest text-slate-400">Per-Head Shift</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-mono text-slate-500 font-semibold hidden sm:inline">
                    {(comparisons.comparisons?.perHeadChange?.previous ?? 0).toFixed(2)} → {(comparisons.comparisons?.perHeadChange?.current ?? 0).toFixed(2)}
                  </span>
                  <DeltaBadge comparison={comparisons.comparisons?.perHeadChange} />
                </div>
              </div>
            </div>
          </div>
        )
      )}

      {/* =========================================
          COMPARATIVE MATRIX TABLE (MOBILE PERFECT)
          ========================================= */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-sm">
        
        {/* Table Header & View Switcher */}
        <div className="p-3 sm:p-5 bg-slate-50/50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-white/10 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-slate-200/50 rounded-lg text-slate-600 shrink-0">
              <TableIcon size={14} />
            </div>
            <h3 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white uppercase tracking-widest truncate">
              Comparison Matrix
            </h3>
          </div>

          <div className="flex items-center p-1 bg-slate-100/80 dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-inner overflow-x-auto hide-scrollbar w-full lg:w-auto shrink-0">
            {[
              { id: 'multiRow', label: 'Detailed' },
              { id: 'exceptions', label: 'Exceptions' },
              { id: 'staff', label: 'Staff' },
              { id: 'perHead', label: 'Per Head' }
            ].map(view => (
              <button
                key={view.id}
                onClick={() => setMetricView(view.id)}
                className={`whitespace-nowrap px-3 py-1.5 rounded-lg text-[10px] font-black tracking-widest uppercase transition-all duration-300 flex-1 lg:flex-none text-center ${
                  metricView === view.id
                    ? 'bg-white text-indigo-700 shadow-sm border border-slate-200/50'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                {view.label}
              </button>
            ))}
          </div>
        </div>

        {/* Responsive Table Wrapper */}
        <div className="relative overflow-x-auto custom-scrollbar">
          <table className="w-full text-left border-collapse whitespace-nowrap min-w-[500px]">
            <thead>
              <tr className="bg-slate-100/90 dark:bg-slate-800/90 text-[9px] sm:text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400">
                <th className="sticky left-0 z-20 bg-slate-100/90 dark:bg-slate-800/90 border-b border-r border-slate-200 dark:border-slate-700 px-3 sm:px-4 py-2.5 sm:py-3 shadow-[2px_0_8px_-3px_rgba(0,0,0,0.1)] w-[160px] sm:w-[220px]">
                  School Segment
                </th>
                {displayTerms.map((t) => {
                  const shortName = t.replace(/\s*Term$/i, '');
                  const hasTermSuffix = t.toLowerCase().includes('term');
                  return (
                    <th key={t} className="px-3 py-2 sm:py-2.5 text-center border-b border-slate-200/60 min-w-[95px]" title={t}>
                      <span className="block font-black text-slate-800 dark:text-white text-[11px] sm:text-xs">{shortName}</span>
                      {hasTermSuffix && (
                        <span className="block text-[8px] text-slate-400 font-bold uppercase tracking-wider">Term</span>
                      )}
                    </th>
                  );
                })}
                {hasComparativeHistory && comparisons?.priorTerm && (
                  <th className="px-3 py-2 sm:py-2.5 text-center border-b border-slate-200/60 bg-indigo-50/50 text-indigo-800">
                    <span className="block font-black text-[11px] sm:text-xs">Shift</span>
                    <span className="block text-[8px] text-indigo-500 font-bold uppercase tracking-wider">Change (Δ)</span>
                  </th>
                )}
              </tr>
            </thead>
            
            <tbody className="text-[11px] sm:text-xs text-slate-700 dark:text-slate-300">
              {filteredSegments.map((seg) => {
                const compItem = comparisons?.segmentComparisons?.[seg.key];
                const isCombinedOrTotal = seg.isCombined || seg.isTotal;

                if (metricView === 'multiRow') {
                  return (
                    <React.Fragment key={seg.key}>
                      {/* Segment Header Row */}
                      <tr className={`${isCombinedOrTotal ? 'bg-slate-50 border-t-2 border-slate-200' : 'bg-white border-t border-slate-200'}`}>
                        <td colSpan={displayTerms.length + (hasComparativeHistory && comparisons?.priorTerm ? 2 : 1)} className="p-0">
                          <div className={`sticky left-0 w-max max-w-[85vw] px-3 sm:px-4 py-2 flex items-center gap-2 ${isCombinedOrTotal ? 'bg-slate-50' : 'bg-white'}`}>
                            <span className="font-black text-slate-800 text-[10px] sm:text-[11px] uppercase tracking-wider">{seg.label}</span>
                            <span className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-slate-100 text-[9px] text-slate-500 font-bold uppercase tracking-widest">{seg.description}</span>
                          </div>
                        </td>
                      </tr>

                      {/* Row 1: Exceptions */}
                      <tr className="bg-white dark:bg-slate-900 border-b border-slate-50 dark:border-slate-800 hover:bg-slate-50/50 dark:hover:bg-slate-800/50 transition-colors">
                        <td className="sticky left-0 z-10 bg-white dark:bg-slate-900 px-3 sm:px-4 py-2 border-r border-slate-100 dark:border-slate-800 shadow-[2px_0_8px_-2px_rgba(0,0,0,0.05)]">
                          <div className="flex items-center gap-1.5 sm:gap-2 sm:pl-3">
                            <div className="w-1.5 h-1.5 rounded-full bg-rose-400 shrink-0" />
                            <span className="text-[10px] sm:text-[11px] font-bold text-slate-600">Total Exceptions</span>
                          </div>
                        </td>
                        {displayTerms.map(t => {
                          const stats = comparisons?.segmentStatsByTerm?.[t]?.[seg.key];
                          return (
                            <td key={t} className="px-3 py-2 text-center font-black text-slate-800 border-r border-slate-50">
                              {databaseTerms.includes(t) ? (stats?.exceptionCount ?? 0) : <span className="text-slate-300 font-normal">—</span>}
                            </td>
                          );
                        })}
                        {hasComparativeHistory && comparisons?.priorTerm && (
                          <td className="px-3 py-2 text-center bg-indigo-50/20">
                            <DeltaBadge comparison={compItem?.exceptionComparison} />
                          </td>
                        )}
                      </tr>

                      {/* Row 2: Staff */}
                      <tr className="bg-slate-50/30 border-b border-slate-50 hover:bg-slate-50/80 transition-colors">
                        <td className="sticky left-0 z-10 bg-[#fafbfc] px-3 sm:px-4 py-2 border-r border-slate-100 shadow-[2px_0_8px_-2px_rgba(0,0,0,0.05)]">
                          <div className="flex items-center gap-1.5 sm:gap-2 sm:pl-3">
                            <div className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />
                            <span className="text-[10px] sm:text-[11px] font-bold text-slate-600">Affected Staff</span>
                          </div>
                        </td>
                        {displayTerms.map(t => {
                          const stats = comparisons?.segmentStatsByTerm?.[t]?.[seg.key];
                          return (
                            <td key={t} className="px-3 py-2 text-center font-bold text-slate-500 border-r border-slate-50">
                              {databaseTerms.includes(t) ? (stats?.affectedStaffCount ?? 0) : <span className="text-slate-300 font-normal">—</span>}
                            </td>
                          );
                        })}
                        {hasComparativeHistory && comparisons?.priorTerm && (
                          <td className="px-3 py-2 text-center bg-indigo-50/20">
                            <DeltaBadge comparison={compItem?.affectedStaffComparison} invertSentiment />
                          </td>
                        )}
                      </tr>

                      {/* Row 3: Ratio */}
                      <tr className="bg-indigo-50/20 hover:bg-indigo-50/40 transition-colors">
                        <td className="sticky left-0 z-10 bg-[#f8fafc] px-3 sm:px-4 py-2 border-r border-indigo-100 shadow-[2px_0_8px_-2px_rgba(0,0,0,0.05)]">
                          <div className="flex items-center gap-1.5 sm:gap-2 sm:pl-3">
                            <div className="w-1.5 h-1.5 rounded-full bg-indigo-500 shrink-0" />
                            <span className="text-[10px] sm:text-[11px] font-black text-indigo-800">Per Head</span>
                          </div>
                        </td>
                        {displayTerms.map(t => {
                          const stats = comparisons?.segmentStatsByTerm?.[t]?.[seg.key];
                          return (
                            <td key={t} className="px-3 py-2 text-center font-black text-indigo-700 bg-indigo-50/20 border-r border-indigo-50">
                              {databaseTerms.includes(t) ? (stats?.perHead?.toFixed(2) ?? '0.00') : <span className="text-slate-300 font-normal">—</span>}
                            </td>
                          );
                        })}
                        {hasComparativeHistory && comparisons?.priorTerm && (
                          <td className="px-3 py-2 text-center bg-indigo-100/30">
                            <DeltaBadge comparison={compItem?.perHeadComparison} />
                          </td>
                        )}
                      </tr>
                    </React.Fragment>
                  );
                }

                // If NOT detailed view (e.g. Exceptions only, Staff only)
                return (
                  <tr key={seg.key} className={`border-t border-slate-200 hover:bg-slate-50/50 transition-colors ${isCombinedOrTotal ? 'bg-slate-50' : 'bg-white'}`}>
                    <td className={`sticky left-0 z-10 px-3 sm:px-4 py-2.5 sm:py-3 border-r border-slate-200 shadow-[2px_0_8px_-3px_rgba(0,0,0,0.05)] ${isCombinedOrTotal ? 'bg-slate-50' : 'bg-white'}`}>
                      <div className="font-black text-slate-800 text-[10px] sm:text-[11px] uppercase tracking-wider">{seg.label}</div>
                      <div className="text-[9px] text-slate-400 font-bold mt-0.5">{seg.description}</div>
                    </td>
                    {displayTerms.map((t) => {
                      const hasData = databaseTerms.includes(t);
                      const stats = comparisons?.segmentStatsByTerm?.[t]?.[seg.key];
                      if (!hasData) return <td key={t} className="px-3 py-2.5 text-center text-slate-300">—</td>;

                      if (metricView === 'exceptions') return <td key={t} className="px-3 py-2.5 text-center font-black text-slate-800">{stats?.exceptionCount ?? 0}</td>;
                      if (metricView === 'staff') return <td key={t} className="px-3 py-2.5 text-center font-bold text-slate-500">{stats?.affectedStaffCount ?? 0}</td>;
                      if (metricView === 'perHead') return <td key={t} className="px-3 py-2.5 text-center font-black text-indigo-600 bg-indigo-50/30">{(stats?.perHead ?? 0).toFixed(2)}</td>;
                      return null;
                    })}
                    {hasComparativeHistory && comparisons?.priorTerm && (
                      <td className="px-3 py-2.5 text-center bg-indigo-50/20">
                        <DeltaBadge comparison={metricView === 'staff' ? compItem?.affectedStaffComparison : metricView === 'perHead' ? compItem?.perHeadComparison : compItem?.exceptionComparison} invertSentiment={metricView === 'staff'} />
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* =========================================
          GROUPED BAR CHART VISUALIZATION
          ========================================= */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-4 sm:p-7 shadow-sm">
        
        <svg width="0" height="0" className="absolute">
          <defs>
            <linearGradient id="gradFirst" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#94a3b8" /><stop offset="100%" stopColor="#64748b" /></linearGradient>
            <linearGradient id="gradSecond" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#60a5fa" /><stop offset="100%" stopColor="#3b82f6" /></linearGradient>
            <linearGradient id="gradThird" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#a78bfa" /><stop offset="100%" stopColor="#8b5cf6" /></linearGradient>
            <linearGradient id="gradDefault" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#38bdf8" /><stop offset="100%" stopColor="#0ea5e9" /></linearGradient>
          </defs>
        </svg>

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-xl shrink-0 border border-indigo-100">
              <BarChart3 size={18} />
            </div>
            <div>
              <h3 className="text-sm font-black text-slate-900 uppercase tracking-widest">
                Multi-Term Distribution
              </h3>
              <p className="text-[10px] sm:text-[11px] text-slate-500 font-bold mt-0.5">
                Visual trajectory mapped across academic segments
              </p>
            </div>
          </div>

          <div className="flex items-center p-1 bg-slate-100/80 dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-inner overflow-x-auto hide-scrollbar w-full lg:w-auto shrink-0">
            {[
              { id: 'exceptions', label: 'Exceptions' },
              { id: 'staff', label: 'Staff' },
              { id: 'perHead', label: 'Per Head' }
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveChartMetric(tab.id)}
                className={`whitespace-nowrap px-4 py-1.5 rounded-lg text-[10px] font-black tracking-widest uppercase transition-all duration-300 flex-1 lg:flex-none text-center ${
                  activeChartMetric === tab.id
                    ? 'bg-white text-indigo-700 shadow-sm border border-slate-200/50'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        {/* SVG CHART CONTAINER */}
        <div className="w-full overflow-x-auto custom-scrollbar pb-2">
          <div className="min-w-[650px]">
            <svg viewBox="0 0 800 280" className="w-full h-64 sm:h-72 overflow-visible font-sans select-none">
              {[0, 0.25, 0.5, 0.75, 1].map((pct) => {
                const y = 220 - pct * 180;
                const val = (pct * chartMaxVal).toFixed(activeChartMetric === 'perHead' ? 1 : 0);
                return (
                  <g key={pct}>
                    <line x1="40" y1={y} x2="780" y2={y} stroke="#f1f5f9" strokeWidth="1.5" strokeDasharray="4 4" />
                    <text x="32" y={y + 4} textAnchor="end" className="text-[10px] font-bold fill-slate-400">{val}</text>
                  </g>
                );
              })}
              <line x1="40" y1="220" x2="780" y2="220" stroke="#cbd5e1" strokeWidth="2" strokeLinecap="round" />

              {filteredSegments.map((seg, sIdx) => {
                const groupWidth = (780 - 50) / filteredSegments.length;
                const groupX = 50 + sIdx * groupWidth;
                const numTerms = databaseTerms.length;
                const barWidth = Math.min(24, Math.max(12, (groupWidth - 10) / Math.max(1, numTerms)));
                const groupStartX = groupX + (groupWidth - (barWidth * numTerms + (numTerms - 1) * 4)) / 2;

                return (
                  <g key={seg.key}>
                    <text x={groupX + groupWidth / 2} y="240" textAnchor="middle" className="text-[9px] sm:text-[10px] font-black fill-slate-700 uppercase tracking-wider">
                      {seg.label.split(' ')[0]}
                    </text>
                    {seg.label.split(' ').length > 1 && (
                      <text x={groupX + groupWidth / 2} y="254" textAnchor="middle" className="text-[8px] sm:text-[9px] font-bold fill-slate-400 uppercase tracking-widest">
                        {seg.label.split(' ').slice(1).join(' ')}
                      </text>
                    )}

                    {databaseTerms.map((termKey, tIdx) => {
                      const stats = comparisons?.segmentStatsByTerm?.[termKey]?.[seg.key];
                      let rawVal = 0;
                      if (stats) {
                        if (activeChartMetric === 'exceptions') rawVal = stats.exceptionCount || 0;
                        else if (activeChartMetric === 'staff') rawVal = stats.affectedStaffCount || 0;
                        else if (activeChartMetric === 'perHead') rawVal = stats.perHead || 0;
                      }

                      const barHeight = chartMaxVal > 0 ? (rawVal / chartMaxVal) * 180 : 0;
                      const barX = groupStartX + tIdx * (barWidth + 4);
                      const barY = 220 - barHeight;
                      const color = termColorMap[termKey];
                      const isHovered = hoveredBar?.segment === seg.key && hoveredBar?.term === termKey;

                      return (
                        <g
                          key={termKey}
                          onMouseEnter={() => setHoveredBar({ segment: seg.key, term: termKey, val: rawVal, label: seg.label })}
                          onMouseLeave={() => setHoveredBar(null)}
                          className="cursor-pointer transition-all duration-300"
                          style={{ transformOrigin: `${barX + barWidth/2}px 220px`, transform: isHovered ? 'scaleY(1.02)' : 'scaleY(1)' }}
                        >
                          <rect x={barX} y={barY} width={barWidth} height={Math.max(3, barHeight)} rx="3" fill={color.fill} opacity={isHovered ? 1 : 0.9} />
                          {rawVal > 0 && (
                            <text x={barX + barWidth / 2} y={barY - 6} textAnchor="middle" className={`text-[9px] sm:text-[10px] font-black ${isHovered ? 'fill-indigo-600' : 'fill-slate-600'} transition-all`}>
                              {activeChartMetric === 'perHead' ? rawVal.toFixed(1) : rawVal}
                            </text>
                          )}
                        </g>
                      );
                    })}
                  </g>
                );
              })}
            </svg>
          </div>
        </div>

        {/* Legend */}
        <div className="mt-2 pt-4 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50/50 p-3 sm:p-4 rounded-xl">
          <div className="flex flex-wrap items-center gap-3 text-[9px] sm:text-[10px] font-black uppercase tracking-widest text-slate-500">
            <span className="text-slate-400">Legend:</span>
            {databaseTerms.map((t) => (
              <div key={t} className="flex items-center gap-1.5 bg-white dark:bg-slate-800 px-2 py-1 rounded-md border border-slate-200 dark:border-slate-700 shadow-sm">
                <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: termColorMap[t]?.color || '#64748b' }} />
                <span>{t}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}