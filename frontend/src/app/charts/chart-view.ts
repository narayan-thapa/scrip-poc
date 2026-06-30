import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import {
  CandlestickSeries,
  ColorType,
  createChart,
  createSeriesMarkers,
  CrosshairMode,
  IChartApi,
  ISeriesApi,
  LineSeries,
  LineStyle,
  SeriesMarker,
  Time,
} from 'lightweight-charts';

import { API } from '../core/api';
import {
  ChartView,
  IndicatorCatalogEntry,
  IndicatorCategory,
  IndicatorPoint,
  SignalAction,
} from '../core/models';
import { SmcZoneInput, SmcZonesPrimitive } from './smc-zones-primitive';

/** One line to draw, with its palette color, target pane, and legend metadata. */
interface SeriesPlanItem {
  label: string;
  color: string;
  overlay: boolean;
  paneIndex: number;
  points: IndicatorPoint[];
  last: number | null;
}

const COLORS: Record<SignalAction, string> = { BUY: '#3fb950', SELL: '#f85149', HOLD: '#d29922' };
/** Distinct line colors cycled across indicator outputs. */
const PALETTE = ['#e3b341', '#3fb950', '#db61a2', '#a371f7', '#39c5cf', '#f0883e', '#f85149', '#8b949e'];
const SMC_BULL = '#3fb950';
const SMC_BEAR = '#f85149';

/** Indicators drawn on the price pane; everything else gets its own sub-pane. */
const OVERLAY_KEYS = new Set(['sma', 'ema', 'bbands', 'supertrend']);
const CATEGORY_ORDER: IndicatorCategory[] = ['TREND', 'MOMENTUM', 'VOLATILITY', 'VOLUME'];
/** Cap how many SMC zones we project forward, newest first, to keep the chart readable. */
const SMC_ZONE_LIMIT = 12;

/** Chart colors aligned with the app theme (see src/styles.css). */
const CHART_BG = '#21262d';
const CHART_TEXT = '#9aa7b4';
const CHART_BORDER = '#2b313a';
const CHART_GRID = 'rgba(43, 49, 58, 0.6)';

@Component({
  selector: 'app-chart-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <div class="page-head">
      <h1>Price chart</h1>
      <p class="muted">TradingView Lightweight Charts: candles + indicators + signal markers + SMC zones</p>
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

    <div class="card">
      @if (chart.value(); as c) {
        <h2>{{ c.symbol }} — {{ c.candles.length }} candles</h2>
      } @else {
        <h2 class="muted">Load a symbol to chart it</h2>
      }

      @if (chart.isLoading()) {
        <div class="state">Loading…</div>
      } @else if (chart.error()) {
        <div class="state error">No data for that symbol/range (or a selected indicator has no history here).</div>
      }

      <div #chartContainer class="tv-chart"></div>

      @if (chart.value(); as c) {
        <div class="legend">
          <span class="legend-item"><i class="candle-swatch"></i>OHLC candles</span>
          @for (l of seriesLegend(); track l.label) {
            <span class="legend-item">
              <i [style.background]="l.color"></i>{{ l.label }}
              @if (l.last !== null) {
                <em class="muted">{{ l.last | number: '1.2-2' }}</em>
              }
            </span>
          }
        </div>
        @if (c.volumeProfile; as vp) {
          <p class="muted">
            Volume profile — POC {{ vp.poc | number: '1.2-2' }} · VAH
            {{ vp.valueAreaHigh | number: '1.2-2' }} · VAL {{ vp.valueAreaLow | number: '1.2-2' }}
            (drawn as dashed/dotted price lines)
          </p>
        }
        <p class="muted">
          {{ c.signals.length }} signal marker(s) overlaid.
          @if (c.smc; as smc) {
            · SMC: {{ smc.zones.length }} zone(s) · {{ smc.events.length }} break(s)
          }
        </p>
      }
    </div>
  `,
  styles: `
    .tv-chart {
      width: 100%;
      height: 460px;
      border-radius: 8px;
      overflow: hidden;
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
      margin: 0.6rem 0 0.4rem;
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
    .legend-item i.candle-swatch {
      width: 10px;
      height: 12px;
      border-radius: 2px;
      background: linear-gradient(#3fb950 0 50%, #f85149 50% 100%);
    }
    .legend-item em {
      font-style: normal;
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class ChartViewComponent {
  protected readonly symbol = signal('');
  protected readonly from = signal(isoDaysAgo(180));
  protected readonly to = signal(isoDaysAgo(0));
  protected readonly selected = signal<string[]>([]);
  protected readonly smcEnabled = signal(false);
  private readonly query = signal<{ symbol: string; from: string; to: string } | null>(null);

  private readonly chartContainer = viewChild<ElementRef<HTMLDivElement>>('chartContainer');
  private lwChart?: IChartApi;
  private candleSeries?: ISeriesApi<'Candlestick'>;
  private smcPrimitive?: SmcZonesPrimitive;

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

  /** Same color/pane assignment the chart uses, so the HTML legend matches the series. */
  protected readonly seriesLegend = computed(() => this.buildSeriesPlan(this.chart.value()));

  constructor() {
    // The container exists from first render; build once it's there, then on every data change.
    afterNextRender(() => this.renderChart());
    effect(() => {
      this.chart.value(); // track: re-render whenever the payload changes
      this.renderChart();
    });
    inject(DestroyRef).onDestroy(() => this.lwChart?.remove());
  }

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

  /** Assigns each indicator output a palette color and a pane (overlays share the price pane). */
  private buildSeriesPlan(data: ChartView | undefined): SeriesPlanItem[] {
    if (!data) {
      return [];
    }
    const plan: SeriesPlanItem[] = [];
    let colorIndex = 0;
    let nextPane = 1;
    for (const series of data.indicators) {
      const overlay = OVERLAY_KEYS.has(series.indicator);
      const paneIndex = overlay ? 0 : nextPane++;
      for (const [lineName, pts] of Object.entries(series.lines)) {
        const label = lineName === series.indicator ? series.indicator : `${series.indicator}·${lineName}`;
        plan.push({
          label,
          color: PALETTE[colorIndex++ % PALETTE.length],
          overlay,
          paneIndex,
          points: pts,
          last: pts.length ? pts[pts.length - 1].value : null,
        });
      }
    }
    return plan;
  }

  /** Tears down any existing chart and rebuilds it from the current payload. */
  private renderChart(): void {
    const el = this.chartContainer()?.nativeElement;
    if (!el) {
      return;
    }
    this.lwChart?.remove();
    this.lwChart = undefined;
    this.candleSeries = undefined;
    this.smcPrimitive = undefined;

    const data = this.chart.value();
    if (!data || data.candles.length === 0) {
      return;
    }

    const chart = createChart(el, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: CHART_BG }, textColor: CHART_TEXT },
      grid: { vertLines: { color: CHART_GRID }, horzLines: { color: CHART_GRID } },
      rightPriceScale: { borderColor: CHART_BORDER },
      timeScale: { borderColor: CHART_BORDER, timeVisible: false },
      crosshair: { mode: CrosshairMode.Normal },
    });
    this.lwChart = chart;

    const candle = chart.addSeries(CandlestickSeries, {
      upColor: '#3fb950',
      downColor: '#f85149',
      borderVisible: false,
      wickUpColor: '#3fb950',
      wickDownColor: '#f85149',
    });
    candle.setData(
      data.candles.map((c) => ({ time: c.tradeDate as Time, open: c.open, high: c.high, low: c.low, close: c.close })),
    );
    this.candleSeries = candle;

    const vp = data.volumeProfile;
    if (vp) {
      candle.createPriceLine({ price: vp.poc, color: '#e3b341', lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: 'POC' });
      candle.createPriceLine({ price: vp.valueAreaHigh, color: '#8b949e', lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: 'VAH' });
      candle.createPriceLine({ price: vp.valueAreaLow, color: '#8b949e', lineWidth: 1, lineStyle: LineStyle.Dotted, axisLabelVisible: true, title: 'VAL' });
    }

    for (const item of this.buildSeriesPlan(data)) {
      const line = chart.addSeries(
        LineSeries,
        { color: item.color, lineWidth: 1, priceLineVisible: false, lastValueVisible: !item.overlay, title: item.overlay ? '' : item.label },
        item.paneIndex,
      );
      line.setData(item.points.map((p) => ({ time: p.date as Time, value: p.value })));
    }

    // Give the price pane most of the height when oscillator sub-panes exist.
    const panes = chart.panes();
    if (panes.length > 1) {
      panes[0].setStretchFactor(3);
    }

    this.applyMarkers(candle, data);
    this.applySmcZones(candle, data);

    chart.timeScale().fitContent();
  }

  /** BUY/SELL/HOLD signal markers plus SMC BOS/CHoCH break markers, merged and time-sorted. */
  private applyMarkers(candle: ISeriesApi<'Candlestick'>, data: ChartView): void {
    const markers: SeriesMarker<Time>[] = [];
    for (const s of data.signals) {
      markers.push({
        time: s.date as Time,
        position: s.action === 'SELL' ? 'aboveBar' : s.action === 'BUY' ? 'belowBar' : 'inBar',
        shape: s.action === 'BUY' ? 'arrowUp' : s.action === 'SELL' ? 'arrowDown' : 'circle',
        color: COLORS[s.action],
        text: s.action,
      });
    }
    if (data.smc) {
      for (const ev of data.smc.events) {
        const bullish = ev.type === 'BOS_BULLISH' || ev.type === 'CHOCH_BULLISH';
        markers.push({
          time: ev.date as Time,
          position: bullish ? 'belowBar' : 'aboveBar',
          shape: 'square',
          color: bullish ? SMC_BULL : SMC_BEAR,
          text: ev.label,
        });
      }
    }
    if (markers.length) {
      markers.sort((a, b) => String(a.time).localeCompare(String(b.time)));
      createSeriesMarkers(candle, markers);
    }
  }

  private applySmcZones(candle: ISeriesApi<'Candlestick'>, data: ChartView): void {
    if (!data.smc || data.smc.zones.length === 0) {
      return;
    }
    const candleDates = new Set(data.candles.map((c) => c.tradeDate));
    const zones: SmcZoneInput[] = data.smc.zones
      .filter((z) => candleDates.has(z.fromDate))
      .sort((a, b) => b.fromDate.localeCompare(a.fromDate))
      .slice(0, SMC_ZONE_LIMIT)
      .map((z) => ({
        fromTime: z.fromDate as Time,
        top: z.top,
        bottom: z.bottom,
        bullish: z.type === 'BULLISH_OB' || z.type === 'BULLISH_FVG',
        mitigated: z.mitigated,
      }));
    if (zones.length === 0) {
      return;
    }
    const primitive = new SmcZonesPrimitive();
    candle.attachPrimitive(primitive);
    primitive.setZones(zones);
    this.smcPrimitive = primitive;
  }
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
