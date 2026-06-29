package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;

/** The metric-relevant slice of one closed trade. */
record TradeStat(double returnPct, BigDecimal netPnl, BigDecimal costs) {

    boolean isWin() {
        return netPnl.signum() > 0;
    }
}
