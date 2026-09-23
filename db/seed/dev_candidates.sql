-- Synthetic sample data only. No real personal data (see proposal.md risk R7).
-- Never run in prod.
INSERT INTO candidate (full_name, email, phone, location, current_employer, current_title,
  total_experience_years, notice_period_days, highest_qualification, skills, summary)
VALUES
  ('Asha Rao', 'asha.rao@example.com', '+91 98765 43210', 'Bengaluru', 'Acme Corp', 'Backend Engineer',
   5, 30, 'BACHELORS', ARRAY['Java','Spring','PostgreSQL'], 'Backend engineer with a focus on distributed systems.'),
  ('Bala Krishna', 'bala.krishna@example.com', '+91 91234 56780', 'Hyderabad', 'Globex Inc', 'Frontend Developer',
   3, 15, 'BACHELORS', ARRAY['Angular','TypeScript','CSS'], 'Frontend developer building accessible web apps.'),
  ('Chetan Iyer', 'chetan.iyer@example.com', '+91 99887 76655', 'Pune', NULL, NULL,
   0, NULL, 'MASTERS', ARRAY['Python','Data Analysis'], NULL);
