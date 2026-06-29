package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Parses the floorsheet's non-ISO timestamps, e.g. {@code 2026-06-03 2:59:59:988 PM}.
 * Hazards handled: a comma after the date ({@code 2026-06-05, 2:59:59:729 PM}),
 * irregular whitespace, single-digit (non-zero-padded) 12-hour clock, and the
 * colon-before-millis format. An ordered list of formatters is tried; the first
 * match wins. Unparseable input is rejected to quarantine, never silently coerced.
 */
final class TimestampParser {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss:SSS a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss:SSS a", Locale.ENGLISH),
            // Defensive fallbacks for slight upstream drift.
            DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH));

    private TimestampParser() {
    }

    static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RowRejectedException(RejectionReason.BAD_TIMESTAMP, "empty timestamp");
        }
        String normalized = normalize(raw);
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        throw new RowRejectedException(RejectionReason.BAD_TIMESTAMP,
                "unrecognized timestamp: '" + raw + "'");
    }

    /** Drop a comma immediately after the date and collapse irregular whitespace. */
    private static String normalize(String raw) {
        String trimmed = raw.trim();
        // "2026-06-05, 2:59:..." -> "2026-06-05 2:59:..."
        String noDateComma = trimmed.replaceFirst("(?<=^\\d{4}-\\d{2}-\\d{2}),", "");
        return noDateComma.replaceAll("\\s+", " ");
    }
}
