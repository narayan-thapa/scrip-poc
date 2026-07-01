package np.com.thapanarayan.backend.platform.internal.ratelimit;

import java.util.function.LongSupplier;

/**
 * A simple thread-safe token bucket: {@code capacity} tokens, refilled continuously at
 * {@code refillPerSecond}. Each allowed request consumes one token. The clock is injectable so the
 * refill logic is deterministically unit-testable.
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerMilli;
    private final LongSupplier clockMillis;

    private double tokens;
    private long lastRefill;

    public TokenBucket(double capacity, double refillPerSecond, LongSupplier clockMillis) {
        this.capacity = capacity;
        this.refillPerMilli = refillPerSecond / 1000.0;
        this.clockMillis = clockMillis;
        this.tokens = capacity;
        this.lastRefill = clockMillis.getAsLong();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = clockMillis.getAsLong();
        long elapsed = now - lastRefill;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
            lastRefill = now;
        }
    }
}
