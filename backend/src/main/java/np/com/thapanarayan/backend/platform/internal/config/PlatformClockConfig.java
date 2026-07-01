package np.com.thapanarayan.backend.platform.internal.config;

import java.time.Clock;
import np.com.thapanarayan.backend.platform.api.time.NepalClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the NPT {@link Clock} and {@link NepalClock} as beans so no component reads wall-clock
 * time directly. Tests can override the {@code Clock} bean with a fixed clock to make EOD/date
 * logic deterministic.
 */
@Configuration
class PlatformClockConfig {

    @Bean
    Clock nptClock() {
        return Clock.system(NepalClock.ZONE);
    }

    @Bean
    NepalClock nepalClock(Clock nptClock) {
        return new NepalClock(nptClock);
    }
}
