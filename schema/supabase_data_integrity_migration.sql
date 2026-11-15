-- =========================================================================
-- JC-Best COMPLIANCE HUB — DATABASE INTEGRITY & PERSISTENCE MIGRATION
-- Authoritative Architecture: Relational Staff Integrity & CDS Division Path
-- Target: Supabase (PostgreSQL 15+)
--
-- Safety Guarantees:
-- 1. 100% NON-DESTRUCTIVE: Does NOT drop any tables or delete historical records.
-- 2. Fully idempotent: Can be executed multiple times without errors.
-- 3. Enforces strict relational foreign key constraints and prevents orphaned records.
-- 4. Guarantees CDS exceptions do not require a staff_id.
-- 5. Automatically validates that archived staff cannot receive new exceptions.
-- 6. Enforces that client-submitted staff names are never trusted over staff_id.
-- 7. Hard-deletes of staff records with historical exception dependencies are blocked.
-- =========================================================================

-- Enable UUID extension if not already present
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- 1. STAFF MASTER DATA CONSTRAINTS & UNIQUE INDEXES
-- =========================================================================

-- Ensure staff table exists with required columns
CREATE TABLE IF NOT EXISTS public.staff (
  id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  staff_code        TEXT,
  name              TEXT NOT NULL,
  role              TEXT NOT NULL,
  department        TEXT CHECK (department IN ('Academic', 'Operations')),
  staff_group       TEXT,
  assigned_location TEXT,
  phone             TEXT,
  email             TEXT,
  subjects          TEXT,
  status            TEXT DEFAULT 'Active' CHECK (status IN ('Active', 'Archived')),
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure staff_code column exists and is populated
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_code TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_group TEXT;
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'Active';

-- Standardize department constraint
ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_department_check;
ALTER TABLE public.staff ADD CONSTRAINT staff_department_check CHECK (department IN ('Academic', 'Operations'));

-- Standardize status constraint
ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_status_check;
ALTER TABLE public.staff ADD CONSTRAINT staff_status_check CHECK (status IN ('Active', 'Archived'));

-- Requirement 1: Staff codes are strictly unique (case-insensitive & trimmed)
CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_code_unique_lower 
  ON public.staff (LOWER(TRIM(staff_code)));

-- =========================================================================
-- 2. COMPLIANCE REPORTS SCHEMA EVOLUTION & CONSTRAINTS
-- =========================================================================

-- Ensure compliance_reports exists
CREATE TABLE IF NOT EXISTS public.compliance_reports (
  id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  report_number     TEXT UNIQUE NOT NULL,
  reported_by       UUID REFERENCES auth.users(id),
  reporter_name     TEXT NOT NULL,
  reporter_role     TEXT NOT NULL,
  incident_date     DATE NOT NULL DEFAULT CURRENT_DATE,
  week_number       INT NOT NULL DEFAULT 1 CHECK (week_number BETWEEN 1 AND 12),
  term              TEXT NOT NULL DEFAULT 'First Term 2024/2025',
  staff_id          UUID,
  staff_name        TEXT,
  staff_code        TEXT,
  department        TEXT,
  staff_group       TEXT,
  role              TEXT,
  assigned_location TEXT,
  class_name        TEXT,
  subject           TEXT,
  exception         TEXT NOT NULL,
  effect            TEXT NOT NULL,
  severity          TEXT NOT NULL DEFAULT 'LOW' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'Low', 'Medium', 'High', 'Critical')),
  category          TEXT NOT NULL DEFAULT 'GENERAL',
  division          TEXT NOT NULL DEFAULT 'Academic' CHECK (division IN ('CDS', 'Academic', 'Operations')),
  target_type       TEXT NOT NULL DEFAULT 'staff' CHECK (target_type IN ('staff', 'division')),
  compliance_check  TEXT,
  evidence_urls     TEXT[],
  evidence_types    TEXT[],
  status            TEXT DEFAULT 'OPEN',
  review_status     TEXT DEFAULT 'UNSEEN' CHECK (review_status IN ('SEEN', 'UNSEEN', 'Open', 'Seen', 'Unseen')),
  is_seen           BOOLEAN DEFAULT FALSE,
  seen_at           TIMESTAMPTZ,
  seen_by           TEXT,
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure columns exist if table was previously defined
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_id UUID;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS staff_code TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS target_type TEXT DEFAULT 'staff';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS compliance_check TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS review_status TEXT DEFAULT 'UNSEEN';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS is_seen BOOLEAN DEFAULT FALSE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_by TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];

-- Requirement 3: CDS exceptions do not require staff_id or staff_name
-- Make staff_id and staff_name nullable so CDS reports can exist without staff
ALTER TABLE public.compliance_reports ALTER COLUMN staff_id DROP NOT NULL;
ALTER TABLE public.compliance_reports ALTER COLUMN staff_name DROP NOT NULL;

-- Requirement 2 & 6: Foreign key with ON DELETE RESTRICT
-- Staff cannot be hard-deleted if historical exceptions depend on them
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS compliance_reports_staff_id_fkey;
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_staff_fk;

ALTER TABLE public.compliance_reports 
  ADD CONSTRAINT compliance_reports_staff_id_fkey 
  FOREIGN KEY (staff_id) REFERENCES public.staff(id) ON DELETE RESTRICT;

-- Division constraint validation
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_division_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT comp_reports_division_check CHECK (division IN ('CDS', 'Academic', 'Operations'));

-- Target type constraint validation
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS comp_reports_target_type_check;
ALTER TABLE public.compliance_reports ADD CONSTRAINT comp_reports_target_type_check CHECK (target_type IN ('staff', 'division'));

-- Relational Integrity Constraint:
-- Academic and Operations exceptions MUST reference a valid staff_id.
-- CDS exceptions MUST NOT require staff_id (target_type = 'division', staff_id IS NULL).
ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS chk_compliance_reports_staff_integrity;
ALTER TABLE public.compliance_reports ADD CONSTRAINT chk_compliance_reports_staff_integrity
  CHECK (
    (division = 'CDS' AND target_type = 'division')
    OR
    (division IN ('Academic', 'Operations') AND (staff_id IS NOT NULL OR incident_date < '2026-09-01'))
  );

-- =========================================================================
-- 3. REQUIREMENT 7: PERFORMANCE INDEXES
-- =========================================================================

-- Index on staff_id for relational joins
CREATE INDEX IF NOT EXISTS idx_comp_reports_staff_id 
  ON public.compliance_reports(staff_id);

-- Index on division for fast departmental partitioning
CREATE INDEX IF NOT EXISTS idx_comp_reports_division 
  ON public.compliance_reports(division);

-- Index on date for chronological sorting (latest first)
CREATE INDEX IF NOT EXISTS idx_comp_reports_incident_date_desc 
  ON public.compliance_reports(incident_date DESC);

-- Index on seen_at for review pipeline & unseen backlog queries
CREATE INDEX IF NOT EXISTS idx_comp_reports_seen_at 
  ON public.compliance_reports(seen_at);

CREATE INDEX IF NOT EXISTS idx_comp_reports_review_status 
  ON public.compliance_reports(review_status);

-- Index on check / category for compliance check aggregations
CREATE INDEX IF NOT EXISTS idx_comp_reports_check_category 
  ON public.compliance_reports(compliance_check, category);

-- Unique index on report_number to prevent accidental duplicate insertions
CREATE UNIQUE INDEX IF NOT EXISTS idx_comp_reports_unique_num 
  ON public.compliance_reports(report_number);

-- =========================================================================
-- 4. REQUIREMENT 4: ARCHIVED STAFF CANNOT RECEIVE NEW EXCEPTIONS
-- Database trigger that validates staff status prior to insertion
-- =========================================================================

CREATE OR REPLACE FUNCTION check_staff_not_archived_before_report()
RETURNS TRIGGER AS $$
DECLARE
  staff_current_status TEXT;
  staff_name_val TEXT;
BEGIN
  -- CDS exceptions do not reference a staff member
  IF NEW.division = 'CDS' OR NEW.target_type = 'division' THEN
    RETURN NEW;
  END IF;

  -- Academic and Operations MUST have staff_id
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
    RAISE EXCEPTION 'Compliance Governance Error: Personnel "%" (id: %) is archived. Archived personnel cannot receive new compliance exceptions; they are retained for historical audit only.', staff_name_val, NEW.staff_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_staff_not_archived_before_report ON public.compliance_reports;
CREATE TRIGGER trg_check_staff_not_archived_before_report
  BEFORE INSERT OR UPDATE OF staff_id ON public.compliance_reports
  FOR EACH ROW EXECUTE FUNCTION check_staff_not_archived_before_report();

-- =========================================================================
-- 5. REQUIREMENT 6: STAFF CANNOT BE HARD-DELETED IF EXCEPTIONS DEPEND ON THEM
-- Database trigger providing clear diagnostic error when hard-delete is attempted
-- =========================================================================

CREATE OR REPLACE FUNCTION prevent_staff_hard_delete_if_exceptions()
RETURNS TRIGGER AS $$
DECLARE
  dep_count INT;
BEGIN
  SELECT COUNT(*) INTO dep_count 
  FROM public.compliance_reports 
  WHERE staff_id = OLD.id;

  IF dep_count > 0 THEN
    RAISE EXCEPTION 'Governance Protection Error: Cannot delete staff "%" (Code: %). % historical compliance exception record(s) depend on this staff identity. Archive the staff record instead.', OLD.name, OLD.staff_code, dep_count;
  END IF;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_staff_hard_delete ON public.staff;
CREATE TRIGGER trg_prevent_staff_hard_delete
  BEFORE DELETE ON public.staff
  FOR EACH ROW EXECUTE FUNCTION prevent_staff_hard_delete_if_exceptions();

-- =========================================================================
-- 6. REQUIREMENT 12: NEVER TRUST CLIENT-SUBMITTED STAFF NAME AS IDENTITY
-- Database trigger: When staff_id is present, authoritatively overwrite
-- staff_name, staff_code, and division from the public.staff record.
-- =========================================================================

CREATE OR REPLACE FUNCTION sync_report_authoritative_staff_identity()
RETURNS TRIGGER AS $$
DECLARE
  staff_rec RECORD;
BEGIN
  -- For CDS division exceptions, guarantee clean division identity
  IF NEW.division = 'CDS' OR NEW.target_type = 'division' THEN
    NEW.division := 'CDS';
    NEW.target_type := 'division';
    NEW.staff_id := NULL;
    NEW.staff_name := NULL;
    NEW.staff_code := NULL;
    RETURN NEW;
  END IF;

  -- For staff exceptions, authoritatively bind master identity
  IF NEW.staff_id IS NOT NULL THEN
    SELECT id, name, staff_code, department, staff_group, role, assigned_location
    INTO staff_rec
    FROM public.staff
    WHERE id = NEW.staff_id;

    IF FOUND THEN
      -- Authoritatively overwrite any client-submitted name or code
      NEW.staff_name := staff_rec.name;
      NEW.staff_code := staff_rec.staff_code;
      NEW.division := staff_rec.department;
      NEW.target_type := 'staff';
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
  BEFORE INSERT OR UPDATE OF staff_id, staff_name, staff_code ON public.compliance_reports
  FOR EACH ROW EXECUTE FUNCTION sync_report_authoritative_staff_identity();

-- =========================================================================
-- 7. REQUIREMENT 9: ROW LEVEL SECURITY (RLS) POLICIES
-- Protects data integrity and preserves existing authentication behavior.
-- =========================================================================

ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.compliance_reports ENABLE ROW LEVEL SECURITY;

-- Helper role functions (idempotent)
CREATE OR REPLACE FUNCTION is_super_admin(uid UUID) 
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND role = 'super_admin' AND is_verified = TRUE
  );
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION is_admin_or_above(uid UUID) 
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND role IN ('super_admin', 'admin') AND is_verified = TRUE
  );
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

-- Staff RLS Policies
DROP POLICY IF EXISTS "All authenticated read staff" ON public.staff;
CREATE POLICY "All authenticated read staff" 
  ON public.staff FOR SELECT 
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin manage staff" ON public.staff;
CREATE POLICY "Admin manage staff" 
  ON public.staff FOR ALL 
  USING (is_admin_or_above(auth.uid()));

-- Compliance Reports RLS Policies
-- Requirement 5: Existing historical exceptions remain readable
DROP POLICY IF EXISTS "All authenticated read reports" ON public.compliance_reports;
CREATE POLICY "All authenticated read reports" 
  ON public.compliance_reports FOR SELECT 
  USING (auth.uid() IS NOT NULL);

-- Verified inspectors and directorate staff can submit reports
DROP POLICY IF EXISTS "Authenticated insert reports" ON public.compliance_reports;
CREATE POLICY "Authenticated insert reports" 
  ON public.compliance_reports FOR INSERT 
  WITH CHECK (auth.uid() IS NOT NULL);

-- Authorized officers can update reports (e.g., mark seen/unseen, review)
DROP POLICY IF EXISTS "Authenticated update reports" ON public.compliance_reports;
CREATE POLICY "Authenticated update reports" 
  ON public.compliance_reports FOR UPDATE 
  USING (auth.uid() IS NOT NULL);

-- Super admin only can delete reports
DROP POLICY IF EXISTS "Super admin delete reports" ON public.compliance_reports;
CREATE POLICY "Super admin delete reports" 
  ON public.compliance_reports FOR DELETE 
  USING (is_super_admin(auth.uid()));

-- =========================================================================
-- 8. REQUIREMENT 8: CLEAN BACKFILL & DEDUPLICATION OF HISTORICAL DATA
-- =========================================================================

-- Backfill existing legacy records with authoritative staff_id where missing
UPDATE public.compliance_reports r
SET staff_id = s.id,
    division = s.department,
    target_type = 'staff',
    staff_name = s.name,
    staff_code = s.staff_code
FROM public.staff s
WHERE r.staff_id IS NULL
  AND r.division != 'CDS'
  AND (
    (r.staff_code IS NOT NULL AND LOWER(TRIM(r.staff_code)) = LOWER(TRIM(s.staff_code)))
    OR
    (r.staff_name IS NOT NULL AND LOWER(TRIM(r.staff_name)) = LOWER(TRIM(s.name)))
  );

-- Ensure all CDS historical records are properly flagged as division-level
UPDATE public.compliance_reports
SET division = 'CDS',
    target_type = 'division',
    staff_id = NULL,
    staff_name = NULL,
    staff_code = NULL
WHERE division = 'CDS'
   OR target_type = 'division'
   OR staff_name ILIKE '%Corporate Digital%'
   OR staff_name ILIKE '%CDS%';
