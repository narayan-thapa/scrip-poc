package np.com.thapanarayan.backend.iam.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security tunables (§10.10, §11). The JWT secret MUST come from the environment /
 * secret manager — never hard-coded. When absent, an ephemeral random key is
 * generated at startup (see {@code SecurityConfig}) so dev can boot, but tokens then
 * do not survive a restart; production must set {@code NEPSE_SECURITY_JWT_SECRET}.
 *
 * @param jwtSecret              HMAC signing secret (>= 32 bytes); blank → ephemeral dev key
 * @param accessTokenTtlSeconds  short-lived access-token lifetime
 * @param refreshTokenTtlDays    refresh-token (cookie) lifetime
 * @param corsAllowedOrigin      the single allowed browser origin (the Angular app)
 * @param cookieSecure           whether the refresh cookie is marked Secure (set false only for local http)
 * @param loginRateLimitPerMinute per-IP attempts allowed on auth endpoints each minute
 */
@ConfigurationProperties(prefix = "nepse.security")
record SecurityProperties(
        String jwtSecret,
        Integer accessTokenTtlSeconds,
        Integer refreshTokenTtlDays,
        String corsAllowedOrigin,
        Boolean cookieSecure,
        Integer loginRateLimitPerMinute) {

    SecurityProperties {
        if (accessTokenTtlSeconds == null || accessTokenTtlSeconds < 60) {
            accessTokenTtlSeconds = 900; // 15 minutes
        }
        if (refreshTokenTtlDays == null || refreshTokenTtlDays < 1) {
            refreshTokenTtlDays = 14;
        }
        if (corsAllowedOrigin == null || corsAllowedOrigin.isBlank()) {
            corsAllowedOrigin = "http://localhost:4200";
        }
        if (cookieSecure == null) {
            cookieSecure = true;
        }
        if (loginRateLimitPerMinute == null || loginRateLimitPerMinute < 1) {
            loginRateLimitPerMinute = 10;
        }
    }
}
