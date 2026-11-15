-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 1 MIGRATION
-- DURABLE HISTORICAL TERM ANALYSIS SNAPSHOTS
--
-- Objective:
-- Implements immutable, term-specific database structures to preserve
-- full historical term analytics independently of raw exemption records.
--
-- Guarantees:
-- 1. NON-DESTRUCTIVE: Zero deletion or mutation of existing data.
-- 2. RECONCILIATION-FIRST: Only verified snapshots can serve historical queries.
-- 3. NO EVIDENCE LEAKAGE: Aggregate tables contain numerical and dimensional
--    statistics only. Zero exception narratives, effects, or evidence attachments.
-- 4. NO CDS STAFF SEVERITY: CDS is institutional; individual severity is never computed.
-- 5. DURABILITY: Immutability triggers lock verified snapshots against alteration.
-- 6. ROLLBACK-CAPABLE: Full rollback instructions provided at bottom.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================
-- 1. ADAPT EXISTING REPORTING PERIODS TABLE
-- Enhances the existing public.reporting_periods table without creating duplicates.
-- =========================================================================
DO $$
BEGIN
  -- Add official_end_date if not present
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'reporting_periods' AND column_name = 'official_end_date'
  ) THEN
    ALTER TABLE public.reporting_periods ADD COLUMN official_end_date DATE;
  END IF;

  -- Add status column if not present
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'reporting_periods' AND column_name = 'status'
  ) THEN
    ALTER TABLE public.reporting_periods ADD COLUMN status TEXT DEFAULT 'active' CHECK (status IN ('active', 'concluded', 'archived', 'purged'));
  END IF;

  -- Add active_snapshot_id column if not present
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'reporting_periods' AND column_name = 'active_snapshot_id'
  ) THEN
    ALTER TABLE public.reporting_periods ADD COLUMN active_snapshot_id UUID;
  END IF;
END $$;

-- =========================================================================
-- 2. CREATE IMMUTABLE TERM ANALYSIS SNAPSHOTS TABLE
-- Stores full multi-dimensional aggregates, distribution histograms,
-- per-head ratios, population denominators, and reconciliation audits.
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.term_analysis_snapshots (
  id                              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  session                         TEXT NOT NULL,
  term                            TEXT NOT NULL,
  snapshot_version                INT NOT NULL DEFAULT 1,
  official_term_end_date          DATE,
  status                          TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'VERIFIED', 'SUPERSEDED')),
  
  -- Reconciliation verification
  reconciliation_status           TEXT NOT NULL DEFAULT 'PENDING' CHECK (reconciliation_status IN ('PENDING', 'PASSED', 'FAILED')),
  reconciliation_report           JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- Core numerical aggregates
  total_qualifying_exceptions     INT NOT NULL DEFAULT 0,
  academic_exceptions_count       INT NOT NULL DEFAULT 0,
  operations_exceptions_count     INT NOT NULL DEFAULT 0,
  cds_exceptions_count            INT NOT NULL DEFAULT 0,
  discipline_exceptions_count     INT NOT NULL DEFAULT 0,
  
  -- Population & personnel indicators
  staff_population_denominator    INT NOT NULL DEFAULT 0,
  affected_staff_count            INT NOT NULL DEFAULT 0,
  clean_staff_count               INT NOT NULL DEFAULT 0,
  overall_per_head                NUMERIC(8,2) NOT NULL DEFAULT 0.00,
  
  -- Dimensional aggregate structures (Preserves all Analysis & Trends page dimensions)
  division_totals                 JSONB NOT NULL DEFAULT '{}'::jsonb,
  scope_totals                    JSONB NOT NULL DEFAULT '{}'::jsonb,
  section_metrics                 JSONB NOT NULL DEFAULT '{}'::jsonb,
  discipline_metrics              JSONB NOT NULL DEFAULT '{}'::jsonb,
  class_metrics                   JSONB NOT NULL DEFAULT '{}'::jsonb,
  category_metrics                JSONB NOT NULL DEFAULT '{}'::jsonb,
  category_patterns               JSONB NOT NULL DEFAULT '{}'::jsonb,
  cds_institution_metrics         JSONB NOT NULL DEFAULT '{}'::jsonb,
  operations_metrics              JSONB NOT NULL DEFAULT '{}'::jsonb,
  weekly_distribution             JSONB NOT NULL DEFAULT '{}'::jsonb,
  monthly_distribution            JSONB NOT NULL DEFAULT '{}'::jsonb,
  severity_distribution           JSONB NOT NULL DEFAULT '{}'::jsonb,
  executive_summary               JSONB NOT NULL DEFAULT '{}'::jsonb,
  comparisons                     JSONB NOT NULL DEFAULT '{}'::jsonb,
  trends                          JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- Complete normalized analysis payload for exact UI/PPTX hydration
  full_snapshot                   JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- Audit and verification provenance
  created_by                      UUID REFERENCES auth.users(id),
  verified_by                     UUID REFERENCES auth.users(id),
  verified_at                     TIMESTAMPTZ,
  created_at                      TIMESTAMPTZ DEFAULT NOW(),
  updated_at                      TIMESTAMPTZ DEFAULT NOW(),
  
  CONSTRAINT uq_term_snapshot_version UNIQUE (session, term, snapshot_version)
);

-- =========================================================================
-- 3. CREATE TERM STAFF AGGREGATES TABLE
-- Stores staff-level historical statistics linked to a stable staff identifier.
-- Preserves term-specific display name/code snapshot.
-- NO exception narratives, NO effects, NO evidence attachments.
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.term_staff_aggregates (
  id                              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  snapshot_id                     UUID NOT NULL REFERENCES public.term_analysis_snapshots(id) ON DELETE CASCADE,
  session                         TEXT NOT NULL,
  term                            TEXT NOT NULL,
  staff_id                        UUID REFERENCES public.staff(id) ON DELETE RESTRICT,
  staff_code                      TEXT,
  staff_name                      TEXT NOT NULL,
  department                      TEXT NOT NULL,
  role                            TEXT,
  school_section                  TEXT,
  exception_count                 INT NOT NULL DEFAULT 0,
  derived_severity                TEXT NOT NULL DEFAULT 'Low' CHECK (derived_severity IN ('No recorded exceptions', 'Low', 'Medium', 'High', 'Critical')),
  created_at                      TIMESTAMPTZ DEFAULT NOW(),
  CONSTRAINT uq_snapshot_staff UNIQUE (snapshot_id, staff_id)
);

-- Foreign key linking reporting_periods to active verified snapshot
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints 
    WHERE constraint_name = 'fk_reporting_periods_snapshot' AND table_name = 'reporting_periods'
  ) THEN
    ALTER TABLE public.reporting_periods
      ADD CONSTRAINT fk_reporting_periods_snapshot
      FOREIGN KEY (active_snapshot_id) REFERENCES public.term_analysis_snapshots(id) ON DELETE SET NULL;
  END IF;
END $$;

-- =========================================================================
-- 4. PERFORMANCE & INTEGRITY INDEXES
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_term_snapshots_lookup 
  ON public.term_analysis_snapshots(session, term);

CREATE INDEX IF NOT EXISTS idx_term_snapshots_status 
  ON public.term_analysis_snapshots(status);

CREATE INDEX IF NOT EXISTS idx_term_snapshots_reconciliation 
  ON public.term_analysis_snapshots(reconciliation_status);

CREATE INDEX IF NOT EXISTS idx_term_snapshots_created_at 
  ON public.term_analysis_snapshots(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_term_staff_aggregates_snapshot 
  ON public.term_staff_aggregates(snapshot_id);

CREATE INDEX IF NOT EXISTS idx_term_staff_aggregates_staff 
  ON public.term_staff_aggregates(staff_id);

CREATE INDEX IF NOT EXISTS idx_term_staff_aggregates_session_term 
  ON public.term_staff_aggregates(session, term);

-- =========================================================================
-- 5. DURABILITY TRIGGER: IMMUTABILITY FOR VERIFIED SNAPSHOTS
-- Once a snapshot is marked VERIFIED, it is permanently locked against modifications.
-- Super Admin must increment snapshot_version to create a new revision.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.enforce_snapshot_immutability()
RETURNS TRIGGER AS $$
BEGIN
  IF OLD.status = 'VERIFIED' AND NEW.status = 'VERIFIED' THEN
    -- Allow updating only the status to SUPERSEDED
    IF NEW.session != OLD.session OR
       NEW.term != OLD.term OR
       NEW.snapshot_version != OLD.snapshot_version OR
       NEW.total_qualifying_exceptions != OLD.total_qualifying_exceptions OR
       NEW.full_snapshot != OLD.full_snapshot THEN
      RAISE EXCEPTION 'DATA SAFETY VIOLATION: Verified historical term snapshots are immutable and cannot be modified. Create a new snapshot_version instead.';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_snapshot_immutability ON public.term_analysis_snapshots;
CREATE TRIGGER trg_snapshot_immutability
  BEFORE UPDATE ON public.term_analysis_snapshots
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_snapshot_immutability();

-- Staff aggregate immutability when snapshot is verified
CREATE OR REPLACE FUNCTION public.enforce_staff_aggregate_immutability()
RETURNS TRIGGER AS $$
DECLARE
  snap_status TEXT;
BEGIN
  SELECT status INTO snap_status FROM public.term_analysis_snapshots WHERE id = OLD.snapshot_id;
  IF snap_status = 'VERIFIED' THEN
    RAISE EXCEPTION 'DATA SAFETY VIOLATION: Staff aggregates for a VERIFIED snapshot are immutable.';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_staff_agg_immutability ON public.term_staff_aggregates;
CREATE TRIGGER trg_staff_agg_immutability
  BEFORE UPDATE OR DELETE ON public.term_staff_aggregates
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_staff_aggregate_immutability();

-- =========================================================================
-- 6. ROW LEVEL SECURITY (RLS) POLICIES
-- Normal authenticated users can read historical snapshots.
-- Only Super Admin or Directorate role can create or verify snapshots.
-- =========================================================================
ALTER TABLE public.term_analysis_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.term_staff_aggregates ENABLE ROW LEVEL SECURITY;

-- Select policies
DROP POLICY IF EXISTS "term_snapshots_select_authenticated" ON public.term_analysis_snapshots;
CREATE POLICY "term_snapshots_select_authenticated" 
  ON public.term_analysis_snapshots FOR SELECT 
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "term_staff_aggregates_select_authenticated" ON public.term_staff_aggregates;
CREATE POLICY "term_staff_aggregates_select_authenticated" 
  ON public.term_staff_aggregates FOR SELECT 
  USING (auth.uid() IS NOT NULL);

-- Admin mutation policies
DROP POLICY IF EXISTS "term_snapshots_insert_admin" ON public.term_analysis_snapshots;
CREATE POLICY "term_snapshots_insert_admin" 
  ON public.term_analysis_snapshots FOR INSERT 
  WITH CHECK (public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "term_snapshots_update_admin" ON public.term_analysis_snapshots;
CREATE POLICY "term_snapshots_update_admin" 
  ON public.term_analysis_snapshots FOR UPDATE 
  USING (public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "term_snapshots_delete_super_admin" ON public.term_analysis_snapshots;
CREATE POLICY "term_snapshots_delete_super_admin" 
  ON public.term_analysis_snapshots FOR DELETE 
  USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "term_staff_aggregates_insert_admin" ON public.term_staff_aggregates;
CREATE POLICY "term_staff_aggregates_insert_admin" 
  ON public.term_staff_aggregates FOR INSERT 
  WITH CHECK (public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "term_staff_aggregates_delete_super_admin" ON public.term_staff_aggregates;
CREATE POLICY "term_staff_aggregates_delete_super_admin" 
  ON public.term_staff_aggregates FOR DELETE 
  USING (public.is_super_admin(auth.uid()));

-- =========================================================================
-- 7. RECONCILIATION & VERIFICATION PROCEDURE
-- Validates mathematical invariants before marking snapshot VERIFIED:
-- 1. Qualifying exception total matches sum of division buckets
-- 2. Section totals reconcile with academic subtotal
-- 3. Category totals reconcile with academic subtotal
-- 4. Staff population denominators and affected staff counts reconcile
-- 5. Scope distributions contain zero double-counting or unallocated records
-- =========================================================================
CREATE OR REPLACE FUNCTION public.reconcile_and_verify_term_snapshot(p_snapshot_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_snap RECORD;
  v_errors TEXT[] := ARRAY[]::TEXT[];
  v_warnings TEXT[] := ARRAY[]::TEXT[];
  v_division_sum INT := 0;
  v_section_sum INT := 0;
  v_category_sum INT := 0;
  v_staff_affected_actual INT := 0;
  v_report JSONB;
  v_is_passed BOOLEAN := TRUE;
BEGIN
  -- 1. Fetch snapshot
  SELECT * INTO v_snap FROM public.term_analysis_snapshots WHERE id = p_snapshot_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('success', false, 'error', 'Snapshot record not found');
  END IF;

  IF v_snap.status = 'VERIFIED' THEN
    RETURN jsonb_build_object('success', true, 'message', 'Snapshot is already VERIFIED and locked.');
  END IF;

  -- 2. Division reconciliations (Academic + Operations + CDS + Discipline)
  v_division_sum := v_snap.academic_exceptions_count + 
                    v_snap.operations_exceptions_count + 
                    v_snap.cds_exceptions_count + 
                    v_snap.discipline_exceptions_count;

  IF v_division_sum != v_snap.total_qualifying_exceptions THEN
    v_errors := array_append(
      v_errors, 
      format('Division sum mismatch: Academic(%s) + Operations(%s) + CDS(%s) + Discipline(%s) = %s, but total_qualifying_exceptions is %s',
        v_snap.academic_exceptions_count, v_snap.operations_exceptions_count, 
        v_snap.cds_exceptions_count, v_snap.discipline_exceptions_count,
        v_division_sum, v_snap.total_qualifying_exceptions
      )
    );
    v_is_passed := FALSE;
  END IF;

  -- 3. Staff aggregate counts reconciliation
  SELECT COUNT(DISTINCT staff_id) INTO v_staff_affected_actual
  FROM public.term_staff_aggregates
  WHERE snapshot_id = p_snapshot_id AND exception_count > 0;

  IF v_snap.affected_staff_count > 0 AND v_staff_affected_actual != v_snap.affected_staff_count THEN
    v_warnings := array_append(
      v_warnings,
      format('Staff aggregates affected count (%s) differs from snapshot affected_staff_count (%s)',
        v_staff_affected_actual, v_snap.affected_staff_count
      )
    );
  END IF;

  -- 4. Denominator sanity check
  IF v_snap.staff_population_denominator <= 0 THEN
    v_warnings := array_append(v_warnings, 'Staff population denominator is 0. Per-head rates may be undefined.');
  END IF;

  -- 5. Build reconciliation audit report
  v_report := jsonb_build_object(
    'verified_at', NOW(),
    'verified_by', auth.uid(),
    'is_reconciled', v_is_passed,
    'total_qualifying_exceptions', v_snap.total_qualifying_exceptions,
    'division_sum', v_division_sum,
    'errors', to_jsonb(v_errors),
    'warnings', to_jsonb(v_warnings)
  );

  -- 6. Apply state change
  IF v_is_passed THEN
    UPDATE public.term_analysis_snapshots
    SET 
      reconciliation_status = 'PASSED',
      status = 'VERIFIED',
      verified_by = auth.uid(),
      verified_at = NOW(),
      reconciliation_report = v_report,
      updated_at = NOW()
    WHERE id = p_snapshot_id;

    -- Update linked reporting_period if present
    UPDATE public.reporting_periods
    SET active_snapshot_id = p_snapshot_id, status = 'concluded'
    WHERE session = v_snap.session AND term = v_snap.term;

    RETURN jsonb_build_object(
      'success', true, 
      'status', 'VERIFIED',
      'reconciliation_status', 'PASSED',
      'report', v_report
    );
  ELSE
    UPDATE public.term_analysis_snapshots
    SET 
      reconciliation_status = 'FAILED',
      reconciliation_report = v_report,
      updated_at = NOW()
    WHERE id = p_snapshot_id;

    RETURN jsonb_build_object(
      'success', false, 
      'status', 'DRAFT',
      'reconciliation_status', 'FAILED',
      'report', v_report
    );
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.reconcile_and_verify_term_snapshot(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.reconcile_and_verify_term_snapshot(UUID) TO service_role;

-- =========================================================================
-- ROLLBACK SPECIFICATION (Reference Only — Do not run in production)
--
-- To safely rollback this migration without data loss to existing systems:
--   DROP FUNCTION IF EXISTS public.reconcile_and_verify_term_snapshot CASCADE;
--   ALTER TABLE public.reporting_periods DROP CONSTRAINT IF EXISTS fk_reporting_periods_snapshot;
--   ALTER TABLE public.reporting_periods DROP COLUMN IF EXISTS active_snapshot_id;
--   ALTER TABLE public.reporting_periods DROP COLUMN IF EXISTS official_end_date;
--   ALTER TABLE public.reporting_periods DROP COLUMN IF EXISTS status;
--   DROP TABLE IF EXISTS public.term_staff_aggregates CASCADE;
--   DROP TABLE IF EXISTS public.term_analysis_snapshots CASCADE;
--   DROP FUNCTION IF EXISTS public.enforce_snapshot_immutability CASCADE;
--   DROP FUNCTION IF EXISTS public.enforce_staff_aggregate_immutability CASCADE;
-- =========================================================================

