import React from 'react';
import { Activity, RotateCcw } from 'lucide-react';

export default function AnalysisHeader({
  onSyncDatabase,
  refreshing = false,
  loading = false,
  children,
}) {
  return (
    <section className="relative overflow-hidden rounded-[24px] bg-gradient-to-br from-[#0f172a] via-[#1e1b4b] to-[#0f172a] p-8 sm:p-10 text-white shadow-2xl">
      {/* Abstract Floating Orbs */}
      <div className="absolute top-0 right-0 -translate-y-12 translate-x-1/4 w-96 h-96 bg-indigo-500/20 rounded-full blur-[80px] animate-float pointer-events-none" />
      <div
        className="absolute bottom-0 left-0 translate-y-1/4 -translate-x-1/4 w-80 h-80 bg-fuchsia-500/10 rounded-full blur-[80px] animate-float pointer-events-none"
        style={{ animationDelay: '2s' }}
      />

      <div className="relative z-10 flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div className="flex items-center gap-5">
          <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-md border border-white/20 shadow-[0_8px_30px_rgba(0,0,0,0.12)]">
            <Activity size={32} className="text-indigo-400" />
          </div>
          <div>
            <h1 className="text-3xl font-black tracking-tight sm:text-4xl text-white drop-shadow-sm">
              Analysis
            </h1>
            <p className="mt-2 text-sm text-slate-300 max-w-xl leading-relaxed font-medium">
              Authoritative personnel metrics derived strictly from the database and active timeframes.
            </p>
          </div>
        </div>

        <div className="flex items-center self-start lg:self-center">
          <button
            type="button"
            onClick={onSyncDatabase}
            disabled={refreshing || loading}
            className="inline-flex items-center gap-2 rounded-xl bg-white/5 backdrop-blur-xl border border-white/10 hover:bg-white/10 hover:border-white/20 px-5 py-3 text-xs font-bold text-white shadow-xl transition-all cursor-pointer active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            <RotateCcw size={16} className={refreshing ? 'animate-spin text-indigo-400' : 'text-indigo-400'} />
            <span>{refreshing ? 'Syncing...' : 'Sync Database'}</span>
          </button>
        </div>
      </div>

      {children}
    </section>
  );
}
