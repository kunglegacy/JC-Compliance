-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — STAGE 2: PRIVATE EVIDENCE STORAGE BUCKET
-- Run this in your Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- =========================================================================

-- 1. Create or Update 'compliance-evidence' as a strictly PRIVATE bucket
-- public = FALSE ensures evidence cannot be accessed by guessing a URL.
-- Access is granted strictly via time-limited signed URLs generated for authenticated users.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'compliance-evidence',
  'compliance-evidence',
  FALSE,
  104857600, -- 100 MB max file size limit
  ARRAY[
    -- Images
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/svg+xml',
    'image/gif',
    -- Audio
    'audio/mpeg',
    'audio/mp3',
    'audio/wav',
    'audio/x-wav',
    'audio/mp4',
    'audio/x-m4a',
    'audio/webm',
    'audio/ogg',
    'audio/aac',
    -- Video
    'video/mp4',
    'video/webm',
    'video/quicktime',
    'video/ogg',
    'video/x-m4v',
    -- Documents
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

-- 3. Revoke public/anonymous access to storage.objects for compliance-evidence
DROP POLICY IF EXISTS "Public storage read" ON storage.objects;
DROP POLICY IF EXISTS "Public storage upload" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated read compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated upload compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated update compliance-evidence" ON storage.objects;
DROP POLICY IF EXISTS "Allow authenticated delete compliance-evidence" ON storage.objects;

-- 4. Policy: Authenticated Directorate users can read evidence via signed tokens
CREATE POLICY "Allow authenticated read compliance-evidence"
  ON storage.objects FOR SELECT
  TO authenticated
  USING (bucket_id = 'compliance-evidence');

-- 6. Policy: Authenticated Directorate users can upload compliance evidence
CREATE POLICY "Allow authenticated upload compliance-evidence"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'compliance-evidence' AND
    (storage.foldername(name))[1] = 'evidence'
  );

-- 7. Policy: Authenticated Directorate users can update their uploaded evidence
CREATE POLICY "Allow authenticated update compliance-evidence"
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (bucket_id = 'compliance-evidence');

-- 8. Policy: Authenticated Directorate users can delete compliance evidence
CREATE POLICY "Allow authenticated delete compliance-evidence"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (bucket_id = 'compliance-evidence');

-- 9. Confirmation query
SELECT id, name, public, file_size_limit, array_length(allowed_mime_types, 1) AS allowed_types_count
FROM storage.buckets
WHERE id = 'compliance-evidence';
