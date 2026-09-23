import { Routes } from '@angular/router';
import { unsavedCandidateChangesGuard } from './form/unsaved-changes.guard';

export const CANDIDATE_ROUTES: Routes = [
  {
    path: 'new',
    loadComponent: () => import('./form/candidate-form').then((m) => m.CandidateFormComponent),
    canDeactivate: [unsavedCandidateChangesGuard],
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./form/candidate-form').then((m) => m.CandidateFormComponent),
    canDeactivate: [unsavedCandidateChangesGuard],
  },
];
