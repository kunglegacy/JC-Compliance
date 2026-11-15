-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE — DISABLE EMAIL VERIFICATION & AUTO-CONFIRM USERS
-- Run this in your Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- =========================================================================

-- =========================================================================
-- STEP 1 (RECOMMENDED IN SUPABASE DASHBOARD):
-- 1. Go to your Supabase Project Dashboard (https://supabase.com/dashboard)
-- 2. Click on "Authentication" in the left sidebar
-- 3. Click on "Providers" -> then click "Email"
-- 4. Toggle OFF "Confirm email" (or "Enable email confirmations")
-- 5. Click "Save" at the bottom.
-- That ensures Supabase Auth will never require users to click an email link.
-- =========================================================================

-- =========================================================================
-- STEP 2 (DATABASE-LEVEL AUTOMATIC CONFIRMATION):
-- This SQL guarantees that every newly created user in auth.users is
-- automatically confirmed immediately upon creation, and fixes any existing
-- accounts that were awaiting email confirmation.
-- =========================================================================

-- 1. Retroactively confirm all existing accounts in auth.users
UPDATE auth.users
SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
WHERE email_confirmed_at IS NULL;

-- 2. Create an automatic trigger on auth.users to auto-confirm ANY future user
CREATE OR REPLACE FUNCTION public.handle_auto_confirm_new_user()
RETURNS trigger AS $$
BEGIN
  -- Automatically assign current timestamp so email verification is never needed
  IF NEW.email_confirmed_at IS NULL THEN
    NEW.email_confirmed_at := NOW();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Re-create the BEFORE INSERT trigger on auth.users
DROP TRIGGER IF EXISTS on_auth_user_created_auto_confirm ON auth.users;
CREATE TRIGGER on_auth_user_created_auto_confirm
BEFORE INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_auto_confirm_new_user();

-- 3. Provide an RPC function allowing the application to auto-confirm any user on demand
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

  -- Also update user_roles is_verified flag
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

-- Grant execution permission to authenticated and anon roles
GRANT EXECUTE ON FUNCTION public.confirm_user_email(text) TO anon, authenticated, service_role;

-- 4. Ensure public.user_roles has email column for easy audit and listing
ALTER TABLE public.user_roles ADD COLUMN IF NOT EXISTS email TEXT;

-- Backfill email into user_roles from auth.users
UPDATE public.user_roles r
SET email = u.email
FROM auth.users u
WHERE r.user_id = u.id AND (r.email IS NULL OR r.email = '');

-- 5. Mark all user_roles as verified
UPDATE public.user_roles
SET is_verified = TRUE
WHERE is_verified IS NOT TRUE;

-- Verify results
SELECT id, email, email_confirmed_at, created_at 
FROM auth.users 
ORDER BY created_at DESC 
LIMIT 10;
