package np.com.thapanarayan.backend.ingestion.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

/**
 * Parses one raw floorsheet line into a validated {@link ParsedTrade}, treating
 * the input as hostile. Defends against the documented hazards:
 * <ul>
 *   <li>irregular whitespace after commas (every field is trimmed);</li>
 *   <li>a comma <em>inside</em> the timestamp field ({@code 2026-06-05, 2:59:...}),
 *       which would otherwise shift column alignment — handled by treating the
 *       last token as the contract id and rejoining the middle tokens as the time;</li>
 *   <li>mixed integer/decimal prices and trailing spaces in numbers;</li>
 *   <li>amount that disagrees with quantity*price beyond a tolerance;</li>
 *   <li>a row whose timestamp date differs from the authoritative filename date.</li>
 * </ul>
 * Any violation throws {@link RowRejectedException} so the row is quarantined,
 * never crashing the file.
 *
 * <p>Expected layout: {@code Symbol, Buyer, Seller, Quantity, Price, Amount, Trade Time, Contract Id}.</p>
 */
@Component
class FloorsheetLineParser {

    private static final int MIN_FIELDS = 8;

    /**
     * @param line         raw CSV line (no surrounding quotes expected)
     * @param filenameDate authoritative trade date from the filename; the stored
     *                     trade_date, and the value each row's timestamp must match
     * @param amountTolerance max allowed |amount - quantity*price|
     */
    ParsedTrade parse(String line, LocalDate filenameDate, BigDecimal amountTolerance) {
        if (line == null || line.isBlank()) {
            throw new RowRejectedException(RejectionReason.MALFORMED_STRUCTURE, "empty line");
        }
        String[] t = line.split(",", -1);
        if (t.length < MIN_FIELDS) {
            throw new RowRejectedException(RejectionReason.MALFORMED_STRUCTURE,
                    "expected at least " + MIN_FIELDS + " fields, found " + t.length);
        }

        String symbol = t[0].trim();
        if (symbol.isEmpty()) {
            throw new RowRejectedException(RejectionReason.MALFORMED_STRUCTURE, "blank symbol");
        }
        int buyer = parseBroker(t[1]);
        int seller = parseBroker(t[2]);
        long quantity = parseLong(t[3]);
        BigDecimal price = parseDecimal(t[4]);
        BigDecimal amount = parseDecimal(t[5]);

        String contractId = t[t.length - 1].trim();
        if (contractId.isEmpty()) {
            throw new RowRejectedException(RejectionReason.MISSING_CONTRACT_ID, "blank contract id");
        }

        // Tokens between Amount and Contract Id are the Trade Time; rejoin to
        // preserve any comma the timestamp itself contained.
        String tradeTimeRaw = rejoin(t, 6, t.length - 2);
        LocalDateTime tradeTime = TimestampParser.parse(tradeTimeRaw);
        LocalDate rowDate = tradeTime.toLocalDate();

        if (filenameDate != null && !rowDate.equals(filenameDate)) {
            throw new RowRejectedException(RejectionReason.DATE_MISMATCH,
                    "row date " + rowDate + " != filename date " + filenameDate);
        }
        if (quantity <= 0) {
            throw new RowRejectedException(RejectionReason.NON_POSITIVE_QUANTITY, "quantity=" + quantity);
        }
        if (price.signum() <= 0) {
            throw new RowRejectedException(RejectionReason.NON_POSITIVE_PRICE, "price=" + price);
        }
        BigDecimal product = price.multiply(BigDecimal.valueOf(quantity));
        if (amount.subtract(product).abs().compareTo(amountTolerance) > 0) {
            throw new RowRejectedException(RejectionReason.AMOUNT_MISMATCH,
                    "amount=" + amount + " vs qty*price=" + product);
        }

        LocalDate tradeDate = filenameDate != null ? filenameDate : rowDate;
        return new ParsedTrade(contractId, symbol, buyer, seller, quantity, price, amount, tradeTime, tradeDate);
    }

    private static String rejoin(String[] tokens, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            if (i > from) {
                sb.append(',');
            }
            sb.append(tokens[i]);
        }
        return sb.toString().trim();
    }

    private static int parseBroker(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v <= 0) {
                throw new RowRejectedException(RejectionReason.INVALID_BROKER, "broker=" + v);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new RowRejectedException(RejectionReason.INVALID_BROKER, "broker='" + raw.trim() + "'");
        }
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new RowRejectedException(RejectionReason.INVALID_NUMBER, "quantity='" + raw.trim() + "'");
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new RowRejectedException(RejectionReason.INVALID_NUMBER, "number='" + raw.trim() + "'");
        }
    }
}
