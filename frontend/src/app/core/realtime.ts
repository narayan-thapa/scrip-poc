import { Injectable, inject, signal } from '@angular/core';

import { API } from './api';
import { AuthService } from './auth';
import { NotificationView } from './models';

/**
 * Minimal realtime notifications over SSE, read with the Fetch API so the Bearer
 * token can be sent as a header (the native EventSource cannot). New notifications
 * surface as transient toasts and bump the unread badge. Kept dependency-free; a
 * production build could swap in STOMP/WebSocket with the same surface.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private controller?: AbortController;

  private readonly _toasts = signal<NotificationView[]>([]);
  private readonly _unread = signal(0);
  readonly toasts = this._toasts.asReadonly();
  readonly unread = this._unread.asReadonly();

  start(): void {
    if (this.controller || !this.auth.token()) {
      return;
    }
    void this.connect();
  }

  stop(): void {
    this.controller?.abort();
    this.controller = undefined;
  }

  setUnread(count: number): void {
    this._unread.set(count);
  }

  dismiss(id: string): void {
    this._toasts.update((list) => list.filter((n) => n.id !== id));
  }

  private async connect(): Promise<void> {
    const token = this.auth.token();
    if (!token) {
      return;
    }
    this.controller = new AbortController();
    try {
      const res = await fetch(`${API}/notifications/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: this.controller.signal,
      });
      if (!res.body) {
        return;
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          this.handleEvent(buffer.slice(0, sep));
          buffer = buffer.slice(sep + 2);
        }
      }
    } catch {
      // aborted on stop() or a transient network error — the badge still reflects the feed
    }
  }

  private handleEvent(chunk: string): void {
    const data = chunk
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())
      .join('\n');
    if (!data) {
      return;
    }
    try {
      const notification = JSON.parse(data) as NotificationView;
      this._toasts.update((list) => [notification, ...list].slice(0, 4));
      this._unread.update((count) => count + 1);
    } catch {
      // ignore keep-alive or malformed frames
    }
  }
}
