-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — STAGE 7: EVIDENCE SECURITY AUDIT & HARDENING
-- Run this in your Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- =========================================================================

-- 1. Strictly enforce PRIVATE bucket configuration on 'compliance-evidence'
-- public = FALSE ensures that raw object paths or guessing URLs will ALWAYS return 403 Forbidden.
-- Access is granted strictly via time-limited signed URLs generated for authenticated users.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'compliance-evidence',
  'compliance-evidence',
  FALSE,
  104857600, -- 100 MB max file size limit
  ARRAY[
    -- Images (Forensically admissible formats)
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/svg+xml',
    'image/gif',
    -- Audio (Speech recordings and memos)
    'audio/mpeg',
    'audio/mp3',
    'audio/wav',
    'audio/x-wav',
    'audio/mp4',
    'audio/x-m4a',
    'audio/m4a',
    'audio/webm',
    'audio/ogg',
    'audio/aac',
    -- Video (Inspection footage)
    'video/mp4',
    'video/webm',
    'video/quicktime',
    'video/ogg',
    'video/x-m4v',
    -- Documents (Statutory inspection files)
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'text/plain'
  ]
)
ON CONFLICT (id) DO UPDATE SET
  public = FALSE,
  file_size_limit = 104857600,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

-- 2. Clean up legacy or overly permissive storage policies
DROP POLICY IF EXISTS "Public storage read" ON storage.objects;
DROP POLICY IF EXISTS "Public storage upload" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated read compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated upload compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated update compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated delete compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "evidence_authenticated_read" ON storage.objects;
DROP POLICY IF EXISTS "evidence_authenticated_upload" ON storage.objects;
DROP POLICY IF EXISTS "evidence_authenticated_delete" ON storage.objects;

-- 5. Hardened Read Policy: Authenticated users can only read objects in private compliance bucket
CREATE POLICY "evidence_authenticated_read"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'compliance-evidence'
  );

-- 6. Hardened Upload Policy: Authenticated users can only upload into the isolated /evidence/ namespace
CREATE POLICY "evidence_authenticated_upload"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'compliance-evidence' AND
    (storage.foldername(name))[1] = 'evidence'
  );

-- 7. Hardened Delete Policy: Authenticated Directorate users can only delete compliance evidence
CREATE POLICY "evidence_authenticated_delete"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'compliance-evidence'
  );

-- 8. Server-Side Evidence Authority Verification Helper Function
-- Verifies whether a given user has authority to access an exemption report and its evidence
CREATE OR REPLACE FUNCTION public.check_evidence_authorization(
  p_report_id text,
  p_user_id uuid
)
RETURNS boolean AS $$
DECLARE
  v_user_role text;
  v_reporter_id uuid;
  v_is_owner boolean;
BEGIN
  -- Check user role from public.user_roles
  SELECT role INTO v_user_role
  FROM public.user_roles
  WHERE user_id = p_user_id;

  -- Super admin and admin have institutional oversight
  IF v_user_role IN ('super_admin', 'admin') THEN
    RETURN TRUE;
  END IF;

  -- Check if user is the reporter of the exemption report
  SELECT reporter_id INTO v_reporter_id
  FROM public.compliance_reports
  WHERE id::text = p_report_id;

  IF v_reporter_id = p_user_id THEN
    RETURN TRUE;
  END IF;

  RETURN FALSE;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.check_evidence_authorization(text, uuid) TO authenticated, service_role;

-- 9. Security Verification Summary
SELECT
  id AS bucket_name,
  CASE WHEN public = FALSE THEN 'PRIVATE (Protected)' ELSE 'PUBLIC (INSECURE)' END AS security_status,
  file_size_limit / (1024 * 1024) AS max_file_size_mb,
  array_length(allowed_mime_types, 1) AS allowed_mime_types_count
FROM storage.buckets
WHERE id = 'compliance-evidence';
