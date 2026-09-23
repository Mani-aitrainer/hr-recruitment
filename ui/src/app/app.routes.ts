import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'candidates',
    loadChildren: () => import('./features/candidate/candidate.routes').then((m) => m.CANDIDATE_ROUTES),
  },
  { path: '', redirectTo: 'candidates/new', pathMatch: 'full' },
];
