package np.com.thapanarayan.backend.platform.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendar-aware date math over NEPSE trading days. Every look-back in the
 * platform ("14-day RSI", "20-day volume average") counts <em>trading days</em>,
 * not calendar days, so holidays and weekends are skipped here rather than via
 * naive date subtraction.
 *
 * <p>The published contract lives in {@code platform}; the {@code reference}
 * module owns the concrete implementation backed by the {@code trading_day}
 * table (delivered in Stage 1).</p>
 */
public interface TradingCalendar {

    boolean isTradingDay(LocalDate date);

    /** The most recent trading day strictly before {@code date}. */
    LocalDate previousTradingDay(LocalDate date);

    /** The earliest trading day strictly after {@code date}. */
    LocalDate nextTradingDay(LocalDate date);

    /** {@code date} shifted back by {@code n} trading days (n &gt;= 0). */
    LocalDate minusTradingDays(LocalDate date, int n);

    /** Trading days in the inclusive range, ascending. */
    List<LocalDate> tradingDaysBetween(LocalDate fromInclusive, LocalDate toInclusive);
}
