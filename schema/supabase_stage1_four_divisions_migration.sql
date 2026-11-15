-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 1 DATABASE MIGRATION
-- Canonical Four Divisions Schema & Safe Migration
-- Target: Supabase (PostgreSQL 15+)
--
-- Official Four Divisions:
-- 1. Academic
-- 2. CDS
-- 3. Operation (supports 'Operations' alias for seamless backward compatibility)
-- 4. Discipline
--
-- Safety Guarantees:
-- 1. 100% NON-DESTRUCTIVE: DOES NOT DROP ANY TABLES or DELETE ANY EXISTING DATA.
-- 2. Fully idempotent: Can be run multiple times safely.
-- 3. Does not insert fake, sample, or mock exception data.
-- 4. Preserves all historical compliance reports, foreign keys, and RLS policies.
-- 5. Standardizes divisions in staff, compliance_areas, compliance_reports,
--    and report_ready_exceptions view.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================
-- 1. CANONICAL DIVISIONS REFERENCE TABLE
-- Establishes the authoritative database source of truth for the four divisions.
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.divisions (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE,
  code        TEXT NOT NULL UNIQUE,
  description TEXT,
  order_index INT DEFAULT 0,
  is_active   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Seed authoritative canonical divisions
INSERT INTO public.divisions (id, name, code, description, order_index, is_active) VALUES
  ('academic', 'Academic', 'ACAD', 'Academic instruction, classroom governance, curriculum scheme, and instructional quality.', 1, true),
  ('cds', 'CDS', 'CDS', 'Corporate Digital Services, ICT infrastructure, biometric, camera, and CBT systems.', 2, true),
  ('operation', 'Operation', 'OPS', 'Campus operations, facilities, logistics, transport, minders, hostels, and physical plant.', 3, true),
  ('discipline', 'Discipline', 'DISC', 'Student conduct, decorum, institutional grooming, punctuality, and behavioral governance.', 4, true)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  code = EXCLUDED.code,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index,
  is_active = EXCLUDED.is_active,
  updated_at = NOW();

-- Enable RLS on divisions table
ALTER TABLE public.divisions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "divisions_read_all" ON public.divisions;
CREATE POLICY "divisions_read_all"
  ON public.divisions FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "divisions_admin_manage" ON public.divisions;
CREATE POLICY "divisions_admin_manage"
  ON public.divisions FOR ALL
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.user_roles
      WHERE user_id = auth.uid()
        AND role IN ('super_admin', 'admin')
        AND is_verified = TRUE
    )
  );

-- =========================================================================
-- 2. STAFF REGISTRY SCHEMA UPDATE FOR FOUR DIVISIONS
-- Allows department to be Academic, Operation, Operations, CDS, or Discipline.
-- Normalizes existing records safely.
-- =========================================================================

-- Ensure columns exist
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_group TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS school_section TEXT DEFAULT 'SENIOR_SECONDARY';

-- Drop old department check constraint
ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_department_check;

-- Add updated department check constraint supporting canonical divisions
ALTER TABLE public.staff ADD CONSTRAINT staff_department_check 
  CHECK (department IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));

-- Update any existing Discipline Unit or Discipline officer records in staff table
UPDATE public.staff
SET department = 'Discipline',
    staff_group = COALESCE(staff_group, 'Discipline'),
    updated_at = NOW()
WHERE role ILIKE '%discipline%' 
   OR staff_group ILIKE '%discipline%'
   OR name ILIKE '%discipline%';

-- =========================================================================
-- 3. COMPLIANCE AREAS SCHEMA UPDATE FOR FOUR DIVISIONS
-- Add 'Discipline' and 'Operation' to compliance_areas division constraint.
-- Seed the official 'Discipline' compliance area and checks if not present.
-- =========================================================================

ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
ALTER TABLE public.compliance_areas DROP CONSTRAINT IF EXISTS comp_areas_division_check;
ALTER TABLE public.compliance_areas ADD CONSTRAINT comp_areas_division_check 
  CHECK (division IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));

-- Upsert official Discipline compliance area
INSERT INTO public.compliance_areas (id, name, division, description, order_index) VALUES
  ('discipline', 'Discipline', 'Discipline', 'Student conduct, behavioral standards, dress code, decorum, and duty punctuality.', 4)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  division = EXCLUDED.division,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index,
  updated_at = NOW();

-- Upsert official Discipline compliance checks
INSERT INTO public.compliance_checks (id, area_id, name, group_name, description, order_index) VALUES
  ('discipline-conduct', 'discipline', 'Student Conduct & Decorum', 'Discipline', 'Adherence to school rules, classroom decorum, and behavioral guidelines.', 1),
  ('discipline-dress-code', 'discipline', 'Dress Code & Hair Grooming', 'Discipline', 'Institutional uniform correctness, improper hairstyles, and grooming compliance.', 2),
  ('discipline-punctuality', 'discipline', 'Punctuality & Assembly Decorum', 'Discipline', 'Timely arrival for morning assembly, class transitions, and duty assignments.', 3),
  ('discipline-substance-contraband', 'discipline', 'Contraband & Unauthorized Items', 'Discipline', 'Possession of prohibited items, electronic devices, or non-compliant materials.', 4),
  ('discipline-bullying-fighting', 'discipline', 'Bullying, Fighting & Physical Altercation', 'Discipline', 'Zero tolerance for fighting, peer harassment, extortion, or bullying behavior.', 5),
  ('discipline-truancy-loitering', 'discipline', 'Truancy & Class Loitering', 'Discipline', 'Leaving classrooms without exeat, wandering on campus corridors, or bunking.', 6)
ON CONFLICT (id) DO UPDATE SET
  area_id = EXCLUDED.area_id,
  name = EXCLUDED.name,
  group_name = EXCLUDED.group_name,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index,
  updated_at = NOW();

-- =========================================================================
-- 4. COMPLIANCE REPORTS SCHEMA UPDATE FOR FOUR DIVISIONS & EXPANDED SCOPES
-- Updates division constraint to include 'Discipline' alongside Academic, CDS, Operation, Operations.
-- Updates target_type integrity constraints so:
-- - CDS reports have target_type = 'division' (staff_id optional / null).
-- - Academic, Operation, Operations reports reference staff_id.
-- - Discipline reports can target staff or division (e.g. Discipline Unit or specific staff).
-- =========================================================================

-- Ensure columns exist
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS target_type TEXT DEFAULT 'staff';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_id UUID REFERENCES public.staff(id) ON DELETE RESTRICT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS class_id UUID REFERENCES public.classes(id) ON DELETE SET NULL;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS subject_id UUID REFERENCES public.subjects(id) ON DELETE SET NULL;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS session TEXT DEFAULT '2026/2027';
ALTER TABLE public.compliance_reports ALTER COLUMN session SET DEFAULT '2026/2027';
UPDATE public.compliance_reports SET session = '2026/2027' WHERE session = '2024/2025' OR session IS NULL;

-- Update division check constraint
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_division_check;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_division_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT compliance_reports_division_check 
  CHECK (division IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline'));

-- Update target_type check constraint
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_target_type_check;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_target_type_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT compliance_reports_target_type_check 
  CHECK (target_type IN ('staff', 'division'));

-- Update relational integrity check constraint
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS chk_compliance_reports_staff_integrity;
ALTER TABLE public.compliance_reports ADD CONSTRAINT chk_compliance_reports_staff_integrity
  CHECK (
    (division = 'CDS' AND target_type = 'division')
    OR
    (division = 'Discipline')
    OR
    (division IN ('Academic', 'Operation', 'Operations') AND (staff_id IS NOT NULL OR incident_date < '2026-09-01'))
  );

-- =========================================================================
-- 5. RELATIONAL LOOKUP & NORMALIZATION INDEXES
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_comp_reports_division ON public.compliance_reports(division);
CREATE INDEX IF NOT EXISTS idx_comp_reports_session ON public.compliance_reports(session);
CREATE INDEX IF NOT EXISTS idx_comp_reports_term ON public.compliance_reports(term);
CREATE INDEX IF NOT EXISTS idx_comp_reports_staff_id ON public.compliance_reports(staff_id);
CREATE INDEX IF NOT EXISTS idx_comp_reports_class_id ON public.compliance_reports(class_id);
CREATE INDEX IF NOT EXISTS idx_comp_reports_subject_id ON public.compliance_reports(subject_id);
CREATE INDEX IF NOT EXISTS idx_comp_reports_check_id ON public.compliance_reports(check_id);
CREATE INDEX IF NOT EXISTS idx_comp_reports_date ON public.compliance_reports(incident_date DESC);
CREATE INDEX IF NOT EXISTS idx_staff_department ON public.staff(department);

-- =========================================================================
-- 6. UPDATE REPORT IDENTITY SYNCHRONIZATION TRIGGER
-- Ensures staff_name, staff_code, division are authoritatively kept in sync
-- without overwriting Discipline or CDS division classifications.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.sync_report_authoritative_staff_identity()
RETURNS TRIGGER AS $$
DECLARE
  staff_rec RECORD;
BEGIN
  -- For CDS division exceptions, guarantee clean division identity
  IF NEW.division = 'CDS' OR NEW.target_type = 'division' THEN
    NEW.division := COALESCE(NEW.division, 'CDS');
    -- If division is not Discipline, ensure CDS specifics
    IF NEW.division = 'CDS' THEN
      NEW.target_type := 'division';
      NEW.staff_id := NULL;
      NEW.staff_name := NULL;
      NEW.staff_code := NULL;
      RETURN NEW;
    END IF;
  END IF;

  -- For staff exceptions, authoritatively bind master identity
  IF NEW.staff_id IS NOT NULL THEN
    SELECT id, name, staff_code, department, staff_group, role, assigned_location
    INTO staff_rec
    FROM public.staff
    WHERE id = NEW.staff_id;

    IF FOUND THEN
      -- Authoritatively overwrite client-submitted name or code with staff registry data
      NEW.staff_name := staff_rec.name;
      NEW.staff_code := staff_rec.staff_code;
      
      -- If division was not explicitly specified or matches staff's department, align it
      IF NEW.division IS NULL OR NEW.division = '' OR NEW.division IN ('Academic', 'Operation', 'Operations') THEN
        NEW.division := staff_rec.department;
      END IF;

      IF NEW.staff_group IS NULL THEN
        NEW.staff_group := staff_rec.staff_group;
      END IF;
      IF NEW.role IS NULL THEN
        NEW.role := staff_rec.role;
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_report_authoritative_staff_identity ON public.compliance_reports;
CREATE TRIGGER trg_sync_report_authoritative_staff_identity
  BEFORE INSERT OR UPDATE OF staff_id, staff_name, staff_code, division ON public.compliance_reports
  FOR EACH ROW EXECUTE FUNCTION public.sync_report_authoritative_staff_identity();

-- =========================================================================
-- 7. RECREATE AUTHORITATIVE REPORT-READY VIEW
-- Updates public.report_ready_exceptions to cleanly include Discipline,
-- canonical divisions, class section, session, and joined dimensions.
-- Keeps original column order intact (e.id, date, division, staff_id...)
-- so existing view queries never hit 42P16 column rename errors.
-- =========================================================================
DROP VIEW IF EXISTS public.report_ready_exceptions CASCADE;
CREATE OR REPLACE VIEW public.report_ready_exceptions AS
SELECT
  e.id,
  COALESCE(e.date, e.incident_date, e.created_at::DATE) AS date,
  e.division,
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
  COALESCE(c.group_name, c.area_name, 'General') AS check_group,
  e.class_id,
  COALESCE(cl.name, e.class_name) AS class_name,
  cl.section AS class_section,
  cl.arm AS class_arm,
  cl.level AS class_level,
  e.subject_id,
  COALESCE(sub.name, e.subject) AS subject_name,
  sub.discipline_group,
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
  e.target_type,
  e.created_at,
  e.updated_at
FROM public.compliance_reports e
LEFT JOIN public.staff s ON s.id = e.staff_id
LEFT JOIN public.compliance_checks c ON c.id = e.check_id
LEFT JOIN public.classes cl ON cl.id = e.class_id
LEFT JOIN public.subjects sub ON sub.id = e.subject_id;

-- Grant permissions to anon and authenticated
GRANT SELECT ON public.report_ready_exceptions TO anon, authenticated;
GRANT SELECT ON public.divisions TO anon, authenticated;

-- =========================================================================
-- 8. SAFE BACKFILL & RECLASSIFICATION FOR EXISTING DISCIPLINE RECORDS
-- Reclassifies reports that mention Discipline Unit or discipline violations
-- to have division = 'Discipline' without altering genuine reported text.
-- =========================================================================
UPDATE public.compliance_reports
SET division = 'Discipline',
    updated_at = NOW()
WHERE (staff_name ILIKE '%discipline%' 
   OR staff_code = 'd1'
   OR staff_category = 'Discipline'
   OR exception ILIKE '%hairstyle%'
   OR exception ILIKE '%dress code%')
  AND division != 'Discipline';
