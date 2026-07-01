package np.com.thapanarayan.backend.platform.internal.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-key token-bucket rate limiter (in-memory). Keys are typically the client IP. For multiple
 * instances this would be backed by Redis counters; the in-memory map suffices for a single instance.
 */
@Component
public class RateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties props;

    RateLimiter(RateLimitProperties props) {
        this.props = props;
    }

    public boolean allowAuth(String key) {
        return bucket(key).tryConsume();
    }

    private TokenBucket bucket(String key) {
        return buckets.computeIfAbsent(key,
                k -> new TokenBucket(props.authCapacity(), props.authRefillPerSecond(), System::currentTimeMillis));
    }
}
