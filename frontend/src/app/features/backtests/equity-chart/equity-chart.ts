import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { AreaSeries, HistogramSeries, createChart, type IChartApi } from 'lightweight-charts';
import { BacktestApi } from '../backtest.service';

/** Equity curve (area) + drawdown (histogram, below) for a backtest run. Lightweight Charts v5. */
@Component({
  selector: 'app-equity-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #container class="chart"></div>`,
  styles: [`.chart { width: 100%; height: 360px; }`],
})
export class EquityChart {
  readonly runId = input.required<string>();

  private readonly api = inject(BacktestApi);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('container');
  private chart?: IChartApi;
  private equity?: ReturnType<IChartApi['addSeries']>;
  private drawdown?: ReturnType<IChartApi['addSeries']>;

  constructor() {
    afterNextRender(() => this.init());
    inject(DestroyRef).onDestroy(() => this.chart?.remove());
    effect(() => {
      this.runId();
      if (this.chart) {
        this.load();
      }
    });
  }

  private init(): void {
    this.chart = createChart(this.container().nativeElement, {
      autoSize: true,
      layout: { attributionLogo: true, background: { color: 'transparent' }, textColor: '#5b6573' },
      grid: { vertLines: { visible: false }, horzLines: { color: '#eceff3' } },
      rightPriceScale: { borderVisible: false },
    });
    this.equity = this.chart.addSeries(AreaSeries, {
      lineColor: '#2563eb',
      topColor: 'rgba(37,99,235,0.3)',
      bottomColor: 'rgba(37,99,235,0.02)',
    });
    this.drawdown = this.chart.addSeries(HistogramSeries, { priceScaleId: 'dd', color: 'rgba(220,38,38,0.5)' });
    this.drawdown.priceScale().applyOptions({ scaleMargins: { top: 0.75, bottom: 0 } });
    this.load();
  }

  private load(): void {
    this.api.equityCurve(this.runId()).subscribe((points) => {
      this.equity?.setData(points.map((p) => ({ time: p.date, value: p.equity })));
      this.drawdown?.setData(points.map((p) => ({ time: p.date, value: p.drawdownPct })));
      this.chart?.timeScale().fitContent();
    });
  }
}
