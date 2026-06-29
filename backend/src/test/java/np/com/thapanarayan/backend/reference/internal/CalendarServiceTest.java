package np.com.thapanarayan.backend.reference.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.platform.api.DomainException;

/**
 * Exercises the calendar look-back arithmetic against a known, hand-built set of
 * open trading days. Pure logic — no Spring context or database needed.
 *
 * <p>Open days (June 2026): Mon 1, Tue 2, Wed 3, Thu 4, [Fri 5 / Sat 6 weekend],
 * Sun 7, Mon 8. Coverage = 2026-06-01 .. 2026-06-30.</p>
 */
class CalendarServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 6, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 6, 4);
    private static final LocalDate FRI = LocalDate.of(2026, 6, 5);
    private static final LocalDate D7 = LocalDate.of(2026, 6, 7);
    private static final LocalDate D8 = LocalDate.of(2026, 6, 8);

    private CalendarService calendar;

    @BeforeEach
    void setUp() {
        TradingDayRepository repo = mock(TradingDayRepository.class);
        when(repo.minDate()).thenReturn(D1);
        when(repo.maxDate()).thenReturn(LocalDate.of(2026, 6, 30));
        when(repo.findAllOpenOrdered()).thenReturn(List.of(
                openDay(D1), openDay(D2), openDay(D3), openDay(D4), openDay(D7), openDay(D8)));
        calendar = new CalendarService(repo);
        calendar.reload();
    }

    private static TradingDayEntity openDay(LocalDate date) {
        return new TradingDayEntity(date, true, null);
    }

    @Test
    void isTradingDayReflectsOpenSet() {
        assertThat(calendar.isTradingDay(D3)).isTrue();
        assertThat(calendar.isTradingDay(FRI)).isFalse();
    }

    @Test
    void previousAndNextSkipTheWeekend() {
        assertThat(calendar.previousTradingDay(D7)).isEqualTo(D4);
        assertThat(calendar.nextTradingDay(D4)).isEqualTo(D7);
    }

    @Test
    void minusTradingDaysCountsOpenDaysOnly() {
        assertThat(calendar.minusTradingDays(D8, 2)).isEqualTo(D4);
        assertThat(calendar.minusTradingDays(D8, 5)).isEqualTo(D1);
    }

    @Test
    void minusTradingDaysBeyondHistoryThrows() {
        assertThatThrownBy(() -> calendar.minusTradingDays(D8, 6))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("trading days before");
    }

    @Test
    void tradingDaysBetweenIsInclusiveAndOpenOnly() {
        assertThat(calendar.tradingDaysBetween(D2, D7))
                .containsExactly(D2, D3, D4, D7);
    }

    @Test
    void outsideCoverageThrows() {
        assertThatThrownBy(() -> calendar.isTradingDay(LocalDate.of(2026, 7, 1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("outside the seeded trading calendar");
    }
}
