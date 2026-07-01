import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ComputeResponse, IndicatorDescriptor } from '../../core/api/models';

/** Client for the indicator catalog + compute API. */
@Injectable({ providedIn: 'root' })
export class IndicatorApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/indicators`;

  catalog(): Observable<IndicatorDescriptor[]> {
    return this.http.get<IndicatorDescriptor[]>(`${this.base}/catalog`);
  }

  compute(
    symbol: string,
    id: string,
    from: string,
    to: string,
    params: Record<string, unknown>,
  ): Observable<ComputeResponse> {
    return this.http.post<ComputeResponse>(`${this.base}/compute`, { symbol, id, from, to, params });
  }
}
