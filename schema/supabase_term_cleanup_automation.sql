-- ============================================================================
-- JC-Best Compliance Hub — End-of-Term Evidence Storage Purge Automation
-- ============================================================================
-- Description:
-- Erases storage evidence media files from the 'compliance-evidence' Supabase bucket 
-- at the conclusion of each academic term (upon reaching Week 12 / Vacation or transitioning to Week 1),
-- freeing storage capacity for the next term while preserving historical exception report logs.
-- ============================================================================

-- 1. Create function to purge all evidence objects from the storage bucket
CREATE OR REPLACE FUNCTION public.purge_term_evidence_bucket()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  deleted_count integer := 0;
  report_updated_count integer := 0;
BEGIN
  -- A. Delete storage object records from storage.objects for the 'compliance-evidence' bucket
  WITH deleted_objects AS (
    DELETE FROM storage.objects
    WHERE bucket_id = 'compliance-evidence'
    RETURNING id
  )
  SELECT count(*) INTO deleted_count FROM deleted_objects;

  -- B. Reset evidence references on public.compliance_reports while preserving report records & metrics
  WITH updated_reports AS (
    UPDATE public.compliance_reports
    SET 
      evidence_url = NULL,
      evidence_type = 'none',
      evidence_urls = NULL,
      evidence_types = NULL,
      updated_at = now()
    WHERE evidence_url IS NOT NULL OR evidence_type != 'none'
    RETURNING id
  )
  SELECT count(*) INTO report_updated_count FROM updated_reports;

  RETURN jsonb_build_object(
    'success', true,
    'deleted_storage_files', deleted_count,
    'cleared_reports_count', report_updated_count,
    'message', 'Compliance evidence storage bucket successfully purged for the new academic term.'
  );
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object(
    'success', false,
    'error', SQLERRM
  );
END;
$$;

-- Grant execution permissions to authenticated users and service role
GRANT EXECUTE ON FUNCTION public.purge_term_evidence_bucket() TO authenticated;
GRANT EXECUTE ON FUNCTION public.purge_term_evidence_bucket() TO service_role;

-- 2. Create trigger function to automatically trigger evidence purge when term reaches Week 12 / Vacation or resets to Week 1
CREATE OR REPLACE FUNCTION public.handle_term_end_evidence_purge()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Trigger when current_week is updated to 'Vacation' or '12' or when term changes to a new term at Week '1'
  IF (NEW.key = 'current_week' AND (NEW.value = 'Vacation' OR NEW.value = '12')) OR
     (NEW.key = 'current_term' AND OLD.value IS DISTINCT FROM NEW.value) THEN
    PERFORM public.purge_term_evidence_bucket();
  END IF;
  RETURN NEW;
END;
$$;

-- Drop trigger if exists and recreate
DROP TRIGGER IF EXISTS trigger_auto_purge_evidence_on_term_end ON public.system_settings;

CREATE TRIGGER trigger_auto_purge_evidence_on_term_end
  AFTER UPDATE ON public.system_settings
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_term_end_evidence_purge();

-- ============================================================================
-- VERIFICATION & MANUAL EXECUTION QUERY
-- ============================================================================
-- To manually run the term evidence purge at any time in the Supabase SQL Editor:
-- SELECT public.purge_term_evidence_bucket();
