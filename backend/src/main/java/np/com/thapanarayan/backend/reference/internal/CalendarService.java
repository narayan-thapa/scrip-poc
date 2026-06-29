package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.TradingCalendar;

/**
 * {@link TradingCalendar} backed by the {@code trading_day} table. Open trading
 * days are materialized into a sorted epoch-day array so look-backs are O(log n)
 * binary searches rather than per-day queries — important because indicator
 * warm-up windows ask for hundreds of trading days back. Queries outside the
 * seeded coverage range fail loudly rather than guessing.
 */
@Service
class CalendarService implements TradingCalendar {

    private final TradingDayRepository repository;

    /** Immutable snapshot, swapped atomically on {@link #reload()}. */
    private volatile Snapshot snapshot;

    CalendarService(TradingDayRepository repository) {
        this.repository = repository;
    }

    private record Snapshot(long[] openDays, long coverageFrom, long coverageTo) {
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        LocalDate min = repository.minDate();
        LocalDate max = repository.maxDate();
        if (min == null || max == null) {
            this.snapshot = new Snapshot(new long[0], Long.MAX_VALUE, Long.MIN_VALUE);
            return;
        }
        long[] open = repository.findAllOpenOrdered().stream()
                .mapToLong(t -> t.getTradeDate().toEpochDay())
                .toArray();
        this.snapshot = new Snapshot(open, min.toEpochDay(), max.toEpochDay());
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            reload();
            s = snapshot;
        }
        return s;
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        Snapshot s = snapshot();
        requireCovered(s, date);
        return Arrays.binarySearch(s.openDays(), date.toEpochDay()) >= 0;
    }

    @Override
    public LocalDate previousTradingDay(LocalDate date) {
        Snapshot s = snapshot();
        requireCovered(s, date);
        int idx = indexStrictlyBefore(s.openDays(), date.toEpochDay());
        if (idx < 0) {
            throw insufficientHistory(date, "previous");
        }
        return LocalDate.ofEpochDay(s.openDays()[idx]);
    }

    @Override
    public LocalDate nextTradingDay(LocalDate date) {
        Snapshot s = snapshot();
        requireCovered(s, date);
        int idx = indexStrictlyAfter(s.openDays(), date.toEpochDay());
        if (idx >= s.openDays().length) {
            throw insufficientHistory(date, "next");
        }
        return LocalDate.ofEpochDay(s.openDays()[idx]);
    }

    @Override
    public LocalDate minusTradingDays(LocalDate date, int n) {
        if (n < 0) {
            throw new DomainException("CALENDAR_BAD_ARGUMENT", "n must be >= 0, was " + n);
        }
        Snapshot s = snapshot();
        requireCovered(s, date);
        int anchor = indexAtOrBefore(s.openDays(), date.toEpochDay());
        int target = anchor - n;
        if (anchor < 0 || target < 0) {
            throw insufficientHistory(date, n + " trading days before");
        }
        return LocalDate.ofEpochDay(s.openDays()[target]);
    }

    @Override
    public List<LocalDate> tradingDaysBetween(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive.isAfter(toInclusive)) {
            return List.of();
        }
        Snapshot s = snapshot();
        requireCovered(s, fromInclusive);
        requireCovered(s, toInclusive);
        long[] open = s.openDays();
        int start = indexAtOrAfter(open, fromInclusive.toEpochDay());
        List<LocalDate> result = new ArrayList<>();
        long toEpoch = toInclusive.toEpochDay();
        for (int i = start; i < open.length && open[i] <= toEpoch; i++) {
            result.add(LocalDate.ofEpochDay(open[i]));
        }
        return result;
    }

    private void requireCovered(Snapshot s, LocalDate date) {
        long e = date.toEpochDay();
        if (e < s.coverageFrom() || e > s.coverageTo()) {
            throw new DomainException("CALENDAR_OUT_OF_RANGE",
                    "Date " + date + " is outside the seeded trading calendar coverage");
        }
    }

    private DomainException insufficientHistory(LocalDate date, String what) {
        return new DomainException("CALENDAR_INSUFFICIENT_HISTORY",
                "No trading day available for '" + what + "' relative to " + date);
    }

    /** Index of the latest open day &lt;= epoch, or -1. */
    private static int indexAtOrBefore(long[] open, long epoch) {
        int pos = Arrays.binarySearch(open, epoch);
        return pos >= 0 ? pos : (-pos - 1) - 1;
    }

    /** Index of the latest open day &lt; epoch, or -1. */
    private static int indexStrictlyBefore(long[] open, long epoch) {
        int pos = Arrays.binarySearch(open, epoch);
        return pos >= 0 ? pos - 1 : (-pos - 1) - 1;
    }

    /** Index of the earliest open day &gt;= epoch, or open.length. */
    private static int indexAtOrAfter(long[] open, long epoch) {
        int pos = Arrays.binarySearch(open, epoch);
        return pos >= 0 ? pos : (-pos - 1);
    }

    /** Index of the earliest open day &gt; epoch, or open.length. */
    private static int indexStrictlyAfter(long[] open, long epoch) {
        int pos = Arrays.binarySearch(open, epoch);
        return pos >= 0 ? pos + 1 : (-pos - 1);
    }
}
