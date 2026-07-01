package np.com.thapanarayan.backend.ingestion.internal.parse;

/**
 * Neutralizes CSV/formula injection (Decision A): a cell beginning with {@code = + - @} (or tab/CR)
 * can execute when opened in a spreadsheet. We prefix such cells with a single quote and always
 * quote/escape, so raw scraped content (e.g. in the downloadable rejection report) is inert.
 */
public final class CsvSanitizer {

    private CsvSanitizer() {
    }

    public static String sanitizeCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = value;
        char first = escaped.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            escaped = "'" + escaped;
        }
        // Standard CSV quoting: double embedded quotes, wrap in quotes.
        return '"' + escaped.replace("\"", "\"\"") + '"';
    }
}
