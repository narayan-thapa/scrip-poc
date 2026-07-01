/**
 * Development environment. `apiBaseUrl` stays empty; the Angular dev server proxies `/api` and
 * `/v3/api-docs` to the backend (see proxy.conf.json) so there is no CORS in local dev.
 */
export const environment = {
  production: false,
  apiBaseUrl: '',
};
