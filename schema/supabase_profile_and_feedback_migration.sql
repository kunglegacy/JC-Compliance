-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — ADDITIVE PROFILE AVATAR & EVIDENCE NOTES MIGRATION
-- Safe, idempotent migration (run in Supabase SQL Editor if needed).
-- =========================================================================

-- 1. Cloud profile avatar column on user_roles
ALTER TABLE public.user_roles
  ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- 2. Structured evidence-level notes & multi-evidence arrays on compliance_reports
ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];

ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_types TEXT[];

ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_notes JSONB DEFAULT '[]'::jsonb;
