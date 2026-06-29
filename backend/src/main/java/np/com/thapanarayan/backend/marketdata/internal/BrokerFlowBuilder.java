package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.BrokerNetView;

/**
 * Builds per-broker accumulation/distribution and concentration metrics (§6.3).
 *
 * <p>Netting and {@link #summarize} are separated so the aggregation path can
 * persist per-broker rows while the read path recomputes the (cheap, derivable)
 * concentration metrics from those stored rows — one formula, two callers.</p>
 */
final class BrokerFlowBuilder {

    private static final int SHARE_SCALE = 4;

    private BrokerFlowBuilder() {
    }

    private static final class Acc {
        long buyQty;
        long sellQty;
        BigDecimal buyAmt = BigDecimal.ZERO;
        BigDecimal sellAmt = BigDecimal.ZERO;
    }

    /** Per-broker net rows for a symbol's trades, ordered by net quantity descending. */
    static List<BrokerNetView> netByBroker(List<FloorsheetTradeRecord> trades) {
        Map<Integer, Acc> byBroker = new TreeMap<>();
        for (FloorsheetTradeRecord t : trades) {
            Acc buyer = byBroker.computeIfAbsent(t.buyerBroker(), k -> new Acc());
            buyer.buyQty += t.quantity();
            buyer.buyAmt = buyer.buyAmt.add(t.amount());

            Acc seller = byBroker.computeIfAbsent(t.sellerBroker(), k -> new Acc());
            seller.sellQty += t.quantity();
            seller.sellAmt = seller.sellAmt.add(t.amount());
        }

        List<BrokerNetView> rows = new ArrayList<>(byBroker.size());
        for (var e : byBroker.entrySet()) {
            Acc a = e.getValue();
            rows.add(new BrokerNetView(
                    e.getKey(), a.buyQty, a.sellQty, a.buyQty - a.sellQty,
                    a.buyAmt, a.sellAmt, a.buyAmt.subtract(a.sellAmt)));
        }
        rows.sort(Comparator.comparingLong(BrokerNetView::netQuantity).reversed()
                .thenComparingInt(BrokerNetView::brokerId));
        return rows;
    }

    /** Assembles the full view with top-N share and Herfindahl concentration. */
    static BrokerFlowView summarize(String symbol, LocalDate tradeDate, List<BrokerNetView> brokers) {
        long totalBuy = brokers.stream().mapToLong(BrokerNetView::buyQuantity).sum();
        long totalSell = brokers.stream().mapToLong(BrokerNetView::sellQuantity).sum();

        Integer topBuyer = null;
        Integer topSeller = null;
        long maxBuy = -1L;
        long maxSell = -1L;
        double hhiBuy = 0.0;
        double hhiSell = 0.0;
        for (BrokerNetView b : brokers) {
            if (b.buyQuantity() > maxBuy) {
                maxBuy = b.buyQuantity();
                topBuyer = b.brokerId();
            }
            if (b.sellQuantity() > maxSell) {
                maxSell = b.sellQuantity();
                topSeller = b.brokerId();
            }
            if (totalBuy > 0) {
                double sb = (double) b.buyQuantity() / totalBuy;
                hhiBuy += sb * sb;
            }
            if (totalSell > 0) {
                double ss = (double) b.sellQuantity() / totalSell;
                hhiSell += ss * ss;
            }
        }

        return new BrokerFlowView(symbol, tradeDate, brokers,
                topBuyer, share(maxBuy, totalBuy), topSeller, share(maxSell, totalSell),
                ratio(hhiBuy), ratio(hhiSell));
    }

    private static BigDecimal share(long part, long total) {
        if (total <= 0 || part < 0) {
            return BigDecimal.ZERO.setScale(SHARE_SCALE);
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(SHARE_SCALE, RoundingMode.HALF_UP);
    }
}
