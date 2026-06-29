import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';

import { API } from '../core/api';
import { NotificationView, PageResponse } from '../core/models';
import { RealtimeService } from '../core/realtime';

@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <div class="page-head">
      <h1>Notifications</h1>
      <p class="muted">Alert matches from each daily signal run</p>
    </div>

    @if (feed.value().content.length === 0) {
      <div class="state">No notifications yet. Configure alert rules and run a daily signal generation.</div>
    } @else {
      <div class="card" style="padding:0">
        <table>
          <thead>
            <tr><th>When</th><th>Notification</th><th></th></tr>
          </thead>
          <tbody>
            @for (n of feed.value().content; track n.id) {
              <tr [style.opacity]="n.read ? 0.55 : 1">
                <td>{{ n.createdAt | date: 'short' }}</td>
                <td><strong>{{ n.title }}</strong><br /><span class="muted">{{ n.body }}</span></td>
                <td>
                  @if (!n.read) {
                    <button class="ghost" (click)="markRead(n.id)">Mark read</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class Notifications {
  private readonly http = inject(HttpClient);
  private readonly realtime = inject(RealtimeService);

  protected readonly feed = httpResource<PageResponse<NotificationView>>(
    () => `${API}/notifications?size=50`,
    { defaultValue: { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 } },
  );

  constructor() {
    this.http
      .get<{ count: number }>(`${API}/notifications/unread-count`)
      .subscribe((r) => this.realtime.setUnread(r.count));
  }

  protected markRead(id: string): void {
    this.http.post(`${API}/notifications/${id}/read`, {}).subscribe(() => this.feed.reload());
  }
}
