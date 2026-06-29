package np.com.thapanarayan.backend.platform.internal;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import np.com.thapanarayan.backend.platform.api.NepseClock;

/**
 * Wires the shared-kernel beans every module relies on.
 */
@Configuration
public class PlatformConfig {

    @Bean
    public NepseClock nepseClock() {
        return NepseClock.system();
    }

    /** NPT-pinned {@link Clock} for components that prefer the JDK type directly. */
    @Bean
    public Clock clock(NepseClock nepseClock) {
        return nepseClock.clock();
    }
}
