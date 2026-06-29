package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import np.com.thapanarayan.backend.platform.api.DomainException;

/**
 * Validates the {@code YYYY-MM-DD.csv} filename contract and extracts the
 * authoritative trade date. The filename is NEVER reused as a filesystem path;
 * only the parsed date is trusted downstream (archival keys are derived from the
 * date + content hash). Rejects path-traversal and any non-conforming name.
 */
final class FilenameValidator {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\.csv$");

    private FilenameValidator() {
    }

    static LocalDate requireValidAndExtractDate(String filename) {
        if (filename == null) {
            throw new DomainException("INVALID_FILENAME", "missing filename");
        }
        var matcher = PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new DomainException("INVALID_FILENAME",
                    "filename must match YYYY-MM-DD.csv, was '" + filename + "'");
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException e) {
            throw new DomainException("INVALID_FILENAME",
                    "filename date is not a real date: '" + filename + "'");
        }
    }
}
