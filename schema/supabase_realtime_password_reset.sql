-- =========================================================================
-- JC-BEST DIRECTORATE COMPLIANCE HUB
-- REAL-TIME SUPABASE AUTH PASSWORD RESET & REAL AUTH EMAIL SYNCHRONIZATION
-- Run this script in your Supabase Project: SQL Editor -> New Query -> Run
-- Project URL: https://fprxrgddvzjvfwvhmfee.supabase.co
-- Direct link: https://supabase.com/dashboard/project/fprxrgddvzjvfwvhmfee/sql/new
-- =========================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Ensure public.user_roles has email column for direct schema alignment
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS email TEXT;

-- Backfill email from auth.users into user_roles
UPDATE public.user_roles r
SET email = LOWER(TRIM(u.email)),
    updated_at = NOW()
FROM auth.users u
WHERE r.user_id = u.id AND (r.email IS NULL OR r.email = '');

-- 2. Real-Time Password Reset (Direct to 12345678 or specified password without email verification)
CREATE OR REPLACE FUNCTION public.admin_reset_user_password(
  target_user_id UUID,
  new_password TEXT DEFAULT '12345678'
)
RETURNS jsonb AS $$
DECLARE
  v_user auth.users%ROWTYPE;
BEGIN
  IF target_user_id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Target user ID is required');
  END IF;

  -- Locate target user in auth.users (either by auth.users.id or user_roles.id)
  SELECT * INTO v_user FROM auth.users WHERE id = target_user_id;
  IF NOT FOUND THEN
    SELECT u.* INTO v_user
    FROM auth.users u
    JOIN public.user_roles r ON r.user_id = u.id
    WHERE r.id = target_user_id;
  END IF;

  IF v_user.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Target user does not exist in Supabase Authentication');
  END IF;

  -- Authoritatively update password in auth.users using bcrypt pgcrypto
  UPDATE auth.users
  SET encrypted_password = crypt(COALESCE(new_password, '12345678'), gen_salt('bf')),
      email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
      updated_at = NOW(),
      raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb
  WHERE id = v_user.id;

  -- Ensure user_roles has updated_at and is_verified = TRUE
  UPDATE public.user_roles
  SET is_verified = TRUE,
      updated_at = NOW()
  WHERE user_id = v_user.id;

  RETURN jsonb_build_object(
    'success', true,
    'user_id', v_user.id,
    'email', v_user.email,
    'message', 'Password successfully reset to ' || COALESCE(new_password, '12345678') || ' in Supabase Authentication without email verification.'
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. Reset password by target email address directly
CREATE OR REPLACE FUNCTION public.admin_reset_user_password_by_email(
  target_email TEXT,
  new_password TEXT DEFAULT '12345678'
)
RETURNS jsonb AS $$
DECLARE
  v_user auth.users%ROWTYPE;
BEGIN
  IF target_email IS NULL OR TRIM(target_email) = '' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Target email is required');
  END IF;

  SELECT * INTO v_user FROM auth.users WHERE LOWER(TRIM(email)) = LOWER(TRIM(target_email));
  IF v_user.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Email ' || target_email || ' does not exist in Supabase Authentication');
  END IF;

  UPDATE auth.users
  SET encrypted_password = crypt(COALESCE(new_password, '12345678'), gen_salt('bf')),
      email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
      updated_at = NOW(),
      raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"]}'::jsonb
  WHERE id = v_user.id;

  UPDATE public.user_roles
  SET is_verified = TRUE,
      email = v_user.email,
      updated_at = NOW()
  WHERE user_id = v_user.id;

  RETURN jsonb_build_object(
    'success', true,
    'user_id', v_user.id,
    'email', v_user.email,
    'message', 'Password successfully reset to ' || COALESCE(new_password, '12345678') || ' in Supabase Authentication without email verification.'
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Direct Update of User Email in Supabase Auth & Directorate Registry
CREATE OR REPLACE FUNCTION public.admin_update_user_email(
  target_user_id UUID,
  new_email TEXT
)
RETURNS jsonb AS $$
DECLARE
  v_clean_email TEXT := LOWER(TRIM(new_email));
  v_user auth.users%ROWTYPE;
BEGIN
  IF v_clean_email IS NULL OR v_clean_email = '' OR v_clean_email NOT LIKE '%@%.%' THEN
    RETURN jsonb_build_object('success', false, 'error', 'Invalid email address format');
  END IF;

  SELECT * INTO v_user FROM auth.users WHERE id = target_user_id;
  IF NOT FOUND THEN
    SELECT u.* INTO v_user
    FROM auth.users u
    JOIN public.user_roles r ON r.user_id = u.id
    WHERE r.id = target_user_id;
  END IF;

  IF v_user.id IS NULL THEN
    RETURN jsonb_build_object('success', false, 'error', 'Target user not found in Supabase Authentication');
  END IF;

  -- Update auth.users email and keep email_confirmed_at
  UPDATE auth.users
  SET email = v_clean_email,
      email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
      updated_at = NOW()
  WHERE id = v_user.id;

  -- Update public.user_roles
  UPDATE public.user_roles
  SET email = v_clean_email,
      staff_number = v_clean_email,
      updated_at = NOW()
  WHERE user_id = v_user.id;

  RETURN jsonb_build_object(
    'success', true,
    'user_id', v_user.id,
    'new_email', v_clean_email,
    'message', 'Email address successfully updated in Supabase Authentication.'
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Unified RPC to return all Directorate Users with authoritative auth emails
CREATE OR REPLACE FUNCTION public.get_directorate_users()
RETURNS TABLE (
  id UUID,
  user_id UUID,
  email TEXT,
  role TEXT,
  display_name TEXT,
  staff_number TEXT,
  is_verified BOOLEAN,
  is_founder BOOLEAN,
  is_owner BOOLEAN,
  assigned_by UUID,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    COALESCE(r.id, u.id) AS id,
    u.id AS user_id,
    u.email::TEXT AS email,
    COALESCE(r.role, 'standard')::TEXT AS role,
    COALESCE(r.display_name, u.raw_user_meta_data->>'display_name', split_part(u.email, '@', 1))::TEXT AS display_name,
    r.staff_number::TEXT,
    COALESCE(r.is_verified, (u.email_confirmed_at IS NOT NULL)) AS is_verified,
    COALESCE(r.is_founder, (u.email = 'ifeanyiof360@gmail.com' OR u.email = 'ifeanyiof777@gmail.com')) AS is_founder,
    COALESCE(r.is_owner, (u.email = 'ifeanyiof360@gmail.com' OR u.email = 'ifeanyiof777@gmail.com')) AS is_owner,
    r.assigned_by,
    COALESCE(r.created_at, u.created_at) AS created_at,
    COALESCE(r.updated_at, u.updated_at) AS updated_at
  FROM auth.users u
  LEFT JOIN public.user_roles r ON r.user_id = u.id
  ORDER BY u.created_at ASC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 6. Helper to retrieve auth email for a given user id (SECURITY DEFINER)
CREATE OR REPLACE FUNCTION public.get_auth_email(uid UUID)
RETURNS TEXT AS $$
  SELECT LOWER(TRIM(email)) FROM auth.users WHERE id = uid LIMIT 1;
$$ LANGUAGE SQL SECURITY DEFINER STABLE;

-- 7. Grant execution permissions
GRANT EXECUTE ON FUNCTION public.admin_reset_user_password(UUID, TEXT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_reset_user_password_by_email(TEXT, TEXT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.admin_update_user_email(UUID, TEXT) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_directorate_users() TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_auth_email(UUID) TO anon, authenticated, service_role;
