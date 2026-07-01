import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { environment } from '../../../environments/environment';
import { NotificationItem, PageResponse } from '../api/models';
import { TokenStore } from '../auth/token-store';
import { NotificationService } from './notification.service';

/**
 * The in-app notification center: loads the persisted feed and subscribes to the realtime SSE stream
 * (via fetch-event-source, which can send the bearer token — EventSource can't). New notifications
 * raise a toast and bump the unread count.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenter {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStore);
  private readonly toasts = inject(NotificationService);
  private readonly base = `${environment.apiBaseUrl}/api/v1/notifications`;

  private controller?: AbortController;

  readonly items = signal<NotificationItem[]>([]);
  readonly unread = signal(0);

  /** Load the feed and open the live stream (idempotent). Call once authenticated. */
  connect(): void {
    this.reload();
    if (this.controller) {
      return;
    }
    const token = this.tokens.token();
    if (!token) {
      return;
    }
    this.controller = new AbortController();
    fetchEventSource(`${this.base}/stream`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: this.controller.signal,
      openWhenHidden: true,
      onmessage: (ev) => {
        if (ev.event === 'notification' && ev.data) {
          const n = JSON.parse(ev.data) as NotificationItem;
          this.items.update((list) => [n, ...list]);
          this.unread.update((c) => c + 1);
          this.toasts.info(n.title);
        }
      },
      onerror: () => {
        // Let the library retry with backoff; don't throw (that would stop retries).
      },
    }).catch(() => undefined);
  }

  disconnect(): void {
    this.controller?.abort();
    this.controller = undefined;
    this.items.set([]);
    this.unread.set(0);
  }

  reload(): void {
    this.http.get<PageResponse<NotificationItem>>(`${this.base}?size=30`).subscribe((page) => this.items.set(page.content));
    this.http.get<{ unread: number }>(`${this.base}/unread-count`).subscribe((r) => this.unread.set(r.unread));
  }

  markRead(id: string): void {
    this.http.patch(`${this.base}/${id}/read`, {}).subscribe(() => {
      this.items.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
      this.unread.update((c) => Math.max(0, c - 1));
    });
  }

  markAllRead(): void {
    this.http.post(`${this.base}/read-all`, {}).subscribe(() => {
      this.items.update((list) => list.map((n) => ({ ...n, read: true })));
      this.unread.set(0);
    });
  }
}
