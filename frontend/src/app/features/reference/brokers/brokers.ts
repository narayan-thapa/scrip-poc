import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { BrokerDto, PageResponse } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';

/** NEPSE broker registry (paged). */
@Component({
  selector: 'app-brokers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, ErrorState, EmptyState],
  template: `
    <header class="page-head"><h1>Brokers</h1></header>
    @if (brokers.isLoading()) {
      <app-spinner label="Loading brokers" />
    } @else if (brokers.error()) {
      <app-error-state title="Couldn't load brokers" (retry)="brokers.reload()" />
    } @else if (rows().length === 0) {
      <app-empty-state title="No brokers" />
    } @else {
      <table class="data-table">
        <thead><tr><th>ID</th><th>Name</th><th>Status</th></tr></thead>
        <tbody>
          @for (b of rows(); track b.brokerId) {
            <tr><td class="numeric">{{ b.brokerId }}</td><td>{{ b.name }}</td><td>{{ b.status }}</td></tr>
          }
        </tbody>
      </table>
      <nav class="pager">
        <button type="button" [disabled]="!hasPrev()" (click)="prev()">← Prev</button>
        <span>Page {{ (brokers.value()?.page ?? 0) + 1 }} of {{ brokers.value()?.totalPages ?? 1 }}</span>
        <button type="button" [disabled]="!hasNext()" (click)="next()">Next →</button>
      </nav>
    }
  `,
})
export class Brokers {
  protected readonly page = signal(0);
  private readonly size = 50;

  protected readonly brokers = httpResource<PageResponse<BrokerDto>>(
    () => `${environment.apiBaseUrl}/api/v1/brokers?page=${this.page()}&size=${this.size}`,
  );

  protected readonly rows = computed(() => this.brokers.value()?.content ?? []);
  protected readonly hasPrev = computed(() => !(this.brokers.value()?.first ?? true));
  protected readonly hasNext = computed(() => !(this.brokers.value()?.last ?? true));

  prev(): void {
    if (this.hasPrev()) this.page.update((p) => p - 1);
  }
  next(): void {
    if (this.hasNext()) this.page.update((p) => p + 1);
  }
}
