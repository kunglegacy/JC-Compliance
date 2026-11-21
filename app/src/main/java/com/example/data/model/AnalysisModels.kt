package com.example.data.model

data class TermAnalysisSnapshot(
    val id: String,
    val session: String,
    val term: String,
    val snapshotVersion: Int = 1,
    val officialTermEndDate: String? = null,
    val status: String = "DRAFT", // DRAFT | VERIFIED | SUPERSEDED
    val reconciliationStatus: String = "PENDING", // PENDING | PASSED | FAILED
    val totalQualifyingExceptions: Int = 0,
    val academicExceptionsCount: Int = 0,
    val operationsExceptionsCount: Int = 0,
    val cdsExceptionsCount: Int = 0,
    val disciplineExceptionsCount: Int = 0,
    val staffPopulationDenominator: Int = 0,
    val affectedStaffCount: Int = 0,
    val cleanStaffCount: Int = 0,
    val overallPerHead: Float = 0f,
    val rawFullSnapshotJson: String = "",
    val rawSectionMetricsJson: String = "",
    val rawDisciplineMetricsJson: String = "",
    val rawClassMetricsJson: String = "",
    val rawCategoryMetricsJson: String = "",
    val rawCategoryPatternsJson: String = "",
    val rawSeverityDistributionJson: String = "",
    val rawExecutiveSummaryJson: String = "",
    val rawComparisonsJson: String = "",
    val rawTrendsJson: String = "",
    val createdAt: String = ""
) {
    val isVerified: Boolean
        get() = status.equals("VERIFIED", ignoreCase = true) ||
            reconciliationStatus.equals("VERIFIED", ignoreCase = true) ||
            reconciliationStatus.equals("PASSED", ignoreCase = true)
}

enum class DeltaStatus {
    NO_PRIOR_BASELINE,
    UNCHANGED,
    INCREASED,
    DECREASED
}

data class DeltaMetric(
    val current: Float,
    val previous: Float,
    val absoluteChange: Float,
    val percentageChange: Int?,
    val hasBaseline: Boolean,
    val status: DeltaStatus,
    val isFloatMetric: Boolean = false
)

data class AuthoritativeGrandTotals(
    val staffDenominator: Int = 0,
    val affectedStaffCount: Int = 0,
    val staffWithExceptionsPercentage: Int = 0,
    val cleanStaffCount: Int = 0,
    val cleanStaffPercentage: Int = 0,
    val totalExceptions: Int = 0,
    val academicExceptions: Int = 0,
    val operationsExceptions: Int = 0,
    val disciplineExceptions: Int = 0,
    val cdsExceptions: Int = 0,
    val seenExceptions: Int = 0,
    val unseenExceptions: Int = 0,
    val seenCdsCount: Int = 0,
    val unseenCdsCount: Int = 0,
    val overallPerHead: Float = 0f,
    val criticalStaffCount: Int = 0,
    val highStaffCount: Int = 0,
    val mediumStaffCount: Int = 0,
    val lowStaffCount: Int = 0
)

data class SegmentConfig(
    val key: String,
    val label: String,
    val category: String, // PRIMARY | SECONDARY | NURSERY | TOTAL
    val isCombined: Boolean = false,
    val isTotal: Boolean = false,
    val description: String = ""
)

data class SegmentTermStats(
    val exceptionCount: Int = 0,
    val affectedStaffCount: Int = 0,
    val perHead: Float = 0f
)

data class SegmentComparisonRow(
    val segmentKey: String,
    val exceptionComparison: DeltaMetric,
    val affectedStaffComparison: DeltaMetric,
    val perHeadComparison: DeltaMetric
)

data class TermComparisonData(
    val activeTerm: String,
    val priorTerm: String? = null,
    val availableTerms: List<String> = emptyList(),
    val hasComparativeHistory: Boolean = false,
    val segmentStatsByTerm: Map<String, Map<String, SegmentTermStats>> = emptyMap(),
    val segmentComparisons: Map<String, SegmentComparisonRow> = emptyMap(),
    val totalExceptionsChange: DeltaMetric? = null,
    val affectedStaffChange: DeltaMetric? = null,
    val perHeadChange: DeltaMetric? = null
)

data class SectionMetricItem(
    val key: String,
    val label: String,
    val rank: Int,
    val exceptionCount: Int,
    val affectedStaffCount: Int,
    val perHead: Float,
    val shareOfAcademic: Int,
    val historicalDelta: DeltaMetric? = null
) {
    val perHeadText: String
        get() = String.format(java.util.Locale.US, "%.2f", perHead)
}

data class SectionAnalysisData(
    val rankedList: List<SectionMetricItem> = emptyList(),
    val primaryCombinedCount: Int = 0,
    val secondaryCombinedCount: Int = 0,
    val totalAcademicExceptions: Int = 0
)

data class DisciplineMetricItem(
    val rank: Int,
    val discipline: String,
    val exceptionCount: Int,
    val affectedTeachers: Int,
    val perHead: Float,
    val shareOfScope: Int,
    val historicalChange: DeltaMetric? = null,
    val teacherNamesSummary: String = ""
) {
    val perHeadText: String
        get() = String.format(java.util.Locale.US, "%.2f", perHead)
}

data class DisciplineScopeData(
    val rows: List<DisciplineMetricItem> = emptyList(),
    val totalExceptions: Int = 0,
    val totalTeachers: Int = 0,
    val perHead: Float = 0f
)

data class DisciplineAnalysisData(
    val primary: DisciplineScopeData = DisciplineScopeData(),
    val secondary: DisciplineScopeData = DisciplineScopeData(),
    val allDisciplines: List<DisciplineMetricItem> = emptyList(),
    val priorTerm: String? = null
)

data class SubcategoryCount(
    val category: String,
    val count: Int
)

data class ClassMetricItem(
    val rank: Int,
    val className: String,
    val sectionKey: String,
    val sectionLabel: String,
    val exceptionCount: Int,
    val affectedStaffCount: Int,
    val shareOfSection: Int,
    val topCategory: String,
    val subcategories: List<SubcategoryCount> = emptyList(),
    val topSubcategoriesSummary: String = ""
)

data class SectionClassGroup(
    val sectionKey: String,
    val sectionLabel: String,
    val totalExceptions: Int,
    val affectedStaffCount: Int,
    val perHead: Float,
    val classes: List<ClassMetricItem> = emptyList()
)

data class ClassAnalysisData(
    val bySection: List<SectionClassGroup> = emptyList(),
    val allClassesRanked: List<ClassMetricItem> = emptyList(),
    val totalClassesCount: Int = 0,
    val unspecifiedCount: Int = 0
)

data class CategoryFrequencyItem(
    val rank: Int,
    val category: String,
    val occurrences: Int,
    val percentage: Int,
    val cumulativePercentage: Int,
    val affectedSectionsSummary: String,
    val historicalDelta: DeltaMetric? = null
)

data class ConcentrationMetrics(
    val top3Count: Int = 0,
    val top3Share: Int = 0,
    val categoriesFor80PercentVolume: Int = 0,
    val totalCategories: Int = 0
)

data class SectionContributionItem(
    val sectionKey: String,
    val section: String,
    val occurrences: Int,
    val percentage: Int,
    val isInstitutionLevel: Boolean = false
)

data class PatternObservation(
    val id: String,
    val title: String,
    val statement: String,
    val highlight: String
)

data class CategoryPatternData(
    val rankedCategories: List<CategoryFrequencyItem> = emptyList(),
    val topDrivers: List<CategoryFrequencyItem> = emptyList(),
    val concentration: ConcentrationMetrics = ConcentrationMetrics(),
    val sectionContributions: List<SectionContributionItem> = emptyList(),
    val observations: List<PatternObservation> = emptyList(),
    val priorTermName: String? = null
)

data class TermTrendPoint(
    val termName: String,
    val totalExceptions: Int,
    val academicExceptions: Int,
    val operationsExceptions: Int,
    val disciplineExceptions: Int,
    val cdsExceptions: Int,
    val affectedStaffCount: Int,
    val perHead: Float,
    val isFromSnapshot: Boolean = false
)

data class OverallTrendData(
    val points: List<TermTrendPoint> = emptyList(),
    val availableTerms: List<String> = emptyList(),
    val netChangePct: Int? = null
)

data class CategorySectionMatrixRow(
    val category: String,
    val jnrColl: Int,
    val snrCol: Int,
    val jnrPri: Int,
    val snrPri: Int,
    val nur: Int,
    val rowTotal: Int
)

data class CategorySectionMatrixTotals(
    val jnrColl: Int = 0,
    val snrCol: Int = 0,
    val jnrPri: Int = 0,
    val snrPri: Int = 0,
    val nur: Int = 0,
    val grandTotal: Int = 0
)

data class CategorySectionMatrix(
    val rows: List<CategorySectionMatrixRow> = emptyList(),
    val columnTotals: CategorySectionMatrixTotals = CategorySectionMatrixTotals()
)

data class TermAnalysisResult(
    val term: String,
    val session: String,
    val periodLabel: String,
    val isFromRawData: Boolean = true,
    val isHydratedFromDurableSnapshot: Boolean = false,
    val isCorruptOrUnverified: Boolean = false,
    val snapshotStatus: String? = null,
    val snapshotVersion: Int? = null,
    val officialTermEndDate: String? = null,
    val errorMessage: String? = null,
    val grandTotals: AuthoritativeGrandTotals = AuthoritativeGrandTotals(),
    val comparisons: TermComparisonData = TermComparisonData(activeTerm = term),
    val sections: SectionAnalysisData = SectionAnalysisData(),
    val disciplines: DisciplineAnalysisData = DisciplineAnalysisData(),
    val classes: ClassAnalysisData = ClassAnalysisData(),
    val categoryPatterns: CategoryPatternData = CategoryPatternData(),
    val trends: OverallTrendData = OverallTrendData(),
    val matrix: CategorySectionMatrix = CategorySectionMatrix()
)

data class DrillDownContext(
    val type: String, // category | section | discipline | kpi
    val title: String,
    val subtitle: String,
    val exceptions: List<ComplianceReport> = emptyList(),
    val snapshotAnalytics: DrillDownAnalytics? = null
)

data class NameCountPct(
    val name: String,
    val count: Int,
    val percentage: Int
)

data class DrillDownAnalytics(
    val total: Int,
    val uniqueStaffCount: Int,
    val perHeadText: String,
    val seenCount: Int,
    val unseenCount: Int,
    val bySection: List<NameCountPct> = emptyList(),
    val byDiscipline: List<NameCountPct> = emptyList(),
    val byClass: List<NameCountPct> = emptyList(),
    val byTerm: List<NameCountPct> = emptyList()
)

data class PptxSlideConfig(
    val slide2Heading: String = "PRESENTATION OF DATA ON EXCEPTIONS - COMPARATIVE ANALYSIS",
    val slide3Heading: String = "PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS DISCIPLINE - FIRST TERM",
    val slide4Heading: String = "PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS CLASSES - FIRST TERM",
    val slide5Heading: String = "PRESENTATION OF EXCEPTIONS ANALYSIS IN VARIOUS DISCIPLINE - FIRST TERM"
) {
    val isValid: Boolean
        get() = slide2Heading.isNotBlank() &&
            slide3Heading.isNotBlank() &&
            slide4Heading.isNotBlank() &&
            slide5Heading.isNotBlank()

    companion object {
        fun forTerm(term: String): PptxSlideConfig {
            val upTerm = term.ifBlank { "First Term" }.uppercase()
            return PptxSlideConfig(
                slide2Heading = "PRESENTATION OF DATA ON EXCEPTIONS - COMPARATIVE ANALYSIS",
                slide3Heading = "PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS DISCIPLINE - $upTerm",
                slide4Heading = "PRESENTATION OF DATA ON EXCEPTIONS IN VARIOUS CLASSES - $upTerm",
                slide5Heading = "PRESENTATION OF EXCEPTIONS ANALYSIS IN VARIOUS DISCIPLINE - $upTerm"
            )
        }
    }
}
