import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SignalSummary } from '../../core/api/models';

/** Client for signal reads (the chart loads a symbol's signals imperatively for markers). */
@Injectable({ providedIn: 'root' })
export class SignalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/signals`;

  forSymbol(symbol: string, limit = 120): Observable<SignalSummary[]> {
    return this.http.get<SignalSummary[]>(`${this.base}/symbol/${symbol}?limit=${limit}`);
  }
}
