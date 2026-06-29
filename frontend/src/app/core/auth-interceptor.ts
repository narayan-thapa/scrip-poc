import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth';

/**
 * Attaches the in-memory access token as a Bearer header to API calls and sends
 * credentials (so the refresh cookie rides along on auth calls). On a 401, it tries a
 * single silent refresh and replays the request; if that fails, it forces logout.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApi = req.url.startsWith('/api/');
  const isAuthCall = req.url.includes('/api/v1/auth/');

  if (!isApi) {
    return next(req);
  }

  const token = auth.token();
  const authed =
    token && !isAuthCall
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` }, withCredentials: true })
      : req.clone({ withCredentials: true });

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      const retried = req.headers.has('X-Retry');
      if (err.status === 401 && !isAuthCall && !retried) {
        return auth.refresh().pipe(
          switchMap((fresh) =>
            next(
              req.clone({
                setHeaders: { Authorization: `Bearer ${fresh}`, 'X-Retry': '1' },
                withCredentials: true,
              }),
            ),
          ),
          catchError((refreshErr) => {
            auth.forceLogout();
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};
