import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { SignalDetail as SignalDetailModel } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { ReasonsPanel } from '../reasons-panel/reasons-panel';

/** Full signal detail page: the reasons panel for one signal. */
@Component({
  selector: 'app-signal-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, ReasonsPanel],
  template: `
    <p><a routerLink="/signals">← Signals</a></p>
    @if (detail.isLoading()) {
      <app-spinner label="Loading" />
    } @else if (detail.error()) {
      <app-error-state title="Signal not found" [retryable]="false" />
    } @else if (detail.value(); as s) {
      <header class="page-head">
        <h1>{{ s.symbol }} <span class="muted">{{ s.tradeDate }}</span></h1>
        <a [routerLink]="['/market', s.symbol]">View chart →</a>
      </header>
      <section class="card">
        <app-reasons-panel [signal]="s" />
      </section>
    }
  `,
  styles: [
    `
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-6); box-shadow: var(--shadow); max-width: 40rem; }
      .muted { color: var(--color-text-muted); font-weight: 400; font-size: 0.9rem; }
    `,
  ],
})
export class SignalDetail {
  readonly id = input.required<string>();

  protected readonly detail = httpResource<SignalDetailModel>(
    () => `${environment.apiBaseUrl}/api/v1/signals/${this.id()}`,
  );
}
