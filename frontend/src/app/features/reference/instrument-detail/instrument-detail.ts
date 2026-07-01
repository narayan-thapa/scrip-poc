import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { InstrumentDto } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';

/** Single-instrument detail. `symbol` is bound from the route via withComponentInputBinding(). */
@Component({
  selector: 'app-instrument-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState],
  template: `
    <p><a routerLink="/instruments">← Instruments</a></p>
    @if (instrument.isLoading()) {
      <app-spinner label="Loading" />
    } @else if (instrument.error()) {
      <app-error-state title="Instrument not found" [retryable]="false" />
    } @else {
      @let i = instrument.value();
      <section class="card">
        <h1>{{ i?.symbol }} <small>{{ i?.name }}</small></h1>
        <p><a [routerLink]="['/market', i?.symbol]">View chart &amp; broker flow →</a></p>
        <dl class="kv">
          <dt>Sector</dt><dd>{{ i?.sector ?? '—' }}</dd>
          <dt>Type</dt><dd>{{ i?.type }}</dd>
          <dt>Status</dt><dd>{{ i?.status }}</dd>
          <dt>Listed shares</dt><dd class="numeric">{{ i?.listedShares ?? '—' }}</dd>
          <dt>Price band</dt><dd class="numeric">{{ i?.priceBand ?? '—' }}</dd>
          <dt>Provisional</dt><dd>{{ i?.provisional ? 'yes' : 'no' }}</dd>
        </dl>
      </section>
    }
  `,
  styles: [
    `
      .card { background: var(--color-surface); border: 1px solid var(--color-border);
        border-radius: var(--radius); padding: var(--space-6); box-shadow: var(--shadow); max-width: 32rem; }
      h1 { margin: 0 0 var(--space-4); font-size: 1.5rem; }
      h1 small { color: var(--color-text-muted); font-size: 1rem; font-weight: 400; }
      .kv { display: grid; grid-template-columns: auto 1fr; gap: var(--space-2) var(--space-6); margin: 0; }
      dt { color: var(--color-text-muted); }
      dd { margin: 0; }
    `,
  ],
})
export class InstrumentDetail {
  readonly symbol = input.required<string>();

  protected readonly instrument = httpResource<InstrumentDto>(
    () => `${environment.apiBaseUrl}/api/v1/instruments/${encodeURIComponent(this.symbol())}`,
  );
}
