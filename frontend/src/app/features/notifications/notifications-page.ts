import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationCenter } from '../../core/notification/notification-center';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';

/** Notification feed (read/unread) with mark-read + mark-all-read. Live updates arrive over SSE. */
@Component({
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EmptyState],
  template: `
    <header class="page-head">
      <h1>Notifications</h1>
      <button type="button" (click)="center.markAllRead()">Mark all read</button>
    </header>

    @if (center.items().length === 0) {
      <app-empty-state title="No notifications" hint="Signals on your watchlist symbols will appear here." />
    } @else {
      <ul class="feed">
        @for (n of center.items(); track n.id) {
          <li [class.unread]="!n.read">
            <div class="row">
              <strong>{{ n.title }}</strong>
              <span class="muted">{{ n.createdAt }}</span>
            </div>
            <p class="body">{{ n.body }}</p>
            <div class="actions">
              @if (n.signalId) { <a [routerLink]="['/signals', n.signalId]">View signal →</a> }
              @if (!n.read) { <button type="button" (click)="center.markRead(n.id)">Mark read</button> }
            </div>
          </li>
        }
      </ul>
    }
  `,
  styles: [
    `
      .feed { list-style: none; padding: 0; margin: 0; max-width: 40rem; }
      .feed li { border: 1px solid var(--color-border); border-radius: var(--radius); padding: var(--space-3) var(--space-4);
        margin-bottom: var(--space-2); background: var(--color-surface); }
      .feed li.unread { border-left: 3px solid var(--color-accent); }
      .row { display: flex; justify-content: space-between; gap: var(--space-4); }
      .muted { color: var(--color-text-muted); font-size: 0.8rem; }
      .body { margin: var(--space-1) 0; color: var(--color-text-muted); font-size: 0.9rem; }
      .actions { display: flex; gap: var(--space-4); font-size: 0.85rem; align-items: center; }
    `,
  ],
})
export class NotificationsPage {
  protected readonly center = inject(NotificationCenter);

  constructor() {
    this.center.reload();
  }
}
