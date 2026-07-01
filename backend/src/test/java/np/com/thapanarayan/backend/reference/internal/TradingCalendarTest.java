package np.com.thapanarayan.backend.reference.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import np.com.thapanarayan.backend.reference.internal.TradingCalendarImpl;
import np.com.thapanarayan.backend.reference.internal.domain.TradingDay;
import np.com.thapanarayan.backend.reference.internal.repo.TradingDayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * No-Docker test of trading-day look-backs. The repository is stubbed to compute results from an
 * in-memory calendar exactly as the SQL would (open-only, ordered, limited), so the test proves the
 * service skips a mid-week holiday AND the weekend when counting trading days back.
 *
 * Calendar (June 2026): Mon15 open · Tue16 open · Wed17 HOLIDAY · Thu18 open · Fri19/Sat20 weekend ·
 * Sun21 open · Mon22 open.
 */
@ExtendWith(MockitoExtension.class)
class TradingCalendarTest {

    @Mock
    TradingDayRepository repository;

    TradingCalendarImpl calendar;

    private final List<TradingDay> days = List.of(
            new TradingDay(LocalDate.of(2026, 6, 15), true, null),
            new TradingDay(LocalDate.of(2026, 6, 16), true, null),
            new TradingDay(LocalDate.of(2026, 6, 17), false, "Holiday"),
            new TradingDay(LocalDate.of(2026, 6, 18), true, null),
            new TradingDay(LocalDate.of(2026, 6, 19), false, "Weekend"),
            new TradingDay(LocalDate.of(2026, 6, 20), false, "Weekend"),
            new TradingDay(LocalDate.of(2026, 6, 21), true, null),
            new TradingDay(LocalDate.of(2026, 6, 22), true, null));

    @BeforeEach
    void setUp() {
        calendar = new TradingCalendarImpl(repository);
    }

    private void stubLookBack() {
        when(repository.findByTradeDateLessThanAndOpenTrueOrderByTradeDateDesc(any(), any()))
                .thenAnswer(inv -> {
                    LocalDate before = inv.getArgument(0);
                    Pageable pageable = inv.getArgument(1);
                    return days.stream()
                            .filter(TradingDay::isOpen)
                            .filter(td -> td.getTradeDate().isBefore(before))
                            .sorted(Comparator.comparing(TradingDay::getTradeDate).reversed())
                            .limit(pageable.getPageSize())
                            .toList();
                });
    }

    @Test
    void previousTradingDaySkipsHoliday() {
        stubLookBack();
        // Day before Thu 18th is Wed 17th (holiday) → must fall back to Tue 16th.
        assertThat(calendar.previousTradingDay(LocalDate.of(2026, 6, 18)))
                .contains(LocalDate.of(2026, 6, 16));
    }

    @Test
    void nTradingDaysBeforeSkipsHolidayAndWeekend() {
        stubLookBack();
        // 3 trading days before Mon 22nd: Sun21(1), Thu18(2), Tue16(3) — Fri19/Sat20/Wed17 skipped.
        assertThat(calendar.nTradingDaysBefore(LocalDate.of(2026, 6, 22), 3))
                .contains(LocalDate.of(2026, 6, 16));
    }

    @Test
    void insufficientHistoryReturnsEmpty() {
        stubLookBack();
        // Only two open days before Tue 16th (Mon 15th) → asking for 5 yields empty.
        assertThat(calendar.nTradingDaysBefore(LocalDate.of(2026, 6, 16), 5)).isEmpty();
    }

    @Test
    void nMustBePositive() {
        assertThatThrownBy(() -> calendar.nTradingDaysBefore(LocalDate.of(2026, 6, 22), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
