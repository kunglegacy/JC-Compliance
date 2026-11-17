-- =========================================================================
-- JC-BEST COMPLIANCE HUB — COMPLETE DATABASE, USER SYNC, STATUS, EDIT &
-- NOTIFICATION MIGRATION
-- Safe, idempotent migration script (PostgreSQL 15+ / Supabase)
-- Run this script once in your Supabase SQL Editor.
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. compliance_reports: Required columns, Edit tracking & Status cleanup
-- -------------------------------------------------------------------------
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'OPEN';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS review_status TEXT DEFAULT 'UNSEEN';
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS is_seen BOOLEAN DEFAULT FALSE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS seen_by TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolution_notes TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_notes JSONB DEFAULT '[]'::jsonb;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_urls TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS evidence_types TEXT[];
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS pending_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS pending_by TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolved_by TEXT;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS is_edited BOOLEAN DEFAULT FALSE;
ALTER TABLE public.compliance_reports ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;

-- Ensure NULL JSON arrays default cleanly
UPDATE public.compliance_reports
SET evidence_notes = '[]'::jsonb
WHERE evidence_notes IS NULL;

UPDATE public.compliance_reports
SET is_edited = FALSE
WHERE is_edited IS NULL;

-- Drop any legacy status / review_status CHECK constraints BEFORE normalizing rows
DO $$
DECLARE
  con_rec RECORD;
BEGIN
  FOR con_rec IN
    SELECT conname
    FROM pg_constraint
    WHERE conrelid = 'public.compliance_reports'::regclass
      AND contype = 'c'
      AND (
        pg_get_constraintdef(oid) ILIKE '%review_status%'
        OR conname ILIKE '%review_status%'
        OR pg_get_constraintdef(oid) ILIKE '%status%'
        OR conname ILIKE '%status%'
      )
  LOOP
    EXECUTE format('ALTER TABLE public.compliance_reports DROP CONSTRAINT IF EXISTS %I', con_rec.conname);
  END LOOP;
END $$;

-- Normalize statuses strictly to: OPEN, PENDING, ACTION_REQUIRED, REJECTED, ACCEPTED
UPDATE public.compliance_reports
SET status = CASE UPPER(TRIM(COALESCE(status, 'OPEN')))
  WHEN 'RESOLVED' THEN 'ACCEPTED'
  WHEN 'ACCEPTED' THEN 'ACCEPTED'
  WHEN 'IN_REVIEW' THEN 'PENDING'
  WHEN 'PENDING' THEN 'PENDING'
  WHEN 'ESCALATED' THEN 'ACTION_REQUIRED'
  WHEN 'ACTION_REQUIRED' THEN 'ACTION_REQUIRED'
  WHEN 'DISMISSED' THEN 'REJECTED'
  WHEN 'REJECTED' THEN 'REJECTED'
  WHEN 'OPEN' THEN 'OPEN'
  ELSE 'OPEN'
END;

-- Normalize seen & review_status flags consistently
UPDATE public.compliance_reports
SET is_seen = CASE
    WHEN UPPER(COALESCE(status, '')) IN ('PENDING', 'ACTION_REQUIRED', 'REJECTED', 'ACCEPTED')
      OR UPPER(COALESCE(review_status, '')) IN ('SEEN', 'PENDING')
      OR seen_at IS NOT NULL THEN TRUE
    ELSE FALSE
  END
WHERE is_seen IS NULL OR (UPPER(COALESCE(status, '')) IN ('PENDING', 'ACTION_REQUIRED', 'REJECTED', 'ACCEPTED') AND is_seen = FALSE);

UPDATE public.compliance_reports
SET review_status = CASE
  WHEN UPPER(COALESCE(status, '')) = 'PENDING' THEN 'PENDING'
  WHEN COALESCE(is_seen, FALSE) = TRUE THEN 'SEEN'
  ELSE 'UNSEEN'
END
WHERE review_status IS NULL
   OR TRIM(review_status) = ''
   OR UPPER(review_status) NOT IN ('UNSEEN', 'PENDING', 'SEEN');

-- Add strict 5-stage CHECK constraint + review_status constraint
ALTER TABLE public.compliance_reports
  ADD CONSTRAINT chk_compliance_reports_review_status
  CHECK (review_status IN ('UNSEEN', 'PENDING', 'SEEN', 'Open', 'Pending', 'Seen', 'Unseen'));

ALTER TABLE public.compliance_reports
  ADD CONSTRAINT chk_compliance_reports_resolution_status
  CHECK (UPPER(status) IN ('OPEN', 'PENDING', 'ACTION_REQUIRED', 'REJECTED', 'ACCEPTED'));

CREATE INDEX IF NOT EXISTS idx_reports_status_review
  ON public.compliance_reports (status, review_status, is_seen);

CREATE INDEX IF NOT EXISTS idx_reports_reported_by
  ON public.compliance_reports (reported_by, created_at DESC);

-- -------------------------------------------------------------------------
-- 2. Triggers: Ownership enforcement on INSERT + Auto-mark is_edited on UPDATE
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.enforce_report_ownership()
RETURNS TRIGGER AS $$
DECLARE
  officer_name TEXT;
  officer_role TEXT;
BEGIN
  IF auth.uid() IS NOT NULL THEN
    NEW.reported_by := auth.uid();
    NEW.reporter_id := auth.uid();

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

  NEW.review_status := COALESCE(NULLIF(TRIM(NEW.review_status), ''), 'UNSEEN');
  NEW.status := UPPER(COALESCE(NULLIF(TRIM(NEW.status), ''), 'OPEN'));
  IF NEW.status NOT IN ('OPEN', 'PENDING', 'ACTION_REQUIRED', 'REJECTED', 'ACCEPTED') THEN
    NEW.status := 'OPEN';
  END IF;

  IF NEW.is_seen IS NULL THEN
    NEW.is_seen := FALSE;
  END IF;
  IF NEW.is_seen = FALSE THEN
    NEW.seen_at := NULL;
    NEW.seen_by := NULL;
  END IF;
  NEW.is_edited := COALESCE(NEW.is_edited, FALSE);

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_enforce_report_ownership ON public.compliance_reports;
CREATE TRIGGER trg_enforce_report_ownership
  BEFORE INSERT ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_report_ownership();

CREATE OR REPLACE FUNCTION public.mark_report_edited_on_change()
RETURNS TRIGGER AS $$
BEGIN
  IF (
    COALESCE(OLD.compliance_check, '') IS DISTINCT FROM COALESCE(NEW.compliance_check, '') OR
    COALESCE(OLD.exception_text, '') IS DISTINCT FROM COALESCE(NEW.exception_text, '') OR
    COALESCE(OLD.exception, '') IS DISTINCT FROM COALESCE(NEW.exception, '') OR
    COALESCE(OLD.effect_text, '') IS DISTINCT FROM COALESCE(NEW.effect_text, '') OR
    COALESCE(OLD.effect, '') IS DISTINCT FROM COALESCE(NEW.effect, '') OR
    COALESCE(OLD.incident_date::TEXT, '') IS DISTINCT FROM COALESCE(NEW.incident_date::TEXT, '') OR
    COALESCE(OLD.division, '') IS DISTINCT FROM COALESCE(NEW.division, '') OR
    COALESCE(OLD.category, '') IS DISTINCT FROM COALESCE(NEW.category, '') OR
    COALESCE(OLD.staff_name, '') IS DISTINCT FROM COALESCE(NEW.staff_name, '') OR
    COALESCE(OLD.class_name, '') IS DISTINCT FROM COALESCE(NEW.class_name, '') OR
    COALESCE(OLD.subject, '') IS DISTINCT FROM COALESCE(NEW.subject, '') OR
    COALESCE(OLD.evidence_url, '') IS DISTINCT FROM COALESCE(NEW.evidence_url, '') OR
    COALESCE(OLD.evidence_urls, ARRAY[]::TEXT[]) IS DISTINCT FROM COALESCE(NEW.evidence_urls, ARRAY[]::TEXT[])
  ) THEN
    NEW.is_edited := TRUE;
    NEW.edited_at := NOW();
  END IF;
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_mark_report_edited ON public.compliance_reports;
CREATE TRIGGER trg_mark_report_edited
  BEFORE UPDATE ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.mark_report_edited_on_change();

-- -------------------------------------------------------------------------
-- 3. User Roles Schema, Admin Helper, Auth->user_roles Sync & RPCs
-- -------------------------------------------------------------------------
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'Active';
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS staff_number TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS display_name TEXT;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT TRUE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_owner BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS is_founder BOOLEAN DEFAULT FALSE;
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

UPDATE public.user_roles
SET status = CASE WHEN COALESCE(is_verified, TRUE) THEN 'Active' ELSE 'Inactive' END
WHERE status IS NULL OR TRIM(status) = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_roles_user_id_unique ON public.user_roles (user_id);

CREATE OR REPLACE FUNCTION public.is_admin_or_above(uid UUID)
RETURNS BOOLEAN AS $$
BEGIN
  IF uid IS NULL THEN
    RETURN FALSE;
  END IF;
  RETURN EXISTS (
    SELECT 1
    FROM public.user_roles ur
    WHERE ur.user_id = uid
      AND (
        LOWER(COALESCE(ur.role, 'standard')) IN ('admin', 'super_admin')
        OR COALESCE(ur.is_owner, FALSE) = TRUE
        OR COALESCE(ur.is_founder, FALSE) = TRUE
      )
      AND COALESCE(ur.is_verified, TRUE) = TRUE
  ) OR EXISTS (
    SELECT 1
    FROM auth.users u
    WHERE u.id = uid
      AND LOWER(COALESCE(u.email, '')) LIKE 'ifeanyiof%'
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public, auth;

CREATE OR REPLACE FUNCTION public.get_auth_email(uid UUID)
RETURNS TEXT AS $$
DECLARE
  found_email TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RETURN NULL;
  END IF;
  IF auth.uid() != uid AND NOT public.is_admin_or_above(auth.uid()) THEN
    RETURN NULL;
  END IF;
  SELECT u.email INTO found_email FROM auth.users u WHERE u.id = uid LIMIT 1;
  RETURN found_email;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public, auth;

DROP FUNCTION IF EXISTS public.get_user_id_by_email(TEXT);
CREATE OR REPLACE FUNCTION public.get_user_id_by_email(target_email TEXT)
RETURNS UUID AS $$
DECLARE
  found_uid UUID;
BEGIN
  IF NOT public.is_admin_or_above(auth.uid()) THEN
    RETURN NULL;
  END IF;
  SELECT u.id INTO found_uid
  FROM auth.users u
  WHERE LOWER(u.email) = LOWER(TRIM(target_email))
  LIMIT 1;
  RETURN found_uid;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE SET search_path = public, auth;

DROP FUNCTION IF EXISTS public.confirm_user_email(TEXT);
CREATE OR REPLACE FUNCTION public.confirm_user_email(target_email text)
RETURNS jsonb AS $$
DECLARE
  updated_count int;
BEGIN
  IF target_email IS NULL OR TRIM(target_email) = '' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Email is required');
  END IF;

  UPDATE auth.users
  SET email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
      updated_at = NOW()
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
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = auth, public;

DROP FUNCTION IF EXISTS public.get_directorate_users();
CREATE OR REPLACE FUNCTION public.get_directorate_users()
RETURNS TABLE (
  user_id UUID,
  email TEXT,
  display_name TEXT,
  staff_number TEXT,
  role TEXT,
  status TEXT,
  is_verified BOOLEAN,
  is_owner BOOLEAN,
  is_founder BOOLEAN,
  avatar_url TEXT,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
) AS $$
BEGIN
  IF NOT public.is_admin_or_above(auth.uid()) THEN
    RAISE EXCEPTION 'Only Directorate administrators can view all users.';
  END IF;

  RETURN QUERY
  SELECT
    u.id AS user_id,
    LOWER(COALESCE(NULLIF(ur.email, ''), u.email)) AS email,
    COALESCE(
      NULLIF(ur.display_name, ''),
      NULLIF(u.raw_user_meta_data->>'display_name', ''),
      SPLIT_PART(u.email, '@', 1)
    ) AS display_name,
    COALESCE(
      NULLIF(ur.staff_number, ''),
      NULLIF(u.raw_user_meta_data->>'staff_number', ''),
      ''
    ) AS staff_number,
    CASE
      WHEN LOWER(u.email) LIKE 'ifeanyiof%' THEN 'super_admin'
      ELSE LOWER(COALESCE(NULLIF(ur.role, ''), NULLIF(u.raw_user_meta_data->>'role', ''), 'standard'))
    END AS role,
    CASE
      WHEN COALESCE(ur.is_verified, TRUE) = FALSE THEN 'Inactive'
      ELSE COALESCE(NULLIF(ur.status, ''), 'Active')
    END AS status,
    COALESCE(ur.is_verified, TRUE) AS is_verified,
    (COALESCE(ur.is_owner, FALSE) OR LOWER(u.email) LIKE 'ifeanyiof%') AS is_owner,
    (COALESCE(ur.is_founder, FALSE) OR LOWER(u.email) LIKE 'ifeanyiof%') AS is_founder,
    COALESCE(NULLIF(ur.avatar_url, ''), NULLIF(u.raw_user_meta_data->>'avatar_url', ''), '') AS avatar_url,
    COALESCE(ur.created_at, u.created_at) AS created_at,
    COALESCE(ur.updated_at, u.updated_at) AS updated_at
  FROM auth.users u
  LEFT JOIN public.user_roles ur ON ur.user_id = u.id
  ORDER BY COALESCE(ur.created_at, u.created_at) DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

GRANT EXECUTE ON FUNCTION public.get_auth_email(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_user_id_by_email(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_user_email(text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_directorate_users() TO authenticated;

-- Automatically sync every newly registered auth.users account into public.user_roles
CREATE OR REPLACE FUNCTION public.handle_auth_user_to_roles()
RETURNS TRIGGER AS $$
DECLARE
  is_own BOOLEAN;
  resolved_role TEXT;
  resolved_name TEXT;
  resolved_staff TEXT;
BEGIN
  is_own := LOWER(COALESCE(NEW.email, '')) LIKE 'ifeanyiof%';
  resolved_role := CASE
    WHEN is_own THEN 'super_admin'
    ELSE LOWER(COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'standard'))
  END;
  resolved_name := COALESCE(
    NULLIF(NEW.raw_user_meta_data->>'display_name', ''),
    NULLIF(NEW.raw_user_meta_data->>'full_name', ''),
    SPLIT_PART(COALESCE(NEW.email, 'officer'), '@', 1)
  );
  resolved_staff := COALESCE(
    NULLIF(NEW.raw_user_meta_data->>'staff_number', ''),
    NEW.email
  );

  INSERT INTO public.user_roles (
    user_id, email, display_name, staff_number, role, status, is_verified, is_owner, is_founder, created_at, updated_at
  )
  VALUES (
    NEW.id, LOWER(NEW.email), resolved_name, resolved_staff, resolved_role, 'Active', TRUE, is_own, is_own, NOW(), NOW()
  )
  ON CONFLICT (user_id) DO UPDATE SET
    email = COALESCE(NULLIF(public.user_roles.email, ''), EXCLUDED.email),
    display_name = COALESCE(NULLIF(public.user_roles.display_name, ''), EXCLUDED.display_name),
    staff_number = COALESCE(NULLIF(public.user_roles.staff_number, ''), EXCLUDED.staff_number),
    updated_at = NOW();

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, auth;

DROP TRIGGER IF EXISTS on_auth_user_created_sync_roles ON auth.users;
CREATE TRIGGER on_auth_user_created_sync_roles
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_auth_user_to_roles();

-- One-time backfill for any users in auth.users missing from user_roles (or missing email)
INSERT INTO public.user_roles (
  user_id, email, display_name, staff_number, role, status, is_verified, is_owner, is_founder, created_at, updated_at
)
SELECT
  u.id,
  LOWER(u.email),
  COALESCE(NULLIF(u.raw_user_meta_data->>'display_name', ''), SPLIT_PART(u.email, '@', 1)),
  COALESCE(NULLIF(u.raw_user_meta_data->>'staff_number', ''), u.email),
  CASE
    WHEN LOWER(u.email) LIKE 'ifeanyiof%' THEN 'super_admin'
    ELSE LOWER(COALESCE(NULLIF(u.raw_user_meta_data->>'role', ''), 'standard'))
  END,
  'Active',
  TRUE,
  (LOWER(u.email) LIKE 'ifeanyiof%'),
  (LOWER(u.email) LIKE 'ifeanyiof%'),
  u.created_at,
  NOW()
FROM auth.users u
ON CONFLICT (user_id) DO UPDATE SET
  email = COALESCE(NULLIF(public.user_roles.email, ''), EXCLUDED.email),
  updated_at = NOW();

-- -------------------------------------------------------------------------
-- 4. RLS Policies for compliance_reports, user_roles, staff, settings, checks
-- -------------------------------------------------------------------------
DROP POLICY IF EXISTS "reports_update_super_admin" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_admin_or_above" ON public.compliance_reports;
DROP POLICY IF EXISTS "reports_update_reporter_review" ON public.compliance_reports;

CREATE POLICY "reports_update_admin_or_above"
  ON public.compliance_reports FOR UPDATE
  USING (public.is_admin_or_above(auth.uid()));

-- Reporters can edit ALL fields & evidence when status is Open, Pending, or Action Required
CREATE POLICY "reports_update_reporter_review"
  ON public.compliance_reports FOR UPDATE
  USING (
    auth.uid() IS NOT NULL
    AND (reported_by = auth.uid() OR reporter_id = auth.uid())
    AND UPPER(COALESCE(status, 'OPEN')) IN ('OPEN', 'PENDING', 'ACTION_REQUIRED')
  )
  WITH CHECK (
    auth.uid() IS NOT NULL
    AND (reported_by = auth.uid() OR reporter_id = auth.uid())
  );

ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_roles_admin_insert" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_admin_update" ON public.user_roles;
DROP POLICY IF EXISTS "user_roles_admin_select" ON public.user_roles;

CREATE POLICY "user_roles_admin_select"
  ON public.user_roles FOR SELECT
  USING (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

CREATE POLICY "user_roles_admin_insert"
  ON public.user_roles FOR INSERT
  WITH CHECK (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

CREATE POLICY "user_roles_admin_update"
  ON public.user_roles FOR UPDATE
  USING (user_id = auth.uid() OR public.is_admin_or_above(auth.uid()));

ALTER TABLE public.staff ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "staff_admin_insert" ON public.staff;
DROP POLICY IF EXISTS "staff_admin_update" ON public.staff;

CREATE POLICY "staff_admin_insert"
  ON public.staff FOR INSERT
  WITH CHECK (public.is_admin_or_above(auth.uid()));

CREATE POLICY "staff_admin_update"
  ON public.staff FOR UPDATE
  USING (public.is_admin_or_above(auth.uid()));

CREATE TABLE IF NOT EXISTS public.system_settings (
  key TEXT PRIMARY KEY,
  value TEXT
);
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "system_settings_select_all" ON public.system_settings;
DROP POLICY IF EXISTS "system_settings_admin_write" ON public.system_settings;

CREATE POLICY "system_settings_select_all"
  ON public.system_settings FOR SELECT
  USING (auth.uid() IS NOT NULL);

CREATE POLICY "system_settings_admin_write"
  ON public.system_settings FOR ALL
  USING (public.is_admin_or_above(auth.uid()))
  WITH CHECK (public.is_admin_or_above(auth.uid()));

ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "compliance_checks_admin_write" ON public.compliance_checks;
CREATE POLICY "compliance_checks_admin_write"
  ON public.compliance_checks FOR ALL
  USING (public.is_admin_or_above(auth.uid()))
  WITH CHECK (public.is_admin_or_above(auth.uid()));

-- -------------------------------------------------------------------------
-- 5. Rebuild authoritative report_ready_exceptions View
-- -------------------------------------------------------------------------
DROP VIEW IF EXISTS public.report_ready_exceptions CASCADE;
CREATE OR REPLACE VIEW public.report_ready_exceptions AS
SELECT
  e.id,
  COALESCE(e.date, e.incident_date, e.created_at::DATE) AS date,
  e.division,
  e.staff_id,
  s.staff_code,
  COALESCE(s.name, e.staff_name) AS staff_name,
  COALESCE(s.department, e.division) AS department,
  s.staff_group,
  s.role,
  s.school_section,
  COALESCE(s.assigned_location, e.class_name) AS assigned_location,
  e.check_id,
  COALESCE(c.name, e.compliance_check, e.category) AS check_name,
  COALESCE(c.group_name, c.area_name, 'General') AS check_group,
  e.class_id,
  COALESCE(cl.name, e.class_name) AS class_name,
  cl.section AS class_section,
  cl.arm AS class_arm,
  cl.level AS class_level,
  e.subject_id,
  COALESCE(sub.name, e.subject) AS subject_name,
  sub.discipline_group,
  COALESCE(e.exception_text, e.exception) AS exception_text,
  COALESCE(e.effect_text, e.effect) AS effect_text,
  COALESCE(e.severity, 'MEDIUM') AS severity,
  COALESCE(e.evidence_url, (CASE WHEN array_length(e.evidence_urls, 1) > 0 THEN e.evidence_urls[1] ELSE NULL END)) AS evidence_url,
  COALESCE(e.evidence_type, (CASE WHEN array_length(e.evidence_types, 1) > 0 THEN e.evidence_types[1] ELSE 'none' END)) AS evidence_type,
  e.evidence_urls,
  e.evidence_types,
  e.evidence_notes,
  e.resolution_notes,
  e.reported_by,
  e.reporter_id,
  e.reporter_name,
  e.reporting_officer,
  e.term,
  e.session,
  e.week_number,
  e.status,
  e.review_status,
  (COALESCE(e.is_seen, FALSE) OR UPPER(COALESCE(e.review_status, '')) IN ('SEEN', 'PENDING')) AS is_seen,
  e.seen_at,
  e.seen_by,
  e.pending_at,
  e.pending_by,
  e.resolved_by,
  e.resolved_at,
  COALESCE(e.is_edited, FALSE) AS is_edited,
  e.edited_at,
  e.target_type,
  e.created_at,
  e.updated_at
FROM public.compliance_reports e
LEFT JOIN public.staff s ON s.id = e.staff_id
LEFT JOIN public.compliance_checks c ON c.id = e.check_id
LEFT JOIN public.classes cl ON cl.id = e.class_id
LEFT JOIN public.subjects sub ON sub.id = e.subject_id;

GRANT SELECT ON public.report_ready_exceptions TO anon, authenticated;

-- -------------------------------------------------------------------------
-- 6. Notifications Table & Push-Trigger Automation
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recipient_user_id UUID,
  recipient_role TEXT DEFAULT 'user',
  type TEXT NOT NULL CHECK (type IN ('ACTION_REQUIRED', 'ACCEPTED', 'NEW_SUBMISSION', 'DRAFT_REMINDER', 'GENERAL')),
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  report_id UUID,
  report_number TEXT,
  actor_name TEXT,
  is_read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications (recipient_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_role ON public.notifications (recipient_role, created_at DESC);

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "notifications_select_own_or_admin" ON public.notifications;
DROP POLICY IF EXISTS "notifications_insert_auth" ON public.notifications;
DROP POLICY IF EXISTS "notifications_update_own_or_admin" ON public.notifications;

CREATE POLICY "notifications_select_own_or_admin"
  ON public.notifications FOR SELECT
  USING (
    auth.uid() IS NOT NULL
    AND (
      recipient_user_id = auth.uid()
      OR (recipient_role = 'admin' AND public.is_admin_or_above(auth.uid()))
    )
  );

CREATE POLICY "notifications_insert_auth"
  ON public.notifications FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

CREATE POLICY "notifications_update_own_or_admin"
  ON public.notifications FOR UPDATE
  USING (
    auth.uid() IS NOT NULL
    AND (
      recipient_user_id = auth.uid()
      OR (recipient_role = 'admin' AND public.is_admin_or_above(auth.uid()))
    )
  );

CREATE OR REPLACE FUNCTION public.notify_admins_on_new_report()
RETURNS TRIGGER AS $$
DECLARE
  reporter_label TEXT;
BEGIN
  reporter_label := COALESCE(
    NULLIF(TRIM(NEW.reporter_name), ''),
    NULLIF(TRIM(NEW.reporting_officer), ''),
    'A reporting officer'
  );

  INSERT INTO public.notifications (
    recipient_user_id, recipient_role, type, title, body, report_id, report_number, actor_name
  )
  VALUES (
    NULL,
    'admin',
    'NEW_SUBMISSION',
    'New Compliance Exception',
    reporter_label || ' has submitted a new exception. Please review.',
    NEW.id,
    NEW.report_number,
    reporter_label
  );

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_notify_admins_new_report ON public.compliance_reports;
CREATE TRIGGER trg_notify_admins_new_report
  AFTER INSERT ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.notify_admins_on_new_report();

CREATE OR REPLACE FUNCTION public.notify_user_on_status_change()
RETURNS TRIGGER AS $$
DECLARE
  target_uid UUID;
  next_st TEXT;
  prev_st TEXT;
  check_label TEXT;
BEGIN
  next_st := UPPER(COALESCE(NEW.status, 'OPEN'));
  prev_st := UPPER(COALESCE(OLD.status, 'OPEN'));
  target_uid := COALESCE(NEW.reported_by, NEW.reporter_id);
  check_label := COALESCE(NULLIF(TRIM(NEW.compliance_check), ''), 'Compliance Exception');

  IF target_uid IS NOT NULL AND next_st IS DISTINCT FROM prev_st THEN
    IF next_st = 'ACTION_REQUIRED' THEN
      INSERT INTO public.notifications (
        recipient_user_id, recipient_role, type, title, body, report_id, report_number, actor_name
      )
      VALUES (
        target_uid,
        'user',
        'ACTION_REQUIRED',
        'Action Required on Exception',
        'Your exception (' || check_label || ') requires your attention. Please check and resolve it.',
        NEW.id,
        NEW.report_number,
        COALESCE(NULLIF(TRIM(NEW.seen_by::TEXT), ''), 'Directorate Admin')
      );
    ELSIF next_st = 'ACCEPTED' THEN
      INSERT INTO public.notifications (
        recipient_user_id, recipient_role, type, title, body, report_id, report_number, actor_name
      )
      VALUES (
        target_uid,
        'user',
        'ACCEPTED',
        'Exception Accepted',
        'Your exception (' || check_label || ') has been accepted.',
        NEW.id,
        NEW.report_number,
        COALESCE(NULLIF(TRIM(NEW.resolved_by::TEXT), ''), NULLIF(TRIM(NEW.seen_by::TEXT), ''), 'Directorate Admin')
      );
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

DROP TRIGGER IF EXISTS trg_notify_user_status_change ON public.compliance_reports;
CREATE TRIGGER trg_notify_user_status_change
  AFTER UPDATE ON public.compliance_reports
  FOR EACH ROW
  EXECUTE FUNCTION public.notify_user_on_status_change();

-- Reload PostgREST schema cache immediately
NOTIFY pgrst, 'reload schema';
