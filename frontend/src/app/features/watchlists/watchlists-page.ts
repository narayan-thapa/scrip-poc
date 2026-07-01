import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { WatchlistDto } from '../../core/api/models';
import { Spinner } from '../../shared/ui/spinner/spinner';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';

/** Manage watchlists: create, add/remove symbols, delete. Watched symbols notify on BUY/SELL. */
@Component({
  selector: 'app-watchlists-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Spinner, EmptyState],
  template: `
    <header class="page-head">
      <h1>Watchlists</h1>
      <form (submit)="create(nameInput.value); nameInput.value=''; $event.preventDefault()">
        <input #nameInput placeholder="New watchlist name" />
        <button type="submit">Create</button>
      </form>
    </header>

    @if (lists.isLoading()) {
      <app-spinner label="Loading" />
    } @else if ((lists.value() ?? []).length === 0) {
      <app-empty-state title="No watchlists yet" hint="Create one above." />
    } @else {
      @for (w of lists.value() ?? []; track w.id) {
        <section class="card">
          <header class="wl-head">
            <h2>{{ w.name }}</h2>
            <button type="button" (click)="remove(w.id)">Delete</button>
          </header>
          <div class="symbols">
            @for (s of w.symbols; track s) {
              <span class="chip">
                <a [routerLink]="['/market', s]">{{ s }}</a>
                <button type="button" (click)="removeSymbol(w.id, s)" aria-label="Remove">×</button>
              </span>
            }
          </div>
          <form (submit)="addSymbol(w.id, symInput.value); symInput.value=''; $event.preventDefault()">
            <input #symInput placeholder="Add symbol (e.g. NABIL)" />
            <button type="submit">Add</button>
          </form>
        </section>
      }
    }
  `,
  styles: [
    `
      .page-head form, .card form { display: flex; gap: var(--space-2); }
      input { padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
        background: var(--color-surface); color: var(--color-text); }
      .card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius);
        padding: var(--space-4); box-shadow: var(--shadow); margin-bottom: var(--space-4); max-width: 40rem; }
      .wl-head { display: flex; justify-content: space-between; align-items: center; }
      .wl-head h2 { margin: 0; font-size: 1.05rem; }
      .symbols { display: flex; flex-wrap: wrap; gap: var(--space-2); margin: var(--space-3) 0; }
      .chip { display: inline-flex; gap: var(--space-1); align-items: center; border: 1px solid var(--color-border);
        border-radius: var(--radius-sm); padding: 1px var(--space-2); }
      .chip button { border: 0; background: transparent; cursor: pointer; color: var(--color-text-muted); }
    `,
  ],
})
export class WatchlistsPage {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/watchlists`;

  protected readonly lists = httpResource<WatchlistDto[]>(() => this.base);

  create(name: string): void {
    if (name.trim()) {
      this.http.post(this.base, { name: name.trim() }).subscribe(() => this.lists.reload());
    }
  }

  remove(id: string): void {
    this.http.delete(`${this.base}/${id}`).subscribe(() => this.lists.reload());
  }

  addSymbol(id: string, symbol: string): void {
    if (symbol.trim()) {
      this.http.post(`${this.base}/${id}/items`, { symbol: symbol.trim() }).subscribe(() => this.lists.reload());
    }
  }

  removeSymbol(id: string, symbol: string): void {
    this.http.delete(`${this.base}/${id}/items/${symbol}`).subscribe(() => this.lists.reload());
  }
}
