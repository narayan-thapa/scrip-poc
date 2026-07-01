package np.com.thapanarayan.backend.iam.internal.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security configuration. {@code jwtSecret} is read from the environment/secret manager; when blank
 * (local dev) a random key is generated at startup so no secret is ever committed — tokens then do
 * not survive a restart, which is fine for dev. CORS is locked to the configured Angular origins.
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        String issuer,
        boolean cookieSecure,
        String cookieSameSite,
        List<String> corsOrigins) {

    public SecurityProperties {
        if (accessTtl == null) {
            accessTtl = Duration.ofMinutes(15);
        }
        if (refreshTtl == null) {
            refreshTtl = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "nepse-platform";
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            cookieSameSite = "Strict";
        }
        if (corsOrigins == null || corsOrigins.isEmpty()) {
            corsOrigins = List.of("http://localhost:4200");
        }
    }
}
