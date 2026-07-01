import { httpResource, HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { StrategyConfig } from '../../../core/api/models';
import { NotificationService } from '../../../core/notification/notification.service';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';

/** Strategy configs with inline weight/enabled tuning (ANALYST/ADMIN; the API enforces the role). */
@Component({
  selector: 'app-strategies',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, ErrorState],
  template: `
    <header class="page-head"><h1>Strategies</h1></header>
    @if (strategies.isLoading()) {
      <app-spinner label="Loading strategies" />
    } @else if (strategies.error()) {
      <app-error-state title="Couldn't load strategies" (retry)="strategies.reload()" />
    } @else {
      <table class="data-table">
        <thead><tr><th>ID</th><th>Name</th><th>Weight</th><th>Enabled</th></tr></thead>
        <tbody>
          @for (s of strategies.value() ?? []; track s.id) {
            <tr>
              <td>{{ s.id }}</td>
              <td>{{ s.name }}</td>
              <td>
                <input type="number" step="0.1" min="0" [value]="s.weight"
                       (change)="patch(s.id, { weight: +$any($event.target).value })" />
              </td>
              <td>
                <input type="checkbox" [checked]="s.enabled"
                       (change)="patch(s.id, { enabled: $any($event.target).checked })" />
              </td>
            </tr>
          }
        </tbody>
      </table>
      <p class="muted">Changes apply to the next signal run. Tuning requires the ANALYST or ADMIN role.</p>
    }
  `,
  styles: [
    `
      input[type='number'] { width: 5rem; padding: var(--space-1); border: 1px solid var(--color-border);
        border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); }
      .muted { color: var(--color-text-muted); font-size: 0.875rem; }
    `,
  ],
})
export class Strategies {
  private readonly http = inject(HttpClient);
  private readonly notify = inject(NotificationService);

  protected readonly strategies = httpResource<StrategyConfig[]>(
    () => `${environment.apiBaseUrl}/api/v1/strategies`,
  );

  patch(id: string, body: { weight?: number; enabled?: boolean }): void {
    this.http.patch(`${environment.apiBaseUrl}/api/v1/strategies/${id}`, body).subscribe({
      next: () => this.notify.success(`Updated ${id}`),
      error: () => this.strategies.reload(),
    });
  }
}
