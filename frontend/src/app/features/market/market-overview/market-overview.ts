import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Mover, MoversDto, SummaryDto } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';

/** Whole-market day view: breadth + totals, and movers (gainers / losers / most active). */
@Component({
  selector: 'app-market-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState],
  template: `
    <header class="page-head">
      <h1>Market overview</h1>
      <div class="filters">
        <label>Date <input type="date" [value]="date()" (change)="date.set($any($event.target).value)" /></label>
      </div>
    </header>

    @if (summary.isLoading()) {
      <app-spinner label="Loading summary" />
    } @else if (summary.error()) {
      <app-error-state title="No market summary for this date" [retryable]="false" />
    } @else {
      @let s = summary.value();
      <section class="breadth">
        <div class="stat up"><span>Advances</span><strong>{{ s?.advances }}</strong></div>
        <div class="stat down"><span>Declines</span><strong>{{ s?.declines }}</strong></div>
        <div class="stat"><span>Unchanged</span><strong>{{ s?.unchanged }}</strong></div>
        <div class="stat"><span>Turnover</span><strong class="numeric">{{ s?.totalTurnover }}</strong></div>
        <div class="stat"><span>Volume</span><strong class="numeric">{{ s?.totalVolume }}</strong></div>
        <div class="stat"><span>Trades</span><strong class="numeric">{{ s?.totalTrades }}</strong></div>
      </section>
    }

    @if (movers.value(); as m) {
      <div class="mover-grid">
        @for (panel of panels(m); track panel.title) {
          <section class="card">
            <h2>{{ panel.title }}</h2>
            <table class="data-table">
              <thead><tr><th>Symbol</th><th>Close</th><th>Δ%</th><th>Volume</th></tr></thead>
              <tbody>
                @for (row of panel.rows; track row.symbol) {
                  <tr>
                    <td><a [routerLink]="['/market', row.symbol]">{{ row.symbol }}</a></td>
                    <td class="numeric">{{ row.close }}</td>
                    <td class="numeric" [class.up]="(row.changePct ?? 0) > 0" [class.down]="(row.changePct ?? 0) < 0">
                      {{ row.changePct ?? '—' }}
                    </td>
                    <td class="numeric">{{ row.volume }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      </div>
    }
  `,
  styles: [
    `
      .breadth { display: flex; gap: var(--space-4); flex-wrap: wrap; margin-bottom: var(--space-8); }
      .stat { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-3) var(--space-4); display: flex; flex-direction: column; min-width: 7rem; }
      .stat span { color: var(--color-text-muted); font-size: 0.8rem; }
      .stat strong { font-size: 1.25rem; }
      .mover-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); gap: var(--space-6); }
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); }
      .card h2 { margin: 0 0 var(--space-2); font-size: 1rem; }
    `,
  ],
})
export class MarketOverview {
  protected readonly date = signal(new Date().toISOString().slice(0, 10));

  protected readonly summary = httpResource<SummaryDto>(
    () => `${environment.apiBaseUrl}/api/v1/market/summary?date=${this.date()}`,
  );

  protected readonly movers = httpResource<MoversDto>(
    () => `${environment.apiBaseUrl}/api/v1/market/movers?date=${this.date()}`,
  );

  protected panels(m: MoversDto): { title: string; rows: Mover[] }[] {
    return [
      { title: 'Top gainers', rows: m.gainers },
      { title: 'Top losers', rows: m.losers },
      { title: 'Most active (volume)', rows: m.mostActiveByVolume },
      { title: 'Most active (turnover)', rows: m.mostActiveByTurnover },
    ];
  }
}
