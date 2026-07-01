/**
 * Production environment. `apiBaseUrl` is empty so the SPA calls the backend on its own origin
 * (reverse-proxied in deployment). Dev overrides this via environment.development.ts + a dev proxy.
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
};
