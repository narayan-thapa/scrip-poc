import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './core/auth';
import { RealtimeService } from './core/realtime';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly realtime = inject(RealtimeService);

  constructor() {
    // Open the realtime stream while signed in; close it on logout.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.realtime.start();
      } else {
        this.realtime.stop();
      }
    });
  }

  protected logout(): void {
    this.auth.logout();
  }
}
