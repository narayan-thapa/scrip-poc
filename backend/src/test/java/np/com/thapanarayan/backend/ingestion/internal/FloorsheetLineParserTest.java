package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class FloorsheetLineParserTest {

    private static final BigDecimal TOL = new BigDecimal("0.5");
    private static final LocalDate D = LocalDate.of(2026, 6, 3);

    private final FloorsheetLineParser parser = new FloorsheetLineParser();

    @Test
    void parsesRowWithIrregularWhitespaceAndDecimalPrice() {
        String line = "BHCL,   41,    58,     2000,     600.8, 1201600, 2026-06-03 2:59:59:988 PM, 2026060301020260";

        ParsedTrade t = parser.parse(line, D, TOL);

        assertThat(t.symbol()).isEqualTo("BHCL");
        assertThat(t.buyerBroker()).isEqualTo(41);
        assertThat(t.sellerBroker()).isEqualTo(58);
        assertThat(t.quantity()).isEqualTo(2000L);
        assertThat(t.price()).isEqualByComparingTo("600.8");
        assertThat(t.tradeTime()).isEqualTo(LocalDateTime.of(2026, 6, 3, 14, 59, 59, 988_000_000));
        assertThat(t.tradeDate()).isEqualTo(D);
        assertThat(t.contractId()).isEqualTo("2026060301020260");
    }

    @Test
    void handlesCommaAfterDateInsideTimestampField() {
        // The timestamp itself contains a comma, shifting the column count to 9.
        String line = "GRDBL, 63, 28, 100, 500, 50000, 2026-06-05, 2:59:59:729 PM, 2026060501020261";

        ParsedTrade t = parser.parse(line, LocalDate.of(2026, 6, 5), TOL);

        assertThat(t.symbol()).isEqualTo("GRDBL");
        assertThat(t.contractId()).isEqualTo("2026060501020261");
        assertThat(t.tradeTime()).isEqualTo(LocalDateTime.of(2026, 6, 5, 14, 59, 59, 729_000_000));
    }

    @Test
    void trimsTrailingSpacesInQuantity() {
        String line = "ABC, 1, 2, 2000 , 10, 20000, 2026-06-03 10:00:00:000 AM, C1";
        assertThat(parser.parse(line, D, TOL).quantity()).isEqualTo(2000L);
    }

    @Test
    void rejectsAmountMismatch() {
        String line = "ABC,1,2,2000,10,99999,2026-06-03 10:00:00:000 AM,C1";
        assertReason(line, RejectionReason.AMOUNT_MISMATCH);
    }

    @Test
    void rejectsRowDateNotMatchingFilenameDate() {
        String line = "ABC,1,2,10,10,100,2026-06-04 10:00:00:000 AM,C1";
        assertReason(line, RejectionReason.DATE_MISMATCH);
    }

    @Test
    void rejectsTooFewFields() {
        assertReason("BHCL,41,58", RejectionReason.MALFORMED_STRUCTURE);
    }

    @Test
    void rejectsUnparseableTimestamp() {
        String line = "ABC,1,2,10,10,100,not-a-time,C1";
        assertReason(line, RejectionReason.BAD_TIMESTAMP);
    }

    @Test
    void rejectsNonPositiveQuantity() {
        String line = "ABC,1,2,0,10,0,2026-06-03 10:00:00:000 AM,C1";
        assertReason(line, RejectionReason.NON_POSITIVE_QUANTITY);
    }

    @Test
    void rejectsNonNumericBroker() {
        String line = "ABC,x,2,10,10,100,2026-06-03 10:00:00:000 AM,C1";
        assertReason(line, RejectionReason.INVALID_BROKER);
    }

    private void assertReason(String line, RejectionReason expected) {
        assertThatThrownBy(() -> parser.parse(line, D, TOL))
                .isInstanceOfSatisfying(RowRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(expected));
    }
}
