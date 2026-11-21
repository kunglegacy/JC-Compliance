-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — ADDITIVE PROFILE AVATAR & EVIDENCE NOTES MIGRATION
-- Safe, idempotent migration (run in Supabase SQL Editor if needed).
-- =========================================================================

-- 1. Cloud profile avatar columns on user_roles
ALTER TABLE public.user_roles
  ADD COLUMN IF NOT EXISTS avatar_url TEXT;

ALTER TABLE public.user_roles
  ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMPTZ DEFAULT NOW();

-- 2. Structured evidence-level notes & multi-evidence arrays on compliance_reports
ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];

ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_types TEXT[];

ALTER TABLE public.compliance_reports
  ADD COLUMN IF NOT EXISTS evidence_notes JSONB DEFAULT '[]'::jsonb;

-- 3. Self-service profile update RLS + privilege escalation guard
-- Allows authenticated users (auth.uid() = user_id) to update ONLY their own
-- avatar_url, avatar_updated_at, and display_name without modifying role,
-- is_owner, is_founder, status, is_verified, user_id, or email.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'user_roles'
      AND policyname = 'user_roles_self_profile_update'
  ) THEN
    CREATE POLICY "user_roles_self_profile_update"
      ON public.user_roles FOR UPDATE
      USING (auth.uid() = user_id)
      WITH CHECK (auth.uid() = user_id);
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.guard_user_roles_self_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NOT NULL AND NOT public.is_super_admin(auth.uid()) THEN
    IF auth.uid() <> OLD.user_id THEN
      RAISE EXCEPTION 'Unauthorized update to user_roles';
    END IF;
    IF NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.email IS DISTINCT FROM OLD.email
       OR NEW.role IS DISTINCT FROM OLD.role
       OR NEW.is_owner IS DISTINCT FROM OLD.is_owner
       OR NEW.is_founder IS DISTINCT FROM OLD.is_founder
       OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.is_verified IS DISTINCT FROM OLD.is_verified
       OR NEW.staff_number IS DISTINCT FROM OLD.staff_number THEN
      RAISE EXCEPTION 'Authenticated users may only update display_name, avatar_url, and avatar_updated_at';
    END IF;
    NEW.avatar_updated_at := NOW();
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_user_roles_self_update ON public.user_roles;
CREATE TRIGGER trg_guard_user_roles_self_update
  BEFORE UPDATE ON public.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION public.guard_user_roles_self_update();

-- 4. Optional RPC for updating own profile cleanly
CREATE OR REPLACE FUNCTION public.update_my_profile(
  p_display_name TEXT DEFAULT NULL,
  p_avatar_url TEXT DEFAULT NULL,
  p_clear_avatar BOOLEAN DEFAULT FALSE
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  UPDATE public.user_roles
  SET
    display_name = COALESCE(NULLIF(BTRIM(p_display_name), ''), display_name),
    avatar_url = CASE
      WHEN p_clear_avatar THEN NULL
      WHEN p_avatar_url IS NOT NULL THEN NULLIF(BTRIM(p_avatar_url), '')
      ELSE avatar_url
    END,
    avatar_updated_at = NOW()
  WHERE user_id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_my_profile(TEXT, TEXT, BOOLEAN) TO authenticated;
