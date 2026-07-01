import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { BatchDetail, IntakeResult } from '../../../core/api/models';

/** Client for the admin ingestion API (upload, reprocess, inspect). */
@Injectable({ providedIn: 'root' })
export class IngestionApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/ingestion`;

  /** Filename contract enforced on both ends: YYYY-MM-DD.csv. */
  static readonly FILENAME_RE = /^\d{4}-\d{2}-\d{2}\.csv$/;

  uploadBatch(files: File[]): Observable<IntakeResult> {
    const form = new FormData();
    for (const f of files) {
      form.append('files', f, f.name);
    }
    return this.http.post<IntakeResult>(`${this.base}/uploads/batch`, form);
  }

  getBatch(id: string): Observable<BatchDetail> {
    return this.http.get<BatchDetail>(`${this.base}/batches/${id}`);
  }

  retry(batchId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/batches/${batchId}/retry`, {});
  }

  reprocess(from: string, to: string): Observable<string> {
    return this.http.post(`${this.base}/reprocess`, { from, to }, { responseType: 'text' });
  }

  rejectionsCsvUrl(jobId: string): string {
    return `${this.base}/jobs/${jobId}/rejections.csv`;
  }
}
