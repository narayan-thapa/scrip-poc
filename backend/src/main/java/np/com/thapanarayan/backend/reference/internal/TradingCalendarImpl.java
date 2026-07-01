package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.reference.api.TradingCalendar;
import np.com.thapanarayan.backend.reference.internal.domain.TradingDay;
import np.com.thapanarayan.backend.reference.internal.repo.TradingDayRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * {@link TradingCalendar} backed by the {@code trading_day} table. Look-backs use limit queries
 * (fetch only the N rows needed) rather than scanning, so they stay cheap even over years of history.
 */
@Service
class TradingCalendarImpl implements TradingCalendar {

    private final TradingDayRepository repository;

    TradingCalendarImpl(TradingDayRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        return repository.existsByTradeDateAndOpenTrue(date);
    }

    @Override
    public Optional<LocalDate> previousTradingDay(LocalDate date) {
        return nTradingDaysBefore(date, 1);
    }

    @Override
    public Optional<LocalDate> nextTradingDayOnOrAfter(LocalDate date) {
        return repository
                .findByTradeDateGreaterThanEqualAndOpenTrueOrderByTradeDateAsc(date, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(TradingDay::getTradeDate);
    }

    @Override
    public Optional<LocalDate> nTradingDaysBefore(LocalDate date, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, was " + n);
        }
        List<TradingDay> recent =
                repository.findByTradeDateLessThanAndOpenTrueOrderByTradeDateDesc(date, PageRequest.of(0, n));
        if (recent.size() < n) {
            return Optional.empty();
        }
        return Optional.of(recent.get(n - 1).getTradeDate());
    }

    @Override
    public List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate to) {
        return repository.findByTradeDateBetweenAndOpenTrueOrderByTradeDateAsc(from, to).stream()
                .map(TradingDay::getTradeDate)
                .toList();
    }
}
