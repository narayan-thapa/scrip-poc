package np.com.thapanarayan.backend.reference.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.reference.api.BrokerStatus;
import np.com.thapanarayan.backend.reference.api.InstrumentStatus;

/**
 * Seeds the instrument and broker registries from optional, trusted bundled
 * CSVs ({@code reference/instruments.csv}, {@code reference/brokers.csv}) when
 * the tables are empty. Instruments not seeded here are auto-discovered as
 * PROVISIONAL during ingestion. These seed files are trusted internal resources,
 * not the hostile floorsheet input — simple parsing is fine.
 */
@Component
@Order(20)
class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private final InstrumentRepository instrumentRepository;
    private final BrokerRepository brokerRepository;
    private final NepseClock clock;

    ReferenceDataSeeder(InstrumentRepository instrumentRepository, BrokerRepository brokerRepository,
            NepseClock clock) {
        this.instrumentRepository = instrumentRepository;
        this.brokerRepository = brokerRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (brokerRepository.count() == 0) {
            seedBrokers();
        }
        if (instrumentRepository.count() == 0) {
            seedInstruments();
        }
    }

    private void seedBrokers() {
        List<BrokerEntity> brokers = new ArrayList<>();
        forEachRow("reference/brokers.csv", cols -> {
            if (cols.length < 2) {
                return;
            }
            BrokerEntity b = new BrokerEntity();
            b.setBrokerId(Integer.parseInt(cols[0].trim()));
            b.setName(cols[1].trim());
            b.setStatus(cols.length > 2 ? BrokerStatus.valueOf(cols[2].trim()) : BrokerStatus.ACTIVE);
            brokers.add(b);
        });
        if (!brokers.isEmpty()) {
            brokerRepository.saveAll(brokers);
            log.info("Seeded {} brokers", brokers.size());
        }
    }

    private void seedInstruments() {
        Instant now = Instant.now(clock.clock());
        List<InstrumentEntity> instruments = new ArrayList<>();
        forEachRow("reference/instruments.csv", cols -> {
            if (cols.length < 1 || cols[0].isBlank()) {
                return;
            }
            InstrumentEntity i = new InstrumentEntity();
            i.setSymbol(cols[0].trim());
            i.setName(cols.length > 1 && !cols[1].isBlank() ? cols[1].trim() : cols[0].trim());
            i.setSector(cols.length > 2 && !cols[2].isBlank() ? cols[2].trim() : null);
            i.setStatus(cols.length > 3 && !cols[3].isBlank()
                    ? InstrumentStatus.valueOf(cols[3].trim()) : InstrumentStatus.ACTIVE);
            i.setCreatedAt(now);
            instruments.add(i);
        });
        if (!instruments.isEmpty()) {
            instrumentRepository.saveAll(instruments);
            log.info("Seeded {} instruments", instruments.size());
        }
    }

    private void forEachRow(String resourcePath, Consumer<String[]> rowHandler) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.info("Seed file {} not present; skipping", resourcePath);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!headerSkipped) {
                    headerSkipped = true; // first non-comment line is the header
                    continue;
                }
                try {
                    rowHandler.accept(trimmed.split(","));
                } catch (RuntimeException badRow) {
                    log.warn("Skipping malformed seed row in {}: '{}'", resourcePath, trimmed);
                }
            }
        } catch (IOException e) {
            log.warn("Could not read {}: {}", resourcePath, e.getMessage());
        }
    }
}
