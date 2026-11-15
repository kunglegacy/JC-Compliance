-- =========================================================================
-- JC-BEST SCHOOLS COMPLIANCE MANAGEMENT SYSTEM
-- MIGRATION: DYNAMIC ACADEMIC WEEK CADENCE & END-OF-TERM STATE
-- =========================================================================

-- 1. Ensure system_settings table exists and has proper permissions
CREATE TABLE IF NOT EXISTS public.system_settings (
  key         TEXT PRIMARY KEY,
  value       TEXT NOT NULL,
  updated_by  UUID REFERENCES auth.users(id),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Seed initial dynamic academic week settings if missing
-- Calculates Monday of the current week as the initial default anchor
INSERT INTO public.system_settings (key, value)
VALUES
  ('current_term', 'First Term'),
  ('current_session', '2026/2027'),
  ('current_week', '1'),
  ('manual_week_override', 'none'),
  ('max_weeks_per_term', '12'),
  ('school_name', 'JCBest Schools International')
ON CONFLICT (key) DO NOTHING;

-- Seed term_start_date dynamically to current Monday if missing
DO $$
DECLARE
  v_today DATE := CURRENT_DATE;
  -- In PostgreSQL EXTRACT(DOW FROM d): 0 = Sunday, 1 = Monday, ..., 6 = Saturday
  v_dow INT := EXTRACT(DOW FROM v_today);
  v_offset INT := CASE WHEN v_dow = 0 THEN -6 ELSE 1 - v_dow END;
  v_monday DATE := v_today + v_offset;
BEGIN
  INSERT INTO public.system_settings (key, value)
  VALUES ('term_start_date', v_monday::TEXT)
  ON CONFLICT (key) DO NOTHING;
END $$;

-- 3. SQL helper function to calculate the active academic week dynamically
CREATE OR REPLACE FUNCTION public.calculate_active_academic_week()
RETURNS TABLE (
  active_week TEXT,
  week_number INT,
  is_end_of_term BOOLEAN,
  term_start_date DATE,
  current_monday DATE,
  next_transition DATE
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_term_start TEXT;
  v_override TEXT;
  v_start_date DATE;
  v_today DATE := CURRENT_DATE;
  v_today_dow INT := EXTRACT(DOW FROM v_today);
  v_today_monday DATE := v_today + (CASE WHEN v_today_dow = 0 THEN -6 ELSE 1 - v_today_dow END);
  v_start_monday DATE;
  v_diff_weeks INT;
BEGIN
  SELECT value INTO v_term_start FROM public.system_settings WHERE key = 'term_start_date';
  SELECT value INTO v_override FROM public.system_settings WHERE key = 'manual_week_override';

  -- Check manual override
  IF v_override IS NOT NULL AND v_override NOT IN ('none', '') THEN
    IF LOWER(v_override) IN ('end of term', 'vacation', 'end_of_term') THEN
      RETURN QUERY SELECT 'End of Term'::TEXT, 12::INT, TRUE, NULL::DATE, v_today_monday, NULL::DATE;
      RETURN;
    ELSIF v_override ~ '^\d+$' THEN
      RETURN QUERY SELECT ('Week ' || v_override)::TEXT, v_override::INT, (v_override::INT > 12), NULL::DATE, v_today_monday, NULL::DATE;
      RETURN;
    END IF;
  END IF;

  IF v_term_start IS NOT NULL AND v_term_start ~ '^\d{4}-\d{2}-\d{2}$' THEN
    v_start_date := v_term_start::DATE;
  ELSE
    v_start_date := v_today_monday;
  END IF;

  v_start_monday := v_start_date + (CASE WHEN EXTRACT(DOW FROM v_start_date) = 0 THEN -6 ELSE 1 - EXTRACT(DOW FROM v_start_date)::INT END);
  v_diff_weeks := (v_today_monday - v_start_monday) / 7;

  IF v_diff_weeks < 0 THEN
    RETURN QUERY SELECT 'Week 1'::TEXT, 1::INT, FALSE, v_start_monday, v_today_monday, (v_today_monday + 7);
  ELSIF v_diff_weeks >= 12 THEN
    RETURN QUERY SELECT 'End of Term'::TEXT, 12::INT, TRUE, v_start_monday, v_today_monday, NULL::DATE;
  ELSE
    RETURN QUERY SELECT ('Week ' || (v_diff_weeks + 1)::TEXT)::TEXT, (v_diff_weeks + 1)::INT, FALSE, v_start_monday, v_today_monday, (v_today_monday + 7);
  END IF;
END;
$$;
