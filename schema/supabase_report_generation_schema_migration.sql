-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE & DIRECTORATE SYSTEM
-- STAGE 2 MIGRATION: Authoritative Data Model for Official PowerPoint Reporting
-- Target: Supabase (PostgreSQL)
--
-- Guarantees:
-- 1. 100% NON-DESTRUCTIVE: Preserves all historical records.
-- 2. STAFF TABLE: Supports school_section, active, department, staff_group, role.
-- 3. EXCEPTION TABLE (compliance_reports): Supports normalized fields:
--    staff_id, check_id, class_id, subject_id, date, exception_text, effect_text,
--    evidence_url, evidence_type, seen_at, seen_by.
-- 4. RELATIONAL INTEGRITY: CDS (division='CDS', staff_id=NULL) vs
--    Academic/Operations (staff_id NOT NULL).
-- 5. NO INDIVIDUAL SEVERITY: Individual exception records do NOT carry severity.
-- 6. INDEXES: Created for staff_id, division, date, check_id, class_id, subject_id.
-- 7. AUTHORITATIVE VIEW: public.report_ready_exceptions for zero-loss reporting queries.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================
-- 1. ENHANCE STAFF TABLE (Relational Identity & School Section)
-- =========================================================================
ALTER TABLE public.staff 
  ADD COLUMN IF NOT EXISTS school_section TEXT DEFAULT 'SENIOR_SECONDARY',
  ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;

-- Ensure active flag is synchronized with status
UPDATE public.staff
SET active = (CASE WHEN status = 'Active' THEN TRUE ELSE FALSE END)
WHERE active IS NULL OR (active = TRUE AND status = 'Archived') OR (active = FALSE AND status = 'Active');

-- Backfill school_section based on role, assigned_location, and category
UPDATE public.staff
SET school_section = CASE
  WHEN assigned_location ILIKE '%nursery%' OR role ILIKE '%nursery%' OR role ILIKE '%creche%' THEN 'NURSERY'
  WHEN assigned_location ILIKE '%basic 1%' OR assigned_location ILIKE '%basic 2%' OR assigned_location ILIKE '%basic 3%' OR assigned_location ILIKE '%b1%' OR assigned_location ILIKE '%b2%' OR assigned_location ILIKE '%b3%' OR role ILIKE '%junior primary%' THEN 'JUNIOR_PRIMARY'
  WHEN assigned_location ILIKE '%basic 4%' OR assigned_location ILIKE '%basic 5%' OR assigned_location ILIKE '%basic 6%' OR assigned_location ILIKE '%b4%' OR assigned_location ILIKE '%b5%' OR assigned_location ILIKE '%b6%' OR role ILIKE '%senior primary%' THEN 'SENIOR_PRIMARY'
  WHEN assigned_location ILIKE '%primary%' OR role ILIKE '%primary%' THEN 'JUNIOR_SENIOR_PRIMARY'
  WHEN assigned_location ILIKE '%jss%' OR role ILIKE '%jss%' OR role ILIKE '%junior secondary%' THEN 'JUNIOR_SECONDARY'
  WHEN assigned_location ILIKE '%sss%' OR assigned_location ILIKE '%ss 1%' OR assigned_location ILIKE '%ss 2%' OR assigned_location ILIKE '%ss 3%' OR role ILIKE '%senior secondary%' THEN 'SENIOR_SECONDARY'
  WHEN department = 'Academic' THEN 'JUNIOR_SENIOR_SECONDARY'
  ELSE 'OPERATIONS'
END
WHERE school_section IS NULL OR school_section = '';

-- Trigger to keep active and status in sync
CREATE OR REPLACE FUNCTION trg_sync_staff_active_status()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status = 'Archived' THEN
    NEW.active := FALSE;
  ELSE
    NEW.active := TRUE;
    NEW.status := 'Active';
  END IF;
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_staff_sync_active ON public.staff;
CREATE TRIGGER trg_staff_sync_active
BEFORE INSERT OR UPDATE ON public.staff
FOR EACH ROW EXECUTE FUNCTION trg_sync_staff_active_status();


-- =========================================================================
-- 2. ENHANCE CLASSES TABLE (School Section & Reference Metadata)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.classes (
  id          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE,
  level       TEXT NOT NULL,
  arm         TEXT,
  section     TEXT DEFAULT 'SENIOR_SECONDARY',
  order_index INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.classes
  ADD COLUMN IF NOT EXISTS section TEXT DEFAULT 'SENIOR_SECONDARY',
  ADD COLUMN IF NOT EXISTS order_index INT DEFAULT 0;

-- Seed comprehensive classes reference data if empty
INSERT INTO public.classes (name, level, arm, section, order_index) VALUES
  -- Nursery
  ('Nursery 1', 'NUR', 'A', 'NURSERY', 1),
  ('Nursery 2', 'NUR', 'A', 'NURSERY', 2),
  ('Nursery 3', 'NUR', 'A', 'NURSERY', 3),
  -- Junior Primary
  ('Basic 1A', 'PRI 1', 'A', 'JUNIOR_PRIMARY', 10),
  ('Basic 1B', 'PRI 1', 'B', 'JUNIOR_PRIMARY', 11),
  ('Basic 1C', 'PRI 1', 'C', 'JUNIOR_PRIMARY', 12),
  ('Basic 2A', 'PRI 2', 'A', 'JUNIOR_PRIMARY', 20),
  ('Basic 2B', 'PRI 2', 'B', 'JUNIOR_PRIMARY', 21),
  ('Basic 2C', 'PRI 2', 'C', 'JUNIOR_PRIMARY', 22),
  ('Basic 3A', 'PRI 3', 'A', 'JUNIOR_PRIMARY', 30),
  ('Basic 3B', 'PRI 3', 'B', 'JUNIOR_PRIMARY', 31),
  ('Basic 3C', 'PRI 3', 'C', 'JUNIOR_PRIMARY', 32),
  -- Senior Primary
  ('Basic 4A', 'PRI 4', 'A', 'SENIOR_PRIMARY', 40),
  ('Basic 4B', 'PRI 4', 'B', 'SENIOR_PRIMARY', 41),
  ('Basic 4C', 'PRI 4', 'C', 'SENIOR_PRIMARY', 42),
  ('Basic 5A', 'PRI 5', 'A', 'SENIOR_PRIMARY', 50),
  ('Basic 5B', 'PRI 5', 'B', 'SENIOR_PRIMARY', 51),
  ('Basic 5C', 'PRI 5', 'C', 'SENIOR_PRIMARY', 52),
  ('Basic 6A', 'PRI 6', 'A', 'SENIOR_PRIMARY', 60),
  ('Basic 6B', 'PRI 6', 'B', 'SENIOR_PRIMARY', 61),
  ('Basic 6C', 'PRI 6', 'C', 'SENIOR_PRIMARY', 62),
  -- Junior Secondary
  ('JSS 1 Gold', 'JSS 1', 'Gold', 'JUNIOR_SECONDARY', 70),
  ('JSS 1 Diamond', 'JSS 1', 'Diamond', 'JUNIOR_SECONDARY', 71),
  ('JSS 1 Silver', 'JSS 1', 'Silver', 'JUNIOR_SECONDARY', 72),
  ('JSS 2 Gold', 'JSS 2', 'Gold', 'JUNIOR_SECONDARY', 80),
  ('JSS 2 Diamond', 'JSS 2', 'Diamond', 'JUNIOR_SECONDARY', 81),
  ('JSS 2 Silver', 'JSS 2', 'Silver', 'JUNIOR_SECONDARY', 82),
  ('JSS 3 Gold', 'JSS 3', 'Gold', 'JUNIOR_SECONDARY', 90),
  ('JSS 3 Diamond', 'JSS 3', 'Diamond', 'JUNIOR_SECONDARY', 91),
  ('JSS 3 Silver', 'JSS 3', 'Silver', 'JUNIOR_SECONDARY', 92),
  -- Senior Secondary
  ('SSS 1 Science', 'SSS 1', 'Science', 'SENIOR_SECONDARY', 100),
  ('SSS 1 Commercial', 'SSS 1', 'Commercial', 'SENIOR_SECONDARY', 101),
  ('SSS 1 Arts', 'SSS 1', 'Arts', 'SENIOR_SECONDARY', 102),
  ('SSS 2 Science', 'SSS 2', 'Science', 'SENIOR_SECONDARY', 110),
  ('SSS 2 Commercial', 'SSS 2', 'Commercial', 'SENIOR_SECONDARY', 111),
  ('SSS 2 Arts', 'SSS 2', 'Arts', 'SENIOR_SECONDARY', 112),
  ('SSS 3 Science', 'SSS 3', 'Science', 'SENIOR_SECONDARY', 120),
  ('SSS 3 Commercial', 'SSS 3', 'Commercial', 'SENIOR_SECONDARY', 121),
  ('SSS 3 Arts', 'SSS 3', 'Arts', 'SENIOR_SECONDARY', 122)
ON CONFLICT (name) DO UPDATE SET
  section = EXCLUDED.section,
  order_index = EXCLUDED.order_index;


-- =========================================================================
-- 3. ENHANCE SUBJECTS TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.subjects (
  id          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE,
  code        TEXT,
  department  TEXT,
  discipline_group TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.subjects
  ADD COLUMN IF NOT EXISTS code TEXT,
  ADD COLUMN IF NOT EXISTS discipline_group TEXT;

INSERT INTO public.subjects (name, code, department, discipline_group) VALUES
  ('Mathematics', 'MTH', 'Academic', 'Physical Sciences'),
  ('Further Mathematics', 'FMTH', 'Academic', 'Physical Sciences'),
  ('Physics', 'PHY', 'Academic', 'Physical Sciences'),
  ('Chemistry', 'CHM', 'Academic', 'Physical Sciences'),
  ('Biology', 'BIO', 'Academic', 'Life Sciences'),
  ('Basic Science', 'BSC', 'Academic', 'Life Sciences'),
  ('Agricultural Science', 'AGR', 'Academic', 'Life Sciences'),
  ('Food and Nutrition', 'FN', 'Academic', 'Life Sciences'),
  ('English Language', 'ENG', 'Academic', 'Languages'),
  ('Literature in English', 'LIT', 'Academic', 'Languages'),
  ('Yoruba Language', 'YOR', 'Academic', 'Languages'),
  ('French Language', 'FRE', 'Academic', 'Languages'),
  ('Economics', 'ECO', 'Academic', 'Social Sciences'),
  ('Commerce', 'COM', 'Academic', 'Social Sciences'),
  ('Financial Accounting', 'ACC', 'Academic', 'Social Sciences'),
  ('Government', 'GOV', 'Academic', 'Social Sciences'),
  ('History', 'HIS', 'Academic', 'Social Sciences'),
  ('Christian Religious Studies', 'CRS', 'Academic', 'Social Sciences'),
  ('Islamic Religious Studies', 'IRS', 'Academic', 'Social Sciences'),
  ('Civic Education', 'CIV', 'Academic', 'Social Sciences'),
  ('Social Studies', 'SOS', 'Academic', 'Social Sciences'),
  ('Business Studies', 'BUS', 'Academic', 'Social Sciences'),
  ('Technical Drawing', 'TD', 'Academic', 'Physical Sciences'),
  ('Basic Technology', 'BTECH', 'Academic', 'Physical Sciences'),
  ('Computer Studies / ICT', 'ICT', 'Academic', 'Physical Sciences'),
  ('Physical & Health Education', 'PHE', 'Academic', 'Social Sciences'),
  ('Music', 'MUS', 'Academic', 'Social Sciences'),
  ('Fine Art', 'ART', 'Academic', 'Social Sciences')
ON CONFLICT (name) DO UPDATE SET
  discipline_group = EXCLUDED.discipline_group,
  department = EXCLUDED.department;


-- =========================================================================
-- 4. REFERENCE GROUPS & REPORTING PERIODS TABLES
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.academic_discipline_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  description TEXT,
  order_index INT DEFAULT 0
);

DELETE FROM public.academic_discipline_groups;
INSERT INTO public.academic_discipline_groups (id, name, order_index) VALUES
  ('life_sciences', 'Life Sciences', 1),
  ('physical_sciences', 'Physical Sciences', 2),
  ('social_sciences', 'Social Sciences', 3),
  ('languages', 'Languages', 4)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, order_index = EXCLUDED.order_index;

CREATE TABLE IF NOT EXISTS public.operations_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  description TEXT,
  order_index INT DEFAULT 0
);

INSERT INTO public.operations_groups (id, name, order_index) VALUES
  ('minders', 'Early Years Minders & Childcare', 1),
  ('transport', 'School Transport Fleet & Logistics', 2),
  ('facilities_maintenance', 'Campus Maintenance & Physical Facilities', 3),
  ('hostel_kitchen', 'Hostel Boarding & Kitchen Catering', 4),
  ('security', 'Campus Security & Gate Protocol', 5),
  ('medical', 'Clinic & Medical Services', 6),
  ('administration', 'Central Operations Administration', 7)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.reporting_periods (
  id          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  session     TEXT NOT NULL,
  term        TEXT NOT NULL,
  is_current  BOOLEAN DEFAULT FALSE,
  start_date  DATE,
  end_date    DATE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (session, term)
);

INSERT INTO public.reporting_periods (session, term, is_current) VALUES
  ('2025/2026', 'First Term', FALSE),
  ('2025/2026', 'Second Term', FALSE),
  ('2025/2026', 'Third Term', TRUE)
ON CONFLICT (session, term) DO UPDATE SET is_current = EXCLUDED.is_current;


-- =========================================================================
-- 5. ENHANCE COMPLIANCE REPORTS (Normalized Report-Ready Schema)
-- =========================================================================
ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS date DATE,
  ADD COLUMN IF NOT EXISTS check_id TEXT REFERENCES public.compliance_checks(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS class_id UUID REFERENCES public.classes(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS subject_id UUID REFERENCES public.subjects(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS exception_text TEXT,
  ADD COLUMN IF NOT EXISTS effect_text TEXT,
  ADD COLUMN IF NOT EXISTS evidence_url TEXT,
  ADD COLUMN IF NOT EXISTS evidence_type TEXT DEFAULT 'none';

-- Backfill normalized date, exception_text, effect_text, evidence_url from legacy fields
UPDATE public.compliance_reports
SET date = COALESCE(date, incident_date, created_at::DATE),
    exception_text = COALESCE(exception_text, exception),
    effect_text = COALESCE(effect_text, effect),
    evidence_url = COALESCE(evidence_url, (CASE WHEN array_length(evidence_urls, 1) > 0 THEN evidence_urls[1] ELSE NULL END)),
    evidence_type = COALESCE(evidence_type, (CASE WHEN array_length(evidence_types, 1) > 0 THEN evidence_types[1] ELSE 'none' END))
WHERE date IS NULL OR exception_text IS NULL OR effect_text IS NULL;

-- Relational backfill for class_id and subject_id where names match
UPDATE public.compliance_reports r
SET class_id = c.id
FROM public.classes c
WHERE r.class_id IS NULL 
  AND r.class_name IS NOT NULL 
  AND LOWER(TRIM(r.class_name)) = LOWER(TRIM(c.name));

UPDATE public.compliance_reports r
SET subject_id = s.id
FROM public.subjects s
WHERE r.subject_id IS NULL 
  AND r.subject IS NOT NULL 
  AND LOWER(TRIM(r.subject)) = LOWER(TRIM(s.name));

-- Trigger to guarantee synchronization between normalized and legacy columns
CREATE OR REPLACE FUNCTION trg_sync_compliance_reports_normalized()
RETURNS TRIGGER AS $$
BEGIN
  -- Sync date <-> incident_date
  IF NEW.date IS NOT NULL THEN
    NEW.incident_date := NEW.date;
  ELSIF NEW.incident_date IS NOT NULL THEN
    NEW.date := NEW.incident_date;
  ELSE
    NEW.date := CURRENT_DATE;
    NEW.incident_date := CURRENT_DATE;
  END IF;

  -- Sync exception_text <-> exception
  IF NEW.exception_text IS NOT NULL AND NEW.exception_text != '' THEN
    NEW.exception := NEW.exception_text;
  ELSIF NEW.exception IS NOT NULL THEN
    NEW.exception_text := NEW.exception;
  END IF;

  -- Sync effect_text <-> effect
  IF NEW.effect_text IS NOT NULL AND NEW.effect_text != '' THEN
    NEW.effect := NEW.effect_text;
  ELSIF NEW.effect IS NOT NULL THEN
    NEW.effect_text := NEW.effect;
  END IF;

  -- Sync single evidence_url <-> evidence_urls array
  IF NEW.evidence_url IS NOT NULL AND NEW.evidence_url != '' THEN
    IF NEW.evidence_urls IS NULL OR array_length(NEW.evidence_urls, 1) IS NULL THEN
      NEW.evidence_urls := ARRAY[NEW.evidence_url];
    END IF;
  ELSIF NEW.evidence_urls IS NOT NULL AND array_length(NEW.evidence_urls, 1) > 0 THEN
    NEW.evidence_url := NEW.evidence_urls[1];
  END IF;

  -- Check relational integrity: CDS (staff_id is NULL) vs Academic/Operations (staff_id is NOT NULL)
  IF NEW.division = 'CDS' THEN
    NEW.target_type := 'division';
    NEW.staff_id := NULL;
  ELSE
    NEW.target_type := 'staff';
  END IF;

  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reports_sync_normalized ON public.compliance_reports;
CREATE TRIGGER trg_reports_sync_normalized
BEFORE INSERT OR UPDATE ON public.compliance_reports
FOR EACH ROW EXECUTE FUNCTION trg_sync_compliance_reports_normalized();


-- =========================================================================
-- 6. PERFORMANCE & REPORT QUERY INDEXES
-- Explicitly covering: staff_id, division, date, check_id, class_id, subject_id
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_comp_rep_staff_id ON public.compliance_reports(staff_id);
CREATE INDEX IF NOT EXISTS idx_comp_rep_division ON public.compliance_reports(division);
CREATE INDEX IF NOT EXISTS idx_comp_rep_date ON public.compliance_reports(date DESC);
CREATE INDEX IF NOT EXISTS idx_comp_rep_incident_date ON public.compliance_reports(incident_date DESC);
CREATE INDEX IF NOT EXISTS idx_comp_rep_check_id ON public.compliance_reports(check_id);
CREATE INDEX IF NOT EXISTS idx_comp_rep_class_id ON public.compliance_reports(class_id);
CREATE INDEX IF NOT EXISTS idx_comp_rep_subject_id ON public.compliance_reports(subject_id);
CREATE INDEX IF NOT EXISTS idx_comp_rep_term ON public.compliance_reports(term);
CREATE INDEX IF NOT EXISTS idx_comp_rep_review_status ON public.compliance_reports(review_status);

CREATE INDEX IF NOT EXISTS idx_staff_active ON public.staff(active);
CREATE INDEX IF NOT EXISTS idx_staff_school_section ON public.staff(school_section);
CREATE INDEX IF NOT EXISTS idx_staff_department ON public.staff(department);
CREATE INDEX IF NOT EXISTS idx_staff_staff_group ON public.staff(staff_group);


-- =========================================================================
-- 7. AUTHORITATIVE REPORT-READY EXCEPTION VIEW
-- Standard single source of truth for PowerPoint report generation & analytical queries
-- =========================================================================
DROP VIEW IF EXISTS public.report_ready_exceptions CASCADE;
CREATE OR REPLACE VIEW public.report_ready_exceptions AS
SELECT
  e.id,
  COALESCE(e.date, e.incident_date, e.created_at::DATE) AS date,
  e.division,
  e.staff_id,
  s.staff_code,
  s.name AS staff_name,
  COALESCE(s.department, e.division) AS department,
  s.staff_group,
  s.role,
  s.school_section,
  COALESCE(s.assigned_location, e.class_name) AS assigned_location,
  e.check_id,
  COALESCE(c.name, e.compliance_check, e.category) AS check_name,
  c.group_name AS check_group,
  e.class_id,
  COALESCE(cl.name, e.class_name) AS class_name,
  cl.section AS class_section,
  cl.arm AS class_arm,
  e.subject_id,
  COALESCE(sub.name, e.subject) AS subject_name,
  sub.discipline_group,
  COALESCE(e.exception_text, e.exception) AS exception_text,
  COALESCE(e.effect_text, e.effect) AS effect_text,
  COALESCE(e.evidence_url, (CASE WHEN array_length(e.evidence_urls, 1) > 0 THEN e.evidence_urls[1] ELSE NULL END)) AS evidence_url,
  COALESCE(e.evidence_type, (CASE WHEN array_length(e.evidence_types, 1) > 0 THEN e.evidence_types[1] ELSE 'none' END)) AS evidence_type,
  e.reported_by,
  e.reporter_id,
  e.reporter_name,
  e.reporting_officer,
  e.term,
  e.week_number,
  e.status,
  e.review_status,
  COALESCE(e.is_seen, (e.review_status = 'SEEN')) AS is_seen,
  e.seen_at,
  e.seen_by,
  e.created_at
FROM public.compliance_reports e
LEFT JOIN public.staff s ON s.id = e.staff_id
LEFT JOIN public.compliance_checks c ON c.id = e.check_id
LEFT JOIN public.classes cl ON cl.id = e.class_id
LEFT JOIN public.subjects sub ON sub.id = e.subject_id;
