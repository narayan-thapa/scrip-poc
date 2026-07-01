package np.com.thapanarayan.backend.platform.internal.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Rate-limit config. Defaults protect the auth endpoints (~20 requests/min/IP burst 20). */
@ConfigurationProperties(prefix = "platform.rate-limit")
public record RateLimitProperties(Boolean enabled, Double authCapacity, Double authRefillPerSecond) {

    public RateLimitProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (authCapacity == null) {
            authCapacity = 20.0;
        }
        if (authRefillPerSecond == null) {
            authRefillPerSecond = 0.33; // ~20 per minute sustained
        }
    }
}
