package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * The fully-configurable NEPSE cost model (§7.2). Rates change with SEBON/NEPSE
 * circulars, so they are data — supplied per run and stored on the run — never
 * hard-coded. All rates are fractions (0.0036 = 0.36%); flat charges are in NPR.
 *
 * @param brokerageTiers     tiered broker commission by per-side transaction value, ascending
 * @param sebonFeeRate       SEBON regulatory fee as a fraction of turnover (each side)
 * @param dpChargePerScrip   flat depository charge per scrip per settlement, applied on the sell side
 * @param capitalGainsTaxRate CGT as a fraction of realized gain (sell side, gains only)
 * @param slippageBps        assumed slippage in basis points applied to each fill price
 */
public record CostModelSpec(
        List<BrokerageTier> brokerageTiers,
        BigDecimal sebonFeeRate,
        BigDecimal dpChargePerScrip,
        BigDecimal capitalGainsTaxRate,
        BigDecimal slippageBps) {
}
