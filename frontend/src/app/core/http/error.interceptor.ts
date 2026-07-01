import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from '../api/models';
import { NotificationService } from '../notification/notification.service';

/**
 * Normalizes every failed response into the platform {@link ApiError} shape and surfaces a toast,
 * so feature code can rely on a single error contract instead of branching on raw HTTP errors.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const apiError = toApiError(err, req.url);
      // 401s are an expected part of the auth flow (Phase 1) — don't toast those.
      if (apiError.status !== 401) {
        notifications.error(apiError.message);
      }
      return throwError(() => apiError);
    }),
  );
};

function toApiError(err: HttpErrorResponse, path: string): ApiError {
  const body = err.error;
  if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
    return body as ApiError;
  }
  return {
    timestamp: new Date().toISOString(),
    status: err.status,
    code: err.status === 0 ? 'NETWORK_ERROR' : 'INTERNAL_ERROR',
    message: err.status === 0 ? 'Cannot reach the server.' : err.message || 'Unexpected error',
    path,
    fieldErrors: [],
  };
}
