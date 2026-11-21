import React, { useMemo } from 'react';
import {
  Layers,
  X,
  PieChart,
  BarChart3,
  Building,
  Briefcase,
  Users,
  Calendar,
  GraduationCap,
  Sparkles,
  Activity,
  Eye,
  EyeOff,
  TrendingUp,
  ShieldCheck
} from 'lucide-react';
import { normalizeAcademicDepartment } from '../../services/analysisEngine';

/**
 * AnalysisDrillDownModal Component
 * Strictly provides aggregate multidimensional analytical distributions.
 * 
 * Rules:
 * - Never displays exception case narratives
 * - Never displays effects, evidence, or attachment viewers
 * - Never routes into raw report dossiers
 * - Displays purely analytical segmentations (by Section, Discipline, Class, Term)
 */
export default function AnalysisDrillDownModal({
  isOpen,
  onClose,
  drillContext,
}) {
  const {
    type = 'category',
    title = 'Analytical Distribution',
    subtitle = 'Multidimensional Breakdown',
    exceptions = [],
    staffMap = new Map(),
    metricValue = null,
  } = drillContext || {};

  // Compute multidimensional breakdown across actual exception instances in this context
  const analytics = useMemo(() => {
    if (!drillContext) return null;
    const total = exceptions.length;
    const staffSet = new Set();
    const sectionCounts = new Map();
    const disciplineCounts = new Map();
    const classCounts = new Map();
    const termCounts = new Map();
    const statusCounts = { seen: 0, unseen: 0 };

    exceptions.forEach((e) => {
      // Staff key
      const sKey = e.staff_id || e.staff_code || e.staff_name;
      if (sKey) staffSet.add(String(sKey).toLowerCase().trim());

      // Section
      const sec = String(e.school_section || e.section || 'General Academic').trim();
      sectionCounts.set(sec, (sectionCounts.get(sec) || 0) + 1);

      // Discipline
      const disc = normalizeAcademicDepartment(e.discipline_group, e.subject_name || e.subject, e.staff_group || e.role);
      if (disc && disc !== 'null') {
        disciplineCounts.set(disc, (disciplineCounts.get(disc) || 0) + 1);
      }

      // Class
      const cls = String(e.class_name || e.class || 'Unassigned Class').trim();
      if (cls && cls !== 'null') {
        classCounts.set(cls, (classCounts.get(cls) || 0) + 1);
      }

      // Term
      const trm = String(e.term || 'Current Term').trim();
      termCounts.set(trm, (termCounts.get(trm) || 0) + 1);

      // Review Status
      const isSeen = Boolean(e.review_status === 'SEEN' || e.is_seen === true || e.seen === true || e.seen_at);
      if (isSeen) statusCounts.seen++;
      else statusCounts.unseen++;
    });

    const toSortedList = (map) => {
      return Array.from(map.entries())
        .map(([key, count]) => ({
          name: key,
          count,
          percentage: total > 0 ? Math.round((count / total) * 100) : 0,
        }))
        .sort((a, b) => b.count - a.count);
    };

    const uniqueStaffCount = staffSet.size;
    const perHeadVal = uniqueStaffCount > 0 ? (total / uniqueStaffCount).toFixed(2) : '0.00';

    return {
      total,
      uniqueStaffCount,
      perHead: perHeadVal,
      bySection: toSortedList(sectionCounts),
      byDiscipline: toSortedList(disciplineCounts),
      byClass: toSortedList(classCounts),
      byTerm: toSortedList(termCounts),
      statusCounts,
    };
  }, [drillContext, exceptions]);

  if (!isOpen || !drillContext || !analytics) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200"
      onClick={onClose}
    >
      <style>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }
        .custom-scrollbar:hover::-webkit-scrollbar-thumb { background: #94a3b8; }
        @keyframes slide-up-fade {
          0% { opacity: 0; transform: translateY(15px); }
          100% { opacity: 1; transform: translateY(0); }
        }
        .stagger-1 { animation: slide-up-fade 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards 0.1s; opacity: 0; }
        .stagger-2 { animation: slide-up-fade 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards 0.2s; opacity: 0; }
        .stagger-3 { animation: slide-up-fade 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards 0.3s; opacity: 0; }
        .stagger-4 { animation: slide-up-fade 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards 0.4s; opacity: 0; }
      `}</style>

      <div
        className="relative w-full max-w-5xl bg-[#f8fafc] dark:bg-slate-900 rounded-[28px] sm:rounded-[32px] shadow-2xl flex flex-col overflow-hidden border border-slate-200/50 dark:border-white/10 animate-in zoom-in-95 slide-in-from-bottom-4 duration-300 max-h-[92vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* =========================================================
            PREMIUM HEADER (Dark Intelligence Theme)
            ========================================================= */}
        <div className="relative overflow-hidden bg-gradient-to-br from-[#0f172a] via-[#1e1b4b] to-[#0f172a] p-6 sm:p-8 shrink-0">
          {/* Abstract Floating Orbs */}
          <div className="absolute top-0 right-0 -translate-y-12 translate-x-1/4 w-64 h-64 bg-indigo-500/20 rounded-full blur-[60px] pointer-events-none" />
          <div className="absolute bottom-0 left-0 translate-y-1/4 -translate-x-1/4 w-48 h-48 bg-fuchsia-500/10 rounded-full blur-[60px] pointer-events-none" />

          <div className="relative z-10 flex items-start justify-between gap-4">
            <div className="flex items-start sm:items-center gap-4 sm:gap-5 min-w-0">
              <div className="flex h-12 w-12 sm:h-14 sm:w-14 shrink-0 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-md border border-white/20 shadow-[0_8px_30px_rgba(0,0,0,0.12)]">
                <PieChart size={28} className="text-indigo-400" />
              </div>
              <div className="min-w-0">
                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-500/10 text-indigo-300 text-[9px] sm:text-[10px] font-black uppercase tracking-widest mb-1.5 border border-indigo-500/20">
                  <Layers size={12} /> Analytical Breakdown
                </div>
                <h3 className="text-xl sm:text-2xl md:text-3xl font-black text-white tracking-tight truncate">
                  {title}
                </h3>
                <p className="text-[11px] sm:text-xs text-slate-300 font-medium mt-1.5 line-clamp-1 sm:line-clamp-2 leading-relaxed">
                  {subtitle}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="h-10 w-10 shrink-0 rounded-full bg-white/10 border border-white/10 hover:bg-white/20 text-slate-300 hover:text-white flex items-center justify-center transition-all cursor-pointer shadow-sm backdrop-blur-md"
              aria-label="Close analytical breakdown"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* =========================================================
            KPI SUMMARY ROW
            ========================================================= */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 p-4 sm:p-6 bg-slate-50 dark:bg-slate-900/90 border-b border-slate-200 dark:border-white/10 shrink-0 z-10 shadow-sm relative">
          {/* KPI 1 */}
          <div className="p-3.5 sm:p-4 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm flex flex-col justify-between group hover:border-blue-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Total Volume</span>
              <Layers size={14} className="text-blue-500" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-slate-800 dark:text-white dark:text-white tracking-tight">{analytics.total}</div>
          </div>
          
          {/* KPI 2 */}
          <div className="p-3.5 sm:p-4 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm flex flex-col justify-between group hover:border-amber-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Personnel</span>
              <Users size={14} className="text-amber-500" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-slate-800 dark:text-white dark:text-white tracking-tight">{analytics.uniqueStaffCount}</div>
          </div>

          {/* KPI 3 */}
          <div className="p-3.5 sm:p-4 bg-gradient-to-br from-indigo-50 to-white dark:from-indigo-950/40 dark:to-slate-800 rounded-2xl border border-indigo-100 dark:border-indigo-500/20 shadow-sm flex flex-col justify-between group hover:border-indigo-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-black text-indigo-500 uppercase tracking-widest">Ratio / Head</span>
              <Activity size={14} className="text-indigo-600" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-indigo-700 dark:text-indigo-300 tracking-tight">{analytics.perHead}</div>
          </div>

          {/* KPI 4 */}
          <div className="p-3.5 sm:p-4 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm flex flex-col justify-between">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Audit State</span>
              <ShieldCheck size={14} className="text-emerald-500" />
            </div>
            <div className="flex flex-col gap-1 mt-1">
              <div className="flex items-center justify-between text-[11px] font-black text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded">
                <span className="flex items-center gap-1"><Eye size={12}/> Seen</span>
                <span>{analytics.statusCounts.seen}</span>
              </div>
              <div className="flex items-center justify-between text-[11px] font-black text-rose-700 bg-rose-50 px-2 py-0.5 rounded">
                <span className="flex items-center gap-1"><EyeOff size={12}/> Unseen</span>
                <span>{analytics.statusCounts.unseen}</span>
              </div>
            </div>
          </div>
        </div>

        {/* =========================================================
            ANALYTICAL SEGMENTATIONS BODY
            ========================================================= */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 md:p-8 space-y-6 sm:space-y-8 custom-scrollbar bg-slate-50/50 dark:bg-slate-950/60">
          
          {/* Section Distribution */}
          <div className="stagger-1 bg-white dark:bg-slate-900 p-5 sm:p-6 rounded-[24px] border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 dark:border-white/5 mb-5">
              <div className="flex items-center gap-2.5">
                <div className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
                  <Building size={16} />
                </div>
                <h4 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white dark:text-white uppercase tracking-widest">
                  Distribution by School Section
                </h4>
              </div>
              <span className="text-[10px] font-bold text-slate-500 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-2.5 py-1 rounded-md">
                {analytics.bySection.length} Sections
              </span>
            </div>

            <div className="space-y-4">
              {analytics.bySection.map((item) => (
                <div key={item.name} className="space-y-1.5 group">
                  <div className="flex items-center justify-between text-[11px] sm:text-xs">
                    <span className="font-bold text-slate-800 dark:text-slate-200">{item.name}</span>
                    <div className="flex items-center gap-2">
                      <span className="font-black text-slate-800 dark:text-white">{item.count}</span>
                      <span className="text-slate-400 font-mono font-semibold w-9 text-right">{item.percentage}%</span>
                    </div>
                  </div>
                  {/* Glossy Progress Bar */}
                  <div className="h-2.5 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden shadow-inner flex">
                    <div
                      className="h-full bg-indigo-500 shadow-[inset_0_1px_rgba(255,255,255,0.3)] transition-all duration-1000 group-hover:bg-indigo-400"
                      style={{ width: `${item.percentage}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Discipline / Department Distribution */}
          {analytics.byDiscipline.length > 0 && (
            <div className="stagger-2 bg-white dark:bg-slate-900 p-5 sm:p-6 rounded-[24px] border border-slate-200 dark:border-slate-800 shadow-sm">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100 dark:border-white/5 mb-5">
                <div className="flex items-center gap-2.5">
                  <div className="p-1.5 rounded-lg bg-blue-50 text-blue-600">
                    <Briefcase size={16} />
                  </div>
                  <h4 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white dark:text-white uppercase tracking-widest">
                    Subject / Discipline Metrics
                  </h4>
                </div>
                <span className="text-[10px] font-bold text-slate-500 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-2.5 py-1 rounded-md">
                  {analytics.byDiscipline.length} Disciplines
                </span>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                {analytics.byDiscipline.map((item) => (
                  <div key={item.name} className="p-3.5 bg-slate-50/80 dark:bg-slate-800/80 hover:bg-slate-100/80 dark:hover:bg-slate-700/80 transition-colors rounded-xl border border-slate-200 dark:border-slate-700 flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-700 dark:text-slate-200 truncate pr-3">{item.name}</span>
                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className="font-black text-slate-800 dark:text-white">{item.count}</span>
                      <span className="font-mono text-[10px] text-blue-600 bg-blue-50 border border-blue-100 px-1.5 py-0.5 rounded font-bold">
                        {item.percentage}%
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Class-Level Distribution (Top Classes) */}
          {analytics.byClass.length > 0 && (
            <div className="stagger-3 bg-white dark:bg-slate-900 p-5 sm:p-6 rounded-[24px] border border-slate-200 dark:border-slate-800 shadow-sm">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100 dark:border-white/5 mb-5">
                <div className="flex items-center gap-2.5">
                  <div className="p-1.5 rounded-lg bg-emerald-50 text-emerald-600">
                    <GraduationCap size={16} />
                  </div>
                  <h4 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white dark:text-white uppercase tracking-widest">
                    Class-Level Representation
                  </h4>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-100">
                    Top 12 Results Shown
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
                {analytics.byClass.slice(0, 12).map((item) => (
                  <div key={item.name} className="p-3 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 hover:border-emerald-300 hover:shadow-sm transition-all flex flex-col justify-between gap-2">
                    <span className="font-bold text-[11px] sm:text-xs text-slate-600 dark:text-slate-300 truncate">{item.name}</span>
                    <div className="flex items-end justify-between">
                      <span className="text-xl font-black text-slate-800 dark:text-white leading-none">{item.count}</span>
                      <span className="font-mono text-[10px] font-bold text-emerald-600">{item.percentage}%</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Academic Term Distribution */}
          {analytics.byTerm.length > 0 && (
            <div className="stagger-4 bg-white dark:bg-slate-900 p-5 sm:p-6 rounded-[24px] border border-slate-200 dark:border-slate-800 shadow-sm">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100 dark:border-white/5 mb-5">
                <div className="flex items-center gap-2.5">
                  <div className="p-1.5 rounded-lg bg-amber-50 text-amber-600">
                    <Calendar size={16} />
                  </div>
                  <h4 className="text-xs sm:text-sm font-black text-slate-800 dark:text-white dark:text-white uppercase tracking-widest">
                    Term Horizon
                  </h4>
                </div>
              </div>

              <div className="flex flex-wrap gap-3">
                {analytics.byTerm.map((item) => (
                  <div key={item.name} className="px-4 py-2.5 bg-amber-50/50 hover:bg-amber-50 transition-colors rounded-xl border border-amber-200/80 flex items-center gap-3 text-xs shadow-sm">
                    <span className="font-bold text-amber-900">{item.name}</span>
                    <div className="w-px h-3 bg-amber-200" />
                    <span className="font-black text-amber-700">{item.count} <span className="font-semibold text-amber-600/70 ml-1 font-mono">({item.percentage}%)</span></span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* =========================================================
            FOOTER
            ========================================================= */}
        <div className="p-4 sm:p-5 bg-slate-50 dark:bg-slate-900 border-t border-slate-200 dark:border-white/5 flex flex-col sm:flex-row items-center justify-between gap-4 shrink-0">
          <div className="flex items-center gap-2 text-[10px] sm:text-[11px] font-bold text-slate-500 uppercase tracking-widest text-center sm:text-left">
            <Sparkles size={14} className="text-blue-400 hidden sm:block" />
            <span>Aggregate compliance distribution. No raw case dossiers exposed.</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-full sm:w-auto px-8 py-3 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-black text-xs hover:bg-slate-800 dark:hover:bg-slate-100 transition-all cursor-pointer shadow-md hover:shadow-lg active:scale-95"
          >
            Acknowledge & Close
          </button>
        </div>
      </div>
    </div>
  );
}