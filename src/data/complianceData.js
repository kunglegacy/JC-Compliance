/**
 * JC-Best Compliance Hub — Authoritative Compliance Taxonomy & Hierarchy
 * 
 * Hierarchy:
 * DIVISION (CDS | Academic | Operations)
 *     ↓
 * COMPLIANCE AREA / GROUP
 *     ↓
 * COMPLIANCE CHECK
 *     ↓
 * EXCEPTION (Recorded with user-designated Severity: Low | Medium | High | Critical)
 * 
 * Safety & Architecture Rules:
 * 1. Top-level divisions: CDS, Academic, Operations.
 * 2. Source terminology is strictly preserved from the Directorate orientation document.
 * 3. No extra checks invented.
 * 4. Checks do NOT have predefined severity attached (severity is designated per exception).
 * 5. No manual compliant records generated.
 */

// =========================================================================
// 1. TOP-LEVEL REPORTING DIVISIONS
// =========================================================================
export const COMPLIANCE_DIVISIONS = [
  {
    id: 'academic',
    name: 'Academic',
    code: 'ACADEMIC',
    description: 'Academic instruction, classroom governance, staff duty, and instructional quality.',
  },
  {
    id: 'cds',
    name: 'CDS',
    code: 'CDS',
    description: 'Corporate Digital Services and technological infrastructure compliance.',
  },
  {
    id: 'operations',
    name: 'Operation',
    code: 'OPERATION',
    description: 'Core school operations, campus facilities, transport fleet, minders, and hostels.',
  },
  {
    id: 'discipline',
    name: 'Discipline',
    code: 'DISCIPLINE',
    description: 'Student discipline, behavioral monitoring, and student code of conduct enforcement.',
  },
];

// =========================================================================
// 2. AUTHORITATIVE COMPLIANCE AREAS & CHECKS
// =========================================================================
export const COMPLIANCE_AREAS = [
  // -----------------------------------------------------------------------
  // DIVISION: CDS
  // -----------------------------------------------------------------------
  {
    id: 'cds',
    name: 'CDS',
    division: 'CDS',
    description: 'Corporate Digital Services and technological infrastructure compliance.',
    checks: [
      { id: 'CDS1', code: 'CDS1', name: 'CBT', division: 'CDS', group: 'CBT', legacyId: 'cds-cbt' },
      { id: 'CDS7', code: 'CDS7', name: 'Camera', division: 'CDS', group: 'Camera', legacyId: 'cds-camera' },
      { id: 'CDS8', code: 'CDS8', name: 'Computer', division: 'CDS', group: 'Computer', legacyId: 'cds-printer' },
      { id: 'CDS_PROJ', code: 'CDS_PROJ', name: 'Projector', division: 'CDS', group: 'Projector', legacyId: 'cds-projector' },
      { id: 'CDS9', code: 'CDS9', name: 'Result Portal', division: 'CDS', group: 'Result Portal', legacyId: 'cds-result-portal' },
      { id: 'CDS5', code: 'CDS5', name: 'Exam Records', division: 'CDS', group: 'Exam Records', legacyId: 'cds-exam-records' },
      { id: 'CDS6', code: 'CDS6', name: 'Staff Records', division: 'CDS', group: 'Staff Records', legacyId: 'cds-staff-records' },
      { id: 'CDS11', code: 'CDS11', name: 'Biometric', division: 'CDS', group: 'Biometric', legacyId: 'cds-biometric' },
    ],
  },

  // -----------------------------------------------------------------------
  // DIVISION: ACADEMIC
  // -----------------------------------------------------------------------
  {
    id: 'academic',
    name: 'Academic',
    division: 'Academic',
    description: 'Academic instruction, classroom governance, lesson schemes, and teacher conduct.',
    checks: [
      { id: 'ACA1', code: 'ACA1', name: 'UNPROFESSIONAL MISCONDUCT OF TEACHERS', division: 'Academic', group: 'Academic', legacyId: 'academic-unprofessional-misconduct' },
      { id: 'ACA2', code: 'ACA2', name: 'BEHIND IN THE SCHEME OF WORK', division: 'Academic', group: 'Academic', legacyId: 'academic-behind-scheme' },
      { id: 'ACA3', code: 'ACA3', name: 'AHEAD IN THE SCHEME OF WORK', division: 'Academic', group: 'Academic', legacyId: 'academic-ahead-scheme' },
      { id: 'ACA4', code: 'ACA4', name: 'NO SCHEME GIVEN TO STUDENTS', division: 'Academic', group: 'Academic', legacyId: 'academic-no-scheme' },
      { id: 'ACA5', code: 'ACA5', name: 'UNMARKED ASSIGNMENT', division: 'Academic', group: 'Academic', legacyId: 'academic-unmarked-assignment' },
      { id: 'ACA6', code: 'ACA6', name: 'DIRTY AND DISORGANISED CLASSROOM', division: 'Academic', group: 'Academic', legacyId: 'academic-dirty-classroom' },
      { id: 'ACA7', code: 'ACA7', name: 'USING A FAINT MAKER', division: 'Academic', group: 'Academic', legacyId: 'academic-faint-maker' },
      { id: 'ACA8', code: 'ACA8', name: 'POORLY WRITTEN QUALITY NOTE/CONTENT', division: 'Academic', group: 'Academic', legacyId: 'academic-poor-note-quality' },
      { id: 'ACA9', code: 'ACA9', name: 'LATENESS TO CLASSROOM', division: 'Academic', group: 'Academic', legacyId: 'academic-lateness-classroom' },
      { id: 'ACA10', code: 'ACA10', name: 'LATENESS IN TEACHING', division: 'Academic', group: 'Academic', legacyId: 'academic-lateness-teaching' },
      { id: 'ACA11', code: 'ACA11', name: 'LOCKED CLASSROOM DOOR', division: 'Academic', group: 'Academic', legacyId: 'academic-locked-door' },
      { id: 'ACA12', code: 'ACA12', name: 'NON COMPLIANCE IN WRITING SPECIFIC OBJECTIVES', division: 'Academic', group: 'Academic', legacyId: 'academic-no-objectives-writing' },
      { id: 'ACA13', code: 'ACA13', name: 'NON COMPLIANCE TO FLEXIBLE GROUPING', division: 'Academic', group: 'Academic', legacyId: 'academic-no-flexible-grouping' },
      { id: 'ACA14', code: 'ACA14', name: 'CLASS HAVING INCOMPLETE NOTE', division: 'Academic', group: 'Academic', legacyId: 'academic-incomplete-note' },
      { id: 'ACA15', code: 'ACA15', name: 'EXCEPTION DIRECTLY TO ACADEMICS', division: 'Academic', group: 'Academic', legacyId: 'academic-exception-to-academics' },
      { id: 'ACA16', code: 'ACA16', name: 'PENDING DISCIPLINARY CASES', division: 'Academic', group: 'Academic', legacyId: 'academic-disciplinary-cases' },
      { id: 'ACA17', code: 'ACA17', name: 'IMPROPER DRESSING', division: 'Academic', group: 'Academic', legacyId: 'academic-improper-dressing' },
      { id: 'ACA18', code: 'ACA18', name: 'POOR CLASS CONTROL AND MANAGEMENT', division: 'Academic', group: 'Academic', legacyId: 'academic-poor-class-control' },
      { id: 'ACA19', code: 'ACA19', name: 'MARKING OF NOTES IN CLASS', division: 'Academic', group: 'Academic', legacyId: 'academic-marking-notes-in-class' },
      { id: 'ACA20', code: 'ACA20', name: 'SITTING DOWN DURING TUTORIAL DELIVERY', division: 'Academic', group: 'Academic', legacyId: 'academic-sitting-during-tutorial' },
      { id: 'ACA21', code: 'ACA21', name: 'ABSENCE FROM CLASS', division: 'Academic', group: 'Academic', legacyId: 'academic-absence-class' },
      { id: 'ACA22', code: 'ACA22', name: 'UNMARKED NOTES', division: 'Academic', group: 'Academic', legacyId: 'academic-unmarked-notes' },
      { id: 'ACA23', code: 'ACA23', name: 'STUDENTS SLEEPING DURING TUTORIALS', division: 'Academic', group: 'Academic', legacyId: 'academic-students-sleeping' },
      { id: 'ACA24', code: 'ACA24', name: 'STUDENTS DISTRACTED DURING TUTORIALS', division: 'Academic', group: 'Academic', legacyId: 'academic-students-distracted' },
      { id: 'ACA25', code: 'ACA25', name: 'NO TOPIC/SPECIFIC OBJECTIVES WRITTEN DOWN', division: 'Academic', group: 'Academic', legacyId: 'academic-no-topic-objectives' },
      { id: 'ACA26', code: 'ACA26', name: 'LATENESS FOR EXAM INVIGILATION', division: 'Academic', group: 'Academic', legacyId: 'academic-lateness-exam-invigilation' },
      { id: 'ACA27', code: 'ACA27', name: 'DISTRACTIONS DURING EXAMS(TRACKER, CA & QUALITY EXAM)', division: 'Academic', group: 'Academic', legacyId: 'academic-exam-distractions' },
      { id: 'ACA28', code: 'ACA28', name: 'BEATING OF STUDENTS/PUPILS', division: 'Academic', group: 'Academic', legacyId: 'academic-beating-students' },
      { id: 'ACA29', code: 'ACA29', name: 'UNSTANDARDISED EXAM REFERENCE QUESTION', division: 'Academic', group: 'Academic', legacyId: 'academic-unstandardised-exam' },
      { id: 'ACA30', code: 'ACA30', name: 'DEVIATION FROM REGIMENTED PLAN', division: 'Academic', group: 'Academic', legacyId: 'academic-deviation-regimented-plan' }
    ],
  },

  // -----------------------------------------------------------------------
  // DIVISION: OPERATIONS
  // -----------------------------------------------------------------------
  {
    id: 'transport',
    name: 'Transport',
    division: 'Operations',
    description: 'School fleet roadworthiness, driver credentials, and transit punctuality.',
    checks: [
      { id: 'OPR1', code: 'OPR1', name: 'Tyres', division: 'Operations', group: 'Transport', legacyId: 'transport-tyres' },
      { id: 'OPR2', code: 'OPR2', name: 'Windows', division: 'Operations', group: 'Transport', legacyId: 'transport-windows' },
      { id: 'OPR3', code: 'OPR3', name: 'Body Work', division: 'Operations', group: 'Transport', legacyId: 'transport-body-work' },
      { id: 'OPR4', code: 'OPR4', name: 'Lights', division: 'Operations', group: 'Transport', legacyId: 'transport-lights' },
      { id: 'OPR5', code: 'OPR5', name: 'Interior', division: 'Operations', group: 'Transport', legacyId: 'transport-interior' },
      { id: 'OPR6', code: 'OPR6', name: 'Pick-up/drop-off lateness', division: 'Operations', group: 'Transport', legacyId: 'transport-lateness' },
      { id: 'OPR7', code: 'OPR7', name: 'Breakdown', division: 'Operations', group: 'Transport', legacyId: 'transport-breakdown' },
      { id: 'OPR8', code: 'OPR8', name: 'Accident', division: 'Operations', group: 'Transport', legacyId: 'transport-accident' },
      { id: 'OPR9', code: 'OPR9', name: 'Fire extinguisher', division: 'Operations', group: 'Transport', legacyId: 'transport-fire-extinguisher' },
      { id: 'OPR10', code: 'OPR10', name: 'Jack', division: 'Operations', group: 'Transport', legacyId: 'transport-jack' },
      { id: 'OPR11', code: 'OPR11', name: 'Driving Licences', division: 'Operations', group: 'Transport', legacyId: 'transport-driving-licences' },
      { id: 'OPR12', code: 'OPR12', name: 'Dressing', division: 'Operations', group: 'Transport', legacyId: 'transport-dressing' },
    ],
  },
  {
    id: 'maintenance',
    name: 'Maintenance',
    division: 'Operations',
    description: 'Campus preventative maintenance, structural upkeep, fixtures, and repairs.',
    checks: [
      { id: 'OPR13', code: 'OPR13', name: 'Doors', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-doors' },
      { id: 'OPR14', code: 'OPR14', name: 'Windows', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-windows' },
      { id: 'OPR15', code: 'OPR15', name: 'Tables', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-tables' },
      { id: 'OPR16', code: 'OPR16', name: 'Chairs', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-chairs' },
      { id: 'OPR17', code: 'OPR17', name: 'Tiles', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-tiles' },
      { id: 'OPR18', code: 'OPR18', name: 'Lights', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-lights' },
      { id: 'OPR19', code: 'OPR19', name: 'Fans', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-fans' },
      { id: 'OPR20', code: 'OPR20', name: 'Flowers', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-flowers' },
      { id: 'OPR21', code: 'OPR21', name: 'Tidiness', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-tidiness' },
      { id: 'OPR22', code: 'OPR22', name: 'Pipes', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-pipes' },
      { id: 'OPR23', code: 'OPR23', name: 'Kerbs', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-kerbs' },
      { id: 'OPR24', code: 'OPR24', name: 'AC', division: 'Operations', group: 'Maintenance', legacyId: 'facilities-ac' },
      { id: 'OPR32', code: 'OPR32', name: 'Maintenance', division: 'Operations', group: 'Maintenance', legacyId: 'operations-maintenance' },
    ],
  },
  {
    id: 'hostel-kitchen',
    name: 'Hostel / Kitchen',
    division: 'Operations',
    description: 'Boarding quarters, dining etiquette, sanitation, utilities, and siesta protocol.',
    checks: [
      { id: 'OPR25', code: 'OPR25', name: 'Promptness to class from hostel', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-promptness' },
      { id: 'OPR26', code: 'OPR26', name: 'Table etiquette', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-table-etiquette' },
      { id: 'OPR27', code: 'OPR27', name: 'Hot water', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-hot-water' },
      { id: 'OPR28', code: 'OPR28', name: 'Drinking/using water', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-drinking-water' },
      { id: 'OPR29', code: 'OPR29', name: 'Cleanliness', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-cleanliness' },
      { id: 'OPR30', code: 'OPR30', name: 'Siesta enforcement', division: 'Operations', group: 'Hostel / Kitchen', legacyId: 'hostel-siesta' },
    ],
  },
  {
    id: 'minders',
    name: 'Minders',
    division: 'Operations',
    description: 'Early-years childcare, classroom assistant duty, and pupil minding.',
    checks: [
      { id: 'OPR31', code: 'OPR31', name: 'Minders', division: 'Operations', group: 'Minders', legacyId: 'operations-minders' },
    ],
  },
  {
    id: 'security',
    name: 'Security',
    division: 'Operations',
    description: 'Campus perimeter security, gate access, guard vigilance, and night patrols.',
    checks: [
      { id: 'SEC1', code: 'SEC1', name: 'Post Desertion / Absent from Duty', division: 'Operations', group: 'Security', legacyId: 'security-absent' },
      { id: 'SEC2', code: 'SEC2', name: 'Gate Control & Logbook Non-Compliance', division: 'Operations', group: 'Security', legacyId: 'security-gate' },
      { id: 'SEC3', code: 'SEC3', name: 'Perimeter Patrol Negligence', division: 'Operations', group: 'Security', legacyId: 'security-patrol' },
      { id: 'SEC4', code: 'SEC4', name: 'Sleeping on Duty', division: 'Operations', group: 'Security', legacyId: 'security-sleeping' },
    ],
  },
  {
    id: 'dressing',
    name: 'Dressing',
    division: 'Operations',
    description: 'Staff uniform adherence, footwear, identification, and grooming standards.',
    checks: [
      { id: 'OPR33', code: 'OPR33', name: 'Dressing', division: 'Operations', group: 'Dressing', legacyId: 'operations-dressing' },
    ],
  },
  {
    id: 'effectiveness',
    name: 'Effectiveness',
    division: 'Operations',
    description: 'Staff productivity, task execution speed, and operational effectiveness.',
    checks: [
      { id: 'OPR34', code: 'OPR34', name: 'Effectiveness', division: 'Operations', group: 'Effectiveness', legacyId: 'operations-effectiveness' },
    ],
  },
  // -----------------------------------------------------------------------
  // DIVISION: DISCIPLINE
  // -----------------------------------------------------------------------
  {
    id: 'discipline',
    name: 'Discipline',
    division: 'Discipline',
    description: 'Student code of conduct, behavioral decorum, and campus disciplinary monitoring.',
    checks: [
      { id: 'DIS1', code: 'DIS1', name: 'STUDENT CODE OF CONDUCT VIOLATION', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-student-conduct' },
      { id: 'DIS2', code: 'DIS2', name: 'LATENESS TO MORNING ASSEMBLY', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-lateness-assembly' },
      { id: 'DIS3', code: 'DIS3', name: 'LOITERING DURING CLASS HOURS/ SEEN OUTSIDE THE CLASS WITHOUT EXIT CARD', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-loitering' },
      { id: 'DIS4', code: 'DIS4', name: 'BULLYING AND HARASSMENT', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-bullying' },
      { id: 'DIS5', code: 'DIS5', name: 'STUDENT IMPROPER DRESSING & GROOMING', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-uniform' },
      { id: 'DIS6', code: 'DIS6', name: 'UNAUTHORIZED EXIT FROM CAMPUS', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-unauthorized-exit' },
      { id: 'DIS7', code: 'DIS7', name: 'PHYSICAL ALTERCATION / FIGHTING', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-fighting' },
      { id: 'DIS8', code: 'DIS8', name: 'NOISE MAKING AND CLASSROOM DISTURBANCE', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-noise-making' },
      { id: 'DIS9', code: 'DIS9', name: 'CONTRABAND ITEMS POSSESSION', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-contraband' },
      { id: 'DIS10', code: 'DIS10', name: 'DEFACING SCHOOL PROPERTY / VANDALISM', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-defacing-property' },
      { id: 'DIS11', code: 'DIS11', name: 'Lateness to school', division: 'Discipline', group: 'Discipline', legacyId: 'discipline-lateness-school' },
    ],
  },
];

// =========================================================================
// 3. COMPLETE STRUCTURED TAXONOMY MAPPING
// DIVISION → COMPLIANCE AREA / GROUP → COMPLIANCE CHECK
// =========================================================================
export const COMPLIANCE_HIERARCHY = {
  CDS: {
    division: 'CDS',
    name: 'Corporate Digital Services',
    areas: [
      { id: 'cds-cbt', name: 'CBT', checks: [{ id: 'cds-cbt', name: 'CBT' }] },
      { id: 'cds-camera', name: 'Camera', checks: [{ id: 'cds-camera', name: 'Camera' }] },
      { id: 'cds-computer', name: 'Computer', checks: [{ id: 'cds-computer', name: 'Computer' }] },
      { id: 'cds-projector', name: 'Projector', checks: [{ id: 'cds-projector', name: 'Projector' }] },
      { id: 'cds-result-portal', name: 'Result Portal', checks: [{ id: 'cds-result-portal', name: 'Result Portal' }] },
      { id: 'cds-exam-records', name: 'Exam Records', checks: [{ id: 'cds-exam-records', name: 'Exam Records' }] },
      { id: 'cds-staff-records', name: 'Staff Records', checks: [{ id: 'cds-staff-records', name: 'Staff Records' }] },
      { id: 'cds-biometric', name: 'Biometric', checks: [{ id: 'cds-biometric', name: 'Biometric' }] },
    ],
  },
  Academic: {
    division: 'Academic',
    name: 'Academic',
    areas: [
      {
        id: 'academic-staff',
        name: 'Academic Staff',
        checks: [
          { id: 'academic-unprofessional-misconduct', name: 'UNPROFESSIONAL MISCONDUCT OF TEACHERS' },
          { id: 'academic-behind-scheme', name: 'BEHIND IN THE SCHEME OF WORK' },
          { id: 'academic-ahead-scheme', name: 'AHEAD IN THE SCHEME OF WORK' },
          { id: 'academic-no-scheme', name: 'NO SCHEME GIVEN TO STUDENTS' },
          { id: 'academic-unmarked-assignment', name: 'UNMARKED ASSIGNMENT' },
          { id: 'academic-dirty-classroom', name: 'DIRTY AND DISORGANISED CLASSROOM' },
          { id: 'academic-faint-maker', name: 'USING A FAINT MAKER' },
          { id: 'academic-poor-note-quality', name: 'POORLY WRITTEN QUALITY NOTE/CONTENT' },
          { id: 'academic-lateness-classroom', name: 'LATENESS TO CLASSROOM' },
          { id: 'academic-lateness-teaching', name: 'LATENESS IN TEACHING' },
          { id: 'academic-locked-door', name: 'LOCKED CLASSROOM DOOR' },
          { id: 'academic-no-objectives-writing', name: 'NON COMPLIANCE IN WRITING SPECIFIC OBJECTIVES' },
          { id: 'academic-no-flexible-grouping', name: 'NON COMPLIANCE TO FLEXIBLE GROUPING' },
          { id: 'academic-incomplete-note', name: 'CLASS HAVING INCOMPLETE NOTE' },
          { id: 'academic-exception-to-academics', name: 'EXCEPTION DIRECTLY TO ACADEMICS' },
          { id: 'academic-disciplinary-cases', name: 'PENDING DISCIPLINARY CASES' },
          { id: 'academic-improper-dressing', name: 'IMPROPER DRESSING' },
          { id: 'academic-poor-class-control', name: 'POOR CLASS CONTROL AND MANAGEMENT' },
          { id: 'academic-marking-notes-in-class', name: 'MARKING OF NOTES IN CLASS' },
          { id: 'academic-sitting-during-tutorial', name: 'SITTING DOWN DURING TUTORIAL DELIVERY' },
          { id: 'academic-absence-class', name: 'ABSENCE FROM CLASS' },
          { id: 'academic-unmarked-notes', name: 'UNMARKED NOTES' },
          { id: 'academic-students-sleeping', name: 'STUDENTS SLEEPING DURING TUTORIALS' },
          { id: 'academic-students-distracted', name: 'STUDENTS DISTRACTED DURING TUTORIALS' },
          { id: 'academic-no-topic-objectives', name: 'NO TOPIC/SPECIFIC OBJECTIVES WRITTEN DOWN' },
          { id: 'academic-lateness-exam-invigilation', name: 'LATENESS FOR EXAM INVIGILATION' },
          { id: 'academic-exam-distractions', name: 'DISTRACTIONS DURING EXAMS(TRACKER, CA & QUALITY EXAM)' },
          { id: 'academic-beating-students', name: 'BEATING OF STUDENTS/PUPILS' },
          { id: 'academic-unstandardised-exam', name: 'UNSTANDARDISED EXAM REFERENCE QUESTION' },
          { id: 'academic-deviation-regimented-plan', name: 'DEVIATION FROM REGIMENTED PLAN' }
        ],
      },
    ],
  },
  Operations: {
    division: 'Operations',
    name: 'Operations',
    areas: [
      {
        id: 'transport',
        name: 'Transport',
        checks: [
          { id: 'transport-tyres', name: 'Tyres' },
          { id: 'transport-windows', name: 'Windows' },
          { id: 'transport-body-work', name: 'Body Work' },
          { id: 'transport-lights', name: 'Lights' },
          { id: 'transport-interior', name: 'Interior' },
          { id: 'transport-lateness', name: 'Pick-up/drop-off lateness' },
          { id: 'transport-breakdown', name: 'Breakdown' },
          { id: 'transport-accident', name: 'Accident' },
          { id: 'transport-fire-extinguisher', name: 'Fire extinguisher' },
          { id: 'transport-jack', name: 'Jack' },
          { id: 'transport-driving-licences', name: 'Driving Licences' },
          { id: 'transport-dressing', name: 'Dressing' },
        ],
      },
      {
        id: 'maintenance',
        name: 'Maintenance',
        checks: [
          { id: 'facilities-doors', name: 'Doors' },
          { id: 'facilities-windows', name: 'Windows' },
          { id: 'facilities-tables', name: 'Tables' },
          { id: 'facilities-chairs', name: 'Chairs' },
          { id: 'facilities-tiles', name: 'Tiles' },
          { id: 'facilities-lights', name: 'Lights' },
          { id: 'facilities-fans', name: 'Fans' },
          { id: 'facilities-flowers', name: 'Flowers' },
          { id: 'facilities-tidiness', name: 'Tidiness' },
          { id: 'facilities-pipes', name: 'Pipes' },
          { id: 'facilities-kerbs', name: 'Kerbs' },
          { id: 'facilities-ac', name: 'AC' },
          { id: 'operations-maintenance', name: 'Maintenance' },
        ],
      },
      {
        id: 'hostel-kitchen',
        name: 'Hostel / Kitchen',
        checks: [
          { id: 'hostel-promptness', name: 'Promptness to class from hostel' },
          { id: 'hostel-table-etiquette', name: 'Table etiquette' },
          { id: 'hostel-hot-water', name: 'Hot water' },
          { id: 'hostel-drinking-water', name: 'Drinking/using water' },
          { id: 'hostel-cleanliness', name: 'Cleanliness' },
          { id: 'hostel-siesta', name: 'Siesta enforcement' },
        ],
      },
      {
        id: 'minders',
        name: 'Minders',
        checks: [{ id: 'operations-minders', name: 'Minders' }],
      },
      {
        id: 'security',
        name: 'Security',
        checks: [
          { id: 'security-absent', name: 'Post Desertion / Absent from Duty' },
          { id: 'security-gate', name: 'Gate Control & Logbook Non-Compliance' },
          { id: 'security-patrol', name: 'Perimeter Patrol Negligence' },
          { id: 'security-sleeping', name: 'Sleeping on Duty' },
        ],
      },
      {
        id: 'dressing',
        name: 'Dressing',
        checks: [{ id: 'operations-dressing', name: 'Dressing' }],
      },
      {
        id: 'effectiveness',
        name: 'Effectiveness',
        checks: [{ id: 'operations-effectiveness', name: 'Effectiveness' }],
      },
    ],
  },
  Discipline: {
    division: 'Discipline',
    name: 'Discipline',
    areas: [
      {
        id: 'discipline',
        name: 'Discipline',
        checks: [
          { id: 'discipline-student-conduct', name: 'STUDENT CODE OF CONDUCT VIOLATION' },
          { id: 'discipline-lateness-assembly', name: 'LATENESS TO MORNING ASSEMBLY' },
          { id: 'discipline-loitering', name: 'LOITERING DURING CLASS HOURS/ SEEN OUTSIDE THE CLASS WITHOUT EXIT CARD' },
          { id: 'discipline-bullying', name: 'BULLYING AND HARASSMENT' },
          { id: 'discipline-uniform', name: 'STUDENT IMPROPER DRESSING & GROOMING' },
          { id: 'discipline-unauthorized-exit', name: 'UNAUTHORIZED EXIT FROM CAMPUS' },
          { id: 'discipline-fighting', name: 'PHYSICAL ALTERCATION / FIGHTING' },
          { id: 'discipline-noise-making', name: 'NOISE MAKING AND CLASSROOM DISTURBANCE' },
          { id: 'discipline-contraband', name: 'CONTRABAND ITEMS POSSESSION' },
          { id: 'discipline-defacing-property', name: 'DEFACING SCHOOL PROPERTY / VANDALISM' },
          { id: 'discipline-lateness-school', name: 'Lateness to school' },
        ],
      },
    ],
  },
};

// =========================================================================
// 4. TAXONOMY HELPER FUNCTIONS
// =========================================================================

/**
 * Retrieves a compliance area by ID (supporting legacy ID aliases like 'hostel')
 */
export function getComplianceArea(areaId) {
  if (!areaId) return null;
  const normalized = String(areaId).toLowerCase();
  if (normalized === 'hostel') {
    return COMPLIANCE_AREAS.find((a) => a.id === 'hostel-kitchen') || null;
  }
  return COMPLIANCE_AREAS.find((a) => a.id === areaId || a.id.toLowerCase() === normalized) || null;
}

/**
 * Retrieves a single compliance check by its unique ID
 */
export function getComplianceCheck(areaIdOrCheckId, maybeCheckId) {
  const checkId = maybeCheckId || areaIdOrCheckId;
  if (!checkId) return null;
  const q = String(checkId).toLowerCase().trim();

  for (const area of COMPLIANCE_AREAS) {
    const found = area.checks.find(
      (c) =>
        c.id?.toLowerCase() === q ||
        c.code?.toLowerCase() === q ||
        c.legacyId?.toLowerCase() === q ||
        c.name?.toLowerCase() === q
    );
    if (found) {
      return {
        ...found,
        areaId: area.id,
        areaName: area.name,
        division: area.division,
      };
    }
  }
  return null;
}

/**
 * Returns all compliance checks within a specific division
 */
export function getChecksByDivision(division) {
  if (!division) return [];
  const normalized = String(division).toLowerCase();
  return COMPLIANCE_AREAS
    .filter((a) => a.division.toLowerCase() === normalized)
    .flatMap((a) => a.checks);
}

/**
 * Authoritative mapping:
 * Academic → staff group → staff → applicable checks → exception
 * Operations → staff group → staff → applicable checks → exception
 * CDS → CDS → applicable checks → exception
 */
export function getApplicableChecks({ division, staffGroup, staff } = {}) {
  const div = (division || staff?.department || '').toLowerCase();

  // 1. CDS Division: CDS -> CDS -> Applicable Checks -> Exception
  if (div === 'cds') {
    const cdsArea = COMPLIANCE_AREAS.find((a) => a.id === 'cds');
    return cdsArea ? cdsArea.checks : [];
  }

  // 2. Academic Division: Academic -> staff group -> staff -> Applicable Checks -> Exception
  if (div === 'academic') {
    const acadArea = COMPLIANCE_AREAS.find((a) => a.id === 'academic');
    return acadArea ? acadArea.checks : [];
  }

  // 3. Operation / Operations Division: Operation -> staff group -> staff -> Applicable Checks -> Exception
  if (div === 'operation' || div === 'operations') {
    const group = String(staffGroup || staff?.staff_group || '').toLowerCase();

    if (group.includes('transport') || group.includes('driver')) {
      const trans = COMPLIANCE_AREAS.find((a) => a.id === 'transport')?.checks || [];
      const dress = COMPLIANCE_AREAS.find((a) => a.id === 'dressing')?.checks || [];
      const eff = COMPLIANCE_AREAS.find((a) => a.id === 'effectiveness')?.checks || [];
      return [...trans, ...dress, ...eff];
    }

    if (group.includes('maint') || group.includes('facilit')) {
      const maint = COMPLIANCE_AREAS.find((a) => a.id === 'maintenance')?.checks || [];
      const dress = COMPLIANCE_AREAS.find((a) => a.id === 'dressing')?.checks || [];
      const eff = COMPLIANCE_AREAS.find((a) => a.id === 'effectiveness')?.checks || [];
      return [...maint, ...dress, ...eff];
    }

    if (group.includes('secur')) {
      const sec = COMPLIANCE_AREAS.find((a) => a.id === 'security')?.checks || [];
      const dress = COMPLIANCE_AREAS.find((a) => a.id === 'dressing')?.checks || [];
      const eff = COMPLIANCE_AREAS.find((a) => a.id === 'effectiveness')?.checks || [];
      return [...sec, ...dress, ...eff];
    }

    if (group.includes('minder')) {
      const mind = COMPLIANCE_AREAS.find((a) => a.id === 'minders')?.checks || [];
      const dress = COMPLIANCE_AREAS.find((a) => a.id === 'dressing')?.checks || [];
      const eff = COMPLIANCE_AREAS.find((a) => a.id === 'effectiveness')?.checks || [];
      return [...mind, ...dress, ...eff];
    }

    if (group.includes('hostel') || group.includes('kitchen')) {
      const host = COMPLIANCE_AREAS.find((a) => a.id === 'hostel-kitchen')?.checks || [];
      const dress = COMPLIANCE_AREAS.find((a) => a.id === 'dressing')?.checks || [];
      const eff = COMPLIANCE_AREAS.find((a) => a.id === 'effectiveness')?.checks || [];
      return [...host, ...dress, ...eff];
    }

    // Default for general Operation: all Operation checks
    return COMPLIANCE_AREAS
      .filter((a) => a.division === 'Operations' || a.division === 'Operation')
      .flatMap((a) => a.checks);
  }

  // 4. Discipline Division: Discipline -> Applicable Checks -> Exception
  if (div === 'discipline') {
    const discArea = COMPLIANCE_AREAS.find((a) => a.id === 'discipline');
    return discArea ? discArea.checks : [];
  }

  // If division not specified, return all checks
  return COMPLIANCE_AREAS.flatMap((a) => a.checks);
}

/**
 * Returns all areas that contain detailed checks
 */
export function getAreasWithChecks() {
  return COMPLIANCE_AREAS.filter((a) => a.checks && a.checks.length > 0);
}

/**
 * Total count of defined checks across all areas
 */
export function getTotalChecksCount() {
  return COMPLIANCE_AREAS.reduce((sum, a) => sum + (a.checks ? a.checks.length : 0), 0);
}

// =========================================================================
// 5. BASELINE SEED EXCEPTIONS (Zero hardcoded records in production)
// =========================================================================
export const SEED_OBSERVATIONS = [];

// =========================================================================
// 6. AUTHORITATIVE DERIVED STAFF SEVERITY ENGINE
// =========================================================================

/**
 * Authoritative Staff Severity Calculation
 * 
 * Default Rule:
 * 0 exceptions: 'No recorded exceptions'
 * 1 exception: 'Low'
 * 2 exceptions: 'Medium'
 * 3 exceptions: 'High'
 * 4 or more: 'Critical'
 * 
 * Architectural Rules:
 * 1. Count is based on actual exception records belonging to that staff member.
 * 2. Severity respects the active analysis date filter (e.g. startDate to endDate).
 * 3. Seen/unseen review states do NOT change the exception count. Seen is a review state, not a resolution state.
 * 4. User cannot manually override derived severity.
 * 5. Severity is not stored on individual exceptions, but derived from the personnel exception count.
 */
export function calculateStaffSeverity(exceptionCount) {
  const count = Number(exceptionCount) || 0;
  if (count <= 0) return 'No recorded exceptions';
  if (count === 1) return 'Low';
  if (count === 2) return 'Medium';
  if (count === 3) return 'High';
  return 'Critical';
}

/**
 * Standard classification key: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
 */
export function getSeverityKey(exceptionCount) {
  const count = Number(exceptionCount) || 0;
  if (count <= 0) return 'NONE';
  if (count === 1) return 'LOW';
  if (count === 2) return 'MEDIUM';
  if (count === 3) return 'HIGH';
  return 'CRITICAL';
}

/**
 * Numerical weight for risk index calculations:
 * 0 -> 0, 1 -> 1, 2 -> 2, 3 -> 3, 4+ -> 4
 */
export function getSeverityWeight(exceptionCount) {
  const count = Number(exceptionCount) || 0;
  if (count <= 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  if (count === 3) return 3;
  return 4;
}

/**
 * Returns consistent Tailwind UI styling classes for a severity level or key
 */
export function getSeverityBadgeStyle(severityOrCount) {
  const sev = typeof severityOrCount === 'number'
    ? calculateStaffSeverity(severityOrCount)
    : (severityOrCount || '').toString().trim();

  const upper = sev.toUpperCase();
  if (upper === 'CRITICAL' || upper === 'CRIT') {
    return 'bg-rose-50 text-rose-700 border border-rose-200';
  }
  if (upper === 'HIGH') {
    return 'bg-orange-50 text-orange-700 border border-orange-200';
  }
  if (upper === 'MEDIUM' || upper === 'MED') {
    return 'bg-amber-50 text-amber-700 border border-amber-200';
  }
  if (upper === 'LOW') {
    return 'bg-blue-50 text-blue-700 border border-blue-200';
  }
  return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
}

/**
 * Filters exceptions for a specific staff member or subject,
 * strictly respecting active date filter boundaries (e.g. Analysis date range).
 * 
 * Seen/unseen review states do NOT change the exception count.
 */
export function getStaffExceptions(staff, exceptions = [], dateFilter = {}) {
  if (!staff || !Array.isArray(exceptions)) return [];

  const staffIdStr = staff.id ? String(staff.id) : null;
  const staffCode = staff.staff_code ? String(staff.staff_code).trim().toLowerCase() : null;
  const staffName = staff.name ? String(staff.name).trim().toLowerCase() : null;

  const startDate = dateFilter.startDate || dateFilter.start_date || dateFilter.from || null;
  const endDate = dateFilter.endDate || dateFilter.end_date || dateFilter.to || null;

  return exceptions.filter((r) => {
    // 1. Staff relational identity check
    const matchesStaff =
      (staffIdStr && r.staff_id && String(r.staff_id) === staffIdStr) ||
      (staffCode && r.staff_code && String(r.staff_code).trim().toLowerCase() === staffCode) ||
      (staffName && r.staff_name && String(r.staff_name).trim().toLowerCase() === staffName) ||
      (staffName && r.teacher_name && String(r.teacher_name).trim().toLowerCase() === staffName);

    if (!matchesStaff) return false;

    // 2. Date filter check (respects active analysis date range)
    const recordDate = r.incident_date || r.date || (r.created_at ? r.created_at.split('T')[0] : null);
    if (recordDate) {
      if (startDate && recordDate < startDate) return false;
      if (endDate && recordDate > endDate) return false;
    }

    // 3. Exclude only records that are explicitly compliant observations (if any)
    if (r.status === 'Compliant') return false;

    // Seen/unseen is a review state, not a resolution state; it does NOT change the count.
    return true;
  });
}

/**
 * Master Derived Staff Severity Engine:
 * Computes authoritative derived severity for a staff member.
 */
export function deriveStaffSeverity(staff, exceptions = [], dateFilter = {}) {
  const staffExceptions = getStaffExceptions(staff, exceptions, dateFilter);
  const count = staffExceptions.length;
  const severity = calculateStaffSeverity(count);
  const key = getSeverityKey(count);
  const weight = getSeverityWeight(count);

  return {
    count,
    severity, // 'No recorded exceptions' | 'Low' | 'Medium' | 'High' | 'Critical'
    key,      // 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
    weight,
    score: count * weight,
    exceptions: staffExceptions,
    label: count === 0
      ? 'No recorded exceptions'
      : count === 1
        ? '1 exception'
        : `${count} exceptions`,
  };
}

/**
 * Creates a fast lookup map of derived staff severities for a staff roster,
 * respecting active date filters.
 */
export function computeStaffSeverityMap(staffList = [], exceptions = [], dateFilter = {}) {
  const map = new Map();
  staffList.forEach((person) => {
    const derived = deriveStaffSeverity(person, exceptions, dateFilter);
    if (person.id) map.set(String(person.id), derived);
    if (person.staff_code) map.set(`code:${String(person.staff_code).trim().toLowerCase()}`, derived);
    if (person.name) map.set(`name:${String(person.name).trim().toLowerCase()}`, derived);
  });
  return map;
}

// =========================================================================
// 7. AUTHORITATIVE INTEGRATED DATA MODEL ENGINE
// =========================================================================

/**
 * Authoritative Active Staff Denominator:
 * Active Academic Staff + Active Operation Staff + Active Discipline Staff
 * (Archived excluded, CDS excluded as institutional target)
 */
export function getActiveStaffDenominator(staffList = []) {
  if (!Array.isArray(staffList)) return [];
  return staffList.filter((s) => {
    if (s.status === 'Archived') return false;
    const dept = (s.department || (s.category === 'Academic Staff' ? 'Academic' : 'Operation') || '').trim().toLowerCase();
    return dept === 'academic' || dept === 'operation' || dept === 'operations' || dept === 'discipline';
  });
}

/**
 * Evaluates whether a record represents CDS (division/infrastructure level).
 * CDS is NOT a staff reporting department.
 */
export function isCdsRecord(r) {
  if (!r) return false;
  const div = String(r.division || r.department || '').trim().toLowerCase();
  return (
    div === 'cds' ||
    r.areaId === 'cds' ||
    (r.target_type === 'division' && (!div || div === 'cds'))
  );
}

/**
 * Evaluates whether a record is a valid exception (strictly excludes legacy 'Compliant').
 */
export function isExceptionRecord(r) {
  if (!r) return false;
  return r.status !== 'Compliant';
}

/**
 * Review state helper: strictly checks if marked as seen.
 * Seen is a review state, NOT a resolution state.
 * Seen/unseen does NOT change the exception count or staff severity.
 */
export function isSeenException(r) {
  if (!r) return false;
  return Boolean(
    r.review_status === 'SEEN' ||
    r.is_seen === true ||
    r.seen === true ||
    r.seen_at
  );
}

export function isUnseenException(r) {
  return !isSeenException(r);
}

/**
 * Authoritative Unified Metrics Engine:
 * Returns consistent, unified counts for the entire application.
 * Guarantees that:
 * 1. Total exceptions = Staff exceptions + CDS exceptions.
 * 2. Unseen count matches the Reports unseen filter.
 * 3. All staff-based metrics use the exact activeStaffDenominator.
 * 4. Zero obsolete "Compliant" concepts.
 */
export function getAuthoritativeMetrics(staffList = [], reports = [], dateFilter = {}) {
  const activeStaff = getActiveStaffDenominator(staffList);
  const totalStaffCount = activeStaff.length;

  const startDate = dateFilter.startDate || dateFilter.start_date || dateFilter.from || null;
  const endDate = dateFilter.endDate || dateFilter.end_date || dateFilter.to || null;

  // Filter exception records in active date range and term
  const termFilter = dateFilter.term || dateFilter.selectedTerm || null;
  const inRangeExceptions = (Array.isArray(reports) ? reports : []).filter((r) => {
    if (!isExceptionRecord(r)) return false;
    
    // Strict term isolation: if term filter is specified and not 'ALL', enforce exact term match
    if (termFilter && termFilter !== 'ALL') {
      const rTerm = String(r.term || '').trim().toLowerCase();
      if (rTerm !== termFilter.trim().toLowerCase()) return false;
    }

    const d = r.incident_date || r.date || (r.created_at ? r.created_at.split('T')[0] : null);
    if (d) {
      if (startDate && d < startDate) return false;
      if (endDate && d > endDate) return false;
    }
    return true;
  });

  const staffExceptions = inRangeExceptions.filter((r) => !isCdsRecord(r));
  const cdsExceptions = inRangeExceptions.filter((r) => isCdsRecord(r));

  // Canonical Four Division Segregations
  const academicExceptions = inRangeExceptions.filter((r) => {
    const div = String(r.division || r.department || '').toLowerCase();
    return div === 'academic';
  });
  const operationsExceptions = inRangeExceptions.filter((r) => {
    const div = String(r.division || r.department || '').toLowerCase();
    return div === 'operation' || div === 'operations';
  });
  const disciplineExceptions = inRangeExceptions.filter((r) => {
    const div = String(r.division || r.department || '').toLowerCase();
    return div === 'discipline';
  });

  const totalExceptions = inRangeExceptions.length;
  const seenExceptions = inRangeExceptions.filter(isSeenException);
  const unseenExceptions = inRangeExceptions.filter(isUnseenException);

  const seenStaffExceptions = staffExceptions.filter(isSeenException);
  const unseenStaffExceptions = staffExceptions.filter(isUnseenException);

  const seenCdsExceptions = cdsExceptions.filter(isSeenException);
  const unseenCdsExceptions = cdsExceptions.filter(isUnseenException);

  // Compute derived staff severity for every active staff member
  const analyzedStaffList = activeStaff.map((person) => {
    const derived = deriveStaffSeverity(person, staffExceptions, { startDate, endDate });
    return {
      ...person,
      derivedCount: derived.count,
      derivedSeverity: derived.severity, // 'No recorded exceptions' | 'Low' | 'Medium' | 'High' | 'Critical'
      severityKey: derived.key,
      weight: derived.weight,
      score: derived.score,
      exceptions: derived.exceptions,
      exceptionLabel: derived.label,
    };
  });

  const staffWithExceptions = analyzedStaffList.filter((s) => s.derivedCount > 0);
  const staffWithoutExceptions = analyzedStaffList.filter((s) => s.derivedCount === 0);

  const criticalStaff = analyzedStaffList.filter((s) => s.derivedSeverity === 'Critical');
  const highStaff = analyzedStaffList.filter((s) => s.derivedSeverity === 'High');
  const mediumStaff = analyzedStaffList.filter((s) => s.derivedSeverity === 'Medium');
  const lowStaff = analyzedStaffList.filter((s) => s.derivedSeverity === 'Low');

  return {
    // Staff Population
    activeStaff,
    totalStaffCount,
    // Exception Totals
    totalExceptions,
    inRangeExceptions,
    staffExceptions,
    cdsExceptions,
    academicExceptions,
    operationsExceptions,
    disciplineExceptions,
    totalStaffExceptionsCount: staffExceptions.length,
    totalCdsExceptionsCount: cdsExceptions.length,
    totalAcademicExceptionsCount: academicExceptions.length,
    totalOperationsExceptionsCount: operationsExceptions.length,
    totalDisciplineExceptionsCount: disciplineExceptions.length,
    // Review States
    seenExceptions,
    unseenExceptions,
    seenCount: seenExceptions.length,
    unseenCount: unseenExceptions.length,
    seenStaffCount: seenStaffExceptions.length,
    unseenStaffCount: unseenStaffExceptions.length,
    seenCdsCount: seenCdsExceptions.length,
    unseenCdsCount: unseenCdsExceptions.length,
    // Staff Segments
    analyzedStaffList,
    staffWithExceptions,
    staffWithoutExceptions,
    criticalStaff,
    highStaff,
    mediumStaff,
    lowStaff,
    // Percentages
    staffWithExceptionsPct: totalStaffCount > 0 ? Math.round((staffWithExceptions.length / totalStaffCount) * 100) : 0,
    noExceptionsPct: totalStaffCount > 0 ? Math.round((staffWithoutExceptions.length / totalStaffCount) * 100) : 0,
  };
}

// =========================================================================
// 8. EXCEPTION REPOSITORY (Zero hardcoded records in production)
export const SEED_EXCEPTIONS = [];
