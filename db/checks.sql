-- Proves every CHECK/NOT NULL constraint on `candidate` (V1). Run against a database that
-- already has V1 applied. Each "expect FAIL" statement must raise an error; run manually and
-- confirm, or wrap each in error-handling to get an automated pass/fail summary.

-- Valid row: must succeed.
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Asha Rao', 'asha.rao@example.com', '+91 98765 43210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java','SQL']);

-- full_name: expect FAIL (1 char)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('A', 'a@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java']);

-- email: expect FAIL (malformed)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Bala Krishna', 'not-an-email', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java']);

-- email: expect FAIL (uppercase not allowed, must be stored lowercase)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Bala Krishna', 'Bala@Example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java']);

-- phone: expect FAIL (7 digits, below 8 char minimum)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Chetan Iyer', 'chetan@example.com', '1234567', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java']);

-- phone: expect FAIL (contains letters)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Chetan Iyer', 'chetan2@example.com', '98765ABCDE', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java']);

-- location: expect FAIL (1 char)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Divya Menon', 'divya@example.com', '9876543210', 'B', 5, 'BACHELORS', ARRAY['Java']);

-- total_experience_years: expect FAIL (41, above range)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Esha Nair', 'esha@example.com', '9876543210', 'Bengaluru', 41, 'BACHELORS', ARRAY['Java']);

-- total_experience_years: expect FAIL (-1, below range)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Esha Nair', 'esha2@example.com', '9876543210', 'Bengaluru', -1, 'BACHELORS', ARRAY['Java']);

-- notice_period_days: expect FAIL (181, above range)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, notice_period_days, highest_qualification, skills)
VALUES ('Farhan Sheikh', 'farhan@example.com', '9876543210', 'Bengaluru', 5, 181, 'BACHELORS', ARRAY['Java']);

-- highest_qualification: expect FAIL (unknown enum value)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Gita Pillai', 'gita@example.com', '9876543210', 'Bengaluru', 5, 'PHD_EXTRA', ARRAY['Java']);

-- skills: expect FAIL (empty array, below cardinality 1)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Harish Dutt', 'harish@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY[]::text[]);

-- skills: expect FAIL (16 items, above cardinality 15)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Ira Bhatt', 'ira@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS',
  ARRAY['s1','s2','s3','s4','s5','s6','s7','s8','s9','s10','s11','s12','s13','s14','s15','s16']);

-- skills: expect FAIL (item over 30 chars)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills)
VALUES ('Jayant Kapoor', 'jayant@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS',
  ARRAY['this-skill-name-is-way-too-long-for-the-rule']);

-- summary: expect FAIL (over 2000 chars)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills, summary)
VALUES ('Kavya Suresh', 'kavya@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java'], repeat('x', 2001));

-- status: expect FAIL (unknown enum value)
INSERT INTO candidate (full_name, email, phone, location, total_experience_years, highest_qualification, skills, status)
VALUES ('Laxmi Iyengar', 'laxmi@example.com', '9876543210', 'Bengaluru', 5, 'BACHELORS', ARRAY['Java'], 'PENDING');
