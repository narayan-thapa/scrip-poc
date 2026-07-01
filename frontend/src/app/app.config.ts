import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  inject,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { refreshInterceptor } from './core/http/refresh.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    // Order: add token → handle 401/refresh+retry → normalize errors/toast.
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, refreshInterceptor, errorInterceptor]),
    ),
    provideAnimationsAsync(),
    // Silently restore the session from the refresh cookie before the app renders.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};
