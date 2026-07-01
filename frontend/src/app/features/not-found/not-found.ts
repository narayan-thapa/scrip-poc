import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Catch-all 404 route. */
@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="nf">
      <h1>404</h1>
      <p>That page doesn't exist.</p>
      <a routerLink="/">Back to home</a>
    </div>
  `,
  styles: [
    `
      .nf { text-align: center; padding: var(--space-8); }
      h1 { font-size: 3rem; margin: 0; color: var(--color-text-muted); }
    `,
  ],
})
export class NotFound {}
