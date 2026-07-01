package np.com.thapanarayan.backend.marketdata.internal.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;
import org.junit.jupiter.api.Test;

/** Unit tests for the pure aggregation maths: candle OHLCV/VWAP, volume profile, broker flow. */
class CalculatorsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static TradeView trade(int buyer, int seller, long qty, String price, int minute) {
        BigDecimal p = new BigDecimal(price);
        return new TradeView("BHCL", buyer, seller, qty, p, p.multiply(BigDecimal.valueOf(qty)),
                LocalDateTime.of(2026, 6, 3, 11, minute, 0));
    }

    // ---- candle ----

    @Test
    void candleComputesOhlcVwapAndChange() {
        List<TradeView> trades = List.of(
                trade(1, 2, 100, "100", 1),   // open
                trade(1, 2, 50, "110", 2),    // high
                trade(1, 2, 100, "90", 3),    // low
                trade(1, 2, 50, "105", 4));   // close
        DailyCandle c = CandleCalculator.compute("BHCL", DATE, trades, new BigDecimal("100"));

        assertThat(c.open()).isEqualByComparingTo("100");
        assertThat(c.high()).isEqualByComparingTo("110");
        assertThat(c.low()).isEqualByComparingTo("90");
        assertThat(c.close()).isEqualByComparingTo("105");
        assertThat(c.volume()).isEqualTo(300);
        assertThat(c.tradesCount()).isEqualTo(4);
        // turnover = 100*100 + 110*50 + 90*100 + 105*50 = 10000+5500+9000+5250 = 29750; vwap=29750/300
        assertThat(c.turnover()).isEqualByComparingTo("29750");
        assertThat(c.vwap()).isEqualByComparingTo("99.1667");
        // change% = (105-100)/100*100 = 5
        assertThat(c.changePct()).isEqualByComparingTo("5.0000");
    }

    @Test
    void candleChangeIsNullWithoutPrevClose() {
        DailyCandle c = CandleCalculator.compute("BHCL", DATE, List.of(trade(1, 2, 10, "100", 1)), null);
        assertThat(c.changePct()).isNull();
    }

    // ---- volume profile ----

    @Test
    void volumeProfilePicksPocAtHeaviestPrice() {
        List<TradeView> trades = new ArrayList<>();
        // Concentrate volume around price 100, lighter at the wings.
        for (int i = 0; i < 5; i++) trades.add(trade(1, 2, 10, "90", i));
        for (int i = 0; i < 5; i++) trades.add(trade(1, 2, 100, "100", i));
        for (int i = 0; i < 5; i++) trades.add(trade(1, 2, 10, "110", i));

        VolumeProfileResult r = VolumeProfileCalculator.compute(trades, 5);
        assertThat(r.poc().doubleValue()).isBetween(98.0, 102.0);
        // Value area brackets the POC.
        assertThat(r.val().doubleValue()).isLessThanOrEqualTo(r.poc().doubleValue());
        assertThat(r.vah().doubleValue()).isGreaterThanOrEqualTo(r.poc().doubleValue());
        assertThat(r.bins()).hasSize(5);
    }

    @Test
    void volumeProfileHandlesSinglePrice() {
        VolumeProfileResult r = VolumeProfileCalculator.compute(
                List.of(trade(1, 2, 10, "100", 1), trade(1, 2, 5, "100", 2)), 24);
        assertThat(r.poc()).isEqualByComparingTo("100");
        assertThat(r.vah()).isEqualByComparingTo("100");
        assertThat(r.val()).isEqualByComparingTo("100");
        assertThat(r.bins()).hasSize(1);
        assertThat(r.bins().get(0).volume()).isEqualTo(15);
    }

    // ---- broker flow ----

    @Test
    void brokerFlowAggregatesNetAndConcentration() {
        // Broker 1 buys everything; sellers split between 2 and 3.
        List<TradeView> trades = List.of(
                trade(1, 2, 100, "100", 1),
                trade(1, 3, 100, "100", 2),
                trade(1, 2, 200, "100", 3));

        BrokerFlowResult r = BrokerFlowCalculator.compute(trades);

        var buyer1 = r.brokers().stream().filter(b -> b.brokerId() == 1).findFirst().orElseThrow();
        assertThat(buyer1.buyQty()).isEqualTo(400);
        assertThat(buyer1.sellQty()).isZero();
        assertThat(buyer1.netQty()).isEqualTo(400);
        // One buyer dominates → top buyer share = 1.0 and HHI_buy = 1.0
        assertThat(r.topBuyerShare()).isEqualTo(1.0);
        assertThat(r.hhiBuy()).isEqualTo(1.0);
        // Sellers: 2 sold 300, 3 sold 100 → top seller share = 0.75
        assertThat(r.topSellerShare()).isEqualTo(0.75);
        assertThat(r.hhiSell()).isCloseTo(0.625, org.assertj.core.data.Offset.offset(1e-9));
    }
}
