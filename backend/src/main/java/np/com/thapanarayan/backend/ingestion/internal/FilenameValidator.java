package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Validates the {@code YYYY-MM-DD.csv} filename contract and extracts the authoritative trade date.
 * The filename is validated and parsed but NEVER used as a filesystem path (Decision A) — the
 * archive derives its own key from the date + content hash.
 */
final class FilenameValidator {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\.csv$");

    private FilenameValidator() {
    }

    static LocalDate tradeDateOf(String filename) {
        if (filename == null) {
            throw badName("null");
        }
        var matcher = PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw badName(filename);
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException e) {
            throw badName(filename);
        }
    }

    private static ApiException badName(String filename) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Filename must match YYYY-MM-DD.csv: " + filename);
    }
}
