import React from 'react';
import { ShieldAlert, AlertCircle, ArrowRight } from 'lucide-react';

export default function SeverityBreakdown({
  criticalCount = 0,
  highCount = 0,
  mediumCount = 0,
  lowCount = 0,
  totalAffectedStaffCount = 0,
  onOpenPanel,
}) {
  return (
    <div className="rounded-[24px] border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-6 shadow-md space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 dark:border-white/5 pb-4">
        <div>
          <h3 className="text-base font-black text-slate-800 dark:text-white flex items-center gap-2.5">
            <div className="p-1.5 bg-rose-100 text-rose-600 rounded-lg">
              <ShieldAlert size={16} />
            </div>
            Derived Staff Severity Breakdown
          </h3>
          <p className="text-[13px] text-slate-500 font-medium mt-1.5 ml-10">
            Click any tier to isolate personnel.
          </p>
        </div>
        <span className="text-xs font-black text-rose-700 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/30 border border-rose-100 dark:border-rose-500/20 px-4 py-2 rounded-xl shadow-sm shrink-0">
          {totalAffectedStaffCount} Affected Staff
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* STAT 4: Critical Staff */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('CRITICAL_STAFF')}
          className="group text-left p-5 rounded-2xl border-2 border-rose-100 dark:border-rose-500/20 bg-rose-50/50 dark:bg-rose-950/20 hover:bg-rose-50 dark:hover:bg-rose-950/30 hover:border-rose-400 hover:shadow-lg transition-all duration-300 cursor-pointer relative overflow-hidden"
        >
          <div className="absolute right-0 bottom-0 opacity-5 group-hover:scale-150 transition-transform duration-500">
            <AlertCircle size={80} />
          </div>
          <div className="flex items-center justify-between mb-3 relative z-10">
            <span className="text-[11px] font-black uppercase tracking-widest text-rose-700">
              4. Critical
            </span>
            <span className="text-[10px] font-black text-rose-700 dark:text-rose-300 bg-rose-200/50 dark:bg-rose-900/40 px-2 py-1 rounded shadow-sm">
              ≥ 4 Exceptions
            </span>
          </div>
          <div className="text-3xl font-black text-rose-700 relative z-10">
            {criticalCount}
          </div>
          <div className="mt-2 text-[11px] font-bold text-rose-600 flex items-center justify-between relative z-10">
            <span className="bg-white/50 dark:bg-slate-900/50 px-2 py-0.5 rounded">Immediate escalation</span>
            <ArrowRight size={14} className="group-hover:translate-x-1 transition-transform" />
          </div>
        </button>

        {/* STAT 5: High Staff */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('HIGH_STAFF')}
          className="group text-left p-5 rounded-2xl border-2 border-orange-100 dark:border-orange-500/20 bg-orange-50/50 dark:bg-orange-950/20 hover:bg-orange-50 dark:hover:bg-orange-950/30 hover:border-orange-400 hover:shadow-lg transition-all duration-300 cursor-pointer relative overflow-hidden"
        >
          <div className="flex items-center justify-between mb-3 relative z-10">
            <span className="text-[11px] font-black uppercase tracking-widest text-orange-700">
              5. High
            </span>
            <span className="text-[10px] font-black text-orange-700 dark:text-orange-300 bg-orange-200/50 dark:bg-orange-900/40 px-2 py-1 rounded shadow-sm">
              3 Exceptions
            </span>
          </div>
          <div className="text-3xl font-black text-orange-700 relative z-10">
            {highCount}
          </div>
          <div className="mt-2 text-[11px] font-bold text-orange-600 flex items-center justify-between relative z-10">
            <span className="bg-white/50 dark:bg-slate-900/50 px-2 py-0.5 rounded">Priority review</span>
            <ArrowRight size={14} className="group-hover:translate-x-1 transition-transform" />
          </div>
        </button>

        {/* STAT 6: Medium Staff */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('MEDIUM_STAFF')}
          className="group text-left p-5 rounded-2xl border-2 border-amber-100 dark:border-amber-500/20 bg-amber-50/50 dark:bg-amber-950/20 hover:bg-amber-50 dark:hover:bg-amber-950/30 hover:border-amber-400 hover:shadow-lg transition-all duration-300 cursor-pointer relative overflow-hidden"
        >
          <div className="flex items-center justify-between mb-3 relative z-10">
            <span className="text-[11px] font-black uppercase tracking-widest text-amber-800">
              6. Medium
            </span>
            <span className="text-[10px] font-black text-amber-800 dark:text-amber-300 bg-amber-200/50 dark:bg-amber-900/40 px-2 py-1 rounded shadow-sm">
              2 Exceptions
            </span>
          </div>
          <div className="text-3xl font-black text-amber-800 relative z-10">
            {mediumCount}
          </div>
          <div className="mt-2 text-[11px] font-bold text-amber-700 flex items-center justify-between relative z-10">
            <span className="bg-white/50 dark:bg-slate-900/50 px-2 py-0.5 rounded">Advisory stage</span>
            <ArrowRight size={14} className="group-hover:translate-x-1 transition-transform" />
          </div>
        </button>

        {/* STAT 7: Low Staff */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('LOW_STAFF')}
          className="group text-left p-5 rounded-2xl border-2 border-blue-100 dark:border-blue-500/20 bg-blue-50/50 dark:bg-blue-950/20 hover:bg-blue-50 dark:hover:bg-blue-950/30 hover:border-blue-400 hover:shadow-lg transition-all duration-300 cursor-pointer relative overflow-hidden"
        >
          <div className="flex items-center justify-between mb-3 relative z-10">
            <span className="text-[11px] font-black uppercase tracking-widest text-blue-700">
              7. Low
            </span>
            <span className="text-[10px] font-black text-blue-700 dark:text-blue-300 bg-blue-200/50 dark:bg-blue-900/40 px-2 py-1 rounded shadow-sm">
              1 Exception
            </span>
          </div>
          <div className="text-3xl font-black text-blue-700 relative z-10">
            {lowCount}
          </div>
          <div className="mt-2 text-[11px] font-bold text-blue-600 flex items-center justify-between relative z-10">
            <span className="bg-white/50 dark:bg-slate-900/50 px-2 py-0.5 rounded">Single infraction</span>
            <ArrowRight size={14} className="group-hover:translate-x-1 transition-transform" />
          </div>
        </button>
      </div>
    </div>
  );
}
