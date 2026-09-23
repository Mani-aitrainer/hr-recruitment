import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { CandidateApiError, CandidateService } from './candidate.service';
import { Candidate, CandidateRequest, ProblemDetail } from './candidate.model';

describe('CandidateService', () => {
  let service: CandidateService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/api/v1/candidates`;

  const sampleCandidate: Candidate = {
    id: '11111111-1111-1111-1111-111111111111',
    fullName: 'Asha Rao',
    email: 'asha.rao@example.com',
    phone: '9876543210',
    location: 'Bengaluru',
    currentEmployer: null,
    currentTitle: null,
    totalExperienceYears: 5,
    noticePeriodDays: null,
    highestQualification: 'BACHELORS',
    skills: ['Java'],
    summary: null,
    status: 'ACTIVE',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    archivedAt: null,
  };

  const sampleRequest: CandidateRequest = {
    fullName: 'Asha Rao',
    email: 'asha.rao@example.com',
    phone: '9876543210',
    location: 'Bengaluru',
    currentEmployer: null,
    currentTitle: null,
    totalExperienceYears: 5,
    noticePeriodDays: null,
    highestQualification: 'BACHELORS',
    skills: ['Java'],
    summary: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CandidateService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ---- CAND-1: positive, create sends the right request and maps the response ----
  it('CAND-1 create() POSTs to /api/v1/candidates and returns the created candidate', () => {
    let result: Candidate | undefined;
    service.create(sampleRequest).subscribe((c) => (result = c));

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.fullName).toBe('Asha Rao');
    req.flush(sampleCandidate);

    expect(result).toEqual(sampleCandidate);
  });

  // ---- CAND-8: negative, error responses surface as typed failures, not crashes ----
  it('CAND-8 getById() maps a 404 problem+json body to a CandidateApiError', () => {
    let error: unknown;
    service.getById('unknown-id').subscribe({ error: (e) => (error = e) });

    const problem: ProblemDetail = {
      type: 'https://hr-recruitment/errors/not-found',
      title: 'Not found',
      status: 404,
      detail: 'not found',
      instance: '/api/v1/candidates/unknown-id',
      requestId: 'r1',
      errors: null,
    };
    httpMock.expectOne(`${baseUrl}/unknown-id`).flush(problem, { status: 404, statusText: 'Not Found' });

    expect(error).toBeInstanceOf(CandidateApiError);
    expect((error as CandidateApiError).status).toBe(404);
  });

  // ---- CAND-7: negative, 409 version-conflict is surfaced as a typed error ----
  it('CAND-7 update() maps a 409 version-conflict problem to a CandidateApiError', () => {
    let error: unknown;
    service.update(sampleCandidate.id, { ...sampleRequest, version: 0 }).subscribe({ error: (e) => (error = e) });

    const problem: ProblemDetail = {
      type: 'https://hr-recruitment/errors/version-conflict',
      title: 'Version conflict',
      status: 409,
      detail: 'stale version',
      instance: `/api/v1/candidates/${sampleCandidate.id}`,
      requestId: 'r2',
      errors: null,
    };
    httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`).flush(problem, { status: 409, statusText: 'Conflict' });

    expect(error).toBeInstanceOf(CandidateApiError);
    expect((error as CandidateApiError).problem?.type).toContain('version-conflict');
  });

  it('update() PUTs to /api/v1/candidates/{id} with the request body', () => {
    service.update(sampleCandidate.id, { ...sampleRequest, version: 0 }).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(0);
    req.flush(sampleCandidate);
  });

  it('getById() GETs /api/v1/candidates/{id}', () => {
    service.getById(sampleCandidate.id).subscribe();
    const req = httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`);
    expect(req.request.method).toBe('GET');
    req.flush(sampleCandidate);
  });
});
