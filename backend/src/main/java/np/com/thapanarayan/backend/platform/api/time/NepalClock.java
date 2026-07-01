package np.com.thapanarayan.backend.platform.api.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for "now" in Nepal Time (NPT, Asia/Kathmandu, UTC+05:45).
 *
 * <p>The whole platform is an EOD system keyed on NPT trading dates (the 15:01 NPT run, filename
 * dates, look-back windows), so every component must agree on the zone. Inject the {@link Clock}
 * bean (see platform config) rather than calling {@code LocalDate.now()} so tests can pin time.
 */
public final class NepalClock {

    /** Nepal Time zone. Fixed UTC+05:45 offset; no DST. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");

    private final Clock clock;

    public NepalClock(Clock clock) {
        this.clock = clock;
    }

    /** A system clock fixed to the NPT zone. */
    public static NepalClock system() {
        return new NepalClock(Clock.system(ZONE));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Clock clock() {
        return clock;
    }
}
