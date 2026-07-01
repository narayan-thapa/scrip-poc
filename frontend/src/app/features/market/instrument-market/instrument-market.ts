import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BrokerFlowDto, SignalDetail, SignalSummary } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { PriceChart } from '../price-chart/price-chart';
import { AddIndicator, ActiveStudy } from '../../indicators/add-indicator/add-indicator';
import { ReasonsPanel } from '../../signals/reasons-panel/reasons-panel';

/** Per-symbol market page: the price chart + a broker-flow panel for a chosen date. */
@Component({
  selector: 'app-instrument-market',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PriceChart, Spinner, AddIndicator, ReasonsPanel],
  template: `
    <header class="page-head">
      <h1>{{ symbol() }}</h1>
      <a [routerLink]="['/instruments', symbol()]">Instrument details →</a>
    </header>

    <section class="card">
      <div class="chart-toolbar">
        <app-add-indicator (added)="addStudy($event)" />
        @if (studies().length) {
          <div class="active-studies">
            @for (s of studies(); track s.key) {
              <span class="chip" [style.borderColor]="s.color">
                {{ s.name }}
                <button type="button" aria-label="Remove" (click)="removeStudy(s.key)">×</button>
              </span>
            }
          </div>
        }
      </div>
      <app-price-chart
        [symbol]="symbol()"
        [studies]="studies()"
        [signalMarkers]="signals.value() ?? []"
        (dateClicked)="selectSignalOn($event)"
      />
      <p class="muted">Markers show daily BUY/SELL/HOLD signals — click one to see why.</p>
    </section>

    @if (selectedSignal.value(); as s) {
      <section class="card">
        <app-reasons-panel [signal]="s" />
      </section>
    }

    <section class="card">
      <header class="flow-head">
        <h2>Broker flow</h2>
        <label>Date <input type="date" [value]="date()" (change)="date.set($any($event.target).value)" /></label>
      </header>
      @if (brokerFlow.isLoading()) {
        <app-spinner label="Loading broker flow" />
      } @else if (brokerFlow.error() || (brokerFlow.value()?.brokers ?? []).length === 0) {
        <p class="muted">No broker flow for this date.</p>
      } @else {
        @let f = brokerFlow.value();
        <p class="muted">
          Top buyer share {{ pct(f?.topBuyerShare) }} · top seller share {{ pct(f?.topSellerShare) }}
          · HHI buy {{ f?.hhiBuy?.toFixed(3) }} / sell {{ f?.hhiSell?.toFixed(3) }}
        </p>
        <table class="data-table">
          <thead><tr><th>Broker</th><th>Buy</th><th>Sell</th><th>Net</th></tr></thead>
          <tbody>
            @for (b of topRows(); track b.brokerId) {
              <tr>
                <td class="numeric">{{ b.brokerId }}</td>
                <td class="numeric">{{ b.buyQty }}</td>
                <td class="numeric">{{ b.sellQty }}</td>
                <td class="numeric" [class.up]="b.netQty > 0" [class.down]="b.netQty < 0">{{ b.netQty }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); margin-bottom: var(--space-6); }
      .flow-head { display: flex; justify-content: space-between; align-items: center; }
      .flow-head h2 { margin: 0; font-size: 1rem; }
      .muted { color: var(--color-text-muted); font-size: 0.875rem; }
      .chart-toolbar { display: flex; flex-direction: column; gap: var(--space-3); margin-bottom: var(--space-3); }
      .active-studies { display: flex; gap: var(--space-2); flex-wrap: wrap; }
      .chip { font-size: 0.75rem; padding: var(--space-1) var(--space-2); border: 1px solid var(--color-border);
        border-left-width: 3px; border-radius: var(--radius-sm); display: inline-flex; gap: var(--space-1); align-items: center; }
      .chip button { background: transparent; border: 0; cursor: pointer; color: var(--color-text-muted); font-size: 1rem; }
    `,
  ],
})
export class InstrumentMarket {
  readonly symbol = input.required<string>();
  protected readonly date = signal(new Date().toISOString().slice(0, 10));
  protected readonly studies = signal<ActiveStudy[]>([]);
  protected readonly selectedSignalId = signal<string | null>(null);

  /** The symbol's signals — drive chart markers. */
  protected readonly signals = httpResource<SignalSummary[]>(
    () => `${environment.apiBaseUrl}/api/v1/signals/symbol/${this.symbol()}?limit=120`,
  );

  /** The clicked signal's full detail (reasons), if any. */
  protected readonly selectedSignal = httpResource<SignalDetail>(() => {
    const id = this.selectedSignalId();
    return id ? `${environment.apiBaseUrl}/api/v1/signals/${id}` : undefined;
  });

  addStudy(study: ActiveStudy): void {
    this.studies.update((list) => [...list, study]);
  }

  removeStudy(key: string): void {
    this.studies.update((list) => list.filter((s) => s.key !== key));
  }

  selectSignalOn(date: string): void {
    const match = (this.signals.value() ?? []).find((s) => s.tradeDate === date);
    this.selectedSignalId.set(match ? match.id : null);
  }

  protected readonly brokerFlow = httpResource<BrokerFlowDto>(
    () => `${environment.apiBaseUrl}/api/v1/market/broker-flow?symbol=${this.symbol()}&date=${this.date()}`,
  );

  /** Show the 10 biggest net buyers + 10 biggest net sellers. */
  protected readonly topRows = computed(() => {
    const rows = [...(this.brokerFlow.value()?.brokers ?? [])].sort((a, b) => b.netQty - a.netQty);
    return [...rows.slice(0, 10), ...rows.slice(-10)].filter((v, i, arr) => arr.indexOf(v) === i);
  });

  protected pct(v: number | undefined): string {
    return v == null ? '—' : `${(v * 100).toFixed(1)}%`;
  }
}
