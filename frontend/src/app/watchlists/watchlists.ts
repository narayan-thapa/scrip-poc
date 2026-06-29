import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';

import { API } from '../core/api';
import { WatchlistView } from '../core/models';

@Component({
  selector: 'app-watchlists',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-head">
      <h1>Watchlists</h1>
      <p class="muted">Track scrips and drive WATCHLIST_SIGNAL alerts</p>
    </div>

    <div class="card">
      <div class="row">
        <div class="field" style="flex:1">
          <label>New watchlist</label>
          <input [value]="newName()" (input)="newName.set(value($event))" placeholder="e.g. Banking" />
        </div>
        <button (click)="create()" [disabled]="!newName().trim()">Create</button>
      </div>
      @if (error()) {
        <p class="error">{{ error() }}</p>
      }
    </div>

    @for (w of lists.value(); track w.id) {
      <div class="card">
        <div class="row" style="justify-content:space-between">
          <h2>{{ w.name }}</h2>
          <button class="ghost" (click)="remove(w.id)">Delete</button>
        </div>
        <div style="display:flex; flex-wrap:wrap; gap:0.4rem; margin:0.5rem 0">
          @for (item of w.items; track item.symbol) {
            <span class="chip">{{ item.symbol }} <a (click)="removeItem(w.id, item.symbol)">×</a></span>
          } @empty {
            <span class="muted">No symbols yet.</span>
          }
        </div>
        <div class="row">
          <input [value]="draft(w.id)" (input)="setDraft(w.id, value($event))" placeholder="Add symbol e.g. NABIL" />
          <button class="ghost" (click)="addItem(w.id)" [disabled]="!draft(w.id).trim()">Add</button>
        </div>
      </div>
    } @empty {
      <div class="state">No watchlists yet — create one above.</div>
    }
  `,
  styles: `
    .chip {
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: 999px;
      padding: 0.2rem 0.6rem;
    }
    .chip a {
      color: var(--sell);
      cursor: pointer;
      margin-left: 0.3rem;
      font-weight: 700;
    }
  `,
})
export class Watchlists {
  private readonly http = inject(HttpClient);

  protected readonly newName = signal('');
  protected readonly error = signal<string | null>(null);
  private readonly drafts = signal<Record<string, string>>({});

  protected readonly lists = httpResource<WatchlistView[]>(() => `${API}/watchlists`, { defaultValue: [] });

  protected create(): void {
    this.error.set(null);
    this.http.post(`${API}/watchlists`, { name: this.newName().trim() }).subscribe({
      next: () => {
        this.newName.set('');
        this.lists.reload();
      },
      error: (e) => this.error.set(e?.error?.message ?? 'Could not create watchlist'),
    });
  }

  protected remove(id: string): void {
    this.http.delete(`${API}/watchlists/${id}`).subscribe({ next: () => this.lists.reload() });
  }

  protected addItem(id: string): void {
    const symbol = this.draft(id).trim();
    if (!symbol) {
      return;
    }
    this.http.post(`${API}/watchlists/${id}/items`, { symbol }).subscribe({
      next: () => {
        this.setDraft(id, '');
        this.lists.reload();
      },
      error: (e) => this.error.set(e?.error?.message ?? 'Could not add symbol'),
    });
  }

  protected removeItem(id: string, symbol: string): void {
    this.http.delete(`${API}/watchlists/${id}/items/${symbol}`).subscribe({ next: () => this.lists.reload() });
  }

  protected draft(id: string): string {
    return this.drafts()[id] ?? '';
  }

  protected setDraft(id: string, value: string): void {
    this.drafts.update((d) => ({ ...d, [id]: value }));
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
