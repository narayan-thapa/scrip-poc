import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChartPayload } from '../../core/api/models';

/** Client for the composite chart payload (F7): candles + volume + profile + markers in one call. */
@Injectable({ providedIn: 'root' })
export class ChartApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/charts`;

  chart(symbol: string, from: string, to: string, overlays: string[] = ['volprofile']): Observable<ChartPayload> {
    let params = new HttpParams().set('from', from).set('to', to);
    for (const o of overlays) {
      params = params.append('overlays', o);
    }
    return this.http.get<ChartPayload>(`${this.base}/${symbol}`, { params });
  }

  /** Server-side PNG snapshot as a blob (fetched with the auth token, unlike a bare <img src>). */
  snapshot(symbol: string, from: string, to: string): Observable<Blob> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get(`${this.base}/${symbol}/snapshot.png`, { params, responseType: 'blob' });
  }
}
