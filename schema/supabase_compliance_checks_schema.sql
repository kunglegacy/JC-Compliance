-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE MANAGEMENT SYSTEM
-- AUTHORITATIVE COMPLIANCE CHECKS SCHEMA & SEED MIGRATION (IDEMPOTENT)
-- =========================================================================
-- This script:
-- 1. Creates/alters the compliance_checks table idempotently (never fails if table/columns already exist):
--    - ACA1 - ACA30 : Academic Division
--    - CDS1 - CDS11 : Corporate Digital Services Division
--    - OPR1 - OPR34 : Operations Division (Transport, Facilities, Hostel, Minders, Maintenance, Dressing, Effectiveness)
--    - DIS1 - DIS10 : Discipline Division
-- 2. Sets up an automated trigger to auto-generate the next ID (ACA31, OPR35, CDS12, DIS11...)
--    whenever an administrator or user adds a new compliance check.
-- 3. Enables Row Level Security (RLS) idempotently (drops existing policies before re-creating).
-- 4. Seeds all 85 authoritative compliance checks grouped by division and area (using ON CONFLICT).
-- =========================================================================

-- 1. Create table if missing
CREATE TABLE IF NOT EXISTS public.compliance_checks (
  id          TEXT PRIMARY KEY,
  check_code  TEXT,
  division    TEXT NOT NULL DEFAULT 'Academic',
  area_name   TEXT NOT NULL DEFAULT 'Academic',
  area_id     TEXT,
  name        TEXT NOT NULL DEFAULT '',
  description TEXT,
  order_index INT DEFAULT 0,
  is_active   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Idempotently ensure all necessary columns exist if the table was created by an older migration
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS check_code TEXT;

ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
UPDATE public.compliance_checks SET division = 'Academic' WHERE division IS NULL OR division = '';
ALTER TABLE public.compliance_checks ALTER COLUMN division SET DEFAULT 'Academic';

ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS area_name TEXT DEFAULT 'Academic';
UPDATE public.compliance_checks SET area_name = 'Academic' WHERE area_name IS NULL OR area_name = '';
ALTER TABLE public.compliance_checks ALTER COLUMN area_name SET DEFAULT 'Academic';

ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS area_id TEXT;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS name TEXT DEFAULT '';
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS order_index INT DEFAULT 0;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- Safely add division validation check constraint if not already present
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_compliance_checks_division'
  ) THEN
    ALTER TABLE public.compliance_checks
    ADD CONSTRAINT chk_compliance_checks_division
    CHECK (division IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));
  END IF;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- Indexing for high-performance filtering (safe if already existing)
CREATE INDEX IF NOT EXISTS idx_compliance_checks_division ON public.compliance_checks(division);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_area_name ON public.compliance_checks(area_name);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_is_active ON public.compliance_checks(is_active);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_order ON public.compliance_checks(order_index);

-- 2. Timestamp update function and trigger (idempotent)
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_comp_checks_updated ON public.compliance_checks;
CREATE TRIGGER trg_comp_checks_updated
BEFORE UPDATE ON public.compliance_checks
FOR EACH ROW
EXECUTE FUNCTION update_timestamp();

-- 3. Trigger for automated ID generation (e.g. ACA31, OPR35, CDS12, DIS11)
CREATE OR REPLACE FUNCTION public.generate_compliance_check_id()
RETURNS TRIGGER AS $$
DECLARE
  v_prefix TEXT;
  v_max_num INT;
BEGIN
  -- Standardize division to canonical form
  IF NEW.division IN ('Operation', 'Operations') THEN
    NEW.division := 'Operation';
  END IF;

  -- Determine appropriate prefix based on division
  IF NEW.division = 'Academic' THEN
    v_prefix := 'ACA';
  ELSIF NEW.division = 'Operation' THEN
    v_prefix := 'OPR';
  ELSIF NEW.division = 'CDS' THEN
    v_prefix := 'CDS';
  ELSIF NEW.division = 'Discipline' THEN
    v_prefix := 'DIS';
  ELSE
    v_prefix := 'CHK';
  END IF;

  -- Auto-generate ID if not provided, empty, or placeholder
  IF NEW.id IS NULL OR TRIM(NEW.id) = '' OR NEW.id LIKE 'temp-%' OR NEW.id LIKE 'new-%' OR NEW.id = 'none-listed' THEN
    SELECT COALESCE(MAX(
      SUBSTRING(id FROM '^' || v_prefix || '([0-9]+)$')::INT
    ), 0) + 1
    INTO v_max_num
    FROM public.compliance_checks
    WHERE id ~ ('^' || v_prefix || '[0-9]+$');

    NEW.id := v_prefix || v_max_num::TEXT;
  END IF;

  -- Ensure check_code matches id
  IF NEW.check_code IS NULL OR TRIM(NEW.check_code) = '' THEN
    NEW.check_code := NEW.id;
  END IF;

  -- Populate area_id if empty
  IF NEW.area_id IS NULL OR TRIM(NEW.area_id) = '' THEN
    NEW.area_id := LOWER(REGEXP_REPLACE(NEW.area_name, '[^a-zA-Z0-9]+', '-', 'g'));
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_generate_compliance_check_id ON public.compliance_checks;
CREATE TRIGGER trg_generate_compliance_check_id
BEFORE INSERT ON public.compliance_checks
FOR EACH ROW
EXECUTE FUNCTION public.generate_compliance_check_id();

-- 4. Row Level Security Policies (Idempotent: drop before create)
ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow read compliance checks" ON public.compliance_checks;
DROP POLICY IF EXISTS "Authenticated read compliance checks" ON public.compliance_checks;
CREATE POLICY "Allow read compliance checks"
ON public.compliance_checks FOR SELECT
USING (TRUE);

DROP POLICY IF EXISTS "Allow authenticated insert compliance checks" ON public.compliance_checks;
DROP POLICY IF EXISTS "Authenticated insert compliance checks" ON public.compliance_checks;
CREATE POLICY "Allow authenticated insert compliance checks"
ON public.compliance_checks FOR INSERT
WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Allow admin update compliance checks" ON public.compliance_checks;
DROP POLICY IF EXISTS "Admin manage compliance checks" ON public.compliance_checks;
CREATE POLICY "Allow admin update compliance checks"
ON public.compliance_checks FOR UPDATE
USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Allow admin delete compliance checks" ON public.compliance_checks;
CREATE POLICY "Allow admin delete compliance checks"
ON public.compliance_checks FOR DELETE
USING (auth.uid() IS NOT NULL);

-- 5. Seed 85 Authoritative Checks with Canonical Prefixes (ACA, CDS, OPR, DIS)
-- Safely archive/deactivate any obsolete non-canonical checks
UPDATE public.compliance_checks
SET is_active = FALSE
WHERE id NOT LIKE 'ACA%' AND id NOT LIKE 'CDS%' AND id NOT LIKE 'OPR%' AND id NOT LIKE 'DIS%';

-- Insert or update authoritative checks (idempotent, never duplicates or errors)
INSERT INTO public.compliance_checks (id, check_code, division, area_name, area_id, name, description, order_index, is_active) VALUES
  -- =========================================================================
  -- 1. ACADEMIC CHECKS (ACA1 to ACA30)
  -- =========================================================================
  ('ACA1', 'ACA1', 'Academic', 'Academic', 'academic', 'UNPROFESSIONAL MISCONDUCT OF TEACHERS', 'Unprofessional behavioral misconduct or decorum violation by teacher.', 1, TRUE),
  ('ACA2', 'ACA2', 'Academic', 'Academic', 'academic', 'BEHIND IN THE SCHEME OF WORK', 'Class instructional delivery is lagging behind the approved syllabus scheme of work.', 2, TRUE),
  ('ACA3', 'ACA3', 'Academic', 'Academic', 'academic', 'AHEAD IN THE SCHEME OF WORK', 'Class instructional delivery has unsustainably outpaced the approved syllabus scheme of work.', 3, TRUE),
  ('ACA4', 'ACA4', 'Academic', 'Academic', 'academic', 'NO SCHEME GIVEN TO STUDENTS', 'Students have not been provided the term syllabus scheme of work.', 4, TRUE),
  ('ACA5', 'ACA5', 'Academic', 'Academic', 'academic', 'UNMARKED ASSIGNMENT', 'Failure to mark, correct, or grade student homework and class assignments.', 5, TRUE),
  ('ACA6', 'ACA6', 'Academic', 'Academic', 'academic', 'DIRTY AND DISORGANISED CLASSROOM', 'Classroom environment, floor, whiteboard, or desks left dirty or disorderly.', 6, TRUE),
  ('ACA7', 'ACA7', 'Academic', 'Academic', 'academic', 'USING A FAINT MAKER', 'Use of depleted or faint whiteboard markers causing legibility difficulties for learners.', 7, TRUE),
  ('ACA8', 'ACA8', 'Academic', 'Academic', 'academic', 'POORLY WRITTEN QUALITY NOTE/CONTENT', 'Substandard, illegible, or incorrect lesson content delivered on board or in student notes.', 8, TRUE),
  ('ACA9', 'ACA9', 'Academic', 'Academic', 'academic', 'LATENESS TO CLASSROOM', 'Teacher failure to report to assigned classroom at the designated bell time.', 9, TRUE),
  ('ACA10', 'ACA10', 'Academic', 'Academic', 'academic', 'LATENESS IN TEACHING', 'Delayed commencement of instructional delivery after entering the classroom.', 10, TRUE),
  ('ACA11', 'ACA11', 'Academic', 'Academic', 'academic', 'LOCKED CLASSROOM DOOR', 'Classroom door locked during instructional periods preventing observation or airflow.', 11, TRUE),
  ('ACA12', 'ACA12', 'Academic', 'Academic', 'academic', 'NON COMPLIANCE IN WRITING SPECIFIC OBJECTIVES', 'Failure to clearly write instructional behavioral objectives on the board.', 12, TRUE),
  ('ACA13', 'ACA13', 'Academic', 'Academic', 'academic', 'NON COMPLIANCE TO FLEXIBLE GROUPING', 'Non-adherence to differentiated, flexible grouping pedagogy for student ability levels.', 13, TRUE),
  ('ACA14', 'ACA14', 'Academic', 'Academic', 'academic', 'CLASS HAVING INCOMPLETE NOTE', 'Classroom notes incomplete due to missed lessons or unverified catch-up delivery.', 14, TRUE),
  ('ACA15', 'ACA15', 'Academic', 'Academic', 'academic', 'EXCEPTION DIRECTLY TO ACADEMICS', 'Direct breach of standard academic governance and instructional guidelines.', 15, TRUE),
  ('ACA16', 'ACA16', 'Academic', 'Academic', 'academic', 'PENDING DISCIPLINARY CASES', 'Unresolved academic misconduct or disciplinary referral pending management hearing.', 16, TRUE),
  ('ACA17', 'ACA17', 'Academic', 'Academic', 'academic', 'IMPROPER DRESSING', 'Teacher appearance failing to meet the professional school dress code standards.', 17, TRUE),
  ('ACA18', 'ACA18', 'Academic', 'Academic', 'academic', 'POOR CLASS CONTROL AND MANAGEMENT', 'Inability to maintain orderly discipline and focused learner attention during lessons.', 18, TRUE),
  ('ACA19', 'ACA19', 'Academic', 'Academic', 'academic', 'MARKING OF NOTES IN CLASS', 'Marking notebooks during lesson delivery time instead of teaching and interacting with pupils.', 19, TRUE),
  ('ACA20', 'ACA20', 'Academic', 'Academic', 'academic', 'SITTING DOWN DURING TUTORIAL DELIVERY', 'Sitting during lecture/tutorial delivery without instructional justification.', 20, TRUE),
  ('ACA21', 'ACA21', 'Academic', 'Academic', 'academic', 'ABSENCE FROM CLASS', 'Unauthorized absence or abandonment of assigned class period.', 21, TRUE),
  ('ACA22', 'ACA22', 'Academic', 'Academic', 'academic', 'UNMARKED NOTES', 'Lesson notes unvetted, unendorsed, or uncorrected across student exercise books.', 22, TRUE),
  ('ACA23', 'ACA23', 'Academic', 'Academic', 'academic', 'STUDENTS SLEEPING DURING TUTORIALS', 'Learners asleep in class due to unengaging delivery or supervisory negligence.', 23, TRUE),
  ('ACA24', 'ACA24', 'Academic', 'Academic', 'academic', 'STUDENTS DISTRACTED DURING TUTORIALS', 'Students unengaged, using mobile devices, or playing during instruction.', 24, TRUE),
  ('ACA25', 'ACA25', 'Academic', 'Academic', 'academic', 'NO TOPIC/SPECIFIC OBJECTIVES WRITTEN DOWN', 'Topic title and instructional goals absent from lesson board presentation.', 25, TRUE),
  ('ACA26', 'ACA26', 'Academic', 'Academic', 'academic', 'LATENESS FOR EXAM INVIGILATION', 'Delayed arrival for examination supervision and test script distribution.', 26, TRUE),
  ('ACA27', 'ACA27', 'Academic', 'Academic', 'academic', 'DISTRACTIONS DURING EXAMS(TRACKER, CA & QUALITY EXAM)', 'Invigilator distraction (phone browsing, conversations) during assessments.', 27, TRUE),
  ('ACA28', 'ACA28', 'Academic', 'Academic', 'academic', 'BEATING OF STUDENTS/PUPILS', 'Physical corporal punishment in direct violation of child protection regulations.', 28, TRUE),
  ('ACA29', 'ACA29', 'Academic', 'Academic', 'academic', 'UNSTANDARDISED EXAM REFERENCE QUESTION', 'Exam questions failing the standard moderation and quality assurance rubric.', 29, TRUE),
  ('ACA30', 'ACA30', 'Academic', 'Academic', 'academic', 'DEVIATION FROM REGIMENTED PLAN', 'Unauthorized departure from the prescribed daily academic timetable and schedule.', 30, TRUE),

  -- =========================================================================
  -- 2. CDS CHECKS (CDS1 to CDS11)
  -- =========================================================================
  ('CDS1', 'CDS1', 'CDS', 'CBT', 'cds-cbt', 'CBT', 'Computer-Based Testing platforms, question bank upload, and server readiness.', 1, TRUE),
  ('CDS2', 'CDS2', 'CDS', 'Hardware', 'cds-hardware', 'Hardware', 'Workstations, networking routers, switchboards, and peripheral reliability.', 2, TRUE),
  ('CDS3', 'CDS3', 'CDS', 'Website', 'cds-website', 'Website', 'School portal uptime, SSL security certificates, and website announcement updates.', 3, TRUE),
  ('CDS4', 'CDS4', 'CDS', 'Digital Marketing', 'cds-digital-mkt', 'Digital Marketing', 'Official social media broadcasts, campaign flyers, and school public communications.', 4, TRUE),
  ('CDS5', 'CDS5', 'CDS', 'Exam Records', 'cds-exam-records', 'Exam Records', 'Digital mark sheets, continuous assessment score sync, and database backups.', 5, TRUE),
  ('CDS6', 'CDS6', 'CDS', 'Staff Records', 'cds-staff-records', 'Staff Records', 'Digital personnel rosters, qualification credentials, and digital HR records.', 6, TRUE),
  ('CDS7', 'CDS7', 'CDS', 'Camera / Printer / Computer Maintenance', 'cds-camera', 'Camera', 'CCTV campus camera feeds, security storage, and network video recording.', 7, TRUE),
  ('CDS8', 'CDS8', 'CDS', 'Camera / Printer / Computer Maintenance', 'cds-printer', 'Printer & Computer Maintenance', 'Printer toner, paper feed, lab workstations, and preventative computer maintenance.', 8, TRUE),
  ('CDS9', 'CDS9', 'CDS', 'Result Portal', 'cds-result-portal', 'Result Portal', 'Term report card portal availability, PIN generation, and parent portal access.', 9, TRUE),
  ('CDS10', 'CDS10', 'CDS', 'Documentation', 'cds-documentation', 'Documentation', 'Standard operating procedures, IT asset register, and software license records.', 10, TRUE),
  ('CDS11', 'CDS11', 'CDS', 'Biometric', 'cds-biometric', 'Biometric', 'Staff and student biometric attendance scanners synchronization and network uptime.', 11, TRUE),

  -- =========================================================================
  -- 3. OPERATIONS CHECKS (OPR1 to OPR34)
  -- =========================================================================
  -- Transport Allocation (OPR1 to OPR12)
  ('OPR1', 'OPR1', 'Operation', 'Transport', 'transport', 'Tyres', 'Bus tyre tread depth, inflation pressure, and spare tyre condition.', 1, TRUE),
  ('OPR2', 'OPR2', 'Operation', 'Transport', 'transport', 'Windows', 'Bus glass integrity, window sliders, and weather sealing.', 2, TRUE),
  ('OPR3', 'OPR3', 'Operation', 'Transport', 'transport', 'Body Work', 'Vehicle exterior panel condition, paintwork, bumpers, and branding.', 3, TRUE),
  ('OPR4', 'OPR4', 'Operation', 'Transport', 'transport', 'Lights', 'Headlamps, brake signals, indicators, and hazard lighting.', 4, TRUE),
  ('OPR5', 'OPR5', 'Operation', 'Transport', 'transport', 'Interior', 'Passenger seatbelts, upholstery cleanliness, and aisle order.', 5, TRUE),
  ('OPR6', 'OPR6', 'Operation', 'Transport', 'transport', 'Pick-up/drop-off lateness', 'Delayed morning pupil pickup or afternoon drop-off route schedule.', 6, TRUE),
  ('OPR7', 'OPR7', 'Operation', 'Transport', 'transport', 'Breakdown', 'En-route vehicle mechanical failure or unscheduled transit disruption.', 7, TRUE),
  ('OPR8', 'OPR8', 'Operation', 'Transport', 'transport', 'Accident', 'Vehicle collision, scrape, or road safety incident.', 8, TRUE),
  ('OPR9', 'OPR9', 'Operation', 'Transport', 'transport', 'Fire extinguisher', 'Functional, serviced, and unexpired fire extinguisher in vehicle.', 9, TRUE),
  ('OPR10', 'OPR10', 'Operation', 'Transport', 'transport', 'Jack', 'Vehicle wheel jack, lug wrench, and emergency safety triangle.', 10, TRUE),
  ('OPR11', 'OPR11', 'Operation', 'Transport', 'transport', 'Driving Licences', 'Valid national commercial driving licences and statutory road credentials.', 11, TRUE),
  ('OPR12', 'OPR12', 'Operation', 'Transport', 'transport', 'Dressing', 'Transport driver and conductor uniform neatness and footwear compliance.', 12, TRUE),

  -- Facilities Allocation (OPR13 to OPR24)
  ('OPR13', 'OPR13', 'Operation', 'Facilities', 'facilities', 'Doors', 'Classroom and office door locks, hinges, and handles.', 13, TRUE),
  ('OPR14', 'OPR14', 'Operation', 'Facilities', 'facilities', 'Windows', 'Classroom louvres, window catches, mosquito nets, and glass panes.', 14, TRUE),
  ('OPR15', 'OPR15', 'Operation', 'Facilities', 'facilities', 'Tables', 'Student and teacher work table stability, smooth surface, and structural integrity.', 15, TRUE),
  ('OPR16', 'OPR16', 'Operation', 'Facilities', 'facilities', 'Chairs', 'Desk chair sturdiness, backrests, and absence of broken frames.', 16, TRUE),
  ('OPR17', 'OPR17', 'Operation', 'Facilities', 'facilities', 'Tiles', 'Floor tiling, absence of cracked, chipped, or slippery surfaces.', 17, TRUE),
  ('OPR18', 'OPR18', 'Operation', 'Facilities', 'facilities', 'Lights', 'Fluorescent fixtures, classroom bulb illumination, and functional switches.', 18, TRUE),
  ('OPR19', 'OPR19', 'Operation', 'Facilities', 'facilities', 'Fans', 'Ceiling and standing fan speed regulation and motor safety.', 19, TRUE),
  ('OPR20', 'OPR20', 'Operation', 'Facilities', 'facilities', 'Flowers', 'Campus landscaping, potted plant watering, and flowerbed upkeep.', 20, TRUE),
  ('OPR21', 'OPR21', 'Operation', 'Facilities', 'facilities', 'Tidiness', 'Corridor tidiness, waste bin emptying, and absence of refuse.', 21, TRUE),
  ('OPR22', 'OPR22', 'Operation', 'Facilities', 'facilities', 'Pipes', 'Plumbing pipework, clean drainage, and absence of leaks.', 22, TRUE),
  ('OPR23', 'OPR23', 'Operation', 'Facilities', 'facilities', 'Kerbs', 'Walkway kerb painting, stone borders, and path cleanliness.', 23, TRUE),
  ('OPR24', 'OPR24', 'Operation', 'Facilities', 'facilities', 'AC', 'Air conditioning unit cooling efficiency, filter cleaning, and servicing.', 24, TRUE),

  -- Hostel / Kitchen Allocation (OPR25 to OPR30)
  ('OPR25', 'OPR25', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Promptness to class from hostel', 'Boarder punctuality from dormitory to morning assembly and classroom.', 25, TRUE),
  ('OPR26', 'OPR26', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Table etiquette', 'Dining hall manners, supervised meal queues, and decorum.', 26, TRUE),
  ('OPR27', 'OPR27', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Hot water', 'Hot water supply availability for student morning bathing.', 27, TRUE),
  ('OPR28', 'OPR28', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Drinking/using water', 'Clean treated drinking water supply and dispenser sanitation.', 28, TRUE),
  ('OPR29', 'OPR29', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Cleanliness', 'Hostel dorm sanitation, kitchen hygiene, and utensil sterilization.', 29, TRUE),
  ('OPR30', 'OPR30', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Siesta enforcement', 'Adherence to designated student afternoon siesta quiet hours.', 30, TRUE),

  -- Minders Allocation (OPR31)
  ('OPR31', 'OPR31', 'Operation', 'Minders', 'minders', 'Minders', 'Early years pupil supervision, playground vigilance, and dining support.', 31, TRUE),

  -- Maintenance Allocation (OPR32)
  ('OPR32', 'OPR32', 'Operation', 'Maintenance', 'maintenance', 'Maintenance', 'Prompt fault rectification, electrical repairs, and carpentry upkeep.', 32, TRUE),

  -- Universal Operations Standards (OPR33 & OPR34)
  ('OPR33', 'OPR33', 'Operation', 'Dressing', 'dressing', 'Dressing', 'Support staff uniform adherence, name badges, and professional grooming.', 33, TRUE),
  ('OPR34', 'OPR34', 'Operation', 'Effectiveness', 'effectiveness', 'Effectiveness', 'Task completion speed, diligent shift coverage, and operational output.', 34, TRUE),

  -- =========================================================================
  -- 4. DISCIPLINE CHECKS (DIS1 to DIS10)
  -- =========================================================================
  ('DIS1', 'DIS1', 'Discipline', 'Discipline', 'discipline', 'STUDENT CODE OF CONDUCT VIOLATION', 'General breach of institutional student behavioral regulations.', 1, TRUE),
  ('DIS2', 'DIS2', 'Discipline', 'Discipline', 'discipline', 'LATENESS TO MORNING ASSEMBLY', 'Student delayed arrival to the central morning school assembly.', 2, TRUE),
  ('DIS3', 'DIS3', 'Discipline', 'Discipline', 'discipline', 'LOITERING DURING CLASS HOURS', 'Student loitering on walkways or outside class while lessons are in session.', 3, TRUE),
  ('DIS4', 'DIS4', 'Discipline', 'Discipline', 'discipline', 'BULLYING AND HARASSMENT', 'Intimidation, verbal harassment, extortion, or victimisation of peers.', 4, TRUE),
  ('DIS5', 'DIS5', 'Discipline', 'Discipline', 'discipline', 'STUDENT IMPROPER DRESSING & GROOMING', 'Inappropriate student uniform, unprescribed footwear, jewelry, or haircut.', 5, TRUE),
  ('DIS6', 'DIS6', 'Discipline', 'Discipline', 'discipline', 'UNAUTHORIZED EXIT FROM CAMPUS', 'Leaving school perimeter or hostel premises without an authorized gate pass.', 6, TRUE),
  ('DIS7', 'DIS7', 'Discipline', 'Discipline', 'discipline', 'PHYSICAL ALTERCATION / FIGHTING', 'Physical brawl, violence, or aggressive fighting on school property.', 7, TRUE),
  ('DIS8', 'DIS8', 'Discipline', 'Discipline', 'discipline', 'NOISE MAKING AND CLASSROOM DISTURBANCE', 'Excessive noise, rowdiness, or disruption of study environment.', 8, TRUE),
  ('DIS9', 'DIS9', 'Discipline', 'Discipline', 'discipline', 'CONTRABAND ITEMS POSSESSION', 'Possession of prohibited gadgets, unauthorized food, or forbidden items.', 9, TRUE),
  ('DIS10', 'DIS10', 'Discipline', 'Discipline', 'discipline', 'DEFACING SCHOOL PROPERTY / VANDALISM', 'Graffiti on walls, damaged desks, or intentional vandalism of facilities.', 10, TRUE)
ON CONFLICT (id) DO UPDATE SET
  check_code = EXCLUDED.check_code,
  division = EXCLUDED.division,
  area_name = EXCLUDED.area_name,
  area_id = EXCLUDED.area_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();
