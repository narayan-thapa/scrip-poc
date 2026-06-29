package np.com.thapanarayan.backend.reference.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.platform.api.NepseClock;

/**
 * Populates {@code trading_day} once, on first startup, for the configured
 * window: every calendar day gets a row, closed on the Nepali weekend
 * (Friday &amp; Saturday) and on dates listed in the optional
 * {@code classpath:reference/nepse-holidays.csv}. Real holiday data is admin-maintained;
 * absent that file, only the weekend rule applies.
 */
@Component
@Order(10)
class TradingCalendarSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TradingCalendarSeeder.class);
    private static final String HOLIDAYS_RESOURCE = "reference/nepse-holidays.csv";

    private final TradingDayRepository repository;
    private final CalendarService calendarService;
    private final CalendarProperties properties;
    private final NepseClock clock;

    TradingCalendarSeeder(TradingDayRepository repository, CalendarService calendarService,
            CalendarProperties properties, NepseClock clock) {
        this.repository = repository;
        this.calendarService = calendarService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            calendarService.reload();
            return;
        }
        Map<LocalDate, String> holidays = loadHolidays();
        LocalDate from = properties.startDate();
        LocalDate to = clock.today().plusDays(properties.forwardDays());

        List<TradingDayEntity> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek() == DayOfWeek.FRIDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY;
            String holidayNote = holidays.get(d);
            boolean open = !weekend && holidayNote == null;
            String note = holidayNote != null ? holidayNote : (weekend ? "weekend" : null);
            rows.add(new TradingDayEntity(d, open, note));
        }
        repository.saveAll(rows);
        calendarService.reload();
        log.info("Seeded {} trading-calendar days from {} to {} ({} holidays applied)",
                rows.size(), from, to, holidays.size());
    }

    private Map<LocalDate, String> loadHolidays() {
        Map<LocalDate, String> holidays = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(HOLIDAYS_RESOURCE);
        if (!resource.exists()) {
            return holidays;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",", 2);
                try {
                    LocalDate date = LocalDate.parse(parts[0].trim());
                    String note = parts.length > 1 ? parts[1].trim() : "holiday";
                    holidays.put(date, note.isEmpty() ? "holiday" : note);
                } catch (RuntimeException badRow) {
                    log.warn("Skipping malformed holiday row: '{}'", trimmed);
                }
            }
        } catch (IOException e) {
            log.warn("Could not read {}: {}", HOLIDAYS_RESOURCE, e.getMessage());
        }
        return holidays;
    }
}
