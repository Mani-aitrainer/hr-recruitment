-- V1: candidate table (candidate spec v0.1, docs/specs/candidate.spec.md)
-- Standalone talent pool record. No foreign key to job_posting (out of scope, see spec).

-- CHECK constraints cannot contain a subquery, so the per-item skills length rule
-- (each skill 1-30 chars) is expressed as an immutable function instead.
CREATE FUNCTION candidate_skills_item_lengths_ok(skills text[]) RETURNS boolean AS $$
  SELECT NOT EXISTS (
    SELECT 1 FROM unnest(skills) AS s WHERE char_length(s) < 1 OR char_length(s) > 30
  );
$$ LANGUAGE sql IMMUTABLE;

CREATE TABLE candidate (
  id                       uuid         PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Basic information
  full_name                varchar(100) NOT NULL CHECK (char_length(full_name) BETWEEN 2 AND 100),
  email                    varchar(255) NOT NULL CHECK (char_length(email) <= 255 AND email = lower(email) AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  phone                    varchar(20)  NOT NULL CHECK (char_length(phone) BETWEEN 8 AND 20 AND phone ~ '^\+?[0-9 \-]+$'),
  location                 varchar(100) NOT NULL CHECK (char_length(location) BETWEEN 2 AND 100),

  -- Professional information
  current_employer         varchar(100)          CHECK (current_employer IS NULL OR char_length(current_employer) BETWEEN 2 AND 100),
  current_title            varchar(100)          CHECK (current_title IS NULL OR char_length(current_title) BETWEEN 2 AND 100),
  total_experience_years   smallint     NOT NULL CHECK (total_experience_years BETWEEN 0 AND 40),
  notice_period_days       smallint              CHECK (notice_period_days IS NULL OR notice_period_days BETWEEN 0 AND 180),
  highest_qualification    varchar(20)  NOT NULL CHECK (highest_qualification IN ('HIGH_SCHOOL','DIPLOMA','BACHELORS','MASTERS','DOCTORATE')),
  skills                   text[]       NOT NULL CHECK (cardinality(skills) BETWEEN 1 AND 15),
  summary                  varchar(2000)         CHECK (summary IS NULL OR char_length(summary) <= 2000),

  -- Server-set
  status                   varchar(12)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  version                  integer      NOT NULL DEFAULT 1,
  created_at               timestamptz  NOT NULL DEFAULT now(),
  updated_at               timestamptz  NOT NULL DEFAULT now(),
  archived_at              timestamptz,
  deleted_at               timestamptz
);

-- Each skill item must be 1-30 chars; case-insensitive uniqueness within the array is enforced
-- in the API (CandidateService), matching how job_posting.skills is handled.
ALTER TABLE candidate ADD CONSTRAINT candidate_skills_item_length
  CHECK (candidate_skills_item_lengths_ok(skills));

CREATE INDEX candidate_status_updated_idx ON candidate (status, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX candidate_email_idx ON candidate (email) WHERE deleted_at IS NULL;
