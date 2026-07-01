import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { SignalSummary } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';

/** Latest run's BUY/SELL/HOLD list, filterable by action. */
@Component({
  selector: 'app-signals-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, EmptyState],
  template: `
    <header class="page-head">
      <h1>Signals <span class="muted">(latest run)</span></h1>
      <div class="filters">
        <select [value]="action()" (change)="action.set($any($event.target).value)">
          <option value="">All actions</option>
          <option value="BUY">Buy</option>
          <option value="SELL">Sell</option>
          <option value="HOLD">Hold</option>
        </select>
      </div>
    </header>

    @if (signals.isLoading()) {
      <app-spinner label="Loading signals" />
    } @else if (signals.error()) {
      <app-error-state title="Couldn't load signals" (retry)="signals.reload()" />
    } @else if ((signals.value() ?? []).length === 0) {
      <app-empty-state title="No signals yet" hint="Run the pipeline to generate signals." />
    } @else {
      <table class="data-table">
        <thead><tr><th>Symbol</th><th>Action</th><th>Score</th><th>Confidence</th><th></th></tr></thead>
        <tbody>
          @for (s of signals.value() ?? []; track s.id) {
            <tr>
              <td><a [routerLink]="['/market', s.symbol]">{{ s.symbol }}</a></td>
              <td><span class="action" [class]="s.action.toLowerCase()">{{ s.action }}</span></td>
              <td class="numeric" [class.up]="s.score > 0" [class.down]="s.score < 0">{{ s.score.toFixed(1) }}</td>
              <td class="numeric">{{ (s.confidence * 100).toFixed(0) }}%</td>
              <td><a [routerLink]="['/signals', s.id]">Why →</a></td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [
    `
      .muted { color: var(--color-text-muted); font-weight: 400; font-size: 0.9rem; }
      .action { font-size: 0.75rem; font-weight: 700; padding: 1px var(--space-2); border-radius: var(--radius-sm); color: #fff; }
      .action.buy { background: var(--color-up); }
      .action.sell { background: var(--color-down); }
      .action.hold { background: var(--color-text-muted); }
    `,
  ],
})
export class SignalsOverview {
  protected readonly action = signal('');

  protected readonly signals = httpResource<SignalSummary[]>(() => {
    const a = this.action();
    return `${environment.apiBaseUrl}/api/v1/signals/latest${a ? `?action=${a}` : ''}`;
  });
}
