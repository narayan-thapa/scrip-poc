package np.com.thapanarayan.backend.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import np.com.thapanarayan.backend.platform.internal.ratelimit.TokenBucket;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void allowsUpToCapacityThenBlocks() {
        AtomicLong clock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(3, 1, clock::get); // 3 tokens, 1/sec refill

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).as("bucket empty").isFalse();
    }

    @Test
    void refillsOverTime() {
        AtomicLong clock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(2, 1, clock::get);
        bucket.tryConsume();
        bucket.tryConsume();
        assertThat(bucket.tryConsume()).isFalse();

        clock.set(1_000); // 1 second → +1 token
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }
}
