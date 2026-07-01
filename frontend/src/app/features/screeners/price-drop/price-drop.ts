import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PriceDropRow } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';

/** F12 — Sharp price-drop screener: window presets 30/45/60 + custom, three drop lenses. */
@Component({
  selector: 'app-price-drop',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, EmptyState],
  template: `
    <header class="page-head">
      <h1>Sharp price-drop screener</h1>
    </header>

    <div class="controls">
      <div class="presets">
        Window:
        @for (w of [30, 45, 60]; track w) {
          <button type="button" [class.active]="window() === w" (click)="window.set(w)">{{ w }}d</button>
        }
        <input type="number" min="5" max="365" [value]="window()" (change)="window.set(+$any($event.target).value)" />
      </div>
      <label>Metric
        <select [value]="metric()" (change)="metric.set($any($event.target).value)">
          <option value="pctchange">% change</option>
          <option value="drawdown">Drawdown from high</option>
          <option value="sharpness">Sharpness</option>
        </select>
      </label>
    </div>

    @if (rows.isLoading()) {
      <app-spinner label="Scanning" />
    } @else if (rows.error()) {
      <app-error-state title="Couldn't run the screener" (retry)="rows.reload()" />
    } @else if ((rows.value() ?? []).length === 0) {
      <app-empty-state title="No scrips match" hint="Try a wider window." />
    } @else {
      <table class="data-table">
        <thead>
          <tr><th>Symbol</th><th>Close</th><th>Δ% ({{ window() }}d)</th><th>Drawdown</th><th>Sharpness</th>
            <th>RVOL</th><th>Signal</th></tr>
        </thead>
        <tbody>
          @for (r of rows.value() ?? []; track r.symbol) {
            <tr>
              <td>
                <a [routerLink]="['/market', r.symbol]">{{ r.symbol }}</a>
                @if (r.insufficientHistory) { <span class="tag">insufficient history</span> }
              </td>
              <td class="numeric">{{ r.close }}</td>
              <td class="numeric down">{{ r.pctChange }}%</td>
              <td class="numeric down">{{ r.drawdownFromHigh }}%</td>
              <td class="numeric">{{ r.sharpness }}</td>
              <td class="numeric">{{ r.rvolRatio }}×</td>
              <td>@if (r.signal) { <span class="chip" [class]="r.signal.action.toLowerCase()">{{ r.signal.action }}</span> }</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [
    `
      .controls { display: flex; gap: var(--space-6); align-items: flex-end; margin-bottom: var(--space-4); flex-wrap: wrap; }
      .presets { display: flex; align-items: center; gap: var(--space-2); }
      .presets button { padding: var(--space-1) var(--space-3); }
      .presets button.active { background: var(--color-accent); color: #fff; border-color: var(--color-accent); }
      .presets input { width: 5rem; padding: var(--space-1) var(--space-2); border: 1px solid var(--color-border);
        border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); }
      label { display: flex; flex-direction: column; font-size: 0.8rem; color: var(--color-text-muted); gap: 0.15rem; }
      select { padding: var(--space-1) var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
        background: var(--color-surface); color: var(--color-text); }
      .chip { font-size: 0.7rem; font-weight: 700; padding: 1px var(--space-2); border-radius: var(--radius-sm); color: #fff; }
      .chip.buy { background: var(--color-up); } .chip.sell { background: var(--color-down); } .chip.hold { background: var(--color-text-muted); }
    `,
  ],
})
export class PriceDrop {
  protected readonly window = signal(30);
  protected readonly metric = signal('pctchange');

  protected readonly rows = httpResource<PriceDropRow[]>(
    () => `${environment.apiBaseUrl}/api/v1/screener/price-drop?window=${this.window()}&metric=${this.metric()}`,
  );
}
