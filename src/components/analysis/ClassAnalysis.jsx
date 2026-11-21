import React, { useState, useMemo } from 'react';
import {
  GraduationCap,
  Award,
  Layers,
  BarChart2,
  Table as TableIcon,
  Tag,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

/**
 * Class-Level Compliance Analysis Component
 * Dynamically displays class exception distribution, subcategory breakdowns,
 * and section totals adapting to live database records without hardcoding fixed arms.
 */
export default function ClassAnalysis({ analysis }) {
  const classesData = analysis?.classes;
  const [viewMode, setViewMode] = useState('section'); // 'section' | 'ranked'
  const [expandedSections, setExpandedSections] = useState({});

  const bySection = classesData?.bySection || [];
  const allClassesRanked = classesData?.allClassesRanked || [];
  const totalClassesCount = classesData?.totalClassesCount || 0;
  const unspecifiedCount = classesData?.unspecifiedCount || 0;

  // Toggle section accordion
  const toggleSection = (secKey) => {
    setExpandedSections((prev) => ({
      ...prev,
      [secKey]: prev[secKey] === false ? true : false,
    }));
  };

  const topClass = allClassesRanked[0];
  const maxClassCount = useMemo(() => {
    if (allClassesRanked.length === 0) return 10;
    return Math.max(...allClassesRanked.map((c) => c.exceptionCount), 5);
  }, [allClassesRanked]);

  if (!classesData || allClassesRanked.length === 0) {
    return (
      <section className="p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 text-center shadow-xs mt-6">
        <div className="w-10 h-10 mx-auto rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mb-2">
          <GraduationCap size={20} />
        </div>
        <h3 className="text-sm font-black text-slate-700">Class Analysis</h3>
        <p className="text-xs text-slate-500 mt-1">No class exception records logged for this period.</p>
      </section>
    );
  }

  return (
    <section className="space-y-6 mt-8 stagger-3">
      {/* SECTION HEADER & VIEW CONTROLS */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200 dark:border-white/10 pb-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-purple-100 text-purple-700 rounded-xl shadow-xs">
            <GraduationCap size={20} />
          </div>
          <div>
            <h2 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">
              Class-Level Exception Analysis
            </h2>
            <p className="text-xs font-semibold text-slate-500 mt-0.5">
              Classroom exception frequency, subcategory breakdown, and section groupings
            </p>
          </div>
        </div>

        {/* View Mode Toggle */}
        <div className="inline-flex p-1 bg-slate-100 dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-bold self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setViewMode('section')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              viewMode === 'section'
                ? 'bg-white text-purple-700 shadow-xs font-black'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Grouped by Section
          </button>
          <button
            type="button"
            onClick={() => setViewMode('ranked')}
            className={`px-3 py-1.5 rounded-lg transition-all ${
              viewMode === 'ranked'
                ? 'bg-white text-purple-700 shadow-xs font-black'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            All Classes Ranked
          </button>
        </div>
      </div>

      {/* SUMMARY CARDS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Card 1: Top Class */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Highest Class Volume</span>
            <Award size={16} className="text-amber-500" />
          </div>
          <div className="text-lg font-black text-slate-800 dark:text-slate-200 dark:text-white">
            {topClass?.className}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1 flex items-center gap-1.5">
            <span className="text-rose-600 font-black">{topClass?.exceptionCount} Exceptions</span>
            <span>•</span>
            <span>{topClass?.sectionLabel}</span>
          </div>
        </div>

        {/* Card 2: Total Classes Recorded */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Classroom Reach</span>
            <Layers size={16} className="text-purple-500" />
          </div>
          <div className="text-lg font-black text-slate-800 dark:text-slate-200 dark:text-white">
            {totalClassesCount} Distinct Classes
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1">
            {bySection.length} School Sections Represented
          </div>
        </div>

        {/* Card 3: Top Prevalent Category */}
        <div className="p-4 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 shadow-xs">
          <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-wider text-slate-400 mb-2">
            <span>Leading Check in Top Class</span>
            <Tag size={16} className="text-blue-500" />
          </div>
          <div className="text-sm font-black text-slate-800 dark:text-slate-200 dark:text-white truncate" title={topClass?.topCategory}>
            {topClass?.topCategory || 'General Check'}
          </div>
          <div className="text-xs font-bold text-slate-500 mt-1">
            {unspecifiedCount > 0 ? (
              <span className="text-amber-600 font-bold">{unspecifiedCount} exceptions without specified class</span>
            ) : (
              <span className="text-emerald-600 font-bold">100% classes specified</span>
            )}
          </div>
        </div>
      </div>

      {/* RANKED HORIZONTAL BAR VISUALIZATION (Top 8 Classes) */}
      <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 shadow-xs">
        <div className="flex items-center gap-2 mb-5">
          <BarChart2 size={18} className="text-purple-600" />
          <h3 className="text-sm font-extrabold text-slate-900 dark:text-white dark:text-white uppercase tracking-wider">
            Top Class Exception Hotspots
          </h3>
        </div>

        <div className="space-y-3.5">
          {allClassesRanked.slice(0, 8).map((cls) => {
            const pct = maxClassCount > 0 ? (cls.exceptionCount / maxClassCount) * 100 : 0;
            return (
              <div key={cls.className} className="group">
                <div className="flex items-center justify-between text-xs mb-1">
                  <div className="flex items-center gap-2">
                    <span className="w-5 h-5 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 flex items-center justify-center font-black text-[10px]">
                      #{cls.rank}
                    </span>
                    <span className="font-extrabold text-slate-800 dark:text-slate-200">{cls.className}</span>
                    <span className="text-[10px] font-bold text-purple-700 bg-purple-50 border border-purple-100 px-1.5 py-0.2 rounded">
                      {cls.sectionLabel}
                    </span>
                  </div>
                  <div className="flex items-center gap-3 font-semibold text-slate-600">
                    <span className="text-[11px] text-slate-500 font-medium truncate max-w-xs hidden sm:inline" title={cls.topSubcategoriesSummary}>
                      {cls.topSubcategoriesSummary}
                    </span>
                    <span className="font-black text-slate-900">{cls.exceptionCount} exc</span>
                  </div>
                </div>

                <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-purple-500 to-indigo-600 rounded-full transition-all duration-500 group-hover:from-purple-600 group-hover:to-indigo-700"
                    style={{ width: `${Math.max(3, pct)}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* VIEW 1: GROUPED BY SECTION */}
      {viewMode === 'section' && (
        <div className="space-y-4">
          {bySection.map((secGroup) => {
            const isCollapsed = expandedSections[secGroup.sectionKey] === false;
            return (
              <div
                key={secGroup.sectionKey}
                className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-xs"
              >
                {/* SECTION HEADER ROW WITH TOTALS */}
                <button
                  type="button"
                  onClick={() => toggleSection(secGroup.sectionKey)}
                  className="w-full px-6 py-4 bg-slate-50/80 hover:bg-slate-100/70 border-b border-slate-200 flex items-center justify-between text-left transition-colors cursor-pointer"
                >
                  <div className="flex items-center gap-3">
                    <div className="p-1.5 rounded-lg bg-purple-100 text-purple-700 font-black text-xs">
                      {secGroup.classes.length}
                    </div>
                    <div>
                      <h3 className="text-sm font-black text-slate-800 dark:text-slate-200 uppercase tracking-wide">
                        {secGroup.sectionLabel}
                      </h3>
                      <p className="text-[11px] font-semibold text-slate-500">
                        Section Total: <strong className="text-slate-800">{secGroup.totalExceptions} Exceptions</strong> • {secGroup.affectedStaffCount} Staff • {secGroup.perHead} p/head
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 text-slate-400">
                    {isCollapsed ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
                  </div>
                </button>

                {/* SECTION CLASSES TABLE */}
                {!isCollapsed && (
                  <div className="overflow-x-auto custom-scrollbar">
                    <table className="w-full text-left border-collapse min-w-[650px]">
                      <thead>
                        <tr className="border-b border-slate-100 bg-slate-50/40 text-[10px] font-black uppercase text-slate-500 tracking-wider">
                          <th className="py-2.5 px-4 w-12 text-center">Rank</th>
                          <th className="py-2.5 px-4 w-44">Class</th>
                          <th className="py-2.5 px-4 text-center w-24">Exceptions</th>
                          <th className="py-2.5 px-4 text-center w-24">Staff</th>
                          <th className="py-2.5 px-4 text-center w-28">Section Share</th>
                          <th className="py-2.5 px-4">Subcategory Breakdown</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs font-semibold">
                        {secGroup.classes.map((cls) => (
                          <tr key={cls.className} className="hover:bg-slate-50/70 transition-colors">
                            <td className="py-3 px-4 text-center text-slate-400 font-bold">
                              #{cls.rank}
                            </td>
                            <td className="py-3 px-4">
                              <span className="font-extrabold text-slate-900 dark:text-white">
                                {cls.className === 'Not specified' ? (
                                  <span className="italic text-slate-400">Not specified</span>
                                ) : (
                                  cls.className
                                )}
                              </span>
                            </td>
                            <td className="py-3 px-4 text-center font-black text-slate-800 dark:text-slate-200">
                              {cls.exceptionCount}
                            </td>
                            <td className="py-3 px-4 text-center font-bold text-slate-600">
                              {cls.affectedStaffCount}
                            </td>
                            <td className="py-3 px-4 text-center">
                              <span className="inline-block px-2 py-0.5 rounded bg-purple-50 text-purple-700 font-bold border border-purple-100 text-[11px]">
                                {cls.shareOfSection}%
                              </span>
                            </td>
                            <td className="py-3 px-4">
                              <div className="flex flex-wrap items-center gap-1.5">
                                {cls.subcategories.slice(0, 3).map((sc) => (
                                  <span
                                    key={sc.category}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-200"
                                  >
                                    <span>{sc.category}</span>
                                    <strong className="text-purple-700">({sc.count})</strong>
                                  </span>
                                ))}
                                {cls.subcategories.length > 3 && (
                                  <span className="text-[10px] font-bold text-slate-400">
                                    +{cls.subcategories.length - 3} more
                                  </span>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* VIEW 2: ALL CLASSES RANKED */}
      {viewMode === 'ranked' && (
        <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 overflow-hidden shadow-xs">
          <div className="px-6 py-4 bg-slate-50/70 dark:bg-slate-800/60 border-b border-slate-200 dark:border-white/10 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <TableIcon size={16} className="text-slate-600" />
              <h3 className="text-sm font-extrabold text-slate-800 dark:text-slate-200 uppercase tracking-wider">
                All Classes Ranked (Full School)
              </h3>
            </div>
            <span className="text-[11px] font-bold text-slate-500">
              Total Classes: <strong className="text-slate-800">{totalClassesCount}</strong>
            </span>
          </div>

          <div className="overflow-x-auto custom-scrollbar">
            <table className="w-full text-left border-collapse min-w-[700px]">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700 bg-slate-100/60 dark:bg-slate-800/80 text-[11px] font-black uppercase text-slate-600 dark:text-slate-300 tracking-wider">
                  <th className="py-3 px-4 w-14 text-center">Rank</th>
                  <th className="py-3 px-4">Class</th>
                  <th className="py-3 px-4">School Section</th>
                  <th className="py-3 px-4 text-center">Exceptions</th>
                  <th className="py-3 px-4 text-center">Staff Flagged</th>
                  <th className="py-3 px-4">Primary Subcategory</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-xs font-semibold">
                {allClassesRanked.map((cls) => (
                  <tr key={cls.className} className="hover:bg-slate-50/80 transition-colors">
                    <td className="py-3.5 px-4 text-center">
                      <span className="inline-block w-6 h-6 rounded-md bg-slate-100 text-slate-700 font-black text-xs leading-6">
                        {cls.rank}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="font-extrabold text-slate-900 dark:text-white">
                        {cls.className === 'Not specified' ? (
                          <span className="italic text-slate-400">Not specified</span>
                        ) : (
                          cls.className
                        )}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="text-[11px] font-bold text-slate-600 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                        {cls.sectionLabel}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-center font-black text-slate-800 dark:text-slate-200">
                      {cls.exceptionCount}
                    </td>
                    <td className="py-3.5 px-4 text-center font-bold text-slate-600">
                      {cls.affectedStaffCount}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-semibold bg-purple-50 text-purple-700 border border-purple-100">
                        <span>{cls.topCategory}</span>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
