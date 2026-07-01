import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of, shareReplay, tap } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { Role, TokenResponse, UserProfile } from '../api/models';
import { TokenStore } from './token-store';

/**
 * Owns the authenticated session. The access token lives in {@link TokenStore} (memory only); the
 * refresh token is an httpOnly cookie the backend manages. On bootstrap we attempt a silent refresh
 * so a page reload restores the session from the cookie without a re-login.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStore);
  private readonly base = environment.apiBaseUrl;

  private readonly userSig = signal<UserProfile | null>(null);
  readonly user = this.userSig.asReadonly();
  readonly isAuthenticated = computed(() => this.userSig() !== null);

  /** In-flight refresh shared across concurrent 401s to avoid a refresh stampede. */
  private refreshInFlight?: Observable<string>;

  register(email: string, password: string): Observable<UserProfile> {
    return this.http.post<UserProfile>(`${this.base}/api/v1/auth/register`, { email, password });
  }

  login(email: string, password: string): Observable<UserProfile> {
    return this.http.post<TokenResponse>(`${this.base}/api/v1/auth/login`, { email, password }).pipe(
      tap((t) => this.tokens.set(t.accessToken)),
      // Load the profile after authenticating so role-based UI/guards have data.
      switchMap(() => this.loadProfile()),
      map((u) => u as UserProfile),
    );
  }

  /** Used by the refresh interceptor; rotates the access token via the refresh cookie. */
  refresh(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<TokenResponse>(`${this.base}/api/v1/auth/refresh`, {})
        .pipe(
          map((t) => t.accessToken),
          tap((token) => this.tokens.set(token)),
          shareReplay(1),
          tap({ complete: () => (this.refreshInFlight = undefined), error: () => (this.refreshInFlight = undefined) }),
        );
    }
    return this.refreshInFlight;
  }

  loadProfile(): Observable<UserProfile | null> {
    return this.http.get<UserProfile>(`${this.base}/api/v1/users/me`).pipe(
      tap((u) => this.userSig.set(u)),
      catchError(() => {
        this.userSig.set(null);
        return of(null);
      }),
    );
  }

  /** Run at app bootstrap: silently restore the session from the refresh cookie, if any. */
  restoreSession(): Observable<UserProfile | null> {
    return this.refresh().pipe(
      switchMap(() => this.loadProfile()),
      catchError(() => of(null)),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/api/v1/auth/logout`, {}).pipe(
      tap(() => this.clearSession()),
      catchError(() => {
        this.clearSession();
        return of(void 0);
      }),
    );
  }

  /** Local-only teardown used when a refresh fails irrecoverably. */
  clearSession(): void {
    this.tokens.clear();
    this.userSig.set(null);
  }

  hasRole(role: Role): boolean {
    return this.userSig()?.role === role;
  }
}
