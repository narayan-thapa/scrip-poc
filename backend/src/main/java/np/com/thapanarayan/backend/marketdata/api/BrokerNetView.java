package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;

/**
 * Net flow for one broker in a (symbol, trade_date). Positive {@code netQuantity}
 * means the broker bought more than it sold (accumulation); negative means
 * distribution.
 */
public record BrokerNetView(
        int brokerId,
        long buyQuantity,
        long sellQuantity,
        long netQuantity,
        BigDecimal buyAmount,
        BigDecimal sellAmount,
        BigDecimal netAmount) {
}
