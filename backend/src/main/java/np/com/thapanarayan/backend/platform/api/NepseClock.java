package np.com.thapanarayan.backend.platform.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Nepal Time (NPT, {@code Asia/Kathmandu}, UTC+05:45) clock. All trading-date
 * math anchors here so "today" and the 15:01 NPT trigger are unambiguous
 * regardless of the JVM's default zone. Inject this rather than calling
 * {@link LocalDate#now()} directly, so tests can pin a fixed clock.
 */
public final class NepseClock {

    public static final ZoneId NPT = ZoneId.of("Asia/Kathmandu");

    private final Clock clock;

    public NepseClock(Clock clock) {
        this.clock = clock.withZone(NPT);
    }

    /** Live system clock pinned to NPT. */
    public static NepseClock system() {
        return new NepseClock(Clock.system(NPT));
    }

    /** Fixed clock for deterministic tests. */
    public static NepseClock fixed(ZonedDateTime instant) {
        return new NepseClock(Clock.fixed(instant.toInstant(), NPT));
    }

    public Clock clock() {
        return clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }
}
