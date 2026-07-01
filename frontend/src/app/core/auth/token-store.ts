import { Injectable, signal } from '@angular/core';

/**
 * Holds the short-lived access token in memory only (never localStorage) per security Decision B:
 * the refresh token lives in an httpOnly+Secure+SameSite cookie the JS can't read, mitigating XSS
 * token theft. Phase 1 wires login/refresh into this store; for now it's an empty stub the auth
 * interceptor reads.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly accessToken = signal<string | null>(null);

  readonly token = this.accessToken.asReadonly();

  set(token: string | null): void {
    this.accessToken.set(token);
  }

  clear(): void {
    this.accessToken.set(null);
  }
}
