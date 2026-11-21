import React from 'react';
import { Presentation, X, Sparkles, RotateCcw, Download } from 'lucide-react';

export default function SlideConfigModal({
  isOpen,
  onClose,
  reportConfig,
  onChangeReportConfig,
  onDownload,
  isGenerating = false,
  isValid = true,
}) {
  if (!isOpen) return null;

  const fields = [
    {
      key: 'slide2Heading',
      label: 'Slide 2: Comparative Analysis',
      value: reportConfig?.slide2Heading || '',
    },
    {
      key: 'slide3Heading',
      label: 'Slide 3: Primary Discipline',
      value: reportConfig?.slide3Heading || '',
    },
    {
      key: 'slide4Heading',
      label: 'Slide 4: Primary Class Analysis',
      value: reportConfig?.slide4Heading || '',
    },
    {
      key: 'slide5Heading',
      label: 'Slide 5: Secondary Discipline',
      value: reportConfig?.slide5Heading || '',
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-2xl bg-white dark:bg-slate-900 rounded-[24px] shadow-2xl border border-slate-100 dark:border-white/10 overflow-hidden flex flex-col max-h-[90vh] animate-in zoom-in-95 slide-in-from-bottom-4 duration-300">
        
        {/* Modal Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-100 dark:border-white/5 bg-slate-50/80 dark:bg-slate-800/80">
          <div className="flex items-center gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-100 text-indigo-600 shadow-sm border border-indigo-200">
              <Presentation size={24} />
            </div>
            <div>
              <h3 className="text-lg font-black text-slate-900 dark:text-white tracking-tight">
                Presentation Configurator
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">
                Define custom slide headings for the official PowerPoint deck.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-full text-slate-400 hover:text-slate-700 hover:bg-white dark:hover:bg-slate-800 shadow-sm transition-all cursor-pointer border border-transparent hover:border-slate-200 dark:hover:border-slate-700"
            aria-label="Close presentation configurator"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 overflow-y-auto custom-scrollbar bg-white dark:bg-slate-900">
          <div className="p-4 rounded-xl bg-blue-50/50 border border-blue-100 flex items-start gap-3">
            <Sparkles size={16} className="text-blue-500 shrink-0 mt-0.5" />
            <p className="text-xs text-blue-800 leading-relaxed font-medium">
              The inputs below map directly to the master slide titles in your presentation. The text you enter will be rendered exactly as typed.
            </p>
          </div>

          {fields.map((field) => (
            <div key={field.key} className="space-y-2 group">
              <div className="flex items-center justify-between">
                <label className="text-xs font-black text-slate-700 tracking-wide">
                  {field.label} <span className="text-rose-500">*</span>
                </label>
              </div>
              <textarea
                rows={2}
                value={field.value}
                onChange={(e) => onChangeReportConfig(field.key, e.target.value)}
                className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-[13px] font-semibold text-slate-800 dark:text-white outline-none focus:bg-white dark:focus:bg-slate-900 focus:ring-4 focus:ring-indigo-500/10 focus:border-indigo-500 transition-all resize-none shadow-sm group-hover:border-slate-300 custom-scrollbar"
              />
            </div>
          ))}
        </div>

        {/* Modal Footer */}
        <div className="flex items-center justify-end gap-3 p-5 border-t border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-850">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-bold text-xs hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-800 dark:hover:text-white transition-all cursor-pointer shadow-sm"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onDownload}
            disabled={isGenerating || !isValid}
            className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl font-black text-xs bg-indigo-600 hover:bg-indigo-700 text-white shadow-md shadow-indigo-500/20 hover:shadow-lg hover:-translate-y-0.5 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
          >
            {isGenerating ? (
              <>
                <RotateCcw size={14} className="animate-spin" />
                <span>Processing Deck...</span>
              </>
            ) : (
              <>
                <Download size={14} />
                <span>Download Report</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
