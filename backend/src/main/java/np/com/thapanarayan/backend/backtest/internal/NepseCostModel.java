package np.com.thapanarayan.backend.backtest.internal;

/**
 * NEPSE transaction costs (§7.2): tiered brokerage + SEBON fee on both sides, a flat DP charge on the
 * sell side, and CGT on realized gains — frictions Ta4j's flat cost model can't express, so they're
 * applied in this custom layer. Pure and deterministic; slippage is applied to fill prices by the engine.
 */
final class NepseCostModel {

    private final CostConfig config;

    NepseCostModel(CostConfig config) {
        this.config = config;
    }

    /** Buy-side charges on a purchase of {@code turnover} rupees. */
    double buyCost(double turnover) {
        return brokerage(turnover) + sebon(turnover);
    }

    /** Sell-side charges (adds the flat per-scrip DP charge). */
    double sellCost(double turnover) {
        return brokerage(turnover) + sebon(turnover) + config.dpPerScrip();
    }

    /** Capital gains tax on a realized gain (zero if the position lost money). */
    double capitalGainsTax(double realizedGain) {
        return realizedGain > 0 ? realizedGain * config.cgtPct() / 100.0 : 0.0;
    }

    /** Apply slippage to a fill price: worse for the trader (higher on buy, lower on sell). */
    double applySlippage(double price, boolean buy) {
        double factor = config.slippageBps() / 10_000.0;
        return buy ? price * (1 + factor) : price * (1 - factor);
    }

    private double brokerage(double turnover) {
        for (CostConfig.Tier tier : config.brokerageTiers()) {
            if (turnover <= tier.upToValue()) {
                return turnover * tier.ratePct() / 100.0;
            }
        }
        return turnover * config.brokerageTiers().get(config.brokerageTiers().size() - 1).ratePct() / 100.0;
    }

    private double sebon(double turnover) {
        return turnover * config.sebonPct() / 100.0;
    }
}
