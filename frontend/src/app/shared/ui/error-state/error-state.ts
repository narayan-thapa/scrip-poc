import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Error panel with an optional retry action, used for failed resource loads. */
@Component({
  selector: 'app-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error" role="alert">
      <p class="title">{{ title() }}</p>
      @if (detail()) {
        <p class="detail">{{ detail() }}</p>
      }
      @if (retryable()) {
        <button type="button" (click)="retry.emit()">Retry</button>
      }
    </div>
  `,
  styles: [
    `
      .error {
        text-align: center; padding: 1.5rem 1rem; border: 1px solid var(--color-down);
        border-radius: var(--radius); background: color-mix(in srgb, var(--color-down) 8%, transparent);
      }
      .title { margin: 0; font-weight: 600; color: var(--color-down); }
      .detail { margin: 0.25rem 0 0.75rem; font-size: 0.875rem; color: var(--color-text-muted); }
    `,
  ],
})
export class ErrorState {
  readonly title = input('Something went wrong');
  readonly detail = input<string | null>(null);
  readonly retryable = input(true);
  readonly retry = output<void>();
}
