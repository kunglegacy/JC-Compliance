-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE MANAGEMENT SYSTEM
-- AUTHORITATIVE COMPLIANCE REPORTS & REFERENCE ENTITIES SEED MIGRATION
-- =========================================================================
-- 100% Comprehensive Seed for 1st Term 2026/2027 Academic Session (Weeks 1-3)
-- Covers all 125 official compliance records across 12 departments.
-- (is_seen = TRUE, review_status = 'SEEN')
-- =========================================================================

-- Ensure public.staff department constraint supports all canonical divisions
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (
    SELECT conname 
    FROM pg_constraint 
    WHERE conrelid = 'public.staff'::regclass 
      AND contype = 'c' 
      AND pg_get_constraintdef(oid) ILIKE '%department%'
  ) LOOP
    EXECUTE 'ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
  END LOOP;
END $$;

UPDATE public.staff
SET department = CASE
  WHEN department ILIKE 'acad%' THEN 'Academic'
  WHEN department ILIKE 'cds%' THEN 'CDS'
  WHEN department ILIKE 'disc%' THEN 'Discipline'
  ELSE 'Operations'
END
WHERE department IS NULL OR department NOT IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline');

ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_department_check;
ALTER TABLE public.staff ADD CONSTRAINT staff_department_check 
  CHECK (department IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));

-- Ensure all target columns exist on public.compliance_reports idempotently
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_group TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS role TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS assigned_location TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_name TEXT;

-- Drop legacy division and target_type constraints if any
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (
    SELECT conname 
    FROM pg_constraint 
    WHERE conrelid = 'public.compliance_reports'::regclass 
      AND contype = 'c' 
      AND conname NOT IN ('chk_compliance_reports_staff_integrity')
      AND pg_get_constraintdef(oid) ILIKE '%division%'
  ) LOOP
    EXECUTE 'ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
  END LOOP;

  FOR r IN (
    SELECT conname 
    FROM pg_constraint 
    WHERE conrelid = 'public.compliance_reports'::regclass 
      AND contype = 'c' 
      AND conname NOT IN ('chk_compliance_reports_staff_integrity')
      AND pg_get_constraintdef(oid) ILIKE '%target_type%'
  ) LOOP
    EXECUTE 'ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
  END LOOP;
END $$;

ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_division_check;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_division_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT compliance_reports_division_check 
  CHECK (division IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));

ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_target_type_check;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_target_type_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT compliance_reports_target_type_check 
  CHECK (target_type IN ('staff', 'division'));

ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS chk_compliance_reports_staff_integrity;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_staff_integrity_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT chk_compliance_reports_staff_integrity
  CHECK (
    (division = 'CDS' AND target_type = 'division')
    OR
    (division = 'Discipline')
    OR
    (target_type = 'division')
    OR
    (division IN ('Academic', 'Operation', 'Operations') AND (staff_id IS NOT NULL OR incident_date < '2026-09-01'))
  );

-- Triggers & Functions
CREATE OR REPLACE FUNCTION generate_report_number()
RETURNS TRIGGER AS $$
DECLARE yr TEXT; seq INT; report_no TEXT;
BEGIN
  IF NEW.report_number IS NOT NULL AND TRIM(NEW.report_number) != '' THEN
    RETURN NEW;
  END IF;
  yr := TO_CHAR(CURRENT_DATE, 'YYYY');
  SELECT COUNT(*) + 1 INTO seq FROM public.compliance_reports WHERE TO_CHAR(created_at, 'YYYY') = yr;
  report_no := 'JCB-' || yr || '-' || LPAD(seq::TEXT, 4, '0');
  NEW.report_number := report_no;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION check_staff_not_archived_before_report()
RETURNS TRIGGER AS $$
DECLARE
  staff_current_status TEXT;
  staff_name_val TEXT;
BEGIN
  IF NEW.division = 'CDS' OR NEW.target_type = 'division' THEN
    IF NEW.staff_id IS NOT NULL THEN
      SELECT status, name INTO staff_current_status, staff_name_val
      FROM public.staff
      WHERE id = NEW.staff_id;
      IF FOUND AND staff_current_status = 'Archived' THEN
        RAISE EXCEPTION 'Compliance Governance Error: Unit "%" (id: %) is archived.', staff_name_val, NEW.staff_id;
      END IF;
    END IF;
    RETURN NEW;
  END IF;

  IF NEW.staff_id IS NULL THEN
    RAISE EXCEPTION 'Database Integrity Error: Academic and Operations exceptions must specify a valid staff_id.';
  END IF;

  SELECT status, name INTO staff_current_status, staff_name_val
  FROM public.staff
  WHERE id = NEW.staff_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Database Integrity Error: Selected staff_id % does not exist in the staff register.', NEW.staff_id;
  END IF;

  IF staff_current_status = 'Archived' THEN
    RAISE EXCEPTION 'Compliance Governance Error: Personnel "%" (id: %) is archived.', staff_name_val, NEW.staff_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_staff_not_archived_before_report ON public.compliance_reports;
CREATE TRIGGER trg_check_staff_not_archived_before_report
  BEFORE INSERT OR UPDATE OF staff_id ON public.compliance_reports
  FOR EACH ROW EXECUTE FUNCTION check_staff_not_archived_before_report();

CREATE OR REPLACE FUNCTION sync_weekly_tracker()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.reported_by IS NULL OR NEW.term IS NULL OR NEW.week_number IS NULL THEN
    RETURN NEW;
  END IF;

  INSERT INTO public.weekly_tracker (user_id, term, week_number, report_count, last_report_at)
  VALUES (NEW.reported_by, NEW.term, NEW.week_number, 1, COALESCE(NEW.created_at, NOW()))
  ON CONFLICT (user_id, term, week_number)
  DO UPDATE SET 
    report_count = weekly_tracker.report_count + 1, 
    last_report_at = COALESCE(NEW.created_at, NOW()), 
    updated_at = NOW();

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_weekly_tracker ON public.compliance_reports;
CREATE TRIGGER trg_sync_weekly_tracker 
  AFTER INSERT ON public.compliance_reports 
  FOR EACH ROW EXECUTE FUNCTION sync_weekly_tracker();


-- -------------------------------------------------------------------------
-- STEP 1: ENSURE ALL REFERENCE CLASSES & LOCATIONS EXIST
-- -------------------------------------------------------------------------
INSERT INTO public.classes (name, level, arm, section) VALUES
  -- Senior Secondary
  ('SS3D', 'SS 3', 'D', 'SENIOR_SECONDARY'),
  ('SS1C', 'SS 1', 'C', 'SENIOR_SECONDARY'),
  ('SS2A', 'SS 2', 'A', 'SENIOR_SECONDARY'),
  ('SS2 Royal', 'SS 2', 'Royal', 'SENIOR_SECONDARY'),
  ('SS2B', 'SS 2', 'B', 'SENIOR_SECONDARY'),
  ('SS1D', 'SS 1', 'D', 'SENIOR_SECONDARY'),
  ('SS1E', 'SS 1', 'E', 'SENIOR_SECONDARY'),
  ('SS1', 'SS 1', 'General', 'SENIOR_SECONDARY'),
  ('SS3', 'SS 3', 'General', 'SENIOR_SECONDARY'),
  ('Royal Class', 'Senior Secondary', 'Royal', 'SENIOR_SECONDARY'),
  ('SS1A', 'SS 1', 'A', 'SENIOR_SECONDARY'),
  ('SS3A', 'SS 3', 'A', 'SENIOR_SECONDARY'),
  ('SS3E', 'SS 3', 'E', 'SENIOR_SECONDARY'),
  ('SS2D', 'SS 2', 'D', 'SENIOR_SECONDARY'),
  ('SS1B', 'SS 1', 'B', 'SENIOR_SECONDARY'),

  -- Senior Primary
  ('B5P', 'Basic 5', 'P', 'SENIOR_PRIMARY'),
  ('B5R', 'Basic 5', 'R', 'SENIOR_PRIMARY'),
  ('B4S', 'Basic 4', 'S', 'SENIOR_PRIMARY'),
  ('B5S', 'Basic 5', 'S', 'SENIOR_PRIMARY'),
  ('B4R', 'Basic 4', 'R', 'SENIOR_PRIMARY'),
  ('B4 Ruby', 'Basic 4', 'Ruby', 'SENIOR_PRIMARY'),
  ('B5', 'Basic 5', 'General', 'SENIOR_PRIMARY'),

  -- Junior Primary
  ('B1P', 'Basic 1', 'P', 'JUNIOR_PRIMARY'),
  ('B3R', 'Basic 3', 'R', 'JUNIOR_PRIMARY'),
  ('B1R', 'Basic 1', 'R', 'JUNIOR_PRIMARY'),
  ('B2R', 'Basic 2', 'R', 'JUNIOR_PRIMARY'),
  ('B2P', 'Basic 2', 'P', 'JUNIOR_PRIMARY'),
  ('B3P', 'Basic 3', 'P', 'JUNIOR_PRIMARY'),

  -- Junior Secondary
  ('B8C', 'Basic 8', 'C', 'JUNIOR_SECONDARY'),
  ('B9B', 'Basic 9', 'B', 'JUNIOR_SECONDARY'),
  ('B7B', 'Basic 7', 'B', 'JUNIOR_SECONDARY'),
  ('B7E', 'Basic 7', 'E', 'JUNIOR_SECONDARY'),
  ('B9D', 'Basic 9', 'D', 'JUNIOR_SECONDARY'),
  ('B8B', 'Basic 8', 'B', 'JUNIOR_SECONDARY'),
  ('B8A', 'Basic 8', 'A', 'JUNIOR_SECONDARY'),
  ('B9', 'Basic 9', 'General', 'JUNIOR_SECONDARY'),
  ('B9E', 'Basic 9', 'E', 'JUNIOR_SECONDARY'),
  ('B7C', 'Basic 7', 'C', 'JUNIOR_SECONDARY'),
  ('B9C', 'Basic 9', 'C', 'JUNIOR_SECONDARY'),

  -- Nursery & Kindergarten
  ('Nur 2P', 'Nursery 2', 'P', 'NURSERY'),
  ('Nur 1S', 'Nursery 1', 'S', 'NURSERY'),
  ('Nur 2C', 'Nursery 2', 'C', 'NURSERY'),
  ('Nur 2R', 'Nursery 2', 'R', 'NURSERY'),
  ('Nur 1R', 'Nursery 1', 'R', 'NURSERY'),
  ('Nur 2S', 'Nursery 2', 'S', 'NURSERY'),
  ('KG 2R', 'Kindergarten 2', 'R', 'NURSERY'),
  ('KG 2P', 'Kindergarten 2', 'P', 'NURSERY'),
  ('Creche', 'Creche', 'General', 'NURSERY'),

  -- Institutional Scopes & Locations
  ('COLLEGE', 'Secondary', 'College Wide', 'SENIOR_SECONDARY'),
  ('Academics', 'Academic Scope', 'General', 'SENIOR_SECONDARY'),
  ('Academics (Secondary)', 'Academic Scope', 'Secondary', 'SENIOR_SECONDARY'),
  ('Duty', 'Staff Duty', 'Campus Wide', 'SENIOR_SECONDARY'),
  ('Directorate Primary Control Room', 'Operations', 'Facility', 'SENIOR_PRIMARY'),
  ('Staircase leading to Royal Class', 'Operations', 'Facility', 'SENIOR_SECONDARY'),
  ('Chem Lab', 'Academic', 'Laboratory', 'SENIOR_SECONDARY'),
  ('Campus Wide', 'General', 'Campus Wide', 'SENIOR_SECONDARY')
ON CONFLICT (name) DO UPDATE SET
  level = EXCLUDED.level,
  arm = EXCLUDED.arm,
  section = EXCLUDED.section;


-- -------------------------------------------------------------------------
-- STEP 2: ENSURE ALL REFERENCED SUBJECTS EXIST
-- -------------------------------------------------------------------------
INSERT INTO public.subjects (name, department, discipline_group) VALUES
  ('Physics', 'Academic', 'Physical Sciences'),
  ('LF', 'Academic', 'Languages'),
  ('Maths', 'Academic', 'Physical Sciences'),
  ('Sat Class (Phy Lab)', 'Academic', 'Physical Sciences'),
  ('CRS', 'Academic', 'Social Sciences'),
  ('Compo', 'Academic', 'Languages'),
  ('Civic', 'Academic', 'Social Sciences'),
  ('Digital Technology', 'Academic', 'Physical Sciences'),
  ('Lateness', 'Administrative', 'Social Sciences'),
  ('Speech', 'Academic', 'Languages'),
  ('Compu', 'Academic', 'Physical Sciences'),
  ('D. Tech', 'Academic', 'Physical Sciences'),
  ('Projector', 'Operations', 'Physical Sciences'),
  ('CHS', 'Academic', 'Social Sciences'),
  ('Computer', 'Academic', 'Physical Sciences'),
  ('Camera', 'Operations', 'Physical Sciences'),
  ('D/Tech', 'Academic', 'Physical Sciences'),
  ('Sat Class', 'Academic', 'Physical Sciences'),
  ('Digital Lit', 'Academic', 'Physical Sciences'),
  ('Agric Sci', 'Academic', 'Life Sciences'),
  ('Agric', 'Academic', 'Life Sciences'),
  ('English', 'Academic', 'Languages'),
  ('French', 'Academic', 'Languages'),
  ('Lit', 'Academic', 'Languages'),
  ('CCA', 'Academic', 'Social Sciences'),
  ('Compre', 'Academic', 'Languages'),
  ('Night Class', 'Academic', 'Physical Sciences'),
  ('Bio', 'Academic', 'Life Sciences'),
  ('Night Physics', 'Academic', 'Physical Sciences'),
  ('Basic Tech', 'Academic', 'Physical Sciences'),
  ('Maintenance', 'Operations', 'Physical Sciences'),
  ('Phonics', 'Academic', 'Languages'),
  ('R/R', 'Academic', 'Languages'),
  ('BOARD', 'Operations', 'Physical Sciences'),
  ('Wastebin in front of college block', 'Operations', 'Social Sciences'),
  ('Wastebin', 'Operations', 'Social Sciences'),
  ('Toilet', 'Operations', 'Social Sciences'),
  ('General', 'Academic', 'Physical Sciences')
ON CONFLICT (name) DO UPDATE SET
  department = EXCLUDED.department,
  discipline_group = EXCLUDED.discipline_group;


-- Normalize existing compliance reports to 2026/2027 session
UPDATE public.compliance_reports
SET session = '2026/2027'
WHERE session IS NULL OR session = '2024/2025';

-- -------------------------------------------------------------------------
-- STEP 3: INSERT / UPSERT ALL 125 COMPLIANCE REPORTS (ATOMIC CTE)
-- -------------------------------------------------------------------------
WITH temp_compliance_reports_seed (
  report_number, date, staff_name, staff_code, class_name, subject, check_id, division, target_type, exception, effect, severity, violation_level
) AS (
  VALUES
  -- =========================================================================
  -- DEPARTMENT 1: SENIOR SECONDARY (18 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0001', '2026-09-10'::DATE, 'A.J Providence', 'JCB-T-113', 'SS3D', 'Physics', 'ACA12', 'Academic', 'staff',
   'Taught without writing any specific objectives on the board', 'This confuses the students as they do not know the goal of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0002', '2026-09-10', 'Academics', 'JCB-A-001', 'COLLEGE', NULL, 'ACA30', 'Academic', 'division',
   'Bell was rung 10 minutes before stipulated closing time-2:30pm', 'This causes confusion amongst students and staff', 'MEDIUM', 2),
  ('JCB-2026-0003', '2026-09-15', 'Nwadialor Ijeoma', 'JCB-T-153', 'SS1C', 'LF', 'ACA12', 'Academic', 'staff',
   'She taught the previous class while Civic Specific objectives was still written on the board', 'This confuses the students', 'MEDIUM', 2),
  ('JCB-2026-0004', '2026-09-16', 'Anthony Ekeh', 'JCB-T-130', 'SS2A', NULL, 'ACA6', 'Academic', 'staff',
   'Class books were littered on the floor near the teacher table', 'This portrays the wrong values to the students', 'MEDIUM', 1),
  ('JCB-2026-0005', '2026-09-16', 'Mr Philimon Darshaka', 'JCB-T-118', 'SS2 Royal Class', 'Maths', 'ACA23', 'Academic', 'staff',
   'A student by name Hamila Lawal was sleeping in class while tutorial was ongoing', 'The student lost out on the tutorial delivered', 'MEDIUM', 2),
  ('JCB-2026-0006', '2026-09-16', 'Mr Godwin Ekeigwe', 'JCB-T-116', 'SS2B', 'Physics', 'ACA10', 'Academic', 'staff',
   '17 mins into the class, he has not started teaching on his specific objective.', 'Loss of tutorial time.', 'MEDIUM', 2),
  ('JCB-2026-0007', '2026-09-16', 'Mr Ifeanyi Obidimma', 'JCB-T-115', 'SS1D', 'Physics', 'ACA5', 'Academic', 'staff',
   'No assignment has been given as of now', 'The students lost out on the tutorial delivered', 'MEDIUM', 2),
  ('JCB-2026-0008', '2026-09-16', 'Mr Philimon Darshaka', 'JCB-T-118', 'SS2 Royal Class', 'Maths', 'ACA2', 'Academic', 'staff',
   'Teaching out of scheme. Topic taught was Statistics and there is no statistics on the scheme.', 'This will lead to comfusion and derail the students', 'HIGH', 3),
  ('JCB-2026-0009', '2026-09-17', 'Academics (Secondary)', 'JCB-A-002', 'SS1E', NULL, 'ACA15', 'Academic', 'division',
   'The population of the class is much and the seats are not enough to accommodate the population as most of the students sit 3 in a seat', 'This causes discomfort to the students during tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0010', '2026-09-18', 'Academics (Secondary)', 'JCB-A-002', 'COLLEGE', NULL, 'ACA11', 'Academic', 'division',
   'As at 7:52am, the staircase opposite the boys toilet leading to Junior secondary classes was still locked', 'This could lead to delay in teachers arriving to their class', 'MEDIUM', 2),
  ('JCB-2026-0011', '2026-09-19', 'Providence', 'JCB-T-113', 'SS1', 'Sat Class (Phy Lab)', 'ACA18', 'Academic', 'staff',
   'Sensitive: The class was in chaos as many students were seen outside the class playing, gisting and disturbing the school.', 'Learning is practically impossible under this condition', 'HIGH', 4),
  ('JCB-2026-0012', '2026-09-19', 'Peter', 'JCB-T-174', 'SS3', 'CRS', 'ACA21', 'Academic', 'staff',
   'The class was rowdy and no teacher was put in as a substitute', 'This led to wastage in students’ learning time', 'HIGH', 3),
  ('JCB-2026-0013', '2026-09-21', 'Zugu Armstrong', 'JCB-T-170', 'Royal Class', 'Compo', 'ACA13', 'Academic', 'staff',
   'Only girls were assigned to a group during flexible grouping. Sample names are Enenche Treasure, Hafsah', 'This is in violation of the school rules', 'MEDIUM', 2),
  ('JCB-2026-0014', '2026-09-21', 'Zugu Armstrong', 'JCB-T-170', 'Royal Class', 'Compo', 'ACA23', 'Academic', 'staff',
   'A student by name Kamsi Nwakuba slept for more than 1 mins during tutorial delivery', 'The students missed out during tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0015', '2026-09-22', 'Nwadialor', 'JCB-T-153', 'SS1C', 'LF', 'ACA12', 'Academic', 'staff',
   'She taught the students while Grammar specific objectives were still written on the board', 'This confuses the learners', 'MEDIUM', 2),
  ('JCB-2026-0016', '2026-09-23', 'Ijeoma Nwadialor', 'JCB-T-153', 'SS1A', 'LF', 'ACA13', 'Academic', 'staff',
   'During flexible grouping 6 people were assigned to a group. Sample name includes: Ulonnam Amarachi', 'This is in violation of the school rules', 'MEDIUM', 2),
  ('JCB-2026-0017', '2026-09-24', 'Ijeoma Nwadialor', 'JCB-T-153', 'SS2B', 'LF', 'ACA5', 'Academic', 'staff',
   'Assignment has not been given by the teacher since the beginning of the term', 'This prevents evaluation of students on what has been taught', 'MEDIUM', 2),
  ('JCB-2026-0018', '2026-09-24', 'Francis Terdo', 'JCB-T-175', 'SS1D', 'Civic', 'ACA25', 'Academic', 'staff',
   'No topic was written on the board during tutorial delivery', 'This lead to difficulty in following focus on lesson objectives', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 2: SENIOR PRIMARY (7 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0019', '2026-09-14', 'Mr Tite John', 'JCB-T-176', 'B5P', 'Maths', 'ACA1', 'Academic', 'staff',
   'He was seen discussing for more than 3 mins with Mrs Okpana during class hour by the window', 'He left the learners unattended to', 'MEDIUM', 2),
  ('JCB-2026-0020', '2026-09-15', 'Aaron Yayok', 'JCB-T-177', 'B5P', 'Digital Technology', 'ACA12', 'Academic', 'staff',
   'No specific objectives was written on the board for the class', 'This confuses the students', 'MEDIUM', 2),
  ('JCB-2026-0021', '2026-09-23', NULL, NULL, 'B5R', 'Lateness', 'ACA11', 'Academic', 'division',
   'As of 7:05am the classroom door was still locked', 'This prevents students from having assess to their class', 'MEDIUM', 2),
  ('JCB-2026-0022', '2026-09-24', 'Esther Idah', 'JCB-T-178', 'B4S', 'Speech', 'ACA30', 'Academic', 'staff',
   'She did not follow the teaching regimentation. She gave class exercise immediately after writing specific objectives and was moving round to mark pupils note. No teaching or explanation was done for the whole 40 minutes', 'The students leant no new knowledge during this period', 'MEDIUM', 2),
  ('JCB-2026-0023', '2026-09-24', 'Esther Idah', 'JCB-T-178', 'B4S', 'Speech', 'ACA5', 'Academic', 'staff',
   'Note and assignments have not been marked since it was given at the beginning term Sample notes include: Jovanna Efe, Adejare Praise and Chinetugo Odum', 'This discourages students from coping notes and doing their assignments', 'MEDIUM', 2),
  ('JCB-2026-0024', '2026-09-24', 'Franklin', 'JCB-T-179', 'B4S', 'Compu', 'ACA1', 'Academic', 'staff',
   'He hung his hands on a female pupil’s shoulders as he explains while teaching', 'This might send the wrong signal to the pupils in class', 'MEDIUM', 2),
  ('JCB-2026-0025', '2026-09-24', 'Aaron Yayok', 'JCB-T-177', 'B5S', 'D. Tech', 'ACA7', 'Academic', 'staff',
   'He used a faint marker during tutorial delivery', 'The pupils struggled to see what was written clearly on the board', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 3: TRANSPORT (11 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0026', '2026-09-14', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses brought students late to school: BUS 466…7:48AM…..IFEANYI, BUS 474…7:48AM…..JOHN ANI, BUS 475…7:49AM…..IBRAHIM, BUS 471…7:49AM…..SUNDAY, BUS 469…7:57AM…..PETER, BUS 882…8:00AM…..JETRO', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0027', '2026-09-15', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses left late from school to drop students: BUS 613…4:47PM…..EMMANUEL, BUS 613….NO MINDER IN BUS, BUS 469…4:49PM…..PETER, BUS 467…4:49PM…..PAUL, BUS 467…NO MINDER IN BUS, BUS 465…4:53PM…..DAVID, BUS 983…4:53PM…..PATRICK, BUS 475…4:54PM…..IBRAHIM, BUS 456…4:54PM…..IFEANYI', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0028', '2026-09-16', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses brought students late to school: BUS 475…7:48AM…..IBRAHIM, BUS 883…7:51AM…..JOHN ANI, BUS 471…7:52AM…..SUNDAY', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0029', '2026-09-16', 'Transport', 'JCB-O-001', 'Campus Wide', NULL, 'OPR7', 'Operation', 'division',
   'Bus 17 broke down on the road and bus 23 went to pick the students from the road and arrived school by 8:30am', 'This made the students came late to school', 'HIGH', 3),
  ('JCB-2026-0030', '2026-09-16', 'Transport', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'Bus 18 left the school late by 5:37PM to drop students home', 'Students would arrive home late', 'MEDIUM', 2),
  ('JCB-2026-0031', '2026-09-19', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses brought students late to school: BUS 985…8:59AM…..PATRICK, BUS 475…9:16AM…..IBRAHIM, BUS 465…9:16AM…..DAVID AGWU, BUS 882…9:25AM…..JETHRO', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0032', '2026-09-21', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses left late from school to drop students: BUS 882…4:49PM…..JETHRO, BUS 985…4:52PM…..PATRICK, BUS 883…4:52PM…..JULIUS, BUS 181…4:56PM…..IFEANYI, BUS 181…NO MINDER PRESENT, BUS 467…NO MINDER PRESENT', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0033', '2026-09-22', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses brought students late to school: BUS 181…7:46AM…..IFEANYI, BUS 475…7:50AM…..IBRAHIM', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0034', '2026-09-22', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses left late from school to drop students: BUS 181…4:49PM…..IFEANYI, BUS 613…4:49PM…..EMMANUEL, BUS 465…4:50PM…..DAVID AGWU, BUS 467…4:50PM…..PAUL SIMON, BUS 472…4:52PM…..SUNDAY, BUS 469…4:52PM…..PETER ALEX, BUS 466…4:53PM…..JOSEPH, BUS 985…4:55PM…..PATRICK, BUS 474…4:5PM…..DANLADI, BUS 472…4:57PM…..JOHN ANI, BUS 883…4:56PM…..JULIUS', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0035', '2026-09-23', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses left late from school to drop students: BUS 472…4:46PM…..JOHN ANI, BUS 985…4:47PM…..PATRICK, BUS 882…4:47PM…..JETHRO, BUS 469…4:47PM…..PETER ALEX, BUS 466…4:48PM…..JOSEPH, BUS 475…4:48PM…..IBRAHIM, BUS 467…4:49PM…..PAUL SIMON, BUS 181…4:50PM…..IFEANYI, BUS 883…4:51PM…..JULIUS', 'De-market the school', 'HIGH', 3),
  ('JCB-2026-0036', '2026-09-26', 'TRANSPORT', 'JCB-O-001', 'Campus Wide', NULL, 'OPR6', 'Operation', 'division',
   'The following buses brought students late to school: BUS 471…9:03AM…..SUNDAY, BUS 883…9:12AM…..JULUS, BUS 985…9:16AM…..PATRICK, BUS 465…9:18AM…..DAVID AGWU', 'De-market the school', 'HIGH', 3),

  -- =========================================================================
  -- DEPARTMENT 4: CDS (25 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0037', '2026-09-08', 'CDS', 'JCB-C-001', 'B5S', 'Projector', 'CDS7', 'CDS', 'division',
   'Camera is not well positioned to view the board and teacher during tutorial delivery', 'This hinders teachers’ appraisal through the control room', 'MEDIUM', 2),
  ('JCB-2026-0038', '2026-09-08', 'CDS', 'JCB-C-001', 'B3P', 'Projector', 'CDS7', 'CDS', 'division',
   'Camera is not well positioned to view the students during tutorial delivery', 'This obstructs assessment', 'MEDIUM', 2),
  ('JCB-2026-0039', '2026-09-08', 'CDS', 'JCB-C-001', 'B3R', NULL, 'ACA18', 'CDS', 'division',
   'This class is over populated', 'This make tutorial delivery difficult as the class is noisy', 'MEDIUM', 2),
  ('JCB-2026-0040', '2026-09-10', 'CDS', 'JCB-C-001', 'B5S', NULL, 'CDS7', 'CDS', 'division',
   'Camera is not properly positioned to capture the board and teachers', 'Inability to access teacher’s work on the board by the directorate officers', 'MEDIUM', 2),
  ('JCB-2026-0041', '2026-09-10', 'Francis Tedo', 'JCB-T-175', 'B9B', 'CHS', 'ACA12', 'Academic', 'staff',
   '14 minutes into the class, no specific objectives has been written', 'This confuses the students as they do not know the goal of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0042', '2026-09-10', 'Francis Tedo', 'JCB-T-175', 'B9B', 'CHS', 'ACA25', 'Academic', 'staff',
   '14 minutes into the class, no subject or topic was written on the board', 'This confuses the students as they do not know the goal of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0043', '2026-09-10', 'CDS', 'JCB-C-001', 'B4R', NULL, 'CDS7', 'CDS', 'division',
   'Camera is not properly positioned to capture the board and teachers', 'Inability to access teacher’s work on the board by the directorate officers', 'MEDIUM', 2),
  ('JCB-2026-0044', '2026-09-10', 'CDS', 'JCB-C-001', 'B5R', NULL, 'CDS7', 'CDS', 'division',
   'Camera is not properly positioned to capture the board and teachers', 'Inability to access teacher’s work on the board by the directorate officers', 'MEDIUM', 2),
  ('JCB-2026-0045', '2026-09-10', 'CDS', 'JCB-C-001', 'B2R', NULL, 'CDS7', 'CDS', 'division',
   'Camera is not properly positioned to capture the board and teachers', 'Inability to access teacher’s work on the board by the directorate officers', 'MEDIUM', 2),
  ('JCB-2026-0046', '2026-09-14', 'Franklin', 'JCB-T-179', 'B5S', 'Computer', 'ACA8', 'Academic', 'staff',
   'Poorly written specific objectives', 'This confuses the students', 'MEDIUM', 2),
  ('JCB-2026-0047', '2026-09-14', 'CDS', 'JCB-C-001', 'SS3E', 'Projector', 'CDS2', 'CDS', 'division',
   'The Projector cable is spoilt', 'It prevents the teacher from using the projector and providing value addition', 'MEDIUM', 2),
  ('JCB-2026-0048', '2026-09-15', 'CDS', 'JCB-C-001', 'Nur 2R', NULL, 'CDS2', 'CDS', 'division',
   'There is no television in the class', 'This led to ineffective tutorial delivery as the teacher had to use her phone to display video', 'MEDIUM', 2),
  ('JCB-2026-0049', '2026-09-15', 'Aaron Yayok', 'JCB-T-177', 'B5P', 'Digital Technology', 'ACA12', 'Academic', 'staff',
   'No specific objectives was written on the board for the class', 'This confuses the students', 'MEDIUM', 2),
  ('JCB-2026-0050', '2026-09-16', 'CDS', 'JCB-C-001', 'SS3A', 'Camera', 'CDS7', 'CDS', 'division',
   'The Camera is not well positioned', 'This hinders appraisal of teachers from directorate', 'MEDIUM', 2),
  ('JCB-2026-0051', '2026-09-16', 'CDS', 'JCB-C-001', 'B7C', 'Projector', 'CDS2', 'CDS', 'division',
   'The projector is faulty', 'This hinders tutorial delivery as videos and pictures cannot be projected', 'MEDIUM', 2),
  ('JCB-2026-0052', '2026-09-16', 'CDS', 'JCB-C-001', 'SS2D', 'Camera', 'CDS7', 'CDS', 'division',
   'The Camera is not well positioned', 'This hinders appraisal of teachers from directorate', 'MEDIUM', 2),
  ('JCB-2026-0053', '2026-09-17', 'Chika Arikpo', 'JCB-T-124', 'SS2B', 'D/Tech', 'ACA21', 'Academic', 'staff',
   '10 mins into the class, she was absent', '25% loss of tutorial time', 'MEDIUM', 2),
  ('JCB-2026-0054', '2026-09-17', 'CDS', 'JCB-C-001', 'SS1D', 'Projector', 'CDS2', 'CDS', 'division',
   'The Projector in this class is not functional', 'This prevents proper display of what is written on the board', 'MEDIUM', 2),
  ('JCB-2026-0055', '2026-09-19', 'CDS', 'JCB-C-001', 'B5', 'Sat Class', 'ACA18', 'CDS', 'division',
   'As of 11:50am, 14 students were left unattended to in their class after they were done with CBT but the period was not over', 'This led to extreme noise in the classroom', 'HIGH', 3),
  ('JCB-2026-0056', '2026-09-21', 'CDS', 'JCB-C-001', 'B9C', 'Camera', 'CDS7', 'CDS', 'division',
   'The Camera is wrongly placed as the board cannot be viewed', 'This hinders teachers’ assessment from the control room', 'MEDIUM', 2),
  ('JCB-2026-0057', '2026-09-23', 'Franklin', 'JCB-T-179', 'B2R', 'Digital Lit', 'ACA5', 'Academic', 'staff',
   'Assignment has not been given to the students since the beginning of the term. Sample notes includes: Chizitere Okonkwo, Ferdinand Naron and Nancy', 'The prevents evaluation of the students', 'MEDIUM', 2),
  ('JCB-2026-0058', '2026-09-24', 'Franklin', 'JCB-T-179', 'B4S', 'Compu', 'ACA1', 'Academic', 'staff',
   'He hung his hands on a female pupil’s shoulders as he explains while teaching', 'This might send the wrong signal to the pupils in class', 'MEDIUM', 2),
  ('JCB-2026-0059', '2026-09-24', 'Aaron Yayok', 'JCB-T-177', 'B5S', 'D. Tech', 'ACA7', 'Academic', 'staff',
   'He used a faint marker during tutorial delivery', 'The pupils struggled to see what was written clearly on the board', 'MEDIUM', 2),
  ('JCB-2026-0060', '2026-09-24', 'CDS', 'JCB-C-001', 'B2R', NULL, 'CDS2', 'CDS', 'division',
   'Faulty Projector', 'This hinders tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0061', '2026-09-25', 'CDS', 'JCB-C-001', 'SS1B', 'Camera', 'CDS7', 'CDS', 'division',
   'The camera is not well positioned. The view is upside down', 'This hinders and prevents effective monitoring of the teachers  and class activities from the control room', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 5: JUNIOR SECONDARY (19 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0062', '2026-09-10', 'Anthony Ekeh', 'JCB-T-130', 'B8C', 'Agric Sci', 'ACA9', 'Academic', 'staff',
   'He came 20 minutes late', '50% loss of tutorial class', 'MEDIUM', 2),
  ('JCB-2026-0063', '2026-09-10', 'Francis Tedo', 'JCB-T-175', 'B9B', 'CHS', 'ACA12', 'Academic', 'staff',
   '14 minutes into the class, no specific objectives has been written', 'This confuses the students as they do not know the goal of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0064', '2026-09-10', 'Francis Tedo', 'JCB-T-175', 'B9B', 'CHS', 'ACA25', 'Academic', 'staff',
   '14 minutes into the class, no subject or topic was written on the board', 'This confuses the students as they do not know the goal of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0065', '2026-09-14', 'Isaiah Michael', 'JCB-T-161', 'B7B', 'Compre', 'ACA13', 'Academic', 'staff',
   'Six students were assigned to a group during flexible grouping', 'This is in violation of the school rules', 'MEDIUM', 2),
  ('JCB-2026-0066', '2026-09-14', 'Godwin Ekeh', 'JCB-T-163', 'B7E', 'English', 'ACA29', 'Academic', 'staff',
   'The reference standardized exam question is inaccurate: ‘Give two examples of an abstract noun’', 'This is misleading for the students', 'MEDIUM', 2),
  ('JCB-2026-0067', '2026-09-14', 'Joel Samuel', 'JCB-T-171', 'B9D', 'Speech', 'ACA9', 'Academic', 'staff',
   'He came 10 minutes late to class', '25% loss of tutorial time', 'MEDIUM', 2),
  ('JCB-2026-0068', '2026-09-14', 'Favour Imole', 'JCB-T-180', 'B8B', 'Compre', 'ACA12', 'Academic', 'staff',
   '5 mins into the class, no specific objectives have been written', 'This led to delay in tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0069', '2026-09-14', 'John Providence', 'JCB-T-113', 'B8A', 'Physics', 'ACA4', 'Academic', 'staff',
   'Non-detailed scheme of work', 'Lack of information will hinder the student and the accessor from understanding what is going on', 'MEDIUM', 2),
  ('JCB-2026-0070', '2026-09-19', 'Thompson Ododot', 'JCB-T-146', 'B9', 'Night Class', 'ACA9', 'Academic', 'staff',
   '10 mins late for night class', '16.7% loss of tutorial time', 'MEDIUM', 2),
  ('JCB-2026-0071', '2026-09-19', 'Godfrey', 'JCB-T-134', 'B9', 'Bio', 'ACA21', 'Academic', 'staff',
   'The teacher completely missed his class', '100% loss of tutorial time', 'HIGH', 3),
  ('JCB-2026-0072', '2026-09-21', 'Joel Samuel', 'JCB-T-171', 'B7B', 'Speech', 'ACA13', 'Academic', 'staff',
   'There were more than 5 in a group. Sample names includes: Judah, Godwin Regan, Chisom Aturobi', 'This is in violation of the school rules', 'MEDIUM', 2),
  ('JCB-2026-0073', '2026-09-21', 'Samuel Joel', 'JCB-T-171', 'Duty', NULL, 'ACA9', 'Academic', 'staff',
   'Came 12 mins late to morning duty', 'This is in violation to the school rules', 'MEDIUM', 2),
  ('JCB-2026-0074', '2026-09-21', 'Odey Nichodemus', 'JCB-T-181', 'Duty', NULL, 'ACA9', 'Academic', 'staff',
   'Came 8 mins late to morning duty', 'This is in violation to the school rules', 'MEDIUM', 2),
  ('JCB-2026-0075', '2026-09-21', 'Chiedozie Prosper', 'JCB-T-143', 'B8A', 'Civic', 'ACA5', 'Academic', 'staff',
   'Notes given on the 7th of September has not been marked. Sample notes includes: Kedi Arodesode, Ugwu King David, Labetek Samuel and Chizaram Okonkwo', 'Corrections and Ommissions can’t be corrected', 'MEDIUM', 2),
  ('JCB-2026-0076', '2026-09-21', 'Bello Favour', 'JCB-T-169', 'B8B', 'Compre', 'ACA13', 'Academic', 'staff',
   'In one of the groups formed during flexible grouping, there was only girls assigned in that group. Some of the names of the students are: Gabriella Anikamadu, Shogbamu Abisola, Kalia Abuh', 'This is in violation of the school rules', 'MEDIUM', 2),
  ('JCB-2026-0077', '2026-09-22', 'Eleje Michael', 'JCB-T-109', 'B9E', 'Maths', 'ACA28', 'Academic', 'staff',
   'He slapped a student by name Ibekwe Munachi on his mouth', 'This could lead to bruising and it violates the school rules', 'HIGH', 3),
  ('JCB-2026-0078', '2026-09-22', 'Michael Isaiah', 'JCB-T-161', 'B7C', 'Compre', 'ACA5', 'Academic', 'staff',
   'No assignment has been given since the beginning of the term. Sample notes include: Uzoma Chiemezie, Hanniel Chidi and Khatra Abdulraheem', 'This hinders evaluation of students on what has been taught', 'MEDIUM', 2),
  ('JCB-2026-0079', '2026-09-24', 'Ifeanyi Obidinma', 'JCB-T-115', 'B9', 'Night Class', 'ACA12', 'Academic', 'staff',
   'He taught without specific objectives', 'This confuses the learners as they do not know the aim of the tutorial', 'MEDIUM', 2),
  ('JCB-2026-0080', '2026-09-25', 'Daniel Mgbemere', 'JCB-T-128', 'B7B', 'Basic Tech', 'ACA5', 'Academic', 'staff',
   'Notes  and assignment given on the 12th of September have not been marked. Sample notes includes Mac Emmanuel, Matthew Prosper and Reagan Godwin.', 'This encourages students not to copy notes and do their assignments', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 6: MAINTENANCE (18 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0081', '2026-09-10', 'Operation', 'JCB-O-002', 'Directorate Primary Control Room', 'Maintenance', 'OPR19', 'Operation', 'division',
   'Faulty/Noisy fan', 'Causes Noise pollution and can lead to accident', 'MEDIUM', 2),
  ('JCB-2026-0082', '2026-09-14', 'Operations', 'JCB-O-002', 'SS3E', 'Maintenance', 'ACA6', 'Operation', 'division',
   'The classroom board is dirty', 'This prevents proper display of what is written on the board', 'MEDIUM', 2),
  ('JCB-2026-0083', '2026-09-14', 'Operations', 'JCB-O-002', NULL, 'Maintenance', 'OPR19', 'Operation', 'division',
   'The Fan in Mrs Esther Ezeike’s office is faulty. It makes noise when in use.', 'This lowers the standard of the school', 'MEDIUM', 2),
  ('JCB-2026-0084', '2026-09-15', 'Operations', 'JCB-O-002', 'B8A', 'Maintenance', 'OPR17', 'Operation', 'division',
   'Some tiles in this class are broken', 'This can lead to injury prone accident to students and staff', 'HIGH', 3),
  ('JCB-2026-0085', '2026-09-15', 'Operations', 'JCB-O-002', 'KG 2R', 'Maintenance', 'OPR24', 'Operation', 'division',
   'The Air Conditioner in the classroom is faulty', 'Extreme heat could affect learners’ ability to learn', 'MEDIUM', 2),
  ('JCB-2026-0086', '2026-09-15', 'Operations', 'JCB-O-002', 'KG 2R', 'Maintenance', 'OPR22', 'Operation', 'division',
   'Faulty washing Hand Basin in the toilet', 'This leads to poor hygiene', 'MEDIUM', 2),
  ('JCB-2026-0087', '2026-09-15', 'Operations', 'JCB-O-002', 'KG 2P', 'Maintenance', 'OPR22', 'Operation', 'division',
   'Faulty ceiling in the bathroom in which water pass through during rainfall', 'This further damages the ceiling and the toilet tiles', 'MEDIUM', 2),
  ('JCB-2026-0088', '2026-09-15', 'Operations', 'JCB-O-002', 'B8A', 'Maintenance', 'OPR16', 'Operation', 'division',
   'The Teacher’s chair in this class is faulty', 'This causes discomfort to the teacher while teaching', 'MEDIUM', 2),
  ('JCB-2026-0089', '2026-09-15', 'Operations', 'JCB-O-002', NULL, 'Maintenance', 'OPR32', 'Operation', 'division',
   'The following classes do not have an identification name plate at the entrance to the class: Creche, KG2P, KG2R, Nur 1R, Nur 1S, Nur 2P and Nur 2S', 'This causes difficulty in identifying classes for parents and staffs', 'MEDIUM', 2),
  ('JCB-2026-0090', '2026-09-16', 'Operations', 'JCB-O-002', 'B4R', 'Maintenance', 'ACA6', 'Operation', 'division',
   'The shelves in the classroom are not sufficient to accommodate all the bags and lunch boxes of the students', 'This causes the classroom to be disorganised', 'MEDIUM', 2),
  ('JCB-2026-0091', '2026-09-16', 'Operations', 'JCB-O-002', 'B4 Ruby', 'Maintenance', 'ACA6', 'Operation', 'division',
   'Shelve not big enough, space not sufficient to accommodate the pupils’ bags', 'Bags on the floor – not a good image for the class, take up space, potential harm to the bags', 'MEDIUM', 2),
  ('JCB-2026-0092', '2026-09-17', 'Operation', 'JCB-O-002', 'Staircase leading to Royal Class', 'Maintenance', 'OPR17', 'Operation', 'division',
   'Broken Tiles', 'This can lead to injury prone accident to staff and students', 'HIGH', 3),
  ('JCB-2026-0093', '2026-09-19', 'Operations', 'JCB-O-002', NULL, 'Maintenance', 'OPR27', 'Operation', 'division',
   'After Saturday Evening sport, there was no water in the boys hostel and major areas in the school for the students to bathe', 'Many of the students could not take their bath and went to night class smelling and in discomfort', 'HIGH', 3),
  ('JCB-2026-0094', '2026-09-19', 'Operations', 'JCB-O-002', NULL, 'Maintenance', 'OPR27', 'Operation', 'division',
   'There has been no water in the girl’s hostel since morning', 'This prevented them from taking their bath and drinking of water', 'HIGH', 3),
  ('JCB-2026-0095', '2026-09-22', 'Operations', 'JCB-O-002', 'SS1D', 'Maintenance', 'CDS2', 'Operation', 'division',
   'Faulty Class Projector', 'This hinders effective tutorial delivery as videos and pictures cannot be projected', 'MEDIUM', 2),
  ('JCB-2026-0096', '2026-09-22', 'Operations', 'JCB-O-002', 'Nur 1S', 'Maintenance', 'CDS2', 'Operation', 'division',
   'Faulty Television', 'This hinders effective tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0097', '2026-09-22', 'Operations', 'JCB-O-002', 'Nur 2P', 'Maintenance', 'OPR16', 'Operation', 'division',
   'Faulty Teacher Chair', 'This causes discomfort for the teachers', 'MEDIUM', 2),
  ('JCB-2026-0098', '2026-09-22', 'Operations', 'JCB-O-002', 'Nur 2S', 'Maintenance', 'CDS2', 'Operation', 'division',
   'There is no Television in the class', 'This hinders effective tutorial delivery', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 7: MENTORSHIP (2 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0099', '2026-09-09', 'Mentorship', 'JCB-D-001', 'COLLEGE', 'BOYS', 'DIS8', 'Discipline', 'division',
   'Today being the beginning of night prep, the class block was noisy and disorganized. Students didn’t observe the night prep', 'Night Prep was ineffective', 'MEDIUM', 2),
  ('JCB-2026-0100', '2026-09-10', 'Mentorship', 'JCB-D-001', 'COLLEGE', 'BOYS', 'DIS8', 'Discipline', 'division',
   'Today being the beginning of night prep, the class block was noisy and disorganized. Students didn’t observe the night prep', 'Night Prep was ineffective', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 8: MINDERS (6 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0101', '2026-09-10', 'Minder', 'JCB-O-003', 'SS3D', 'BOARD', 'ACA6', 'Operation', 'division',
   'The Board is very dirty with ink', 'This reduces visibility of words written on the board', 'MEDIUM', 2),
  ('JCB-2026-0102', '2026-09-16', 'Minders', 'JCB-O-003', 'COLLEGE', 'Wastebin in front of college block', 'OPR21', 'Operation', 'division',
   'As of 7:02am, the waste bin in front of college block has not been trashed', 'This made the environment dirty', 'MEDIUM', 2),
  ('JCB-2026-0103', '2026-09-17', 'Minders', 'JCB-O-003', 'Chem Lab', NULL, 'OPR21', 'Operation', 'division',
   'As at 7:17am, Refuse was not disposed of', 'This affects environmental health', 'MEDIUM', 2),
  ('JCB-2026-0104', '2026-09-21', 'Minder', 'JCB-O-003', 'COLLEGE', 'Wastebin', 'OPR21', 'Operation', 'division',
   'As of 7:00am, the wastebin in front of the entrance of the college block was still filled up', 'This leads to the spread of diseases and germs to students and staff', 'HIGH', 3),
  ('JCB-2026-0105', '2026-09-21', 'MINDER', 'JCB-O-003', 'B7B', NULL, 'ACA6', 'Operation', 'division',
   'The board in this class is very dirty', 'Subsequent writings on the board were not clear', 'MEDIUM', 2),
  ('JCB-2026-0106', '2026-09-22', 'Minders', 'JCB-O-003', 'B2P', 'Toilet', 'OPR29', 'Operation', 'division',
   'The Primary block and corridor from the toilet to basic 2P stinks badly with urine', 'This causes discomfort to pupils and staff passing by', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 9: NURSERY AND KINDERGARTEN (5 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0107', '2026-09-14', 'Ann Ijeoma', 'JCB-T-182', 'Nur 2P', NULL, 'ACA28', 'Academic', 'staff',
   'She slapped a child', 'This is in violation of the school rules', 'HIGH', 3),
  ('JCB-2026-0108', '2026-09-15', 'Alheri Ali', 'JCB-T-183', 'Nur 1S', 'Phonics', 'ACA9', 'Academic', 'staff',
   'Started Phonics lesson 30 mins late', 'This will lead to a rush in tutorial delivery', 'MEDIUM', 2),
  ('JCB-2026-0109', '2026-09-16', 'Nnodi Happiness', 'JCB-T-184', 'Nur 2C', 'R/R', 'ACA12', 'Academic', 'staff',
   'No specific objectives were written on the board for the lesson', 'The lesson lacks direction and focus.', 'MEDIUM', 2),
  ('JCB-2026-0110', '2026-09-17', 'Isa Blessing', 'JCB-T-185', 'KG 2R', NULL, 'OPR16', 'Academic', 'staff',
   'Sat on a pupil’s desk in class', 'This will damage the desk as it is not built for such function', 'MEDIUM', 2),
  ('JCB-2026-0111', '2026-09-21', 'Solomon Deborah', 'JCB-T-186', 'Nur 2R', NULL, 'OPR16', 'Academic', 'staff',
   'She sat on the pupil’s desk in class', 'This could damage the desk as the desk is not built for such function', 'MEDIUM', 2),

  -- =========================================================================
  -- DEPARTMENT 10: SECURITY (2 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0112', '2026-09-10', 'Security', 'JCB-O-004', 'COLLEGE', 'Main Entrance to college Building', 'OPR33', 'Operation', 'division',
   'No security personnel was stationed at the entrance gate to the college building as at 10:36am', 'No proper checking of who comes into the college which place the school at risk', 'HIGH', 3),
  ('JCB-2026-0113', '2026-09-18', 'Security Personnel', 'JCB-O-004', 'COLLEGE', NULL, 'OPR33', 'Operation', 'division',
   'As of 2:06pm, no security personnel was positioned at the entrance of the college block', 'Students used the opportunity to leave their classroom', 'HIGH', 3),

  -- =========================================================================
  -- DEPARTMENT 11: JUNIOR PRIMARY (10 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0114', '2026-09-08', 'Esther Idah', 'JCB-T-178', 'B1P', 'Speech', 'ACA12', 'Academic', 'staff',
   'The specific objectives were written 12 minutes into the class', 'This deprived students of familiarizing with the objectives of the lesson on time', 'MEDIUM', 2),
  ('JCB-2026-0115', '2026-09-14', 'Nicholight Goodness', 'JCB-T-187', 'B3R', 'Maths', 'ACA23', 'Academic', 'staff',
   'Two students by name Hamza and Yahaya were sleeping while tutorial was ongoing', 'The students lost out on the tutorial delivered', 'MEDIUM', 2),
  ('JCB-2026-0116', '2026-09-15', 'Maxwell Bentu', 'JCB-T-188', 'B1R', 'Agric', 'ACA10', 'Academic', 'staff',
   '6 mins into the class, tutorial has not commenced.', '15% loss of tutorial time', 'MEDIUM', 2),
  ('JCB-2026-0117', '2026-09-15', 'Toyin Sunday', 'JCB-T-189', 'B1R', 'English', 'ACA1', 'Academic', 'staff',
   'She was seen discussing with another teacher during tutorial time', 'This is an unprofessional act and breaks learning', 'MEDIUM', 2),
  ('JCB-2026-0118', '2026-09-23', 'Blessing Egbonu', 'JCB-T-190', 'B2R', 'French', 'ACA5', 'Academic', 'staff',
   'Notes given on the 7th of September has not been marked. Sample notes includes: Valerie Williams, Fabi Amina, Bethel Bamal and King David', 'This encourages students not to copy notes', 'MEDIUM', 2),
  ('JCB-2026-0119', '2026-09-23', 'Patience George', 'JCB-T-191', 'B3R', 'Lit', 'ACA5', 'Academic', 'staff',
   'Assignments given on the 16th of September has not been marked. Sample notes include: Dominion Samuel and Valarie Agujiobi', 'This discourages students from doing assignments', 'MEDIUM', 2),
  ('JCB-2026-0120', '2026-09-23', 'Maduka Beatrice', 'JCB-T-192', 'B1P', 'CCA', 'ACA5', 'Academic', 'staff',
   'No assignment has been given since the beginning of the term. Sample notes includes Emmanuel Ebube, Jedidiah Rex and Zara Nwobodo', 'This prevents students from doing assignments', 'MEDIUM', 2),
  ('JCB-2026-0121', '2026-09-24', 'Maxwell', 'JCB-T-188', 'B2P', 'Agric', 'ACA18', 'Academic', 'staff',
   'Lacks Class Control as the classroom was rowdy and filled with noise', 'This causes distractions and make the environment unfit for learning', 'MEDIUM', 2),
  ('JCB-2026-0122', '2026-09-24', 'Onyemechi Peace', 'JCB-T-193', 'B2P', 'Compo', 'ACA18', 'Academic', 'staff',
   'Poor class control as two learners were seen fighting during her class', 'This disrupts tutorial delivery', 'HIGH', 3),
  ('JCB-2026-0123', '2026-09-24', 'Onyemechi Peace', 'JCB-T-193', 'B3R', 'Speech', 'ACA28', 'Academic', 'staff',
   'She beat a student in the course of her tutorial delivery', 'This is in violation of the school rules', 'HIGH', 3),

  -- =========================================================================
  -- DEPARTMENT 12: DISCIPLINE (2 ENTRIES)
  -- =========================================================================
  ('JCB-2026-0124', '2026-09-15', 'Discipline', 'JCB-D-002', 'B7C', NULL, 'DIS5', 'Discipline', 'division',
   'A student by name Azariah Jayden came to school with the wrong hairstyle', 'This is in violation of the school’s dress code for students', 'MEDIUM', 2),
  ('JCB-2026-0125', '2026-09-15', 'Discipline Woman', 'JCB-D-002', 'Campus Wide', NULL, 'ACA9', 'Discipline', 'staff',
   'Reported late to duty on Tuesday', 'This violates the school rules on promptness', 'MEDIUM', 2)
)
INSERT INTO public.compliance_reports (
  report_number,
  reporter_name,
  reporting_officer,
  reporter_role,
  incident_date,
  date,
  week_number,
  term,
  session,
  exception,
  exception_text,
  effect,
  effect_text,
  severity,
  category,
  division,
  target_type,
  violation_level,
  staff_id,
  staff_name,
  staff_code,
  staff_category,
  staff_group,
  role,
  department,
  check_id,
  compliance_check,
  class_id,
  class_name,
  subject_id,
  subject,
  evidence_type,
  status,
  review_status,
  is_seen,
  created_at,
  updated_at
)
SELECT DISTINCT ON (t.report_number)
  t.report_number,
  'Directorate Quality Assurance' AS reporter_name,
  'Directorate Officer' AS reporting_officer,
  'Super Admin' AS reporter_role,
  t.date AS incident_date,
  t.date AS date,
  CASE
    WHEN t.date < '2026-09-14' THEN 1
    WHEN t.date < '2026-09-21' THEN 2
    ELSE 3
  END AS week_number,
  'First Term' AS term,
  '2026/2027' AS session,
  t.exception AS exception,
  t.exception AS exception_text,
  t.effect AS effect,
  t.effect AS effect_text,
  t.severity AS severity,
  COALESCE(chk.name, t.check_id) AS category,
  t.division AS division,
  t.target_type AS target_type,
  t.violation_level AS violation_level,
  COALESCE(
    s.id,
    div_staff.id,
    (SELECT ds.id FROM public.staff ds WHERE ds.staff_code = t.staff_code LIMIT 1),
    (SELECT ds.id FROM public.staff ds WHERE LOWER(TRIM(ds.name)) = LOWER(TRIM(t.staff_name)) LIMIT 1),
    (SELECT ds.id FROM public.staff ds WHERE ds.department = 'Academic' AND ds.status = 'Active' LIMIT 1),
    (SELECT ds.id FROM public.staff ds WHERE ds.status = 'Active' LIMIT 1)
  ) AS staff_id,
  COALESCE(t.staff_name, s.name, div_staff.name) AS staff_name,
  COALESCE(s.staff_code, t.staff_code, div_staff.staff_code) AS staff_code,
  COALESCE(s.department, div_staff.department, t.division) AS staff_category,
  COALESCE(s.staff_group, div_staff.staff_group, 'General') AS staff_group,
  COALESCE(s.role, div_staff.role, 'Staff') AS role,
  COALESCE(s.department, div_staff.department, t.division) AS department,
  t.check_id AS check_id,
  COALESCE(chk.name, t.check_id) AS compliance_check,
  c.id AS class_id,
  t.class_name AS class_name,
  sub.id AS subject_id,
  t.subject AS subject,
  'none' AS evidence_type,
  'OPEN' AS status,
  'SEEN' AS review_status,
  TRUE AS is_seen,
  NOW() AS created_at,
  NOW() AS updated_at
FROM temp_compliance_reports_seed t
LEFT JOIN LATERAL (
  SELECT s.id, s.staff_code, s.name, s.department, s.staff_group, s.role
  FROM public.staff s
  WHERE s.staff_code = t.staff_code
     OR LOWER(TRIM(s.name)) = LOWER(TRIM(t.staff_name))
     OR (
       LOWER(TRIM(s.name)) = LOWER(TRIM(REGEXP_REPLACE(t.staff_name, '^(Mr|Mrs|Miss|Dr|Pst)\s+', '', 'i')))
     )
  ORDER BY 
    (s.staff_code = t.staff_code) DESC,
    (LOWER(TRIM(s.name)) = LOWER(TRIM(t.staff_name))) DESC
  LIMIT 1
) s ON TRUE
LEFT JOIN LATERAL (
  SELECT ds.id, ds.staff_code, ds.name, ds.department, ds.staff_group, ds.role
  FROM public.staff ds
  WHERE 
    (t.division = 'Academic' AND ds.staff_code IN ('JCB-A-001', 'JCB-A-002'))
    OR (t.division IN ('Operation', 'Operations') AND ds.staff_code IN ('JCB-O-001', 'JCB-O-002', 'JCB-O-003', 'JCB-O-004'))
    OR (t.division = 'Discipline' AND ds.staff_code IN ('JCB-D-001', 'JCB-D-002'))
    OR (t.division = 'CDS' AND ds.staff_code = 'JCB-C-001')
  ORDER BY (ds.staff_code = t.staff_code) DESC
  LIMIT 1
) div_staff ON TRUE
LEFT JOIN LATERAL (
  SELECT chk.id, chk.name
  FROM public.compliance_checks chk
  WHERE chk.id = t.check_id
  LIMIT 1
) chk ON TRUE
LEFT JOIN LATERAL (
  SELECT c.id, c.name
  FROM public.classes c
  WHERE c.name = t.class_name
     OR LOWER(TRIM(c.name)) = LOWER(TRIM(t.class_name))
  ORDER BY (c.name = t.class_name) DESC, c.name, c.id
  LIMIT 1
) c ON t.class_name IS NOT NULL
LEFT JOIN LATERAL (
  SELECT sub.id, sub.name
  FROM public.subjects sub
  WHERE sub.name = t.subject
     OR LOWER(TRIM(sub.name)) = LOWER(TRIM(t.subject))
  ORDER BY (sub.name = t.subject) DESC, sub.name, sub.id
  LIMIT 1
) sub ON t.subject IS NOT NULL
ORDER BY t.report_number
ON CONFLICT (report_number) DO UPDATE SET
  reporter_name = EXCLUDED.reporter_name,
  reporting_officer = EXCLUDED.reporting_officer,
  reporter_role = EXCLUDED.reporter_role,
  incident_date = EXCLUDED.incident_date,
  date = EXCLUDED.date,
  week_number = EXCLUDED.week_number,
  term = EXCLUDED.term,
  session = EXCLUDED.session,
  exception = EXCLUDED.exception,
  exception_text = EXCLUDED.exception_text,
  effect = EXCLUDED.effect,
  effect_text = EXCLUDED.effect_text,
  severity = EXCLUDED.severity,
  category = EXCLUDED.category,
  division = EXCLUDED.division,
  target_type = EXCLUDED.target_type,
  violation_level = EXCLUDED.violation_level,
  staff_id = EXCLUDED.staff_id,
  staff_name = EXCLUDED.staff_name,
  staff_code = EXCLUDED.staff_code,
  staff_category = EXCLUDED.staff_category,
  staff_group = EXCLUDED.staff_group,
  role = EXCLUDED.role,
  department = EXCLUDED.department,
  check_id = EXCLUDED.check_id,
  compliance_check = EXCLUDED.compliance_check,
  class_id = EXCLUDED.class_id,
  class_name = EXCLUDED.class_name,
  subject_id = EXCLUDED.subject_id,
  subject = EXCLUDED.subject,
  review_status = EXCLUDED.review_status,
  is_seen = EXCLUDED.is_seen,
  updated_at = NOW();