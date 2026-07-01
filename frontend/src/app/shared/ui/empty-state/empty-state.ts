import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Neutral placeholder for "no data" panels. Title + optional hint. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <p class="title">{{ title() }}</p>
      @if (hint()) {
        <p class="hint">{{ hint() }}</p>
      }
    </div>
  `,
  styles: [
    `
      .empty { text-align: center; padding: 2rem 1rem; color: var(--color-text-muted); }
      .title { margin: 0; font-weight: 600; }
      .hint { margin: 0.25rem 0 0; font-size: 0.875rem; }
    `,
  ],
})
export class EmptyState {
  readonly title = input('Nothing here yet');
  readonly hint = input<string | null>(null);
}
