import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ErrorState } from '../../shared/ui/error-state/error-state';

function iso(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Trading-calendar viewer: pick a range, list the open NEPSE trading days within it. */
@Component({
  selector: 'app-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, ErrorState],
  template: `
    <header class="page-head">
      <h1>Trading calendar</h1>
      <div class="filters">
        <label>From <input type="date" [value]="from()" (change)="from.set($any($event.target).value)" /></label>
        <label>To <input type="date" [value]="to()" (change)="to.set($any($event.target).value)" /></label>
      </div>
    </header>

    @if (days.isLoading()) {
      <app-spinner label="Loading calendar" />
    } @else if (days.error()) {
      <app-error-state title="Couldn't load trading days" (retry)="days.reload()" />
    } @else {
      <p class="summary">{{ (days.value() ?? []).length }} trading days in range.</p>
      <ul class="day-grid">
        @for (d of days.value() ?? []; track d) {
          <li class="numeric">{{ d }}</li>
        }
      </ul>
    }
  `,
  styles: [
    `
      .summary { color: var(--color-text-muted); }
      .filters label { display: inline-flex; gap: var(--space-1); align-items: center; font-size: 0.875rem; }
      .day-grid { list-style: none; padding: 0; display: grid;
        grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr)); gap: var(--space-2); }
      .day-grid li { padding: var(--space-2); text-align: center; background: var(--color-surface);
        border: 1px solid var(--color-border); border-radius: var(--radius-sm);
        color: var(--color-up); }
    `,
  ],
})
export class Calendar {
  private static monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  private static monthEnd = new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0);

  protected readonly from = signal(iso(Calendar.monthStart));
  protected readonly to = signal(iso(Calendar.monthEnd));

  protected readonly days = httpResource<string[]>(() => {
    if (!this.from() || !this.to()) return undefined;
    return `${environment.apiBaseUrl}/api/v1/calendar/trading-days?from=${this.from()}&to=${this.to()}`;
  });
}
