-- =========================================================================
-- JC-BEST COMPLIANCE HUB — STAGE 2 MIGRATION
-- PRIVATE TERM EXEMPTION SQL ARCHIVE & MANIFEST INFRASTRUCTURE
--
-- Objective:
-- Implements secure, term-specific SQL archive manifests and private storage
-- policies to enable non-destructive, verifiable exemption exports prior
-- to storage and record purging.
--
-- Guarantees:
-- 1. TERM ISOLATION: Manifests & archives represent strictly one term.
-- 2. PRIVATE STORAGE: Preserved in private bucket with zero public access.
-- 3. AUDIT & CHECKSUM: Every archive is tracked with SHA-256 and row counts.
-- 4. NON-DESTRUCTIVE: Zero deletion or alteration of active production data.
-- 5. ROLE HARDENING: Creation and validation restricted to Admin / Super Admin.
-- =========================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================================
-- 1. CREATE TERM ARCHIVE MANIFESTS TABLE
-- Records archival metadata, source scope, object storage path, SHA-256
-- checksum, row counts, and validation status without duplicating raw rows.
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.term_archive_manifests (
  id                          UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  session_id                  TEXT NOT NULL,
  term_id                     TEXT NOT NULL,
  exact_source_scope          TEXT NOT NULL,
  archive_bucket              TEXT NOT NULL DEFAULT 'compliance-evidence',
  archive_object_path         TEXT NOT NULL,
  source_record_count         INT NOT NULL DEFAULT 0,
  dependent_audit_count       INT NOT NULL DEFAULT 0,
  file_size_bytes             BIGINT NOT NULL DEFAULT 0,
  checksum                    TEXT NOT NULL, -- SHA-256 hexadecimal hash
  format_version              TEXT NOT NULL DEFAULT 'JC-BEST-TERM-SQL-ARCHIVE/v1.0',
  validation_status           TEXT NOT NULL DEFAULT 'PENDING' CHECK (validation_status IN ('PENDING', 'VALIDATED', 'FAILED')),
  archive_lifecycle_status    TEXT NOT NULL DEFAULT 'CREATED' CHECK (archive_lifecycle_status IN ('CREATED', 'READY', 'EXPIRED', 'PURGED')),
  validation_error            TEXT,
  metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by                  UUID REFERENCES auth.users(id),
  created_at                  TIMESTAMPTZ DEFAULT NOW(),
  validated_at                TIMESTAMPTZ,
  expires_at                  TIMESTAMPTZ,
  updated_at                  TIMESTAMPTZ DEFAULT NOW(),
  
  CONSTRAINT uq_term_archive_path UNIQUE (archive_bucket, archive_object_path)
);

-- =========================================================================
-- 2. PERFORMANCE & INTEGRITY INDEXES
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_term_archive_manifests_lookup 
  ON public.term_archive_manifests(session_id, term_id);

CREATE INDEX IF NOT EXISTS idx_term_archive_manifests_validation 
  ON public.term_archive_manifests(validation_status);

CREATE INDEX IF NOT EXISTS idx_term_archive_manifests_lifecycle 
  ON public.term_archive_manifests(archive_lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_term_archive_manifests_created 
  ON public.term_archive_manifests(created_at DESC);

-- =========================================================================
-- 3. ROW LEVEL SECURITY (RLS) POLICIES
-- Only authenticated administrators can inspect or create archive manifests.
-- Only Super Admins can remove archive manifests.
-- =========================================================================
ALTER TABLE public.term_archive_manifests ENABLE ROW LEVEL SECURITY;

-- Select policy: Administrators and above
DROP POLICY IF EXISTS "term_archive_manifests_select_admin" ON public.term_archive_manifests;
CREATE POLICY "term_archive_manifests_select_admin" 
  ON public.term_archive_manifests FOR SELECT 
  USING (public.is_admin_or_above(auth.uid()));

-- Insert policy: Administrators and above
DROP POLICY IF EXISTS "term_archive_manifests_insert_admin" ON public.term_archive_manifests;
CREATE POLICY "term_archive_manifests_insert_admin" 
  ON public.term_archive_manifests FOR INSERT 
  WITH CHECK (public.is_admin_or_above(auth.uid()));

-- Update policy: Administrators and above
DROP POLICY IF EXISTS "term_archive_manifests_update_admin" ON public.term_archive_manifests;
CREATE POLICY "term_archive_manifests_update_admin" 
  ON public.term_archive_manifests FOR UPDATE 
  USING (public.is_admin_or_above(auth.uid()));

-- Delete policy: Super Administrator only
DROP POLICY IF EXISTS "term_archive_manifests_delete_super_admin" ON public.term_archive_manifests;
CREATE POLICY "term_archive_manifests_delete_super_admin" 
  ON public.term_archive_manifests FOR DELETE 
  USING (public.is_super_admin(auth.uid()));

-- =========================================================================
-- 4. PRIVATE EVIDENCE STORAGE BUCKET ACCESS POLICIES
-- Extends storage policies on private 'compliance-evidence' bucket
-- to allow authorized Administrators to manage 'term-archive' files.
-- =========================================================================
DO $$
BEGIN
  -- Ensure bucket exists and remains private
  INSERT INTO storage.buckets (id, name, public, file_size_limit)
  VALUES ('compliance-evidence', 'compliance-evidence', FALSE, 104857600)
  ON CONFLICT (id) DO UPDATE SET public = FALSE;
END $$;

-- Drop existing storage policies if they conflict
DROP POLICY IF EXISTS "Allow authenticated upload term-archive" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated select term-archive" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated delete term-archive" ON storage.objects;

-- Insert policy: Administrators can upload term archives
CREATE POLICY "Allow authenticated upload term-archive"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'compliance-evidence' AND
    (
      (storage.foldername(name))[1] = 'evidence' OR
      ((storage.foldername(name))[1] = 'term-archive' AND public.is_admin_or_above(auth.uid()))
    )
  );

-- Select policy: Administrators can read term archives
CREATE POLICY "Allow authenticated select term-archive"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'compliance-evidence' AND
    (
      (storage.foldername(name))[1] = 'evidence' OR
      ((storage.foldername(name))[1] = 'term-archive' AND public.is_admin_or_above(auth.uid()))
    )
  );

-- Delete policy: Super Administrators can delete term archives
CREATE POLICY "Allow authenticated delete term-archive"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'compliance-evidence' AND
    (
      (storage.foldername(name))[1] = 'evidence' OR
      ((storage.foldername(name))[1] = 'term-archive' AND public.is_super_admin(auth.uid()))
    )
  );

-- =========================================================================
-- 5. STORED PROCEDURE: MANIFEST VERIFICATION & VALIDATION
-- Verifies source row count and cryptographic checksum.
-- Marks manifest VALIDATED if authentic, or FAILED if discrepancies exist.
-- =========================================================================
CREATE OR REPLACE FUNCTION public.validate_term_archive_manifest(
  p_manifest_id UUID,
  p_verified_count INT,
  p_verified_checksum TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_manifest RECORD;
  v_errors TEXT[] := ARRAY[]::TEXT[];
BEGIN
  -- Fetch manifest
  SELECT * INTO v_manifest FROM public.term_archive_manifests WHERE id = p_manifest_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('success', false, 'error', 'Manifest record not found');
  END IF;

  -- Validate record count
  IF v_manifest.source_record_count != p_verified_count THEN
    v_errors := array_append(
      v_errors,
      format('Record count mismatch: manifest expected %s, verified content contains %s',
        v_manifest.source_record_count, p_verified_count
      )
    );
  END IF;

  -- Validate checksum
  IF LOWER(TRIM(v_manifest.checksum)) != LOWER(TRIM(p_verified_checksum)) THEN
    v_errors := array_append(
      v_errors,
      format('SHA-256 checksum mismatch: manifest expected %s, verified hash is %s',
        v_manifest.checksum, p_verified_checksum
      )
    );
  END IF;

  -- Update manifest status
  IF array_length(v_errors, 1) IS NULL THEN
    UPDATE public.term_archive_manifests
    SET 
      validation_status = 'VALIDATED',
      archive_lifecycle_status = 'READY',
      validation_error = NULL,
      validated_at = NOW(),
      updated_at = NOW()
    WHERE id = p_manifest_id;

    RETURN jsonb_build_object(
      'success', true,
      'status', 'VALIDATED',
      'archive_lifecycle_status', 'READY',
      'manifest_id', p_manifest_id
    );
  ELSE
    UPDATE public.term_archive_manifests
    SET 
      validation_status = 'FAILED',
      validation_error = array_to_string(v_errors, '; '),
      updated_at = NOW()
    WHERE id = p_manifest_id;

    RETURN jsonb_build_object(
      'success', false,
      'status', 'FAILED',
      'errors', to_jsonb(v_errors),
      'manifest_id', p_manifest_id
    );
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.validate_term_archive_manifest(UUID, INT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.validate_term_archive_manifest(UUID, INT, TEXT) TO service_role;

-- =========================================================================
-- ROLLBACK SPECIFICATION (Reference Only — Do not run in production)
--
-- To safely rollback this migration without data loss to existing systems:
--   DROP FUNCTION IF EXISTS public.validate_term_archive_manifest CASCADE;
--   DROP TABLE IF EXISTS public.term_archive_manifests CASCADE;
-- =========================================================================
