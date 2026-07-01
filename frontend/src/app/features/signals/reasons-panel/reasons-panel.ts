import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SignalDetail } from '../../../core/api/models';

/**
 * The "why" view (§F5): the action + score, a per-strategy contribution bar chart, the narrative
 * reasons, and the dissenting strategies — so every BUY/SELL/HOLD is explained, not asserted.
 */
@Component({
  selector: 'app-reasons-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (signal(); as s) {
      <div class="reasons">
        <header>
          <span class="action" [class]="s.action.toLowerCase()">{{ s.action }}</span>
          <span class="score" [class.up]="s.score > 0" [class.down]="s.score < 0">
            score {{ s.score.toFixed(1) }}
          </span>
          <span class="muted">confidence {{ (s.confidence * 100).toFixed(0) }}%</span>
        </header>

        <h3>Strategy votes</h3>
        <ul class="votes">
          @for (v of s.votes; track v.strategyId) {
            <li>
              <span class="vname">{{ v.name }}</span>
              <span class="bar-track">
                <span class="bar" [class.up]="v.contribution > 0" [class.down]="v.contribution < 0"
                      [style.width.%]="barWidth(v.contribution)"></span>
              </span>
              <span class="vval numeric">{{ v.contribution.toFixed(2) }}</span>
            </li>
          }
        </ul>

        <h3>Reasons</h3>
        <ul class="narratives">
          @for (r of s.reasons; track r.strategyId + r.indicator) {
            <li>
              <strong>{{ r.indicator }}</strong> — {{ r.narrative }}
              <span class="muted">({{ r.observedValue }})</span>
            </li>
          }
        </ul>

        @if (dissents().length) {
          <p class="muted">Dissenting: {{ dissents().join(', ') }}</p>
        }
      </div>
    }
  `,
  styles: [
    `
      .reasons { font-size: 0.875rem; }
      header { display: flex; gap: var(--space-3); align-items: center; margin-bottom: var(--space-4); }
      .action { font-weight: 700; padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); color: #fff; }
      .action.buy { background: var(--color-up); }
      .action.sell { background: var(--color-down); }
      .action.hold { background: var(--color-text-muted); }
      h3 { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--color-text-muted);
        margin: var(--space-4) 0 var(--space-2); }
      .votes { list-style: none; padding: 0; margin: 0; }
      .votes li { display: grid; grid-template-columns: 9rem 1fr 3rem; gap: var(--space-2); align-items: center; padding: 2px 0; }
      .bar-track { background: var(--color-surface-2); border-radius: 2px; height: 0.7rem; position: relative; }
      .bar { display: block; height: 100%; border-radius: 2px; }
      .bar.up { background: var(--color-up); }
      .bar.down { background: var(--color-down); }
      .vval { text-align: right; }
      .narratives { margin: 0; padding-left: 1.1rem; }
      .narratives li { margin-bottom: var(--space-1); }
      .muted { color: var(--color-text-muted); }
    `,
  ],
})
export class ReasonsPanel {
  readonly signal = input.required<SignalDetail>();

  private readonly maxAbs = computed(() =>
    Math.max(0.01, ...this.signal().votes.map((v) => Math.abs(v.contribution))),
  );

  protected readonly dissents = computed(() => {
    const s = this.signal();
    return s.votes
      .filter((v) => (s.action === 'BUY' && v.vote < 0) || (s.action === 'SELL' && v.vote > 0))
      .map((v) => v.name);
  });

  barWidth(contribution: number): number {
    return (Math.abs(contribution) / this.maxAbs()) * 100;
  }
}
