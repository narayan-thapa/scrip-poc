import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * On a 401 from a protected endpoint, attempts a single silent token refresh (shared across
 * concurrent failures) and retries the original request with the new access token. Auth endpoints
 * are skipped to avoid recursion; if the refresh itself fails, the session is cleared and the
 * original 401 propagates.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (req.url.includes('/api/v1/auth/')) {
    return next(req);
  }

  return next(req).pipe(
    catchError((err) => {
      if (err.status !== 401) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((token) => next(withBearer(req, token))),
        catchError(() => {
          auth.clearSession();
          return throwError(() => err);
        }),
      );
    }),
  );
};

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` }, withCredentials: true });
}
