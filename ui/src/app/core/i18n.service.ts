import { Injectable } from '@angular/core';
import en from '../../i18n/en.json';

/**
 * Every user-facing label lives in src/i18n/en.json (candidate spec, non-functional
 * requirements). This service is the only place that reads it.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly labels: unknown = en;

  /** Looks up a dot-path (e.g. "candidate.form.save") and interpolates {placeholders}. */
  t(path: string, params?: Record<string, string | number>): string {
    const value = path.split('.').reduce<unknown>((node, key) => {
      if (node && typeof node === 'object' && key in (node as Record<string, unknown>)) {
        return (node as Record<string, unknown>)[key];
      }
      return undefined;
    }, this.labels);

    if (typeof value !== 'string') {
      return path;
    }
    if (!params) {
      return value;
    }
    return Object.entries(params).reduce(
      (text, [key, val]) => text.replaceAll(`{${key}}`, String(val)),
      value,
    );
  }
}
