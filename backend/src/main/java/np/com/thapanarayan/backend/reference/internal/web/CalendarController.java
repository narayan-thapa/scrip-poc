package np.com.thapanarayan.backend.reference.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import np.com.thapanarayan.backend.reference.api.TradingCalendar;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trading-calendar queries used by the UI and (internally) by every look-back window. */
@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar", description = "NEPSE trading-day calendar")
class CalendarController {

    /** Bound the range so a single request can't ask for an unbounded scan. */
    private static final long MAX_RANGE_DAYS = 366;

    private final TradingCalendar calendar;

    CalendarController(TradingCalendar calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/trading-days")
    List<LocalDate> tradingDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "'to' must be on/after 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Range exceeds " + MAX_RANGE_DAYS + " days");
        }
        return calendar.tradingDaysBetween(from, to);
    }

    @GetMapping("/is-open")
    IsOpenResponse isOpen(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return new IsOpenResponse(date, calendar.isTradingDay(date));
    }

    record IsOpenResponse(LocalDate date, boolean open) {}
}
