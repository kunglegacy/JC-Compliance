import React from 'react';
import { Activity, ShieldCheck, FileText, Eye, EyeOff, ArrowRight } from 'lucide-react';

export default function CdsAnalysis({
  totalCdsCount = 0,
  seenCdsCount = 0,
  unseenCdsCount = 0,
  onOpenPanel,
}) {
  return (
    <section className="rounded-[24px] border border-indigo-200 dark:border-indigo-500/20 bg-gradient-to-br from-indigo-50/50 via-white to-slate-50 dark:from-indigo-950/20 dark:via-slate-900 dark:to-slate-950 p-8 shadow-lg stagger-3 relative overflow-hidden">
      <div className="absolute top-0 right-0 p-8 opacity-5 pointer-events-none">
        <Activity size={120} className="text-indigo-600" />
      </div>
      
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-indigo-100 dark:border-white/5 pb-5 mb-6 relative z-10">
        <div>
          <span className="inline-flex items-center gap-1.5 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 px-2.5 py-1 rounded-lg text-[10px] font-black uppercase tracking-widest border border-indigo-200 dark:border-indigo-500/30 mb-2">
            <ShieldCheck size={12} /> Separate Division Domain
          </span>
          <h2 className="text-xl font-black text-slate-800 dark:text-white">
            Corporate Digital Services (CDS)
          </h2>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-5 relative z-10">
        {/* Total CDS Flags */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('CDS_TOTAL')}
          className="group text-left p-5 rounded-2xl border border-indigo-100 dark:border-white/10 bg-white dark:bg-slate-900 hover:border-indigo-400 hover:shadow-lg transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between text-[11px] font-black text-indigo-800 uppercase tracking-widest mb-3">
            <span>Total CDS Flags</span>
            <FileText size={16} className="text-indigo-500" />
          </div>
          <div className="text-3xl font-black text-slate-800 dark:text-white tracking-tight">
            {totalCdsCount}
          </div>
          <div className="mt-2 text-xs font-bold text-slate-400 flex items-center justify-between">
            <span>CDS logs</span>
            <ArrowRight size={14} className="text-indigo-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
          </div>
        </button>

        {/* Seen CDS Flags */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('CDS_SEEN')}
          className="group text-left p-5 rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 hover:border-blue-400 hover:shadow-lg transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between text-[11px] font-black text-slate-600 group-hover:text-blue-600 transition-colors uppercase tracking-widest mb-3">
            <span>Seen CDS Flags</span>
            <Eye size={16} className="text-slate-400 group-hover:text-blue-500 transition-colors" />
          </div>
          <div className="text-3xl font-black text-slate-800 dark:text-white tracking-tight">
            {seenCdsCount}
          </div>
          <div className="mt-2 text-xs font-bold text-slate-400 flex items-center justify-between">
            <span>Confirmed reviews</span>
            <ArrowRight size={14} className="text-blue-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
          </div>
        </button>

        {/* Unseen CDS Flags */}
        <button
          type="button"
          onClick={() => onOpenPanel?.('CDS_UNSEEN')}
          className="group text-left p-5 rounded-2xl border border-purple-200 dark:border-purple-500/20 bg-purple-50/30 dark:bg-purple-950/20 hover:bg-white dark:hover:bg-slate-900 hover:border-purple-400 hover:shadow-lg transition-all duration-300 flex flex-col justify-between cursor-pointer"
        >
          <div className="flex items-center justify-between text-[11px] font-black text-purple-700 uppercase tracking-widest mb-3">
            <span>Unseen CDS Flags</span>
            <EyeOff size={16} className="text-purple-500" />
          </div>
          <div className="text-3xl font-black text-purple-900 dark:text-purple-200 tracking-tight">
            {unseenCdsCount}
          </div>
          <div className="mt-2 text-xs font-bold text-purple-500 flex items-center justify-between">
            <span>Pending review</span>
            <ArrowRight size={14} className="text-purple-600 opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
          </div>
        </button>
      </div>
    </section>
  );
}
