package np.com.thapanarayan.backend.reference.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendar-aware trading-day math, published for every downstream module. All "look-back" windows
 * in the platform ("14-day RSI", "20-day volume average", "previous close") count <b>trading days</b>
 * from this calendar — never naive calendar-date subtraction — so holidays and weekends are skipped
 * correctly.
 */
public interface TradingCalendar {

    /** True if NEPSE traded on {@code date}. */
    boolean isTradingDay(LocalDate date);

    /** The most recent trading day strictly before {@code date}, or empty if none is known. */
    java.util.Optional<LocalDate> previousTradingDay(LocalDate date);

    /** The first trading day on or after {@code date}, or empty if none is known. */
    java.util.Optional<LocalDate> nextTradingDayOnOrAfter(LocalDate date);

    /**
     * The trading day {@code n} sessions before {@code date} (n &ge; 1), skipping holidays/weekends,
     * or empty if history is insufficient. {@code nTradingDaysBefore(d, 1)} == {@code previousTradingDay(d)}.
     */
    java.util.Optional<LocalDate> nTradingDaysBefore(LocalDate date, int n);

    /** Open trading days within the inclusive range, ascending. */
    List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate to);
}
