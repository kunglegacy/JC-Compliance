import React from 'react';
import { Calendar } from 'lucide-react';

export default function AnalysisFilters({
  activeDateLabel = 'All Time',
  datePreset = 'all',
  onSelectPreset,
  customStartDate = '',
  customEndDate = '',
  onCustomStartDateChange,
  onCustomEndDateChange,
}) {
  const presets = [
    { id: 'all', label: 'All Time' },
    { id: 'this_month', label: 'This Month' },
    { id: 'last_30_days', label: 'Last 30 Days' },
    { id: 'custom', label: 'Custom' },
  ];

  return (
    <div className="relative z-10 mt-8 pt-6 border-t border-white/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <Calendar size={18} className="text-indigo-400" />
        <span className="text-xs font-bold text-slate-300 uppercase tracking-widest">Active Range:</span>
        <span className="text-[13px] font-black text-white bg-indigo-500/20 px-3 py-1.5 rounded-lg border border-indigo-500/30">
          {activeDateLabel}
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center p-1 bg-white/5 backdrop-blur-sm rounded-xl border border-white/10 custom-scrollbar overflow-x-auto">
          {presets.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => onSelectPreset(p.id)}
              className={`rounded-lg px-4 py-2 text-[11px] font-black uppercase tracking-wider transition-all cursor-pointer whitespace-nowrap ${
                datePreset === p.id
                  ? 'bg-white text-slate-900 shadow-sm'
                  : 'text-slate-400 hover:text-white hover:bg-white/5'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>

        {datePreset === 'custom' && (
          <div className="flex items-center gap-2 bg-white/5 backdrop-blur-sm p-1.5 rounded-xl border border-white/10">
            <input
              type="date"
              value={customStartDate}
              onChange={(e) => onCustomStartDateChange(e.target.value)}
              className="rounded-lg bg-white/10 text-white text-[11px] font-bold px-3 py-1.5 outline-none border border-transparent focus:border-indigo-400 transition-colors"
            />
            <span className="text-slate-400 text-xs font-black">-</span>
            <input
              type="date"
              value={customEndDate}
              onChange={(e) => onCustomEndDateChange(e.target.value)}
              className="rounded-lg bg-white/10 text-white text-[11px] font-bold px-3 py-1.5 outline-none border border-transparent focus:border-indigo-400 transition-colors"
            />
          </div>
        )}
      </div>
    </div>
  );
}
