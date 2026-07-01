import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { InstrumentDto, PageResponse } from '../../../core/api/models';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';

/** Paged, filterable instrument browser. Filters + page are signals that drive the httpResource URL. */
@Component({
  selector: 'app-instruments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, EmptyState],
  templateUrl: './instruments.html',
  styleUrl: './instruments.css',
})
export class Instruments {
  protected readonly q = signal('');
  protected readonly sector = signal('');
  protected readonly status = signal('');
  protected readonly page = signal(0);
  private readonly size = 20;

  protected readonly instruments = httpResource<PageResponse<InstrumentDto>>(() => {
    const params = new URLSearchParams();
    if (this.q()) params.set('q', this.q());
    if (this.sector()) params.set('sector', this.sector());
    if (this.status()) params.set('status', this.status());
    params.set('page', String(this.page()));
    params.set('size', String(this.size));
    return `${environment.apiBaseUrl}/api/v1/instruments?${params.toString()}`;
  });

  protected readonly rows = computed(() => this.instruments.value()?.content ?? []);
  protected readonly hasPrev = computed(() => !(this.instruments.value()?.first ?? true));
  protected readonly hasNext = computed(() => !(this.instruments.value()?.last ?? true));

  applyQuery(value: string): void {
    this.page.set(0);
    this.q.set(value.trim());
  }

  applyStatus(value: string): void {
    this.page.set(0);
    this.status.set(value);
  }

  prev(): void {
    if (this.hasPrev()) this.page.update((p) => p - 1);
  }

  next(): void {
    if (this.hasNext()) this.page.update((p) => p + 1);
  }
}
