import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth.guard';

/**
 * Public auth routes + authenticated app routes. Feature areas (charts, signals, screeners,
 * backtests, admin) are added in later phases behind the same guard (and role guards where needed).
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Home · NEPSE Signal Platform',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'instruments',
        title: 'Instruments',
        loadComponent: () =>
          import('./features/reference/instruments/instruments').then((m) => m.Instruments),
      },
      {
        path: 'instruments/:symbol',
        title: 'Instrument',
        loadComponent: () =>
          import('./features/reference/instrument-detail/instrument-detail').then(
            (m) => m.InstrumentDetail,
          ),
      },
      {
        path: 'brokers',
        title: 'Brokers',
        loadComponent: () => import('./features/reference/brokers/brokers').then((m) => m.Brokers),
      },
      {
        path: 'calendar',
        title: 'Trading calendar',
        loadComponent: () => import('./features/calendar/calendar').then((m) => m.Calendar),
      },
      {
        path: 'market',
        pathMatch: 'full',
        title: 'Market overview',
        loadComponent: () =>
          import('./features/market/market-overview/market-overview').then((m) => m.MarketOverview),
      },
      {
        path: 'dashboard',
        title: 'Day dashboard',
        loadComponent: () =>
          import('./features/screeners/day-dashboard/day-dashboard').then((m) => m.DayDashboard),
      },
      {
        path: 'screener/price-drop',
        title: 'Price-drop screener',
        loadComponent: () => import('./features/screeners/price-drop/price-drop').then((m) => m.PriceDrop),
      },
      {
        path: 'market/:symbol',
        title: 'Market',
        loadComponent: () =>
          import('./features/market/instrument-market/instrument-market').then((m) => m.InstrumentMarket),
      },
      {
        path: 'signals',
        pathMatch: 'full',
        title: 'Signals',
        loadComponent: () =>
          import('./features/signals/signals-overview/signals-overview').then((m) => m.SignalsOverview),
      },
      {
        path: 'signals/:id',
        title: 'Signal detail',
        loadComponent: () =>
          import('./features/signals/signal-detail/signal-detail').then((m) => m.SignalDetail),
      },
      {
        path: 'strategies',
        title: 'Strategies',
        loadComponent: () => import('./features/signals/strategies/strategies').then((m) => m.Strategies),
      },
      {
        path: 'notifications',
        title: 'Notifications',
        loadComponent: () =>
          import('./features/notifications/notifications-page').then((m) => m.NotificationsPage),
      },
      {
        path: 'watchlists',
        title: 'Watchlists',
        loadComponent: () => import('./features/watchlists/watchlists-page').then((m) => m.WatchlistsPage),
      },
      {
        path: 'alerts',
        title: 'Alerts',
        loadComponent: () => import('./features/alerts/alerts-page').then((m) => m.AlertsPage),
      },
      {
        path: 'pipeline',
        title: 'Pipeline status',
        loadComponent: () => import('./features/pipeline/pipeline-status').then((m) => m.PipelineStatus),
      },
      {
        path: 'backtests',
        pathMatch: 'full',
        title: 'Backtests',
        loadComponent: () =>
          import('./features/backtests/backtests-page/backtests-page').then((m) => m.BacktestsPage),
      },
      {
        path: 'backtests/:id',
        title: 'Backtest result',
        loadComponent: () =>
          import('./features/backtests/backtest-detail/backtest-detail').then((m) => m.BacktestDetail),
      },
      {
        path: 'admin/ingestion',
        canActivate: [roleGuard('ADMIN')],
        title: 'Ingestion',
        loadComponent: () =>
          import('./features/admin/ingestion/ingestion-console').then((m) => m.IngestionConsole),
      },
      {
        path: 'admin/ingestion/batches/:id',
        canActivate: [roleGuard('ADMIN')],
        title: 'Ingestion batch',
        loadComponent: () =>
          import('./features/admin/ingestion/batch-detail').then((m) => m.BatchDetail),
      },
    ],
  },
  {
    path: '**',
    title: 'Not found',
    loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
  },
];
