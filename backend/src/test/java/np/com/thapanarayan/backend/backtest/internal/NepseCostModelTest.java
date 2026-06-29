package np.com.thapanarayan.backend.backtest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.backtest.api.BrokerageTier;
import np.com.thapanarayan.backend.backtest.api.CostModelSpec;

/** Each NEPSE cost component (§7.2) on a hand-computed round trip. */
class NepseCostModelTest {

    private static CostModelSpec spec(BigDecimal slippageBps) {
        return new CostModelSpec(
                List.of(new BrokerageTier(new BigDecimal("50000"), new BigDecimal("0.0040")),
                        new BrokerageTier(null, new BigDecimal("0.0030"))),
                new BigDecimal("0.0010"),  // SEBON 0.10%
                new BigDecimal("25"),      // DP NPR 25
                new BigDecimal("0.10"),    // CGT 10%
                slippageBps);
    }

    @Test
    void appliesSlippageBrokerageSebonDpAndCgtOnAProfitableRoundTrip() {
        NepseCostModel model = new NepseCostModel(spec(new BigDecimal("100"))); // 1% slippage
        PositionCosts c = model.roundTrip(new BigDecimal("100"), new BigDecimal("110"), 10);

        // Slippage moves fills against us: buy 100→101, sell 110→108.9
        assertThat(c.entryFillPrice()).isEqualByComparingTo("101.00");
        assertThat(c.exitFillPrice()).isEqualByComparingTo("108.90");
        // gross = 1089 - 1010 = 79
        assertThat(c.grossPnl()).isEqualByComparingTo("79.00");
        // buy: 1010*0.004 + 1010*0.001 = 5.05; sell: 1089*0.004 + 1089*0.001 + 25 + 79*0.10 = 38.345
        assertThat(c.buyCosts()).isEqualByComparingTo("5.05");
        assertThat(c.totalCosts()).isEqualByComparingTo("43.40");
        assertThat(c.netPnl()).isEqualByComparingTo("35.60");
    }

    @Test
    void chargesNoCapitalGainsTaxOnALosingTrade() {
        NepseCostModel model = new NepseCostModel(spec(BigDecimal.ZERO));
        PositionCosts c = model.roundTrip(new BigDecimal("100"), new BigDecimal("90"), 10);

        assertThat(c.grossPnl()).isEqualByComparingTo("-100.00");
        // No slippage; buy 1000, sell 900. Costs: (1000+900)*0.005 + 25 = 9.5 + 25 = 34.50, CGT 0.
        assertThat(c.totalCosts()).isEqualByComparingTo("34.50");
        assertThat(c.netPnl()).isEqualByComparingTo("-134.50");
    }

    @Test
    void usesTheTopTierRateAboveEveryFiniteCeiling() {
        NepseCostModel model = new NepseCostModel(spec(BigDecimal.ZERO));
        // qty 1000 @ 100 → 100000 buy value, above the 50000 ceiling → 0.30% tier.
        PositionCosts c = model.roundTrip(new BigDecimal("100"), new BigDecimal("100"), 1000);

        // buyCosts = 100000*0.003 + 100000*0.001 = 300 + 100 = 400
        assertThat(c.buyCosts()).isEqualByComparingTo("400.00");
    }
}
