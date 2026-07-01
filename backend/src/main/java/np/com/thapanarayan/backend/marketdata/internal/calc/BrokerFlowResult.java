package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.util.List;

/**
 * Output of {@link BrokerFlowCalculator}: per-broker aggregates plus concentration metrics. High
 * concentration is suggestive (one broker often aggregates many clients), never a standalone signal.
 */
public record BrokerFlowResult(
        List<BrokerAgg> brokers,
        double topBuyerShare,
        double topSellerShare,
        double hhiBuy,
        double hhiSell) {

    /** One broker's buy/sell totals for a symbol+date (symbol/date attached when persisted). */
    public record BrokerAgg(int brokerId, long buyQty, long sellQty, BigDecimal buyAmount, BigDecimal sellAmount) {
        public long netQty() {
            return buyQty - sellQty;
        }
    }
}
