import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { API } from '../core/api';
import { ChartView, SignalAction } from '../core/models';

interface Marker {
  cx: number;
  cy: number;
  action: SignalAction;
}

interface Geometry {
  width: number;
  height: number;
  line: string;
  markers: Marker[];
}

const W = 920;
const H = 300;
const PAD = 12;
const COLORS: Record<SignalAction, string> = { BUY: '#3fb950', SELL: '#f85149', HOLD: '#d29922' };

@Component({
  selector: 'app-chart-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="page-head">
      <h1>Price chart</h1>
      <p class="muted">Composite payload: candles + signal markers + volume profile (one request)</p>
    </div>

    <div class="card">
      <div class="row">
        <div class="field">
          <label>Symbol</label>
          <input [value]="symbol()" (input)="symbol.set(asValue($event))" placeholder="e.g. NABIL" />
        </div>
        <div class="field">
          <label>From</label>
          <input type="date" [value]="from()" (change)="from.set(asValue($event))" />
        </div>
        <div class="field">
          <label>To</label>
          <input type="date" [value]="to()" (change)="to.set(asValue($event))" />
        </div>
        <button (click)="load()" [disabled]="!symbol().trim()">Load chart</button>
      </div>
    </div>

    @if (chart.isLoading()) {
      <div class="state">Loading…</div>
    } @else if (chart.error()) {
      <div class="state error">No data for that symbol/range.</div>
    } @else if (chart.value(); as c) {
      <div class="card">
        <h2>{{ c.symbol }} — {{ c.candles.length }} candles</h2>
        @if (geometry(); as g) {
          <svg [attr.viewBox]="'0 0 ' + g.width + ' ' + g.height" class="chart" role="img">
            <polyline [attr.points]="g.line" fill="none" stroke="#4493f8" stroke-width="1.5" />
            @for (m of g.markers; track $index) {
              <circle [attr.cx]="m.cx" [attr.cy]="m.cy" r="4" [attr.fill]="color(m.action)" />
            }
          </svg>
        } @else {
          <p class="muted">No candles in range.</p>
        }
        @if (c.volumeProfile; as vp) {
          <p class="muted">
            Volume profile — POC {{ vp.poc | number: '1.2-2' }} · VAH
            {{ vp.valueAreaHigh | number: '1.2-2' }} · VAL {{ vp.valueAreaLow | number: '1.2-2' }}
          </p>
        }
        <p class="muted">{{ c.signals.length }} signal marker(s) overlaid.</p>
      </div>
    } @else {
      <div class="state">Enter a symbol and load a chart.</div>
    }
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
export class ChartViewComponent {
  protected readonly symbol = signal('');
  protected readonly from = signal(isoDaysAgo(180));
  protected readonly to = signal(isoDaysAgo(0));
  private readonly query = signal<{ symbol: string; from: string; to: string } | null>(null);

  protected readonly chart = httpResource<ChartView>(() => {
    const q = this.query();
    return q ? `${API}/charts/${q.symbol}?from=${q.from}&to=${q.to}&overlays=volprofile` : undefined;
  });

  protected readonly geometry = computed<Geometry | null>(() => {
    const c = this.chart.value();
    if (!c || c.candles.length === 0) {
      return null;
    }
    const closes = c.candles.map((x) => x.close);
    const min = Math.min(...closes);
    const max = Math.max(...closes);
    const n = c.candles.length;
    const x = (i: number) => PAD + (n === 1 ? 0 : (i * (W - 2 * PAD)) / (n - 1));
    const y = (v: number) => (max === min ? H / 2 : PAD + ((max - v) * (H - 2 * PAD)) / (max - min));

    const line = c.candles.map((cd, i) => `${x(i)},${y(cd.close)}`).join(' ');
    const index = new Map(c.candles.map((cd, i) => [cd.tradeDate, i] as const));
    const markers: Marker[] = c.signals
      .filter((s) => index.has(s.date))
      .map((s) => {
        const i = index.get(s.date)!;
        return { cx: x(i), cy: y(c.candles[i].close), action: s.action };
      });
    return { width: W, height: H, line, markers };
  });

  protected load(): void {
    this.query.set({ symbol: this.symbol().trim().toUpperCase(), from: this.from(), to: this.to() });
  }

  protected asValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected color(action: SignalAction): string {
    return COLORS[action];
  }
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
