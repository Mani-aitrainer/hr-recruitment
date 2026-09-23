import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Candidate } from '../data/candidate.model';
import { CandidateFormComponent } from './candidate-form';

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

function setup(routeId: string | null) {
  TestBed.configureTestingModule({
    imports: [CandidateFormComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(CandidateFormComponent);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, httpMock };
}

describe('CandidateFormComponent (new mode)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function fillValidForm(component: CandidateFormComponent) {
    component['form'].setValue({
      fullName: 'Asha Rao',
      email: 'asha.rao@example.com',
      phone: '9876543210',
      location: 'Bengaluru',
      currentEmployer: '',
      currentTitle: '',
      totalExperienceYears: 5,
      noticePeriodDays: null,
      highestQualification: 'BACHELORS',
      skills: ['Java'],
      summary: '',
    });
  }

  // ---- CAND-1: positive, valid form submits and creates ----
  it('CAND-1 submits a valid form and shows a success confirmation', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);

    component['submit']();

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    req.flush(sampleCandidate);

    expect(component['saveState']()).toBe('saved');
    expect(component['savedCandidateName']()).toBe('Asha Rao');
  });

  // ---- CAND-2: negative, fullName boundary blocks submission ----
  it('CAND-2 blocks submission when fullName is 1 character', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);
    component['form'].controls.fullName.setValue('A');

    component['submit']();

    httpMock.expectNone(baseUrl);
    expect(component['form'].controls.fullName.invalid).toBe(true);
  });

  // ---- CAND-3: negative, malformed email blocks submission ----
  it('CAND-3 blocks submission for a malformed email', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);
    component['form'].controls.email.setValue('mani@');

    component['submit']();

    httpMock.expectNone(baseUrl);
    expect(component['form'].controls.email.invalid).toBe(true);
  });

  // ---- CAND-4: negative, experience boundary blocks submission ----
  it('CAND-4 blocks submission when totalExperienceYears is 41', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);
    component['form'].controls.totalExperienceYears.setValue(41);

    component['submit']();

    httpMock.expectNone(baseUrl);
    expect(component['form'].controls.totalExperienceYears.invalid).toBe(true);
  });

  it('accepts totalExperienceYears at the boundary of 40 and 0', () => {
    const { component } = setup(null);
    component['form'].controls.totalExperienceYears.setValue(40);
    expect(component['form'].controls.totalExperienceYears.valid).toBe(true);
    component['form'].controls.totalExperienceYears.setValue(0);
    expect(component['form'].controls.totalExperienceYears.valid).toBe(true);
  });

  // ---- CAND-5: negative, empty skills blocks submission ----
  it('CAND-5 blocks submission when skills is empty', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);
    component['form'].controls.skills.setValue([]);

    component['submit']();

    httpMock.expectNone(baseUrl);
    expect(component['form'].controls.skills.invalid).toBe(true);
  });

  // ---- negative: server 400 maps onto the right control ----
  it('maps a server 400 field error onto the matching form control', () => {
    const { fixture, component, httpMock } = setup(null);
    fixture.detectChanges();
    fillValidForm(component);

    component['submit']();
    const req = httpMock.expectOne(baseUrl);
    req.flush(
      {
        type: 'https://hr-recruitment/errors/validation',
        title: 'Validation failed',
        status: 400,
        detail: '1 field is invalid',
        instance: '/api/v1/candidates',
        requestId: 'r1',
        errors: [{ field: 'email', message: 'must be a valid email address' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(component['form'].controls.email.errors?.['server']).toBe('must be a valid email address');
    expect(component['form'].controls.email.touched).toBe(true);

    fixture.detectChanges();
    const rendered = (fixture.nativeElement as HTMLElement).querySelector('#email-error')?.textContent;
    expect(rendered).toContain('must be a valid email address');
  });
});

describe('CandidateFormComponent (edit mode)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  // ---- CAND-6: positive, loads and edits an existing candidate ----
  it('CAND-6 loads the candidate by id and prefills the form', () => {
    const { fixture, httpMock } = setup(sampleCandidate.id);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`);
    req.flush(sampleCandidate);

    const component = fixture.componentInstance;
    expect(component['form'].controls.fullName.value).toBe('Asha Rao');
    expect(component['loadState']()).toBe('ready');
  });

  // ---- CAND-7: negative, 409 version-conflict shows a reload prompt ----
  it('CAND-7 shows a reload prompt on a 409 version-conflict', () => {
    const { fixture, httpMock } = setup(sampleCandidate.id);
    fixture.detectChanges();
    httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`).flush(sampleCandidate);

    const component = fixture.componentInstance;
    component['form'].controls.fullName.setValue('Asha Rao Updated');
    component['submit']();

    const putReq = httpMock.expectOne(`${baseUrl}/${sampleCandidate.id}`);
    putReq.flush(
      {
        type: 'https://hr-recruitment/errors/version-conflict',
        title: 'Version conflict',
        status: 409,
        detail: 'stale',
        instance: `/api/v1/candidates/${sampleCandidate.id}`,
        requestId: 'r2',
        errors: null,
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(component['saveState']()).toBe('version-conflict');
  });

  // ---- CAND-8: negative, unknown id shows a not-found message ----
  it('CAND-8 shows a not-found message for an unknown id', () => {
    const { fixture, httpMock } = setup('00000000-0000-0000-0000-000000000000');
    fixture.detectChanges();

    httpMock
      .expectOne(`${baseUrl}/00000000-0000-0000-0000-000000000000`)
      .flush(
        {
          type: 'https://hr-recruitment/errors/not-found',
          title: 'Not found',
          status: 404,
          detail: 'not found',
          instance: '/api/v1/candidates/00000000-0000-0000-0000-000000000000',
          requestId: 'r3',
          errors: null,
        },
        { status: 404, statusText: 'Not Found' },
      );

    expect(fixture.componentInstance['loadState']()).toBe('not-found');
  });
});
