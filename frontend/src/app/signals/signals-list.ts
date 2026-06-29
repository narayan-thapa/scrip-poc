import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { API } from '../core/api';
import { SignalAction, SignalView } from '../core/models';

const ACTIONS: readonly (SignalAction | '')[] = ['', 'BUY', 'SELL', 'HOLD'];

@Component({
  selector: 'app-signals-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe, DatePipe],
  template: `
    <div class="page-head">
      <h1>Latest signals</h1>
      <p class="muted">Most recent generated trade date · confluence of 8 strategies</p>
    </div>

    <div class="toolbar">
      @for (a of actions; track a) {
        <button class="ghost" [class.active]="action() === a" (click)="action.set(a)">
          {{ a || 'ALL' }}
        </button>
      }
    </div>

    @if (signals.isLoading()) {
      <div class="state">Loading…</div>
    } @else if (signals.error()) {
      <div class="state error">Could not load signals. Is the backend running?</div>
    } @else if (signals.value().length === 0) {
      <div class="state">No signals yet. Generate them from a date with ingested data.</div>
    } @else {
      <div class="card" style="padding:0">
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Action</th>
              <th class="num">Score</th>
              <th>Date</th>
              <th>Why</th>
            </tr>
          </thead>
          <tbody>
            @for (s of signals.value(); track s.id) {
              <tr class="clickable" [routerLink]="['/signals', s.id]">
                <td><strong>{{ s.symbol }}</strong></td>
                <td><span class="badge" [class]="'badge ' + s.action">{{ s.action }}</span></td>
                <td class="num" [class.pos]="s.score > 0" [class.neg]="s.score < 0">
                  {{ s.score | number: '1.1-1' }}
                </td>
                <td>{{ s.tradeDate | date: 'mediumDate' }}</td>
                <td class="muted">{{ s.narrative }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class SignalsList {
  protected readonly actions = ACTIONS;
  protected readonly action = signal<SignalAction | ''>('');

  protected readonly signals = httpResource<SignalView[]>(
    () => `${API}/signals/latest${this.action() ? `?action=${this.action()}` : ''}`,
    { defaultValue: [] },
  );
}
