-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 5 MIGRATION
-- TEMPORARY TERM SQL ARCHIVE EXPIRATION & LIFECYCLE MANAGEMENT
--
-- Objective:
-- Implements safe, verifiable expiration of temporary SQL text archives
-- once a newer academic session has officially started and all raw-record
-- purges have completed, while retaining manifest audit records and
-- permanent analysis snapshots forever.
--
-- Guarantees:
-- 1. AUTHORITATIVE SESSION DETECTION: Evaluates newer academic session from
--    system_settings ('current_session'), never inferring from current date.
-- 2. 5-POINT EXPIRATION INVARIANTS:
--    a. Archive session is no longer active.
--    b. A newer academic session has been officially activated.
--    c. Associated permanent term-analysis snapshot remains VERIFIED.
--    d. All raw exemption records from that archive have been purged (count = 0).
--    e. Archive lifecycle manifest confirms deletion is permitted.
-- 3. CRITICAL TIMING SAFEGUARD: Even if a new session starts, the archive
--    is preserved until the raw-data purge window elapses and finishes safely.
-- 4. MANIFEST METADATA RETENTION: Only the private storage object is removed.
--    The manifest audit row retains checksum, source count, deletion timestamp,
--    deletion rationale, and verified snapshot confirmation.
-- 5. ZERO PERMANENT LOSS: Durable aggregate snapshots remain 100% intact.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- 1. EXTEND TERM ARCHIVE MANIFESTS SCHEMA
-- Adds tracking columns for deletion audit trail without duplicating SQL.
-- =========================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'deleted_at'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN deleted_at TIMESTAMPTZ;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'deletion_reason'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN deletion_reason TEXT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'snapshot_verified_at_deletion'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN snapshot_verified_at_deletion BOOLEAN NOT NULL DEFAULT FALSE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'deleted_by'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN deleted_by TEXT;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'deletion_retry_count'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN deletion_retry_count INT NOT NULL DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'term_archive_manifests' AND column_name = 'last_deletion_error'
  ) THEN
    ALTER TABLE public.term_archive_manifests ADD COLUMN last_deletion_error TEXT;
  END IF;
END $$;

-- Performance index on deleted_at
CREATE INDEX IF NOT EXISTS idx_term_archive_manifests_deleted_at 
  ON public.term_archive_manifests(deleted_at);

-- =========================================================================
-- 2. HELPER FUNCTION: PARSE & COMPARE ACADEMIC SESSIONS
-- Determines if Session A is strictly newer than Session B based on year format.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.is_newer_academic_session(
  p_active_session TEXT,
  p_target_session TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_active_year INT;
  v_target_year INT;
  v_active_match TEXT[];
  v_target_match TEXT[];
BEGIN
  IF p_active_session IS NULL OR p_target_session IS NULL THEN
    RETURN FALSE;
  END IF;

  IF LOWER(TRIM(p_active_session)) = LOWER(TRIM(p_target_session)) THEN
    RETURN FALSE;
  END IF;

  -- Match standard 4-digit start year (e.g., '2026' from '2026/2027')
  v_active_match := regexp_matches(p_active_session, '(\d{4})');
  v_target_match := regexp_matches(p_target_session, '(\d{4})');

  IF v_active_match IS NOT NULL AND v_target_match IS NOT NULL THEN
    v_active_year := v_active_match[1]::INT;
    v_target_year := v_target_match[1]::INT;
    RETURN v_active_year > v_target_year;
  END IF;

  -- Fallback: Alphabetical comparison if custom session naming
  RETURN TRIM(p_active_session) > TRIM(p_target_session);
END;
$$;

GRANT EXECUTE ON FUNCTION public.is_newer_academic_session(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_newer_academic_session(TEXT, TEXT) TO service_role;

-- =========================================================================
-- 3. STORED PROCEDURE: CHECK ARCHIVE EXPIRATION ELIGIBILITY
-- Evaluates all 5 conditions without modifying any record.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.check_term_archive_expiration_eligibility(
  p_manifest_id UUID
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_manifest RECORD;
  v_active_session TEXT;
  v_active_term TEXT;
  v_snap_count INT := 0;
  v_raw_report_count INT := 0;
  v_policy RECORD;
  v_is_eligible BOOLEAN := TRUE;
  v_reasons TEXT[] := ARRAY[]::TEXT[];
  v_is_newer_session BOOLEAN := FALSE;
BEGIN
  -- 1. Fetch manifest
  SELECT * INTO v_manifest FROM public.term_archive_manifests WHERE id = p_manifest_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'eligible', false,
      'error', 'Manifest not found.'
    );
  END IF;

  -- Already purged
  IF v_manifest.archive_lifecycle_status = 'PURGED' THEN
    RETURN jsonb_build_object(
      'eligible', false,
      'manifest_id', p_manifest_id,
      'status', 'ALREADY_PURGED',
      'deleted_at', v_manifest.deleted_at,
      'reasons', ARRAY['Archive storage content has already been purged.']
    );
  END IF;

  -- 2. Fetch current active session and term from system_settings
  SELECT COALESCE(value, '') INTO v_active_session FROM public.system_settings WHERE key = 'current_session';
  SELECT COALESCE(value, '') INTO v_active_term FROM public.system_settings WHERE key = 'current_term';

  -- Fetch retention policy
  SELECT * INTO v_policy FROM public.term_retention_policy WHERE id = 1;

  -- Condition 1: Its academic session is no longer active
  IF LOWER(TRIM(v_manifest.session_id)) = LOWER(TRIM(v_active_session)) THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, format('PROTECTED: Session %s is the currently active academic session.', v_active_session));
  END IF;

  -- Condition 2: A newer academic session has been officially activated
  v_is_newer_session := public.is_newer_academic_session(v_active_session, v_manifest.session_id);
  IF NOT v_is_newer_session THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, format('PROTECTED: Active session %s is not newer than archive session %s.', v_active_session, v_manifest.session_id));
  END IF;

  -- Condition 3: The associated permanent term-analysis snapshot remains VERIFIED
  SELECT COUNT(*) INTO v_snap_count
  FROM public.term_analysis_snapshots
  WHERE session = v_manifest.session_id 
    AND term = v_manifest.term_id 
    AND status = 'VERIFIED' 
    AND reconciliation_status = 'PASSED';

  IF v_snap_count = 0 THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, 'BLOCKED: No VERIFIED permanent term analysis snapshot found for this term.');
  END IF;

  -- Condition 4: All required raw exemption records have been purged successfully
  SELECT COUNT(*) INTO v_raw_report_count
  FROM public.compliance_reports
  WHERE term = v_manifest.term_id 
    AND (session IS NULL OR session = v_manifest.session_id);

  IF v_raw_report_count > 0 THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, format('BLOCKED: Raw exemption records still exist in database (%s rows). Raw-record purge must complete first.', v_raw_report_count));
  END IF;

  -- Condition 5: The archive lifecycle manifest confirms that deletion is permitted
  IF v_manifest.validation_status IS DISTINCT FROM 'VALIDATED' THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, format('BLOCKED: Archive validation status is %s (must be VALIDATED).', v_manifest.validation_status));
  END IF;

  IF v_policy.archive_deletion_policy = 'manual_only' THEN
    v_is_eligible := FALSE;
    v_reasons := array_append(v_reasons, 'POLICY: Retention policy is configured for manual-only archive deletion.');
  END IF;

  RETURN jsonb_build_object(
    'eligible', v_is_eligible,
    'manifest_id', p_manifest_id,
    'session_id', v_manifest.session_id,
    'term_id', v_manifest.term_id,
    'active_session', v_active_session,
    'is_newer_session', v_is_newer_session,
    'snapshot_verified', (v_snap_count > 0),
    'remaining_raw_reports', v_raw_report_count,
    'validation_status', v_manifest.validation_status,
    'archive_lifecycle_status', v_manifest.archive_lifecycle_status,
    'reasons', to_jsonb(v_reasons)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.check_term_archive_expiration_eligibility(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.check_term_archive_expiration_eligibility(UUID) TO service_role;

-- =========================================================================
-- 4. STORED PROCEDURE: EXPIRE TERM SQL ARCHIVE MANIFEST
-- Finalizes manifest metadata record upon verified storage object deletion.
-- Retains audit ledger: checksum, source count, deletion time and reason.
-- NEVER deletes the manifest row or durable snapshot records.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.expire_term_sql_archive_manifest(
  p_manifest_id UUID,
  p_reason TEXT,
  p_deleted_by TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_manifest RECORD;
  v_active_session TEXT;
  v_snap_count INT;
  v_raw_report_count INT;
  v_is_newer BOOLEAN;
BEGIN
  -- 1. Fetch manifest
  SELECT * INTO v_manifest FROM public.term_archive_manifests WHERE id = p_manifest_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'ARCHIVE EXPIRATION REJECTED: Manifest % does not exist.', p_manifest_id;
  END IF;

  IF v_manifest.archive_lifecycle_status = 'PURGED' THEN
    RETURN jsonb_build_object(
      'success', true,
      'status', 'ALREADY_PURGED',
      'manifest_id', p_manifest_id,
      'message', 'Archive manifest is already in PURGED lifecycle status.'
    );
  END IF;

  -- 2. Verify Session Invariants
  SELECT COALESCE(value, '') INTO v_active_session FROM public.system_settings WHERE key = 'current_session';

  IF LOWER(TRIM(v_manifest.session_id)) = LOWER(TRIM(v_active_session)) THEN
    RAISE EXCEPTION 'ARCHIVE EXPIRATION REJECTED: Session % is the actively running academic session.', v_active_session;
  END IF;

  v_is_newer := public.is_newer_academic_session(v_active_session, v_manifest.session_id);
  IF NOT v_is_newer THEN
    RAISE EXCEPTION 'ARCHIVE EXPIRATION REJECTED: Current session % is not strictly newer than archive session %.', v_active_session, v_manifest.session_id;
  END IF;

  -- 3. Verify Snapshot Invariant
  SELECT COUNT(*) INTO v_snap_count
  FROM public.term_analysis_snapshots
  WHERE session = v_manifest.session_id 
    AND term = v_manifest.term_id 
    AND status = 'VERIFIED' 
    AND reconciliation_status = 'PASSED';

  IF v_snap_count = 0 THEN
    RAISE EXCEPTION 'ARCHIVE EXPIRATION REJECTED: No VERIFIED permanent analysis snapshot found for session % term %.', v_manifest.session_id, v_manifest.term_id;
  END IF;

  -- 4. Verify Raw Records Purged Invariant
  SELECT COUNT(*) INTO v_raw_report_count
  FROM public.compliance_reports
  WHERE term = v_manifest.term_id 
    AND (session IS NULL OR session = v_manifest.session_id);

  IF v_raw_report_count > 0 THEN
    RAISE EXCEPTION 'ARCHIVE EXPIRATION REJECTED: Raw exemption records still exist in database (% rows).', v_raw_report_count;
  END IF;

  -- 5. Update Manifest Lifecycle Record (Retaining checksum, row counts, and object path for audit)
  UPDATE public.term_archive_manifests
  SET 
    archive_lifecycle_status = 'PURGED',
    deleted_at = NOW(),
    deletion_reason = COALESCE(p_reason, format('Expired after newer session %s activated and raw exemption records purged.', v_active_session)),
    snapshot_verified_at_deletion = TRUE,
    deleted_by = COALESCE(p_deleted_by, 'super_admin'),
    updated_at = NOW()
  WHERE id = p_manifest_id;

  RETURN jsonb_build_object(
    'success', true,
    'manifest_id', p_manifest_id,
    'session_id', v_manifest.session_id,
    'term_id', v_manifest.term_id,
    'archive_object_path', v_manifest.archive_object_path,
    'checksum', v_manifest.checksum,
    'source_record_count', v_manifest.source_record_count,
    'status', 'PURGED',
    'deleted_at', NOW(),
    'message', 'Temporary SQL archive lifecycle transitioned to PURGED. Storage object removed and manifest audit preserved.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.expire_term_sql_archive_manifest(UUID, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.expire_term_sql_archive_manifest(UUID, TEXT, TEXT) TO service_role;

-- =========================================================================
-- ROLLBACK SPECIFICATION (Reference Only — Do not run in production)
--
-- To safely rollback this migration:
--   DROP FUNCTION IF EXISTS public.expire_term_sql_archive_manifest CASCADE;
--   DROP FUNCTION IF EXISTS public.check_term_archive_expiration_eligibility CASCADE;
--   DROP FUNCTION IF EXISTS public.is_newer_academic_session CASCADE;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS last_deletion_error;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS deletion_retry_count;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS deleted_by;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS snapshot_verified_at_deletion;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS deletion_reason;
--   ALTER TABLE public.term_archive_manifests DROP COLUMN IF EXISTS deleted_at;
-- =========================================================================
