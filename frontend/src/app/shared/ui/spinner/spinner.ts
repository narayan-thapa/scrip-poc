import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Inline loading indicator. Used wherever a resource is `isLoading()`. */
@Component({
  selector: 'app-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="spinner" role="status" [attr.aria-label]="label()">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
    </span>
  `,
  styles: [
    `
      .spinner { display: inline-flex; gap: 0.25rem; align-items: center; }
      .dot {
        width: 0.45rem; height: 0.45rem; border-radius: 50%;
        background: var(--color-accent); animation: pulse 1s infinite ease-in-out;
      }
      .dot:nth-child(2) { animation-delay: 0.15s; }
      .dot:nth-child(3) { animation-delay: 0.3s; }
      @keyframes pulse { 0%, 80%, 100% { opacity: 0.3; } 40% { opacity: 1; } }
    `,
  ],
})
export class Spinner {
  readonly label = input('Loading');
}
