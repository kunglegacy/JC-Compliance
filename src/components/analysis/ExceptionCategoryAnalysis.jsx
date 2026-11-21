import React, { useState } from 'react';
import {
  PieChart,
  Flame,
  Layers,
  Sparkles,
  TrendingUp,
  TrendingDown,
  Minus,
  Table as TableIcon,
  CheckCircle2,
} from 'lucide-react';

/**
 * Historical change pill helper
 */
function DeltaPill({ comparison }) {
  if (!comparison) return <span className="text-slate-400 text-xs">—</span>;

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
 * Exception Category and Pattern Analysis Component (STAGE 6)
 * Authoritative analysis of WHAT exception categories are recorded,
 * top drivers, cumulative concentration, section contributions, and data-grounded observations.
 */
export default function ExceptionCategoryAnalysis({
  analysis,
  onDrillCategory,
  onDrillSection,
}) {
  const categoryPatterns = analysis?.categoryPatterns;
  const [tableSearch, setTableSearch] = useState('');

  const rankedCategories = categoryPatterns?.rankedCategories || [];
  const topDrivers = categoryPatterns?.topDrivers || [];
  const concentration = categoryPatterns?.concentration || {};
  const sectionContributions = categoryPatterns?.sectionContributions || [];
  const observations = categoryPatterns?.observations || [];
  const priorTermName = categoryPatterns?.priorTermName;

  const filteredCategories = tableSearch
    ? rankedCategories.filter((c) =>
        c.category.toLowerCase().includes(tableSearch.toLowerCase()) ||
        c.affectedSectionsSummary.toLowerCase().includes(tableSearch.toLowerCase())
      )
    : rankedCategories;

  if (!categoryPatterns || rankedCategories.length === 0) {
    return (
      <section className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 text-center shadow-xs mt-6">
        <div className="w-10 h-10 mx-auto rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
          <PieChart size={20} />
        </div>
        <h3 className="text-sm font-black text-slate-700">Exception Category Analysis</h3>
        <p className="text-xs text-slate-500 mt-1">No exception records found for category aggregation.</p>
      </section>
    );
  }

  return (
    <section className="space-y-6 mt-8 stagger-3">
      {/* SECTION HEADER */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 dark:border-white/10 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-rose-100 text-rose-700 rounded-xl shadow-xs">
            <Flame size={20} />
          </div>
          <div>
            <h2 className="text-xl font-black text-slate-900 dark:text-white dark:text-white tracking-tight">
              Exception Category & Pattern Analysis
            </h2>
            <p className="text-xs font-semibold text-slate-500 mt-0.5">
              Aggregated frequency of recorded exception types, cumulative volume concentration, and section contributions
            </p>
          </div>
        </div>
        <div className="text-xs font-bold text-slate-500 bg-slate-100 px-3 py-1.5 rounded-xl border border-slate-200 self-start sm:self-auto">
          {rankedCategories.length} Active Categories
        </div>
      </div>

      {/* TOP EXCEPTION DRIVERS CARDS */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <Flame size={16} className="text-rose-600" />
          <h3 className="text-xs font-black uppercase tracking-wider text-slate-700">
            Top Exception Drivers
          </h3>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {topDrivers.slice(0, 3).map((driver, idx) => {
            const badgeColors = [
              'border-rose-200 bg-gradient-to-br from-rose-50/70 to-white text-rose-950',
              'border-amber-200 dark:border-amber-500/20 bg-gradient-to-br from-amber-50/70 to-white dark:from-amber-950/20 dark:to-slate-900 text-amber-950 dark:text-amber-200',
              'border-blue-200 dark:border-blue-500/20 bg-gradient-to-br from-blue-50/70 to-white dark:from-blue-950/20 dark:to-slate-900 text-blue-950 dark:text-blue-200',
            ];
            const rankColors = ['bg-rose-600', 'bg-amber-600', 'bg-blue-600'];

            return (
              <div
                key={driver.category}
                onClick={() => onDrillCategory && onDrillCategory(driver.category)}
                className={`p-5 rounded-[22px] border ${badgeColors[idx] || 'border-slate-200 bg-white'} shadow-xs flex flex-col justify-between cursor-pointer hover:scale-[1.02] transition-transform`}
                title="Click to view analytical distribution"
              >
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <span className={`px-2 py-0.5 rounded text-[10px] font-black uppercase text-white ${rankColors[idx]}`}>
                      Rank #{driver.rank}
                    </span>
                    <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                      Driver #{driver.rank}
                    </span>
                  </div>

                  <h4 className="text-sm font-black uppercase tracking-wide text-slate-900 dark:text-white leading-snug line-clamp-2" title={driver.category}>
                    {driver.category}
                  </h4>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-200/60 flex items-end justify-between">
                  <div>
                    <div className="text-2xl font-black text-slate-900 dark:text-white dark:text-white tracking-tight">
                      {driver.occurrences} <span className="text-xs font-semibold text-slate-500">occurrences</span>
                    </div>
                    <div className="text-xs font-bold text-slate-600 dark:text-slate-300 mt-0.5">
                      {driver.percentage}% of total
                    </div>
                  </div>

                  {priorTermName && (
                    <div className="text-right">
                      <div className="text-[10px] font-bold text-slate-400 mb-0.5">vs {priorTermName}</div>
                      <DeltaPill comparison={driver.historicalDelta} />
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* CONCENTRATION & SECTION CONTRIBUTION SPLIT */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* PANEL 1: EXCEPTION CONCENTRATION */}
        <div className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 mb-4">
              <PieChart size={18} className="text-indigo-600" />
              <div>
                <h3 className="text-sm font-extrabold text-slate-900 dark:text-white uppercase tracking-wider">
                  Exception Volume Concentration
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  Cumulative volume contribution across top recorded categories
                </p>
              </div>
            </div>

            {/* Executive Concentration Callout */}
            <div className="p-4 rounded-2xl bg-indigo-50/70 border border-indigo-100 mb-5">
              <div className="text-xs font-extrabold text-indigo-950">
                Top {concentration.top3Count} categories account for{' '}
                <span className="text-indigo-700 text-sm font-black underline underline-offset-2">
                  {concentration.top3Share}%
                </span>{' '}
                of total academic exception volume.
              </div>
              <p className="text-[11px] text-slate-600 mt-1 font-medium">
                80% of recorded volume is concentrated in the top {concentration.categoriesFor80PercentVolume} of {concentration.totalCategories} active categories.
              </p>
            </div>

            {/* Top 5 Cumulative Progress Bars */}
            <div className="space-y-3">
              {rankedCategories.slice(0, 5).map((cat) => (
                <div key={cat.category} className="group">
                  <div className="flex items-center justify-between text-xs mb-1">
                    <span className="font-extrabold text-slate-800 dark:text-slate-200 truncate max-w-[65%]" title={cat.category}>
                      #{cat.rank} {cat.category}
                    </span>
                    <span className="text-[11px] font-bold text-slate-600">
                      {cat.occurrences} exc ({cat.percentage}%) • Cum: <strong className="text-indigo-700">{cat.cumulativePercentage}%</strong>
                    </span>
                  </div>
                  <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-indigo-600 rounded-full transition-all duration-500"
                      style={{ width: `${Math.min(100, cat.cumulativePercentage)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* PANEL 2: SECTION CONTRIBUTION BREAKDOWN */}
        <div className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 mb-4">
              <Layers size={18} className="text-blue-600" />
              <div>
                <h3 className="text-sm font-extrabold text-slate-900 dark:text-white uppercase tracking-wider">
                  School Section Contribution
                </h3>
                <p className="text-xs text-slate-500 font-medium">
                  Each school tier's proportional share of total exception volume
                </p>
              </div>
            </div>

            <div className="space-y-3.5">
              {sectionContributions.map((sec) => (
                <div
                  key={sec.section}
                  onClick={() => onDrillSection && onDrillSection(sec.section, sec.section)}
                  className="p-3 rounded-xl border border-slate-100 bg-slate-50/60 hover:bg-indigo-50/50 cursor-pointer transition-colors"
                  title="Click to view section breakdown"
                >
                  <div className="flex items-center justify-between text-xs mb-1.5">
                    <div className="flex items-center gap-2">
                      <span className="font-extrabold text-slate-900">{sec.section}</span>
                      {sec.isInstitutionLevel && (
                        <span className="px-1.5 py-0.2 rounded text-[10px] font-black uppercase bg-purple-100 text-purple-700 border border-purple-200">
                          Institution-Level
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="font-black text-slate-800">{sec.occurrences} exc</span>
                      <span className="text-xs font-black text-blue-700 min-w-10 text-right">
                        {sec.percentage}%
                      </span>
                    </div>
                  </div>

                  <div className="h-2 w-full bg-slate-200/70 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        sec.isInstitutionLevel
                          ? 'bg-purple-600'
                          : 'bg-gradient-to-r from-blue-500 to-indigo-600'
                      }`}
                      style={{ width: `${Math.max(2, sec.percentage)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* DATA-GROUNDED PATTERN OBSERVATIONS */}
      {observations.length > 0 && (
        <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 shadow-xs">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles size={18} className="text-amber-600" />
            <div>
              <h3 className="text-sm font-extrabold text-slate-900 dark:text-white uppercase tracking-wider">
                Data-Grounded Pattern Observations
              </h3>
              <p className="text-xs text-slate-500 font-medium">
                Direct mathematical observations verified by database records (no speculative causes)
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {observations.map((obs) => (
              <div
                key={obs.id}
                className="p-4 rounded-xl border border-slate-100 bg-slate-50/70 flex items-start gap-3"
              >
                <div className="p-1.5 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 shrink-0 mt-0.5">
                  <CheckCircle2 size={16} className="text-emerald-600" />
                </div>
                <div className="space-y-1">
                  <div className="text-[11px] font-black uppercase tracking-wider text-slate-400">
                    {obs.title}
                  </div>
                  <div className="text-xs font-medium text-slate-700 leading-relaxed">
                    {obs.statement}
                  </div>
                  <div className="inline-block text-[11px] font-black text-indigo-700 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded mt-1">
                    {obs.highlight}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* COMPREHENSIVE CATEGORY ANALYSIS TABLE */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-xs">
        <div className="px-6 py-4 bg-slate-50/70 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <TableIcon size={16} className="text-slate-600" />
            <h3 className="text-sm font-extrabold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
              Category Frequency Table
            </h3>
          </div>

          {/* Quick search filter */}
          <input
            type="text"
            placeholder="Search category or section..."
            value={tableSearch}
            onChange={(e) => setTableSearch(e.target.value)}
            className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 max-w-xs"
          />
        </div>

        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full text-left border-collapse min-w-[700px]">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700 bg-slate-100/60 dark:bg-slate-800/80 text-[11px] font-black uppercase text-slate-600 dark:text-slate-300 tracking-wider">
                <th className="py-3 px-4 w-14 text-center">Rank</th>
                <th className="py-3 px-4">Compliance Category / Check</th>
                <th className="py-3 px-4 text-center">Occurrences</th>
                <th className="py-3 px-4 text-center">Share (%)</th>
                <th className="py-3 px-4 text-center">Cumulative (%)</th>
                <th className="py-3 px-4">Affected Sections</th>
                {priorTermName && (
                  <th className="py-3 px-4 text-center bg-indigo-50/30 text-indigo-900">
                    Term Delta
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs font-semibold">
              {filteredCategories.map((cat) => (
                <tr
                  key={cat.category}
                  onClick={() => onDrillCategory && onDrillCategory(cat.category)}
                  className="hover:bg-indigo-50/40 transition-colors cursor-pointer"
                  title="Click to view analytical distribution"
                >
                  <td className="py-3.5 px-4 text-center">
                    <span className="inline-block w-6 h-6 rounded-md bg-slate-100 text-slate-700 font-black text-xs leading-6">
                      {cat.rank}
                    </span>
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="font-extrabold text-slate-900">{cat.category}</div>
                  </td>
                  <td className="py-3.5 px-4 text-center font-black text-slate-800">
                    {cat.occurrences}
                  </td>
                  <td className="py-3.5 px-4 text-center font-bold text-slate-600">
                    {cat.percentage}%
                  </td>
                  <td className="py-3.5 px-4 text-center font-bold text-indigo-700">
                    {cat.cumulativePercentage}%
                  </td>
                  <td className="py-3.5 px-4">
                    <div className="text-[11px] font-medium text-slate-500 truncate max-w-xs" title={cat.affectedSectionsSummary}>
                      {cat.affectedSectionsSummary}
                    </div>
                  </td>
                  {priorTermName && (
                    <td className="py-3.5 px-4 text-center bg-indigo-50/10">
                      <DeltaPill comparison={cat.historicalDelta} />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
