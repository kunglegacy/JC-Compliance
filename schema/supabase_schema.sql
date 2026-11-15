-- =========================================================================
-- JCBest Schools Directorate Compliance Management System
-- FULL DATABASE SCHEMA v3.0 — Supabase (PostgreSQL)
-- Run this entire script in your Supabase SQL Editor
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================
-- SAFE, NON-DESTRUCTIVE SCHEMA INITIALIZATION (IDEMPOTENT)
-- Never drops tables or destroys existing user compliance data.
-- If any table, column, trigger, policy, or row already exists, it passes safely.
-- =========================================================================

-- =========================================================================
-- 1. USER ROLES TABLE (DIRECTORATE REGISTRY)
-- Manages roles: super_admin | admin | standard
-- Owner is: ifeanyiof360@gmail.com (SUPER ADMIN + is_owner = TRUE)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.user_roles (
  id            UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role          TEXT NOT NULL DEFAULT 'standard' CHECK (role IN ('super_admin', 'admin', 'standard', 'directorate')),
  display_name  TEXT NOT NULL DEFAULT '',
  staff_number  TEXT,
  is_verified   BOOLEAN DEFAULT TRUE,
  is_founder    BOOLEAN DEFAULT FALSE,
  is_owner      BOOLEAN DEFAULT FALSE,
  assigned_by   UUID REFERENCES auth.users(id),
  verified_by   UUID REFERENCES auth.users(id),
  verified_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id)
);

ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'standard';
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS staff_number TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT TRUE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_founder BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_owner BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS assigned_by UUID REFERENCES auth.users(id);
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS verified_by UUID REFERENCES auth.users(id);
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- =========================================================================
-- =========================================================================
-- 2. STAFF REGISTRY TABLE (Authoritative Identity for Academic & Operations)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.staff (
  id                UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  staff_code        TEXT UNIQUE NOT NULL,
  name              TEXT NOT NULL,
  department        TEXT NOT NULL DEFAULT 'Academic' CHECK (department IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline')),
  staff_group       TEXT NOT NULL DEFAULT 'General',
  role              TEXT NOT NULL DEFAULT 'Staff',
  school_section    TEXT DEFAULT 'SENIOR_SECONDARY',
  assigned_location TEXT,
  subjects          TEXT,
  phone             TEXT,
  email             TEXT,
  status            TEXT DEFAULT 'Active' CHECK (status IN ('Active', 'Archived')),
  active            BOOLEAN DEFAULT TRUE,
  category          TEXT, -- Legacy compatibility column
  notes             TEXT,
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_code TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS department TEXT DEFAULT 'Academic';
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_group TEXT DEFAULT 'General';
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'Staff';
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS school_section TEXT DEFAULT 'SENIOR_SECONDARY';
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS assigned_location TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS subjects TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'Active';
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS category TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS staff_code_unique_lower_idx ON public.staff (LOWER(TRIM(staff_code)));
CREATE INDEX IF NOT EXISTS idx_staff_active ON public.staff(active);
CREATE INDEX IF NOT EXISTS idx_staff_school_section ON public.staff(school_section);
CREATE INDEX IF NOT EXISTS idx_staff_department ON public.staff(department);
CREATE INDEX IF NOT EXISTS idx_staff_staff_group ON public.staff(staff_group);

-- =========================================================================
-- 3. CLASSES TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.classes (
  id          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name        TEXT NOT NULL UNIQUE,
  level       TEXT NOT NULL DEFAULT '',
  arm         TEXT,
  section     TEXT DEFAULT 'SENIOR_SECONDARY',
  order_index INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS level TEXT DEFAULT '';
ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS arm TEXT;
ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS section TEXT DEFAULT 'SENIOR_SECONDARY';
ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS order_index INT DEFAULT 0;
ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- 4. SUBJECTS TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.subjects (
  id               UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name             TEXT NOT NULL UNIQUE,
  code             TEXT,
  department       TEXT,
  discipline_group TEXT,
  created_at       TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.subjects ADD COLUMN IF NOT EXISTS code TEXT;
ALTER TABLE public.subjects ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE public.subjects ADD COLUMN IF NOT EXISTS discipline_group TEXT;
ALTER TABLE public.subjects ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- 4b. REFERENCE GROUPS & REPORTING PERIODS TABLES
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.academic_discipline_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  description TEXT,
  order_index INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.operations_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  description TEXT,
  order_index INT DEFAULT 0
);

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

-- =========================================================================
-- 5. COMPLIANCE REPORTS TABLE (Official Exception Repository)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.compliance_reports (
  id                UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  report_number     TEXT UNIQUE NOT NULL,
  reported_by       UUID REFERENCES auth.users(id),
  reporter_id       UUID, -- Secure reference for standard role isolation
  reporter_name     TEXT NOT NULL DEFAULT '',
  reporting_officer TEXT, -- Verified display name for report generation
  reporter_role     TEXT NOT NULL DEFAULT '',
  incident_date     DATE NOT NULL DEFAULT CURRENT_DATE,
  date              DATE DEFAULT CURRENT_DATE,
  week_number       INT NOT NULL DEFAULT 1 CHECK (week_number BETWEEN 1 AND 12),
  term              TEXT NOT NULL DEFAULT 'First Term',
  staff_id          UUID REFERENCES public.staff(id) ON DELETE RESTRICT,
  staff_name        TEXT,
  staff_code        TEXT,
  staff_category    TEXT,
  check_id          TEXT,
  class_id          UUID REFERENCES public.classes(id) ON DELETE SET NULL,
  class_name        TEXT,
  subject_id        UUID REFERENCES public.subjects(id) ON DELETE SET NULL,
  subject           TEXT,
  exception         TEXT NOT NULL DEFAULT '',
  exception_text    TEXT,
  exception_tags    TEXT[],
  effect            TEXT NOT NULL DEFAULT '',
  effect_text       TEXT,
  effect_tags       TEXT[],
  severity          TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'Low', 'Medium', 'High', 'Critical')),
  category          TEXT NOT NULL DEFAULT 'CONDUCT',
  division          TEXT NOT NULL DEFAULT 'Academic' CHECK (division IN ('CDS', 'Academic', 'Operation', 'Operations', 'Discipline')),
  target_type       TEXT NOT NULL DEFAULT 'staff' CHECK (target_type IN ('staff', 'division')),
  compliance_check  TEXT,
  violation_level   INT NOT NULL DEFAULT 1 CHECK (violation_level BETWEEN 1 AND 5),
  evidence_url      TEXT,
  evidence_type     TEXT DEFAULT 'none',
  evidence_urls     TEXT[],
  evidence_types    TEXT[],
  status            TEXT DEFAULT 'OPEN',
  review_status     TEXT DEFAULT 'UNSEEN' CHECK (review_status IN ('SEEN', 'UNSEEN', 'Open', 'Seen', 'Unseen')),
  is_seen           BOOLEAN DEFAULT FALSE,
  seen_at           TIMESTAMPTZ,
  seen_by           TEXT,
  resolution_notes  TEXT,
  resolved_by       UUID REFERENCES auth.users(id),
  resolved_at       TIMESTAMPTZ,
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reported_by UUID REFERENCES auth.users(id);
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporter_id UUID;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporter_name TEXT DEFAULT '';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporting_officer TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporter_role TEXT DEFAULT '';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS incident_date DATE DEFAULT CURRENT_DATE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS date DATE DEFAULT CURRENT_DATE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS week_number INT DEFAULT 1;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS term TEXT DEFAULT 'First Term';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_id UUID REFERENCES public.staff(id) ON DELETE RESTRICT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_name TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_code TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_category TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS check_id TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS class_id UUID REFERENCES public.classes(id) ON DELETE SET NULL;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS class_name TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS subject_id UUID REFERENCES public.subjects(id) ON DELETE SET NULL;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS subject TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS exception_text TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS exception_tags TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS effect_text TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS effect_tags TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS severity TEXT DEFAULT 'MEDIUM';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'CONDUCT';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS target_type TEXT DEFAULT 'staff';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS compliance_check TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS violation_level INT DEFAULT 1;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_url TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_type TEXT DEFAULT 'none';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_types TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'OPEN';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS review_status TEXT DEFAULT 'UNSEEN';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS is_seen BOOLEAN DEFAULT FALSE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_by TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolution_notes TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolved_by UUID REFERENCES auth.users(id);
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS session TEXT DEFAULT '2026/2027';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- Safely add compliance reports staff integrity constraint if not already present
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_compliance_reports_staff_integrity'
  ) THEN
    ALTER TABLE public.compliance_reports
    ADD CONSTRAINT chk_compliance_reports_staff_integrity CHECK (
      (division = 'CDS' AND target_type = 'division')
      OR
      (division = 'Discipline')
      OR
      (division IN ('Academic', 'Operation', 'Operations') AND staff_id IS NOT NULL)
    );
  END IF;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- =========================================================================
-- 6. WEEKLY TRACKER TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.weekly_tracker (
  id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  term            TEXT NOT NULL,
  week_number     INT NOT NULL CHECK (week_number BETWEEN 1 AND 12),
  report_count    INT DEFAULT 0,
  last_report_at  TIMESTAMPTZ,
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  updated_at      TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, term, week_number)
);

ALTER TABLE public.weekly_tracker ADD COLUMN IF NOT EXISTS report_count INT DEFAULT 0;
ALTER TABLE public.weekly_tracker ADD COLUMN IF NOT EXISTS last_report_at TIMESTAMPTZ;
ALTER TABLE public.weekly_tracker ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.weekly_tracker ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- 7. REPORT AUDIT LOG
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.report_audit_log (
  id            UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  report_id     UUID NOT NULL REFERENCES public.compliance_reports(id) ON DELETE CASCADE,
  action        TEXT NOT NULL,
  from_status   TEXT,
  to_status     TEXT,
  performed_by  UUID REFERENCES auth.users(id),
  performer_name TEXT,
  notes         TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS action TEXT;
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS from_status TEXT;
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS to_status TEXT;
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS performed_by UUID REFERENCES auth.users(id);
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS performer_name TEXT;
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.report_audit_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- 8. SYSTEM SETTINGS
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.system_settings (
  key         TEXT PRIMARY KEY,
  value       TEXT NOT NULL,
  updated_by  UUID REFERENCES auth.users(id),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.system_settings ADD COLUMN IF NOT EXISTS value TEXT DEFAULT '';
ALTER TABLE public.system_settings ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES auth.users(id);
ALTER TABLE public.system_settings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- =========================================================================
-- 9. COMPLIANCE HIERARCHY (Area -> Check -> Observation)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.compliance_areas (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  division    TEXT NOT NULL DEFAULT 'Academic' CHECK (division IN ('CDS', 'Academic', 'Operation', 'Operations', 'Discipline')),
  description TEXT,
  order_index INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS name TEXT DEFAULT '';
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS order_index INT DEFAULT 0;
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE TABLE IF NOT EXISTS public.compliance_checks (
  id          TEXT PRIMARY KEY,
  check_code  TEXT,
  division    TEXT NOT NULL DEFAULT 'Academic' CHECK (division IN ('Academic', 'CDS', 'Operation', 'Operations', 'Discipline')),
  area_name   TEXT NOT NULL DEFAULT 'Academic',
  area_id     TEXT,
  name        TEXT NOT NULL DEFAULT '',
  description TEXT,
  order_index INT DEFAULT 0,
  is_active   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure all compliance_checks columns exist idempotently
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS check_code TEXT;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
UPDATE public.compliance_checks SET division = 'Academic' WHERE division IS NULL OR division = '';
ALTER TABLE public.compliance_checks ALTER COLUMN division SET DEFAULT 'Academic';

ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS area_name TEXT DEFAULT 'Academic';
UPDATE public.compliance_checks SET area_name = 'Academic' WHERE area_name IS NULL OR area_name = '';
ALTER TABLE public.compliance_checks ALTER COLUMN area_name SET DEFAULT 'Academic';

ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS area_id TEXT;
ALTER TABLE public.compliance_checks ADD COLUMN IF NOT EXISTS group_name TEXT;
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

CREATE TABLE IF NOT EXISTS public.compliance_observations (
  id            TEXT PRIMARY KEY,
  area_id       TEXT NOT NULL REFERENCES public.compliance_areas(id) ON DELETE CASCADE,
  area_name     TEXT,
  check_id      TEXT NOT NULL REFERENCES public.compliance_checks(id) ON DELETE CASCADE,
  check_name    TEXT,
  status        TEXT NOT NULL CHECK (status IN ('Compliant', 'Exception')),
  severity      TEXT CHECK (severity IN ('Low', 'Medium', 'High', 'Critical')),
  date          DATE NOT NULL DEFAULT CURRENT_DATE,
  note          TEXT,
  reported_by   UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  reporter_name TEXT,
  evidence_urls TEXT[],
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS area_id TEXT REFERENCES public.compliance_areas(id) ON DELETE CASCADE;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS area_name TEXT;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS check_id TEXT REFERENCES public.compliance_checks(id) ON DELETE CASCADE;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS check_name TEXT;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'Compliant';
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS severity TEXT;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS date DATE DEFAULT CURRENT_DATE;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS reported_by UUID REFERENCES auth.users(id) ON DELETE SET NULL;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS reporter_name TEXT;
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.compliance_observations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_compliance_obs_area_check'
  ) THEN
    ALTER TABLE public.compliance_observations
    ADD CONSTRAINT uq_compliance_obs_area_check UNIQUE (area_id, check_id);
  END IF;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_reports_staff_id ON public.compliance_reports(staff_id);
CREATE INDEX IF NOT EXISTS idx_reports_division ON public.compliance_reports(division);
CREATE INDEX IF NOT EXISTS idx_reports_target_type ON public.compliance_reports(target_type);
CREATE INDEX IF NOT EXISTS idx_reports_date ON public.compliance_reports(date DESC);
CREATE INDEX IF NOT EXISTS idx_reports_incident_date ON public.compliance_reports(incident_date DESC);
CREATE INDEX IF NOT EXISTS idx_reports_check_id ON public.compliance_reports(check_id);
CREATE INDEX IF NOT EXISTS idx_reports_class_id ON public.compliance_reports(class_id);
CREATE INDEX IF NOT EXISTS idx_reports_subject_id ON public.compliance_reports(subject_id);
CREATE INDEX IF NOT EXISTS idx_reports_reported_by ON public.compliance_reports(reported_by);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_id ON public.compliance_reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_reports_week_number ON public.compliance_reports(week_number);
CREATE INDEX IF NOT EXISTS idx_reports_term ON public.compliance_reports(term);
CREATE INDEX IF NOT EXISTS idx_reports_status ON public.compliance_reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_review_status ON public.compliance_reports(review_status);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON public.compliance_reports(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_weekly_tracker_user ON public.weekly_tracker(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON public.user_roles(user_id);

-- =========================================================================
-- AUTHORITATIVE REPORT-READY VIEW
-- Updates public.report_ready_exceptions to cleanly include Discipline,
-- canonical divisions, class section, session, and joined dimensions.
-- Drops view cascade first so PostgreSQL never throws 42P16 cannot drop columns error.
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

CREATE INDEX IF NOT EXISTS idx_comp_areas_order ON public.compliance_areas(order_index);
CREATE INDEX IF NOT EXISTS idx_comp_checks_area_id ON public.compliance_checks(area_id);
CREATE INDEX IF NOT EXISTS idx_comp_checks_order ON public.compliance_checks(order_index);

CREATE INDEX IF NOT EXISTS idx_obs_area_id ON public.compliance_observations(area_id);
CREATE INDEX IF NOT EXISTS idx_obs_check_id ON public.compliance_observations(check_id);
CREATE INDEX IF NOT EXISTS idx_obs_status ON public.compliance_observations(status);
CREATE INDEX IF NOT EXISTS idx_obs_severity ON public.compliance_observations(severity);
CREATE INDEX IF NOT EXISTS idx_obs_date ON public.compliance_observations(date DESC);
CREATE INDEX IF NOT EXISTS idx_obs_reported_by ON public.compliance_observations(reported_by);

-- Auto-update timestamps
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_staff_updated ON public.staff;
CREATE TRIGGER trg_staff_updated BEFORE UPDATE ON public.staff FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_reports_updated ON public.compliance_reports;
CREATE TRIGGER trg_reports_updated BEFORE UPDATE ON public.compliance_reports FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_user_roles_updated ON public.user_roles;
CREATE TRIGGER trg_user_roles_updated BEFORE UPDATE ON public.user_roles FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_weekly_tracker_updated ON public.weekly_tracker;
CREATE TRIGGER trg_weekly_tracker_updated BEFORE UPDATE ON public.weekly_tracker FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_comp_areas_updated ON public.compliance_areas;
CREATE TRIGGER trg_comp_areas_updated BEFORE UPDATE ON public.compliance_areas FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_comp_checks_updated ON public.compliance_checks;
CREATE TRIGGER trg_comp_checks_updated BEFORE UPDATE ON public.compliance_checks FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_obs_updated ON public.compliance_observations;
CREATE TRIGGER trg_obs_updated BEFORE UPDATE ON public.compliance_observations FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- Auto-generate report number
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

DROP TRIGGER IF EXISTS trg_report_number ON public.compliance_reports;
CREATE TRIGGER trg_report_number BEFORE INSERT ON public.compliance_reports FOR EACH ROW EXECUTE FUNCTION generate_report_number();

-- Auto-sync weekly tracker
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
CREATE TRIGGER trg_sync_weekly_tracker AFTER INSERT ON public.compliance_reports FOR EACH ROW EXECUTE FUNCTION sync_weekly_tracker();

-- RLS
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.compliance_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.weekly_tracker ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.report_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.get_user_role(uid UUID) RETURNS TEXT AS $$
  SELECT role FROM public.user_roles WHERE user_id = uid LIMIT 1;
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_owner(uid UUID) RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles r
    LEFT JOIN auth.users u ON u.id = r.user_id
    WHERE r.user_id = uid 
      AND (r.is_owner = TRUE OR r.is_founder = TRUE OR LOWER(TRIM(u.email)) = 'ifeanyiof360@gmail.com')
  );
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_super_admin(uid UUID) RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND (role = 'super_admin' OR is_owner = TRUE OR is_founder = TRUE)
  ) OR public.is_owner(uid);
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_admin_or_above(uid UUID) RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND (role IN ('super_admin', 'admin') OR is_owner = TRUE OR is_founder = TRUE)
  ) OR public.is_owner(uid);
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

-- Trigger: Owner Inviolability (Blocks Demotion, Role Alteration, Deletion of Owner)
CREATE OR REPLACE FUNCTION public.enforce_owner_protection()
RETURNS TRIGGER AS $$
DECLARE
  target_email TEXT;
  caller_is_owner BOOLEAN;
BEGIN
  SELECT LOWER(TRIM(email)) INTO target_email FROM auth.users WHERE id = OLD.user_id;
  IF OLD.is_owner = TRUE OR OLD.is_founder = TRUE OR target_email = 'ifeanyiof360@gmail.com' THEN
    IF TG_OP = 'DELETE' THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account (ifeanyiof360@gmail.com) is permanently protected and cannot be deleted.';
    END IF;
    IF NEW.role != 'super_admin' OR NEW.is_owner != TRUE THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account must permanently retain Super Admin permissions and Owner protection.';
    END IF;
    caller_is_owner := public.is_owner(auth.uid());
    IF auth.uid() IS NOT NULL AND auth.uid() != OLD.user_id AND NOT caller_is_owner THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: Only the Owner can update their own account profile.';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_owner_roles ON public.user_roles;
CREATE TRIGGER trg_protect_owner_roles
  BEFORE UPDATE OR DELETE ON public.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_owner_protection();

-- Trigger: User Role Insertion & Escalation Hardening
CREATE OR REPLACE FUNCTION public.enforce_role_creation_security()
RETURNS TRIGGER AS $$
DECLARE
  target_email TEXT;
  caller_is_sa BOOLEAN;
BEGIN
  SELECT LOWER(TRIM(email)) INTO target_email FROM auth.users WHERE id = NEW.user_id;
  IF target_email = 'ifeanyiof360@gmail.com' THEN
    NEW.role := 'super_admin';
    NEW.is_owner := TRUE;
    NEW.is_founder := TRUE;
    NEW.is_verified := TRUE;
    RETURN NEW;
  END IF;

  caller_is_sa := public.is_super_admin(auth.uid());
  IF NOT caller_is_sa THEN
    NEW.role := 'standard';
    NEW.is_owner := FALSE;
    NEW.is_founder := FALSE;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_role_creation_security ON public.user_roles;
CREATE TRIGGER trg_role_creation_security
  BEFORE INSERT ON public.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_role_creation_security();

-- Trigger: Authoritative Report Ownership & Reporting Officer Derivation
CREATE OR REPLACE FUNCTION public.enforce_report_ownership()
RETURNS TRIGGER AS $$
DECLARE
  officer_name TEXT;
  officer_role TEXT;
BEGIN
  IF auth.uid() IS NOT NULL THEN
    NEW.reported_by := auth.uid();
    NEW.reporter_id := auth.uid();
    SELECT display_name, role INTO officer_name, officer_role
    FROM public.user_roles
    WHERE user_id = auth.uid()
    LIMIT 1;

    IF officer_name IS NOT NULL AND officer_name != '' THEN
      NEW.reporter_name := officer_name;
      NEW.reporting_officer := officer_name;
    END IF;

    IF officer_role IS NOT NULL THEN
      NEW.reporter_role := (CASE 
        WHEN officer_role = 'super_admin' THEN 'Super Admin'
        WHEN officer_role = 'admin' THEN 'Vice Principal'
        ELSE 'Compliance Inspector'
      END);
    END IF;
  END IF;

  IF NEW.review_status IS NULL OR NEW.review_status = '' THEN
    NEW.review_status := 'UNSEEN';
  END IF;
  IF NEW.is_seen IS NULL THEN
    NEW.is_seen := FALSE;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_enforce_report_ownership ON public.compliance_reports;
CREATE TRIGGER trg_enforce_report_ownership
  BEFORE INSERT ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_report_ownership();

-- User roles policies
DROP POLICY IF EXISTS "user_roles_select_policy" ON public.user_roles;
CREATE POLICY "user_roles_select_policy" ON public.user_roles FOR SELECT USING (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "user_roles_insert_policy" ON public.user_roles;
CREATE POLICY "user_roles_insert_policy" ON public.user_roles FOR INSERT WITH CHECK (user_id = auth.uid() OR public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "user_roles_update_policy" ON public.user_roles;
CREATE POLICY "user_roles_update_policy" ON public.user_roles FOR UPDATE USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "user_roles_delete_policy" ON public.user_roles;
CREATE POLICY "user_roles_delete_policy" ON public.user_roles FOR DELETE USING (public.is_super_admin(auth.uid()));

-- Staff policies
DROP POLICY IF EXISTS "staff_select_policy" ON public.staff;
CREATE POLICY "staff_select_policy" ON public.staff FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "staff_insert_policy" ON public.staff;
CREATE POLICY "staff_insert_policy" ON public.staff FOR INSERT WITH CHECK (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "staff_update_policy" ON public.staff;
CREATE POLICY "staff_update_policy" ON public.staff FOR UPDATE USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "staff_delete_policy" ON public.staff;
CREATE POLICY "staff_delete_policy" ON public.staff FOR DELETE USING (public.is_super_admin(auth.uid()));

-- Reports policies
DROP POLICY IF EXISTS "reports_select_admin" ON public.compliance_reports;
CREATE POLICY "reports_select_admin" ON public.compliance_reports FOR SELECT USING (public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "reports_select_standard" ON public.compliance_reports;
CREATE POLICY "reports_select_standard" ON public.compliance_reports FOR SELECT USING (NOT public.is_admin_or_above(auth.uid()) AND (reported_by = auth.uid() OR reporter_id = auth.uid()));

DROP POLICY IF EXISTS "reports_insert_policy" ON public.compliance_reports;
CREATE POLICY "reports_insert_policy" ON public.compliance_reports FOR INSERT WITH CHECK (auth.uid() IS NOT NULL AND (reported_by = auth.uid() OR reporter_id = auth.uid() OR reported_by IS NULL));

DROP POLICY IF EXISTS "reports_update_super_admin" ON public.compliance_reports;
CREATE POLICY "reports_update_super_admin" ON public.compliance_reports FOR UPDATE USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "reports_update_reporter_review" ON public.compliance_reports;
CREATE POLICY "reports_update_reporter_review" ON public.compliance_reports FOR UPDATE USING (auth.uid() IS NOT NULL AND (reported_by = auth.uid() OR reporter_id = auth.uid()));

DROP POLICY IF EXISTS "reports_delete_policy" ON public.compliance_reports;
CREATE POLICY "reports_delete_policy" ON public.compliance_reports FOR DELETE USING (public.is_super_admin(auth.uid()));

-- Classes & Subjects
DROP POLICY IF EXISTS "Read classes" ON public.classes;
CREATE POLICY "Read classes" ON public.classes FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin+ manage classes" ON public.classes;
CREATE POLICY "Admin+ manage classes" ON public.classes FOR ALL USING (is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "Read subjects" ON public.subjects;
CREATE POLICY "Read subjects" ON public.subjects FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin+ manage subjects" ON public.subjects;
CREATE POLICY "Admin+ manage subjects" ON public.subjects FOR ALL USING (is_admin_or_above(auth.uid()));

-- Weekly tracker
DROP POLICY IF EXISTS "Users see own tracker, admin sees all" ON public.weekly_tracker;
CREATE POLICY "Users see own tracker, admin sees all" ON public.weekly_tracker FOR SELECT USING (user_id = auth.uid() OR is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "System inserts tracker" ON public.weekly_tracker;
CREATE POLICY "System inserts tracker" ON public.weekly_tracker FOR INSERT WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "System updates tracker" ON public.weekly_tracker;
CREATE POLICY "System updates tracker" ON public.weekly_tracker FOR UPDATE USING (user_id = auth.uid() OR is_admin_or_above(auth.uid()));

-- Audit log
DROP POLICY IF EXISTS "Admin+ read audit" ON public.report_audit_log;
CREATE POLICY "Admin+ read audit" ON public.report_audit_log FOR SELECT USING (is_admin_or_above(auth.uid()));

-- Settings
DROP POLICY IF EXISTS "Admin+ manage settings" ON public.system_settings;
CREATE POLICY "Admin+ manage settings" ON public.system_settings FOR ALL USING (is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "All read settings" ON public.system_settings;
CREATE POLICY "All read settings" ON public.system_settings FOR SELECT USING (auth.uid() IS NOT NULL);

-- Compliance Areas & Checks
ALTER TABLE public.compliance_areas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance areas" ON public.compliance_areas;
CREATE POLICY "Authenticated read compliance areas" ON public.compliance_areas FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin manage compliance areas" ON public.compliance_areas;
CREATE POLICY "Admin manage compliance areas" ON public.compliance_areas FOR ALL USING (is_admin_or_above(auth.uid()));

ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance checks" ON public.compliance_checks;
CREATE POLICY "Authenticated read compliance checks" ON public.compliance_checks FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin manage compliance checks" ON public.compliance_checks;
CREATE POLICY "Admin manage compliance checks" ON public.compliance_checks FOR ALL USING (is_admin_or_above(auth.uid()));

-- Compliance Observations
ALTER TABLE public.compliance_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated read compliance observations" ON public.compliance_observations FOR SELECT USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated insert compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated insert compliance observations" ON public.compliance_observations FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated update compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated update compliance observations" ON public.compliance_observations FOR UPDATE USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin delete compliance observations" ON public.compliance_observations;
CREATE POLICY "Admin delete compliance observations" ON public.compliance_observations FOR DELETE USING (is_admin_or_above(auth.uid()));

-- =========================================================================
-- SEED DATA
-- =========================================================================
INSERT INTO public.classes (name, level, arm) VALUES
  ('JSS 1 Gold', 'JSS', 'Gold'), ('JSS 1 Diamond', 'JSS', 'Diamond'), ('JSS 1 Silver', 'JSS', 'Silver'),
  ('JSS 2 Gold', 'JSS', 'Gold'), ('JSS 2 Diamond', 'JSS', 'Diamond'), ('JSS 2 Silver', 'JSS', 'Silver'),
  ('JSS 3 Gold', 'JSS', 'Gold'), ('JSS 3 Diamond', 'JSS', 'Diamond'), ('JSS 3 Silver', 'JSS', 'Silver'),
  ('SSS 1 Science', 'SSS', 'Science'), ('SSS 1 Commercial', 'SSS', 'Commercial'), ('SSS 1 Arts', 'SSS', 'Arts'),
  ('SSS 2 Science', 'SSS', 'Science'), ('SSS 2 Commercial', 'SSS', 'Commercial'), ('SSS 2 Arts', 'SSS', 'Arts'),
  ('SSS 3 Science', 'SSS', 'Science'), ('SSS 3 Commercial', 'SSS', 'Commercial'), ('SSS 3 Arts', 'SSS', 'Arts'),
  ('Directorate Office', 'Admin', 'N/A'), ('Administrative Block', 'Admin', 'N/A')
ON CONFLICT DO NOTHING;

INSERT INTO public.subjects (name, department) VALUES
  ('Mathematics', 'Sciences'), ('Further Mathematics', 'Sciences'), ('Physics', 'Sciences'),
  ('Chemistry', 'Sciences'), ('Biology', 'Sciences'), ('English Language', 'Humanities'),
  ('Literature in English', 'Humanities'), ('Yoruba Language', 'Languages'), ('Economics', 'Social Sciences'),
  ('Commerce', 'Social Sciences'), ('Accounting', 'Social Sciences'), ('Government', 'Social Sciences'),
  ('History', 'Humanities'), ('Geography', 'Social Sciences'), ('Christian Religious Studies', 'Humanities'),
  ('Islamic Religious Studies', 'Humanities'), ('Technical Drawing', 'Vocational'),
  ('Basic Technology', 'Vocational'), ('Food and Nutrition', 'Vocational'), ('Agricultural Science', 'Sciences'),
  ('Computer Studies', 'Sciences'), ('Physical Education', 'Sports'), ('Civic Education', 'Social Sciences'),
  ('Business Studies', 'Social Sciences'), ('French', 'Languages'), ('Fine Art', 'Vocational'),
  ('Music', 'Vocational'), ('N/A (Non-Teaching)', 'Administrative')
ON CONFLICT DO NOTHING;

INSERT INTO public.system_settings (key, value) VALUES
  ('current_term', 'First Term'),
  ('current_session', '2026/2027'),
  ('current_week', '1'),
  ('school_name', 'JCBest Schools International'),
  ('school_address', 'Abuja, Nigeria'),
  ('max_weeks_per_term', '12')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- Seed Compliance Areas
INSERT INTO public.compliance_areas (id, name, description, order_index) VALUES
  ('cds', 'CDS', 'Corporate Digital Services and technological infrastructure compliance.', 1),
  ('operations', 'OPERATIONS', 'Core school operations, logistics, staff effectiveness, and campus protocols.', 2),
  ('academic', 'ACADEMIC', 'Academic instruction, classroom governance, staff duty, and student conduct.', 3),
  ('cbt-school-digital-marketing', 'CBT / SCHOOL & DIGITAL MARKETING', 'Digital reach, online marketing, and CBT testing administration.', 4),
  ('exam-records', 'EXAM RECORDS', 'Summative records, grade compilation, and exam script security.', 5),
  ('staff-records', 'STAFF RECORDS', 'Statutory employment files, accreditations, and personnel documentation.', 6),
  ('minders', 'MINDERS', 'Hostel care, pupil supervision, and nursery/early years welfare.', 7),
  ('transport', 'TRANSPORT', 'Fleet roadworthiness, driver discipline, vehicle maintenance, and transit safety.', 8),
  ('maintenance', 'MAINTENANCE', 'Facility upkeep, electricals, plumbing, and campus infrastructure integrity.', 9),
  ('dressing', 'DRESSING', 'Institutional uniform standards, dress codes, and professional appearance.', 10),
  ('effectiveness', 'EFFECTIVENESS', 'Staff productivity, timeline compliance, and administrative deliverables.', 11),
  ('hostel', 'HOSTEL', 'Boarding house governance, student dorm hygiene, utilities, and pastoral welfare.', 12)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index;

-- Seed Compliance Checks (85 Authoritative Checks across 4 Divisions)
CREATE OR REPLACE FUNCTION public.generate_compliance_check_id()
RETURNS TRIGGER AS $$
DECLARE
  v_prefix TEXT;
  v_max_num INT;
BEGIN
  IF NEW.division IN ('Operation', 'Operations') THEN
    NEW.division := 'Operation';
  END IF;

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

  IF NEW.id IS NULL OR TRIM(NEW.id) = '' OR NEW.id LIKE 'temp-%' OR NEW.id LIKE 'new-%' OR NEW.id = 'none-listed' THEN
    SELECT COALESCE(MAX(
      SUBSTRING(id FROM '^' || v_prefix || '([0-9]+)$')::INT
    ), 0) + 1
    INTO v_max_num
    FROM public.compliance_checks
    WHERE id ~ ('^' || v_prefix || '[0-9]+$');

    NEW.id := v_prefix || v_max_num::TEXT;
  END IF;

  IF NEW.check_code IS NULL OR TRIM(NEW.check_code) = '' THEN
    NEW.check_code := NEW.id;
  END IF;

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

INSERT INTO public.compliance_checks (id, check_code, division, area_name, area_id, name, description, order_index, is_active) VALUES
  -- ACADEMIC CHECKS (ACA1 to ACA30)
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

  -- CDS CHECKS (CDS1 to CDS11)
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

  -- OPERATIONS CHECKS (OPR1 to OPR34)
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
  ('OPR25', 'OPR25', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Promptness to class from hostel', 'Boarder punctuality from dormitory to morning assembly and classroom.', 25, TRUE),
  ('OPR26', 'OPR26', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Table etiquette', 'Dining hall manners, supervised meal queues, and decorum.', 26, TRUE),
  ('OPR27', 'OPR27', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Hot water', 'Hot water supply availability for student morning bathing.', 27, TRUE),
  ('OPR28', 'OPR28', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Drinking/using water', 'Clean treated drinking water supply and dispenser sanitation.', 28, TRUE),
  ('OPR29', 'OPR29', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Cleanliness', 'Hostel dorm sanitation, kitchen hygiene, and utensil sterilization.', 29, TRUE),
  ('OPR30', 'OPR30', 'Operation', 'Hostel / Kitchen', 'hostel-kitchen', 'Siesta enforcement', 'Adherence to designated student afternoon siesta quiet hours.', 30, TRUE),
  ('OPR31', 'OPR31', 'Operation', 'Minders', 'minders', 'Minders', 'Early years pupil supervision, playground vigilance, and dining support.', 31, TRUE),
  ('OPR32', 'OPR32', 'Operation', 'Maintenance', 'maintenance', 'Maintenance', 'Prompt fault rectification, electrical repairs, and carpentry upkeep.', 32, TRUE),
  ('OPR33', 'OPR33', 'Operation', 'Dressing', 'dressing', 'Dressing', 'Support staff uniform adherence, name badges, and professional grooming.', 33, TRUE),
  ('OPR34', 'OPR34', 'Operation', 'Effectiveness', 'effectiveness', 'Effectiveness', 'Task completion speed, diligent shift coverage, and operational output.', 34, TRUE),

  -- DISCIPLINE CHECKS (DIS1 to DIS10)
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

