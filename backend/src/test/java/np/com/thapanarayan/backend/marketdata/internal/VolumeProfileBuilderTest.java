package np.com.thapanarayan.backend.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.api.VolumeNode;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/**
 * Volume-at-price profile maths (§6.2) on a hand-built distribution where the POC,
 * value area and node classification are known by construction.
 *
 * <p>Bins (B=3, width=1 over [100,103]): bin0 [100,101)=10, bin1 [101,102)=50,
 * bin2 [102,103]=25 (the max price 103 clamps into the top bin). Total 85.</p>
 */
class VolumeProfileBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static FloorsheetTradeRecord trade(double price, long qty) {
        BigDecimal p = BigDecimal.valueOf(price);
        return new FloorsheetTradeRecord("ABC", 1, 2, qty, p,
                p.multiply(BigDecimal.valueOf(qty)), LocalDateTime.of(DATE, java.time.LocalTime.NOON), DATE);
    }

    private static List<FloorsheetTradeRecord> distribution() {
        List<FloorsheetTradeRecord> trades = new ArrayList<>();
        trades.add(trade(100, 10));
        trades.add(trade(101, 50));
        trades.add(trade(102, 20));
        trades.add(trade(103, 5));
        return trades;
    }

    @Test
    void pocValueAreaAndNodes() {
        VolumeProfileView v = VolumeProfileBuilder.build("ABC", DATE, distribution(), 3, 0.70);

        assertThat(v.binCount()).isEqualTo(3);
        assertThat(v.binWidth()).isEqualByComparingTo("1");
        assertThat(v.totalVolume()).isEqualTo(85);
        // POC is the high-volume bin's midpoint: (101 + 102) / 2.
        assertThat(v.poc()).isEqualByComparingTo("101.5");
        // Value area expands POC(bin1) -> bin2 (25 >= 10) and stops at 75 >= ceil(.7*85)=60.
        assertThat(v.valueAreaVolume()).isEqualTo(75);
        assertThat(v.valueAreaLow()).isEqualByComparingTo("101");
        assertThat(v.valueAreaHigh()).isEqualByComparingTo("103");

        assertThat(v.bins()).hasSize(3);
        assertThat(v.bins().get(0).node()).isEqualTo(VolumeNode.LVN);
        assertThat(v.bins().get(0).inValueArea()).isFalse();
        assertThat(v.bins().get(1).node()).isEqualTo(VolumeNode.HVN);
        assertThat(v.bins().get(1).inValueArea()).isTrue();
        assertThat(v.bins().get(2).node()).isEqualTo(VolumeNode.LVN);
        assertThat(v.bins().get(2).inValueArea()).isTrue();
    }

    @Test
    void singlePriceCollapsesToOneBin() {
        VolumeProfileView v = VolumeProfileBuilder.build("ABC", DATE,
                List.of(trade(250, 100), trade(250, 40)), 24, 0.70);

        assertThat(v.binCount()).isEqualTo(1);
        assertThat(v.poc()).isEqualByComparingTo("250");
        assertThat(v.valueAreaHigh()).isEqualByComparingTo("250");
        assertThat(v.valueAreaLow()).isEqualByComparingTo("250");
        assertThat(v.totalVolume()).isEqualTo(140);
        assertThat(v.valueAreaVolume()).isEqualTo(140);
        assertThat(v.bins()).singleElement()
                .satisfies(b -> assertThat(b.inValueArea()).isTrue());
    }
}
