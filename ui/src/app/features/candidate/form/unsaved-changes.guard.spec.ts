import { vi } from 'vitest';
import { unsavedCandidateChangesGuard } from './unsaved-changes.guard';
import { CandidateFormComponent } from './candidate-form';

describe('unsavedCandidateChangesGuard', () => {
  function fakeComponent(dirty: boolean): CandidateFormComponent {
    return {
      hasUnsavedChanges: () => dirty,
      i18n: { t: (key: string) => key },
    } as unknown as CandidateFormComponent;
  }

  // ---- positive: navigating away with a clean form needs no confirmation ----
  it('allows navigation without prompting when there are no unsaved changes', () => {
    const spy = vi.spyOn(window, 'confirm');
    const result = unsavedCandidateChangesGuard(fakeComponent(false), {} as never, {} as never, {} as never);

    expect(result).toBe(true);
    expect(spy).not.toHaveBeenCalled();
  });

  // ---- negative: a dirty form prompts, and a cancelled prompt blocks navigation ----
  it('prompts and blocks navigation when the form is dirty and the user cancels', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const result = unsavedCandidateChangesGuard(fakeComponent(true), {} as never, {} as never, {} as never);

    expect(result).toBe(false);
  });

  it('prompts and allows navigation when the form is dirty and the user confirms', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const result = unsavedCandidateChangesGuard(fakeComponent(true), {} as never, {} as never, {} as never);

    expect(result).toBe(true);
  });
});
