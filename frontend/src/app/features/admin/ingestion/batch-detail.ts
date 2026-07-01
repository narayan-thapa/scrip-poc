import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BatchDetail as BatchDetailModel } from '../../../core/api/models';
import { NotificationService } from '../../../core/notification/notification.service';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { IngestionApi } from './ingestion.service';

/** Per-batch detail: per-date job rows with counts, a retry action, and rejection downloads. */
@Component({
  selector: 'app-batch-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState],
  template: `
    <p><a routerLink="/admin/ingestion">← Ingestion</a></p>
    @if (detail.isLoading()) {
      <app-spinner label="Loading batch" />
    } @else if (detail.error()) {
      <app-error-state title="Batch not found" [retryable]="false" />
    } @else {
      @let d = detail.value();
      <header class="page-head">
        <h1>Batch {{ d?.batch?.dateFrom }} → {{ d?.batch?.dateTo }}</h1>
        <button type="button" (click)="retry()">Retry failed dates</button>
      </header>
      <p>Status: <strong>{{ d?.batch?.status }}</strong> · {{ d?.batch?.fileCount }} file(s)</p>

      <table class="data-table">
        <thead>
          <tr><th>Date</th><th>File</th><th>Status</th><th>Read</th><th>Accepted</th>
            <th>Rejected</th><th>Duplicate</th><th></th></tr>
        </thead>
        <tbody>
          @for (j of d?.jobs ?? []; track j.id) {
            <tr>
              <td>{{ j.tradeDate }}</td>
              <td>{{ j.sourceFilename }}</td>
              <td>{{ j.status }}</td>
              <td class="numeric">{{ j.rowsRead }}</td>
              <td class="numeric up">{{ j.rowsAccepted }}</td>
              <td class="numeric" [class.down]="j.rowsRejected > 0">{{ j.rowsRejected }}</td>
              <td class="numeric">{{ j.rowsDuplicate }}</td>
              <td>
                @if (j.rowsRejected > 0) {
                  <a [href]="rejectionsUrl(j.id)" download>Rejections CSV</a>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [`:host { display: block; } button { font-size: 0.875rem; }`],
})
export class BatchDetail {
  readonly id = input.required<string>();

  private readonly api = inject(IngestionApi);
  private readonly notify = inject(NotificationService);

  protected readonly detail = httpResource<BatchDetailModel>(
    () => `${environment.apiBaseUrl}/api/v1/ingestion/batches/${this.id()}`,
  );

  retry(): void {
    this.api.retry(this.id()).subscribe(() => {
      this.notify.info('Retrying failed dates…');
      this.detail.reload();
    });
  }

  rejectionsUrl(jobId: string): string {
    return this.api.rejectionsCsvUrl(jobId);
  }
}
