import { TestBed } from '@angular/core/testing';
import { ChipInput } from './chip-input';

describe('ChipInput', () => {
  it('emits a new chip list when a chip is added via Enter', () => {
    const fixture = TestBed.createComponent(ChipInput);
    fixture.componentRef.setInput('chips', ['Java']);
    fixture.detectChanges();

    let emitted: string[] | undefined;
    fixture.componentInstance.chipsChange.subscribe((v) => (emitted = v));
    fixture.componentInstance['draft'].set('SQL');
    fixture.componentInstance.onKeydown({ key: 'Enter', preventDefault: () => {} } as KeyboardEvent);

    expect(emitted).toEqual(['Java', 'SQL']);
  });

  // ---- CAND-5: negative, case-insensitive duplicate is rejected ----
  it('CAND-5 rejects a case-insensitive duplicate chip and does not emit', () => {
    const fixture = TestBed.createComponent(ChipInput);
    fixture.componentRef.setInput('chips', ['Java']);
    fixture.detectChanges();

    let emitted: string[] | undefined;
    fixture.componentInstance.chipsChange.subscribe((v) => (emitted = v));
    fixture.componentInstance['draft'].set('java');
    fixture.componentInstance.onKeydown({ key: 'Enter', preventDefault: () => {} } as KeyboardEvent);

    expect(emitted).toBeUndefined();
    expect(fixture.componentInstance['hasDuplicateError']()).toBe(true);
  });

  it('removes a chip when its remove button is used', () => {
    const fixture = TestBed.createComponent(ChipInput);
    fixture.componentRef.setInput('chips', ['Java', 'SQL']);
    fixture.detectChanges();

    let emitted: string[] | undefined;
    fixture.componentInstance.chipsChange.subscribe((v) => (emitted = v));
    fixture.componentInstance.removeChip(0);

    expect(emitted).toEqual(['SQL']);
  });
});
