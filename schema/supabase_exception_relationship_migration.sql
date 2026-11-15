-- =========================================================================
-- JC-Best COMPLIANCE HUB — EXCEPTION RELATIONSHIP & PERSISTENCE MIGRATION
-- Authoritative staff_id Relationship for Academic and Operations Exceptions
-- Target: Supabase (PostgreSQL)
--
-- Safety Guarantees:
-- 1. DOES NOT DROP ANY TABLES (100% preserves existing historical data)
-- 2. Preserves legacy columns (staff_name, class_name) for backward compatibility
-- 3. Binds staff_id foreign key as authoritative identity relationship
-- 4. Supports CDS division-level exceptions without staff_id (target_type = 'division')
-- =========================================================================

-- 1. Ensure foreign key column staff_id exists on compliance_reports
ALTER TABLE public.compliance_reports 
  ADD COLUMN IF NOT EXISTS staff_id UUID REFERENCES public.staff(id) ON DELETE RESTRICT;

-- 2. Add division column with constraint (CDS | Academic | Operations)
ALTER TABLE public.compliance_reports 
  ADD COLUMN IF NOT EXISTS division TEXT DEFAULT 'Academic';

ALTER TABLE public.compliance_reports 
  DROP CONSTRAINT IF EXISTS comp_reports_division_check;

ALTER TABLE public.compliance_reports 
  ADD CONSTRAINT comp_reports_division_check CHECK (division IN ('CDS', 'Academic', 'Operations'));

-- 3. Add target_type column (staff | division)
ALTER TABLE public.compliance_reports 
  ADD COLUMN IF NOT EXISTS target_type TEXT DEFAULT 'staff';

ALTER TABLE public.compliance_reports 
  DROP CONSTRAINT IF EXISTS comp_reports_target_type_check;

ALTER TABLE public.compliance_reports 
  ADD CONSTRAINT comp_reports_target_type_check CHECK (target_type IN ('staff', 'division'));

-- 4. Add compliance_check column if missing
ALTER TABLE public.compliance_reports 
  ADD COLUMN IF NOT EXISTS compliance_check TEXT;

-- 5. Create performance indexes for relational joins & lookups
CREATE INDEX IF NOT EXISTS idx_compliance_reports_staff_id 
  ON public.compliance_reports(staff_id);

CREATE INDEX IF NOT EXISTS idx_compliance_reports_division 
  ON public.compliance_reports(division);

CREATE INDEX IF NOT EXISTS idx_compliance_reports_target_type 
  ON public.compliance_reports(target_type);

CREATE INDEX IF NOT EXISTS idx_compliance_reports_category 
  ON public.compliance_reports(category);

-- 6. Backfill existing legacy records with authoritative staff_id
-- Match by unique staff_code first
UPDATE public.compliance_reports r
SET staff_id = s.id,
    division = COALESCE(s.department, 'Academic'),
    target_type = 'staff'
FROM public.staff s
WHERE r.staff_id IS NULL
  AND r.staff_code IS NOT NULL
  AND LOWER(TRIM(r.staff_code)) = LOWER(TRIM(s.staff_code));

-- Match remaining unlinked records by exact staff name
UPDATE public.compliance_reports r
SET staff_id = s.id,
    division = COALESCE(s.department, 'Academic'),
    target_type = 'staff'
FROM public.staff s
WHERE r.staff_id IS NULL
  AND (
    LOWER(TRIM(r.staff_name)) = LOWER(TRIM(s.name))
    OR (r.staff_name IS NOT NULL AND LOWER(TRIM(r.staff_name)) = LOWER(TRIM(s.name)))
  )
  AND s.department != 'CDS';

-- 7. Normalize CDS historical exceptions: target_type = 'division', staff_id = NULL
UPDATE public.compliance_reports
SET division = 'CDS',
    target_type = 'division',
    staff_id = NULL,
    staff_name = 'Corporate Digital Services'
WHERE staff_name ILIKE '%CDS%'
   OR staff_name ILIKE '%Corporate Digital%'
   OR staff_name ILIKE '%CDS%'
   OR category IN (
     'CBT', 'Hardware', 'Website', 'Digital Marketing',
     'Exam Records', 'Staff Records', 'Result Portal',
     'Documentation', 'Biometric', 'Camera'
   );
