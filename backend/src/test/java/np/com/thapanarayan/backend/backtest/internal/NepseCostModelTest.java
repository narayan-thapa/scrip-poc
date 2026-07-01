package np.com.thapanarayan.backend.backtest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class NepseCostModelTest {

    private final NepseCostModel model = new NepseCostModel(CostConfig.defaults());
    private static final Offset<Double> EPS = Offset.offset(0.01);

    @Test
    void buyCostAppliesTieredBrokeragePlusSebon() {
        // turnover 40,000 → tier 0.40% brokerage = 160; SEBON 0.015% = 6 → 166
        assertThat(model.buyCost(40_000)).isCloseTo(166.0, EPS);
    }

    @Test
    void higherTurnoverUsesLowerBrokerageTier() {
        // turnover 1,000,000 → tier 0.34% = 3400; SEBON = 150 → 3550
        assertThat(model.buyCost(1_000_000)).isCloseTo(3550.0, EPS);
    }

    @Test
    void sellCostAddsFlatDpCharge() {
        assertThat(model.sellCost(40_000)).isCloseTo(166.0 + 25.0, EPS);
    }

    @Test
    void cgtOnlyOnGains() {
        assertThat(model.capitalGainsTax(1_000)).isCloseTo(75.0, EPS); // 7.5%
        assertThat(model.capitalGainsTax(-500)).isZero();
    }

    @Test
    void slippageWorsensFillPrice() {
        assertThat(model.applySlippage(100, true)).isCloseTo(100.05, EPS);   // buy higher
        assertThat(model.applySlippage(100, false)).isCloseTo(99.95, EPS);   // sell lower
    }
}
