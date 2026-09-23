import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * A reusable chip input: Enter or comma adds a chip, trimmed and checked for a
 * case-insensitive duplicate. Shared by any feature with a "skills"-shaped field
 * (candidate spec: identical rule to job_posting.skills).
 */
@Component({
  selector: 'app-chip-input',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chip-input.html',
  styleUrl: './chip-input.scss',
})
export class ChipInput {
  readonly chips = input<string[]>([]);
  readonly placeholder = input<string>('');
  readonly duplicateMessage = input<string>('Duplicate value');
  readonly inputId = input<string>('chip-input');
  readonly describedBy = input<string | null>(null);

  readonly chipsChange = output<string[]>();

  protected readonly draft = signal('');
  protected readonly duplicateError = signal(false);

  protected readonly hasDuplicateError = computed(() => this.duplicateError());

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.commitDraft();
    } else if (event.key === 'Backspace' && this.draft() === '' && this.chips().length > 0) {
      this.removeChip(this.chips().length - 1);
    }
  }

  /**
   * Blur does NOT commit the draft as a chip — only Enter/comma does. Otherwise tabbing
   * away (e.g. to the Cancel button) would silently add whatever was half-typed.
   */
  onBlur(): void {
    this.draft.set('');
    this.duplicateError.set(false);
  }

  removeChip(index: number): void {
    const next = this.chips().slice();
    next.splice(index, 1);
    this.chipsChange.emit(next);
  }

  private commitDraft(): void {
    const value = this.draft().trim();
    this.draft.set('');
    if (!value) {
      return;
    }
    const isDuplicate = this.chips().some((c) => c.toLowerCase() === value.toLowerCase());
    if (isDuplicate) {
      this.duplicateError.set(true);
      return;
    }
    this.duplicateError.set(false);
    this.chipsChange.emit([...this.chips(), value]);
  }
}
