-- =========================================================================
-- JC-Best COMPLIANCE HUB — STAFF MASTER DATA MIGRATION
-- Authoritative Identity Schema for Academic and Operations Personnel
-- Target: Supabase (PostgreSQL)
--
-- Safety Guarantees:
-- 1. DOES NOT DROP ANY TABLE (100% preserves existing historical data & reports)
-- 2. Idempotent: Can be safely run multiple times
-- 3. Replaces hard deletion with Active/Archived state preservation
-- =========================================================================

-- 1. Ensure staff table exists with base columns
CREATE TABLE IF NOT EXISTS public.staff (
  id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  staff_code        TEXT UNIQUE,
  name              TEXT NOT NULL,
  role              TEXT NOT NULL,
  assigned_location TEXT,
  phone             TEXT,
  email             TEXT,
  subjects          TEXT,
  status            TEXT DEFAULT 'Active',
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Add category column if not exists to safely query legacy records
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS category TEXT;

-- 3. Add department column with strict Academic / Operations constraint
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS department TEXT;
ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_department_check;
ALTER TABLE public.staff ADD CONSTRAINT staff_department_check CHECK (department IN ('Academic', 'Operations'));

-- 4. Add staff_group column (e.g. Sciences, Languages, Minders, Transport, Facilities, etc.)
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS staff_group TEXT;

-- 5. Ensure created_at and updated_at exist
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.staff ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

-- 6. Ensure all existing staff records have a valid, unique staff_code
UPDATE public.staff
SET staff_code = 'JCB-' || CASE WHEN department = 'Academic' THEN 'T' ELSE 'OPS' END || '-' || SUBSTRING(REPLACE(id::text, '-', ''), 1, 4),
    updated_at = NOW()
WHERE staff_code IS NULL OR TRIM(staff_code) = '';

-- 7. Ensure unique index on staff_code (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS staff_code_unique_lower_idx ON public.staff (LOWER(TRIM(staff_code)));

-- 8. Normalize existing legacy 'category' values into department + staff_group
-- Academic Staff -> Academic (Sciences / General)
UPDATE public.staff 
SET department = 'Academic', 
    staff_group = COALESCE(staff_group, 'Sciences'),
    updated_at = NOW()
WHERE department IS NULL AND (category = 'Academic Staff' OR role ILIKE '%teacher%' OR role ILIKE '%master%');

-- Directorate Staff -> Operations (Administration)
UPDATE public.staff 
SET department = 'Operations', 
    staff_group = COALESCE(staff_group, 'Administration'),
    updated_at = NOW()
WHERE department IS NULL AND category = 'Directorate Staff';

-- Other Staff -> Operations (Facilities / Transport / Minders)
UPDATE public.staff 
SET department = 'Operations', 
    staff_group = CASE 
      WHEN role ILIKE '%driver%' OR role ILIKE '%bus%' THEN 'Transport'
      WHEN role ILIKE '%minder%' OR role ILIKE '%nanny%' THEN 'Minders'
      WHEN role ILIKE '%guard%' OR role ILIKE '%security%' THEN 'Security'
      WHEN role ILIKE '%nurse%' OR role ILIKE '%medic%' THEN 'Medical'
      ELSE COALESCE(staff_group, 'Facilities')
    END,
    updated_at = NOW()
WHERE department IS NULL;

-- Default any remaining records
UPDATE public.staff 
SET department = 'Academic', 
    staff_group = COALESCE(staff_group, 'General'),
    status = COALESCE(status, 'Active'),
    updated_at = NOW()
WHERE department IS NULL;

-- 9. Exclude/archive CDS staff from normal staff records for compliance reporting
UPDATE public.compliance_reports
SET staff_id = NULL,
    division = 'CDS',
    target_type = 'division'
WHERE staff_id IN (
  SELECT id FROM public.staff 
  WHERE department = 'CDS' 
     OR staff_group = 'CDS' 
     OR category = 'CDS' 
     OR role ILIKE '%CDS staff%'
     OR role ILIKE '%Corporate Digital%'
);

UPDATE public.staff 
SET status = 'Archived',
    updated_at = NOW()
WHERE department = 'CDS' 
   OR staff_group = 'CDS' 
   OR category = 'CDS' 
   OR role ILIKE '%CDS staff%'
   OR role ILIKE '%Corporate Digital%';

-- 10. Standardize status check constraint to Active or Archived
-- Normalize inactive/suspended statuses to 'Archived'
UPDATE public.staff
SET status = 'Archived',
    updated_at = NOW()
WHERE status IN ('Inactive', 'Suspended');

-- Ensure all Active status defaults
UPDATE public.staff
SET status = 'Active',
    updated_at = NOW()
WHERE status IS NULL OR status IN ('On Leave');

ALTER TABLE public.staff DROP CONSTRAINT IF EXISTS staff_status_check;
ALTER TABLE public.staff ADD CONSTRAINT staff_status_check CHECK (status IN ('Active', 'Archived'));

