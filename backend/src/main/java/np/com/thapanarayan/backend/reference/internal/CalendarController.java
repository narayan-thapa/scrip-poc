package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.TradingCalendar;

@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController {

    /** Bound on the trading-days range to keep responses sane. */
    private static final long MAX_RANGE_DAYS = 3660; // ~10 years

    private final TradingCalendar calendar;

    CalendarController(TradingCalendar calendar) {
        this.calendar = calendar;
    }

    record IsOpenResponse(LocalDate date, boolean open) {
    }

    @GetMapping("/trading-days")
    List<LocalDate> tradingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from.isAfter(to)) {
            throw new DomainException("BAD_RANGE", "'from' must not be after 'to'");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new DomainException("RANGE_TOO_LARGE",
                    "Range exceeds the maximum of " + MAX_RANGE_DAYS + " days");
        }
        return calendar.tradingDaysBetween(from, to);
    }

    @GetMapping("/is-open")
    IsOpenResponse isOpen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new IsOpenResponse(date, calendar.isTradingDay(date));
    }
}
