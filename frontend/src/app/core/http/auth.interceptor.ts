import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStore } from '../auth/token-store';

/**
 * Attaches the in-memory access token as a Bearer header when present, and sends cookies (the
 * httpOnly refresh token) for same-origin requests. A no-op until Phase 1 populates the token.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStore).token();
  const authed = req.clone({
    withCredentials: true,
    setHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return next(authed);
};
