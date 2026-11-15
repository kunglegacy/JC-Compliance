-- =========================================================================
-- SEED REAL ACADEMIC STAFF ROSTER FROM EXTRACTED IMAGE DATA
-- 100% Non-destructive: Updates existing staff identities in place to preserve
-- historical compliance exceptions, foreign keys, and UUID identity integrity.
--
-- NEVER executes DELETE FROM public.staff, preventing Governance Protection
-- trigger errors (prevent_staff_hard_delete_if_exceptions).
-- =========================================================================

-- 1. Create temporary staging table
CREATE TEMP TABLE IF NOT EXISTS temp_academic_roster (
  staff_code        TEXT,
  name              TEXT,
  department        TEXT,
  staff_group       TEXT,
  role              TEXT,
  assigned_location TEXT,
  subjects          TEXT,
  status            TEXT,
  category          TEXT
);

TRUNCATE TABLE temp_academic_roster;

-- 2. Populate 73 verified Academic teachers into staging table
INSERT INTO temp_academic_roster (staff_code, name, department, staff_group, role, assigned_location, subjects, status, category) VALUES
  ('JCB-T-101', 'Toney Balogun', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1C, SS3B, SS2B/C', 'Maths, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-102', 'Alade Emmanuel', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1A, SS3C, SS2R/A', 'Maths, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-103', 'Collins Ede', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1E, SS3A, SS3A/B', 'Maths, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-104', 'Samuel Idoko', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS2A, SS2B, SS1C/D', 'Maths, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-105', 'Michael Kwaghbunde', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1A/B, B8B, SS2D', 'Further Maths, Mathematics', 'Active', 'Academic Staff'),
  ('JCB-T-106', 'Ayogu Eze', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1D, B7B, B8A,B', 'Mathematics, Basic Technology', 'Active', 'Academic Staff'),
  ('JCB-T-107', 'Nelson Ugboja', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1B, B9A-C, B9A', 'Mathematics, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-108', 'Joseph Agber', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B7D, B8D, B9D, B8E', 'Mathematics, Further Maths, Basic Technology', 'Active', 'Academic Staff'),
  ('JCB-T-109', 'Micheal Eleje', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B7C, B9E, Pry Sch', 'Mathematics, Primary School Maths', 'Active', 'Academic Staff'),
  ('JCB-T-110', 'Nzube - Pry', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B9E', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-111', 'Emeka David', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B8E, B8A, B9A,B', 'Mathematics, Basic Technology', 'Active', 'Academic Staff'),
  ('JCB-T-112', 'Dr Etukakpan Eno', 'Academic', 'Physical Sciences', 'Principal', 'B7B-E, B7A', 'Basic Technology, Physics', 'Active', 'Academic Staff'),
  ('JCB-T-113', 'John Providence', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3D, SS1B, B8A-D', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-114', 'Victor Obaje', 'Academic', 'Physical Sciences', 'HOD', 'SS3A, SS2R,C', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-115', 'Ifeanyi', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3B, B7C, SS1C,D, B9D', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-116', 'Godwin', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3C, SS2A,B, SS1A', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-117', 'Henry Arikpo', 'Academic', 'Physical Sciences', 'VP Snr/Mt', 'B8E, B9A-C', 'Physics', 'Active', 'Academic Staff'),
  ('JCB-T-118', 'Philimon Darshaka', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B9E, SS2C, SS2R', 'Further Maths, Mathematics', 'Active', 'Academic Staff'),
  ('JCB-T-119', 'Ifeanyi Ezeagu', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B8C, B7A, B7A', 'Mathematics, Basic Technology', 'Active', 'Academic Staff'),
  ('JCB-T-120', 'Okagu Chidi', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3E, SS3C/D', 'Mathematics, Further Maths', 'Active', 'Academic Staff'),
  ('JCB-T-121', 'Isaac Oyeh', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B9B, B9C, B9D', 'Mathematics', 'Active', 'Academic Staff'),
  ('JCB-T-122', 'Favour Asekhamen', 'Academic', 'Life Sciences', 'Subject Teacher', 'B7B,D,E, B8A-D, B7C-E', 'Physics, Basic Technology, Biology', 'Active', 'Academic Staff'),
  ('JCB-T-123', 'Chukwuma Osakwe', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B7E', 'Mathematics', 'Active', 'Academic Staff'),
  ('JCB-T-124', 'Chiaka Arikpo', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS2R,A,B, SS1A,E', 'Digital Technology', 'Active', 'Academic Staff'),
  ('JCB-T-125', 'Rhema Odinma', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3, SS2C,D', 'Digital Technology', 'Active', 'Academic Staff'),
  ('JCB-T-126', 'Nanret', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1B,C,D, B9', 'Digital Technology', 'Active', 'Academic Staff'),
  ('JCB-T-127', 'Godwin Amah', 'Academic', 'Physical Sciences', 'Subject Teacher', 'B7, B8', 'Digital Technology', 'Active', 'Academic Staff'),
  ('JCB-T-128', 'Daniel Mgbemere', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3, SS2, SS1', 'Technical Drawing', 'Active', 'Academic Staff'),
  ('JCB-T-129', 'Kunle Gabriel', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS1A/B. C/D/E, SS2R/A. B/C/D, SS3A/B. C/D/E, B9C-E', 'Geography, Basic Technology', 'Active', 'Academic Staff'),
  ('JCB-T-130', 'Anthony Ekeh', 'Academic', 'Life Sciences', 'Subject Teacher', 'B9D-E, B8, SS1A/B. C/D/E, SS2R/A. B/C/D', 'Agricultural Science', 'Active', 'Academic Staff'),
  ('JCB-T-131', 'Godwin Faith', 'Academic', 'Life Sciences', 'Subject Teacher', 'B7, B8, B9, B7', 'Home Economics, Agricultural Science', 'Active', 'Academic Staff'),
  ('JCB-T-132', 'Teniola Peace', 'Academic', 'Life Sciences', 'Subject Teacher', 'SS2C,D, B8A,B, B9A,B,C', 'Livestock, Biology, Agricultural Science', 'Active', 'Academic Staff'),
  ('JCB-T-133', 'Emmanuel Sunday', 'Academic', 'Life Sciences', 'Subject Teacher', 'SS3A/B. C/D/E, SS3', 'Agricultural Science, Livestock', 'Active', 'Academic Staff'),
  ('JCB-T-134', 'Godfrey Odemejero', 'Academic', 'Life Sciences', 'Subject Teacher', 'SS3C, SS2C, SS1A, B9B-E', 'Chemistry, Biology', 'Active', 'Academic Staff'),
  ('JCB-T-135', 'Enendu Abigail', 'Academic', 'Life Sciences', 'HOD', 'SS3A/B. C/D, B8C-E', 'Biology', 'Active', 'Academic Staff'),
  ('JCB-T-136', 'Bulus Joseph', 'Academic', 'Life Sciences', 'Subject Teacher', 'B7A-B, SS1A/B. C/D/E, SS2R/A. B/C/D', 'Biology', 'Active', 'Academic Staff'),
  ('JCB-T-137', 'Miracle Onwuzuligbo', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS2A, SS3D, B7B-E', 'Chemistry', 'Active', 'Academic Staff'),
  ('JCB-T-138', 'Uchenna Anyaorah', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS1D, B9A-E, B7A', 'Chemistry', 'Active', 'Academic Staff'),
  ('JCB-T-139', 'Chibuike Chinedu', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3A, SS2B, SS2R, B8A, B', 'Chemistry', 'Active', 'Academic Staff'),
  ('JCB-T-140', 'Amusan Sunday', 'Academic', 'Physical Sciences', 'Subject Teacher', 'SS3B, SS1B, SS1C, B8C, D, E', 'Chemistry', 'Active', 'Academic Staff'),
  ('JCB-T-141', 'Ebuka Obinna', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7A-E, B8A-E, B9A-E, B9', 'BST(PHE), Civic Education', 'Active', 'Academic Staff'),
  ('JCB-T-142', 'Michael Ogbaji', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS1, SS2, SS3B,D, SS2R,A,B', 'Government, Citizen Education', 'Active', 'Academic Staff'),
  ('JCB-T-143', 'Chidozie Prosper', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7, B9, B9, B8', 'Civic Education, Security Education', 'Active', 'Academic Staff'),
  ('JCB-T-144', 'Paschal Ezeamii', 'Academic', 'Social Sciences', 'HOD', 'SS3, B9, SS3A,C,E', 'Government, History, Citizen Education', 'Active', 'Academic Staff'),
  ('JCB-T-145', 'Chioma Madu', 'Academic', 'Social Sciences', 'Subject Teacher', 'B8, B9', 'CCA', 'Active', 'Academic Staff'),
  ('JCB-T-146', 'Thomson Ododot', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7, B7A-B, SS2C-D', 'CCA, Social Studies, Citizen Education', 'Active', 'Academic Staff'),
  ('JCB-T-147', 'Kelvin Kumaorun', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS3A/B. C/D, SS2R/A. B/C/D, SS1A/B', 'Economics', 'Active', 'Academic Staff'),
  ('JCB-T-148', 'Balogun Afeez', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS3, SS2, SS1', 'Accounting', 'Active', 'Academic Staff'),
  ('JCB-T-149', 'Ijeoma Obidigwe', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS3, SS1, 2, B9A, B8B, B8D', 'Commerce, Business Studies, Social Studies', 'Active', 'Academic Staff'),
  ('JCB-T-150', 'Oby Onuorah', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7, B9B-E', 'Business Studies', 'Active', 'Academic Staff'),
  ('JCB-T-151', 'Sylvia Beschel', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7, B8, B8A,B,C, B7', 'History, Social Studies, Security Education', 'Active', 'Academic Staff'),
  ('JCB-T-152', 'Monday Blessing', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS1C/D, E, SS3E, B8A, C-E', 'Economics, Business Studies', 'Active', 'Academic Staff'),
  ('JCB-T-153', 'Ijeoma Nwadiolor', 'Academic', 'Life Sciences', 'Subject Teacher', 'SS1, SS2R,A,B', 'Livestock', 'Active', 'Academic Staff'),
  ('JCB-T-155', 'Solomon Hannah', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7D-E, B8, B9', 'Christian Religious Studies', 'Active', 'Academic Staff'),
  ('JCB-T-156', 'Amina Buhari', 'Academic', 'Social Sciences', 'Subject Teacher', 'B7, B8, B9, SS2, SS3', 'Islamic Religious Studies', 'Active', 'Academic Staff'),
  ('JCB-T-157', 'ISAH HABILA', 'Academic', 'Social Sciences', 'Subject Teacher', 'SS1', 'Islamic Religious Studies', 'Active', 'Academic Staff'),
  ('JCB-T-158', 'Barakat Badmus', 'Academic', 'Social Sciences', 'Subject Teacher', 'B9, B7C-E, B8E', 'Social Studies', 'Active', 'Academic Staff'),
  ('JCB-T-159', 'Chizoba Okoye', 'Academic', 'Languages', 'VP', 'B7, B8, B9', 'Igbo', 'Active', 'Academic Staff'),
  ('JCB-T-160', 'Okoye Patience', 'Academic', 'Languages', 'Subject Teacher', 'B9, B8, B9A,B', 'Literature in English, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-161', 'Micheal Isaiah', 'Academic', 'Languages', 'Subject Teacher', 'B8A-D, B8C-E, B7, B7A, B', 'Speech, Composition, Comprehension', 'Active', 'Academic Staff'),
  ('JCB-T-162', 'Ebuka Emmanuel John', 'Academic', 'Languages', 'HOD', 'SS3E, SS3A, SS2R, A-C', 'Composition, Grammar, Speech', 'Active', 'Academic Staff'),
  ('JCB-T-163', 'Godwin Ekeh', 'Academic', 'Languages', 'Subject Teacher', 'B7, B7, B8A-B', 'Literature in English, Grammar, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-164', 'Mariam Igboanugo', 'Academic', 'Languages', 'Subject Teacher', 'SS1, SS3, SS3C, SS2D', 'Speech, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-165', 'Daniel Okorafor', 'Academic', 'Languages', 'Subject Teacher', 'SS1, SS2, SS1A,B, SS2A', 'Literature in English, Comprehension, Composition, Grammar', 'Active', 'Academic Staff'),
  ('JCB-T-166', 'Theresa Adayi', 'Academic', 'Languages', 'Subject Teacher', 'SS3, SS1, SS1C-E', 'Literature in English, Comprehension, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-167', 'Joseph Obiechi', 'Academic', 'Languages', 'Subject Teacher', 'B7, B8, B9, B7A-C', 'French, Christian Religious Studies', 'Active', 'Academic Staff'),
  ('JCB-T-168', 'Idah James', 'Academic', 'Languages', 'Subject Teacher', 'SS3C-E, B9C-E', 'Grammar, Comprehension, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-169', 'Bello Favour', 'Academic', 'Languages', 'Subject Teacher', 'B8, B9, B7D-E', 'Comprehension, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-170', 'Zugu Armstrong', 'Academic', 'Languages', 'Subject Teacher', 'SS3A,B, SS2A,B,C, SS2, SS2R', 'Comprehension, Composition, Literature in English, Grammar', 'Active', 'Academic Staff'),
  ('JCB-T-171', 'Joel Samuel', 'Academic', 'Languages', 'Subject Teacher', 'B7, B8E, B8, B9, SS2R', 'Speech, Grammar, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-172', 'Victor Edeh', 'Academic', 'Languages', 'Subject Teacher', 'SS1, SS3B, D', 'Grammar, Composition', 'Active', 'Academic Staff'),
  ('JCB-T-173', 'Emmanuel Mishael', 'Academic', 'Languages', 'Subject Teacher', 'SS2D, SS2A-C, B9, B7C', 'Composition, Grammar', 'Active', 'Academic Staff');

-- 3. Match existing staff by NAME: update their staff_code and details IN PLACE
-- This preserves existing UUID primary keys and all dependent compliance_reports!
UPDATE public.staff s
SET staff_code = t.staff_code,
    department = t.department,
    staff_group = t.staff_group,
    role = t.role,
    assigned_location = t.assigned_location,
    subjects = t.subjects,
    status = t.status,
    category = t.category,
    updated_at = NOW()
FROM temp_academic_roster t
WHERE LOWER(TRIM(s.name)) = LOWER(TRIM(t.name));

-- 4. Match existing staff by STAFF_CODE if code matches but name had punctuation differences
UPDATE public.staff s
SET name = t.name,
    department = t.department,
    staff_group = t.staff_group,
    role = t.role,
    assigned_location = t.assigned_location,
    subjects = t.subjects,
    status = t.status,
    category = t.category,
    updated_at = NOW()
FROM temp_academic_roster t
WHERE LOWER(TRIM(s.staff_code)) = LOWER(TRIM(t.staff_code))
  AND LOWER(TRIM(s.name)) != LOWER(TRIM(t.name));

-- 5. Insert staff that do NOT exist yet (by staff_code AND by name)
INSERT INTO public.staff (id, staff_code, name, department, staff_group, role, assigned_location, subjects, status, category)
SELECT 
  gen_random_uuid(),
  t.staff_code,
  t.name,
  t.department,
  t.staff_group,
  t.role,
  t.assigned_location,
  t.subjects,
  t.status,
  t.category
FROM temp_academic_roster t
WHERE NOT EXISTS (
  SELECT 1 FROM public.staff s 
  WHERE LOWER(TRIM(s.staff_code)) = LOWER(TRIM(t.staff_code))
     OR LOWER(TRIM(s.name)) = LOWER(TRIM(t.name))
);

-- 6. Clean up temporary table
DROP TABLE IF EXISTS temp_academic_roster;
