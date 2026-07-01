import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BacktestTrade, RunDetail } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EquityChart } from '../equity-chart/equity-chart';

/** Backtest result: metric cards, equity/drawdown chart, and the trade log with reasons. */
@Component({
  selector: 'app-backtest-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, EquityChart],
  template: `
    <p><a routerLink="/backtests">← Backtests</a></p>
    @if (detail.isLoading()) {
      <app-spinner label="Loading" />
    } @else if (detail.error()) {
      <app-error-state title="Run not found" [retryable]="false" />
    } @else if (detail.value(); as d) {
      <header class="page-head"><h1>{{ d.run.symbol }} <span class="muted">{{ d.run.from }} → {{ d.run.to }}</span></h1></header>

      <section class="metrics">
        @for (m of metricList(); track m.key) {
          <div class="metric">
            <span>{{ m.label }}</span>
            <strong class="numeric" [class.up]="m.good === 1" [class.down]="m.good === -1">{{ m.value }}</strong>
          </div>
        }
      </section>

      <section class="card"><app-equity-chart [runId]="id()" /></section>

      <section class="card">
        <h2>Trades</h2>
        @if ((trades.value() ?? []).length === 0) {
          <p class="muted">No trades in this run.</p>
        } @else {
          <table class="data-table">
            <thead><tr><th>Entry</th><th>Exit</th><th>Qty</th><th>Costs</th><th>P&L</th><th>Return</th></tr></thead>
            <tbody>
              @for (t of trades.value() ?? []; track t.entryDate + t.exitDate) {
                <tr>
                  <td>{{ t.entryDate }} &#64; {{ t.entryPrice }}</td>
                  <td>{{ t.exitDate }} &#64; {{ t.exitPrice }}</td>
                  <td class="numeric">{{ t.quantity }}</td>
                  <td class="numeric">{{ t.costs }}</td>
                  <td class="numeric" [class.up]="t.pnl > 0" [class.down]="t.pnl < 0">{{ t.pnl }}</td>
                  <td class="numeric" [class.up]="t.returnPct > 0" [class.down]="t.returnPct < 0">{{ t.returnPct }}%</td>
                </tr>
              }
            </tbody>
          </table>
        }
      </section>
    }
  `,
  styles: [
    `
      .muted { color: var(--color-text-muted); font-weight: 400; font-size: 0.9rem; }
      .metrics { display: grid; grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr)); gap: var(--space-3);
        margin-bottom: var(--space-6); }
      .metric { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-3); display: flex; flex-direction: column; }
      .metric span { color: var(--color-text-muted); font-size: 0.75rem; }
      .metric strong { font-size: 1.15rem; }
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); margin-bottom: var(--space-6); }
      .card h2 { margin: 0 0 var(--space-3); font-size: 1rem; }
    `,
  ],
})
export class BacktestDetail {
  readonly id = input.required<string>();

  protected readonly detail = httpResource<RunDetail>(
    () => `${environment.apiBaseUrl}/api/v1/backtests/${this.id()}`,
  );
  protected readonly trades = httpResource<BacktestTrade[]>(
    () => `${environment.apiBaseUrl}/api/v1/backtests/${this.id()}/trades`,
  );

  protected readonly metricList = computed(() => {
    const m = this.detail.value()?.metrics ?? {};
    const fmt = (v: number | undefined) => (v == null ? '—' : v.toLocaleString());
    return [
      { key: 'totalReturnPct', label: 'Total return %', value: fmt(m['totalReturnPct']), good: sign(m['totalReturnPct']) },
      { key: 'cagrPct', label: 'CAGR %', value: fmt(m['cagrPct']), good: sign(m['cagrPct']) },
      { key: 'maxDrawdownPct', label: 'Max drawdown %', value: fmt(m['maxDrawdownPct']), good: -1 },
      { key: 'winRatePct', label: 'Win rate %', value: fmt(m['winRatePct']), good: 0 },
      { key: 'profitFactor', label: 'Profit factor', value: fmt(m['profitFactor']), good: 0 },
      { key: 'sortino', label: 'Sortino', value: fmt(m['sortino']), good: 0 },
      { key: 'exposurePct', label: 'Exposure %', value: fmt(m['exposurePct']), good: 0 },
      { key: 'numTrades', label: 'Trades', value: fmt(m['numTrades']), good: 0 },
    ];
  });
}

function sign(v: number | undefined): number {
  return v == null ? 0 : v > 0 ? 1 : v < 0 ? -1 : 0;
}
