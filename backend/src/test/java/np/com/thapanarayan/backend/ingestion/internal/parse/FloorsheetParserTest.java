package np.com.thapanarayan.backend.ingestion.internal.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import np.com.thapanarayan.backend.ingestion.internal.domain.RejectionReason;
import np.com.thapanarayan.backend.ingestion.internal.parse.LineOutcome.Accepted;
import np.com.thapanarayan.backend.ingestion.internal.parse.LineOutcome.Rejected;
import org.junit.jupiter.api.Test;

/**
 * Golden-file style tests over the messy rows called out in the architecture (§1.2): inconsistent
 * whitespace, the comma-after-date timestamp variant, the non-ISO time, and the data-quality checks.
 */
class FloorsheetParserTest {

    private final FloorsheetParser parser = new FloorsheetParser();
    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);
    private static final BigDecimal TOL = new BigDecimal("0.5");

    private LineOutcome parse(String line, LocalDate date) {
        return parser.parseLine(line, date, TOL);
    }

    @Test
    void parsesCanonicalRow() {
        LineOutcome out = parse("BHCL, 41, 58, 2000, 600.8, 1201600, 2026-06-03 2:59:59:988 PM, 2026060301020260", DATE);
        assertThat(out).isInstanceOf(Accepted.class);
        var t = ((Accepted) out).trade();
        assertThat(t.symbol()).isEqualTo("BHCL");
        assertThat(t.buyerBroker()).isEqualTo(41);
        assertThat(t.sellerBroker()).isEqualTo(58);
        assertThat(t.quantity()).isEqualTo(2000);
        assertThat(t.price()).isEqualByComparingTo("600.8");
        assertThat(t.contractId()).isEqualTo("2026060301020260");
        assertThat(t.tradeTime()).isEqualTo(LocalDateTime.of(2026, 6, 3, 14, 59, 59, 988_000_000));
    }

    @Test
    void toleratesInconsistentWhitespace() {
        // No spaces after commas.
        LineOutcome out = parse("GRDBL,63,28,10,512,5120,2026-06-03 11:01:02:003 AM,2026060301020999", DATE);
        assertThat(out).isInstanceOf(Accepted.class);
        assertThat(((Accepted) out).trade().tradeTime().getHour()).isEqualTo(11);
    }

    @Test
    void repairsCommaAfterDateTimestampVariant() {
        // The date carries a trailing comma, splitting the timestamp into two CSV columns (9 fields).
        LineOutcome out = parse("BHCL, 41, 58, 100, 10, 1000, 2026-06-05, 2:59:59:729 PM, 2026060501020260",
                LocalDate.of(2026, 6, 5));
        assertThat(out).isInstanceOf(Accepted.class);
        assertThat(((Accepted) out).trade().tradeTime())
                .isEqualTo(LocalDateTime.of(2026, 6, 5, 14, 59, 59, 729_000_000));
    }

    @Test
    void quarantinesTimestampDateNotMatchingFilename() {
        LineOutcome out = parse("BHCL, 41, 58, 2000, 600.8, 1201600, 2026-06-04 2:59:59:988 PM, 2026060401020260", DATE);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).isEqualTo(RejectionReason.DATE_MISMATCH);
    }

    @Test
    void quarantinesAmountMismatchBeyondTolerance() {
        // 2000 * 600.8 = 1,201,600; declare a wildly wrong amount.
        LineOutcome out = parse("BHCL, 41, 58, 2000, 600.8, 999999, 2026-06-03 2:59:59:988 PM, 2026060301020260", DATE);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).isEqualTo(RejectionReason.AMOUNT_MISMATCH);
    }

    @Test
    void quarantinesUnparseableTimestamp() {
        LineOutcome out = parse("BHCL, 41, 58, 2000, 600.8, 1201600, not-a-time, 2026060301020260", DATE);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).isEqualTo(RejectionReason.BAD_TIMESTAMP);
    }

    @Test
    void quarantinesNonPositiveQuantity() {
        LineOutcome out = parse("BHCL, 41, 58, 0, 600.8, 0, 2026-06-03 2:59:59:988 PM, 2026060301020260", DATE);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).isEqualTo(RejectionReason.NON_POSITIVE_QUANTITY);
    }

    @Test
    void quarantinesMalformedColumnCount() {
        LineOutcome out = parse("BHCL, 41, 58, 2000", DATE);
        assertThat(out).isInstanceOf(Rejected.class);
        assertThat(((Rejected) out).reason()).isEqualTo(RejectionReason.MALFORMED_COLUMNS);
    }

    @Test
    void ignoresBlankAndHeaderLines() {
        assertThat(parse("", DATE)).isInstanceOf(LineOutcome.Ignored.class);
        assertThat(parse("   ", DATE)).isInstanceOf(LineOutcome.Ignored.class);
        assertThat(parse("Symbol, Buyer, Seller, Quantity, Price, Amount, Trade Time, Contract Id", DATE))
                .isInstanceOf(LineOutcome.Ignored.class);
    }

    @Test
    void sanitizerNeutralizesFormulaInjection() {
        assertThat(CsvSanitizer.sanitizeCell("=cmd|' /c calc'!A1")).startsWith("\"'=");
        assertThat(CsvSanitizer.sanitizeCell("BHCL")).isEqualTo("\"BHCL\"");
    }
}
