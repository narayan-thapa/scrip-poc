package np.com.thapanarayan.backend.marketdata.api;

/**
 * Published broker-flow concentration for a (symbol, date) — consumed by the S8 strategy. Shares are
 * 0..1; HHI is the Herfindahl concentration index. Suggestive, never a standalone trigger.
 */
public record BrokerFlowView(
        String symbol,
        double topBuyerShare,
        double topSellerShare,
        double hhiBuy,
        double hhiSell) {
}
