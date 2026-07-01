import { NgTemplateOutlet } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { DashboardDto } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';

/** F11 — Day Transaction Analysis: breadth + high/low trade + relative-volume spikes/drops. */
@Component({
  selector: 'app-day-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, NgTemplateOutlet],
  template: `
    <header class="page-head">
      <h1>Day dashboard</h1>
      <label>Date <input type="date" [value]="date()" (change)="date.set($any($event.target).value)" /></label>
    </header>

    @if (data.isLoading()) {
      <app-spinner label="Loading dashboard" />
    } @else if (data.error()) {
      <app-error-state title="No data for this date" [retryable]="false" />
    } @else if (data.value(); as d) {
      <section class="breadth">
        <div class="stat up"><span>Advances</span><strong>{{ d.breadth.advances }}</strong></div>
        <div class="stat down"><span>Declines</span><strong>{{ d.breadth.declines }}</strong></div>
        <div class="stat"><span>Unchanged</span><strong>{{ d.breadth.unchanged }}</strong></div>
        <div class="stat"><span>Turnover</span><strong class="numeric">{{ d.breadth.totalTurnover }}</strong></div>
        <div class="stat"><span>Volume</span><strong class="numeric">{{ d.breadth.totalVolume }}</strong></div>
      </section>

      <div class="grid">
        <section class="card">
          <h2>High trade (turnover)</h2>
          <ng-container [ngTemplateOutlet]="activeTable" [ngTemplateOutletContext]="{ rows: d.highTrade }" />
        </section>
        <section class="card">
          <h2>Low trade (thin liquidity)</h2>
          <ng-container [ngTemplateOutlet]="activeTable" [ngTemplateOutletContext]="{ rows: d.lowTrade }" />
        </section>
        <section class="card">
          <h2>Relative-volume spikes</h2>
          <ng-container [ngTemplateOutlet]="rvolTable" [ngTemplateOutletContext]="{ rows: d.rvolSpikes }" />
        </section>
        <section class="card">
          <h2>Relative-volume drops</h2>
          <ng-container [ngTemplateOutlet]="rvolTable" [ngTemplateOutletContext]="{ rows: d.rvolDrops }" />
        </section>
      </div>
    }

    <ng-template #activeTable let-rows="rows">
      <table class="data-table">
        <thead><tr><th>Symbol</th><th>Close</th><th>Δ%</th><th>Turnover</th><th>Signal</th></tr></thead>
        <tbody>
          @for (r of $any(rows); track r.symbol) {
            <tr>
              <td><a [routerLink]="['/market', r.symbol]">{{ r.symbol }}</a></td>
              <td class="numeric">{{ r.close }}</td>
              <td class="numeric" [class.up]="(r.changePct ?? 0) > 0" [class.down]="(r.changePct ?? 0) < 0">{{ r.changePct ?? '—' }}</td>
              <td class="numeric">{{ r.turnover }}</td>
              <td>@if (r.signal) { <span class="chip" [class]="r.signal.action.toLowerCase()">{{ r.signal.action }}</span> }</td>
            </tr>
          }
        </tbody>
      </table>
    </ng-template>

    <ng-template #rvolTable let-rows="rows">
      <table class="data-table">
        <thead><tr><th>Symbol</th><th>RVOL</th><th>z</th><th>Δ%</th><th>Signal</th></tr></thead>
        <tbody>
          @for (r of $any(rows); track r.symbol) {
            <tr>
              <td><a [routerLink]="['/market', r.symbol]">{{ r.symbol }}</a></td>
              <td class="numeric">{{ r.rvolRatio }}×</td>
              <td class="numeric">{{ r.rvolZ }}</td>
              <td class="numeric" [class.up]="(r.changePct ?? 0) > 0" [class.down]="(r.changePct ?? 0) < 0">{{ r.changePct ?? '—' }}</td>
              <td>@if (r.signal) { <span class="chip" [class]="r.signal.action.toLowerCase()">{{ r.signal.action }}</span> }</td>
            </tr>
          }
        </tbody>
      </table>
    </ng-template>
  `,
  styles: [
    `
      .breadth { display: flex; gap: var(--space-4); flex-wrap: wrap; margin-bottom: var(--space-6); }
      .stat { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-3) var(--space-4); display: flex; flex-direction: column; min-width: 7rem; }
      .stat span { color: var(--color-text-muted); font-size: 0.8rem; }
      .stat strong { font-size: 1.25rem; }
      .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr)); gap: var(--space-6); }
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); }
      .card h2 { margin: 0 0 var(--space-2); font-size: 1rem; }
      .chip { font-size: 0.7rem; font-weight: 700; padding: 1px var(--space-2); border-radius: var(--radius-sm); color: #fff; }
      .chip.buy { background: var(--color-up); } .chip.sell { background: var(--color-down); } .chip.hold { background: var(--color-text-muted); }
    `,
  ],
})
export class DayDashboard {
  protected readonly date = signal('');

  protected readonly data = httpResource<DashboardDto>(() => {
    const d = this.date();
    return `${environment.apiBaseUrl}/api/v1/dashboard/day${d ? `?date=${d}` : ''}`;
  });
}
