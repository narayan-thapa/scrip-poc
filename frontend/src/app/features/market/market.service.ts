import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BrokerFlowDto, CandleDto, MoversDto, SummaryDto, VolumeProfileDto } from '../../core/api/models';

/** Imperative client for market-data reads (the chart loads candles/profile this way). */
@Injectable({ providedIn: 'root' })
export class MarketApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/market`;

  candles(symbol: string, from: string, to: string): Observable<CandleDto[]> {
    const params = new HttpParams().set('symbol', symbol).set('from', from).set('to', to);
    return this.http.get<CandleDto[]>(`${this.base}/candles`, { params });
  }

  volumeProfile(symbol: string, from: string, to: string): Observable<VolumeProfileDto> {
    const params = new HttpParams().set('symbol', symbol).set('from', from).set('to', to);
    return this.http.get<VolumeProfileDto>(`${this.base}/volume-profile`, { params });
  }

  brokerFlow(symbol: string, date: string): Observable<BrokerFlowDto> {
    const params = new HttpParams().set('symbol', symbol).set('date', date);
    return this.http.get<BrokerFlowDto>(`${this.base}/broker-flow`, { params });
  }

  movers(date: string): Observable<MoversDto> {
    return this.http.get<MoversDto>(`${this.base}/movers`, { params: new HttpParams().set('date', date) });
  }

  summary(date: string): Observable<SummaryDto> {
    return this.http.get<SummaryDto>(`${this.base}/summary`, { params: new HttpParams().set('date', date) });
  }
}
