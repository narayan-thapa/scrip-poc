import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { forkJoin, map } from 'rxjs';
import {
  CandlestickSeries,
  HistogramSeries,
  LineSeries,
  LineStyle,
  createChart,
  createSeriesMarkers,
  type IChartApi,
  type ISeriesApi,
} from 'lightweight-charts';
import { ComputeResponse } from '../../../core/api/models';
import { NotificationService } from '../../../core/notification/notification.service';
import { IndicatorApi } from '../../indicators/indicator.service';
import { ActiveStudy } from '../../indicators/add-indicator/add-indicator';
import { ChartApi } from '../chart.service';

/**
 * Oscillators that render in their own sub-pane below price (v5 multi-pane) rather than overlaid on
 * the price axis — their scale (0–100, ±) is incompatible with price. Everything else (moving
 * averages, Bollinger, Supertrend/UT Bot, SMC) overlays the price pane.
 */
const SEPARATE_PANE_STUDIES = new Set(['rsi', 'macd', 'adx', 'mfi', 'atr', 'cci', 'stoch']);

/**
 * TradingView Lightweight Charts™ v5 price chart: candlesticks + a volume overlay, the volume
 * profile's POC/VAH/VAL as price lines, and any active studies rendered by output kind —
 * LINE/BAND as line series (price overlay, or an own sub-pane for oscillators), SIGNAL as a line +
 * buy/sell markers, MARKER as pattern markers, ZONE as structure rays + labels. EOD → business-day axis.
 */
@Component({
  selector: 'app-price-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #container class="chart"></div>`,
  styles: [`.chart { width: 100%; height: 460px; }`],
})
export class PriceChart {
  readonly symbol = input.required<string>();
  readonly from = input<string>('2024-01-01');
  readonly to = input<string>(new Date().toISOString().slice(0, 10));
  readonly studies = input<ActiveStudy[]>([]);
  /** Emits the signal id when the user clicks a bar carrying a signal marker (to open its reasons). */
  readonly signalSelected = output<string>();

  private readonly api = inject(ChartApi);
  private readonly indicators = inject(IndicatorApi);
  private readonly notify = inject(NotificationService);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('container');

  private chart?: IChartApi;
  private candleSeries?: ISeriesApi<'Candlestick'>;
  private volumeSeries?: ISeriesApi<'Histogram'>;
  private profileLines: unknown[] = [];
  private overlaySeries: ISeriesApi<'Line'>[] = [];
  private overlayPriceLines: unknown[] = [];
  // Loosely typed: the markers plugin's generic (Time) fights structural typing on our plain objects.
  private markersHandle?: { setMarkers: (markers: unknown[]) => void };
  private studyMarkers: { time: string; position: string; color: string; shape: string; text: string }[] = [];
  private signalMarkersData: { time: string; id: string; action: string }[] = [];

  constructor() {
    afterNextRender(() => this.init());
    inject(DestroyRef).onDestroy(() => this.chart?.remove());
    effect(() => {
      this.symbol();
      this.from();
      this.to();
      if (this.chart) {
        this.load();
      }
    });
    effect(() => {
      this.studies();
      if (this.chart) {
        this.renderStudies();
      }
    });
  }

  private init(): void {
    this.chart = createChart(this.container().nativeElement, {
      autoSize: true,
      layout: { attributionLogo: true, background: { color: 'transparent' }, textColor: '#5b6573' },
      grid: { vertLines: { visible: false }, horzLines: { color: '#eceff3' } },
      rightPriceScale: { borderVisible: false },
      timeScale: { borderVisible: false },
    });
    this.candleSeries = this.chart.addSeries(CandlestickSeries, {
      upColor: '#16a34a',
      downColor: '#dc2626',
      wickUpColor: '#16a34a',
      wickDownColor: '#dc2626',
      borderVisible: false,
    });
    this.volumeSeries = this.chart.addSeries(HistogramSeries, { priceFormat: { type: 'volume' }, priceScaleId: '' });
    this.volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });
    this.chart.subscribeClick((param) => {
      if (param.time) {
        const marker = this.signalMarkersData.find((m) => m.time === String(param.time));
        if (marker) {
          this.signalSelected.emit(marker.id);
        }
      }
    });
    this.load();
  }

  private load(): void {
    const symbol = this.symbol();
    // One composite call (F7): candles + volume + volume profile + signal markers (with ids, so a
    // click opens the reasons panel — no separate signals request needed).
    this.api.chart(symbol, this.from(), this.to()).subscribe({
      next: (payload) => {
        this.candleSeries?.setData(
          payload.candles.map((c) => ({ time: c.time, open: c.open, high: c.high, low: c.low, close: c.close })),
        );
        this.volumeSeries?.setData(
          payload.volume.map((v) => ({ time: v.time, value: v.value, color: v.color })),
        );
        if (payload.volumeProfile) {
          this.drawProfileLines(payload.volumeProfile.poc, payload.volumeProfile.vah, payload.volumeProfile.val);
        }
        this.signalMarkersData = payload.markers.map((m) => ({ time: m.time, id: m.id, action: m.action }));
        this.chart?.timeScale().fitContent();
        this.renderStudies();
      },
      error: () => this.notify.error(`No market data for ${symbol} in range.`),
    });
  }

  private drawProfileLines(poc: number, vah: number, val: number): void {
    const series = this.candleSeries;
    if (!series) {
      return;
    }
    this.profileLines.forEach((l) => series.removePriceLine(l as never));
    this.profileLines = [
      series.createPriceLine({ price: poc, color: '#2563eb', lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: 'POC' }),
      series.createPriceLine({ price: vah, color: '#9aa4b2', lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: 'VAH' }),
      series.createPriceLine({ price: val, color: '#9aa4b2', lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: 'VAL' }),
    ];
  }

  private clearOverlays(): void {
    this.overlaySeries.forEach((s) => this.chart?.removeSeries(s));
    this.overlaySeries = [];
    this.overlayPriceLines.forEach((pl) => this.candleSeries?.removePriceLine(pl as never));
    this.overlayPriceLines = [];
    this.markersHandle?.setMarkers([]);
    // Drop any oscillator sub-panes (keep pane 0 = price + volume).
    if (this.chart) {
      for (let i = this.chart.panes().length - 1; i >= 1; i--) {
        this.chart.removePane(i);
      }
    }
  }

  /** Ensure a pane at {@code index} exists (0 = price). */
  private ensurePane(index: number): void {
    while (this.chart && this.chart.panes().length <= index) {
      this.chart.addPane();
    }
  }

  private renderStudies(): void {
    if (!this.chart || !this.candleSeries) {
      return;
    }
    this.clearOverlays();
    const studies = this.studies();
    if (studies.length === 0) {
      this.studyMarkers = [];
      this.applyMarkers();
      return;
    }
    const symbol = this.symbol();
    // Assign each oscillator its own sub-pane (1, 2, …); price overlays stay on pane 0.
    let nextPane = 1;
    const planned = studies.map((st) => ({ st, pane: SEPARATE_PANE_STUDIES.has(st.id) ? nextPane++ : 0 }));

    forkJoin(
      planned.map(({ st, pane }) =>
        this.indicators.compute(symbol, st.id, this.from(), this.to(), st.params).pipe(map((r) => ({ st, r, pane }))),
      ),
    ).subscribe((results) => {
      const markers: { time: string; position: string; color: string; shape: string; text: string }[] = [];
      for (const { st, r, pane } of results) {
        this.renderOne(st, r, markers, pane);
      }
      // Keep the price pane dominant over any oscillator sub-panes.
      const panes = this.chart?.panes() ?? [];
      if (panes.length > 1) {
        panes[0].setStretchFactor(3);
      }
      this.studyMarkers = markers;
      this.applyMarkers();
    });
  }

  /** Merge study + signal markers into the single markers primitive on the candle series. */
  private applyMarkers(): void {
    if (!this.candleSeries) {
      return;
    }
    const signalMarkers = this.signalMarkersData.map((s) => ({
      time: s.time,
      position: s.action === 'BUY' ? 'belowBar' : s.action === 'SELL' ? 'aboveBar' : 'inBar',
      color: s.action === 'BUY' ? '#16a34a' : s.action === 'SELL' ? '#dc2626' : '#9aa4b2',
      shape: s.action === 'BUY' ? 'arrowUp' : s.action === 'SELL' ? 'arrowDown' : 'circle',
      text: s.action,
    }));
    const all = [...this.studyMarkers, ...signalMarkers].sort((a, b) => (a.time < b.time ? -1 : 1));
    this.markersHandle = createSeriesMarkers(this.candleSeries, all as never) as unknown as {
      setMarkers: (markers: unknown[]) => void;
    };
  }

  private renderOne(study: ActiveStudy, r: ComputeResponse,
                    markers: { time: string; position: string; color: string; shape: string; text: string }[],
                    pane: number): void {
    switch (r.outputKind) {
      case 'LINE':
      case 'BAND': {
        this.ensurePane(pane);
        Object.values(r.lines ?? {}).forEach((points) => {
          const line = this.chart!.addSeries(LineSeries, { color: study.color, lineWidth: 2 }, pane);
          line.setData(points.map((p) => ({ time: p.time, value: p.value })));
          this.overlaySeries.push(line);
        });
        break;
      }
      case 'SIGNAL': {
        const line = this.chart!.addSeries(LineSeries, { color: study.color, lineWidth: 1, lineStyle: LineStyle.Dotted });
        line.setData((r.plot ?? []).map((p) => ({ time: p.time, value: p.value })));
        this.overlaySeries.push(line);
        (r.events ?? []).forEach((e) =>
          markers.push({
            time: e.time,
            position: e.side === 'BUY' ? 'belowBar' : 'aboveBar',
            color: e.side === 'BUY' ? '#16a34a' : '#dc2626',
            shape: e.side === 'BUY' ? 'arrowUp' : 'arrowDown',
            text: e.side,
          }),
        );
        break;
      }
      case 'MARKER': {
        (r.markers ?? []).forEach((m) =>
          markers.push({ time: m.time, position: m.position, color: m.color, shape: m.shape, text: m.text }),
        );
        break;
      }
      case 'ZONE': {
        (r.zones?.rays ?? []).forEach((ray) => {
          this.overlayPriceLines.push(
            this.candleSeries!.createPriceLine({ price: ray.price, color: ray.color, lineWidth: 1, lineStyle: LineStyle.Solid, axisLabelVisible: false, title: ray.label }),
          );
        });
        (r.zones?.labels ?? []).forEach((l) =>
          markers.push({ time: l.time, position: 'aboveBar', color: '#9333ea', shape: 'circle', text: l.text }),
        );
        break;
      }
    }
  }
}
