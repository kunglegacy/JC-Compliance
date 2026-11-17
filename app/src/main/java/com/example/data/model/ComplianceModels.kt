package com.example.data.model

enum class ThemePreference(val key: String, val label: String) {
    LIGHT("light", "Light Mode"),
    DARK("dark", "Dark Mode"),
    SYSTEM("system", "System Default");

    companion object {
        fun fromKey(key: String?): ThemePreference =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AccentPalette(val key: String, val label: String) {
    SOLAR("solar", "Solar Amber"),
    SAPPHIRE("sapphire", "Sapphire"),
    ROSE("rose", "Rose Quartz"),
    AMETHYST("amethyst", "Amethyst"),
    TITANIUM("titanium", "Titanium");

    companion object {
        fun fromKey(key: String?): AccentPalette =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SOLAR
    }
}

enum class LockPolicy(val key: String, val title: String, val subtitle: String) {
    ALWAYS("always", "Always Lock", "Require auth on every access."),
    HOURS_24("24h", "24-Hour Lock", "Lock after 24h of inactivity."),
    NEVER("never", "Stay Unlocked", "No password required on return.");

    companion object {
        fun fromKey(key: String?): LockPolicy =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: HOURS_24
    }
}

enum class UserRole(val key: String, val label: String) {
    SUPER_ADMIN("super_admin", "Super Admin"),
    ADMIN("admin", "Administrator"),
    STANDARD("standard", "Reporting Officer");

    val isAdminOrAbove: Boolean
        get() = this == SUPER_ADMIN || this == ADMIN

    val isSuperAdmin: Boolean
        get() = this == SUPER_ADMIN

    companion object {
        fun fromKey(key: String?): UserRole = when (key?.lowercase()?.trim()) {
            "super_admin" -> SUPER_ADMIN
            "admin" -> ADMIN
            else -> STANDARD
        }
    }
}

data class UserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val displayName: String,
    val staffNumber: String = "",
    val role: UserRole = UserRole.STANDARD,
    val status: String = "active",
    val isVerified: Boolean = true,
    val isOwner: Boolean = false,
    val avatarUrl: String = ""
)

data class DirectorateUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val staffNumber: String,
    val role: UserRole,
    val status: String,
    val isVerified: Boolean,
    val isOwner: Boolean,
    val isFounder: Boolean,
    val createdAt: String? = null,
    val avatarUrl: String = ""
)

data class StaffMember(
    val id: String,
    val staffNumber: String,
    val staffCode: String,
    val name: String,
    val category: String, // "Academic" or "Non-Academic"
    val staffGroup: String,
    val role: String,
    val department: String,
    val assignedLocation: String,
    val isActive: Boolean = true
)

data class Classroom(
    val id: String,
    val name: String,
    val section: String
)

data class SubjectItem(
    val id: String,
    val name: String,
    val department: String
)

enum class ComplianceDivision(val key: String, val dbValue: String, val label: String) {
    ACADEMIC("academic", "Academic", "Academic"),
    OPERATIONS("operations", "Operation", "Operation"),
    CDS("cds", "CDS", "CDS"),
    DISCIPLINE("discipline", "Discipline", "Discipline");

    companion object {
        fun fromAny(raw: String?): ComplianceDivision? {
            val s = raw?.lowercase()?.trim().orEmpty()
            return when {
                s.contains("academ") || s.contains("aca") -> ACADEMIC
                s.contains("cds") -> CDS
                s.contains("disc") || s.contains("decorum") -> DISCIPLINE
                s.contains("oper") || s.contains("opr") || s.contains("maint") ||
                    s.contains("transp") || s.contains("minder") || s.contains("sec") ||
                    s.contains("hostel") || s.contains("kitchen") -> OPERATIONS
                else -> null
            }
        }
    }
}

data class ComplianceCheck(
    val id: String,
    val name: String,
    val category: String,
    val division: ComplianceDivision,
    val subSection: String = "",
    val defaultEffect: String = "",
    val severityWeight: Int = 2,
    val isCustom: Boolean = false
)

enum class ReportResolutionStatus(val key: String, val label: String) {
    OPEN("OPEN", "Open"),
    PENDING("PENDING", "Pending"),
    ACTION_REQUIRED("ACTION_REQUIRED", "Action Required"),
    REJECTED("REJECTED", "Rejected"),
    ACCEPTED("ACCEPTED", "Accepted");

    companion object {
        fun fromKey(raw: String?): ReportResolutionStatus = when (raw?.uppercase()?.trim()) {
            "PENDING", "IN_REVIEW" -> PENDING
            "ACTION_REQUIRED", "ESCALATED" -> ACTION_REQUIRED
            "REJECTED", "DISMISSED" -> REJECTED
            "ACCEPTED", "RESOLVED" -> ACCEPTED
            else -> OPEN
        }
    }
}

enum class SeverityLevel(val label: String, val rank: Int) {
    NONE("No recorded exceptions", 0),
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4);

    companion object {
        fun fromCount(count: Int): SeverityLevel = when {
            count <= 0 -> NONE
            count == 1 -> LOW
            count == 2 -> MEDIUM
            count == 3 -> HIGH
            else -> CRITICAL
        }
    }
}

data class ComplianceReport(
    val id: String,
    val reportNumber: String,
    val reportedBy: String,
    val reporterName: String,
    val reporterRole: String,
    val incidentDate: String, // YYYY-MM-DD
    val weekNumber: Int,
    val term: String,
    val staffId: String? = null,
    val staffName: String? = null,
    val staffCode: String? = null,
    val staffCategory: String? = null,
    val staffGroup: String? = null,
    val role: String? = null,
    val department: String? = null,
    val assignedLocation: String? = null,
    val checkId: String? = null,
    val classId: String? = null,
    val className: String? = null,
    val subjectId: String? = null,
    val subject: String? = null,
    val exceptionText: String,
    val effectText: String,
    val category: String,
    val division: String,
    val targetType: String,
    val complianceCheck: String,
    val evidenceUrl: String? = null,
    val evidenceType: String = "none",
    val evidenceName: String? = null,
    val evidenceUrls: List<String> = emptyList(),
    val evidenceTypes: List<String> = emptyList(),
    val evidenceNotes: List<String> = emptyList(),
    val status: ReportResolutionStatus = ReportResolutionStatus.OPEN,
    val reviewStatus: String = "UNSEEN",
    val isSeen: Boolean = false,
    val seenAt: String? = null,
    val seenBy: String? = null,
    val resolutionNotes: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: String? = null,
    val isEdited: Boolean = false,
    val editedAt: String? = null,
    val createdAt: String = ""
) {
    val isPending: Boolean
        get() = status == ReportResolutionStatus.PENDING

    val isActionRequired: Boolean
        get() = status == ReportResolutionStatus.ACTION_REQUIRED

    val isAccepted: Boolean
        get() = status == ReportResolutionStatus.ACCEPTED

    val isRejected: Boolean
        get() = status == ReportResolutionStatus.REJECTED

    val canUserEdit: Boolean
        get() = status == ReportResolutionStatus.OPEN ||
            status == ReportResolutionStatus.PENDING ||
            status == ReportResolutionStatus.ACTION_REQUIRED

    val isReviewedFinal: Boolean
        get() = status == ReportResolutionStatus.ACCEPTED ||
            status == ReportResolutionStatus.REJECTED
}

object MediaUploadLimits {
    const val MAX_IMAGES_PER_REPORT = 4
    const val MAX_VIDEOS_PER_REPORT = 2
}

data class ReportEvidenceAttachment(
    val id: String,
    val pathOrUri: String,
    val mediaType: String, // image | video | audio | document
    val displayName: String,
    val isExistingCloud: Boolean = false
)

data class AppNotificationRecord(
    val id: String,
    val recipientUserId: String?,
    val recipientRole: String,
    val type: String,
    val title: String,
    val body: String,
    val reportId: String? = null,
    val reportNumber: String? = null,
    val actorName: String = "",
    val isRead: Boolean = false,
    val createdAt: String = ""
)

data class DraftMediaItem(
    val id: String,
    val pathOrUri: String,
    val mediaType: String,
    val capturedAtLabel: String
)

data class OfflineDraft(
    val id: String,
    val userId: String = "",
    val title: String,
    val notes: String,
    val mediaPath: String? = null,
    val mediaType: String = "none", // image, video, audio, document, none
    val mediaName: String? = null,
    val mediaItems: List<DraftMediaItem> = emptyList(),
    val incidentDate: String,
    val createdAtMillis: Long
)

data class ReportFormDraft(
    val divisionKey: String = ComplianceDivision.ACADEMIC.key,
    val targetType: String = "staff",
    val classTierFilter: String = "All Tiers",
    val staffSearch: String = "",
    val staffId: String? = null,
    val staffCode: String? = null,
    val staffName: String? = null,
    val classId: String? = null,
    val className: String? = null,
    val subjectId: String? = null,
    val subjectName: String? = null,
    val operationScope: String = "Maintenance",
    val selectedBus: String = "Bus 1",
    val opPersonOrLocation: String = "",
    val studentNamesArm: String = "",
    val checkId: String? = null,
    val checkName: String? = null,
    val incidentDate: String = "",
    val exceptionText: String = "",
    val effectText: String = "",
    val sourceDraftId: String? = null,
    val selectedDraftMedia: List<DraftMediaItem> = emptyList(),
    val localMediaPath: String? = null,
    val evidenceType: String = "none",
    val evidenceName: String? = null,
    val hasUserEdited: Boolean = false,
    val updatedAtMillis: Long = 0L
) {
    val hasContent: Boolean
        get() = hasUserEdited && (
            exceptionText.isNotBlank() ||
                effectText.isNotBlank() ||
                opPersonOrLocation.isNotBlank() ||
                studentNamesArm.isNotBlank() ||
                staffSearch.isNotBlank() ||
                !staffId.isNullOrBlank() ||
                !classId.isNullOrBlank() ||
                !subjectId.isNullOrBlank() ||
                !checkId.isNullOrBlank() ||
                selectedDraftMedia.isNotEmpty() ||
                !localMediaPath.isNullOrBlank() ||
                (evidenceType != "none" && !evidenceName.isNullOrBlank()) ||
                divisionKey != ComplianceDivision.ACADEMIC.key ||
                targetType != "staff" ||
                operationScope != "Maintenance"
            )
}

data class SystemSettings(
    val academicSession: String = "2025/2026",
    val activeTerm: String = "First Term",
    val termStartDate: String = "2025-09-08",
    val manualWeekOverride: String = "none" // "none", "1".."12", "end_of_term"
)

data class AcademicWeekStatus(
    val weekNumber: Int,
    val isEndOfTerm: Boolean,
    val label: String,
    val isHistorical: Boolean
)

enum class ReportScope(val key: String, val label: String) {
    ALL("All", "All Divisions"),
    ACADEMIC("Academic", "All Academic"),
    JUNIOR_SECONDARY("Junior Secondary", "Junior Secondary"),
    SENIOR_SECONDARY("Senior Secondary", "Senior Secondary"),
    JUNIOR_PRIMARY("Junior Primary", "Junior Primary"),
    SENIOR_PRIMARY("Senior Primary", "Senior Primary"),
    NURSERY("Nursery", "Nursery"),
    MENTORSHIP("Mentorship", "Mentorship"),
    DISCIPLINE("Discipline", "Discipline"),
    MAINTENANCE("Maintenance", "Maintenance"),
    SECURITY("Security", "Security"),
    TRANSPORT("Transport", "Transport"),
    MINDERS("Minders", "Minders"),
    HOSTEL_KITCHEN("Hostel / Kitchen", "Hostel / Kitchen"),
    CDS("CDS", "CDS")
}

data class StaffAggregateMetric(
    val staffKey: String,
    val staffId: String?,
    val staffName: String,
    val staffCode: String,
    val department: String,
    val exceptionCount: Int,
    val seenCount: Int,
    val unseenCount: Int,
    val severity: SeverityLevel,
    val latestIncidentDate: String,
    val reports: List<ComplianceReport>
)

data class OfficerMetric(
    val officerKey: String,
    val officerName: String,
    val officerEmail: String,
    val totalReports: Int,
    val seenCount: Int,
    val unseenCount: Int,
    val rejectedCount: Int
)

enum class OfficerSortOption(val label: String) {
    HIGHEST_TOTAL("Highest Total Reports"),
    MOST_UNSEEN("Most Unseen"),
    MOST_REJECTED("Most Rejected")
}

data class SectionBreakdown(
    val sectionName: String,
    val count: Int,
    val headcount: Int,
    val perHeadRatio: Float
)

data class CategoryBreakdown(
    val categoryName: String,
    val count: Int
)

data class DivisionSummary(
    val division: ComplianceDivision,
    val totalReports: Int,
    val seenCount: Int,
    val unseenCount: Int
)

data class AnalysisSummary(
    val totalExceptions: Int,
    val activeCases: Int,
    val resolvedCases: Int,
    val seenCount: Int,
    val unseenCount: Int,
    val divisionBreakdown: List<DivisionSummary>,
    val sectionBreakdowns: List<SectionBreakdown>,
    val disciplinePrimaryCount: Int,
    val disciplinePrimaryRatio: Float,
    val disciplineSecondaryCount: Int,
    val disciplineSecondaryRatio: Float,
    val categoryBreakdowns: List<CategoryBreakdown>,
    val staffSeverityCounts: Map<SeverityLevel, Int>
)

data class BiometricStatus(
    val available: Boolean,
    val enrolled: Boolean,
    val status: String,
    val message: String
)

data class SupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() &&
            !url.contains("your-project-id", ignoreCase = true) &&
            !url.contains("placeholder", ignoreCase = true) &&
            anonKey.isNotBlank() &&
            !anonKey.contains("your_key_here", ignoreCase = true) &&
            !anonKey.contains("your-anon", ignoreCase = true) &&
            !anonKey.contains("placeholder", ignoreCase = true)
}
