import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';

import { API, IngestionApi } from '../core/api';
import { BatchSubmissionResult, IngestionBatchView, IngestionJobView, PageResponse } from '../core/models';

/** Mirrors the backend guardrails in {@code IngestionProperties} so the UI fails fast. */
const MAX_FILE_BYTES = 52_428_800; // 50 MiB
const MAX_BATCH_FILES = 400;
const MAX_BATCH_BYTES = 1_073_741_824; // 1 GiB
const FILENAME_PATTERN = /^\d{4}-\d{2}-\d{2}\.csv$/; // YYYY-MM-DD.csv (matches FilenameValidator)

/** A file the operator has staged, with the result of client-side pre-validation. */
interface StagedFile {
  file: File;
  valid: boolean;
  reason: string | null;
}

const EMPTY_PAGE: PageResponse<IngestionBatchView> = {
  content: [],
  page: 0,
  size: 0,
  totalElements: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-batch-upload',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <div class="page-head">
      <h1>Batch upload</h1>
      <p class="muted">Backfill daily floorsheet CSVs · files must be named <code>YYYY-MM-DD.csv</code></p>
    </div>

    <div class="card">
      <div
        class="dropzone"
        [class.dragging]="dragging()"
        (dragover)="onDragOver($event)"
        (dragleave)="onDragLeave($event)"
        (drop)="onDrop($event)"
        (click)="fileInput.click()"
      >
        <input
          #fileInput
          type="file"
          multiple
          accept=".csv"
          hidden
          (change)="onPicked($event, fileInput)"
        />
        <p class="dz-title">Drop CSV files here or <span class="link">browse</span></p>
        <p class="muted">
          Up to {{ MAX_BATCH_FILES }} files · {{ formatBytes(MAX_FILE_BYTES) }} per file ·
          {{ formatBytes(MAX_BATCH_BYTES) }} per batch
        </p>
      </div>

      @if (staged().length) {
        <div class="staged-head">
          <span>
            {{ staged().length }} file{{ staged().length === 1 ? '' : 's' }} staged ·
            {{ validFiles().length }} valid · {{ formatBytes(totalBytes()) }}
          </span>
          <button class="ghost" (click)="clear()" [disabled]="uploading()">Clear all</button>
        </div>

        <ul class="file-list">
          @for (s of staged(); track s.file.name; let i = $index) {
            <li [class.invalid]="!s.valid">
              <span class="fname">{{ s.file.name }}</span>
              <span class="muted fsize">{{ formatBytes(s.file.size) }}</span>
              @if (!s.valid) {
                <span class="error freason">{{ s.reason }}</span>
              }
              <button class="x" (click)="removeAt(i)" [disabled]="uploading()" aria-label="Remove">×</button>
            </li>
          }
        </ul>

        @if (batchError(); as msg) {
          <p class="error">{{ msg }}</p>
        }

        <div class="row" style="justify-content:flex-end; margin-top:0.75rem">
          <button (click)="upload()" [disabled]="!canUpload()">
            {{ uploading() ? 'Uploading…' : 'Upload ' + validFiles().length + ' file' + (validFiles().length === 1 ? '' : 's') }}
          </button>
        </div>
      }

      @if (error(); as msg) {
        <p class="error">{{ msg }}</p>
      }
    </div>

    @if (result(); as r) {
      <div class="card">
        <h2>Batch #{{ r.batchId }} submitted</h2>
        <p class="muted">Files queued for asynchronous ingestion (oldest trade date first).</p>
        <table>
          <thead>
            <tr><th>File</th><th>Trade date</th><th>Intake</th><th>Detail</th></tr>
          </thead>
          <tbody>
            @for (f of r.files; track f.filename) {
              <tr>
                <td>{{ f.filename }}</td>
                <td>{{ f.tradeDate ? (f.tradeDate | date: 'mediumDate') : '—' }}</td>
                <td>
                  <span class="badge" [class.completed]="f.accepted" [class.failed]="!f.accepted">
                    {{ f.accepted ? 'ACCEPTED' : 'REJECTED' }}
                  </span>
                </td>
                <td class="muted">{{ f.message }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    <div class="card" style="padding:0">
      <table>
        <thead>
          <tr>
            <th>Batch</th><th>Files</th><th>Range</th><th>Status</th>
            <th>Submitted</th><th></th>
          </tr>
        </thead>
        <tbody>
          @for (b of batches.value().content; track b.id) {
            <tr class="clickable" (click)="toggleBatch(b.id)">
              <td>#{{ b.id }}</td>
              <td class="num">{{ b.fileCount }}</td>
              <td>{{ b.dateFrom }} → {{ b.dateTo }}</td>
              <td><span [class]="'badge ' + b.status.toLowerCase()">{{ b.status }}</span></td>
              <td>{{ b.submittedAt | date: 'short' }}</td>
              <td class="num">
                @if (b.status === 'FAILED' || b.status === 'PARTIAL') {
                  <button class="ghost" (click)="retry(b.id, $event)" [disabled]="retrying() === b.id">
                    {{ retrying() === b.id ? 'Retrying…' : 'Retry' }}
                  </button>
                }
              </td>
            </tr>
            @if (selectedBatchId() === b.id) {
              <tr>
                <td colspan="6" style="background:var(--surface-2)">
                  @if (batchFiles.isLoading()) {
                    <span class="muted">Loading files…</span>
                  } @else {
                    <table class="inner">
                      <thead>
                        <tr>
                          <th>File</th><th>Date</th>
                          <th class="num">Read</th><th class="num">Accepted</th>
                          <th class="num">Rejected</th><th class="num">Dup</th><th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (j of batchFiles.value(); track j.id) {
                          <tr>
                            <td>{{ j.sourceFilename }}</td>
                            <td>{{ j.tradeDate | date: 'mediumDate' }}</td>
                            <td class="num">{{ j.rowsRead }}</td>
                            <td class="num pos">{{ j.rowsAccepted }}</td>
                            <td class="num" [class.neg]="j.rowsRejected > 0">{{ j.rowsRejected }}</td>
                            <td class="num">{{ j.rowsDuplicate }}</td>
                            <td><span [class]="'badge ' + j.status.toLowerCase()">{{ j.status }}</span></td>
                          </tr>
                        } @empty {
                          <tr><td colspan="7" class="muted">No files in this batch.</td></tr>
                        }
                      </tbody>
                    </table>
                  }
                </td>
              </tr>
            }
          } @empty {
            <tr><td colspan="6" class="muted">No batches uploaded yet.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .dropzone {
      border: 2px dashed var(--border);
      border-radius: 12px;
      padding: 2rem 1rem;
      text-align: center;
      cursor: pointer;
      transition: border-color 0.15s, background 0.15s;
    }
    .dropzone:hover,
    .dropzone.dragging {
      border-color: var(--accent);
      background: var(--accent-soft);
    }
    .dz-title {
      font-size: 1rem;
      margin: 0 0 0.35rem;
    }
    .staged-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin: 1rem 0 0.5rem;
    }
    .file-list {
      list-style: none;
      margin: 0;
      padding: 0;
      max-height: 320px;
      overflow-y: auto;
    }
    .file-list li {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.4rem 0.5rem;
      border-bottom: 1px solid var(--border);
    }
    .file-list li.invalid .fname {
      color: var(--sell);
      text-decoration: line-through;
    }
    .fname {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .fsize {
      font-variant-numeric: tabular-nums;
    }
    .freason {
      font-size: 0.8rem;
    }
    .x {
      background: transparent;
      color: var(--text-2);
      padding: 0 0.4rem;
      font-size: 1.1rem;
      line-height: 1;
    }
    .x:hover {
      color: var(--sell);
    }
    code {
      background: var(--surface-2);
      padding: 0.05rem 0.35rem;
      border-radius: 5px;
    }
    table.inner {
      margin: 0;
    }
    .badge.queued,
    .badge.skipped {
      background: var(--surface-1);
      color: var(--text-2);
    }
    .badge.running {
      background: var(--accent-soft);
      color: var(--accent);
    }
    .badge.completed {
      background: var(--buy-soft);
      color: var(--buy);
    }
    .badge.partial {
      background: var(--hold-soft);
      color: var(--hold);
    }
    .badge.failed {
      background: var(--sell-soft);
      color: var(--sell);
    }
  `,
})
export class BatchUpload {
  private readonly api = inject(IngestionApi);

  protected readonly MAX_BATCH_FILES = MAX_BATCH_FILES;
  protected readonly MAX_FILE_BYTES = MAX_FILE_BYTES;
  protected readonly MAX_BATCH_BYTES = MAX_BATCH_BYTES;

  protected readonly staged = signal<StagedFile[]>([]);
  protected readonly dragging = signal(false);
  protected readonly uploading = signal(false);
  protected readonly retrying = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<BatchSubmissionResult | null>(null);
  protected readonly selectedBatchId = signal<number | null>(null);

  protected readonly batches = httpResource<PageResponse<IngestionBatchView>>(
    () => `${API}/ingestion/batches?size=10`,
    { defaultValue: EMPTY_PAGE },
  );

  protected readonly batchFiles = httpResource<IngestionJobView[]>(
    () => {
      const id = this.selectedBatchId();
      return id != null ? `${API}/ingestion/batches/${id}/files` : undefined;
    },
    { defaultValue: [] },
  );

  protected readonly validFiles = computed(() => this.staged().filter((s) => s.valid).map((s) => s.file));
  protected readonly totalBytes = computed(() => this.staged().reduce((sum, s) => sum + s.file.size, 0));

  /** A batch-level error (counts/total size) that blocks upload even if each file is individually valid. */
  protected readonly batchError = computed<string | null>(() => {
    const valid = this.validFiles();
    if (valid.length > MAX_BATCH_FILES) {
      return `Too many files: ${valid.length} (max ${MAX_BATCH_FILES} per batch).`;
    }
    if (this.totalBytes() > MAX_BATCH_BYTES) {
      return `Batch too large: ${this.formatBytes(this.totalBytes())} (max ${this.formatBytes(MAX_BATCH_BYTES)}).`;
    }
    return null;
  });

  protected readonly canUpload = computed(
    () => !this.uploading() && this.validFiles().length > 0 && this.batchError() === null,
  );

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(event.dataTransfer?.files ?? null);
  }

  protected onPicked(event: Event, input: HTMLInputElement): void {
    this.addFiles((event.target as HTMLInputElement).files);
    input.value = ''; // allow re-picking the same file
  }

  protected removeAt(index: number): void {
    this.staged.update((list) => list.filter((_, i) => i !== index));
  }

  protected clear(): void {
    this.staged.set([]);
  }

  protected upload(): void {
    if (!this.canUpload()) {
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.api.uploadBatch(this.validFiles()).subscribe({
      next: (r) => {
        this.result.set(r);
        this.staged.set([]);
        this.uploading.set(false);
        this.batches.reload();
        this.selectedBatchId.set(r.batchId);
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Upload failed. Check file names and sizes, then retry.');
        this.uploading.set(false);
      },
    });
  }

  protected toggleBatch(id: number): void {
    this.selectedBatchId.update((current) => (current === id ? null : id));
  }

  protected retry(id: number, event: Event): void {
    event.stopPropagation(); // don't toggle the row's file list
    this.retrying.set(id);
    this.api.retryBatch(id).subscribe({
      next: () => {
        this.retrying.set(null);
        this.batches.reload();
        if (this.selectedBatchId() === id) {
          this.batchFiles.reload();
        }
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Retry failed.');
        this.retrying.set(null);
      },
    });
  }

  /** De-duplicates by filename (one file per trade date) and validates name + size client-side. */
  private addFiles(files: FileList | null): void {
    if (!files || files.length === 0) {
      return;
    }
    this.error.set(null);
    this.staged.update((existing) => {
      const seen = new Set(existing.map((s) => s.file.name));
      const next = [...existing];
      for (const file of Array.from(files)) {
        if (seen.has(file.name)) {
          continue;
        }
        seen.add(file.name);
        next.push({ file, ...this.validate(file) });
      }
      return next;
    });
  }

  private validate(file: File): { valid: boolean; reason: string | null } {
    if (!FILENAME_PATTERN.test(file.name)) {
      return { valid: false, reason: 'Name must be YYYY-MM-DD.csv' };
    }
    if (file.size === 0) {
      return { valid: false, reason: 'File is empty' };
    }
    if (file.size > MAX_FILE_BYTES) {
      return { valid: false, reason: `Exceeds ${this.formatBytes(MAX_FILE_BYTES)}` };
    }
    return { valid: true, reason: null };
  }

  protected formatBytes(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const units = ['KiB', 'MiB', 'GiB'];
    let value = bytes / 1024;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return `${value.toFixed(value >= 10 || value % 1 === 0 ? 0 : 1)} ${units[unit]}`;
  }
}
