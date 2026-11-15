-- =========================================================================
-- JC-Best COMPLIANCE HUB — STAGE 4: SUPABASE RLS & DATABASE HARDENING
-- Authoritative Security Boundaries & Non-Bypassable Database Authorization
-- Target: PostgreSQL 15+ / Supabase
--
-- Security Invariants:
-- 1. Authoritative Identity: auth.uid() is the single source of truth for identity.
-- 2. Non-Spoofable Reports: Trigger trg_enforce_report_ownership forces reported_by/reporter_id = auth.uid()
--    and derives reporting officer display name from the Directorate profile (user_roles).
-- 3. Strict Data Isolation: Standard users can SELECT only where reported_by = auth.uid() or reporter_id = auth.uid().
-- 4. Staff Registry Protection: Only Super Admins can INSERT/UPDATE/DELETE staff records. Admin is read-only.
-- 5. Permanent Owner Protection: ifeanyiof360@gmail.com cannot be deleted, demoted, or modified by other Super Admins.
-- 6. Role Escalation Protection: Standard users and Admins cannot modify or grant roles.
-- =========================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Ensure Columns Exist for Authoritative Tracking
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporter_id UUID REFERENCES auth.users(id);
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS reporting_officer TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_owner BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_founder BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT TRUE;

-- 2. Authoritative Security Helper Functions (SECURITY DEFINER to avoid recursion)
CREATE OR REPLACE FUNCTION public.get_auth_email(uid UUID)
RETURNS TEXT AS $$
  SELECT LOWER(TRIM(email)) FROM auth.users WHERE id = uid LIMIT 1;
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_owner(uid UUID) 
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles r
    LEFT JOIN auth.users u ON u.id = r.user_id
    WHERE r.user_id = uid 
      AND (r.is_owner = TRUE OR r.is_founder = TRUE OR LOWER(TRIM(u.email)) = 'ifeanyiof360@gmail.com')
  );
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_super_admin(uid UUID) 
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND (role = 'super_admin' OR is_owner = TRUE OR is_founder = TRUE)
  ) OR public.is_owner(uid);
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

CREATE OR REPLACE FUNCTION public.is_admin_or_above(uid UUID) 
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles 
    WHERE user_id = uid AND (role IN ('super_admin', 'admin') OR is_owner = TRUE OR is_founder = TRUE)
  ) OR public.is_owner(uid);
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

-- 3. Database Trigger: Owner Inviolability (Blocks Demotion, Role Alteration, Deletion of Owner)
CREATE OR REPLACE FUNCTION public.enforce_owner_protection()
RETURNS TRIGGER AS $$
DECLARE
  target_email TEXT;
  caller_is_owner BOOLEAN;
BEGIN
  -- Get email of target user
  SELECT LOWER(TRIM(email)) INTO target_email FROM auth.users WHERE id = OLD.user_id;

  -- Check if target user is the protected Owner
  IF OLD.is_owner = TRUE OR OLD.is_founder = TRUE OR target_email = 'ifeanyiof360@gmail.com' THEN
    -- Deletion is strictly forbidden
    IF TG_OP = 'DELETE' THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account (ifeanyiof360@gmail.com) is permanently protected and cannot be deleted.';
    END IF;

    -- Demoting Owner or removing Super Admin / Owner flag is strictly forbidden
    IF NEW.role != 'super_admin' OR NEW.is_owner != TRUE THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account must permanently retain Super Admin permissions and Owner protection.';
    END IF;

    -- Non-owners cannot modify the Owner account
    caller_is_owner := public.is_owner(auth.uid());
    IF auth.uid() IS NOT NULL AND auth.uid() != OLD.user_id AND NOT caller_is_owner THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: Only the Owner can update their own account profile.';
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_owner_roles ON public.user_roles;
CREATE TRIGGER trg_protect_owner_roles
  BEFORE UPDATE OR DELETE ON public.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_owner_protection();

-- 4. Database Trigger: User Role Insertion & Escalation Hardening
CREATE OR REPLACE FUNCTION public.enforce_role_creation_security()
RETURNS TRIGGER AS $$
DECLARE
  target_email TEXT;
  caller_is_sa BOOLEAN;
BEGIN
  SELECT LOWER(TRIM(email)) INTO target_email FROM auth.users WHERE id = NEW.user_id;

  -- If this is the owner email, enforce Super Admin and Owner
  IF target_email = 'ifeanyiof360@gmail.com' THEN
    NEW.role := 'super_admin';
    NEW.is_owner := TRUE;
    NEW.is_founder := TRUE;
    NEW.is_verified := TRUE;
    RETURN NEW;
  END IF;

  caller_is_sa := public.is_super_admin(auth.uid());

  -- If non-Super Admin is performing signup or insert, force STANDARD role
  IF NOT caller_is_sa THEN
    NEW.role := 'standard';
    NEW.is_owner := FALSE;
    NEW.is_founder := FALSE;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_role_creation_security ON public.user_roles;
CREATE TRIGGER trg_role_creation_security
  BEFORE INSERT ON public.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_role_creation_security();

-- 5. Database Trigger: Authoritative Report Ownership & Reporting Officer Derivation
CREATE OR REPLACE FUNCTION public.enforce_report_ownership()
RETURNS TRIGGER AS $$
DECLARE
  officer_name TEXT;
  officer_role TEXT;
BEGIN
  -- 1. Authoritative UID derivation: never trust browser-supplied reporter ID
  IF auth.uid() IS NOT NULL THEN
    NEW.reported_by := auth.uid();
    NEW.reporter_id := auth.uid();

    -- Look up authenticated reporting officer from Directorate Registry (user_roles)
    SELECT display_name, role INTO officer_name, officer_role
    FROM public.user_roles
    WHERE user_id = auth.uid()
    LIMIT 1;

    IF officer_name IS NOT NULL AND officer_name != '' THEN
      NEW.reporter_name := officer_name;
      NEW.reporting_officer := officer_name;
    END IF;

    IF officer_role IS NOT NULL THEN
      NEW.reporter_role := (CASE 
        WHEN officer_role = 'super_admin' THEN 'Super Admin'
        WHEN officer_role = 'admin' THEN 'Vice Principal'
        ELSE 'Compliance Inspector'
      END);
    END IF;
  END IF;

  -- Default review workflow: new exceptions start UNSEEN
  IF NEW.review_status IS NULL OR NEW.review_status = '' THEN
    NEW.review_status := 'UNSEEN';
  END IF;
  IF NEW.is_seen IS NULL THEN
    NEW.is_seen := FALSE;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_enforce_report_ownership ON public.compliance_reports;
CREATE TRIGGER trg_enforce_report_ownership
  BEFORE INSERT ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_report_ownership();

-- 6. Comprehensive Row Level Security (RLS) Configuration

-- (A) USER ROLES (Directorate Registry)
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_roles_select" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_select_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_insert_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_update_policy" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_delete_policy" ON public.user_roles;
DROP POLICY IF EXISTS "Users read own role or admin reads all" ON public.user_roles;
DROP POLICY IF EXISTS "Super admins can manage roles" ON public.user_roles;
DROP POLICY IF EXISTS "Allow self-register role on signup" ON public.user_roles;

CREATE POLICY "user_roles_select_policy"
  ON public.user_roles FOR SELECT
  USING (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

CREATE POLICY "user_roles_insert_policy"
  ON public.user_roles FOR INSERT
  WITH CHECK (user_id = auth.uid() OR public.is_super_admin(auth.uid()));

CREATE POLICY "user_roles_update_policy"
  ON public.user_roles FOR UPDATE
  USING (public.is_super_admin(auth.uid()));

CREATE POLICY "user_roles_delete_policy"
  ON public.user_roles FOR DELETE
  USING (public.is_super_admin(auth.uid()));

-- (B) STAFF (Staff Registry - Monitored Personnel)
ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "All authenticated read staff" ON public.staff;
DROP POLICY IF EXISTS "Only super admin can modify staff" ON public.staff;
DROP POLICY IF EXISTS "staff_select_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_insert_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_update_policy" ON public.staff;
DROP POLICY IF EXISTS "staff_delete_policy" ON public.staff;

CREATE POLICY "staff_select_policy"
  ON public.staff FOR SELECT
  USING (auth.uid() IS NOT NULL);

CREATE POLICY "staff_insert_policy"
  ON public.staff FOR INSERT
  WITH CHECK (public.is_super_admin(auth.uid()));

CREATE POLICY "staff_update_policy"
  ON public.staff FOR UPDATE
  USING (public.is_super_admin(auth.uid()));

CREATE POLICY "staff_delete_policy"
  ON public.staff FOR DELETE
  USING (public.is_super_admin(auth.uid()));

-- (C) COMPLIANCE REPORTS
ALTER TABLE public.compliance_reports ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Admin+ sees all reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Standard sees own reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Standard user sees own reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Authenticated users can create reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Authenticated users can submit reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Super admin updates reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Super admins update reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Super admin deletes reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "Super admins delete reports" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_select_admin" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_select_standard" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_insert_policy" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_super_admin" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_reporter_review" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_delete_policy" ON public.compliance_reports;

-- SELECT: Admins/Super Admins see all; Standard users strictly see their own
CREATE POLICY "reports_select_admin"
  ON public.compliance_reports FOR SELECT
  USING (public.is_admin_or_above(auth.uid()));

CREATE POLICY "reports_select_standard"
  ON public.compliance_reports FOR SELECT
  USING (
    NOT public.is_admin_or_above(auth.uid()) AND
    (reported_by = auth.uid() OR reporter_id = auth.uid())
  );

-- INSERT: Any authenticated user can insert reports (trigger guarantees reported_by = auth.uid())
CREATE POLICY "reports_insert_policy"
  ON public.compliance_reports FOR INSERT
  WITH CHECK (
    auth.uid() IS NOT NULL AND
    (reported_by = auth.uid() OR reporter_id = auth.uid() OR reported_by IS NULL)
  );

-- UPDATE: Super Admins can update any report; Reporters can update review state on own reports
CREATE POLICY "reports_update_super_admin"
  ON public.compliance_reports FOR UPDATE
  USING (public.is_super_admin(auth.uid()));

CREATE POLICY "reports_update_reporter_review"
  ON public.compliance_reports FOR UPDATE
  USING (
    auth.uid() IS NOT NULL AND
    (reported_by = auth.uid() OR reporter_id = auth.uid())
  );

-- DELETE: ONLY Super Admin can delete reports (Admin and Standard cannot)
CREATE POLICY "reports_delete_policy"
  ON public.compliance_reports FOR DELETE
  USING (public.is_super_admin(auth.uid()));

-- (D) COMPLIANCE AREAS & CHECKS
ALTER TABLE public.compliance_areas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance areas" ON public.compliance_areas;
DROP POLICY IF EXISTS "Admin manage compliance areas" ON public.compliance_areas;
DROP POLICY IF EXISTS "compliance_areas_select" ON public.compliance_areas;
DROP POLICY IF EXISTS "compliance_areas_all" ON public.compliance_areas;
CREATE POLICY "compliance_areas_select" ON public.compliance_areas FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "compliance_areas_all" ON public.compliance_areas FOR ALL USING (public.is_super_admin(auth.uid()));

ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance checks" ON public.compliance_checks;
DROP POLICY IF EXISTS "Admin manage compliance checks" ON public.compliance_checks;
DROP POLICY IF EXISTS "compliance_checks_select" ON public.compliance_checks;
DROP POLICY IF EXISTS "compliance_checks_all" ON public.compliance_checks;
CREATE POLICY "compliance_checks_select" ON public.compliance_checks FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "compliance_checks_all" ON public.compliance_checks FOR ALL USING (public.is_super_admin(auth.uid()));

-- (E) COMPLIANCE OBSERVATIONS
ALTER TABLE public.compliance_observations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Authenticated read compliance observations" ON public.compliance_observations;
DROP POLICY IF EXISTS "Authenticated insert compliance observations" ON public.compliance_observations;
DROP POLICY IF EXISTS "Authenticated update compliance observations" ON public.compliance_observations;
DROP POLICY IF EXISTS "Admin delete compliance observations" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_select_admin" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_select_own" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_insert" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_update" ON public.compliance_observations;
DROP POLICY IF EXISTS "observations_delete" ON public.compliance_observations;

CREATE POLICY "observations_select_admin" ON public.compliance_observations FOR SELECT USING (public.is_admin_or_above(auth.uid()));
CREATE POLICY "observations_select_own" ON public.compliance_observations FOR SELECT USING (reported_by = auth.uid());
CREATE POLICY "observations_insert" ON public.compliance_observations FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
CREATE POLICY "observations_update" ON public.compliance_observations FOR UPDATE USING (reported_by = auth.uid() OR public.is_super_admin(auth.uid()));
CREATE POLICY "observations_delete" ON public.compliance_observations FOR DELETE USING (public.is_super_admin(auth.uid()));

-- (F) SYSTEM SETTINGS
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "All read settings" ON public.system_settings;
DROP POLICY IF EXISTS "Admin+ manage settings" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_select" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_manage" ON public.system_settings;
CREATE POLICY "system_settings_select" ON public.system_settings FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "system_settings_manage" ON public.system_settings FOR ALL USING (public.is_super_admin(auth.uid()));

-- (G) CLASSES & SUBJECTS
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Read classes" ON public.classes;
DROP POLICY IF EXISTS "Admin+ manage classes" ON public.classes;
DROP POLICY IF EXISTS "classes_select" ON public.classes;
DROP POLICY IF EXISTS "classes_manage" ON public.classes;
CREATE POLICY "classes_select" ON public.classes FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "classes_manage" ON public.classes FOR ALL USING (public.is_super_admin(auth.uid()));

ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Read subjects" ON public.subjects;
DROP POLICY IF EXISTS "Admin+ manage subjects" ON public.subjects;
DROP POLICY IF EXISTS "subjects_select" ON public.subjects;
DROP POLICY IF EXISTS "subjects_manage" ON public.subjects;
CREATE POLICY "subjects_select" ON public.subjects FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "subjects_manage" ON public.subjects FOR ALL USING (public.is_super_admin(auth.uid()));

-- (H) REPORT AUDIT LOG
ALTER TABLE public.report_audit_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Admin+ read audit" ON public.report_audit_log;
DROP POLICY IF EXISTS "report_audit_log_select" ON public.report_audit_log;
DROP POLICY IF EXISTS "report_audit_log_insert" ON public.report_audit_log;
CREATE POLICY "report_audit_log_select" ON public.report_audit_log FOR SELECT USING (public.is_admin_or_above(auth.uid()));
CREATE POLICY "report_audit_log_insert" ON public.report_audit_log FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
