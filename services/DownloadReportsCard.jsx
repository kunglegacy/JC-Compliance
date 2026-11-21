import React, { useState, useMemo, useEffect } from 'react';
import {
  Download, ChevronDown, Check, CalendarDays, RotateCcw, Briefcase,
  ShieldCheck, GraduationCap, BookOpen, Users, Layers, FileText,
  FileCode2, TableProperties, Scale, Wrench, Shield, Bus, Baby,
  UtensilsCrossed, Compass, Loader2
} from 'lucide-react';
import { getRecordScope, buildStaffLookupMap } from '../../services/reportExportData';

export default function DownloadReportsCard({
  rawExceptions = [],
  staffRoster = [],
  startDate = '',
  endDate = '',
  onGenerateReport,
}) {
  const [isDownloadOpen, setIsDownloadOpen] = useState(false);
  const [selectedDownloadScope, setSelectedDownloadScope] = useState('All');
  const [selectedDownloadFormat, setSelectedDownloadFormat] = useState('PDF');
  const [isGenerating, setIsGenerating] = useState(false);
  const [downloadFeedback, setDownloadFeedback] = useState(null);

  const [timeRangePreset, setTimeRangePreset] = useState('ALL');
  const [customStartDate, setCustomStartDate] = useState(startDate || '');
  const [customEndDate, setCustomEndDate] = useState(endDate || '');

  const activeDateBounds = useMemo(() => {
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];

    if (timeRangePreset === 'THIS_WEEK') {
      const day = today.getDay();
      const diffToMonday = (day + 6) % 7;
      const monday = new Date(today);
      monday.setDate(today.getDate() - diffToMonday);
      const mondayStr = monday.toISOString().split('T')[0];
      return { start: mondayStr, end: todayStr, label: 'This Week' };
    }
    if (timeRangePreset === 'LAST_WEEK') {
      const day = today.getDay();
      const diffToMonday = (day + 6) % 7;
      const lastMonday = new Date(today);
      lastMonday.setDate(today.getDate() - diffToMonday - 7);
      const lastSunday = new Date(lastMonday);
      lastSunday.setDate(lastMonday.getDate() + 6);
      return { start: lastMonday.toISOString().split('T')[0], end: lastSunday.toISOString().split('T')[0], label: 'Last Week' };
    }
    if (timeRangePreset === 'LAST_MONTH') {
      const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastDay = new Date(today.getFullYear(), today.getMonth(), 0);
      return { start: firstDay.toISOString().split('T')[0], end: lastDay.toISOString().split('T')[0], label: 'Last Month' };
    }
    if (timeRangePreset === 'CUSTOM') {
      return {
        start: customStartDate, end: customEndDate,
        label: customStartDate && customEndDate ? `${customStartDate} to ${customEndDate}` : 'Custom Range'
      };
    }
    return { start: startDate || '', end: endDate || '', label: 'All Time' };
  }, [timeRangePreset, customStartDate, customEndDate, startDate, endDate]);

  const filteredExceptions = useMemo(() => {
    if (!activeDateBounds.start && !activeDateBounds.end) return rawExceptions;
    return rawExceptions.filter((e) => {
      const d = e.date || e.incident_date || (e.created_at ? e.created_at.split('T')[0] : '');
      if (!d) return true;
      if (activeDateBounds.start && d < activeDateBounds.start) return false;
      if (activeDateBounds.end && d > activeDateBounds.end) return false;
      return true;
    });
  }, [rawExceptions, activeDateBounds]);

  const scopeCounts = useMemo(() => {
    const counts = {
      'Junior Secondary': 0, 'Senior Secondary': 0, 'Junior Primary': 0, 'Senior Primary': 0,
      Nursery: 0, Mentorship: 0, Discipline: 0, Maintenance: 0, Security: 0,
      Transport: 0, Minders: 0, 'Hostel/Kitchen': 0, CDS: 0, All: filteredExceptions.length,
    };
    const staffMap = buildStaffLookupMap(staffRoster);
    filteredExceptions.forEach((e) => {
      const scope = getRecordScope(e, staffMap);
      if (counts[scope] !== undefined) counts[scope] += 1;
    });
    return counts;
  }, [filteredExceptions, staffRoster]);

  const downloadScopes = useMemo(() => [
    { id: 'Junior Secondary', title: 'Junior Secondary', icon: GraduationCap, count: scopeCounts['Junior Secondary'] || 0 },
    { id: 'Senior Secondary', title: 'Senior Secondary', icon: GraduationCap, count: scopeCounts['Senior Secondary'] || 0 },
    { id: 'Junior Primary', title: 'Junior Primary', icon: BookOpen, count: scopeCounts['Junior Primary'] || 0 },
    { id: 'Senior Primary', title: 'Senior Primary', icon: BookOpen, count: scopeCounts['Senior Primary'] || 0 },
    { id: 'Nursery', title: 'Nursery', icon: Baby, count: scopeCounts['Nursery'] || 0 },
    { id: 'Mentorship', title: 'Mentorship', icon: Users, count: scopeCounts['Mentorship'] || 0 },
    { id: 'Discipline', title: 'Discipline', icon: Scale, count: scopeCounts['Discipline'] || 0 },
    { id: 'Maintenance', title: 'Maintenance', icon: Wrench, count: scopeCounts['Maintenance'] || 0 },
    { id: 'Security', title: 'Security', icon: Shield, count: scopeCounts['Security'] || 0 },
    { id: 'Transport', title: 'Transport', icon: Bus, count: scopeCounts['Transport'] || 0 },
    { id: 'Minders', title: 'Minders', icon: Compass, count: scopeCounts['Minders'] || 0 },
    { id: 'Hostel/Kitchen', title: 'Hostel/Kitchen', icon: UtensilsCrossed, count: scopeCounts['Hostel/Kitchen'] || 0 },
    { id: 'CDS', title: 'CDS', icon: ShieldCheck, count: scopeCounts['CDS'] || 0 },
    { id: 'All', title: 'All', icon: Layers, count: scopeCounts['All'] || 0 },
  ], [scopeCounts]);

  const visibleDownloadScopes = useMemo(() => {
    const available = downloadScopes.filter((s) => (s.count || 0) > 0);
    return available.length === 0 ? [{ id: 'All', title: 'All', icon: Layers, count: 0 }] : available;
  }, [downloadScopes]);

  useEffect(() => {
    if (visibleDownloadScopes.length > 0 && !visibleDownloadScopes.some((s) => s.id === selectedDownloadScope)) {
      setSelectedDownloadScope(visibleDownloadScopes[0].id);
    }
  }, [visibleDownloadScopes, selectedDownloadScope]);

  const handleDownloadClick = async () => {
    setIsGenerating(true);
    setDownloadFeedback(null);
    try {
      if (onGenerateReport) {
        const result = await onGenerateReport(selectedDownloadScope, selectedDownloadFormat, activeDateBounds.start, activeDateBounds.end);
        if (result && result.message) setDownloadFeedback({ type: 'success', message: result.message });
      }
    } catch (err) {
      setDownloadFeedback({ type: 'error', message: err?.message || 'Report generation failed.' });
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <section className="relative rounded-[2rem] bg-white dark:bg-slate-900 border border-slate-200/80 dark:border-white/10 shadow-xl shadow-slate-200/60 dark:shadow-[0_8px_30px_rgb(0,0,0,0.2)] transition-all duration-300 overflow-hidden group">
      
      {/* 1. PREMIUM TRIGGER BAR */}
      <div
        role="button"
        tabIndex={0}
        onClick={() => setIsDownloadOpen((prev) => !prev)}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setIsDownloadOpen((prev) => !prev); } }}
        className="w-full flex items-center justify-between p-5 sm:px-8 sm:py-6 cursor-pointer bg-gradient-to-r from-slate-50/50 to-white dark:from-slate-800/50 dark:to-slate-900 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors select-none"
      >
        <div className="flex items-center gap-4 sm:gap-5">
          <div className="flex h-12 w-12 sm:h-14 sm:w-14 shrink-0 items-center justify-center rounded-2xl bg-blue-50 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 shadow-sm transition-transform duration-300 group-hover:scale-105 group-hover:bg-blue-600 group-hover:text-white dark:group-hover:bg-blue-500">
            <Download size={24} strokeWidth={2.5} />
          </div>
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h2 className="text-lg sm:text-xl font-black text-slate-900 dark:text-white tracking-tight">
                Export Reports
              </h2>
              <span className="hidden sm:inline-flex items-center gap-1 text-[9px] font-black text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-500/20 border border-indigo-200/60 dark:border-indigo-500/30 px-2 py-0.5 rounded-md uppercase tracking-widest shadow-sm">
                <ShieldCheck size={10} /> Official
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">
              Statutory exception reports in PDF or DOCX.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-4 shrink-0">
          <div className="hidden lg:flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-100/80 dark:bg-slate-800/80 border border-slate-200/60 dark:border-white/5 text-xs font-bold shadow-inner">
            <span className="text-slate-700 dark:text-slate-300">{selectedDownloadScope}</span>
            <span className="text-slate-300 dark:text-slate-600">•</span>
            <span className="text-blue-600 dark:text-blue-400">{selectedDownloadFormat}</span>
          </div>
          <div className={`flex items-center justify-center h-10 w-10 sm:h-12 sm:w-12 rounded-2xl border transition-all duration-300 shadow-sm ${
            isDownloadOpen
              ? 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-700'
              : 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 border-slate-800 dark:border-white hover:scale-105'
          }`}>
            <ChevronDown size={20} strokeWidth={3} className={`transition-transform duration-500 ${isDownloadOpen ? 'rotate-180' : ''}`} />
          </div>
        </div>
      </div>

      {/* 2. COLLAPSIBLE PANEL CONTENT */}
      {isDownloadOpen && (
        <div className="p-5 sm:p-8 border-t border-slate-100 dark:border-white/10 bg-white dark:bg-slate-900 animate-in fade-in slide-in-from-top-4 duration-300">
          
          {/* Top Toolbar: Format & Time Range */}
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 mb-8">
            
            {/* Format Selector */}
            <div className="flex items-center p-1.5 bg-slate-100 dark:bg-slate-800/80 rounded-2xl border border-slate-200/80 dark:border-white/5 shadow-inner shrink-0 w-full sm:w-auto">
              <button
                type="button"
                onClick={() => setSelectedDownloadFormat('PDF')}
                className={`flex-1 sm:flex-none flex items-center justify-center gap-2 px-6 py-2.5 text-xs font-black rounded-xl transition-all duration-200 ${
                  selectedDownloadFormat === 'PDF'
                    ? 'bg-white dark:bg-slate-700 text-rose-600 dark:text-rose-400 shadow-sm border border-slate-200/60 dark:border-white/10'
                    : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
                }`}
              >
                <FileText size={16} /> PDF
              </button>
              <button
                type="button"
                onClick={() => setSelectedDownloadFormat('DOCX')}
                className={`flex-1 sm:flex-none flex items-center justify-center gap-2 px-6 py-2.5 text-xs font-black rounded-xl transition-all duration-200 ${
                  selectedDownloadFormat === 'DOCX'
                    ? 'bg-white dark:bg-slate-700 text-blue-600 dark:text-blue-400 shadow-sm border border-slate-200/60 dark:border-white/10'
                    : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white'
                }`}
              >
                <FileCode2 size={16} /> DOCX
              </button>
            </div>

            {/* Time Range Presets */}
            <div className="flex flex-wrap items-center gap-2">
              {[
                { id: 'ALL', label: 'All Time' },
                { id: 'THIS_WEEK', label: 'This Week' },
                { id: 'LAST_WEEK', label: 'Last Week' },
                { id: 'LAST_MONTH', label: 'Last Month' },
                { id: 'CUSTOM', label: 'Custom' },
              ].map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTimeRangePreset(item.id)}
                  className={`px-4 py-2 text-xs font-bold rounded-xl transition-all ${
                    timeRangePreset === item.id
                      ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-900 shadow-md'
                      : 'bg-slate-50 dark:bg-slate-800/50 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          {/* Custom Date Inputs */}
          {timeRangePreset === 'CUSTOM' && (
            <div className="flex flex-col sm:flex-row items-center gap-4 p-4 bg-blue-50/50 dark:bg-blue-500/5 border border-blue-200/80 dark:border-blue-500/20 rounded-2xl mb-8 animate-in fade-in duration-200">
              <span className="text-xs font-black text-blue-900 dark:text-blue-400 shrink-0 uppercase tracking-widest">Custom Range</span>
              <div className="flex items-center gap-3 w-full sm:w-auto">
                <input
                  type="date"
                  value={customStartDate}
                  onChange={(e) => setCustomStartDate(e.target.value)}
                  className="w-full sm:w-auto px-4 py-2.5 text-xs font-bold rounded-xl border-none bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-sm focus:ring-2 focus:ring-blue-500 outline-none cursor-pointer"
                />
                <span className="text-slate-400 font-bold">to</span>
                <input
                  type="date"
                  value={customEndDate}
                  onChange={(e) => setCustomEndDate(e.target.value)}
                  className="w-full sm:w-auto px-4 py-2.5 text-xs font-bold rounded-xl border-none bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-sm focus:ring-2 focus:ring-blue-500 outline-none cursor-pointer"
                />
              </div>
            </div>
          )}

          {/* 3. REPORT SCOPE CARDS */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-7 gap-4 mb-8">
            {visibleDownloadScopes.map((scope) => {
              const isSelected = selectedDownloadScope === scope.id;
              const Icon = scope.icon;

              return (
                <button
                  key={scope.id}
                  type="button"
                  onClick={() => setSelectedDownloadScope(scope.id)}
                  className={`group relative flex flex-col text-left p-5 rounded-2xl border transition-all duration-300 overflow-hidden ${
                    isSelected
                      ? 'border-blue-500 dark:border-blue-400 bg-blue-50/80 dark:bg-blue-500/10 shadow-md ring-4 ring-blue-500/10 dark:ring-blue-500/20'
                      : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800/50 hover:border-blue-300 dark:hover:border-blue-500/50 hover:shadow-sm'
                  }`}
                >
                  {isSelected && (
                    <div className="absolute -top-6 -right-6 w-20 h-20 bg-blue-400/20 dark:bg-blue-500/20 rounded-full blur-2xl pointer-events-none" />
                  )}

                  <div className="flex items-start justify-between mb-4 relative z-10">
                    <div className={`flex h-10 w-10 items-center justify-center rounded-xl transition-colors duration-300 shadow-sm ${
                      isSelected ? 'bg-blue-600 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 group-hover:bg-blue-100 dark:group-hover:bg-blue-500/20 group-hover:text-blue-600 dark:group-hover:text-blue-400'
                    }`}>
                      <Icon size={18} strokeWidth={2.5} />
                    </div>
                    <div className={`h-5 w-5 rounded-full border-2 flex items-center justify-center transition-colors duration-300 ${
                      isSelected ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-300 dark:border-slate-600 bg-transparent group-hover:border-blue-400'
                    }`}>
                      {isSelected && <Check size={12} strokeWidth={3} />}
                    </div>
                  </div>

                  <div className="relative z-10">
                    <h3 className={`text-sm font-black tracking-tight truncate mb-1.5 transition-colors ${isSelected ? 'text-blue-900 dark:text-blue-100' : 'text-slate-700 dark:text-slate-300 group-hover:text-blue-800 dark:group-hover:text-blue-300'}`}>
                      {scope.title}
                    </h3>
                    <span className={`inline-flex text-[10px] font-black px-2 py-0.5 rounded-md uppercase tracking-wider ${
                      isSelected ? 'bg-blue-200/50 dark:bg-blue-500/30 text-blue-800 dark:text-blue-300' : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'
                    }`}>
                      {scope.count} logs
                    </span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* 4. ACTION CONSOLE */}
          <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-6 p-6 rounded-2xl bg-slate-50 dark:bg-slate-800/30 border border-slate-100 dark:border-white/5">
            
            <div className="flex-1">
              <div className="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500 mb-3">
                <TableProperties size={14} /> Export Parameters
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="bg-white dark:bg-slate-800 px-4 py-2 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm text-xs">
                  <span className="font-bold text-slate-400 mr-2">Format:</span>
                  <span className="font-black text-blue-600 dark:text-blue-400">{selectedDownloadFormat}</span>
                </div>
                <div className="bg-white dark:bg-slate-800 px-4 py-2 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm text-xs">
                  <span className="font-bold text-slate-400 mr-2">Period:</span>
                  <span className="font-black text-slate-700 dark:text-slate-200">{activeDateBounds.label}</span>
                </div>
              </div>
            </div>

            <button
              type="button"
              disabled={isGenerating}
              onClick={handleDownloadClick}
              className="shrink-0 flex items-center justify-center gap-2.5 rounded-2xl bg-blue-600 hover:bg-blue-700 active:scale-[0.98] px-8 py-4 text-white shadow-lg shadow-blue-600/25 transition-all cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {isGenerating ? (
                <>
                  <Loader2 size={18} className="animate-spin" strokeWidth={3} />
                  <span className="text-sm font-black">Processing...</span>
                </>
              ) : (
                <>
                  <Download size={18} strokeWidth={3} />
                  <span className="text-sm font-black">Generate {selectedDownloadFormat}</span>
                </>
              )}
            </button>
          </div>

          {/* Feedback Message */}
          {downloadFeedback && (
            <div className={`mt-4 flex items-center gap-3 p-4 rounded-2xl text-sm font-bold shadow-sm animate-in fade-in slide-in-from-bottom-2 ${
              downloadFeedback.type === 'error' ? 'bg-rose-50 dark:bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-500/20' : 
              'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-500/20'
            }`}>
              {downloadFeedback.type === 'error' ? <RotateCcw size={18} strokeWidth={2.5}/> : <Check size={18} strokeWidth={2.5}/>}
              <span>{downloadFeedback.message}</span>
            </div>
          )}
        </div>
      )}
    </section>
  );
}