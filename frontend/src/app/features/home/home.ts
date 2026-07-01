import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { SystemService } from '../../core/system/system.service';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ErrorState } from '../../shared/ui/error-state/error-state';

/**
 * Phase 0 landing page. Demonstrates the end-to-end wiring: it reads the backend ping through the
 * `httpResource` and renders loading / error / success states from the shared UI kit. Real feature
 * dashboards replace this from Phase 1 onward.
 */
@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, ErrorState],
  template: `
    <section class="hero">
      <h1>NEPSE Floorsheet Analytics &amp; Signal Platform</h1>
      <p class="tagline">End-of-day floorsheet analytics, indicators, signals, backtests and screeners.</p>
    </section>

    <section class="card">
      <h2>Backend connection</h2>
      @if (system.ping.isLoading()) {
        <app-spinner label="Checking backend" />
      } @else if (system.ping.error()) {
        <app-error-state
          title="Backend unreachable"
          detail="Start the backend and ensure the dev proxy targets it."
          (retry)="system.ping.reload()"
        />
      } @else {
        @let ping = system.ping.value();
        <dl class="kv">
          <dt>Service</dt><dd>{{ ping?.service }}</dd>
          <dt>Status</dt><dd class="up">{{ ping?.status }}</dd>
          <dt>Server time</dt><dd class="numeric">{{ ping?.time }}</dd>
          <dt>Zone</dt><dd>{{ ping?.zone }}</dd>
        </dl>
      }
    </section>
  `,
  styles: [
    `
      .hero { margin-bottom: var(--space-8); }
      h1 { margin: 0 0 var(--space-2); font-size: 1.75rem; }
      .tagline { margin: 0; color: var(--color-text-muted); }
      .card {
        background: var(--color-surface); border: 1px solid var(--color-border);
        border-radius: var(--radius); padding: var(--space-6); box-shadow: var(--shadow);
        max-width: 32rem;
      }
      h2 { margin: 0 0 var(--space-4); font-size: 1rem; }
      .kv { display: grid; grid-template-columns: auto 1fr; gap: var(--space-2) var(--space-6); margin: 0; }
      dt { color: var(--color-text-muted); }
      dd { margin: 0; }
    `,
  ],
})
export class Home {
  protected readonly system = inject(SystemService);
}
