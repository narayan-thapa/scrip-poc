package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Calendar seeding window. The start date should sit well before the earliest
 * date you intend to backfill, so indicator warm-up look-backs have lead-in
 * trading days available.
 */
@ConfigurationProperties(prefix = "nepse.calendar")
record CalendarProperties(
        @DefaultValue("2015-01-01") LocalDate startDate,
        @DefaultValue("400") int forwardDays) {
}
