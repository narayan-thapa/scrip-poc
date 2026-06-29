package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import np.com.thapanarayan.backend.backtest.api.BrokerageTier;
import np.com.thapanarayan.backend.backtest.api.CostModelSpec;

/**
 * Tunables for the backtest engine (§7.2). The scalar cost rates and a default
 * tiered-brokerage ladder live here so a run can omit a cost model and still be
 * realistic; a run that supplies its own {@link CostModelSpec} overrides all of it.
 *
 * <p><b>Illustrative defaults</b> — set the exact figures from current SEBON/NEPSE
 * circulars. Rates are fractions (0.0040 = 0.40%); flat charges and capital are NPR.</p>
 *
 * @param startingCapital     default portfolio capital when a run omits it
 * @param warmupBars          history bars loaded before the range to warm the indicators
 * @param sebonFeeRate        SEBON fee as a fraction of turnover, each side
 * @param dpChargePerScrip    flat depository charge per scrip on the sell side
 * @param capitalGainsTaxRate CGT as a fraction of realized gain
 * @param slippageBps         assumed slippage in basis points per fill
 * @param refreshWindowDays   trailing window backtested when {@code SignalsGeneratedEvent} auto-refreshes the BUY list
 */
@ConfigurationProperties(prefix = "nepse.backtest")
record BacktestProperties(
        BigDecimal startingCapital,
        Integer warmupBars,
        BigDecimal sebonFeeRate,
        BigDecimal dpChargePerScrip,
        BigDecimal capitalGainsTaxRate,
        BigDecimal slippageBps,
        Integer refreshWindowDays) {

    BacktestProperties {
        if (startingCapital == null || startingCapital.signum() <= 0) {
            startingCapital = BigDecimal.valueOf(1_000_000);
        }
        if (warmupBars == null || warmupBars < 1) {
            warmupBars = 260;
        }
        if (refreshWindowDays == null || refreshWindowDays < 1) {
            refreshWindowDays = 365;
        }
        if (sebonFeeRate == null) {
            sebonFeeRate = new BigDecimal("0.00015");
        }
        if (dpChargePerScrip == null) {
            dpChargePerScrip = new BigDecimal("25");
        }
        if (capitalGainsTaxRate == null) {
            capitalGainsTaxRate = new BigDecimal("0.075");
        }
        if (slippageBps == null) {
            slippageBps = new BigDecimal("5");
        }
    }

    /** The default NEPSE cost model: a standard equity brokerage ladder + the scalar rates above. */
    CostModelSpec defaultCostModel() {
        List<BrokerageTier> tiers = List.of(
                new BrokerageTier(new BigDecimal("50000"), new BigDecimal("0.0040")),
                new BrokerageTier(new BigDecimal("500000"), new BigDecimal("0.0037")),
                new BrokerageTier(new BigDecimal("2000000"), new BigDecimal("0.0034")),
                new BrokerageTier(new BigDecimal("10000000"), new BigDecimal("0.0030")),
                new BrokerageTier(null, new BigDecimal("0.0027")));
        return new CostModelSpec(tiers, sebonFeeRate, dpChargePerScrip, capitalGainsTaxRate, slippageBps);
    }
}
