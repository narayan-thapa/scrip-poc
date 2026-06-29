package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Broker accumulation / distribution for a (symbol, trade_date), per §6.3.
 * Concentration metrics quantify how dominated the day was by a single broker.
 *
 * @param brokers         per-broker net rows, ordered by net quantity descending
 * @param topBuyerBroker  broker with the largest buy quantity (or {@code null})
 * @param topBuyerShare   that broker's share of total buy volume, 0..1
 * @param topSellerBroker broker with the largest sell quantity (or {@code null})
 * @param topSellerShare  that broker's share of total sell volume, 0..1
 * @param hhiBuy          Herfindahl index of buy concentration, 0..1
 * @param hhiSell         Herfindahl index of sell concentration, 0..1
 */
public record BrokerFlowView(
        String symbol,
        LocalDate tradeDate,
        List<BrokerNetView> brokers,
        Integer topBuyerBroker,
        BigDecimal topBuyerShare,
        Integer topSellerBroker,
        BigDecimal topSellerShare,
        BigDecimal hhiBuy,
        BigDecimal hhiSell) {
}
