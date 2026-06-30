import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BatchSubmissionResult, BacktestRequest, BacktestRunView } from './models';

/** Base path for the versioned REST API (proxied to the backend in dev). */
export const API = '/api/v1';

/**
 * Imperative API calls (mutations). Reactive GETs are expressed with `httpResource`
 * directly in the components so loading/error state is signal-driven.
 */
@Injectable({ providedIn: 'root' })
export class BacktestApi {
  private readonly http = inject(HttpClient);

  run(request: BacktestRequest): Observable<BacktestRunView> {
    return this.http.post<BacktestRunView>(`${API}/backtests`, request);
  }
}

/** Admin ingestion mutations (multipart batch upload + retry). ADMIN-gated server-side. */
@Injectable({ providedIn: 'root' })
export class IngestionApi {
  private readonly http = inject(HttpClient);

  /**
   * Uploads multiple CSV files as one batch. The browser sets the multipart
   * Content-Type (with boundary) from the {@link FormData} body — do not set it
   * manually or the boundary is lost.
   */
  uploadBatch(files: File[]): Observable<BatchSubmissionResult> {
    const form = new FormData();
    for (const file of files) {
      form.append('files', file, file.name);
    }
    return this.http.post<BatchSubmissionResult>(`${API}/ingestion/uploads/batch`, form);
  }

  retryBatch(batchId: number): Observable<void> {
    return this.http.post<void>(`${API}/ingestion/batches/${batchId}/retry`, null);
  }
}
