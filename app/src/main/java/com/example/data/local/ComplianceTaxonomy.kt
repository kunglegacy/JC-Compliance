package com.example.data.local

import com.example.data.model.Classroom
import com.example.data.model.ComplianceCheck
import com.example.data.model.ComplianceDivision
import com.example.data.model.SubjectItem

object ComplianceTaxonomy {

    val cdsChecks = listOf(
        ComplianceCheck("CDS1", "CBT", "CDS", ComplianceDivision.CDS, "CBT"),
        ComplianceCheck("CDS7", "Camera", "CDS", ComplianceDivision.CDS, "Camera"),
        ComplianceCheck("CDS8", "Computer", "CDS", ComplianceDivision.CDS, "Computer"),
        ComplianceCheck("CDS_PROJ", "Projector", "CDS", ComplianceDivision.CDS, "Projector"),
        ComplianceCheck("CDS9", "Result Portal", "CDS", ComplianceDivision.CDS, "Result Portal"),
        ComplianceCheck("CDS5", "Exam Records", "CDS", ComplianceDivision.CDS, "Exam Records"),
        ComplianceCheck("CDS6", "Staff Records", "CDS", ComplianceDivision.CDS, "Staff Records"),
        ComplianceCheck("CDS11", "Biometric", "CDS", ComplianceDivision.CDS, "Biometric")
    )

    val academicChecks = listOf(
        ComplianceCheck("ACA1", "UNPROFESSIONAL MISCONDUCT OF TEACHERS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA2", "BEHIND IN THE SCHEME OF WORK", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA3", "AHEAD IN THE SCHEME OF WORK", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA4", "NO SCHEME GIVEN TO STUDENTS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA5", "UNMARKED ASSIGNMENT", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA6", "DIRTY AND DISORGANISED CLASSROOM", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA7", "USING A FAINT MAKER", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA8", "POORLY WRITTEN QUALITY NOTE/CONTENT", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA9", "LATENESS TO CLASSROOM", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA10", "LATENESS IN TEACHING", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA11", "LOCKED CLASSROOM DOOR", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA12", "NON COMPLIANCE IN WRITING SPECIFIC OBJECTIVES", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA13", "NON COMPLIANCE TO FLEXIBLE GROUPING", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA14", "CLASS HAVING INCOMPLETE NOTE", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA15", "EXCEPTION DIRECTLY TO ACADEMICS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA16", "PENDING DISCIPLINARY CASES", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA17", "IMPROPER DRESSING", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA18", "POOR CLASS CONTROL AND MANAGEMENT", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA19", "MARKING OF NOTES IN CLASS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA20", "SITTING DOWN DURING TUTORIAL DELIVERY", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA21", "ABSENCE FROM CLASS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA22", "UNMARKED NOTES", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA23", "STUDENTS SLEEPING DURING TUTORIALS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA24", "STUDENTS DISTRACTED DURING TUTORIALS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA25", "NO TOPIC/SPECIFIC OBJECTIVES WRITTEN DOWN", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA26", "LATENESS FOR EXAM INVIGILATION", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA27", "DISTRACTIONS DURING EXAMS(TRACKER, CA & QUALITY EXAM)", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA28", "BEATING OF STUDENTS/PUPILS", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA29", "UNSTANDARDISED EXAM REFERENCE QUESTION", "Academic", ComplianceDivision.ACADEMIC, "Academic"),
        ComplianceCheck("ACA30", "DEVIATION FROM REGIMENTED PLAN", "Academic", ComplianceDivision.ACADEMIC, "Academic")
    )

    val transportChecks = listOf(
        ComplianceCheck("OPR1", "Tyres", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR2", "Windows", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR3", "Body Work", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR4", "Lights", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR5", "Interior", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR6", "Pick-up/drop-off lateness", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR7", "Breakdown", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR8", "Accident", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR9", "Fire extinguisher", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR10", "Jack", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR11", "Driving Licences", "Transport", ComplianceDivision.OPERATIONS, "Transport"),
        ComplianceCheck("OPR12", "Dressing", "Transport", ComplianceDivision.OPERATIONS, "Transport")
    )

    val maintenanceChecks = listOf(
        ComplianceCheck("OPR13", "Doors", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR14", "Windows", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR15", "Tables", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR16", "Chairs", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR17", "Tiles", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR18", "Lights", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR19", "Fans", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR20", "Flowers", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR21", "Tidiness", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR22", "Pipes", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR23", "Kerbs", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR24", "AC", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance"),
        ComplianceCheck("OPR32", "Maintenance", "Maintenance", ComplianceDivision.OPERATIONS, "Maintenance")
    )

    val hostelKitchenChecks = listOf(
        ComplianceCheck("OPR25", "Promptness to class from hostel", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen"),
        ComplianceCheck("OPR26", "Table etiquette", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen"),
        ComplianceCheck("OPR27", "Hot water", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen"),
        ComplianceCheck("OPR28", "Drinking/using water", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen"),
        ComplianceCheck("OPR29", "Cleanliness", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen"),
        ComplianceCheck("OPR30", "Siesta enforcement", "Hostel / Kitchen", ComplianceDivision.OPERATIONS, "Hostel / Kitchen")
    )

    val mindersChecks = listOf(
        ComplianceCheck("OPR31", "Minders", "Minders", ComplianceDivision.OPERATIONS, "Minders")
    )

    val securityChecks = listOf(
        ComplianceCheck("SEC1", "Post Desertion / Absent from Duty", "Security", ComplianceDivision.OPERATIONS, "Security"),
        ComplianceCheck("SEC2", "Gate Control & Logbook Non-Compliance", "Security", ComplianceDivision.OPERATIONS, "Security"),
        ComplianceCheck("SEC3", "Perimeter Patrol Negligence", "Security", ComplianceDivision.OPERATIONS, "Security"),
        ComplianceCheck("SEC4", "Sleeping on Duty", "Security", ComplianceDivision.OPERATIONS, "Security")
    )

    val generalOperationChecks = listOf(
        ComplianceCheck("OPR33", "Dressing", "Dressing", ComplianceDivision.OPERATIONS, "Dressing"),
        ComplianceCheck("OPR34", "Effectiveness", "Effectiveness", ComplianceDivision.OPERATIONS, "Effectiveness")
    )

    val disciplineChecks = listOf(
        ComplianceCheck("DIS1", "STUDENT CODE OF CONDUCT VIOLATION", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS2", "LATENESS TO MORNING ASSEMBLY", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS3", "LOITERING DURING CLASS HOURS/ SEEN OUTSIDE THE CLASS WITHOUT EXIT CARD", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS4", "BULLYING AND HARASSMENT", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS5", "STUDENT IMPROPER DRESSING & GROOMING", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS6", "UNAUTHORIZED EXIT FROM CAMPUS", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS7", "PHYSICAL ALTERCATION / FIGHTING", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS8", "NOISE MAKING AND CLASSROOM DISTURBANCE", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS9", "CONTRABAND ITEMS POSSESSION", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS10", "DEFACING SCHOOL PROPERTY / VANDALISM", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline"),
        ComplianceCheck("DIS11", "Lateness to school", "Discipline", ComplianceDivision.DISCIPLINE, "Discipline")
    )

    val allBuiltInChecks: List<ComplianceCheck> =
        cdsChecks + academicChecks + transportChecks + maintenanceChecks +
            hostelKitchenChecks + mindersChecks + securityChecks + generalOperationChecks + disciplineChecks

    val operationSubScopes = listOf(
        "Maintenance",
        "Transport",
        "Minders",
        "Security",
        "Hostel / Kitchen"
    )

    val academicDepartments = listOf(
        "All Departments",
        "Languages",
        "Life Sciences",
        "Physical Sciences",
        "Social Sciences"
    )

    val noneClassroom = Classroom(
        id = "NONE",
        name = "None",
        section = "All Tiers"
    )

    val academicClassTiers = listOf(
        "All Tiers",
        "Nursery & KG",
        "Junior Primary",
        "Senior Primary",
        "Junior Secondary",
        "Senior Secondary"
    )

    val fleetBuses: List<String> = (1..30).map { "Bus $it" }

    val defaultClasses = listOf(
        Classroom("cls-creche", "Creche", "Nursery & KG"),
        Classroom("cls-nur-1", "Nursery 1", "Nursery & KG"),
        Classroom("cls-nur-2", "Nursery 2", "Nursery & KG"),
        Classroom("cls-nur-3", "Nursery 3", "Nursery & KG"),
        Classroom("cls-kg-1", "KG 1", "Nursery & KG"),
        Classroom("cls-kg-2", "KG 2", "Nursery & KG"),
        Classroom("cls-b1-gold", "Basic 1 Gold", "Junior Primary"),
        Classroom("cls-b1-diamond", "Basic 1 Diamond", "Junior Primary"),
        Classroom("cls-b1-silver", "Basic 1 Silver", "Junior Primary"),
        Classroom("cls-b2-gold", "Basic 2 Gold", "Junior Primary"),
        Classroom("cls-b2-diamond", "Basic 2 Diamond", "Junior Primary"),
        Classroom("cls-b2-silver", "Basic 2 Silver", "Junior Primary"),
        Classroom("cls-b3-gold", "Basic 3 Gold", "Junior Primary"),
        Classroom("cls-b3-diamond", "Basic 3 Diamond", "Junior Primary"),
        Classroom("cls-b3-silver", "Basic 3 Silver", "Junior Primary"),
        Classroom("cls-b4-gold", "Basic 4 Gold", "Senior Primary"),
        Classroom("cls-b4-diamond", "Basic 4 Diamond", "Senior Primary"),
        Classroom("cls-b4-silver", "Basic 4 Silver", "Senior Primary"),
        Classroom("cls-b5-gold", "Basic 5 Gold", "Senior Primary"),
        Classroom("cls-b5-diamond", "Basic 5 Diamond", "Senior Primary"),
        Classroom("cls-b5-silver", "Basic 5 Silver", "Senior Primary"),
        Classroom("cls-b6-gold", "Basic 6 Gold", "Senior Primary"),
        Classroom("cls-b6-diamond", "Basic 6 Diamond", "Senior Primary"),
        Classroom("cls-b6-silver", "Basic 6 Silver", "Senior Primary"),
        Classroom("cls-jss1-gold", "JSS 1 Gold", "Junior Secondary"),
        Classroom("cls-jss1-diamond", "JSS 1 Diamond", "Junior Secondary"),
        Classroom("cls-jss1-silver", "JSS 1 Silver", "Junior Secondary"),
        Classroom("cls-jss2-gold", "JSS 2 Gold", "Junior Secondary"),
        Classroom("cls-jss2-diamond", "JSS 2 Diamond", "Junior Secondary"),
        Classroom("cls-jss2-silver", "JSS 2 Silver", "Junior Secondary"),
        Classroom("cls-jss3-gold", "JSS 3 Gold", "Junior Secondary"),
        Classroom("cls-jss3-diamond", "JSS 3 Diamond", "Junior Secondary"),
        Classroom("cls-jss3-silver", "JSS 3 Silver", "Junior Secondary"),
        Classroom("cls-ss1-gold", "SS 1 Gold", "Senior Secondary"),
        Classroom("cls-ss1-diamond", "SS 1 Diamond", "Senior Secondary"),
        Classroom("cls-ss1-silver", "SS 1 Silver", "Senior Secondary"),
        Classroom("cls-ss2-gold", "SS 2 Gold", "Senior Secondary"),
        Classroom("cls-ss2-diamond", "SS 2 Diamond", "Senior Secondary"),
        Classroom("cls-ss2-silver", "SS 2 Silver", "Senior Secondary"),
        Classroom("cls-ss3-gold", "SS 3 Gold", "Senior Secondary"),
        Classroom("cls-ss3-diamond", "SS 3 Diamond", "Senior Secondary"),
        Classroom("cls-ss3-silver", "SS 3 Silver", "Senior Secondary")
    )

    val defaultSubjects = listOf(
        SubjectItem("sub-math", "Mathematics", "Academic"),
        SubjectItem("sub-eng", "English Language", "Academic"),
        SubjectItem("sub-sci", "Basic Science", "Academic"),
        SubjectItem("sub-tech", "Basic Technology", "Academic"),
        SubjectItem("sub-phy", "Physics", "Academic"),
        SubjectItem("sub-chem", "Chemistry", "Academic"),
        SubjectItem("sub-bio", "Biology", "Academic"),
        SubjectItem("sub-comp", "Computer Studies / ICT", "Academic"),
        SubjectItem("sub-civic", "Civic Education", "Academic"),
        SubjectItem("sub-econ", "Economics", "Academic"),
        SubjectItem("sub-gov", "Government", "Academic"),
        SubjectItem("sub-lit", "Literature in English", "Academic"),
        SubjectItem("sub-acct", "Financial Accounting", "Academic"),
        SubjectItem("sub-comm", "Commerce", "Academic"),
        SubjectItem("sub-agric", "Agricultural Science", "Academic"),
        SubjectItem("sub-crs", "Christian Religious Studies", "Academic"),
        SubjectItem("sub-soc", "Social Studies", "Academic"),
        SubjectItem("sub-phe", "Physical & Health Education", "Academic"),
        SubjectItem("sub-cca", "Cultural & Creative Arts", "Academic"),
        SubjectItem("sub-fren", "French", "Academic"),
        SubjectItem("sub-yor", "Yoruba / Indigenous Language", "Academic"),
        SubjectItem("sub-quant", "Quantitative Reasoning", "Academic"),
        SubjectItem("sub-verbal", "Verbal Reasoning", "Academic")
    )

    fun getApplicableChecks(
        division: ComplianceDivision,
        operationScope: String = "",
        customChecks: List<ComplianceCheck> = emptyList()
    ): List<ComplianceCheck> {
        val base = when (division) {
            ComplianceDivision.CDS -> cdsChecks
            ComplianceDivision.ACADEMIC -> academicChecks
            ComplianceDivision.DISCIPLINE -> disciplineChecks
            ComplianceDivision.OPERATIONS -> {
                val scope = operationScope.lowercase()
                when {
                    scope.contains("transport") -> transportChecks + generalOperationChecks
                    scope.contains("maint") -> maintenanceChecks + generalOperationChecks
                    scope.contains("secur") -> securityChecks + generalOperationChecks
                    scope.contains("minder") -> mindersChecks + generalOperationChecks
                    scope.contains("hostel") || scope.contains("kitchen") -> hostelKitchenChecks + generalOperationChecks
                    else -> transportChecks + maintenanceChecks + hostelKitchenChecks + mindersChecks + securityChecks + generalOperationChecks
                }
            }
        }
        val extra = customChecks.filter { it.division == division }
        return base + extra
    }
}
