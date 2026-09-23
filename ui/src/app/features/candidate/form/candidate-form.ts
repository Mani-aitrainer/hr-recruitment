import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ChipInput } from '../../../shared/chip-input/chip-input';
import { I18nService } from '../../../core/i18n.service';
import { CandidateApiError, CandidateService } from '../data/candidate.service';
import { Candidate, CandidateRequest, HIGHEST_QUALIFICATIONS, HighestQualification } from '../data/candidate.model';

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const PHONE_PATTERN = /^\+?[0-9 -]+$/;

/** One state machine covers both /candidates/new and /candidates/:id/edit (candidate spec, Screens). */
type Mode = 'new' | 'edit';
type LoadState = 'idle' | 'loading' | 'not-found' | 'error' | 'ready';
type SaveState = 'idle' | 'saving' | 'saved' | 'version-conflict';

@Component({
  selector: 'app-candidate-form',
  standalone: true,
  imports: [ReactiveFormsModule, ChipInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './candidate-form.html',
  styleUrl: './candidate-form.scss',
})
export class CandidateFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CandidateService);
  /** Public: read by unsaved-changes.guard.ts, which lives outside this component. */
  readonly i18n = inject(I18nService);

  protected readonly qualifications = HIGHEST_QUALIFICATIONS;

  protected readonly mode = signal<Mode>('new');
  protected readonly loadState = signal<LoadState>('idle');
  protected readonly saveState = signal<SaveState>('idle');
  protected readonly candidateId = signal<string | null>(null);
  protected readonly loadedVersion = signal<number | null>(null);
  protected readonly savedCandidateName = signal<string | null>(null);
  protected readonly formTouchedOrSubmitted = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(EMAIL_PATTERN)]],
    phone: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(20), Validators.pattern(PHONE_PATTERN)]],
    location: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    currentEmployer: ['', [Validators.minLength(2), Validators.maxLength(100)]],
    currentTitle: ['', [Validators.minLength(2), Validators.maxLength(100)]],
    totalExperienceYears: this.fb.nonNullable.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(40),
    ]),
    noticePeriodDays: this.fb.nonNullable.control<number | null>(null, [Validators.min(0), Validators.max(180)]),
    highestQualification: this.fb.nonNullable.control<HighestQualification | ''>('', [Validators.required]),
    skills: this.fb.nonNullable.control<string[]>([], [Validators.required, this.skillsCountValidator]),
    summary: ['', [Validators.maxLength(2000)]],
  });

  protected readonly summaryLength = computed(() => this.form.controls.summary.value?.length ?? 0);
  protected readonly isEditMode = computed(() => this.mode() === 'edit');
  protected readonly serverErrors = signal<Record<string, string>>({});

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.mode.set('edit');
      this.candidateId.set(id);
      this.loadCandidate(id);
    } else {
      this.loadState.set('ready');
    }

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.formTouchedOrSubmitted.set(true));
  }

  /** Public: read by unsaved-changes.guard.ts, which lives outside this component. */
  hasUnsavedChanges(): boolean {
    return this.formTouchedOrSubmitted();
  }

  private skillsCountValidator(control: { value: string[] }) {
    const value = control.value ?? [];
    return value.length >= 1 && value.length <= 15 ? null : { skillsCount: true };
  }

  protected loadCandidate(id: string): void {
    this.loadState.set('loading');
    this.service.getById(id).subscribe({
      next: (candidate) => this.applyCandidate(candidate),
      error: (err: unknown) => {
        if (err instanceof CandidateApiError && err.status === 404) {
          this.loadState.set('not-found');
        } else {
          this.loadState.set('error');
        }
      },
    });
  }

  private applyCandidate(candidate: Candidate): void {
    this.loadedVersion.set(candidate.version);
    this.form.patchValue({
      fullName: candidate.fullName,
      email: candidate.email,
      phone: candidate.phone,
      location: candidate.location,
      currentEmployer: candidate.currentEmployer ?? '',
      currentTitle: candidate.currentTitle ?? '',
      totalExperienceYears: candidate.totalExperienceYears,
      noticePeriodDays: candidate.noticePeriodDays,
      highestQualification: candidate.highestQualification,
      skills: candidate.skills,
      summary: candidate.summary ?? '',
    });
    this.formTouchedOrSubmitted.set(false);
    this.loadState.set('ready');
  }

  protected onSkillsChange(skills: string[]): void {
    this.form.controls.skills.setValue(skills);
    this.form.controls.skills.markAsTouched();
  }

  protected retry(): void {
    const id = this.candidateId();
    if (id) {
      this.loadCandidate(id);
    }
  }

  protected reloadForVersionConflict(): void {
    const id = this.candidateId();
    if (id) {
      this.saveState.set('idle');
      this.loadCandidate(id);
    }
  }

  protected cancel(): void {
    if (this.mode() === 'new') {
      this.form.reset({
        fullName: '',
        email: '',
        phone: '',
        location: '',
        currentEmployer: '',
        currentTitle: '',
        totalExperienceYears: null,
        noticePeriodDays: null,
        highestQualification: '',
        skills: [],
        summary: '',
      });
      this.formTouchedOrSubmitted.set(false);
    } else {
      const id = this.candidateId();
      if (id) {
        this.loadCandidate(id);
      }
    }
  }

  protected submit(): void {
    this.formTouchedOrSubmitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const request: CandidateRequest = {
      fullName: raw.fullName,
      email: raw.email,
      phone: raw.phone,
      location: raw.location,
      currentEmployer: raw.currentEmployer || null,
      currentTitle: raw.currentTitle || null,
      totalExperienceYears: raw.totalExperienceYears as number,
      noticePeriodDays: raw.noticePeriodDays,
      highestQualification: (raw.highestQualification || null) as HighestQualification | null,
      skills: raw.skills,
      summary: raw.summary || null,
    };

    this.saveState.set('saving');
    this.serverErrors.set({});

    if (this.mode() === 'new') {
      this.service.create(request).subscribe({
        next: (created) => this.handleCreated(created),
        error: (err: unknown) => this.handleSaveError(err),
      });
    } else {
      const id = this.candidateId();
      const version = this.loadedVersion();
      if (!id || version === null) return;
      this.service.update(id, { ...request, version }).subscribe({
        next: (updated) => this.handleUpdated(updated),
        error: (err: unknown) => this.handleSaveError(err),
      });
    }
  }

  private handleCreated(created: Candidate): void {
    this.saveState.set('saved');
    this.savedCandidateName.set(created.fullName);
    this.cancel();
  }

  private handleUpdated(updated: Candidate): void {
    this.saveState.set('saved');
    this.loadedVersion.set(updated.version);
    this.formTouchedOrSubmitted.set(false);
  }

  private handleSaveError(err: unknown): void {
    if (!(err instanceof CandidateApiError)) {
      this.saveState.set('idle');
      return;
    }
    if (err.status === 409 && err.problem?.type.endsWith('version-conflict')) {
      this.saveState.set('version-conflict');
      return;
    }
    if (err.status === 400 && err.problem?.errors) {
      const fieldErrors: Record<string, string> = {};
      for (const fieldError of err.problem.errors) {
        fieldErrors[fieldError.field] = fieldError.message;
        const control = this.form.get(fieldError.field);
        control?.setErrors({ server: fieldError.message });
        control?.markAsTouched();
      }
      this.serverErrors.set(fieldErrors);
    }
    this.saveState.set('idle');
  }
}
