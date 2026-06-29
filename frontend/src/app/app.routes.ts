import { Routes } from '@angular/router';

import { authGuard } from './core/auth-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'signals' },
  { path: 'login', loadComponent: () => import('./auth/login').then((m) => m.Login) },
  {
    path: 'signals',
    loadComponent: () => import('./signals/signals-list').then((m) => m.SignalsList),
  },
  {
    path: 'signals/:id',
    loadComponent: () => import('./signals/signal-detail').then((m) => m.SignalDetail),
  },
  {
    path: 'charts',
    loadComponent: () => import('./charts/chart-view').then((m) => m.ChartViewComponent),
  },
  {
    path: 'backtests',
    loadComponent: () => import('./backtests/backtest-runner').then((m) => m.BacktestRunner),
  },
  {
    path: 'watchlists',
    canActivate: [authGuard],
    loadComponent: () => import('./watchlists/watchlists').then((m) => m.Watchlists),
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    loadComponent: () => import('./notifications/notifications').then((m) => m.Notifications),
  },
  { path: '**', redirectTo: 'signals' },
];
