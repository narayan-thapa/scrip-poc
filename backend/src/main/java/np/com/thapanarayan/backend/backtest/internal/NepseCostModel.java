package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import np.com.thapanarayan.backend.backtest.api.BrokerageTier;
import np.com.thapanarayan.backend.backtest.api.CostModelSpec;

/**
 * Applies the NEPSE cost model (§7.2) to a round-trip position. Pure and Spring-free
 * so it is exhaustively unit-testable. All rates come from the {@link CostModelSpec}
 * (data, not code); a {@code null} field is treated as zero so partial specs are safe.
 *
 * <p>Cost components, per real NEPSE frictions:</p>
 * <ul>
 *   <li><b>Slippage</b> — moves each fill against us (buys up, sells down).</li>
 *   <li><b>Broker commission</b> — tiered by per-side transaction value.</li>
 *   <li><b>SEBON fee</b> — a fraction of turnover on each side.</li>
 *   <li><b>DP charge</b> — a flat per-scrip fee on the sell side.</li>
 *   <li><b>Capital Gains Tax</b> — a fraction of the realized price gain (sell side, gains only).</li>
 * </ul>
 */
final class NepseCostModel {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

    private final List<BrokerageTier> tiers;
    private final BigDecimal sebonFeeRate;
    private final BigDecimal dpChargePerScrip;
    private final BigDecimal cgtRate;
    private final BigDecimal slippageBps;

    NepseCostModel(CostModelSpec spec) {
        this.tiers = spec.brokerageTiers() == null ? List.of() : spec.brokerageTiers();
        this.sebonFeeRate = nz(spec.sebonFeeRate());
        this.dpChargePerScrip = nz(spec.dpChargePerScrip());
        this.cgtRate = nz(spec.capitalGainsTaxRate());
        this.slippageBps = nz(spec.slippageBps());
    }

    /** Costs a round trip of {@code quantity} shares bought at {@code rawEntry}, sold at {@code rawExit}. */
    PositionCosts roundTrip(BigDecimal rawEntry, BigDecimal rawExit, long quantity) {
        BigDecimal entry = applySlippage(rawEntry, true);
        BigDecimal exit = applySlippage(rawExit, false);
        BigDecimal qty = BigDecimal.valueOf(quantity);

        BigDecimal buyValue = entry.multiply(qty);
        BigDecimal sellValue = exit.multiply(qty);
        BigDecimal grossPnl = sellValue.subtract(buyValue);

        BigDecimal buyCosts = brokerage(buyValue).add(sebon(buyValue));
        BigDecimal sellCosts = brokerage(sellValue)
                .add(sebon(sellValue))
                .add(dpChargePerScrip)
                .add(capitalGainsTax(grossPnl));

        BigDecimal totalCosts = scale(buyCosts.add(sellCosts));
        BigDecimal netPnl = scale(grossPnl.subtract(totalCosts));
        return new PositionCosts(scale(entry), scale(exit),
                scale(buyCosts), scale(sellCosts), totalCosts, scale(grossPnl), netPnl);
    }

    /** Slippage moves the fill against the trader: buys fill higher, sells fill lower. */
    BigDecimal applySlippage(BigDecimal price, boolean buy) {
        if (slippageBps.signum() == 0) {
            return price;
        }
        BigDecimal factor = slippageBps.divide(TEN_THOUSAND, MC);
        return buy ? price.multiply(BigDecimal.ONE.add(factor), MC)
                : price.multiply(BigDecimal.ONE.subtract(factor), MC);
    }

    private BigDecimal brokerage(BigDecimal value) {
        return value.multiply(tierRate(value), MC);
    }

    /** The commission rate for a transaction value: first tier whose ceiling it fits, else the top tier. */
    private BigDecimal tierRate(BigDecimal value) {
        BigDecimal rate = BigDecimal.ZERO;
        for (BrokerageTier tier : tiers) {
            rate = nz(tier.rate());
            if (tier.uptoAmount() == null || value.compareTo(tier.uptoAmount()) <= 0) {
                return rate;
            }
        }
        return rate; // last tier's rate when value exceeds every finite ceiling
    }

    private BigDecimal sebon(BigDecimal value) {
        return value.multiply(sebonFeeRate, MC);
    }

    private BigDecimal capitalGainsTax(BigDecimal grossGain) {
        return grossGain.signum() > 0 ? grossGain.multiply(cgtRate, MC) : BigDecimal.ZERO;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
