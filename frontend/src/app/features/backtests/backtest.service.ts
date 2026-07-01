import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BacktestTrade, EquityPoint, RunDetail, RunResponse } from '../../core/api/models';

export interface RunRequest {
  symbol: string;
  from: string;
  to: string;
  startingCapital?: number;
  buyThreshold?: number;
  sellThreshold?: number;
}

/** Client for the backtest API. */
@Injectable({ providedIn: 'root' })
export class BacktestApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/backtests`;

  run(req: RunRequest): Observable<RunResponse> {
    return this.http.post<RunResponse>(this.base, req);
  }

  equityCurve(id: string): Observable<EquityPoint[]> {
    return this.http.get<EquityPoint[]>(`${this.base}/${id}/equity-curve`);
  }

  trades(id: string): Observable<BacktestTrade[]> {
    return this.http.get<BacktestTrade[]>(`${this.base}/${id}/trades`);
  }

  compare(ids: string[]): Observable<RunDetail[]> {
    return this.http.post<RunDetail[]>(`${this.base}/compare`, { ids });
  }
}
