import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChartApi } from '../chart.service';

/**
 * A small server-rendered chart snapshot for a symbol (last ~6 months). Fetches the PNG as a blob
 * through HttpClient (so the auth interceptor adds the token — a bare {@code <img src>} can't) and
 * shows it as an object URL, revoked on destroy.
 */
@Component({
  selector: 'app-chart-thumbnail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    @if (url()) {
      <a [routerLink]="['/market', symbol()]" [title]="'Open ' + symbol() + ' chart'">
        <img [src]="url()" [alt]="symbol() + ' chart'" class="thumb" />
      </a>
    }
  `,
  styles: [`.thumb { width: 100%; max-width: 420px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); display: block; }`],
})
export class ChartThumbnail {
  readonly symbol = input.required<string>();

  private readonly api = inject(ChartApi);
  protected readonly url = signal<string | null>(null);

  constructor() {
    // Required inputs are bound after construction, so fetch once the view has rendered.
    afterNextRender(() => {
      const to = new Date();
      const from = new Date(to.getTime() - 180 * 24 * 3600 * 1000);
      const iso = (d: Date) => d.toISOString().slice(0, 10);
      this.api.snapshot(this.symbol(), iso(from), iso(to)).subscribe({
        next: (blob) => this.url.set(URL.createObjectURL(blob)),
        error: () => this.url.set(null),
      });
    });

    inject(DestroyRef).onDestroy(() => {
      const u = this.url();
      if (u) {
        URL.revokeObjectURL(u);
      }
    });
  }
}
