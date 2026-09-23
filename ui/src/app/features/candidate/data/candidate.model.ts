export type HighestQualification = 'HIGH_SCHOOL' | 'DIPLOMA' | 'BACHELORS' | 'MASTERS' | 'DOCTORATE';

export type CandidateStatus = 'ACTIVE' | 'ARCHIVED';

/** Matches the API's CandidateResponse exactly (docs/specs/candidate.spec.md). */
export interface Candidate {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  location: string;
  currentEmployer: string | null;
  currentTitle: string | null;
  totalExperienceYears: number;
  noticePeriodDays: number | null;
  highestQualification: HighestQualification;
  skills: string[];
  summary: string | null;
  status: CandidateStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
}

/** Matches the API's CandidateRequest. `version` is required on PUT, omitted on POST. */
export interface CandidateRequest {
  fullName: string;
  email: string;
  phone: string;
  location: string;
  currentEmployer: string | null;
  currentTitle: string | null;
  totalExperienceYears: number;
  noticePeriodDays: number | null;
  highestQualification: HighestQualification | null;
  skills: string[];
  summary: string | null;
  version?: number;
}

export interface ProblemDetailFieldError {
  field: string;
  message: string;
}

/** RFC 7807 problem+json error body. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  requestId: string;
  errors: ProblemDetailFieldError[] | null;
}

export const HIGHEST_QUALIFICATIONS: HighestQualification[] = [
  'HIGH_SCHOOL',
  'DIPLOMA',
  'BACHELORS',
  'MASTERS',
  'DOCTORATE',
];
