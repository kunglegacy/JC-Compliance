-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — FIX 403 PERMISSIONS & ROW LEVEL SECURITY (RLS)
-- Run this in your Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- =========================================================================

-- 1. Grant Schema & Table Access to Supabase API Roles (anon & authenticated)
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON ROUTINES TO anon, authenticated, service_role;

-- 2. USER ROLES TABLE (Fix Infinite Recursion & Enable Access)
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_roles_select_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_select" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_read_all" ON public.user_roles;
DROP POLICY IF EXISTS "Users read own role or admin reads all" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_insert_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_update_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_delete_policy" ON public.user_roles;
DROP POLICY IF EXISTS "Allow self-register role on signup" ON public.user_roles;

-- Allow reading user roles (non-recursive)
CREATE POLICY "user_roles_read_all"
  ON public.user_roles FOR SELECT
  TO anon, authenticated
  USING (true);

-- Allow creating user role on signup/provisioning
CREATE POLICY "user_roles_insert_policy"
  ON public.user_roles FOR INSERT
  TO anon, authenticated
  WITH CHECK (true);

-- Allow updating role
CREATE POLICY "user_roles_update_policy"
  ON public.user_roles FOR UPDATE
  TO authenticated
  USING (true);

-- Allow deleting role
CREATE POLICY "user_roles_delete_policy"
  ON public.user_roles FOR DELETE
  TO authenticated
  USING (true);

-- 3. STAFF TABLE (Staff Registry)
ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "staff_select_policy" ON public.staff;
DROP POLICY IF EXISTS "All authenticated read staff" ON public.staff;
DROP POLICY IF EXISTS "staff_select_all" ON public.staff;
DROP POLICY IF EXISTS "staff_insert_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_update_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_delete_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_manage_auth" ON public.staff;

CREATE POLICY "staff_select_all"
  ON public.staff FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "staff_manage_auth"
  ON public.staff FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 4. CLASSES TABLE
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "classes_select" ON public.classes;
DROP POLICY IF EXISTS "Read classes" ON public.classes;
DROP POLICY IF EXISTS "classes_select_all" ON public.classes;
DROP POLICY IF EXISTS "classes_manage" ON public.classes;
DROP POLICY IF EXISTS "classes_manage_all" ON public.classes;

CREATE POLICY "classes_select_all"
  ON public.classes FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "classes_manage_all"
  ON public.classes FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 5. SUBJECTS TABLE
ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "subjects_select" ON public.subjects;
DROP POLICY IF EXISTS "Read subjects" ON public.subjects;
DROP POLICY IF EXISTS "subjects_select_all" ON public.subjects;
DROP POLICY IF EXISTS "subjects_manage" ON public.subjects;
DROP POLICY IF EXISTS "subjects_manage_all" ON public.subjects;

CREATE POLICY "subjects_select_all"
  ON public.subjects FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "subjects_manage_all"
  ON public.subjects FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 6. ACADEMIC DISCIPLINE GROUPS & OPERATIONS GROUPS
ALTER TABLE public.academic_discipline_groups ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "academic_discipline_groups_select" ON public.academic_discipline_groups;
CREATE POLICY "academic_discipline_groups_select"
  ON public.academic_discipline_groups FOR SELECT
  TO anon, authenticated
  USING (true);

ALTER TABLE public.operations_groups ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "operations_groups_select" ON public.operations_groups;
CREATE POLICY "operations_groups_select"
  ON public.operations_groups FOR SELECT
  TO anon, authenticated
  USING (true);

-- 7. SYSTEM SETTINGS TABLE
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "system_settings_select" ON public.system_settings;
DROP POLICY IF EXISTS "All read settings" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_select_all" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_manage" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_manage_all" ON public.system_settings;

CREATE POLICY "system_settings_select_all"
  ON public.system_settings FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "system_settings_manage_all"
  ON public.system_settings FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 8. COMPLIANCE AREAS & COMPLIANCE CHECKS
ALTER TABLE public.compliance_areas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "compliance_areas_select" ON public.compliance_areas;
DROP POLICY IF EXISTS "compliance_areas_select_all" ON public.compliance_areas;
CREATE POLICY "compliance_areas_select_all"
  ON public.compliance_areas FOR SELECT
  TO anon, authenticated
  USING (true);

ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "compliance_checks_select" ON public.compliance_checks;
DROP POLICY IF EXISTS "compliance_checks_select_all" ON public.compliance_checks;
CREATE POLICY "compliance_checks_select_all"
  ON public.compliance_checks FOR SELECT
  TO anon, authenticated
  USING (true);

-- 9. COMPLIANCE OBSERVATIONS
ALTER TABLE public.compliance_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "observations_select_admin" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_select_own" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_select_all" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_insert" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_update" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_delete" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_all_auth" ON public.compliance_observations;

CREATE POLICY "observations_select_all"
  ON public.compliance_observations FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "observations_all_auth"
  ON public.compliance_observations FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 10. COMPLIANCE REPORTS
ALTER TABLE public.compliance_reports ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "reports_select_admin" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_select_standard" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_select_all" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_insert_policy" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_super_admin" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_reporter_review" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_delete_policy" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_all_auth" ON public.compliance_reports;

CREATE POLICY "reports_select_all"
  ON public.compliance_reports FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY "reports_all_auth"
  ON public.compliance_reports FOR ALL
  TO authenticated
  USING (true)
  WITH CHECK (true);

-- 11. REPORT AUDIT LOG & REPORTING PERIODS
ALTER TABLE public.report_audit_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "report_audit_log_select" ON public.report_audit_log;
DROP POLICY IF EXISTS "report_audit_log_select_all" ON public.report_audit_log;
CREATE POLICY "report_audit_log_select_all"
  ON public.report_audit_log FOR SELECT
  TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS "report_audit_log_insert" ON public.report_audit_log;
CREATE POLICY "report_audit_log_insert"
  ON public.report_audit_log FOR INSERT
  TO anon, authenticated
  WITH CHECK (true);

ALTER TABLE public.reporting_periods ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "reporting_periods_select" ON public.reporting_periods;
CREATE POLICY "reporting_periods_select"
  ON public.reporting_periods FOR SELECT
  TO anon, authenticated
  USING (true);

-- 12. AUTO-CONFIRM USERS & DISABLE EMAIL VERIFICATION REQUIREMENT
-- Automatically sets email_confirmed_at so users can log in immediately upon creation
UPDATE auth.users
SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
WHERE email_confirmed_at IS NULL;

CREATE OR REPLACE FUNCTION public.handle_auto_confirm_new_user()
RETURNS trigger AS $$
BEGIN
  IF NEW.email_confirmed_at IS NULL THEN
    NEW.email_confirmed_at := NOW();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created_auto_confirm ON auth.users;
CREATE TRIGGER on_auth_user_created_auto_confirm
BEFORE INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_auto_confirm_new_user();

CREATE OR REPLACE FUNCTION public.confirm_user_email(target_email text)
RETURNS jsonb AS $$
DECLARE
  updated_count int;
BEGIN
  IF target_email IS NULL OR TRIM(target_email) = '' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Email is required');
  END IF;

  UPDATE auth.users
  SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
  WHERE LOWER(email) = LOWER(TRIM(target_email));

  GET DIAGNOSTICS updated_count = ROW_COUNT;

  UPDATE public.user_roles r
  SET is_verified = TRUE,
      updated_at = NOW()
  FROM auth.users u
  WHERE r.user_id = u.id AND LOWER(TRIM(u.email)) = LOWER(TRIM(target_email));

  RETURN jsonb_build_object(
    'success', true,
    'confirmed', updated_count > 0,
    'email', target_email
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.confirm_user_email(text) TO anon, authenticated, service_role;

ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS email TEXT;

UPDATE public.user_roles r
SET email = u.email
FROM auth.users u
WHERE r.user_id = u.id AND (r.email IS NULL OR r.email = '');

UPDATE public.user_roles
SET is_verified = TRUE
WHERE is_verified IS NOT TRUE;

-- Done! Your tables will now load live data without 403 Forbidden errors, and all users can log in instantly without email verification.
