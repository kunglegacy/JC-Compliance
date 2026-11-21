import React from 'react';
import { EyeOff, Eye, ArrowRight } from 'lucide-react';

export default function AuditReviewStates({
  unseenCount = 0,
  seenCount = 0,
  onOpenPanel,
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
      {/* STAT 9: Unseen */}
      <button
        type="button"
        onClick={() => onOpenPanel?.('UNSEEN_EXCEPTIONS')}
        className="group text-left p-6 rounded-[24px] border border-purple-200 dark:border-purple-500/20 bg-gradient-to-br from-purple-50/80 to-white dark:from-purple-950/20 dark:to-slate-900 hover:border-purple-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex items-center justify-between relative overflow-hidden cursor-pointer"
      >
        <div className="absolute left-0 top-0 w-1.5 h-full bg-purple-500" />
        <div className="pl-2">
          <span className="text-[11px] font-black uppercase tracking-widest text-purple-700 dark:text-purple-300 flex items-center gap-2">
            <EyeOff size={16} />
            <span>9. Unseen Exceptions</span>
          </span>
          <div className="text-4xl font-black text-purple-900 dark:text-purple-200 tracking-tight mt-3">
            {unseenCount}
          </div>
          <p className="text-xs text-purple-600/90 dark:text-purple-300 font-bold mt-2 bg-purple-100/50 dark:bg-purple-900/40 px-2 py-1 rounded w-max">
            Pending administrative review
          </p>
        </div>
        <div className="h-12 w-12 rounded-2xl bg-white dark:bg-slate-800 shadow-md text-purple-600 dark:text-purple-400 flex items-center justify-center group-hover:scale-110 group-hover:bg-purple-600 group-hover:text-white transition-all duration-300 shrink-0 border border-purple-100 dark:border-purple-500/20">
          <ArrowRight size={20} />
        </div>
      </button>

      {/* STAT 10: Seen */}
      <button
        type="button"
        onClick={() => onOpenPanel?.('SEEN_EXCEPTIONS')}
        className="group text-left p-6 rounded-[24px] border border-slate-200 dark:border-white/10 bg-gradient-to-br from-slate-50 to-white dark:from-slate-900 dark:to-slate-950 hover:border-blue-400 hover:shadow-xl hover:-translate-y-1 transition-all duration-300 flex items-center justify-between relative overflow-hidden cursor-pointer"
      >
        <div className="absolute left-0 top-0 w-1.5 h-full bg-slate-300 group-hover:bg-blue-500 transition-colors" />
        <div className="pl-2">
          <span className="text-[11px] font-black uppercase tracking-widest text-slate-500 group-hover:text-blue-600 flex items-center gap-2 transition-colors">
            <Eye size={16} />
            <span>10. Reviewed Exceptions</span>
          </span>
          <div className="text-4xl font-black text-slate-800 dark:text-white tracking-tight mt-3">
            {seenCount}
          </div>
          <p className="text-xs text-slate-500 font-bold mt-2 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-2 py-1 rounded w-max">
            Confirmed compliance logs
          </p>
        </div>
        <div className="h-12 w-12 rounded-2xl bg-white dark:bg-slate-800 shadow-md text-slate-400 dark:text-slate-300 flex items-center justify-center group-hover:scale-110 group-hover:bg-blue-600 group-hover:text-white transition-all duration-300 shrink-0 border border-slate-100 dark:border-white/10">
          <ArrowRight size={20} />
        </div>
      </button>
    </div>
  );
}
