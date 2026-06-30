import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { API } from '../core/api';
import {
  ChartView,
  IndicatorCatalogEntry,
  IndicatorCategory,
  IndicatorPoint,
  SignalAction,
  SmcEvent,
  SmcZone,
} from '../core/models';

interface Marker {
  cx: number;
  cy: number;
  action: SignalAction;
}

/** A single drawable polyline (one output line of an indicator). */
interface LineGeom {
  label: string;
  color: string;
  points: string;
  last: number | null;
}

/** A stacked oscillator sub-panel rendered below the price chart, with its own value scale. */
interface PanelGeom {
  title: string;
  width: number;
  height: number;
  zeroY: number | null;
  lines: LineGeom[];
}

/** An SMC order block / fair-value gap projected onto the price axis. */
interface ZoneGeom {
  x: number;
  y: number;
  width: number;
  height: number;
  fill: string;
  stroke: string;
  mitigated: boolean;
}

/** An SMC structural break (BOS/CHoCH) marker on the price axis. */
interface EventGeom {
  cx: number;
  cy: number;
  color: string;
  label: string;
  anchor: 'start' | 'end';
}

interface Geometry {
  width: number;
  height: number;
  priceLine: string;
  markers: Marker[];
  overlays: LineGeom[];
  smcZones: ZoneGeom[];
  smcEvents: EventGeom[];
}

const W = 920;
const H = 300;
const PAD = 12;
const PANEL_H = 96;
const PANEL_PAD = 10;
const COLORS: Record<SignalAction, string> = { BUY: '#3fb950', SELL: '#f85149', HOLD: '#d29922' };
const PRICE_COLOR = '#4493f8';
/** Distinct line colors (price keeps the accent blue; these are cycled for indicators). */
const PALETTE = ['#e3b341', '#3fb950', '#db61a2', '#a371f7', '#39c5cf', '#f0883e', '#f85149', '#8b949e'];

/** Indicators drawn on the price axis; everything else gets its own sub-panel. */
const OVERLAY_KEYS = new Set(['sma', 'ema', 'bbands', 'supertrend']);
const CATEGORY_ORDER: IndicatorCategory[] = ['TREND', 'MOMENTUM', 'VOLATILITY', 'VOLUME'];

/** SMC is a structural overlay (zones + markers), requested alongside volume profile. */
const SMC_BULL = '#3fb950';
const SMC_BEAR = '#f85149';
/** Cap how many zones we project forward, newest first, to keep the chart readable. */
const SMC_ZONE_LIMIT = 12;

@Component({
  selector: 'app-chart-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="page-head">
      <h1>Price chart</h1>
      <p class="muted">Composite payload: candles + indicators + signal markers + volume profile (one request)</p>
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

      <div class="indicators">
        <label class="ind-head">Indicators</label>
        @for (group of catalogGroups(); track group.category) {
          <div class="ind-group">
            <span class="ind-cat">{{ group.category }}</span>
            @for (entry of group.entries; track entry.key) {
              <button
                class="ghost chip-toggle"
                [class.active]="isSelected(entry.key)"
                (click)="toggle(entry.key)"
                [title]="entry.name + ' (' + entry.defaultParams.join(', ') + ')'"
              >
                {{ entry.name }}
              </button>
            }
          </div>
        }
        <div class="ind-group">
          <span class="ind-cat">Structure</span>
          <button
            class="ghost chip-toggle"
            [class.active]="smcEnabled()"
            (click)="toggleSmc()"
            title="Smart Money Concepts — order blocks, fair-value gaps, BOS/CHoCH"
          >
            SMC
          </button>
        </div>
        @if (selected().length) {
          <span class="muted ind-hint">
            {{ overlayCount() }} on price · {{ selected().length - overlayCount() }} in sub-panels
          </span>
        }
      </div>
    </div>

    @if (chart.isLoading()) {
      <div class="state">Loading…</div>
    } @else if (chart.error()) {
      <div class="state error">No data for that symbol/range (or a selected indicator has no history here).</div>
    } @else if (chart.value(); as c) {
      <div class="card">
        <h2>{{ c.symbol }} — {{ c.candles.length }} candles</h2>
        @if (geometry(); as g) {
          <div class="legend">
            <span class="legend-item"><i style="background:{{ PRICE_COLOR }}"></i>Close</span>
            @for (l of g.overlays; track l.label) {
              <span class="legend-item">
                <i [style.background]="l.color"></i>{{ l.label }}
                @if (l.last !== null) {
                  <em class="muted">{{ l.last | number: '1.2-2' }}</em>
                }
              </span>
            }
            @if (c.smc; as smc) {
              <span class="legend-item muted">
                SMC · {{ smc.zones.length }} zone(s) · {{ smc.events.length }} break(s)
              </span>
            }
          </div>
          <svg [attr.viewBox]="'0 0 ' + g.width + ' ' + g.height" class="chart" role="img">
            @for (z of g.smcZones; track $index) {
              <rect
                [attr.x]="z.x" [attr.y]="z.y" [attr.width]="z.width" [attr.height]="z.height"
                [attr.fill]="z.fill" [attr.fill-opacity]="z.mitigated ? 0.06 : 0.16"
                [attr.stroke]="z.stroke" stroke-opacity="0.5" stroke-width="1"
                [attr.stroke-dasharray]="z.mitigated ? '3 3' : null"
              />
            }
            <polyline [attr.points]="g.priceLine" fill="none" [attr.stroke]="PRICE_COLOR" stroke-width="1.5" />
            @for (l of g.overlays; track l.label) {
              <polyline [attr.points]="l.points" fill="none" [attr.stroke]="l.color" stroke-width="1.2" />
            }
            @for (ev of g.smcEvents; track $index) {
              <circle [attr.cx]="ev.cx" [attr.cy]="ev.cy" r="3" [attr.fill]="ev.color" />
              <text
                [attr.x]="ev.anchor === 'end' ? ev.cx - 5 : ev.cx + 5" [attr.y]="ev.cy - 4"
                [attr.text-anchor]="ev.anchor" [attr.fill]="ev.color" class="smc-label"
              >{{ ev.label }}</text>
            }
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

      @for (p of panels(); track p.title) {
        <div class="card">
          <div class="legend">
            <strong class="panel-title">{{ p.title }}</strong>
            @for (l of p.lines; track l.label) {
              <span class="legend-item">
                <i [style.background]="l.color"></i>{{ l.label }}
                @if (l.last !== null) {
                  <em class="muted">{{ l.last | number: '1.2-2' }}</em>
                }
              </span>
            }
          </div>
          <svg [attr.viewBox]="'0 0 ' + p.width + ' ' + p.height" class="chart panel" role="img">
            @if (p.zeroY !== null) {
              <line [attr.x1]="0" [attr.x2]="p.width" [attr.y1]="p.zeroY" [attr.y2]="p.zeroY"
                    stroke="var(--border)" stroke-width="1" stroke-dasharray="3 3" />
            }
            @for (l of p.lines; track l.label) {
              <polyline [attr.points]="l.points" fill="none" [attr.stroke]="l.color" stroke-width="1.2" />
            }
          </svg>
        </div>
      }
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
    .chart.panel {
      margin-top: 0.25rem;
    }
    .indicators {
      margin-top: 1rem;
      padding-top: 0.85rem;
      border-top: 1px solid var(--border);
    }
    .ind-head {
      display: block;
      font-size: 0.78rem;
      color: var(--text-2);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin-bottom: 0.5rem;
    }
    .ind-group {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      margin-bottom: 0.4rem;
    }
    .ind-cat {
      width: 90px;
      flex-shrink: 0;
      font-size: 0.72rem;
      color: var(--text-2);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .chip-toggle {
      padding: 0.3rem 0.7rem;
      border-radius: 999px;
      font-size: 0.82rem;
    }
    .ind-hint {
      display: inline-block;
      margin-top: 0.35rem;
      font-size: 0.8rem;
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.9rem;
      margin-bottom: 0.4rem;
    }
    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.82rem;
    }
    .legend-item i {
      width: 12px;
      height: 3px;
      border-radius: 2px;
    }
    .legend-item em {
      font-style: normal;
      font-variant-numeric: tabular-nums;
    }
    .panel-title {
      font-size: 0.9rem;
    }
    .smc-label {
      font-size: 9px;
      font-weight: 600;
    }
  `,
})
export class ChartViewComponent {
  protected readonly PRICE_COLOR = PRICE_COLOR;

  protected readonly symbol = signal('');
  protected readonly from = signal(isoDaysAgo(180));
  protected readonly to = signal(isoDaysAgo(0));
  protected readonly selected = signal<string[]>([]);
  protected readonly smcEnabled = signal(false);
  private readonly query = signal<{ symbol: string; from: string; to: string } | null>(null);

  /** The indicator catalog drives the selector; loaded once, independent of the chart query. */
  protected readonly catalog = httpResource<IndicatorCatalogEntry[]>(
    () => `${API}/indicators/catalog`,
    { defaultValue: [] },
  );

  protected readonly chart = httpResource<ChartView>(() => {
    const q = this.query();
    if (!q) {
      return undefined;
    }
    const inds = this.selected();
    const indParam = inds.length ? `&indicators=${inds.join(',')}` : '';
    const overlays = this.smcEnabled() ? 'volprofile,smc' : 'volprofile';
    return `${API}/charts/${q.symbol}?from=${q.from}&to=${q.to}${indParam}&overlays=${overlays}`;
  });

  /** Catalog grouped by category, in a stable display order, for the selector. */
  protected readonly catalogGroups = computed(() => {
    const byCategory = new Map<IndicatorCategory, IndicatorCatalogEntry[]>();
    for (const entry of this.catalog.value()) {
      (byCategory.get(entry.category) ?? byCategory.set(entry.category, []).get(entry.category)!).push(entry);
    }
    return CATEGORY_ORDER.filter((c) => byCategory.has(c)).map((category) => ({
      category,
      entries: byCategory.get(category)!,
    }));
  });

  protected readonly overlayCount = computed(() => this.selected().filter((k) => OVERLAY_KEYS.has(k)).length);

  private readonly catalogMap = computed(
    () => new Map(this.catalog.value().map((e) => [e.key, e] as const)),
  );

  protected readonly geometry = computed<Geometry | null>(() => {
    const c = this.chart.value();
    if (!c || c.candles.length === 0) {
      return null;
    }
    const n = c.candles.length;
    const x = (i: number) => PAD + (n === 1 ? 0 : (i * (W - 2 * PAD)) / (n - 1));
    const indexByDate = new Map(c.candles.map((cd, i) => [cd.tradeDate, i] as const));

    const overlaySeries = c.indicators.filter((s) => OVERLAY_KEYS.has(s.indicator));

    // SMC: keep zones/events on visible candles only; cap zones to the most recent few.
    const smcZonesRaw = (c.smc?.zones ?? [])
      .filter((z) => indexByDate.has(z.fromDate))
      .sort((a, b) => indexByDate.get(b.fromDate)! - indexByDate.get(a.fromDate)!)
      .slice(0, SMC_ZONE_LIMIT);
    const smcEventsRaw = (c.smc?.events ?? []).filter((e) => indexByDate.has(e.date));

    // Price y-domain spans closes, overlaid indicator values, and SMC levels, so nothing clips.
    const priceValues = c.candles.map((cd) => cd.close);
    for (const series of overlaySeries) {
      for (const pts of Object.values(series.lines)) {
        for (const p of pts) {
          if (indexByDate.has(p.date)) {
            priceValues.push(p.value);
          }
        }
      }
    }
    for (const z of smcZonesRaw) {
      priceValues.push(z.top, z.bottom);
    }
    for (const e of smcEventsRaw) {
      priceValues.push(e.price);
    }
    const lo = Math.min(...priceValues);
    const hi = Math.max(...priceValues);
    const y = (v: number) => (hi === lo ? H / 2 : PAD + ((hi - v) * (H - 2 * PAD)) / (hi - lo));

    const priceLine = c.candles.map((cd, i) => `${x(i)},${y(cd.close)}`).join(' ');

    const markers: Marker[] = c.signals
      .filter((s) => indexByDate.has(s.date))
      .map((s) => {
        const i = indexByDate.get(s.date)!;
        return { cx: x(i), cy: y(c.candles[i].close), action: s.action };
      });

    let colorIndex = 0;
    const overlays: LineGeom[] = [];
    for (const series of overlaySeries) {
      for (const [lineName, pts] of Object.entries(series.lines)) {
        overlays.push(this.buildLine(series.indicator, lineName, pts, indexByDate, x, y, PALETTE[colorIndex++ % PALETTE.length]));
      }
    }

    const smcZones = smcZonesRaw.map((z) => this.buildZone(z, indexByDate, x, y));
    const smcEvents = smcEventsRaw.map((e) => this.buildEvent(e, indexByDate, x, y));

    return { width: W, height: H, priceLine, markers, overlays, smcZones, smcEvents };
  });

  protected readonly panels = computed<PanelGeom[]>(() => {
    const c = this.chart.value();
    if (!c || c.candles.length === 0) {
      return [];
    }
    const n = c.candles.length;
    const x = (i: number) => PAD + (n === 1 ? 0 : (i * (W - 2 * PAD)) / (n - 1));
    const indexByDate = new Map(c.candles.map((cd, i) => [cd.tradeDate, i] as const));

    const panelSeries = c.indicators.filter((s) => !OVERLAY_KEYS.has(s.indicator));
    // Color continues past the overlays so every line on screen has a distinct color.
    let colorIndex = this.geometry()?.overlays.length ?? 0;
    const panels: PanelGeom[] = [];

    for (const series of panelSeries) {
      const values: number[] = [];
      for (const pts of Object.values(series.lines)) {
        for (const p of pts) {
          if (indexByDate.has(p.date)) {
            values.push(p.value);
          }
        }
      }
      if (values.length === 0) {
        continue;
      }
      const lo = Math.min(...values);
      const hi = Math.max(...values);
      const y = (v: number) =>
        hi === lo ? PANEL_H / 2 : PANEL_PAD + ((hi - v) * (PANEL_H - 2 * PANEL_PAD)) / (hi - lo);

      const lines = Object.entries(series.lines).map(([lineName, pts]) =>
        this.buildLine(series.indicator, lineName, pts, indexByDate, x, y, PALETTE[colorIndex++ % PALETTE.length]),
      );

      const entry = this.catalogMap().get(series.indicator);
      const title = `${entry?.name ?? series.indicator.toUpperCase()} (${series.params.join(', ')})`;
      const zeroY = lo < 0 && hi > 0 ? y(0) : null;
      panels.push({ title, width: W, height: PANEL_H, zeroY, lines });
    }
    return panels;
  });

  protected toggle(key: string): void {
    this.selected.update((list) => (list.includes(key) ? list.filter((k) => k !== key) : [...list, key]));
  }

  protected toggleSmc(): void {
    this.smcEnabled.update((on) => !on);
  }

  protected isSelected(key: string): boolean {
    return this.selected().includes(key);
  }

  protected load(): void {
    this.query.set({ symbol: this.symbol().trim().toUpperCase(), from: this.from(), to: this.to() });
  }

  protected asValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected color(action: SignalAction): string {
    return COLORS[action];
  }

  /** Builds one polyline, dropping points whose date isn't a candle in range (warm-up prefix, gaps). */
  private buildLine(
    indicatorKey: string,
    lineName: string,
    pts: IndicatorPoint[],
    indexByDate: Map<string, number>,
    x: (i: number) => number,
    y: (v: number) => number,
    color: string,
  ): LineGeom {
    const inRange = pts.filter((p) => indexByDate.has(p.date));
    const points = inRange.map((p) => `${x(indexByDate.get(p.date)!)},${y(p.value)}`).join(' ');
    // A single-output indicator (line key === indicator key) just shows its key; multi-line shows both.
    const label = lineName === indicatorKey ? indicatorKey : `${indicatorKey}·${lineName}`;
    const last = inRange.length ? inRange[inRange.length - 1].value : null;
    return { label, color, points, last };
  }

  /** A zone box projected from its origin candle forward to the chart's right edge. */
  private buildZone(
    z: SmcZone,
    indexByDate: Map<string, number>,
    x: (i: number) => number,
    y: (v: number) => number,
  ): ZoneGeom {
    const xStart = x(indexByDate.get(z.fromDate)!);
    const yTop = y(z.top);
    const yBottom = y(z.bottom);
    const bullish = z.type === 'BULLISH_OB' || z.type === 'BULLISH_FVG';
    const base = bullish ? SMC_BULL : SMC_BEAR;
    return {
      x: xStart,
      y: yTop,
      width: Math.max(2, W - PAD - xStart),
      height: Math.max(1, yBottom - yTop),
      fill: base,
      stroke: base,
      mitigated: z.mitigated,
    };
  }

  /** A BOS/CHoCH marker at the broken swing level, label kept inside the viewport. */
  private buildEvent(
    e: SmcEvent,
    indexByDate: Map<string, number>,
    x: (i: number) => number,
    y: (v: number) => number,
  ): EventGeom {
    const cx = x(indexByDate.get(e.date)!);
    const bullish = e.type === 'BOS_BULLISH' || e.type === 'CHOCH_BULLISH';
    return {
      cx,
      cy: y(e.price),
      color: bullish ? SMC_BULL : SMC_BEAR,
      label: e.label,
      anchor: cx > W / 2 ? 'end' : 'start',
    };
  }
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
