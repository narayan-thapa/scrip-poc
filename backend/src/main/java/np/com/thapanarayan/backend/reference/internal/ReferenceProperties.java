package np.com.thapanarayan.backend.reference.internal;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls reference seeding. Seeding only fills empty tables (idempotent); set {@code enabled=false}
 * in environments where reference data is loaded by another process.
 */
@ConfigurationProperties(prefix = "reference.seed")
public record ReferenceProperties(
        boolean enabled,
        Integer calendarFromYear,
        Integer calendarToYear,
        List<String> holidays) {

    public ReferenceProperties {
        if (calendarFromYear == null) {
            calendarFromYear = 2024;
        }
        if (calendarToYear == null) {
            calendarToYear = 2026;
        }
        if (holidays == null) {
            holidays = List.of();
        }
    }
}
