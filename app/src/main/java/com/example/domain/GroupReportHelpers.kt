package com.example.domain

import com.example.data.model.Classroom
import com.example.data.model.GroupReportMember
import com.example.data.model.StaffMember
import com.example.data.model.SubjectItem
import org.json.JSONArray
import org.json.JSONObject

object GroupReportHelpers {

    private val canonicalDepartments = listOf(
        "Languages",
        "Life Sciences",
        "Physical Sciences",
        "Social Sciences"
    )

    fun resolveStaffDepartment(staff: StaffMember): String {
        val raw = staff.staffGroup.trim()
        canonicalDepartments.firstOrNull { it.equals(raw, ignoreCase = true) }?.let { return it }
        val normalized = AuthoritativeAnalysisBuilder.normalizeAcademicDepartment(
            disciplineGroup = null,
            subjectName = staff.subjects,
            staffGroup = raw,
            division = staff.department
        )
        if (normalized in canonicalDepartments && (staff.subjects.isNotBlank() || raw.isNotBlank())) {
            return normalized
        }
        return raw.ifBlank { staff.department.ifBlank { "Academic" } }
    }

    fun normalizeMembers(
        members: List<GroupReportMember>,
        staffRoster: List<StaffMember>,
        classes: List<Classroom>,
        subjects: List<SubjectItem>
    ): List<GroupReportMember> {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        fun isUuid(v: String?) = !v.isNullOrBlank() && uuidRegex.matches(v.trim())

        return members.map { m ->
            val matchedStaff = staffRoster.firstOrNull {
                (m.staffId.isNotBlank() && it.id.equals(m.staffId, true)) ||
                    (m.staffCode.isNotBlank() && it.staffCode.equals(m.staffCode, true)) ||
                    it.name.equals(m.staffName, true)
            }
            val resolvedStaffId = matchedStaff?.id?.takeIf { it.isNotBlank() } ?: m.staffId
            val resolvedDept = m.staffGroup.trim().ifBlank {
                matchedStaff?.let { resolveStaffDepartment(it) } ?: "Academic"
            }

            val rawClassName = m.className.trim()
            val isNoneClass = rawClassName.isBlank() ||
                rawClassName.equals("None", ignoreCase = true) ||
                m.classId.equals("NONE", ignoreCase = true)
            val matchedClass = if (!isNoneClass) {
                classes.firstOrNull {
                    (isUuid(m.classId) && it.id == m.classId) ||
                        it.name.equals(rawClassName, ignoreCase = true)
                }
            } else null

            val resolvedClassId = when {
                isNoneClass -> "NONE"
                matchedClass != null -> matchedClass.id
                !m.classId.isNullOrBlank() -> m.classId
                else -> "NONE"
            }
            val resolvedClassName = when {
                isNoneClass -> "None"
                matchedClass != null -> matchedClass.name
                else -> rawClassName
            }

            val rawSubjName = m.subjectName.trim()
            val matchedSubj = if (rawSubjName.isNotBlank()) {
                subjects.firstOrNull {
                    (isUuid(m.subjectId) && it.id == m.subjectId) ||
                        it.name.equals(rawSubjName, ignoreCase = true)
                }
            } else null

            val resolvedSubjectId = matchedSubj?.id ?: m.subjectId?.takeIf { isUuid(it) }
            val resolvedSubjectName = matchedSubj?.name ?: rawSubjName

            GroupReportMember(
                staffId = resolvedStaffId,
                staffName = matchedStaff?.name?.takeIf { it.isNotBlank() } ?: m.staffName.trim(),
                staffCode = matchedStaff?.staffCode?.takeIf { it.isNotBlank() } ?: m.staffCode.trim(),
                staffGroup = resolvedDept,
                classId = resolvedClassId,
                className = resolvedClassName,
                subjectId = resolvedSubjectId,
                subjectName = resolvedSubjectName
            )
        }
    }

    fun toJsonArray(members: List<GroupReportMember>): JSONArray = JSONArray().apply {
        members.forEach { m ->
            put(JSONObject().apply {
                put("staff_id", m.staffId)
                put("staff_name", m.staffName)
                put("staff_code", m.staffCode)
                put("staff_group", m.staffGroup)
                put("class_id", m.classId ?: "NONE")
                put("class_name", m.className.ifBlank { "None" })
                if (!m.subjectId.isNullOrBlank()) put("subject_id", m.subjectId) else put("subject_id", JSONObject.NULL)
                put("subject_name", m.subjectName)
            })
        }
    }

    fun applyGroupFieldsToPayload(
        payload: JSONObject,
        members: List<GroupReportMember>
    ) {
        val membersArray = toJsonArray(members)
        val joinedNames = members.joinToString(", ") { it.staffName }
        val joinedClasses = members.joinToString(", ") { it.className.ifBlank { "None" } }
        val joinedSubjects = members.joinToString(", ") { it.subjectName.ifBlank { "—" } }
        val joinedDepts = members.map { it.staffGroup.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Academic" }

        payload.put("division", "Academic")
        payload.put("department", "Academic")
        payload.put("target_type", "division")
        payload.put("is_group_report", true)
        payload.put("group_members", membersArray)
        payload.put("staff_id", JSONObject.NULL)
        payload.put("staff_name", joinedNames)
        payload.put("staff_code", "GROUP (${members.size})")
        payload.put("staff_category", "Academic")
        payload.put("staff_group", joinedDepts)
        payload.put("role", JSONObject.NULL)
        payload.put("assigned_location", joinedClasses)
        payload.put("class_id", JSONObject.NULL)
        payload.put("class_name", joinedClasses)
        payload.put("subject_id", JSONObject.NULL)
        payload.put("subject", joinedSubjects)
        payload.put("subject_name", joinedSubjects)
        payload.put(
            "exception_tags",
            JSONArray().apply {
                put("__GROUP_REPORT_MEMBERS__:$membersArray")
            }
        )
    }
}
