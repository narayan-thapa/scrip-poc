package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.calc.BrokerFlowResult.BrokerAgg;

/**
 * Aggregates buyer/seller broker IDs into per-broker buy/sell totals and concentration metrics
 * (top-N share + Herfindahl index) — the NEPSE-specific accumulation/distribution input (§6.3).
 */
public final class BrokerFlowCalculator {

    private BrokerFlowCalculator() {
    }

    public static BrokerFlowResult compute(List<TradeView> trades) {
        Map<Integer, long[]> qty = new HashMap<>();          // broker -> [buyQty, sellQty]
        Map<Integer, BigDecimal[]> amt = new HashMap<>();     // broker -> [buyAmt, sellAmt]

        for (TradeView t : trades) {
            accumulate(qty, amt, t.buyerBroker(), t.quantity(), t.amount(), true);
            accumulate(qty, amt, t.sellerBroker(), t.quantity(), t.amount(), false);
        }

        List<BrokerAgg> brokers = new ArrayList<>(qty.size());
        long totalBuy = 0;
        long totalSell = 0;
        long maxBuy = 0;
        long maxSell = 0;
        for (var e : qty.entrySet()) {
            int id = e.getKey();
            long buy = e.getValue()[0];
            long sell = e.getValue()[1];
            BigDecimal[] a = amt.get(id);
            brokers.add(new BrokerAgg(id, buy, sell, a[0], a[1]));
            totalBuy += buy;
            totalSell += sell;
            maxBuy = Math.max(maxBuy, buy);
            maxSell = Math.max(maxSell, sell);
        }

        return new BrokerFlowResult(
                brokers,
                share(maxBuy, totalBuy),
                share(maxSell, totalSell),
                hhi(brokers, totalBuy, true),
                hhi(brokers, totalSell, false));
    }

    private static void accumulate(Map<Integer, long[]> qty, Map<Integer, BigDecimal[]> amt,
                                   int broker, long q, BigDecimal a, boolean buy) {
        long[] qq = qty.computeIfAbsent(broker, k -> new long[2]);
        BigDecimal[] aa = amt.computeIfAbsent(broker, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        int i = buy ? 0 : 1;
        qq[i] += q;
        aa[i] = aa[i].add(a);
    }

    private static double share(long max, long total) {
        return total == 0 ? 0.0 : (double) max / total;
    }

    private static double hhi(List<BrokerAgg> brokers, long total, boolean buy) {
        if (total == 0) {
            return 0.0;
        }
        double sum = 0;
        for (BrokerAgg b : brokers) {
            double s = (double) (buy ? b.buyQty() : b.sellQty()) / total;
            sum += s * s;
        }
        return sum;
    }
}
