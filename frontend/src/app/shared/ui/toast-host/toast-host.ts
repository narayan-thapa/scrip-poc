import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from '../../../core/notification/notification.service';

/** Renders transient toasts from {@link NotificationService}. Mounted once in the shell. */
@Component({
  selector: 'app-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-host" aria-live="polite" aria-atomic="false">
      @for (toast of notifications.toasts(); track toast.id) {
        <div class="toast" [class]="toast.level" role="status">
          <span>{{ toast.message }}</span>
          <button type="button" aria-label="Dismiss" (click)="notifications.dismiss(toast.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toast-host {
        position: fixed; bottom: 1rem; right: 1rem; display: flex; flex-direction: column;
        gap: 0.5rem; z-index: 1000; max-width: min(90vw, 24rem);
      }
      .toast {
        display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
        padding: 0.625rem 0.875rem; border-radius: var(--radius); color: #fff;
        box-shadow: var(--shadow); font-size: 0.875rem;
      }
      .toast.info { background: var(--color-accent); }
      .toast.success { background: var(--color-up); }
      .toast.error { background: var(--color-down); }
      .toast button { background: transparent; border: 0; color: inherit; cursor: pointer; font-size: 1.1rem; }
    `,
  ],
})
export class ToastHost {
  readonly notifications = inject(NotificationService);
}
