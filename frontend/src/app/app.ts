import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { NotificationCenter } from './core/notification/notification-center';
import { SystemService } from './core/system/system.service';
import { ToastHost } from './shared/ui/toast-host/toast-host';

/**
 * Application shell: top navigation, a live backend-connection badge (driven by SystemService's
 * httpResource), the routed content area, and the global toast host. Nav + the user menu adapt to
 * the authentication state from AuthService.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastHost],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly system = inject(SystemService);
  protected readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationCenter);
  private readonly router = inject(Router);

  protected readonly connected = computed(() => this.system.ping.value()?.status === 'UP');
  protected readonly failed = computed(() => this.system.ping.error() != null);

  constructor() {
    // Open/close the realtime notification stream with the session.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.notifications.connect();
      } else {
        this.notifications.disconnect();
      }
    });
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
