import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { BacktestRunView, RunDetail } from '../../../core/api/models';
import { NotificationService } from '../../../core/notification/notification.service';
import { Spinner } from '../../../shared/ui/spinner/spinner';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { BacktestApi } from '../backtest.service';

/** Configure + run a backtest, browse recent runs, and compare a selection side by side. */
@Component({
  selector: 'app-backtests-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ReactiveFormsModule, Spinner, EmptyState],
  templateUrl: './backtests-page.html',
  styleUrl: './backtests-page.css',
})
export class BacktestsPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(BacktestApi);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);

  protected readonly running = signal(false);
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly comparison = signal<RunDetail[] | null>(null);

  protected readonly runs = httpResource<BacktestRunView[]>(
    () => `${environment.apiBaseUrl}/api/v1/backtests?limit=50`,
  );

  protected readonly form = this.fb.nonNullable.group({
    symbol: ['', Validators.required],
    from: ['2024-01-01', Validators.required],
    to: [new Date().toISOString().slice(0, 10), Validators.required],
    startingCapital: [1_000_000, [Validators.required, Validators.min(1)]],
    buyThreshold: [35, Validators.required],
    sellThreshold: [35, Validators.required],
  });

  run(): void {
    if (this.form.invalid || this.running()) {
      return;
    }
    this.running.set(true);
    this.api.run(this.form.getRawValue()).subscribe({
      next: (res) => {
        this.running.set(false);
        this.notify.success('Backtest complete.');
        this.router.navigate(['/backtests', res.runId]);
      },
      error: () => this.running.set(false),
    });
  }

  toggle(id: string): void {
    this.selected.update((set) => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  compare(): void {
    const ids = [...this.selected()];
    if (ids.length < 2) {
      return;
    }
    this.api.compare(ids).subscribe((rows) => this.comparison.set(rows));
  }
}
