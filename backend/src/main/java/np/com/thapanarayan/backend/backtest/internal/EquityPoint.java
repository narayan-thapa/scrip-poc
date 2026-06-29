package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Portfolio equity on a trading day (pre-drawdown; drawdown is derived for the view). */
record EquityPoint(LocalDate date, BigDecimal equity) {
}
