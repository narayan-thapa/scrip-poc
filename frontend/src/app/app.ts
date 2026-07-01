import { UpperCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/auth/auth.service';
import { NotificationCenter } from './core/notification/notification-center';
import { SystemService } from './core/system/system.service';
import { ToastHost } from './shared/ui/toast-host/toast-host';
import { Icon, IconName } from './shared/ui/icon/icon';

interface NavItem {
  label: string;
  route: string;
  icon: IconName;
  exact?: boolean;
  admin?: boolean;
}
interface NavGroup {
  label: string;
  items: NavItem[];
}

/**
 * Application shell: a grouped side-panel navigation, a slim top bar (live backend-connection badge,
 * notifications, user menu) and the routed content area. The sidebar collapses to an icon rail on
 * desktop and slides in as an off-canvas drawer on mobile; nav items adapt to the auth role.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastHost, Icon, UpperCasePipe],
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

  /** Icon-rail collapse (desktop) and off-canvas open (mobile) states. */
  protected readonly collapsed = signal(false);
  protected readonly drawerOpen = signal(false);

  private readonly groups: NavGroup[] = [
    {
      label: 'Overview',
      items: [
        { label: 'Home', route: '/', icon: 'home', exact: true },
        { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
        { label: 'Market', route: '/market', icon: 'market' },
      ],
    },
    {
      label: 'Analysis',
      items: [
        { label: 'Signals', route: '/signals', icon: 'signals' },
        { label: 'Strategies', route: '/strategies', icon: 'strategies' },
        { label: 'Backtests', route: '/backtests', icon: 'backtests' },
        { label: 'Price drops', route: '/screener/price-drop', icon: 'price-drop' },
      ],
    },
    {
      label: 'Personal',
      items: [
        { label: 'Watchlists', route: '/watchlists', icon: 'watchlists' },
        { label: 'Alerts', route: '/alerts', icon: 'alerts' },
        { label: 'Notifications', route: '/notifications', icon: 'bell' },
      ],
    },
    {
      label: 'Reference',
      items: [
        { label: 'Instruments', route: '/instruments', icon: 'instruments' },
        { label: 'Brokers', route: '/brokers', icon: 'brokers' },
        { label: 'Calendar', route: '/calendar', icon: 'calendar' },
      ],
    },
    {
      label: 'Admin',
      items: [
        { label: 'Pipeline', route: '/pipeline', icon: 'pipeline', admin: true },
        { label: 'Ingestion', route: '/admin/ingestion', icon: 'ingestion', admin: true },
      ],
    },
  ];

  /** Nav groups filtered to the current role, dropping any group left empty. */
  protected readonly visibleGroups = computed<NavGroup[]>(() => {
    const isAdmin = this.auth.user()?.role === 'ADMIN';
    return this.groups
      .map((g) => ({ ...g, items: g.items.filter((i) => !i.admin || isAdmin) }))
      .filter((g) => g.items.length > 0);
  });

  constructor() {
    // Open/close the realtime notification stream with the session.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.notifications.connect();
      } else {
        this.notifications.disconnect();
      }
    });

    // Close the mobile drawer after each navigation so it never lingers over the new page.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.drawerOpen.set(false));
  }

  toggleCollapsed(): void {
    this.collapsed.update((c) => !c);
  }

  toggleDrawer(): void {
    this.drawerOpen.update((o) => !o);
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
