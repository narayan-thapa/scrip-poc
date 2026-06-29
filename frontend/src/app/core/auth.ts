import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';

import { API } from './api';
import { TokenResponse, UserView } from './models';

/**
 * Holds the access token in memory only (never localStorage) and the current user.
 * The refresh token lives in an httpOnly cookie the browser manages — so a page
 * reload restores the session via {@link bootstrap} without JS ever seeing it.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly accessToken = signal<string | null>(null);
  private readonly currentUser = signal<UserView | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  token(): string | null {
    return this.accessToken();
  }

  login(email: string, password: string): Observable<void> {
    return this.http
      .post<TokenResponse>(`${API}/auth/login`, { email, password }, { withCredentials: true })
      .pipe(
        tap((res) => this.apply(res)),
        map(() => undefined),
      );
  }

  register(email: string, password: string): Observable<UserView> {
    return this.http.post<UserView>(`${API}/auth/register`, { email, password });
  }

  refresh(): Observable<string> {
    return this.http
      .post<TokenResponse>(`${API}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => this.apply(res)),
        map((res) => res.accessToken),
      );
  }

  logout(): void {
    this.http.post(`${API}/auth/logout`, {}, { withCredentials: true }).subscribe({
      complete: () => this.afterLogout(),
      error: () => this.afterLogout(),
    });
  }

  /** Clears local state and routes to login — used by the interceptor when refresh fails. */
  forceLogout(): void {
    this.afterLogout();
  }

  /** On app start, try to restore a session from the refresh cookie; never throws. */
  bootstrap(): Promise<void> {
    return firstValueFrom(this.refresh().pipe(catchError(() => of('')))).then(() => undefined);
  }

  private apply(res: TokenResponse): void {
    this.accessToken.set(res.accessToken);
    this.currentUser.set(res.user);
  }

  private afterLogout(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
    this.router.navigateByUrl('/login');
  }
}
