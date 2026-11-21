import React, { useMemo } from 'react';
import {
  Layers,
  Award,
  Users,
  TrendingUp,
  TrendingDown,
  Minus,
  Table as TableIcon,
  BarChart2,
} from 'lucide-react';

/**
 * Format helper for change status badges
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
 * Section Compliance Analysis Component
 * Displays ranking, exceptions, personnel counts, per-head concentration, and term shifts.
 */
export default function SectionAnalysis({ analysis, onDrillSection }) {
  const sections = analysis?.sections;
  const comparisons = analysis?.comparisons;
  const totalAcademicExceptions = analysis?.grandTotals?.academicExceptions || 0;

  const rankedSections = useMemo(() => {
    if (!sections) return [];
    if (sections.rankedList && sections.rankedList.length > 0) {
      return sections.rankedList;
    }

    const list = [
      sections.juniorPrimary,
      sections.seniorPrimary,
      sections.juniorSecondary,
      sections.seniorSecondary,
      sections.nursery,
    ].filter(Boolean);

    return list
      .sort((a, b) => b.exceptionCount - a.exceptionCount)
      .map((s, idx) => ({ ...s, rank: idx + 1 }));
  }, [sections]);

  // Max count for chart scaling
  const maxCount = useMemo(() => {
    if (rankedSections.length === 0) return 10;
    return Math.max(...rankedSections.map((s) => s.exceptionCount), 5);
  }, [rankedSections]);

  if (!sections || rankedSections.length === 0 || totalAcademicExceptions === 0) {
    return (
      <section className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 text-center shadow-xs mt-6">
        <div className="w-10 h-10 mx-auto rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
          <Layers size={20} />
        </div>
        <h3 className="text-sm font-black text-slate-700">Section Analysis</h3>
        <p className="text-xs text-slate-500 mt-1">No academic exception records logged for this period.</p>
      </section>
    );
  }

  const topSection = rankedSections[0];
  const highestPerHeadSection = [...rankedSections].sort((a, b) => b.perHead - a.perHead)[0];

  return (
    <section className="space-y-6 mt-8 stagger-3">
      {/* SECTION HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 dark:border-white/10 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-blue-100 text-blue-700 rounded-xl shadow-xs">
            <Layers size={20} />
          </div>
          <div>
            <h2 className="text-xl font-black text-slate-900 dark:text-white dark:text-white tracking-tight">
              School Section Analysis
            </h2>
            <p className="text-xs font-semibold text-slate-500 mt-0.5">
              Comparative exception distribution and personnel concentration across school sections
            </p>
          </div>
        </div>
        <div className="text-xs font-bold text-slate-500 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 self-start sm:self-auto">
          {rankedSections.length} Sections Represented
        </div>
      </div>

      {/* COMPACT SUMMARY CARDS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Card 1: Top Section by Volume */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Highest Volume</span>
            <Award size={16} className="text-amber-500" />
          </div>
          <div className="text-lg font-black text-slate-800 dark:text-slate-200 dark:text-white">
            {topSection?.label}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1 flex items-center gap-1.5">
            <span className="text-rose-600 font-black">{topSection?.exceptionCount} Exceptions</span>
            <span>•</span>
            <span>{topSection?.shareOfAcademic}% share</span>
          </div>
        </div>

        {/* Card 2: Highest Per-Head Concentration */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Highest Per-Head</span>
            <Users size={16} className="text-indigo-500" />
          </div>
          <div className="text-lg font-black text-slate-800 dark:text-slate-200 dark:text-white">
            {highestPerHeadSection?.label}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1 flex items-center gap-1.5">
            <span className="text-indigo-600 font-black">{highestPerHeadSection?.perHead} per staff</span>
            <span>•</span>
            <span>{highestPerHeadSection?.affectedStaffCount} staff</span>
          </div>
        </div>

        {/* Card 3: Composite Primary vs Secondary */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Primary vs Secondary</span>
            <Layers size={16} className="text-blue-500" />
          </div>
          <div className="flex items-baseline justify-between mt-1">
            <div>
              <span className="text-xs font-bold text-slate-500">Primary: </span>
              <span className="text-base font-black text-slate-800 dark:text-slate-200 dark:text-white">{sections?.juniorSeniorPrimary?.exceptionCount ?? 0}</span>
            </div>
            <div>
              <span className="text-xs font-bold text-slate-500">Secondary: </span>
              <span className="text-base font-black text-slate-800 dark:text-slate-200 dark:text-white">{sections?.juniorSeniorSecondary?.exceptionCount ?? 0}</span>
            </div>
          </div>
          <div className="text-[11px] font-semibold text-slate-400 mt-1">
            Combined tiers summary
          </div>
        </div>
      </div>

      {/* RANKED HORIZONTAL BAR VISUALIZATION */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 shadow-xs">
        <div className="flex items-center gap-2 mb-5">
          <BarChart2 size={18} className="text-blue-600" />
          <h3 className="text-sm font-extrabold text-slate-900 dark:text-white dark:text-white uppercase tracking-wider">
            Ranked Section Exception Volume
          </h3>
        </div>

        <div className="space-y-4">
          {rankedSections.map((sec) => {
            const pctBar = maxCount > 0 ? (sec.exceptionCount / maxCount) * 100 : 0;
            return (
              <div
                key={sec.key || sec.label}
                onClick={() => onDrillSection && onDrillSection(sec.key || sec.label, sec.label)}
                className="group cursor-pointer p-2 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
                title="Click to view analytical breakdown"
              >
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <div className="flex items-center gap-2">
                    <span className="w-5 h-5 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 flex items-center justify-center font-black text-[10px]">
                      #{sec.rank}
                    </span>
                    <span className="font-extrabold text-slate-800 dark:text-slate-200">{sec.label}</span>
                  </div>
                  <div className="flex items-center gap-3 font-semibold text-slate-600">
                    <span className="text-indigo-600 font-bold">{sec.perHeadText} p/head</span>
                    <span className="font-black text-slate-900 dark:text-white">{sec.exceptionCount} exceptions</span>
                    <span className="text-[11px] font-bold text-slate-400 min-w-10 text-right">
                      {sec.shareOfAcademic}%
                    </span>
                  </div>
                </div>

                {/* Progress bar */}
                <div className="h-3 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-blue-500 to-indigo-600 rounded-full transition-all duration-500 group-hover:from-blue-600 group-hover:to-indigo-700"
                    style={{ width: `${Math.max(3, pctBar)}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* AUTHORITATIVE SECTION TABLE */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-xs">
        <div className="px-6 py-4 bg-slate-50/70 dark:bg-slate-800/60 border-b border-slate-200 dark:border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TableIcon size={16} className="text-slate-600" />
            <h3 className="text-sm font-extrabold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
              Section Breakdown Table
            </h3>
          </div>
          <span className="text-[11px] font-bold text-slate-500">
            Total Academic Exceptions: <strong className="text-slate-800">{totalAcademicExceptions}</strong>
          </span>
        </div>

        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full text-left border-collapse min-w-[650px]">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700 bg-slate-100/60 dark:bg-slate-800/80 text-[11px] font-black uppercase text-slate-600 dark:text-slate-300 tracking-wider">
                <th className="py-3 px-4 w-16 text-center">Rank</th>
                <th className="py-3 px-4">School Section</th>
                <th className="py-3 px-4 text-center">Exceptions</th>
                <th className="py-3 px-4 text-center">Affected Staff</th>
                <th className="py-3 px-4 text-center">Per-Head</th>
                <th className="py-3 px-4 text-center">Academic Share</th>
                <th className="py-3 px-4 text-center bg-indigo-50/40 text-indigo-900">
                  {comparisons?.priorTerm ? `Shift vs ${comparisons.priorTerm}` : 'Term Comparison'}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs font-semibold">
              {rankedSections.map((sec) => {
                const segComp = comparisons?.segmentComparisons?.[sec.key];
                return (
                  <tr
                    key={sec.key || sec.label}
                    onClick={() => onDrillSection && onDrillSection(sec.key || sec.label, sec.label)}
                    className="hover:bg-indigo-50/40 transition-colors cursor-pointer"
                    title="Click to view analytical breakdown"
                  >
                    <td className="py-3.5 px-4 text-center">
                      <span className="inline-block w-6 h-6 rounded-md bg-slate-100 text-slate-700 font-black text-xs leading-6">
                        {sec.rank}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="font-extrabold text-slate-900 dark:text-white">{sec.label}</div>
                    </td>
                    <td className="py-3.5 px-4 text-center font-black text-slate-800 dark:text-slate-200">
                      {sec.exceptionCount}
                    </td>
                    <td className="py-3.5 px-4 text-center font-bold text-slate-600 dark:text-slate-300">
                      {sec.affectedStaffCount}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <span className="inline-block px-2 py-0.5 rounded bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 font-extrabold border border-indigo-100 dark:border-indigo-500/20">
                        {sec.perHead.toFixed(2)}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-center font-bold text-slate-600 dark:text-slate-300">
                      {sec.shareOfAcademic}%
                    </td>
                    <td className="py-3.5 px-4 text-center bg-indigo-50/10">
                      {comparisons?.priorTerm ? (
                        <DeltaPill comparison={segComp?.exceptionComparison} />
                      ) : (
                        <span className="text-[10px] text-slate-400 font-mono" title="Comparative history is not yet available">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
