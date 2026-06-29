package np.com.thapanarayan.backend.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.BrokerNetView;

/**
 * Broker accumulation/distribution + concentration (§6.3) on three trades among
 * brokers 1/2/3, where net flow, top-N share and Herfindahl index are known.
 */
class BrokerFlowBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static FloorsheetTradeRecord trade(int buyer, int seller, long qty, double price) {
        BigDecimal p = BigDecimal.valueOf(price);
        return new FloorsheetTradeRecord("ABC", buyer, seller, qty, p,
                p.multiply(BigDecimal.valueOf(qty)), LocalDateTime.of(DATE, LocalTime.NOON), DATE);
    }

    @Test
    void netFlowAndConcentration() {
        List<FloorsheetTradeRecord> trades = List.of(
                trade(1, 2, 100, 10),
                trade(1, 3, 50, 10),
                trade(2, 1, 30, 10));

        List<BrokerNetView> nets = BrokerFlowBuilder.netByBroker(trades);
        BrokerFlowView flow = BrokerFlowBuilder.summarize("ABC", DATE, nets);

        // Rows ordered by net quantity desc: broker1 (+120), broker3 (-50), broker2 (-70).
        assertThat(nets).extracting(BrokerNetView::brokerId).containsExactly(1, 3, 2);
        assertThat(nets.get(0).netQuantity()).isEqualTo(120);
        assertThat(nets.get(0).buyQuantity()).isEqualTo(150);
        assertThat(nets.get(0).sellQuantity()).isEqualTo(30);

        assertThat(flow.topBuyerBroker()).isEqualTo(1);
        assertThat(flow.topBuyerShare()).isEqualByComparingTo("0.8333");  // 150/180
        assertThat(flow.topSellerBroker()).isEqualTo(2);
        assertThat(flow.topSellerShare()).isEqualByComparingTo("0.5556"); // 100/180
        assertThat(flow.hhiBuy()).isEqualByComparingTo("0.7222");
        assertThat(flow.hhiSell()).isEqualByComparingTo("0.4136");
    }
}
