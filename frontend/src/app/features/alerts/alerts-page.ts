import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AlertRuleDto } from '../../core/api/models';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';

/** Manage alert rules. Phase 8 supports SIGNAL_ACTION (notify when a symbol gets a given action). */
@Component({
  selector: 'app-alerts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, EmptyState],
  template: `
    <header class="page-head"><h1>Alerts</h1></header>

    <section class="card">
      <h2>New signal-action alert</h2>
      <div class="new">
        <input placeholder="Symbol" [value]="symbol()" (change)="symbol.set($any($event.target).value)" />
        <select [value]="action()" (change)="action.set($any($event.target).value)">
          <option value="BUY">BUY</option>
          <option value="SELL">SELL</option>
        </select>
        <button type="button" [disabled]="!symbol()" (click)="create()">Add alert</button>
      </div>
      <p class="muted">Also: any symbol on a watchlist notifies automatically on BUY/SELL.</p>
    </section>

    @if (rules.isLoading()) {
      <app-spinner label="Loading" />
    } @else if ((rules.value() ?? []).length === 0) {
      <app-empty-state title="No alert rules" />
    } @else {
      <table class="data-table">
        <thead><tr><th>Type</th><th>Params</th><th>Enabled</th><th></th></tr></thead>
        <tbody>
          @for (r of rules.value() ?? []; track r.id) {
            <tr>
              <td>{{ r.type }}</td>
              <td class="numeric">{{ paramsText(r) }}</td>
              <td><input type="checkbox" [checked]="r.enabled" (change)="toggle(r, $any($event.target).checked)" /></td>
              <td><button type="button" (click)="remove(r.id)">Delete</button></td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [
    `
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); margin-bottom: var(--space-6); max-width: 34rem; }
      .card h2 { margin: 0 0 var(--space-3); font-size: 1rem; }
      .new { display: flex; gap: var(--space-2); }
      input, select { padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
        background: var(--color-surface); color: var(--color-text); }
      .muted { color: var(--color-text-muted); font-size: 0.85rem; }
    `,
  ],
})
export class AlertsPage {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/alerts`;

  protected readonly symbol = signal('');
  protected readonly action = signal('BUY');
  protected readonly rules = httpResource<AlertRuleDto[]>(() => this.base);

  create(): void {
    this.http
      .post(this.base, { type: 'SIGNAL_ACTION', params: { symbol: this.symbol().toUpperCase(), action: this.action() } })
      .subscribe(() => {
        this.symbol.set('');
        this.rules.reload();
      });
  }

  toggle(rule: AlertRuleDto, enabled: boolean): void {
    this.http.patch(`${this.base}/${rule.id}`, { enabled }).subscribe(() => this.rules.reload());
  }

  remove(id: string): void {
    this.http.delete(`${this.base}/${id}`).subscribe(() => this.rules.reload());
  }

  paramsText(r: AlertRuleDto): string {
    return Object.entries(r.params ?? {})
      .map(([k, v]) => `${k}=${v}`)
      .join(', ');
  }
}
