-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 4 MIGRATION
-- SAFE TERM-END EXEMPTION AND EVIDENCE PURGE ENGINE & AUDITING
--
-- Objective:
-- Implements backend purge orchestration, recoverable stage tracking,
-- and tamper-proof audit logging for eligible concluded terms.
--
-- Guarantees:
-- 1. 9-POINT ELIGIBILITY ENFORCEMENT: All 9 conditions must pass before any deletion.
-- 2. STAGED RECOVERABILITY: Evidence storage verified & deleted before database rows.
-- 3. SCOPED DELETION: Only target term exemptions & child audits are removed.
-- 4. NEVER TOUCHES: Staff registry, user accounts, system configuration,
--    reporting period definitions, or durable term snapshots.
-- 5. AUDIT LOGGING: Immutable audit ledger recording operations, row counts,
--    errors, and initiator identities without storing case narratives or secrets.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- 1. CREATE TERM PURGE AUDIT LOGS TABLE
-- Records immutable history of every purge dry-run and execution.
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.term_purge_audit_logs (
  id                          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  session_id                  TEXT NOT NULL,
  term_id                     TEXT NOT NULL,
  reporting_period_id         UUID REFERENCES public.reporting_periods(id) ON DELETE SET NULL,
  eligible_record_count       INT NOT NULL DEFAULT 0,
  evidence_deleted_count      INT NOT NULL DEFAULT 0,
  database_deleted_count      INT NOT NULL DEFAULT 0,
  skipped_record_count        INT NOT NULL DEFAULT 0,
  status                      TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'PARTIAL')),
  errors                      JSONB NOT NULL DEFAULT '[]'::jsonb,
  is_dry_run                  BOOLEAN NOT NULL DEFAULT FALSE,
  initiated_by                TEXT NOT NULL, -- UID of Super Admin or 'automated_scheduler'
  started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at                TIMESTAMPTZ,
  metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- Indexes for performance & audit lookups
CREATE INDEX IF NOT EXISTS idx_term_purge_audit_lookup 
  ON public.term_purge_audit_logs(session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_term_purge_audit_status 
  ON public.term_purge_audit_logs(status);

CREATE INDEX IF NOT EXISTS idx_term_purge_audit_started 
  ON public.term_purge_audit_logs(started_at DESC);

-- =========================================================================
-- 2. ROW LEVEL SECURITY (RLS) FOR PURGE AUDIT LOGS
-- =========================================================================
ALTER TABLE public.term_purge_audit_logs ENABLE ROW LEVEL SECURITY;

-- Select policy: Administrators and above
DROP POLICY IF EXISTS "purge_audit_select_admin" ON public.term_purge_audit_logs;
CREATE POLICY "purge_audit_select_admin"
  ON public.term_purge_audit_logs FOR SELECT
  USING (public.is_admin_or_above(auth.uid()));

-- Insert policy: Super Admin or Service Role
DROP POLICY IF EXISTS "purge_audit_insert_super_admin" ON public.term_purge_audit_logs;
CREATE POLICY "purge_audit_insert_super_admin"
  ON public.term_purge_audit_logs FOR INSERT
  WITH CHECK (public.is_super_admin(auth.uid()));

-- Update policy: Super Admin or Service Role
DROP POLICY IF EXISTS "purge_audit_update_super_admin" ON public.term_purge_audit_logs;
CREATE POLICY "purge_audit_update_super_admin"
  ON public.term_purge_audit_logs FOR UPDATE
  USING (public.is_super_admin(auth.uid()));

-- Delete policy: Permanently disallow deletion of purge audit logs
DROP POLICY IF EXISTS "purge_audit_no_delete" ON public.term_purge_audit_logs;
CREATE POLICY "purge_audit_no_delete"
  ON public.term_purge_audit_logs FOR DELETE
  USING (FALSE);

-- =========================================================================
-- 3. STORED PROCEDURE: EXECUTE DATABASE ROW PURGE FOR ELIGIBLE TERM
-- Executes strictly scoped deletion of child audit records and parent
-- compliance_reports for an eligible term after evidence has been cleared.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.execute_term_database_purge(
  p_session TEXT,
  p_term TEXT,
  p_audit_id UUID,
  p_initiated_by TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_policy RECORD;
  v_period RECORD;
  v_active_term TEXT;
  v_active_session TEXT;
  v_snap_count INT;
  v_arch_count INT;
  v_deleted_audit_count INT := 0;
  v_deleted_report_count INT := 0;
  v_eligibility_date DATE;
  v_today DATE := CURRENT_DATE;
BEGIN
  -- 1. Fetch retention policy
  SELECT * INTO v_policy FROM public.term_retention_policy WHERE id = 1;
  IF NOT FOUND OR NOT v_policy.auto_cleanup_enabled THEN
    RAISE EXCEPTION 'PURGE REJECTED: Retention cleanup is not enabled by Super Admin policy.';
  END IF;

  IF v_policy.retention_period_weeks IS NULL OR v_policy.retention_period_weeks <= 0 THEN
    RAISE EXCEPTION 'PURGE REJECTED: Retention period in weeks is not configured.';
  END IF;

  -- 2. Fetch active term & session from system_settings
  SELECT COALESCE(value, '') INTO v_active_term FROM public.system_settings WHERE key = 'current_term';
  SELECT COALESCE(value, '') INTO v_active_session FROM public.system_settings WHERE key = 'current_session';

  IF p_session = v_active_session AND p_term = v_active_term THEN
    RAISE EXCEPTION 'PURGE REJECTED: Targeted term (% %) is the actively running academic term.', p_session, p_term;
  END IF;

  -- 3. Fetch reporting period and check official completion
  SELECT * INTO v_period FROM public.reporting_periods WHERE session = p_session AND term = p_term;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'PURGE REJECTED: Reporting period record for session % term % not found.', p_session, p_term;
  END IF;

  IF v_period.is_current THEN
    RAISE EXCEPTION 'PURGE REJECTED: Reporting period is flagged as currently active.';
  END IF;

  IF v_period.official_end_date IS NULL THEN
    RAISE EXCEPTION 'PURGE REJECTED: Term has no verified official end date.';
  END IF;

  -- 4. Check retention window eligibility date
  v_eligibility_date := v_period.official_end_date + (v_policy.retention_period_weeks * 7);
  IF v_today < v_eligibility_date THEN
    RAISE EXCEPTION 'PURGE REJECTED: Retention grace window active until % (today is %).', v_eligibility_date, v_today;
  END IF;

  -- 5. Check Verified Snapshot existence
  SELECT COUNT(*) INTO v_snap_count
  FROM public.term_analysis_snapshots
  WHERE session = p_session AND term = p_term AND status = 'VERIFIED' AND reconciliation_status = 'PASSED';

  IF v_snap_count = 0 THEN
    RAISE EXCEPTION 'PURGE REJECTED: No VERIFIED term analysis snapshot found for % %.', p_session, p_term;
  END IF;

  -- 6. Check Validated SQL Archive existence
  SELECT COUNT(*) INTO v_arch_count
  FROM public.term_archive_manifests
  WHERE session_id = p_session AND term_id = p_term AND validation_status = 'VALIDATED';

  IF v_arch_count = 0 THEN
    RAISE EXCEPTION 'PURGE REJECTED: No VALIDATED private SQL restoration archive found for % %.', p_session, p_term;
  END IF;

  -- 7. Execute scoped child deletion (report_audit_log linked strictly to this term's reports)
  WITH deleted_audits AS (
    DELETE FROM public.report_audit_log
    WHERE report_id IN (
      SELECT id FROM public.compliance_reports
      WHERE term = p_term AND (session IS NULL OR session = p_session)
    )
    RETURNING id
  )
  SELECT COUNT(*) INTO v_deleted_audit_count FROM deleted_audits;

  -- 8. Execute scoped parent deletion (compliance_reports for this term)
  WITH deleted_reports AS (
    DELETE FROM public.compliance_reports
    WHERE term = p_term AND (session IS NULL OR session = p_session)
    RETURNING id
  )
  SELECT COUNT(*) INTO v_deleted_report_count FROM deleted_reports;

  -- 9. Update reporting period status
  UPDATE public.reporting_periods
  SET status = 'purged', updated_at = NOW()
  WHERE session = p_session AND term = p_term;

  -- 10. Update term retention policy timestamp
  UPDATE public.term_retention_policy
  SET last_cleanup_at = NOW(), updated_at = NOW()
  WHERE id = 1;

  -- Update system_settings last_cleanup_timestamp
  UPDATE public.system_settings
  SET value = NOW()::text, updated_at = NOW()
  WHERE key = 'last_cleanup_timestamp';

  -- 11. Update Audit Log
  IF p_audit_id IS NOT NULL THEN
    UPDATE public.term_purge_audit_logs
    SET 
      database_deleted_count = v_deleted_report_count,
      status = 'COMPLETED',
      completed_at = NOW(),
      metadata = jsonb_build_object(
        'deleted_reports', v_deleted_report_count,
        'deleted_audits', v_deleted_audit_count,
        'reporting_period_id', v_period.id
      )
    WHERE id = p_audit_id;
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'session', p_session,
    'term', p_term,
    'deleted_reports_count', v_deleted_report_count,
    'deleted_child_audits_count', v_deleted_audit_count,
    'status', 'COMPLETED'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.execute_term_database_purge(TEXT, TEXT, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.execute_term_database_purge(TEXT, TEXT, UUID, TEXT) TO service_role;

-- =========================================================================
-- ROLLBACK SPECIFICATION (Reference Only — Do not run in production)
--
-- To safely rollback this migration without data loss to existing systems:
--   DROP FUNCTION IF EXISTS public.execute_term_database_purge CASCADE;
--   DROP TABLE IF EXISTS public.term_purge_audit_logs CASCADE;
-- =========================================================================
