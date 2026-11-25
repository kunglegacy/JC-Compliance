package com.example

import com.example.data.local.ComplianceTaxonomy
import com.example.data.model.ComplianceReport
import com.example.data.model.GroupReportMember
import com.example.data.model.ReportResolutionStatus
import com.example.data.model.ReportScope
import com.example.data.model.StaffMember
import com.example.domain.DocxReportExporter
import com.example.domain.GroupReportHelpers
import com.example.domain.ReportExportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupReportAndExportTest {

    private fun sampleReport(
        id: String = "r-1",
        isSeen: Boolean = true,
        reviewStatus: String = "SEEN",
        status: ReportResolutionStatus = ReportResolutionStatus.OPEN,
        isGroupReport: Boolean = false,
        groupMembers: List<GroupReportMember> = emptyList(),
        className: String? = "Basic 3B",
        subject: String? = "Mathematics"
    ) = ComplianceReport(
        id = id,
        reportNumber = "EXP-$id",
        reportedBy = "user-1",
        reporterName = "QA Officer",
        reporterRole = "standard",
        incidentDate = "2026-11-24",
        weekNumber = 8,
        term = "First Term",
        staffId = if (isGroupReport) null else "staff-1",
        staffName = if (isGroupReport) "Alheri Ali, Musa Bala" else "Alheri Ali",
        staffCode = if (isGroupReport) "GROUP (${groupMembers.size})" else "JCB-T-183",
        staffCategory = "Academic",
        staffGroup = "Languages",
        className = className,
        subject = subject,
        exceptionText = "Lesson note incomplete before period start.",
        effectText = "Delayed instructional delivery.",
        category = "Lesson Delivery",
        division = "Academic",
        targetType = "division",
        complianceCheck = "Lesson Note Review",
        status = status,
        reviewStatus = reviewStatus,
        isSeen = isSeen,
        isGroupReport = isGroupReport,
        groupMembers = groupMembers
    )

    @Test
    fun `group member normalization resolves None class and canonical departments`() {
        val staffRoster = listOf(
            StaffMember("550e8400-e29b-41d4-a716-446655440000", "183", "JCB-T-183", "Alheri Ali", "Academic", "Languages", "Teacher", "Academic", "Main"),
            StaffMember("550e8400-e29b-41d4-a716-446655440001", "184", "JCB-T-184", "Musa Bala", "Academic", "", "Teacher", "Academic", "Main", subjects = "Physics")
        )
        val rawMembers = listOf(
            GroupReportMember(
                staffId = "550e8400-e29b-41d4-a716-446655440000",
                staffName = "Alheri Ali",
                staffCode = "JCB-T-183",
                staffGroup = "",
                classId = "NONE",
                className = "None",
                subjectId = "sub-eng",
                subjectName = "English Language"
            ),
            GroupReportMember(
                staffId = "550e8400-e29b-41d4-a716-446655440001",
                staffName = "Musa Bala",
                staffCode = "JCB-T-184",
                staffGroup = "",
                classId = "cls-ss1-gold",
                className = "SS 1 Gold",
                subjectId = "sub-phy",
                subjectName = "Physics"
            )
        )

        val normalized = GroupReportHelpers.normalizeMembers(
            members = rawMembers,
            staffRoster = staffRoster,
            classes = ComplianceTaxonomy.defaultClasses,
            subjects = ComplianceTaxonomy.defaultSubjects
        )

        assertEquals(2, normalized.size)
        assertEquals("Languages", normalized[0].staffGroup)
        assertEquals("NONE", normalized[0].classId)
        assertEquals("None", normalized[0].className)
        assertEquals("Physical Sciences", normalized[1].staffGroup)
        assertEquals("cls-ss1-gold", normalized[1].classId)
        assertEquals("SS 1 Gold", normalized[1].className)
    }

    @Test
    fun `export eligibility guard excludes unseen flagged action_required and rejected`() {
        assertTrue(ReportExportData.isExportEligible(sampleReport(id = "ok1", isSeen = true, reviewStatus = "SEEN", status = ReportResolutionStatus.OPEN)))
        assertTrue(ReportExportData.isExportEligible(sampleReport(id = "ok2", isSeen = true, reviewStatus = "APPROVED", status = ReportResolutionStatus.ACCEPTED)))
        assertFalse(ReportExportData.isExportEligible(sampleReport(id = "u1", isSeen = false, reviewStatus = "SEEN")))
        assertFalse(ReportExportData.isExportEligible(sampleReport(id = "u2", isSeen = true, reviewStatus = "UNSEEN")))
        assertFalse(ReportExportData.isExportEligible(sampleReport(id = "f1", isSeen = true, reviewStatus = "FLAGGED")))
        assertFalse(ReportExportData.isExportEligible(sampleReport(id = "a1", isSeen = true, reviewStatus = "SEEN", status = ReportResolutionStatus.ACTION_REQUIRED)))
        assertFalse(ReportExportData.isExportEligible(sampleReport(id = "r1", isSeen = true, reviewStatus = "SEEN", status = ReportResolutionStatus.REJECTED)))
    }

    @Test
    fun `group report prepares subRows and renders OOXML vertical merges cleanly`() {
        val members = listOf(
            GroupReportMember(
                staffId = "s1",
                staffName = "Alheri Ali",
                staffCode = "JCB-T-183",
                staffGroup = "Languages",
                classId = "NONE",
                className = "None",
                subjectId = "sub1",
                subjectName = "English"
            ),
            GroupReportMember(
                staffId = "s2",
                staffName = "Musa Bala",
                staffCode = "JCB-T-184",
                staffGroup = "Physical Sciences",
                classId = "c2",
                className = "SS 1A",
                subjectId = "sub2",
                subjectName = "Physics"
            ),
            GroupReportMember(
                staffId = "s3",
                staffName = "Grace Okafor",
                staffCode = "JCB-T-185",
                staffGroup = "Life Sciences",
                classId = "c3",
                className = "SS 2B",
                subjectId = "sub3",
                subjectName = "Biology"
            )
        )
        val staffRoster = listOf(
            StaffMember("s1", "183", "JCB-T-183", "Alheri Ali", "Academic", "Languages", "Teacher", "Academic", "Main"),
            StaffMember("s2", "184", "JCB-T-184", "Musa Bala", "Academic", "Physical Sciences", "Teacher", "Academic", "Main"),
            StaffMember("s3", "185", "JCB-T-185", "Grace Okafor", "Academic", "Life Sciences", "Teacher", "Academic", "Main")
        )
        val groupReport = sampleReport(
            id = "grp1",
            isSeen = true,
            reviewStatus = "SEEN",
            isGroupReport = true,
            groupMembers = members,
            className = "None, SS 1A, SS 2B",
            subject = "English, Physics, Biology"
        )
        val excludedUnseen = sampleReport(
            id = "unseen1",
            isSeen = false,
            reviewStatus = "UNSEEN"
        )

        val dataset = ReportExportData.prepareDataset(
            scope = ReportScope.ALL,
            format = "DOCX",
            records = listOf(groupReport, excludedUnseen),
            staffRoster = staffRoster
        )

        assertEquals(1, dataset.records.size)
        assertEquals(1, dataset.rows.size)
        val row = dataset.rows.first()
        assertTrue(row.isGroup)
        assertEquals(3, row.subRows.size)
        assertEquals("Alheri Ali (Languages)", row.subRows[0].name)
        assertEquals("None", row.subRows[0].className)
        assertEquals("English", row.subRows[0].subject)
        assertEquals("Musa Bala (Physical Sciences)", row.subRows[1].name)
        assertEquals("Grace Okafor (Life Sciences)", row.subRows[2].name)

        val xml = DocxReportExporter.buildDocumentXml(dataset, hasCrest = false)
        assertTrue(xml.contains("<w:vMerge w:val=\"restart\"/>"))
        assertTrue(xml.contains("<w:vMerge/>"))
        assertTrue(xml.contains("Alheri Ali (Languages)"))
        assertTrue(xml.contains("Musa Bala (Physical Sciences)"))
        assertTrue(xml.contains("Grace Okafor (Life Sciences)"))
        assertTrue(xml.contains("1 Exception(s)"))
    }
}
