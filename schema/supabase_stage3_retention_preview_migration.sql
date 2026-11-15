-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 3 MIGRATION
-- CONFIGURABLE TERM-END RETENTION POLICY & PURGE PREVIEW
--
-- Objective:
-- Implements secure, Super Admin-only retention configuration,
-- default-OFF automated cleanup controls, and dry-run preview capabilities.
--
-- Guarantees:
-- 1. NO MUTATION: Pure dry-run preview. Zero records or files are deleted.
-- 2. SUPER ADMIN RESTRICTION: Only Super Admins can alter retention policies.
-- 3. DEFAULT OFF: Automatic cleanup defaults to disabled.
-- 4. EXPLICIT VALUE: No hardcoded retention period; requires explicit weeks value.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- 1. CREATE TERM RETENTION POLICY TABLE (Singleton)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.term_retention_policy (
  id                              INT PRIMARY KEY DEFAULT 1,
  retention_period_weeks          INT CHECK (retention_period_weeks IS NULL OR retention_period_weeks > 0),
  auto_cleanup_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
  archive_deletion_policy         TEXT NOT NULL DEFAULT 'retain_until_new_session' CHECK (archive_deletion_policy IN ('retain_until_new_session', 'custom_weeks', 'manual_only')),
  last_cleanup_at                 TIMESTAMPTZ,
  next_eligible_cleanup_at        TIMESTAMPTZ,
  updated_by                      UUID REFERENCES auth.users(id),
  updated_at                      TIMESTAMPTZ DEFAULT NOW(),
  
  CONSTRAINT chk_retention_singleton CHECK (id = 1)
);

-- Seed default initial singleton record (Disabled by default, no retention weeks set)
INSERT INTO public.term_retention_policy (id, retention_period_weeks, auto_cleanup_enabled, archive_deletion_policy)
VALUES (1, NULL, FALSE, 'retain_until_new_session')
ON CONFLICT (id) DO UPDATE SET
  archive_deletion_policy = EXCLUDED.archive_deletion_policy;

-- Seed default keys in system_settings for backward compatibility
INSERT INTO public.system_settings (key, value)
VALUES 
  ('retention_period_weeks', ''),
  ('auto_cleanup_enabled', 'false'),
  ('archive_deletion_policy', 'retain_until_new_session'),
  ('last_cleanup_timestamp', '')
ON CONFLICT (key) DO NOTHING;

-- =========================================================================
-- 2. ROW LEVEL SECURITY (RLS) POLICIES
-- Only Super Admins may modify retention policies.
-- Administrators can view retention settings and dry-run previews.
-- =========================================================================
ALTER TABLE public.term_retention_policy ENABLE ROW LEVEL SECURITY;

-- Read policy: Administrators and above
DROP POLICY IF EXISTS "retention_policy_select_admin" ON public.term_retention_policy;
CREATE POLICY "retention_policy_select_admin"
  ON public.term_retention_policy FOR SELECT
  USING (public.is_admin_or_above(auth.uid()));

-- Insert/Update policy: Super Admin only
DROP POLICY IF EXISTS "retention_policy_modify_super_admin" ON public.term_retention_policy;
CREATE POLICY "retention_policy_modify_super_admin"
  ON public.term_retention_policy FOR ALL
  USING (public.is_super_admin(auth.uid()))
  WITH CHECK (public.is_super_admin(auth.uid()));

-- =========================================================================
-- 3. STORED PROCEDURE: DRY-RUN RETENTION PURGE PREVIEW
-- Computes real eligibility metrics without altering any database records.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.get_term_retention_preview()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_policy RECORD;
  v_active_term TEXT := 'First Term';
  v_active_session TEXT := '2026/2027';
  v_term_cur RECORD;
  v_today DATE := CURRENT_DATE;
  v_preview_items JSONB := '[]'::jsonb;
  v_total_eligible_reports INT := 0;
  v_total_eligible_evidence INT := 0;
  v_total_protected_reports INT := 0;
BEGIN
  -- 1. Fetch current policy
  SELECT * INTO v_policy FROM public.term_retention_policy WHERE id = 1;
  
  -- Fetch active session and term from system_settings
  SELECT COALESCE(value, 'First Term') INTO v_active_term 
  FROM public.system_settings WHERE key = 'current_term';
  
  SELECT COALESCE(value, '2026/2027') INTO v_active_session 
  FROM public.system_settings WHERE key = 'current_session';

  -- 2. Inspect all known reporting periods
  FOR v_term_cur IN 
    SELECT 
      rp.id,
      rp.session,
      rp.term,
      rp.official_end_date,
      rp.status AS period_status,
      rp.is_current,
      snap.id AS snapshot_id,
      snap.status AS snapshot_status,
      snap.reconciliation_status AS snapshot_reconciliation,
      arch.id AS archive_id,
      arch.validation_status AS archive_validation,
      arch.archive_lifecycle_status AS archive_lifecycle
    FROM public.reporting_periods rp
    LEFT JOIN public.term_analysis_snapshots snap 
      ON snap.session = rp.session AND snap.term = rp.term AND snap.status = 'VERIFIED'
    LEFT JOIN public.term_archive_manifests arch
      ON arch.session_id = rp.session AND arch.term_id = rp.term AND arch.validation_status = 'VALIDATED'
    ORDER BY rp.created_at DESC
  LOOP
    DECLARE
      v_is_active BOOLEAN := FALSE;
      v_is_eligible BOOLEAN := TRUE;
      v_reasons TEXT[] := ARRAY[]::TEXT[];
      v_eligibility_date DATE := NULL;
      v_report_count INT := 0;
      v_evidence_count INT := 0;
      v_days_remaining INT := 0;
    BEGIN
      -- A. Count reports for this term
      SELECT COUNT(*), COUNT(evidence_url) INTO v_report_count, v_evidence_count
      FROM public.compliance_reports
      WHERE term = v_term_cur.term AND (session IS NULL OR session = v_term_cur.session);

      -- B. Check Active Session/Term Protection
      IF v_term_cur.session = v_active_session AND v_term_cur.term = v_active_term THEN
        v_is_active := TRUE;
        v_is_eligible := FALSE;
        v_reasons := array_append(v_reasons, 'PROTECTED: Currently active academic term');
      END IF;

      IF v_term_cur.is_current THEN
        v_is_eligible := FALSE;
        v_reasons := array_append(v_reasons, 'PROTECTED: Flagged as current active period');
      END IF;

      -- C. Check Official End Date
      IF v_term_cur.official_end_date IS NULL THEN
        v_is_eligible := FALSE;
        v_reasons := array_append(v_reasons, 'BLOCKED: No official term-end date recorded');
      ELSE
        -- Compute eligibility date if retention period is configured
        IF v_policy.retention_period_weeks IS NULL OR v_policy.retention_period_weeks <= 0 THEN
          v_is_eligible := FALSE;
          v_reasons := array_append(v_reasons, 'BLOCKED: Retention period in weeks is not configured');
        ELSE
          v_eligibility_date := v_term_cur.official_end_date + (v_policy.retention_period_weeks * 7);
          IF v_today < v_eligibility_date THEN
            v_is_eligible := FALSE;
            v_days_remaining := v_eligibility_date - v_today;
            v_reasons := array_append(v_reasons, format('GRACE PERIOD: Retention period active (%s days remaining)', v_days_remaining));
          END IF;
        END IF;
      END IF;

      -- D. Check Durable Snapshot Verification
      IF v_term_cur.snapshot_status IS DISTINCT FROM 'VERIFIED' OR v_term_cur.snapshot_reconciliation IS DISTINCT FROM 'PASSED' THEN
        v_is_eligible := FALSE;
        v_reasons := array_append(v_reasons, 'BLOCKED: Historical term analysis snapshot is missing or unverified');
      END IF;

      -- E. Check SQL Archive Validation
      IF v_term_cur.archive_validation IS DISTINCT FROM 'VALIDATED' THEN
        v_is_eligible := FALSE;
        v_reasons := array_append(v_reasons, 'BLOCKED: Private SQL restoration archive is missing or unvalidated');
      END IF;

      -- Update tallies
      IF v_is_eligible THEN
        v_total_eligible_reports := v_total_eligible_reports + v_report_count;
        v_total_eligible_evidence := v_total_eligible_evidence + v_evidence_count;
      ELSE
        v_total_protected_reports := v_total_protected_reports + v_report_count;
      END IF;

      -- Build item object
      v_preview_items := v_preview_items || jsonb_build_object(
        'reporting_period_id', v_term_cur.id,
        'session', v_term_cur.session,
        'term', v_term_cur.term,
        'official_end_date', v_term_cur.official_end_date,
        'configured_retention_weeks', v_policy.retention_period_weeks,
        'calculated_eligibility_date', v_eligibility_date,
        'days_until_eligible', v_days_remaining,
        'is_eligible', v_is_eligible,
        'is_active_term', v_is_active,
        'reasons', to_jsonb(v_reasons),
        'exemption_rows_count', v_report_count,
        'evidence_objects_count', v_evidence_count,
        'snapshot_status', COALESCE(v_term_cur.snapshot_status, 'MISSING'),
        'snapshot_reconciliation', COALESCE(v_term_cur.snapshot_reconciliation, 'NONE'),
        'archive_status', COALESCE(v_term_cur.archive_validation, 'MISSING')
      );
    END;
  END LOOP;

  RETURN jsonb_build_object(
    'success', true,
    'generated_at', NOW(),
    'policy', jsonb_build_object(
      'retention_period_weeks', v_policy.retention_period_weeks,
      'auto_cleanup_enabled', v_policy.auto_cleanup_enabled,
      'archive_deletion_policy', v_policy.archive_deletion_policy,
      'last_cleanup_at', v_policy.last_cleanup_at
    ),
    'summary', jsonb_build_object(
      'total_eligible_reports', v_total_eligible_reports,
      'total_eligible_evidence', v_total_eligible_evidence,
      'total_protected_reports', v_total_protected_reports,
      'is_retention_configured', (v_policy.retention_period_weeks IS NOT NULL AND v_policy.retention_period_weeks > 0)
    ),
    'items', v_preview_items
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_term_retention_preview() TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_term_retention_preview() TO service_role;

-- =========================================================================
-- ROLLBACK SPECIFICATION (Reference Only — Do not run in production)
--
-- To safely rollback this migration without data loss to existing systems:
--   DROP FUNCTION IF EXISTS public.get_term_retention_preview CASCADE;
--   DROP TABLE IF EXISTS public.term_retention_policy CASCADE;
-- =========================================================================
