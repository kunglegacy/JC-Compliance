import React from 'react';
import { Users, AlertTriangle, CheckCircle2, FileText, ArrowRight } from 'lucide-react';

export default function AnalysisSummary({
  totalStaffCount = 0,
  staffWithExceptionsCount = 0,
  staffWithExceptionsPct = 0,
  staffWithoutExceptionsCount = 0,
  noExceptionsPct = 0,
  totalExceptionsCount = 0,
  filteredStaffExceptionsCount = 0,
  filteredCdsExceptionsCount = 0,
  academicCount = 0,
  operationsCount = 0,
  disciplineCount = 0,
  onOpenPanel,
}) {
  return (
    <section className="space-y-5 stagger-2 mt-6">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4">
        <div>
          <h2 className="text-lg font-black text-slate-800 dark:text-white tracking-tight flex items-center gap-2.5">
            <div className="p-1.5 bg-blue-100 text-blue-600 rounded-lg">
              <Users size={18} />
            </div>
            <span>Authoritative Personnel Metrics</span>
          </h2>
          <p className="text-[13px] text-slate-500 font-medium mt-1.5 ml-10">
            <strong className="text-slate-700">Click any metric to inspect analytical breakdown across school divisions.</strong>
          </p>
        </div>
      </div>

      {/* PRIMARY STATS GRID */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
        
        {/* STAT 1: Total Staff */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('TOTAL_STAFF')}
          className="group text-left p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 hover:border-blue-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between w-full mb-4">
            <span className="text-[11px] font-black uppercase tracking-widest text-slate-400">
              1. Total Staff
            </span>
            <div className="h-10 w-10 rounded-xl bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 flex items-center justify-center group-hover:scale-110 group-hover:bg-blue-600 group-hover:text-white transition-all duration-300 shadow-sm">
              <Users size={18} />
            </div>
          </div>
          <div>
            <div className="text-4xl font-black text-slate-800 dark:text-white tracking-tight">
              {totalStaffCount}
            </div>
            <div className="mt-2 text-xs font-bold text-slate-500 flex items-center gap-1.5">
              <span>Active Academic + Operations</span>
              <ArrowRight size={14} className="text-blue-500 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all ml-auto" />
            </div>
          </div>
        </button>

        {/* STAT 2: Staff With Exceptions */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('STAFF_WITH_EXCEPTIONS')}
          className="group text-left p-6 rounded-[24px] border border-amber-200/80 dark:border-amber-500/20 bg-gradient-to-br from-amber-50/50 to-white dark:from-amber-950/20 dark:to-slate-900 hover:border-amber-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between w-full mb-4">
            <span className="text-[11px] font-black uppercase tracking-widest text-amber-700 dark:text-amber-400">
              2. Flagged Staff
            </span>
            <div className="h-10 w-10 rounded-xl bg-amber-100 dark:bg-amber-900/40 text-amber-600 dark:text-amber-400 flex items-center justify-center group-hover:scale-110 group-hover:bg-amber-500 group-hover:text-white transition-all duration-300 shadow-sm">
              <AlertTriangle size={18} />
            </div>
          </div>
          <div>
            <div className="text-4xl font-black text-amber-900 dark:text-amber-200 tracking-tight flex items-baseline gap-2">
              <span>{staffWithExceptionsCount}</span>
              <span className="text-lg font-bold text-amber-600/60">/ {totalStaffCount}</span>
            </div>
            <div className="mt-2 text-xs font-bold text-amber-700 dark:text-amber-400 flex items-center gap-1.5">
              <span className="bg-amber-100/50 dark:bg-amber-900/40 px-2 py-0.5 rounded">{staffWithExceptionsPct}% of roster</span>
              <ArrowRight size={14} className="text-amber-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all ml-auto" />
            </div>
          </div>
        </button>

        {/* STAT 3: No Exceptions */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('NO_EXCEPTIONS')}
          className="group text-left p-6 rounded-[24px] border border-emerald-200/80 dark:border-emerald-500/20 bg-gradient-to-br from-emerald-50/50 to-white dark:from-emerald-950/20 dark:to-slate-900 hover:border-emerald-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between w-full mb-4">
            <span className="text-[11px] font-black uppercase tracking-widest text-emerald-700 dark:text-emerald-400">
              3. Clean Standing
            </span>
            <div className="h-10 w-10 rounded-xl bg-emerald-100 dark:bg-emerald-900/40 text-emerald-600 dark:text-emerald-400 flex items-center justify-center group-hover:scale-110 group-hover:bg-emerald-500 group-hover:text-white transition-all duration-300 shadow-sm">
              <CheckCircle2 size={18} />
            </div>
          </div>
          <div>
            <div className="text-4xl font-black text-emerald-900 dark:text-emerald-200 tracking-tight flex items-baseline gap-2">
              <span>{staffWithoutExceptionsCount}</span>
              <span className="text-lg font-bold text-emerald-600/60">/ {totalStaffCount}</span>
            </div>
            <div className="mt-2 text-xs font-bold text-emerald-700 dark:text-emerald-400 flex items-center gap-1.5">
              <span className="bg-emerald-100/50 dark:bg-emerald-900/40 px-2 py-0.5 rounded">{noExceptionsPct}% optimal</span>
              <ArrowRight size={14} className="text-emerald-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all ml-auto" />
            </div>
          </div>
        </button>

        {/* STAT 8: Total Exceptions */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('TOTAL_EXCEPTIONS')}
          className="group text-left p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 hover:border-indigo-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex flex-col justify-between relative overflow-hidden cursor-pointer"
        >
          <div className="absolute top-0 left-0 w-1.5 h-full bg-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity" />
          <div className="flex items-center justify-between w-full mb-4 pl-2">
            <span className="text-[11px] font-black uppercase tracking-widest text-slate-400 group-hover:text-indigo-600 transition-colors">
              8. Total Exceptions
            </span>
            <div className="h-10 w-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400 flex items-center justify-center group-hover:scale-110 group-hover:bg-indigo-600 group-hover:text-white transition-all duration-300 shadow-sm">
              <FileText size={18} />
            </div>
          </div>
          <div className="pl-2">
            <div className="text-4xl font-black text-slate-800 dark:text-white tracking-tight">
              {totalExceptionsCount}
            </div>
            <div className="mt-2 text-[11px] font-bold text-slate-500 flex items-center gap-1 flex-wrap">
              <span>{academicCount || filteredStaffExceptionsCount} Acad · {operationsCount} Ops · {disciplineCount} Disc · {filteredCdsExceptionsCount} CDS</span>
              <ArrowRight size={14} className="text-indigo-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all ml-auto shrink-0" />
            </div>
          </div>
        </button>
      </div>
    </section>
  );
}
