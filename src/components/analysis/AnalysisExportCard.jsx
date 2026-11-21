import React, { useState } from 'react';
import {
  Presentation,
  ChevronDown,
  Check,
  CalendarDays,
  RotateCcw,
  ShieldCheck,
  ArrowDown,
  Sparkles,
} from 'lucide-react';

export default function DownloadSlideCard({
  allExceptions = [],
  term = "Third Term",
  session = "2026/2027",
  reportingPeriod = "All Time",
  isGenerating = false,
  downloadError = null,
  downloadSuccess = false,
  onGenerateSlide, // (scope, format) => void
  onOpenConfigModal, // () => void
}) {
  const [isSlideOpen, setIsSlideOpen] = useState(false);

  const handleActionClick = () => {
    if (onOpenConfigModal) {
      onOpenConfigModal();
    } else if (onGenerateSlide) {
      onGenerateSlide('All', 'PPTX');
    }
  };

  return (
    <section className="relative rounded-[24px] bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-white/10 shadow-sm hover:shadow-md transition-shadow duration-300 overflow-hidden">
      
      {/* 1. REVIEW ANALYSIS -> DOWNLOAD PRESENTATION STEP WORKFLOW BANNER */}
      <div className="bg-slate-50/80 dark:bg-slate-850 px-6 py-2.5 border-b border-slate-100 dark:border-white/5 flex items-center justify-between text-[11px] font-bold text-slate-500">
        <div className="flex items-center gap-2">
          <span className="text-slate-400">Step 1:</span>
          <span className="text-slate-700 uppercase tracking-wider">Review Analysis on Page</span>
          <ArrowDown size={13} className="text-indigo-500 rotate-[-90deg] mx-1" />
          <span className="text-slate-400">Step 2:</span>
          <span className="text-indigo-700 uppercase tracking-wider font-extrabold">Download Presentation (PPTX)</span>
        </div>
        <span className="hidden md:inline-flex items-center gap-1.5 text-slate-400 font-medium">
          <Sparkles size={13} className="text-amber-500" />
          Direct Export of Active Analytical State
        </span>
      </div>

      {/* 2. SLIDE EXPORT TRIGGER BAR */}
      <div
        role="button"
        tabIndex={0}
        onClick={() => setIsSlideOpen((prev) => !prev)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setIsSlideOpen((prev) => !prev);
          }
        }}
        className="w-full flex items-center justify-between p-5 sm:px-6 cursor-pointer bg-white dark:bg-slate-900 hover:bg-slate-50/70 dark:hover:bg-slate-800/70 transition-colors select-none"
      >
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-50 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20 shadow-2xs">
            <Presentation size={19} strokeWidth={2.5} />
          </div>
          <div>
            <div className="flex items-center gap-2 mb-0.5">
              <h2 className="text-base font-extrabold text-slate-900 dark:text-white tracking-tight">
                PowerPoint Presentation Export
              </h2>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 font-medium hidden sm:block">
              Export high-fidelity executive presentation deck synchronized with the active analytical scope.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <div
            className={`flex items-center gap-2 px-4 py-2 text-xs font-black rounded-xl border transition-all duration-300 shadow-2xs ${
              isSlideOpen
                ? 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-200 dark:hover:bg-slate-700'
                : 'bg-indigo-900 text-white border-indigo-800 hover:bg-indigo-950 hover:shadow-xs'
            }`}
          >
            <span>{isSlideOpen ? 'Hide Export Console' : 'Configure & Download'}</span>
            <ChevronDown
              size={15}
              strokeWidth={3}
              className={`transition-transform duration-300 ${isSlideOpen ? 'rotate-180 text-slate-400' : 'text-indigo-300'}`}
            />
          </div>
        </div>
      </div>

      {/* 3. COLLAPSIBLE CONSOLE */}
      {isSlideOpen && (
        <div className="p-5 sm:p-6 border-t border-slate-100 dark:border-white/5 bg-white dark:bg-slate-900 animate-in fade-in slide-in-from-top-2 duration-200">
          
          {/* SLIDE GENERATION PARAMETERS CONSOLE */}
          <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4 rounded-2xl border border-slate-200/80 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-800/50 p-5">
            <div className="space-y-2 flex-1">
              <div className="flex items-center gap-2 text-[11px] font-black uppercase tracking-widest text-slate-600">
                <CalendarDays size={14} className="text-indigo-600" />
                <span>Synchronized Export Parameters</span>
              </div>
              
              <div className="flex flex-wrap items-center gap-2.5 text-xs">
                <div className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-3 py-1.5 rounded-xl shadow-2xs">
                  <span className="font-bold text-slate-400 mr-1.5">Term & Session:</span>
                  <span className="font-black text-indigo-700">{term} ({session})</span>
                </div>
                <div className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-3 py-1.5 rounded-xl shadow-2xs">
                  <span className="font-bold text-slate-400 mr-1.5">Audit Scope:</span>
                  <span className="font-black text-slate-800 dark:text-white">{allExceptions.length} records</span>
                </div>
                <div className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-3 py-1.5 rounded-xl shadow-2xs">
                  <span className="font-bold text-slate-400 mr-1.5">Period:</span>
                  <span className="font-mono font-bold text-slate-700 dark:text-slate-300">{reportingPeriod}</span>
                </div>
              </div>
            </div>

            {/* Main Action Button */}
            <div className="shrink-0 flex flex-col items-stretch md:items-end gap-2 min-w-[220px]">
              <button
                type="button"
                disabled={isGenerating}
                onClick={handleActionClick}
                className="w-full flex flex-col items-center justify-center gap-0.5 rounded-xl bg-indigo-700 hover:bg-indigo-800 active:scale-[0.98] px-5 py-3 text-white shadow-xs transition-all cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed group/btn"
              >
                {isGenerating ? (
                  <div className="flex items-center gap-2 text-xs font-black">
                    <RotateCcw size={15} className="animate-spin" />
                    <span>Validating & Building PPTX...</span>
                  </div>
                ) : (
                  <>
                    <div className="flex items-center gap-2 text-xs font-black">
                      <Presentation size={16} />
                      <span>Download Presentation (.pptx)</span>
                    </div>
                    <span className="text-[10px] font-medium text-indigo-200">
                      Configure custom slide headings
                    </span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Feedback Notices */}
          {downloadSuccess && (
            <div className="mt-3 flex items-center gap-2 p-3 rounded-xl text-xs font-bold leading-relaxed bg-emerald-50 dark:bg-emerald-500/10 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-500/20 animate-in fade-in slide-in-from-bottom-2 shadow-2xs">
              <Check size={16} className="text-emerald-600 shrink-0" />
              <span>PowerPoint presentation downloaded successfully. Analytical totals validated against active dashboard state.</span>
            </div>
          )}
          {downloadError && (
            <div className="mt-3 flex items-center gap-2 p-3 rounded-xl text-xs font-bold leading-relaxed bg-rose-50 dark:bg-rose-500/10 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-500/20 animate-in fade-in slide-in-from-bottom-2 shadow-2xs">
              <RotateCcw size={16} className="text-rose-600 shrink-0" />
              <span>{downloadError}</span>
            </div>
          )}

        </div>
      )}
    </section>
  );
}

