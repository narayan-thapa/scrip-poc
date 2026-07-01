package np.com.thapanarayan.backend.marketdata.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-(symbol, date, broker) buy/sell aggregates from the floorsheet's buyer/seller IDs. */
public record BrokerFlow(
        String symbol,
        LocalDate tradeDate,
        int brokerId,
        long buyQty,
        long sellQty,
        long netQty,
        BigDecimal buyAmount,
        BigDecimal sellAmount) {
}
