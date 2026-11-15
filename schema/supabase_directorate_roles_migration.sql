-- =========================================================================
-- JC-Best COMPLIANCE HUB — STAGE 3: DIRECTORATE REGISTRY & ROLE SYSTEM
-- Secure Distinction between STAFF REGISTRY and DIRECTORATE REGISTRY
-- Target: Supabase (PostgreSQL 15+)
--
-- Security Guarantees:
-- 1. Strict distinction: Staff Registry (monitored staff) vs Directorate Registry (auth users).
-- 2. Standardized Roles: SUPER ADMIN, ADMIN, STANDARD.
-- 3. Permanent Owner Protection: ifeanyiof360@gmail.com is SUPER ADMIN + OWNER.
-- 4. Server-enforced RLS & DB Triggers: Owner cannot be deleted, demoted, or altered.
-- 5. Data Isolation: STANDARD users are strictly restricted to their own reports.
-- =========================================================================

-- 1. Ensure user_roles table has is_owner column and standardized role constraints
CREATE TABLE IF NOT EXISTS public.user_roles (
  id            UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role          TEXT NOT NULL DEFAULT 'standard',
  display_name  TEXT NOT NULL,
  staff_number  TEXT,
  is_verified   BOOLEAN DEFAULT TRUE,
  is_founder    BOOLEAN DEFAULT FALSE,
  is_owner      BOOLEAN DEFAULT FALSE,
  assigned_by   UUID REFERENCES auth.users(id),
  verified_by   UUID REFERENCES auth.users(id),
  verified_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id)
);

ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_owner BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_founder BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT TRUE;

-- Update role constraint to allow standardized roles: super_admin, admin, standard, directorate (legacy alias)
ALTER TABLE public.user_roles DROP CONSTRAINT IF EXISTS user_roles_role_check;
ALTER TABLE public.user_roles ADD CONSTRAINT user_roles_role_check 
  CHECK (role IN ('super_admin', 'admin', 'standard', 'directorate'));

-- Migrate any legacy 'directorate' role to 'standard'
UPDATE public.user_roles
SET role = 'standard'
WHERE role = 'directorate';

-- 2. Mark ifeanyiof360@gmail.com as permanent OWNER and SUPER ADMIN
UPDATE public.user_roles r
SET role = 'super_admin',
    is_owner = TRUE,
    is_founder = TRUE,
    is_verified = TRUE,
    updated_at = NOW()
FROM auth.users u
WHERE r.user_id = u.id AND LOWER(TRIM(u.email)) = 'ifeanyiof360@gmail.com';

-- 3. Security Helper Functions
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

-- 4. Database Trigger: Protect Website Owner from Modification, Demotion or Deletion
CREATE OR REPLACE FUNCTION public.enforce_owner_protection()
RETURNS TRIGGER AS $$
DECLARE
  target_email TEXT;
  caller_is_owner BOOLEAN;
BEGIN
  -- Get email of the target user
  SELECT LOWER(TRIM(email)) INTO target_email FROM auth.users WHERE id = OLD.user_id;

  -- Check if target user is the protected Owner
  IF OLD.is_owner = TRUE OR OLD.is_founder = TRUE OR target_email = 'ifeanyiof360@gmail.com' THEN
    
    -- Deletion of Owner is strictly forbidden
    IF TG_OP = 'DELETE' THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account (ifeanyiof360@gmail.com) is permanently protected and cannot be deleted.';
    END IF;

    -- Non-owners cannot modify the Owner account
    caller_is_owner := public.is_owner(auth.uid());
    IF auth.uid() IS NOT NULL AND auth.uid() != OLD.user_id AND NOT caller_is_owner THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: Only the Owner can update their own account profile.';
    END IF;

    -- Demoting Owner or removing Super Admin / Owner flag is strictly forbidden
    IF NEW.role != 'super_admin' OR NEW.is_owner != TRUE THEN
      RAISE EXCEPTION 'CRITICAL SECURITY: The Owner account must permanently retain Super Admin permissions and Owner protection.';
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

-- 5. Row Level Security Policies for Directorate Registry (user_roles)
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users read own role or admin reads all" ON public.user_roles;
CREATE POLICY "Users read own role or admin reads all" 
  ON public.user_roles FOR SELECT 
  USING (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "Super admins can manage roles" ON public.user_roles;
CREATE POLICY "Super admins can manage roles" 
  ON public.user_roles FOR ALL 
  USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "Allow self-register role on signup" ON public.user_roles;
CREATE POLICY "Allow self-register role on signup" 
  ON public.user_roles FOR INSERT 
  WITH CHECK (user_id = auth.uid());

-- 6. Row Level Security Policies for Staff Registry (staff)
-- Monitored staff records are managed exclusively by Super Admins
ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "All authenticated read staff" ON public.staff;
CREATE POLICY "All authenticated read staff" 
  ON public.staff FOR SELECT 
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Only super admin can modify staff" ON public.staff;
CREATE POLICY "Only super admin can modify staff" 
  ON public.staff FOR ALL 
  USING (public.is_super_admin(auth.uid()));

-- 7. Row Level Security Policies for Compliance Reports (compliance_reports)
-- Super Admin / Admin: can see all reports
-- Standard: can ONLY see their own submitted reports
ALTER TABLE public.compliance_reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Admin+ sees all reports" ON public.compliance_reports;
CREATE POLICY "Admin+ sees all reports" 
  ON public.compliance_reports FOR SELECT 
  USING (public.is_admin_or_above(auth.uid()));

DROP POLICY IF EXISTS "Standard user sees own reports" ON public.compliance_reports;
CREATE POLICY "Standard user sees own reports" 
  ON public.compliance_reports FOR SELECT 
  USING (reported_by = auth.uid() OR reporter_id = auth.uid());

DROP POLICY IF EXISTS "Authenticated users can submit reports" ON public.compliance_reports;
CREATE POLICY "Authenticated users can submit reports" 
  ON public.compliance_reports FOR INSERT 
  WITH CHECK (
    auth.uid() IS NOT NULL AND 
    (reported_by = auth.uid() OR reporter_id = auth.uid() OR reported_by IS NULL)
  );

DROP POLICY IF EXISTS "Super admins update reports" ON public.compliance_reports;
CREATE POLICY "Super admins update reports" 
  ON public.compliance_reports FOR UPDATE 
  USING (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "Super admins delete reports" ON public.compliance_reports;
CREATE POLICY "Super admins delete reports" 
  ON public.compliance_reports FOR DELETE 
  USING (public.is_super_admin(auth.uid()));
