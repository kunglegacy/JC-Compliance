package com.example.data.local

import com.example.data.model.ComplianceDivision

data class AutoFillResult(
    val exceptionText: String,
    val effectText: String
)

object ComplianceAutoFillEngine {

    fun generate(
        division: ComplianceDivision,
        targetType: String = "staff",
        checkId: String = "",
        checkName: String = "",
        staffName: String = "",
        className: String = "",
        subjectName: String = "",
        driverName: String = "",
        selectedBus: String = "",
        minderName: String = "",
        minderLocation: String = "",
        securityName: String = "",
        maintenanceLocation: String = "",
        studentNamesArm: String = "",
        operationScope: String = ""
    ): AutoFillResult {
        val qCheck = (checkName.ifBlank { checkId }).trim().lowercase()
        val teacher = staffName.trim().ifBlank { "Assigned Teacher" }
        val cls = className.trim().ifBlank { "Classroom" }
        val subj = subjectName.trim().ifBlank { "Subject" }
        val driver = driverName.trim().ifBlank { "Driver" }
        val bus = selectedBus.trim().ifBlank { "Fleet Bus" }
        val minder = minderName.trim().ifBlank { "Minder" }
        val minderLoc = minderLocation.trim().ifBlank { "Early Years Wing" }
        val guard = securityName.trim().ifBlank { "Security Officer" }
        val maintLoc = maintenanceLocation.trim().ifBlank { "Facility Area" }
        val students = formatStudentList(studentNamesArm)

        return when (division) {
            ComplianceDivision.DISCIPLINE -> {
                if (targetType == "division") {
                    when {
                        qCheck.contains("lateness to school") || checkId == "DIS11" -> AutoFillResult(
                            if (students.isNotBlank()) "Lateness to school after official assembly time by: $students."
                            else "This set of students arrived late to school after official morning assembly time.",
                            "Disrupts morning assembly routines and delays first-period instructional start time."
                        )
                        qCheck.contains("assembly") || checkId == "DIS2" -> AutoFillResult(
                            if (students.isNotBlank()) "Lateness to morning assembly observed for: $students."
                            else "Students arrived late to morning assembly procession.",
                            "Missed morning devotions and vital institutional announcements."
                        )
                        qCheck.contains("loiter") || checkId == "DIS3" -> AutoFillResult(
                            if (students.isNotBlank()) "Observed loitering during class hours without exit cards: $students."
                            else "Students observed loitering along school corridors during active instructional hours.",
                            "Loss of valuable class time and corridor noise disruption."
                        )
                        qCheck.contains("bully") || checkId == "DIS4" -> AutoFillResult(
                            if (students.isNotBlank()) "Bullying and harassment incident reported involving: $students."
                            else "Incident of student intimidation and harassment reported on campus.",
                            "Compromises student emotional safety and peaceful peer environment."
                        )
                        qCheck.contains("dressing") || qCheck.contains("grooming") || checkId == "DIS5" -> AutoFillResult(
                            if (students.isNotBlank()) "Non-compliance with uniform and hair grooming standards by: $students."
                            else "Improper school uniform attire and hair grooming observed.",
                            "Undermines institutional dress code policy and personal discipline."
                        )
                        qCheck.contains("unauthorized exit") || checkId == "DIS6" -> AutoFillResult(
                            if (students.isNotBlank()) "Unauthorized exit from campus perimeter attempted by: $students."
                            else "Students attempted or executed unauthorized departure from campus premises.",
                            "Creates severe security risk and student safety hazard."
                        )
                        qCheck.contains("fight") || qCheck.contains("altercation") || checkId == "DIS7" -> AutoFillResult(
                            if (students.isNotBlank()) "Physical altercation and fighting engaged on campus by: $students."
                            else "Physical altercation engaged between students on school grounds.",
                            "Presents physical injury risk and breach of campus peace."
                        )
                        qCheck.contains("noise") || qCheck.contains("disturbance") || checkId == "DIS8" -> AutoFillResult(
                            if (students.isNotBlank()) "Excessive noise making and classroom disturbance caused by: $students."
                            else "Uncontrolled noise making and classroom disturbance recorded.",
                            "Distracts ongoing instruction and hampers peer concentration."
                        )
                        qCheck.contains("contraband") || checkId == "DIS9" -> AutoFillResult(
                            if (students.isNotBlank()) "Possession of unauthorized contraband items detected with: $students."
                            else "Possession of prohibited contraband items detected during inspection.",
                            "Breaches campus safety regulations and student conduct rules."
                        )
                        qCheck.contains("defacing") || qCheck.contains("vandalism") || checkId == "DIS10" -> AutoFillResult(
                            if (students.isNotBlank()) "Defacing or damaging school property committed by: $students."
                            else "Damage or defacement of school property observed.",
                            "Causes facility repair expense and degrades campus environment."
                        )
                        else -> AutoFillResult(
                            if (students.isNotBlank()) "Violation of student code of conduct observed involving: $students."
                            else "Student code of conduct violation observed on campus.",
                            "Breaches behavioral decorum standards and institutional discipline."
                        )
                    }
                } else {
                    AutoFillResult(
                        "$teacher failed to execute assigned student conduct monitoring duty effectively.",
                        "Leaves student decorum unsupervised during active monitoring hours."
                    )
                }
            }

            ComplianceDivision.ACADEMIC -> when {
                qCheck.contains("unprofessional misconduct") || checkId == "ACA1" -> AutoFillResult(
                    "Unprofessional conduct exhibited by $teacher during $subj period in $cls.",
                    "Lowers instructional professionalism and sets a poor exemplary standard."
                )
                qCheck.contains("behind in the scheme") || checkId == "ACA2" -> AutoFillResult(
                    "$teacher is lagging behind the approved scheme of work for $subj in $cls.",
                    "Delayed syllabus coverage risks student exam readiness."
                )
                qCheck.contains("ahead in the scheme") || checkId == "ACA3" -> AutoFillResult(
                    "$teacher is rushing ahead of syllabus pacing without proper topic consolidation in $cls ($subj).",
                    "Students risk inadequate comprehension of foundational concepts."
                )
                qCheck.contains("no scheme given") || checkId == "ACA4" -> AutoFillResult(
                    "$teacher has not issued course scheme outlines to students in $cls for $subj.",
                    "Students lack learning roadmap and syllabus expectations."
                )
                qCheck.contains("unmarked assignment") || checkId == "ACA5" -> AutoFillResult(
                    "$teacher has pending unmarked assignment scripts for $subj in $cls.",
                    "Delays student learning feedback and continuous assessment records."
                )
                qCheck.contains("dirty") || qCheck.contains("disorganised") || checkId == "ACA6" -> AutoFillResult(
                    "Classroom found untidy and disorganised during $teacher's lesson in $cls.",
                    "Creates an unhygienic and distracting learning environment."
                )
                qCheck.contains("faint maker") || qCheck.contains("faint marker") || checkId == "ACA7" -> AutoFillResult(
                    "$teacher observed using a faint whiteboard marker during $subj instruction in $cls.",
                    "Impairs whiteboard legibility for students seated at the back."
                )
                qCheck.contains("poorly written") || qCheck.contains("poor note") || checkId == "ACA8" -> AutoFillResult(
                    "Substandard quality of lesson notes provided by $teacher for $subj in $cls.",
                    "Compromises student study material and revision accuracy."
                )
                qCheck.contains("lateness to classroom") || checkId == "ACA9" -> AutoFillResult(
                    "$teacher arrived late to $cls for scheduled $subj instruction.",
                    "Reduces effective instructional time and creates idle class window."
                )
                qCheck.contains("lateness in teaching") || checkId == "ACA10" -> AutoFillResult(
                    "Delayed start of actual teaching delivery by $teacher in $cls ($subj).",
                    "Loss of core instructional minutes during period allocation."
                )
                qCheck.contains("locked classroom door") || checkId == "ACA11" -> AutoFillResult(
                    "Classroom door locked during active timetable teaching hour in $cls.",
                    "Restricts inspection access and prevents student entry."
                )
                qCheck.contains("specific objectives") || checkId == "ACA12" -> AutoFillResult(
                    "$teacher failed to write clear lesson objectives on the board for $subj in $cls.",
                    "Lacks instructional focus and learning goal clarity."
                )
                qCheck.contains("flexible grouping") || checkId == "ACA13" -> AutoFillResult(
                    "Flexible student learning grouping not implemented by $teacher in $cls ($subj).",
                    "Neglects varied student learning pace and interactive participation."
                )
                qCheck.contains("incomplete note") || checkId == "ACA14" -> AutoFillResult(
                    "Students in $cls found with incomplete lesson notes for $subj taught by $teacher.",
                    "Leaves students unprepared for continuous assessments and exams."
                )
                qCheck.contains("improper dressing") || checkId == "ACA17" -> AutoFillResult(
                    "Non-compliant professional attire worn by $teacher while on duty.",
                    "Violates staff grooming code and professional standards."
                )
                qCheck.contains("poor class control") || checkId == "ACA18" -> AutoFillResult(
                    "Ineffective classroom control and noise management by $teacher in $cls ($subj).",
                    "Disrupts learning focus and neighboring classroom activities."
                )
                qCheck.contains("marking of notes in class") || checkId == "ACA19" -> AutoFillResult(
                    "$teacher spent active teaching period marking notebooks instead of delivering instruction in $cls.",
                    "Wastes valuable teaching minutes on administrative grading."
                )
                qCheck.contains("sitting down") || checkId == "ACA20" -> AutoFillResult(
                    "$teacher remained seated continuously throughout active lesson delivery in $cls.",
                    "Reduces teacher enthusiasm and class engagement."
                )
                qCheck.contains("absence from class") || checkId == "ACA21" -> AutoFillResult(
                    "$teacher absent from scheduled $subj lesson period in $cls.",
                    "Leaves class unattended and halts curriculum progress."
                )
                qCheck.contains("unmarked notes") || checkId == "ACA22" -> AutoFillResult(
                    "Accumulation of unmarked student notebooks in $cls for $subj ($teacher).",
                    "Prevents error correction and delays learning feedback."
                )
                qCheck.contains("sleeping") || checkId == "ACA23" -> AutoFillResult(
                    "Multiple students found sleeping during $teacher's $subj lesson in $cls.",
                    "Indicates low lesson engagement and poor student alertness."
                )
                qCheck.contains("distracted") || checkId == "ACA24" -> AutoFillResult(
                    "Students engaged in non-lesson distractions during $subj in $cls.",
                    "Compromises learning retention and class discipline."
                )
                qCheck.contains("no topic") || checkId == "ACA25" -> AutoFillResult(
                    "No lesson topic or specific objectives written on board by $teacher in $cls.",
                    "Disorients students regarding the day's lesson focus."
                )
                qCheck.contains("invigilation") || checkId == "ACA26" -> AutoFillResult(
                    "$teacher arrived late for scheduled examination invigilation duty.",
                    "Delays exam commencement and causes student anxiety."
                )
                (qCheck.contains("exam") && qCheck.contains("distraction")) || checkId == "ACA27" -> AutoFillResult(
                    "Invigilator $teacher distracted during active examination supervision.",
                    "Risks examination integrity and supervisory vigilance."
                )
                qCheck.contains("beating") || checkId == "ACA28" -> AutoFillResult(
                    "Corporal punishment administered by $teacher against student in $cls.",
                    "Violates child protection policy and school safety standards."
                )
                qCheck.contains("unstandardised") || checkId == "ACA29" -> AutoFillResult(
                    "Non-standardized or flawed examination questions submitted by $teacher for $subj.",
                    "Compromises assessment validity and grading accuracy."
                )
                qCheck.contains("regimented plan") || checkId == "ACA30" -> AutoFillResult(
                    "$teacher departed from approved lesson plan structure in $cls.",
                    "Inconsistent delivery weakens standardized quality control."
                )
                else -> AutoFillResult(
                    "Academic compliance exception noted for $teacher in $cls ($subj).",
                    "Impacts instructional quality and academic delivery."
                )
            }

            ComplianceDivision.OPERATIONS -> {
                val scope = operationScope.lowercase()
                when {
                    scope.contains("transport") -> when {
                        qCheck.contains("tyre") || checkId == "OPR1" -> AutoFillResult(
                            "Worn out or improperly inflated tyres inspected on $bus (Driver: $driver).",
                            "Presents a road safety hazard during student transit."
                        )
                        qCheck.contains("window") || checkId == "OPR2" -> AutoFillResult(
                            "Faulty window latch or damaged glass observed on $bus.",
                            "Risks passenger comfort and transit safety."
                        )
                        qCheck.contains("body work") || checkId == "OPR3" -> AutoFillResult(
                            "Body work damage, dent, or paint scrape observed on $bus.",
                            "Diminishes fleet aesthetic standard and structural condition."
                        )
                        qCheck.contains("light") || checkId == "OPR4" -> AutoFillResult(
                            "Non-functional headlight, brake light, or indicator on $bus.",
                            "Reduces night visibility and signals vehicle defect to traffic."
                        )
                        qCheck.contains("interior") || checkId == "OPR5" -> AutoFillResult(
                            "Untidy seat covers or litter inside $bus passenger cabin.",
                            "Compromises passenger hygiene and travel comfort."
                        )
                        qCheck.contains("lateness") || checkId == "OPR6" -> AutoFillResult(
                            "$bus delayed during official pick-up or drop-off route (Driver: $driver).",
                            "Causes late arrival of students for morning assembly."
                        )
                        qCheck.contains("breakdown") || checkId == "OPR7" -> AutoFillResult(
                            "Mechanical breakdown experienced by $bus while on route.",
                            "Strands transit students and requires emergency backup vehicle."
                        )
                        qCheck.contains("accident") || checkId == "OPR8" -> AutoFillResult(
                            "Minor transit collision or vehicle scrape involving $bus.",
                            "Requires immediate vehicle safety inspection and route report."
                        )
                        qCheck.contains("extinguisher") || checkId == "OPR9" -> AutoFillResult(
                            "Missing or expired fire extinguisher in $bus.",
                            "Violates transport fire safety regulations."
                        )
                        qCheck.contains("jack") || checkId == "OPR10" -> AutoFillResult(
                            "Missing hydraulic jack or emergency wheel kit in $bus.",
                            "Delays roadside recovery during flat tyre emergency."
                        )
                        qCheck.contains("licence") || qCheck.contains("license") || checkId == "OPR11" -> AutoFillResult(
                            "Driver $driver operating $bus with unverified driver's licence.",
                            "Breaches legal traffic regulations and institutional policy."
                        )
                        qCheck.contains("dressing") || checkId == "OPR12" -> AutoFillResult(
                            "Driver $driver non-compliant with official transport uniform code.",
                            "Undermines institutional transport representation."
                        )
                        else -> AutoFillResult(
                            "Transport fleet compliance exception noted for $bus (Driver: $driver).",
                            "Affects transport fleet efficiency and transit safety."
                        )
                    }

                    scope.contains("maint") -> when {
                        qCheck.contains("door") || checkId == "OPR13" -> AutoFillResult(
                            "Damaged door lock, handle, or hinges observed at $maintLoc.",
                            "Compromises room security and facility privacy."
                        )
                        qCheck.contains("window") || checkId == "OPR14" -> AutoFillResult(
                            "Broken window louvre or loose frame detected at $maintLoc.",
                            "Exposes room interior to weather elements and safety risk."
                        )
                        qCheck.contains("table") || checkId == "OPR15" -> AutoFillResult(
                            "Unstable or damaged study tables found at $maintLoc.",
                            "Hinders student writing posture and study comfort."
                        )
                        qCheck.contains("chair") || checkId == "OPR16" -> AutoFillResult(
                            "Broken or defective chairs inspected at $maintLoc.",
                            "Presents seating discomfort and physical injury risk."
                        )
                        qCheck.contains("tile") || checkId == "OPR17" -> AutoFillResult(
                            "Cracked or chipped floor tiles noted at $maintLoc.",
                            "Creates trip hazard and compromises floor aesthetics."
                        )
                        qCheck.contains("light") || checkId == "OPR18" -> AutoFillResult(
                            "Flickering or burnt out light fitting at $maintLoc.",
                            "Reduces room illumination for reading and work."
                        )
                        qCheck.contains("fan") || checkId == "OPR19" -> AutoFillResult(
                            "Non-functional or noisy ceiling fan at $maintLoc.",
                            "Causes thermal discomfort during warm hours."
                        )
                        qCheck.contains("flower") || checkId == "OPR20" -> AutoFillResult(
                            "Unkempt or overgrown landscaping flowers around $maintLoc.",
                            "Degrades campus green scenery and grounds neatness."
                        )
                        qCheck.contains("tidiness") || checkId == "OPR21" -> AutoFillResult(
                            "Litter or clutter accumulated at $maintLoc.",
                            "Undermines campus cleanliness and sanitation standards."
                        )
                        qCheck.contains("pipe") || checkId == "OPR22" -> AutoFillResult(
                            "Leaking water pipe or plumbing fault at $maintLoc.",
                            "Causes water wastage and floor dampness."
                        )
                        qCheck.contains("kerb") || checkId == "OPR23" -> AutoFillResult(
                            "Chipped or faded walkway kerb painting around $maintLoc.",
                            "Reduces campus perimeter visual appeal."
                        )
                        qCheck.contains("ac") || checkId == "OPR24" -> AutoFillResult(
                            "Air conditioning unit malfunctioning or leaking water at $maintLoc.",
                            "Impairs indoor climate control and comfort."
                        )
                        else -> AutoFillResult(
                            "Facility maintenance defect inspected at $maintLoc.",
                            "Requires prompt facility repair dispatch."
                        )
                    }

                    scope.contains("hostel") || scope.contains("kitchen") -> when {
                        qCheck.contains("promptness") || checkId == "OPR25" -> AutoFillResult(
                            "Hostel boarders delayed in leaving quarters for morning classes.",
                            "Causes student lateness to first-period lesson."
                        )
                        qCheck.contains("table etiquette") || checkId == "OPR26" -> AutoFillResult(
                            "Poor dining table manners and food spillage during meal service.",
                            "Substandard dining etiquette and food wastage."
                        )
                        qCheck.contains("hot water") || checkId == "OPR27" -> AutoFillResult(
                            "Interrupted hot water supply in hostel bathing facilities.",
                            "Causes boarder bathing inconvenience during morning prep."
                        )
                        qCheck.contains("drinking") || qCheck.contains("water") || checkId == "OPR28" -> AutoFillResult(
                            "Water supply disruption or clarity defect in hostel quarters.",
                            "Compromises essential sanitation and boarder welfare."
                        )
                        qCheck.contains("cleanliness") || checkId == "OPR29" -> AutoFillResult(
                            "Substandard cleanliness in hostel dormitories or kitchen area.",
                            "Risks hygiene standards and student health."
                        )
                        qCheck.contains("siesta") || checkId == "OPR30" -> AutoFillResult(
                            "Lax siesta quiet time supervision in boarding hostel.",
                            "Deprives boarders of mandatory afternoon rest."
                        )
                        else -> AutoFillResult(
                            "Hostel/Kitchen operational exception noted during inspection.",
                            "Impacts boarding welfare and kitchen sanitation."
                        )
                    }

                    scope.contains("minder") -> AutoFillResult(
                        "$minder inattentive or absent from station during pupil care duty at $minderLoc.",
                        "Leaves early-years pupils unsupervised."
                    )

                    scope.contains("secur") -> when {
                        qCheck.contains("post desertion") || qCheck.contains("absent") || checkId == "SEC1" -> AutoFillResult(
                            "$guard deserted assigned security post without relief cover.",
                            "Leaves campus access point completely unmonitored."
                        )
                        qCheck.contains("gate") || qCheck.contains("logbook") || checkId == "SEC2" -> AutoFillResult(
                            "Visitor entry logbook unmaintained or gate left unbolted by $guard.",
                            "Risks unauthorized visitor access onto campus grounds."
                        )
                        qCheck.contains("patrol") || checkId == "SEC3" -> AutoFillResult(
                            "Routine perimeter security patrol missed during shift by $guard.",
                            "Creates security blind spots along boundary wall."
                        )
                        qCheck.contains("sleeping") || checkId == "SEC4" -> AutoFillResult(
                            "Security officer $guard found sleeping while on shift.",
                            "Severely compromises night campus vigilance and security."
                        )
                        else -> AutoFillResult(
                            "Security compliance exception observed at guard post ($guard).",
                            "Compromises overall campus security posture."
                        )
                    }

                    qCheck.contains("dressing") || checkId == "OPR33" -> AutoFillResult(
                        "Staff member non-compliant with official operational dress code.",
                        "Undermines institutional presentation standards."
                    )

                    qCheck.contains("effectiveness") || checkId == "OPR34" -> AutoFillResult(
                        "Delay or low productivity in executing assigned operational tasks.",
                        "Slows down operational service delivery and turnaround time."
                    )

                    else -> AutoFillResult(
                        "Operational compliance exception noted during facility inspection.",
                        "Impacts smooth operational workflow and campus standards."
                    )
                }
            }

            ComplianceDivision.CDS -> when {
                qCheck.contains("cbt") || checkId == "CDS1" -> AutoFillResult(
                    "Computer-Based Test lab network connectivity or terminal glitch during exam session.",
                    "Interrupts student exam session pacing and testing duration."
                )
                qCheck.contains("camera") || checkId == "CDS7" -> AutoFillResult(
                    "CCTV surveillance camera offline or camera lens obscured.",
                    "Creates security monitoring blind spot in campus footage."
                )
                qCheck.contains("computer") || checkId == "CDS8" -> AutoFillResult(
                    "Workstation hardware malfunction or uninstalled required software.",
                    "Delays administrative or academic computing tasks."
                )
                qCheck.contains("projector") || checkId == "CDS_PROJ" -> AutoFillResult(
                    "Classroom projector lamp faulty or display signal desynced.",
                    "Hinders audiovisual lesson projection during class."
                )
                qCheck.contains("result portal") || checkId == "CDS9" -> AutoFillResult(
                    "Result processing portal sync error or database slow response.",
                    "Delays student report card publishing timeline."
                )
                qCheck.contains("exam records") || checkId == "CDS5" -> AutoFillResult(
                    "Incomplete digital score backup or missing grade entry in CDS exam database.",
                    "Risks score record accuracy and record integrity."
                )
                qCheck.contains("staff records") || checkId == "CDS6" -> AutoFillResult(
                    "Outdated or unverified personnel profile records on CDS database.",
                    "Affects staff database accuracy and administrative tracking."
                )
                qCheck.contains("biometric") || checkId == "CDS11" -> AutoFillResult(
                    "Biometric attendance scanner device unresponsive or time desynced.",
                    "Inaccurate staff clock-in attendance logging."
                )
                else -> AutoFillResult(
                    "CDS technological infrastructure exception observed during audit.",
                    "Affects digital system reliability and network operations."
                )
            }
        }
    }

    private fun formatStudentList(raw: String): String {
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return when (parts.size) {
            0 -> ""
            1 -> parts[0]
            2 -> "${parts[0]}, and ${parts[1]}"
            else -> "${parts.dropLast(1).joinToString(", ")}, and ${parts.last()}"
        }
    }
}
