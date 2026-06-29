package np.com.thapanarayan.backend.ingestion.internal;

/**
 * Neutralizes CSV/formula injection for any value that may be written back into
 * an exported CSV (rejection reports, downloads). A leading {@code = + - @},
 * tab, or CR can be interpreted as a formula by spreadsheet software; we prefix
 * such values with a single quote. Ingestion itself never executes cell content;
 * this guards the export path.
 */
final class CsvSanitizer {

    private CsvSanitizer() {
    }

    static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
