-- =========================================================================
-- JC-Best COMPLIANCE HUB — STAGE 12 DATABASE MIGRATION
-- Migration: Add Compliance Hierarchy (Areas -> Checks -> Observations)
-- Target: Supabase (PostgreSQL)
--
-- Safety Guarantees:
-- 1. DOES NOT DROP ANY EXISTING TABLES (Preserves staff, classes, subjects, compliance_reports, user_roles, weekly_tracker)
-- 2. DOES NOT DELETE ANY EXISTING DATA
-- 3. Idempotent: Can be run multiple times safely (IF NOT EXISTS & ON CONFLICT)
-- 4. Fully backwards-compatible with existing auth and reports
-- =========================================================================

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================================
-- 1. COMPLIANCE AREAS TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.compliance_areas (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  division    TEXT NOT NULL DEFAULT 'Operations' CHECK (division IN ('CDS', 'Academic', 'Operations')),
  description TEXT,
  order_index INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Ensure division column exists if table was previously created
ALTER TABLE public.compliance_areas ADD COLUMN IF NOT EXISTS division TEXT NOT NULL DEFAULT 'Operations';
ALTER TABLE public.compliance_areas DROP CONSTRAINT IF EXISTS comp_areas_division_check;
ALTER TABLE public.compliance_areas ADD CONSTRAINT comp_areas_division_check CHECK (division IN ('CDS', 'Academic', 'Operations'));

-- =========================================================================
-- 2. COMPLIANCE CHECKS TABLE
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.compliance_checks (
  id          TEXT PRIMARY KEY,
  area_id     TEXT NOT NULL REFERENCES public.compliance_areas(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  group_name  TEXT,
  description TEXT,
  order_index INT DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- =========================================================================
-- 3. COMPLIANCE OBSERVATIONS TABLE
-- Area -> Check -> Observation -> Status (Compliant | Exception + Severity)
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.compliance_observations (
  id            TEXT PRIMARY KEY,
  area_id       TEXT NOT NULL REFERENCES public.compliance_areas(id) ON DELETE CASCADE,
  area_name     TEXT,
  check_id      TEXT NOT NULL REFERENCES public.compliance_checks(id) ON DELETE CASCADE,
  check_name    TEXT,
  status        TEXT NOT NULL CHECK (status IN ('Compliant', 'Exception')),
  severity      TEXT CHECK (severity IN ('Low', 'Medium', 'High', 'Critical')),
  date          DATE NOT NULL DEFAULT CURRENT_DATE,
  note          TEXT,
  reported_by   UUID REFERENCES auth.users(id) ON DELETE SET NULL,
  reporter_name TEXT,
  evidence_urls TEXT[],
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW(),
  CONSTRAINT uq_compliance_obs_area_check UNIQUE (area_id, check_id)
);

-- =========================================================================
-- 4. PERFORMANCE INDEXES
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_comp_areas_order ON public.compliance_areas(order_index);
CREATE INDEX IF NOT EXISTS idx_comp_checks_area_id ON public.compliance_checks(area_id);
CREATE INDEX IF NOT EXISTS idx_comp_checks_order ON public.compliance_checks(order_index);

CREATE INDEX IF NOT EXISTS idx_comp_obs_area_id ON public.compliance_observations(area_id);
CREATE INDEX IF NOT EXISTS idx_comp_obs_check_id ON public.compliance_observations(check_id);
CREATE INDEX IF NOT EXISTS idx_comp_obs_status ON public.compliance_observations(status);
CREATE INDEX IF NOT EXISTS idx_comp_obs_severity ON public.compliance_observations(severity);
CREATE INDEX IF NOT EXISTS idx_comp_obs_date ON public.compliance_observations(date DESC);
CREATE INDEX IF NOT EXISTS idx_comp_obs_reported_by ON public.compliance_observations(reported_by);

-- =========================================================================
-- 5. AUTO-UPDATE TIMESTAMPS
-- =========================================================================
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_comp_areas_updated ON public.compliance_areas;
CREATE TRIGGER trg_comp_areas_updated
  BEFORE UPDATE ON public.compliance_areas
  FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_comp_checks_updated ON public.compliance_checks;
CREATE TRIGGER trg_comp_checks_updated
  BEFORE UPDATE ON public.compliance_checks
  FOR EACH ROW EXECUTE FUNCTION update_timestamp();

DROP TRIGGER IF EXISTS trg_comp_obs_updated ON public.compliance_observations;
CREATE TRIGGER trg_comp_obs_updated
  BEFORE UPDATE ON public.compliance_observations
  FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- =========================================================================
-- 6. ROW LEVEL SECURITY (RLS) POLICIES
-- =========================================================================
ALTER TABLE public.compliance_areas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.compliance_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.compliance_observations ENABLE ROW LEVEL SECURITY;

-- Helper function if not exists
CREATE OR REPLACE FUNCTION public.is_admin_or_above(uid UUID)
RETURNS BOOLEAN AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.user_roles
    WHERE user_id = uid
      AND role IN ('super_admin', 'admin')
      AND is_verified = TRUE
  );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Compliance Areas Policies
DROP POLICY IF EXISTS "Authenticated read compliance areas" ON public.compliance_areas;
CREATE POLICY "Authenticated read compliance areas"
  ON public.compliance_areas FOR SELECT
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin manage compliance areas" ON public.compliance_areas;
CREATE POLICY "Admin manage compliance areas"
  ON public.compliance_areas FOR ALL
  USING (is_admin_or_above(auth.uid()));

-- Compliance Checks Policies
DROP POLICY IF EXISTS "Authenticated read compliance checks" ON public.compliance_checks;
CREATE POLICY "Authenticated read compliance checks"
  ON public.compliance_checks FOR SELECT
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin manage compliance checks" ON public.compliance_checks;
CREATE POLICY "Admin manage compliance checks"
  ON public.compliance_checks FOR ALL
  USING (is_admin_or_above(auth.uid()));

-- Compliance Observations Policies
DROP POLICY IF EXISTS "Authenticated read compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated read compliance observations"
  ON public.compliance_observations FOR SELECT
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated insert compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated insert compliance observations"
  ON public.compliance_observations FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Authenticated update compliance observations" ON public.compliance_observations;
CREATE POLICY "Authenticated update compliance observations"
  ON public.compliance_observations FOR UPDATE
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Admin delete compliance observations" ON public.compliance_observations;
CREATE POLICY "Admin delete compliance observations"
  ON public.compliance_observations FOR DELETE
  USING (is_admin_or_above(auth.uid()));

-- =========================================================================
-- 7. SEED DATA: AUTHORITATIVE COMPLIANCE AREAS & CHECKS
-- Source: JC-Best Directorate Compliance Orientation Document
-- =========================================================================

-- Seed Compliance Areas with Division
INSERT INTO public.compliance_areas (id, name, division, description, order_index) VALUES
  ('cds', 'CDS', 'CDS', 'Corporate Digital Services and technological infrastructure compliance.', 1),
  ('academic', 'Academic', 'Academic', 'Academic instruction, classroom governance, staff duty, and instructional quality.', 2),
  ('transport', 'Transport', 'Operations', 'School fleet roadworthiness, driver credentials, and transit safety.', 3),
  ('facilities', 'Facilities', 'Operations', 'Physical campus infrastructure, furniture, utilities, and classroom fixtures.', 4),
  ('hostel-kitchen', 'Hostel / Kitchen', 'Operations', 'Boarding facilities, dining etiquette, sanitation, utilities, and siesta protocol.', 5),
  ('minders', 'Minders', 'Operations', 'Early-years childcare, pupil minding, and classroom support supervision.', 6),
  ('maintenance', 'Maintenance', 'Operations', 'Preventive maintenance, campus repairs, and infrastructure upkeep.', 7),
  ('dressing', 'Dressing', 'Operations', 'Institutional uniform adherence, decorum, and operational grooming standards.', 8),
  ('effectiveness', 'Effectiveness', 'Operations', 'Operational performance, task execution pacing, and duty accountability.', 9)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  division = EXCLUDED.division,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index;

-- Seed Authoritative Compliance Checks
INSERT INTO public.compliance_checks (id, area_id, name, group_name, description, order_index) VALUES
  -- 1. CDS Checks
  ('cds-cbt', 'cds', 'CBT', 'CBT', 'Computer-Based Testing platforms and server readiness.', 1),
  ('cds-hardware', 'cds', 'Hardware', 'Hardware', 'Workstations, networking routers, and peripherals.', 2),
  ('cds-website', 'cds', 'Website', 'Website', 'Public portal uptime, SSL certificates, and updates.', 3),
  ('cds-digital-mkt', 'cds', 'Digital Marketing', 'Digital Marketing', 'Digital reach and institutional online presence.', 4),
  ('cds-exam-records', 'cds', 'Exam Records', 'Exam Records', 'Digital score sheets and database backups.', 5),
  ('cds-staff-records', 'cds', 'Staff Records', 'Staff Records', 'Digital personnel files and credential storage.', 6),
  ('cds-camera', 'cds', 'Camera', 'Camera / Printer / Computer Maintenance', 'Campus CCTV camera feed integrity and recording storage.', 7),
  ('cds-printer', 'cds', 'Printer & Computer Maintenance', 'Camera / Printer / Computer Maintenance', 'Printer toner levels, paper inventory, and diagnostic logs.', 8),
  ('cds-result-portal', 'cds', 'Result Portal', 'Result Portal', 'Parent result checking portal performance and uptime.', 9),
  ('cds-documentation', 'cds', 'Documentation', 'Documentation', 'Standard operating procedures and IT asset register.', 10),
  ('cds-biometric', 'cds', 'Biometric', 'Biometric', 'Biometric clock-in scanner synchronization and connectivity.', 11),

  -- 2. ACADEMIC Checks (Academic Staff source checks)
  ('academic-punctuality', 'academic', 'Punctuality', 'Academic Staff', 'Teacher arrival at class before bell rings.', 1),
  ('academic-statutory-records', 'academic', 'Statutory Records', 'Academic Staff', 'Lesson notes, attendance registers, and mark books.', 2),
  ('academic-class-control', 'academic', 'Class Control', 'Academic Staff', 'Classroom management and constructive engagement.', 3),
  ('academic-class-condition', 'academic', 'Class condition & environment', 'Academic Staff', 'Board cleanliness, desk arrangement, and ventilation.', 4),
  ('academic-unprofessional-words', 'academic', 'Use of Unprofessional Words', 'Academic Staff', 'Decorous and standard English spoken at all times.', 5),
  ('academic-mishandling-students', 'academic', 'Physical Mishandling Students', 'Academic Staff', 'Strict adherence to child protection and zero corporal punishment.', 6),
  ('academic-notes', 'academic', 'Notes', 'Academic Staff', 'Vetting of lesson notes and adherence to curriculum scheme.', 7),
  ('academic-assignments', 'academic', 'Assignments', 'Academic Staff', 'Timely distribution and grading of student assignments.', 8),
  ('academic-class-work', 'academic', 'Class work', 'Academic Staff', 'Regularity and thorough marking of daily class exercises.', 9),
  ('academic-practical', 'academic', 'Practical', 'Academic Staff', 'Science lab experiments and ICT practical sessions conduct.', 10),
  ('academic-appearance', 'academic', 'Appearance', 'Academic Staff', 'Professional formal dress code adherence.', 11),
  ('academic-class-teacher-roles', 'academic', 'Class Teacher roles', 'Academic Staff', 'Pastoral care, diary signing, and daily attendance roll.', 12),
  ('academic-exams', 'academic', 'Exams', 'Academic Staff', 'Invigilation diligence and prompt grading of test papers.', 13),
  ('academic-training-programs', 'academic', 'Training programs', 'Academic Staff', 'Attendance and participation in scheduled professional development.', 14),
  ('academic-sleeping-in-class', 'academic', 'Sleeping in class', 'Academic Staff', 'Zero tolerance for sleeping or resting during instructional periods.', 15),
  ('academic-call-in-class', 'academic', 'Call in class', 'Academic Staff', 'Zero non-emergency mobile phone use during teaching periods.', 16),

  -- 3. OPERATIONS Checks
  -- Transport Checks
  ('transport-tyres', 'transport', 'Tyres', 'Transport', 'Tire pressure, tread depth, and spare tire readiness.', 1),
  ('transport-windows', 'transport', 'Windows', 'Transport', 'Vehicle glass integrity and window latch functionality.', 2),
  ('transport-body-work', 'transport', 'Body Work', 'Transport', 'Vehicle body panels, bumpers, and paintwork condition.', 3),
  ('transport-lights', 'transport', 'Lights', 'Transport', 'Headlamps, tail lights, indicators, and reverse lights.', 4),
  ('transport-interior', 'transport', 'Interior', 'Transport', 'Seat cleanliness, upholstery, and cabin sanitation.', 5),
  ('transport-lateness', 'transport', 'Pick-up/drop-off lateness', 'Transport', 'Adherence to morning and afternoon transit schedules.', 6),
  ('transport-breakdown', 'transport', 'Breakdown', 'Transport', 'Roadside mechanical breakdown or failure.', 7),
  ('transport-accident', 'transport', 'Accident', 'Transport', 'Collision, scrape, or vehicular incident.', 8),
  ('transport-fire-extinguisher', 'transport', 'Fire extinguisher', 'Transport', 'Valid and pressurized fire extinguisher in vehicle.', 9),
  ('transport-jack', 'transport', 'Jack', 'Transport', 'Hydraulic jack, wheel spanner, and emergency kit present.', 10),
  ('transport-driving-licences', 'transport', 'Driving Licences', 'Transport', 'Valid commercial drivers license and statutory road permits.', 11),
  ('transport-dressing', 'transport', 'Dressing', 'Transport', 'Driver uniform neatness, footwear, and professional demeanor.', 12),

  -- Facilities Checks
  ('facilities-doors', 'facilities', 'Doors', 'Facilities', 'Door hinges, locks, and handles integrity.', 13),
  ('facilities-windows', 'facilities', 'Windows', 'Facilities', 'Classroom window panes and mesh screens.', 14),
  ('facilities-tables', 'facilities', 'Tables', 'Facilities', 'Student desks and teacher tables condition.', 15),
  ('facilities-chairs', 'facilities', 'Chairs', 'Facilities', 'Student chairs and seating safety.', 16),
  ('facilities-tiles', 'facilities', 'Tiles', 'Facilities', 'Flooring tiles intact with no cracked or loose surfaces.', 17),
  ('facilities-lights', 'facilities', 'Lights', 'Facilities', 'Classroom and hallway bulbs functioning properly.', 18),
  ('facilities-fans', 'facilities', 'Fans', 'Facilities', 'Ceiling and standing fans operational and secured.', 19),
  ('facilities-flowers', 'facilities', 'Flowers', 'Facilities', 'Campus landscaping, flower beds, and grounds upkeep.', 20),
  ('facilities-tidiness', 'facilities', 'Tidiness', 'Facilities', 'General cleanliness and litter-free campus spaces.', 21),
  ('facilities-pipes', 'facilities', 'Pipes', 'Facilities', 'Plumbing lines, gutters, and drainage channels clear.', 22),
  ('facilities-kerbs', 'facilities', 'Kerbs', 'Facilities', 'Walkway kerbs, paving stones, and road borders painted.', 23),
  ('facilities-ac', 'facilities', 'AC', 'Facilities', 'Air conditioning units functional with clean filters.', 24),

  -- Hostel / Kitchen Checks
  ('hostel-promptness', 'hostel-kitchen', 'Promptness to class from hostel', 'Hostel / Kitchen', 'Boarders prompt release and punctual arrival in classrooms.', 25),
  ('hostel-table-etiquette', 'hostel-kitchen', 'Table etiquette', 'Hostel / Kitchen', 'Dining hall meal etiquette, portioning, and orderly behavior.', 26),
  ('hostel-hot-water', 'hostel-kitchen', 'Hot water', 'Hostel / Kitchen', 'Boiler functioning and warm water supply for boarders.', 27),
  ('hostel-drinking-water', 'hostel-kitchen', 'Drinking/using water', 'Hostel / Kitchen', 'Treated drinking water dispenser availability and water pressure.', 28),
  ('hostel-cleanliness', 'hostel-kitchen', 'Cleanliness', 'Hostel / Kitchen', 'Dormitory cleanliness, bed making, and laundry sanitation.', 29),
  ('hostel-siesta', 'hostel-kitchen', 'Siesta enforcement', 'Hostel / Kitchen', 'Enforcement of mandatory afternoon resting period.', 30),

  -- Support & Operations Role Checks
  ('operations-minders', 'minders', 'Minders', 'Minders', 'Early-years childcare, classroom assistant duty, and pupil minding.', 31),
  ('operations-maintenance', 'maintenance', 'Maintenance', 'Maintenance', 'Preventative maintenance and prompt repair execution.', 32),
  ('operations-dressing', 'dressing', 'Dressing', 'Dressing', 'Support staff uniform adherence and grooming standards.', 33),
  ('operations-effectiveness', 'effectiveness', 'Effectiveness', 'Effectiveness', 'Task completion timeline and operational diligence.', 34)
ON CONFLICT (id) DO UPDATE SET
  area_id = EXCLUDED.area_id,
  name = EXCLUDED.name,
  group_name = EXCLUDED.group_name,
  description = EXCLUDED.description,
  order_index = EXCLUDED.order_index;
