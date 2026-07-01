package np.com.thapanarayan.backend.backtest.internal;

import java.util.List;

/**
 * NEPSE friction model parameters (§7.2). Rates change with SEBON/NEPSE circulars, so they live in
 * config / the run request — never hard-coded. Defaults are illustrative, not authoritative.
 */
public record CostConfig(
        List<Tier> brokerageTiers,
        double sebonPct,       // % of turnover
        double dpPerScrip,     // flat, sell side, per scrip per settlement
        double cgtPct,         // % of realized gain
        double slippageBps) {  // basis points on fill price

    /** Brokerage rate (percent of turnover) for transaction values up to {@code upToValue}. */
    public record Tier(double upToValue, double ratePct) {}

    public static CostConfig defaults() {
        return new CostConfig(
                List.of(
                        new Tier(50_000, 0.40),
                        new Tier(500_000, 0.37),
                        new Tier(2_000_000, 0.34),
                        new Tier(10_000_000, 0.30),
                        new Tier(Double.MAX_VALUE, 0.27)),
                0.015,   // SEBON regulatory fee
                25.0,    // DP charge per scrip (sell)
                7.5,     // short-term CGT (individual)
                5.0);    // 5 bps slippage
    }

    public CostConfig {
        if (brokerageTiers == null || brokerageTiers.isEmpty()) {
            brokerageTiers = defaults().brokerageTiers();
        }
    }
}
