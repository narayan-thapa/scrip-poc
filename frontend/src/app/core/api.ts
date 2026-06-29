import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BacktestRequest, BacktestRunView } from './models';

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
