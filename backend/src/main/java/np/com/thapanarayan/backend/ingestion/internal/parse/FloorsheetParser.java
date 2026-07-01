package np.com.thapanarayan.backend.ingestion.internal.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import np.com.thapanarayan.backend.ingestion.internal.domain.FloorsheetTrade;
import np.com.thapanarayan.backend.ingestion.internal.domain.RejectionReason;
import org.springframework.stereotype.Component;

/**
 * Defensive, line-oriented parser for the NEPSE floorsheet CSV (Decision A: untrusted input).
 *
 * <p>Handles the data-quality hazards in the feed: inconsistent whitespace after commas, the
 * {@code "YYYY-MM-DD, h:mm:ss:SSS PM"} variant whose comma splits the timestamp into two columns,
 * the non-ISO {@code H:mm:ss:SSS a} time, and mixed integer/decimal numerics. Every failure mode
 * maps to a {@link RejectionReason} so one bad row is quarantined, never failing the file.
 *
 * <p>The parser is pure and stateless; the ingest service streams a file through it line by line.
 */
@Component
public class FloorsheetParser {

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME_START = Pattern.compile("\\d{1,2}:\\d{2}:\\d{2}.*");

    /** Ordered timestamp patterns; first match wins. NEPSE uses a 12-hour, colon-millis, AM/PM form. */
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss:SSS a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss:SSS a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss:SSS", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH));

    /**
     * Parse one raw line into an outcome.
     *
     * @param rawLine        the original line (kept verbatim for quarantine)
     * @param filenameDate   authoritative trade date from the {@code YYYY-MM-DD.csv} filename
     * @param amountTolerance allowed absolute difference between {@code amount} and {@code qty*price}
     */
    public LineOutcome parseLine(String rawLine, LocalDate filenameDate, BigDecimal amountTolerance) {
        if (rawLine == null || rawLine.isBlank()) {
            return LineOutcome.Ignored.instance();
        }
        String line = rawLine.strip();
        if (isHeader(line)) {
            return LineOutcome.Ignored.instance();
        }

        String[] fields = tokenize(line);
        if (fields == null) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.MALFORMED_COLUMNS,
                    "expected 8 fields after normalization");
        }

        String symbol = fields[0];
        String contractId = fields[7];
        if (symbol.isBlank()) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BLANK_SYMBOL, null);
        }
        if (contractId.isBlank()) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BLANK_CONTRACT_ID, null);
        }

        int buyer;
        int seller;
        try {
            buyer = Integer.parseInt(fields[1]);
            seller = Integer.parseInt(fields[2]);
        } catch (NumberFormatException e) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BAD_BROKER_ID, fields[1] + "/" + fields[2]);
        }

        long quantity;
        try {
            quantity = Long.parseLong(fields[3]);
        } catch (NumberFormatException e) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BAD_NUMBER, "quantity=" + fields[3]);
        }
        if (quantity <= 0) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.NON_POSITIVE_QUANTITY, fields[3]);
        }

        BigDecimal price;
        BigDecimal amount;
        try {
            price = new BigDecimal(fields[4]);
            amount = new BigDecimal(fields[5]);
        } catch (NumberFormatException e) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BAD_NUMBER, "price/amount");
        }
        if (price.signum() <= 0) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.NON_POSITIVE_PRICE, fields[4]);
        }

        LocalDateTime tradeTime = parseTimestamp(fields[6]);
        if (tradeTime == null) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.BAD_TIMESTAMP, fields[6]);
        }
        if (!tradeTime.toLocalDate().equals(filenameDate)) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.DATE_MISMATCH,
                    "row=" + tradeTime.toLocalDate() + " file=" + filenameDate);
        }

        BigDecimal expected = price.multiply(BigDecimal.valueOf(quantity));
        if (amount.subtract(expected).abs().compareTo(amountTolerance) > 0) {
            return new LineOutcome.Rejected(rawLine, RejectionReason.AMOUNT_MISMATCH,
                    "amount=" + amount + " qty*price=" + expected);
        }

        FloorsheetTrade trade = new FloorsheetTrade(
                contractId, symbol, buyer, seller, quantity, price, amount, tradeTime, filenameDate, null);
        return new LineOutcome.Accepted(trade);
    }

    /**
     * Split into the 8 logical fields, repairing the comma-after-date timestamp variant. Returns
     * {@code null} if the line can't be coerced to 8 fields.
     */
    private String[] tokenize(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].strip();
        }
        if (parts.length == 9 && DATE.matcher(parts[6]).matches() && TIME_START.matcher(parts[7]).matches()) {
            // "...amount, YYYY-MM-DD, h:mm:ss:SSS PM, contractId" → merge the split timestamp.
            String[] merged = new String[8];
            System.arraycopy(parts, 0, merged, 0, 6);
            merged[6] = parts[6] + " " + parts[7];
            merged[7] = parts[8];
            return merged;
        }
        return parts.length == 8 ? parts : null;
    }

    private LocalDateTime parseTimestamp(String raw) {
        String value = raw.strip();
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    private boolean isHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("symbol,") || lower.startsWith("symbol ,") || lower.startsWith("symbol\t");
    }
}
