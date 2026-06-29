import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { API, BacktestApi } from '../core/api';
import { BacktestRunView, BacktestTradeView, EquityPointView, PageResponse } from '../core/models';

const W = 920;
const H = 220;
const PAD = 12;

@Component({
  selector: 'app-backtest-runner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe],
  template: `
    <div class="page-head">
      <h1>Backtests</h1>
      <p class="muted">Confluence model · signal-at-close / fill-next-open · NEPSE cost model</p>
    </div>

    <div class="card">
      <div class="row">
        <div class="field" style="flex:1; min-width:220px">
          <label>Symbols (comma-separated)</label>
          <input [value]="symbols()" (input)="symbols.set(asValue($event))" placeholder="NABIL, ADBL" />
        </div>
        <div class="field">
          <label>From</label>
          <input type="date" [value]="from()" (change)="from.set(asValue($event))" />
        </div>
        <div class="field">
          <label>To</label>
          <input type="date" [value]="to()" (change)="to.set(asValue($event))" />
        </div>
        <div class="field">
          <label>Capital</label>
          <input type="number" [value]="capital()" (input)="capital.set(+asValue($event))" />
        </div>
        <button (click)="run()" [disabled]="running() || !symbols().trim()">
          {{ running() ? 'Running…' : 'Run backtest' }}
        </button>
      </div>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }
    </div>

    @if (detail.value(); as r) {
      <div class="card">
        <h2>
          Result
          <span class="badge" [class.pos]="r.status === 'COMPLETED'" [class.neg]="r.status === 'FAILED'">
            {{ r.status }}
          </span>
        </h2>
        <p class="muted">{{ r.symbols.join(', ') }} · {{ r.dateFrom }} → {{ r.dateTo }}</p>
        @if (r.metrics; as m) {
          <div class="metric-grid">
            <div class="metric">
              <div class="label">Total return</div>
              <div class="value" [class.pos]="m.totalReturnPct > 0" [class.neg]="m.totalReturnPct < 0">
                {{ m.totalReturnPct | number: '1.1-1' }}%
              </div>
            </div>
            <div class="metric"><div class="label">CAGR</div><div class="value">{{ m.cagrPct | number: '1.1-1' }}%</div></div>
            <div class="metric"><div class="label">Sharpe</div><div class="value">{{ m.sharpe | number: '1.2-2' }}</div></div>
            <div class="metric"><div class="label">Sortino</div><div class="value">{{ m.sortino | number: '1.2-2' }}</div></div>
            <div class="metric"><div class="label">Max drawdown</div><div class="value neg">{{ m.maxDrawdownPct | number: '1.1-1' }}%</div></div>
            <div class="metric"><div class="label">Win rate</div><div class="value">{{ m.winRatePct | number: '1.0-0' }}%</div></div>
            <div class="metric"><div class="label">Profit factor</div><div class="value">{{ m.profitFactor | number: '1.2-2' }}</div></div>
            <div class="metric"><div class="label">Trades</div><div class="value">{{ m.tradeCount }}</div></div>
            <div class="metric"><div class="label">Total costs</div><div class="value">{{ m.totalCosts | number: '1.0-0' }}</div></div>
            <div class="metric"><div class="label">Final equity</div><div class="value">{{ m.finalEquity | number: '1.0-0' }}</div></div>
          </div>
        } @else if (r.errorMessage) {
          <p class="error">{{ r.errorMessage }}</p>
        }
      </div>

      @if (equityGeom(); as g) {
        <div class="card">
          <h2>Equity curve</h2>
          <svg [attr.viewBox]="'0 0 ' + g.width + ' ' + g.height" class="chart" role="img">
            <polyline [attr.points]="g.line" fill="none" stroke="#3fb950" stroke-width="1.5" />
          </svg>
        </div>
      }

      @if (trades.value().length) {
        <div class="card" style="padding:0">
          <table>
            <thead>
              <tr>
                <th>Symbol</th><th>Entry</th><th class="num">Entry px</th>
                <th>Exit</th><th class="num">Exit px</th><th class="num">Qty</th>
                <th class="num">P&amp;L</th><th class="num">Return</th><th>Exit reason</th>
              </tr>
            </thead>
            <tbody>
              @for (t of trades.value(); track $index) {
                <tr>
                  <td>{{ t.symbol }}</td>
                  <td>{{ t.entryDate | date: 'mediumDate' }}</td>
                  <td class="num">{{ t.entryPrice | number: '1.2-2' }}</td>
                  <td>{{ t.exitDate ? (t.exitDate | date: 'mediumDate') : '—' }}</td>
                  <td class="num">{{ t.exitPrice ? (t.exitPrice | number: '1.2-2') : '—' }}</td>
                  <td class="num">{{ t.quantity }}</td>
                  <td class="num" [class.pos]="t.pnl > 0" [class.neg]="t.pnl < 0">{{ t.pnl | number: '1.0-0' }}</td>
                  <td class="num" [class.pos]="t.returnPct > 0" [class.neg]="t.returnPct < 0">{{ t.returnPct | number: '1.1-1' }}%</td>
                  <td class="muted">{{ t.exitReason }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }

    <div class="card" style="padding:0">
      <table>
        <thead>
          <tr><th>Recent runs</th><th>Symbols</th><th>Range</th><th class="num">Return</th><th>Status</th></tr>
        </thead>
        <tbody>
          @for (r of runs.value().content; track r.id) {
            <tr class="clickable" (click)="selectedRunId.set(r.id)">
              <td>{{ r.createdAt | date: 'short' }}</td>
              <td>{{ r.symbols.join(', ') }}</td>
              <td>{{ r.dateFrom }} → {{ r.dateTo }}</td>
              <td class="num" [class.pos]="(r.metrics?.totalReturnPct ?? 0) > 0" [class.neg]="(r.metrics?.totalReturnPct ?? 0) < 0">
                {{ r.metrics ? (r.metrics.totalReturnPct | number: '1.1-1') + '%' : '—' }}
              </td>
              <td><span class="badge" [class.pos]="r.status === 'COMPLETED'" [class.neg]="r.status === 'FAILED'">{{ r.status }}</span></td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="muted">No runs yet.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .chart {
      width: 100%;
      height: auto;
      background: var(--surface-2);
      border-radius: 8px;
    }
  `,
})
export class BacktestRunner {
  private readonly api = inject(BacktestApi);

  protected readonly symbols = signal('');
  protected readonly from = signal(isoDaysAgo(365));
  protected readonly to = signal(isoDaysAgo(0));
  protected readonly capital = signal(1_000_000);
  protected readonly running = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedRunId = signal<string | null>(null);

  protected readonly runs = httpResource<PageResponse<BacktestRunView>>(() => `${API}/backtests?size=10`, {
    defaultValue: { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 },
  });

  protected readonly detail = httpResource<BacktestRunView>(() => {
    const id = this.selectedRunId();
    return id ? `${API}/backtests/${id}` : undefined;
  });

  protected readonly equity = httpResource<EquityPointView[]>(
    () => {
      const id = this.selectedRunId();
      return id ? `${API}/backtests/${id}/equity-curve` : undefined;
    },
    { defaultValue: [] },
  );

  protected readonly trades = httpResource<BacktestTradeView[]>(
    () => {
      const id = this.selectedRunId();
      return id ? `${API}/backtests/${id}/trades` : undefined;
    },
    { defaultValue: [] },
  );

  protected readonly equityGeom = computed(() => {
    const points = this.equity.value();
    if (!points || points.length < 2) {
      return null;
    }
    const values = points.map((p) => p.equity);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const n = points.length;
    const x = (i: number) => PAD + (i * (W - 2 * PAD)) / (n - 1);
    const y = (v: number) => (max === min ? H / 2 : PAD + ((max - v) * (H - 2 * PAD)) / (max - min));
    const line = points.map((p, i) => `${x(i)},${y(p.equity)}`).join(' ');
    return { width: W, height: H, line };
  });

  protected run(): void {
    this.running.set(true);
    this.error.set(null);
    const symbols = this.symbols()
      .split(/[,\s]+/)
      .map((s) => s.trim().toUpperCase())
      .filter((s) => s.length > 0);

    this.api.run({ symbols, from: this.from(), to: this.to(), startingCapital: this.capital() }).subscribe({
      next: (r) => {
        this.selectedRunId.set(r.id);
        this.runs.reload();
        this.running.set(false);
      },
      error: () => {
        this.error.set('Backtest failed. Check symbols and that the date range has data.');
        this.running.set(false);
      },
    });
  }

  protected asValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
