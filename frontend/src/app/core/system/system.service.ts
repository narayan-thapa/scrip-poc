import { httpResource } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';
import { PingResponse } from '../api/models';

/**
 * Reactive view of backend liveness via Angular's `httpResource`. The shell binds to `ping.value()`
 * / `ping.isLoading()` / `ping.error()` to show a connection badge — this is the frontend half of
 * the Phase 0 contract handshake against `GET /api/v1/system/ping`.
 */
@Injectable({ providedIn: 'root' })
export class SystemService {
  readonly ping = httpResource<PingResponse>(() => `${environment.apiBaseUrl}/api/v1/system/ping`);
}
