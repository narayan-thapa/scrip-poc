package np.com.thapanarayan.backend.reference.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import np.com.thapanarayan.backend.reference.internal.domain.Broker;
import np.com.thapanarayan.backend.reference.internal.domain.Instrument;
import np.com.thapanarayan.backend.reference.internal.domain.InstrumentType;
import np.com.thapanarayan.backend.reference.internal.domain.TradingDay;
import np.com.thapanarayan.backend.reference.internal.repo.BrokerRepository;
import np.com.thapanarayan.backend.reference.internal.repo.InstrumentRepository;
import np.com.thapanarayan.backend.reference.internal.repo.TradingDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds minimal reference data on startup so the app is usable without a manual import: a few sample
 * instruments/brokers and a NEPSE trading calendar (Sun–Thu open; Fri/Sat + configured holidays
 * closed). Idempotent — each table is only seeded when empty.
 */
@Component
@ConditionalOnProperty(prefix = "reference.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
class ReferenceSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSeeder.class);

    private final InstrumentRepository instruments;
    private final BrokerRepository brokers;
    private final TradingDayRepository tradingDays;
    private final ReferenceProperties props;

    ReferenceSeeder(InstrumentRepository instruments, BrokerRepository brokers,
                    TradingDayRepository tradingDays, ReferenceProperties props) {
        this.instruments = instruments;
        this.brokers = brokers;
        this.tradingDays = tradingDays;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedInstruments();
        seedBrokers();
        seedCalendar();
    }

    private void seedInstruments() {
        if (instruments.count() > 0) {
            return;
        }
        List<Instrument> seed = new ArrayList<>();
        seed.add(new Instrument("NEPSE", "NEPSE Index", null, InstrumentType.INDEX));
        seed.add(new Instrument("BHCL", "Bhote Koshi Power Company", "Hydropower", InstrumentType.EQUITY));
        seed.add(new Instrument("GRDBL", "Garima Bikas Bank", "Development Bank", InstrumentType.EQUITY));
        seed.add(new Instrument("NABIL", "Nabil Bank", "Commercial Bank", InstrumentType.EQUITY));
        seed.add(new Instrument("NLIC", "Nepal Life Insurance", "Life Insurance", InstrumentType.EQUITY));
        instruments.saveAll(seed);
        log.info("Seeded {} instruments", seed.size());
    }

    private void seedBrokers() {
        if (brokers.count() > 0) {
            return;
        }
        List<Broker> seed = List.of(28, 41, 49, 58, 63).stream()
                .map(id -> new Broker(id, "Broker " + id))
                .toList();
        brokers.saveAll(seed);
        log.info("Seeded {} brokers", seed.size());
    }

    private void seedCalendar() {
        if (tradingDays.count() > 0) {
            return;
        }
        Set<LocalDate> holidays = props.holidays().stream()
                .map(LocalDate::parse)
                .collect(Collectors.toSet());

        LocalDate start = LocalDate.of(props.calendarFromYear(), 1, 1);
        LocalDate end = LocalDate.of(props.calendarToYear(), 12, 31);

        List<TradingDay> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek() == DayOfWeek.FRIDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY;
            boolean holiday = holidays.contains(d);
            boolean open = !weekend && !holiday;
            String note = holiday ? "Holiday" : (weekend ? "Weekend" : null);
            days.add(new TradingDay(d, open, note));
        }
        tradingDays.saveAll(days);
        log.info("Seeded trading calendar {}..{} ({} days, {} holidays)", start, end, days.size(), holidays.size());
    }
}
