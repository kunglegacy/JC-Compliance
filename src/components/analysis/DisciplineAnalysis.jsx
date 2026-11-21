import React, { useState, useMemo } from 'react';
import {
  BookOpen,
  Award,
  Users,
  BarChart2,
  Table as TableIcon,
  Sparkles,
  TrendingUp,
  TrendingDown,
  Minus,
} from 'lucide-react';

/**
 * Change status pill for historical term shifts
 */
function DeltaPill({ comparison }) {
  if (!comparison) {
    return <span className="text-slate-400 text-xs font-semibold">—</span>;
  }
  const { status, absoluteChange, percentageChange, hasBaseline } = comparison;

  if (!hasBaseline || status === 'no_prior_baseline' || status === 'new_exceptions_no_baseline') {
    return (
      <span
        title={comparison.previous === 0 ? `Current: ${comparison.current}, Prior: 0 (No prior baseline)` : 'No prior baseline available'}
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600 border border-slate-200 shadow-2xs whitespace-nowrap"
      >
        <Minus size={10} className="text-slate-400" />
        No prior baseline
      </span>
    );
  }

  if (status === 'unchanged' || absoluteChange === 0) {
    return (
      <span
        title={`Current: ${comparison.current}, Prior: ${comparison.previous}`}
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600 border border-slate-200 shadow-2xs whitespace-nowrap"
      >
        <Minus size={10} className="text-slate-400" />
        0 (0%)
      </span>
    );
  }

  const isIncrease = absoluteChange > 0;
  const bgClass = isIncrease ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';
  const Icon = isIncrease ? TrendingUp : TrendingDown;
  const sign = isIncrease ? '+' : '';
  const pctStr = percentageChange !== null && percentageChange !== undefined ? ` (${sign}${percentageChange}%)` : '';

  return (
    <span
      title={`Current: ${comparison.current} | Prior: ${comparison.previous} | Diff: ${sign}${absoluteChange}`}
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-extrabold border shadow-2xs whitespace-nowrap ${bgClass}`}
    >
      <Icon size={11} />
      <span>{sign}{absoluteChange}{pctStr}</span>
    </span>
  );
}

/**
 * Discipline Compliance Analysis Component
 * Reconstructs Slides 3 & 5 discipline analytical tables & rankings.
 */
export default function DisciplineAnalysis({ analysis, onDrillDiscipline }) {
  const disciplines = analysis?.disciplines;
  const [activeTab, setActiveTab] = useState('primary'); // 'primary' | 'secondary' | 'all'

  const activeData = useMemo(() => {
    if (!disciplines) return { rows: [], totals: { exceptionCount: 0, affectedTeachers: 0, perHead: 0 } };
    if (activeTab === 'primary') return disciplines.primary || { rows: [], totals: {} };
    if (activeTab === 'secondary') return disciplines.secondary || { rows: [], totals: {} };
    return {
      rows: disciplines.allDisciplines || [],
      totals: {
        exceptionCount: (disciplines.primary?.totals?.exceptionCount || 0) + (disciplines.secondary?.totals?.exceptionCount || 0),
        affectedTeachers: (disciplines.allDisciplines || []).reduce((acc, d) => acc + d.affectedTeachers, 0),
        perHead: 0,
        priorTerm: disciplines.priorTerm || null,
      },
    };
  }, [disciplines, activeTab]);

  const rows = activeData.rows || [];
  const totals = activeData.totals || {};
  const priorTermName = disciplines?.priorTerm || totals?.priorTerm || null;

  const maxVal = useMemo(() => {
    if (rows.length === 0) return 10;
    return Math.max(...rows.map((r) => r.exceptionCount), 5);
  }, [rows]);

  if (!disciplines || rows.length === 0) {
    return (
      <section className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 text-center shadow-xs mt-6">
        <div className="w-10 h-10 mx-auto rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
          <BookOpen size={20} />
        </div>
        <h3 className="text-sm font-black text-slate-700">Discipline Analysis</h3>
        <p className="text-xs text-slate-500 mt-1">No discipline exception records found for this period.</p>
      </section>
    );
  }

  const topDiscipline = rows[0];
  const highestPerHeadDiscipline = [...rows].sort((a, b) => b.perHead - a.perHead)[0];

  return (
    <section className="space-y-6 mt-8 stagger-3">
      {/* SECTION HEADER & TAB CONTROLS */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200 dark:border-white/10 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-emerald-100 text-emerald-700 rounded-xl shadow-xs">
            <BookOpen size={20} />
          </div>
          <div>
            <h2 className="text-xl font-black text-slate-900 dark:text-white dark:text-white tracking-tight">
              Discipline & Department Analysis
            </h2>
            <p className="text-xs font-semibold text-slate-500 mt-0.5">
              School department exception volume, personnel distribution, and intensity ratios (Life Sciences, Physical Sciences, Social Sciences, Languages)
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="inline-flex p-1 bg-slate-100 rounded-xl border border-slate-200 text-xs font-bold self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveTab('primary')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              activeTab === 'primary'
                ? 'bg-white text-emerald-700 shadow-xs font-black'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Primary Disciplines
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('secondary')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              activeTab === 'secondary'
                ? 'bg-white text-emerald-700 shadow-xs font-black'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Secondary Disciplines
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('all')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              activeTab === 'all'
                ? 'bg-white text-emerald-700 shadow-xs font-black'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            All Disciplines
          </button>
        </div>
      </div>

      {/* SUMMARY KPI CARDS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Card 1: Top Discipline */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Highest Exceptions</span>
            <Award size={16} className="text-amber-500" />
          </div>
          <div className="text-base font-black text-slate-800 dark:text-slate-200 dark:text-white dark:text-white truncate" title={topDiscipline?.discipline}>
            {topDiscipline?.discipline}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1 flex items-center gap-1.5">
            <span className="text-rose-600 font-black">{topDiscipline?.exceptionCount} Exceptions</span>
            <span>•</span>
            <span>{topDiscipline?.affectedTeachers} Staff</span>
          </div>
        </div>

        {/* Card 2: Highest Per-Head */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Highest Per-Head Ratio</span>
            <Users size={16} className="text-indigo-500" />
          </div>
          <div className="text-base font-black text-slate-800 dark:text-slate-200 dark:text-white dark:text-white truncate" title={highestPerHeadDiscipline?.discipline}>
            {highestPerHeadDiscipline?.discipline}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1 flex items-center gap-1.5">
            <span className="text-indigo-600 font-black">{highestPerHeadDiscipline?.perHead} per head</span>
            <span>•</span>
            <span>{highestPerHeadDiscipline?.affectedTeachers} Staff</span>
          </div>
        </div>

        {/* Card 3: Scope Totals */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Scope Aggregate</span>
            <Sparkles size={16} className="text-emerald-500" />
          </div>
          <div className="text-base font-black text-slate-800 dark:text-slate-200 dark:text-white">
            {totals.exceptionCount} Total Exceptions
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1">
            Across {rows.length} Active Disciplines • {totals.affectedTeachers} Teachers
          </div>
        </div>
      </div>

      {/* RANKED HORIZONTAL BAR VISUALIZATION */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 shadow-xs">
        <div className="flex items-center gap-2 mb-5">
          <BarChart2 size={18} className="text-emerald-600" />
          <h3 className="text-sm font-extrabold text-slate-900 dark:text-white uppercase tracking-wider">
            Ranked Discipline Volume & Ratio
          </h3>
        </div>

        <div className="space-y-4">
          {rows.map((row) => {
            const pct = maxVal > 0 ? (row.exceptionCount / maxVal) * 100 : 0;
            return (
              <div
                key={row.discipline}
                onClick={() => onDrillDiscipline && onDrillDiscipline(row.discipline)}
                className="group cursor-pointer p-2 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
                title="Click to view analytical distribution"
              >
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <div className="flex items-center gap-2 truncate max-w-[60%]">
                    <span className="w-5 h-5 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 flex items-center justify-center font-black text-[10px] shrink-0">
                      #{row.rank}
                    </span>
                    <span className="font-extrabold text-slate-800 dark:text-slate-200 truncate" title={row.discipline}>
                      {row.discipline}
                    </span>
                  </div>
                  <div className="flex items-center gap-3 font-semibold text-slate-600 shrink-0">
                    <span className="text-indigo-600 font-bold">{row.perHeadText} p/head</span>
                    <span className="font-black text-slate-900 dark:text-white">{row.exceptionCount} exceptions</span>
                    <span className="text-[11px] font-bold text-slate-400 min-w-10 text-right">
                      {row.shareOfTotal || row.shareOfScope}%
                    </span>
                  </div>
                </div>

                <div className="h-2.5 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-emerald-500 to-teal-600 rounded-full transition-all duration-500 group-hover:from-emerald-600 group-hover:to-teal-700"
                    style={{ width: `${Math.max(2, pct)}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* AUTHORITATIVE DISCIPLINE TABLE */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-xs">
        <div className="px-6 py-4 bg-slate-50/70 dark:bg-slate-800/60 border-b border-slate-200 dark:border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TableIcon size={16} className="text-slate-600" />
            <h3 className="text-sm font-extrabold text-slate-800 dark:text-white uppercase tracking-wider">
              {activeTab === 'primary' ? 'Primary' : activeTab === 'secondary' ? 'Secondary' : 'All'} Disciplines Table
            </h3>
          </div>
          <span className="text-[11px] font-bold text-slate-500">
            Total Exceptions: <strong className="text-slate-800">{totals.exceptionCount}</strong>
          </span>
        </div>

        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full text-left border-collapse min-w-[750px]">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700 bg-slate-100/60 dark:bg-slate-800/80 text-[11px] font-black uppercase text-slate-600 dark:text-slate-300 tracking-wider">
                <th className="py-3 px-4 w-14 text-center">Rank</th>
                <th className="py-3 px-4">Discipline / Group</th>
                <th className="py-3 px-4 text-center">Exceptions</th>
                <th className="py-3 px-4 text-center">Teachers</th>
                <th className="py-3 px-4 text-center">Per-Head</th>
                <th className="py-3 px-4 text-center">Share</th>
                <th className="py-3 px-4 text-center bg-indigo-50/40 text-indigo-900">
                  {priorTermName ? `Shift vs ${priorTermName}` : 'Term Shift'}
                </th>
                <th className="py-3 px-4">Personnel Distribution</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs font-semibold">
              {rows.map((row) => (
                <tr
                  key={row.discipline}
                  onClick={() => onDrillDiscipline && onDrillDiscipline(row.discipline)}
                  className="hover:bg-indigo-50/40 transition-colors cursor-pointer"
                  title="Click to view analytical distribution"
                >
                  <td className="py-3.5 px-4 text-center">
                    <span className="inline-block w-6 h-6 rounded-md bg-slate-100 text-slate-700 font-black text-xs leading-6">
                      {row.rank}
                    </span>
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-extrabold text-slate-900">{row.discipline}</div>
                  </td>
                  <td className="py-3.5 px-4 text-center font-black text-slate-800 dark:text-slate-200">
                    {row.exceptionCount}
                  </td>
                  <td className="py-3.5 px-4 text-center font-bold text-slate-600 dark:text-slate-300">
                    {row.affectedTeachers}
                  </td>
                  <td className="py-3.5 px-4 text-center">
                    <span className="inline-block px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 font-extrabold border border-emerald-100">
                      {row.perHead.toFixed(2)}
                    </span>
                  </td>
                  <td className="py-3.5 px-4 text-center font-bold text-slate-600 dark:text-slate-300">
                    {row.shareOfTotal || row.shareOfScope}%
                  </td>
                  <td className="py-3.5 px-4 text-center bg-indigo-50/10">
                    {priorTermName && row.historicalChange ? (
                      <DeltaPill comparison={row.historicalChange} />
                    ) : (
                      <span className="text-[10px] text-slate-400 font-mono" title="Comparative history is not yet available">—</span>
                    )}
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="text-[11px] font-medium text-slate-600 truncate max-w-xs" title={row.teacherNamesSummary}>
                      {row.teacherNamesSummary || '—'}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
