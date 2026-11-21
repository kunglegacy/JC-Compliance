import React from 'react';
import { Table, Layers, FileSpreadsheet } from 'lucide-react';

/**
 * AnalysisTable Component (STAGE 8)
 * Renders the authoritative multidimensional category × school section blueprint matrix table.
 * Corresponds to Slide 5 in the institutional compliance presentation.
 */
export default function AnalysisTable({
  analysis,
  onDrillCategory,
}) {
  const matrix = analysis?.categories;
  const rows = matrix?.rows || [];
  const colTotals = matrix?.columnTotals || {
    jnrColl: 0,
    snrCol: 0,
    jnrPri: 0,
    snrPri: 0,
    nur: 0,
    grandTotal: 0,
  };

  if (!rows || rows.length === 0) {
    return (
      <section className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 sm:p-7 shadow-xs">
        <div className="flex items-center gap-3 pb-4 border-b border-slate-100 dark:border-white/5">
          <div className="p-2.5 bg-slate-100 text-slate-700 rounded-xl">
            <Table size={20} />
          </div>
          <div>
            <h2 className="text-lg font-black text-slate-900 dark:text-white tracking-tight">
              Multidimensional Analytical Matrix
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 font-semibold mt-0.5">
              Category distribution across school sections
            </p>
          </div>
        </div>
        <div className="py-8 text-center text-slate-400 text-xs font-semibold">
          No compliance exception matrix data available for the active period.
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 sm:p-7 shadow-xs space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-100 dark:border-white/5">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-indigo-50 text-indigo-700 rounded-xl shadow-2xs">
            <FileSpreadsheet size={20} />
          </div>
          <div>
            <h2 className="text-lg font-black text-slate-900 dark:text-white tracking-tight">
              Multidimensional Category × Section Matrix
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 font-semibold mt-0.5">
              Canonical breakdown of compliance categories across all academic divisions (Slide 5 Blueprint)
            </p>
          </div>
        </div>

        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-xs font-bold border border-slate-200 dark:border-slate-700">
          <span>{rows.length} Active Categories</span>
        </div>
      </div>

      {/* Matrix Table */}
      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-2xs">
        <div className="overflow-x-auto custom-scrollbar">
          <table className="w-full text-left border-collapse min-w-[760px] text-xs">
            <thead>
              <tr className="bg-slate-100/80 dark:bg-slate-800/80 border-b border-slate-200 dark:border-slate-700 text-[11px] font-black uppercase text-slate-600 dark:text-slate-300 tracking-wider">
                <th className="py-3 px-4 w-[280px]">Compliance Category</th>
                <th className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700">Jnr College</th>
                <th className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700">Snr College</th>
                <th className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700">Jnr Primary</th>
                <th className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700">Snr Primary</th>
                <th className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700">Nursery</th>
                <th className="py-3 px-4 text-center border-l border-slate-200 dark:border-slate-700 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-950 dark:text-indigo-200 font-black">
                  Row Total
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 font-semibold">
              {rows.map((row) => (
                <tr
                  key={row.category}
                  onClick={() => onDrillCategory && onDrillCategory(row.category)}
                  className="hover:bg-indigo-50/40 dark:hover:bg-indigo-950/30 transition-colors cursor-pointer"
                  title="Click to view analytical distribution"
                >
                  {/* Category Name */}
                  <td className="py-3 px-4">
                    <div className="font-extrabold text-slate-900 dark:text-white leading-tight">
                      {row.category}
                    </div>
                  </td>

                  {/* Junior College (JSS) */}
                  <td className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700 font-bold text-slate-800 dark:text-slate-200">
                    {row.jnrColl > 0 ? row.jnrColl : <span className="text-slate-300 font-normal">—</span>}
                  </td>

                  {/* Senior College (SSS) */}
                  <td className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700 font-bold text-slate-800 dark:text-slate-200">
                    {row.snrCol > 0 ? row.snrCol : <span className="text-slate-300 font-normal">—</span>}
                  </td>

                  {/* Junior Primary (Basic 1-3) */}
                  <td className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700 font-bold text-slate-800 dark:text-slate-200">
                    {row.jnrPri > 0 ? row.jnrPri : <span className="text-slate-300 font-normal">—</span>}
                  </td>

                  {/* Senior Primary (Basic 4-5) */}
                  <td className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700 font-bold text-slate-800 dark:text-slate-200">
                    {row.snrPri > 0 ? row.snrPri : <span className="text-slate-300 font-normal">—</span>}
                  </td>

                  {/* Nursery */}
                  <td className="py-3 px-3 text-center border-l border-slate-200 dark:border-slate-700 font-bold text-slate-800 dark:text-slate-200">
                    {row.nur > 0 ? row.nur : <span className="text-slate-300 font-normal">—</span>}
                  </td>

                  {/* Row Total */}
                  <td className="py-3 px-4 text-center border-l border-slate-200 dark:border-slate-700 bg-indigo-50/20 dark:bg-indigo-950/30 font-black text-indigo-700 dark:text-indigo-300 text-sm">
                    {row.rowTotal}
                  </td>
                </tr>
              ))}

              {/* Column Totals Summary Row */}
              <tr className="border-t-2 border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800 font-black text-slate-900 dark:text-white">
                <td className="py-3.5 px-4 uppercase tracking-wider text-xs">
                  Section Column Totals
                </td>
                <td className="py-3.5 px-3 text-center border-l border-slate-300 dark:border-slate-700 text-sm">
                  {colTotals.jnrColl}
                </td>
                <td className="py-3.5 px-3 text-center border-l border-slate-300 dark:border-slate-700 text-sm">
                  {colTotals.snrCol}
                </td>
                <td className="py-3.5 px-3 text-center border-l border-slate-300 dark:border-slate-700 text-sm">
                  {colTotals.jnrPri}
                </td>
                <td className="py-3.5 px-3 text-center border-l border-slate-300 dark:border-slate-700 text-sm">
                  {colTotals.snrPri}
                </td>
                <td className="py-3.5 px-3 text-center border-l border-slate-300 dark:border-slate-700 text-sm">
                  {colTotals.nur}
                </td>
                <td className="py-3.5 px-4 text-center border-l border-slate-300 dark:border-slate-700 bg-indigo-600 text-white font-black text-base shadow-sm">
                  {colTotals.grandTotal}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="p-3 bg-slate-50 dark:bg-slate-800/50 border-t border-slate-200 dark:border-slate-700 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 font-medium px-4">
          <span>Click any category row to view multidimensional analytical breakdown.</span>
          <span>Dimensional matrix dynamically generated from verified database records.</span>
        </div>
      </div>
    </section>
  );
}
