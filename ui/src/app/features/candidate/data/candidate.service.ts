import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Candidate, CandidateRequest, ProblemDetail } from './candidate.model';

/** Thrown for any non-2xx response, carrying the parsed problem+json body when present. */
export class CandidateApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemDetail | null,
  ) {
    super(problem?.detail ?? `Request failed with status ${status}`);
  }
}

/** The only place in the app that calls /api/v1/candidates. */
@Injectable({ providedIn: 'root' })
export class CandidateService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/candidates`;

  create(request: CandidateRequest): Observable<Candidate> {
    const { version: _ignored, ...body } = request;
    return this.http.post<Candidate>(this.baseUrl, body).pipe(catchError((e) => this.rethrow(e)));
  }

  getById(id: string): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.baseUrl}/${id}`).pipe(catchError((e) => this.rethrow(e)));
  }

  update(id: string, request: CandidateRequest): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.baseUrl}/${id}`, request).pipe(catchError((e) => this.rethrow(e)));
  }

  private rethrow(error: HttpErrorResponse): Observable<never> {
    const problem = this.isProblemDetail(error.error) ? (error.error as ProblemDetail) : null;
    return throwError(() => new CandidateApiError(error.status, problem));
  }

  private isProblemDetail(body: unknown): body is ProblemDetail {
    return !!body && typeof body === 'object' && 'status' in body && 'type' in body;
  }
}
