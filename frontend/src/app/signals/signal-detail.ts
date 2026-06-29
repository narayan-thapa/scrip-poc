import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { API } from '../core/api';
import { SignalView } from '../core/models';

@Component({
  selector: 'app-signal-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe, DatePipe],
  template: `
    <a routerLink="/signals" class="link">← All signals</a>

    @if (signal.isLoading()) {
      <div class="state">Loading…</div>
    } @else if (signal.error()) {
      <div class="state error">Signal not found.</div>
    } @else if (signal.value(); as s) {
      <div class="page-head" style="margin-top:0.75rem">
        <h1>
          {{ s.symbol }}
          <span class="badge" [class]="'badge ' + s.action">{{ s.action }}</span>
        </h1>
        <p class="muted">
          {{ s.tradeDate | date: 'fullDate' }} · score
          <strong [class.pos]="s.score > 0" [class.neg]="s.score < 0">{{ s.score | number: '1.1-1' }}</strong>
          · {{ s.barCount }} bars
        </p>
      </div>

      <div class="card">
        <h2>Narrative</h2>
        <p>{{ s.narrative }}</p>
      </div>

      <div class="card" style="padding:0">
        <table>
          <thead>
            <tr>
              <th>Strategy</th>
              <th class="num">Vote</th>
              <th class="num">Confidence</th>
              <th class="num">Weight</th>
              <th class="num">Contribution</th>
            </tr>
          </thead>
          <tbody>
            @for (v of s.votes; track v.strategyId) {
              <tr [style.opacity]="v.applicable ? 1 : 0.45">
                <td>{{ v.strategyId }} · {{ v.label }}</td>
                <td class="num" [class.pos]="v.vote > 0" [class.neg]="v.vote < 0">
                  {{ v.applicable ? (v.vote | number: '1.0-0') : '—' }}
                </td>
                <td class="num">{{ v.confidence | number: '1.2-2' }}</td>
                <td class="num">{{ v.weight | number: '1.2-2' }}</td>
                <td class="num" [class.pos]="v.contribution > 0" [class.neg]="v.contribution < 0">
                  {{ v.contribution | number: '1.2-2' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="card">
        <h2>Top reasons</h2>
        @for (r of s.topReasons; track $index) {
          <p>
            <span class="badge" [class.pos]="r.contribution > 0" [class.neg]="r.contribution < 0">
              {{ r.strategyId }}
            </span>
            {{ r.narrative }}
          </p>
        } @empty {
          <p class="muted">No contributing reasons.</p>
        }
      </div>
    }
  `,
})
export class SignalDetail {
  readonly id = input.required<string>();

  protected readonly signal = httpResource<SignalView>(() => `${API}/signals/${this.id()}`);
}
