import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BatchSummary, PageResponse } from '../../../core/api/models';
import { NotificationService } from '../../../core/notification/notification.service';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { IngestionApi } from './ingestion.service';

interface Candidate {
  file: File;
  valid: boolean;
}

/** Admin ingestion console: validated multi-file upload + a list of recent batches. */
@Component({
  selector: 'app-ingestion-console',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, ErrorState, EmptyState],
  templateUrl: './ingestion-console.html',
  styleUrl: './ingestion-console.css',
})
export class IngestionConsole {
  private readonly api = inject(IngestionApi);
  private readonly notify = inject(NotificationService);

  protected readonly candidates = signal<Candidate[]>([]);
  protected readonly submitting = signal(false);

  protected readonly allValid = computed(
    () => this.candidates().length > 0 && this.candidates().every((c) => c.valid),
  );

  protected readonly batches = httpResource<PageResponse<BatchSummary>>(
    () => `${environment.apiBaseUrl}/api/v1/ingestion/batches?page=0&size=20`,
  );

  protected readonly rows = computed(() => this.batches.value()?.content ?? []);

  onSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.candidates.set(
      files.map((file) => ({ file, valid: IngestionApi.FILENAME_RE.test(file.name) })),
    );
  }

  submit(): void {
    if (!this.allValid() || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.api.uploadBatch(this.candidates().map((c) => c.file)).subscribe({
      next: (result) => {
        this.notify.success(`Queued ${result.files.length} file(s) for ingestion.`);
        this.candidates.set([]);
        this.submitting.set(false);
        this.batches.reload();
      },
      error: () => this.submitting.set(false),
    });
  }
}
