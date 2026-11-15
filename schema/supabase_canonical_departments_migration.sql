-- =========================================================================
-- CANONICAL ACADEMIC DEPARTMENTS MIGRATION
-- The school strictly operates 4 Academic Departments / Disciplines:
-- 1. Life Sciences
-- 2. Physical Sciences
-- 3. Social Sciences
-- 4. Languages
--
-- This script eliminates non-existent departments ('General', 'Science', 'Humanities',
-- 'Technology', 'Creative Arts', 'Vocational', 'Commercial') across all tables & views.
-- =========================================================================

-- 1. Safely handle legacy 'vacant' / unassigned records without violating governance triggers
-- Step A: Unlink / detach compliance reports pointing to the 'vacant' placeholder staff identity
-- (preserves the historical exception logs while reclassifying them cleanly as Division-level unassigned posts)
UPDATE public.compliance_reports
SET staff_id = NULL,
    staff_name = 'Unassigned Post',
    target_type = 'division'
WHERE staff_id IN (
  SELECT id FROM public.staff 
  WHERE LOWER(TRIM(name)) LIKE '%vacant%' 
     OR LOWER(TRIM(name)) = 'unassigned' 
     OR LOWER(TRIM(COALESCE(staff_code, ''))) IN ('jcb-t-154', '54')
);

-- Step B: Reclassify any reports with staff_name recorded as 'vacant'
UPDATE public.compliance_reports
SET staff_id = NULL,
    staff_name = 'Unassigned Post',
    target_type = 'division'
WHERE LOWER(TRIM(COALESCE(staff_name, ''))) LIKE '%vacant%';

-- Step C: Now that 0 compliance reports depend on the dummy 'vacant' staff record, delete it safely
DELETE FROM public.staff 
WHERE LOWER(TRIM(name)) LIKE '%vacant%' 
   OR LOWER(TRIM(name)) = 'unassigned' 
   OR LOWER(TRIM(COALESCE(staff_code, ''))) IN ('jcb-t-154', '54');

-- 2. Update academic_discipline_groups table
CREATE TABLE IF NOT EXISTS public.academic_discipline_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  description TEXT,
  order_index INT DEFAULT 0
);

DELETE FROM public.academic_discipline_groups;

INSERT INTO public.academic_discipline_groups (id, name, description, order_index) VALUES
  ('life_sciences', 'Life Sciences', 'Biological, agricultural, and living systems disciplines', 1),
  ('physical_sciences', 'Physical Sciences', 'Mathematical, chemical, physical, and technological disciplines', 2),
  ('social_sciences', 'Social Sciences', 'Commercial, historical, civic, behavioral, and creative arts disciplines', 3),
  ('languages', 'Languages', 'Literary, linguistic, grammatical, and communication disciplines', 4)
ON CONFLICT (id) DO UPDATE SET 
  name = EXCLUDED.name, 
  description = EXCLUDED.description, 
  order_index = EXCLUDED.order_index;

-- 2. Standardize Subject Discipline Groups
UPDATE public.subjects
SET discipline_group = 'Life Sciences'
WHERE LOWER(name) LIKE '%bio%' 
   OR LOWER(name) LIKE '%agric%' 
   OR LOWER(name) LIKE '%livestock%' 
   OR LOWER(name) LIKE '%basic science%' 
   OR LOWER(name) LIKE '%food%' 
   OR LOWER(name) LIKE '%home econ%';

UPDATE public.subjects
SET discipline_group = 'Physical Sciences'
WHERE LOWER(name) LIKE '%math%' 
   OR LOWER(name) LIKE '%physic%' 
   OR LOWER(name) LIKE '%chem%' 
   OR LOWER(name) LIKE '%tech%' 
   OR LOWER(name) LIKE '%compu%' 
   OR LOWER(name) LIKE '%ict%' 
   OR LOWER(name) LIKE '%draw%';

UPDATE public.subjects
SET discipline_group = 'Languages'
WHERE LOWER(name) LIKE '%lang%' 
   OR LOWER(name) LIKE '%eng%' 
   OR LOWER(name) LIKE '%lit%' 
   OR LOWER(name) LIKE '%fren%' 
   OR LOWER(name) LIKE '%yoruba%' 
   OR LOWER(name) LIKE '%igbo%' 
   OR LOWER(name) LIKE '%speech%' 
   OR LOWER(name) LIKE '%compo%' 
   OR LOWER(name) LIKE '%compre%';

UPDATE public.subjects
SET discipline_group = 'Social Sciences'
WHERE LOWER(name) LIKE '%econ%' 
   OR LOWER(name) LIKE '%commer%' 
   OR LOWER(name) LIKE '%account%' 
   OR LOWER(name) LIKE '%gov%' 
   OR LOWER(name) LIKE '%hist%' 
   OR LOWER(name) LIKE '%crs%' 
   OR LOWER(name) LIKE '%irs%' 
   OR LOWER(name) LIKE '%civic%' 
   OR LOWER(name) LIKE '%social%' 
   OR LOWER(name) LIKE '%bus%' 
   OR LOWER(name) LIKE '%art%' 
   OR LOWER(name) LIKE '%cca%' 
   OR LOWER(name) LIKE '%music%' 
   OR LOWER(name) LIKE '%phe%' 
   OR LOWER(name) LIKE '%geog%';

-- Catch-all for any remaining academic subjects
UPDATE public.subjects
SET discipline_group = 'Physical Sciences'
WHERE department = 'Academic' AND (discipline_group IS NULL OR discipline_group NOT IN ('Life Sciences', 'Physical Sciences', 'Social Sciences', 'Languages'));

-- 3. Standardize Staff Groups for Academic Teachers
UPDATE public.staff
SET staff_group = 'Physical Sciences'
WHERE department = 'Academic' AND (
  LOWER(subjects) LIKE '%math%' 
  OR LOWER(subjects) LIKE '%physic%' 
  OR LOWER(subjects) LIKE '%chem%' 
  OR LOWER(subjects) LIKE '%tech%' 
  OR LOWER(subjects) LIKE '%compu%' 
  OR LOWER(subjects) LIKE '%ict%' 
  OR LOWER(staff_group) LIKE '%tech%'
  OR LOWER(staff_group) LIKE '%math%'
);

UPDATE public.staff
SET staff_group = 'Life Sciences'
WHERE department = 'Academic' AND (
  LOWER(subjects) LIKE '%bio%' 
  OR LOWER(subjects) LIKE '%agric%' 
  OR LOWER(subjects) LIKE '%livestock%' 
  OR LOWER(subjects) LIKE '%home econ%'
  OR LOWER(staff_group) LIKE '%life%'
);

UPDATE public.staff
SET staff_group = 'Languages'
WHERE department = 'Academic' AND (
  LOWER(subjects) LIKE '%eng%' 
  OR LOWER(subjects) LIKE '%lit%' 
  OR LOWER(subjects) LIKE '%french%' 
  OR LOWER(subjects) LIKE '%igbo%' 
  OR LOWER(subjects) LIKE '%yoruba%' 
  OR LOWER(subjects) LIKE '%speech%' 
  OR LOWER(subjects) LIKE '%grammar%' 
  OR LOWER(subjects) LIKE '%compo%' 
  OR LOWER(staff_group) LIKE '%lang%'
);

UPDATE public.staff
SET staff_group = 'Social Sciences'
WHERE department = 'Academic' AND (
  LOWER(subjects) LIKE '%econ%' 
  OR LOWER(subjects) LIKE '%account%' 
  OR LOWER(subjects) LIKE '%commer%' 
  OR LOWER(subjects) LIKE '%bus%' 
  OR LOWER(subjects) LIKE '%gov%' 
  OR LOWER(subjects) LIKE '%hist%' 
  OR LOWER(subjects) LIKE '%civic%' 
  OR LOWER(subjects) LIKE '%citizen%' 
  OR LOWER(subjects) LIKE '%crs%' 
  OR LOWER(subjects) LIKE '%irs%' 
  OR LOWER(subjects) LIKE '%secur%' 
  OR LOWER(subjects) LIKE '%cca%' 
  OR LOWER(subjects) LIKE '%social%' 
  OR LOWER(staff_group) LIKE '%social%'
  OR LOWER(staff_group) LIKE '%human%'
  OR LOWER(staff_group) LIKE '%creat%'
);

-- Catch-all to eliminate any remaining 'General' or non-canonical groups on Academic staff
UPDATE public.staff
SET staff_group = 'Physical Sciences'
WHERE department = 'Academic' AND (staff_group IS NULL OR staff_group NOT IN ('Life Sciences', 'Physical Sciences', 'Social Sciences', 'Languages'));

-- 4. Recreate Authoritative report_ready_exceptions View with Canonical Mapping
DROP VIEW IF EXISTS public.report_ready_exceptions CASCADE;
CREATE OR REPLACE VIEW public.report_ready_exceptions AS
SELECT
  e.id,
  COALESCE(e.date, e.incident_date, e.created_at::DATE) AS date,
  e.division,
  e.target_type,
  e.staff_id,
  s.staff_code,
  COALESCE(s.name, e.staff_name) AS staff_name,
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
  cl.level AS class_level,
  e.subject_id,
  COALESCE(sub.name, e.subject) AS subject_name,
  CASE
    WHEN e.division = 'CDS' OR e.target_type = 'division' THEN NULL
    WHEN e.division = 'Operations' OR LOWER(s.department) = 'operations' OR LOWER(e.department) = 'operations' THEN 'Operations'
    WHEN e.division = 'Discipline' OR LOWER(s.department) = 'discipline' OR LOWER(e.department) = 'discipline' THEN 'Discipline'
    WHEN sub.discipline_group IN ('Life Sciences', 'Physical Sciences', 'Social Sciences', 'Languages') THEN sub.discipline_group
    WHEN s.staff_group IN ('Life Sciences', 'Physical Sciences', 'Social Sciences', 'Languages') THEN s.staff_group
    WHEN LOWER(COALESCE(sub.name, e.subject, '')) ~* '(bio|agric|livestock|food|health)' THEN 'Life Sciences'
    WHEN LOWER(COALESCE(sub.name, e.subject, '')) ~* '(math|physic|chem|tech|compu|ict|draw)' THEN 'Physical Sciences'
    WHEN LOWER(COALESCE(sub.name, e.subject, '')) ~* '(lang|eng|lit|fren|yoruba|igbo|speech|compo|compre)' THEN 'Languages'
    WHEN LOWER(COALESCE(sub.name, e.subject, '')) ~* '(econ|commer|account|gov|hist|crs|irs|civic|citizen|social|bus|art|cca|music|phe)' THEN 'Social Sciences'
    ELSE 'Physical Sciences'
  END AS discipline_group,
  COALESCE(e.exception_text, e.exception) AS exception_text,
  COALESCE(e.effect_text, e.effect) AS effect_text,
  COALESCE(e.severity, 'MEDIUM') AS severity,
  COALESCE(e.evidence_url, (CASE WHEN array_length(e.evidence_urls, 1) > 0 THEN e.evidence_urls[1] ELSE NULL END)) AS evidence_url,
  COALESCE(e.evidence_type, (CASE WHEN array_length(e.evidence_types, 1) > 0 THEN e.evidence_types[1] ELSE 'none' END)) AS evidence_type,
  e.evidence_urls,
  e.evidence_types,
  e.reported_by,
  e.reporter_id,
  e.reporter_name,
  e.reporting_officer,
  e.term,
  e.session,
  e.week_number,
  e.status,
  e.review_status,
  COALESCE(e.is_seen, (e.review_status = 'SEEN')) AS is_seen,
  e.seen_at,
  e.seen_by,
  e.created_at,
  e.updated_at
FROM public.compliance_reports e
LEFT JOIN public.staff s ON s.id = e.staff_id
LEFT JOIN public.compliance_checks c ON c.id = e.check_id
LEFT JOIN public.classes cl ON cl.id = e.class_id
LEFT JOIN public.subjects sub ON sub.id = e.subject_id;

GRANT SELECT ON public.report_ready_exceptions TO anon, authenticated;
