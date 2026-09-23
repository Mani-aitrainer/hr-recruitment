import { CanDeactivateFn } from '@angular/router';
import { CandidateFormComponent } from './candidate-form';

/**
 * Warns about unsaved changes when navigating away (candidate spec, Screens).
 * `hasUnsavedChanges()` is true once the form has been edited and not yet saved.
 */
export const unsavedCandidateChangesGuard: CanDeactivateFn<CandidateFormComponent> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }
  return confirm(component.i18n.t('candidate.form.unsavedChangesWarning'));
};
