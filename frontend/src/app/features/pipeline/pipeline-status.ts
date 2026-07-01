import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { environment } from '../../../environments/environment';
import { StageStatus } from '../../core/api/models';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { ErrorState } from '../../shared/ui/error-state/error-state';

/** Last-run state of the daily EOD pipeline, stage by stage. */
@Component({
  selector: 'app-pipeline-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Spinner, ErrorState],
  template: `
    <header class="page-head">
      <h1>Pipeline status</h1>
      <button type="button" (click)="stages.reload()">Refresh</button>
    </header>

    @if (stages.isLoading()) {
      <app-spinner label="Loading" />
    } @else if (stages.error()) {
      <app-error-state title="Couldn't load pipeline status" (retry)="stages.reload()" />
    } @else {
      <ol class="flow">
        @for (s of stages.value() ?? []; track s.stage) {
          <li [class.done]="s.lastDate">
            <span class="stage">{{ s.stage }}</span>
            <span class="date">{{ s.lastDate ?? 'not run' }}</span>
            @if (s.updatedAt) { <span class="muted">{{ s.updatedAt }}</span> }
          </li>
        }
      </ol>
      <p class="muted">Fed by the ingest → aggregate → indicators → signals events. The 15:01 NPT run drives it daily.</p>
    }
  `,
  styles: [
    `
      .flow { list-style: none; padding: 0; display: flex; gap: var(--space-3); flex-wrap: wrap; }
      .flow li { flex: 1; min-width: 12rem; background: var(--color-surface); border: 1px solid var(--color-border);
        border-radius: var(--radius); padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-1);
        border-top: 3px solid var(--color-border); }
      .flow li.done { border-top-color: var(--color-up); }
      .stage { text-transform: capitalize; font-weight: 700; }
      .date { font-family: var(--font-mono); }
      .muted { color: var(--color-text-muted); font-size: 0.85rem; }
    `,
  ],
})
export class PipelineStatus {
  protected readonly stages = httpResource<StageStatus[]>(
    () => `${environment.apiBaseUrl}/api/v1/system/pipeline/status`,
  );
}
