package np.com.thapanarayan.backend.ingestion.internal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.api.TradesIngestedEvent;
import np.com.thapanarayan.backend.ingestion.internal.archive.RawArchive;
import np.com.thapanarayan.backend.ingestion.internal.domain.FloorsheetTrade;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionRejection;
import np.com.thapanarayan.backend.ingestion.internal.parse.FloorsheetParser;
import np.com.thapanarayan.backend.ingestion.internal.parse.LineOutcome;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionJobRepository;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionRejectionRepository;
import np.com.thapanarayan.backend.reference.api.InstrumentDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests one archived file into {@code floorsheet_trade}: stream-parse → validate → dedup → upsert
 * → quarantine rejects, updating the {@link IngestionJob} counts. Idempotent (contract_id upsert),
 * defensive (a bad row is quarantined, never failing the file), and bounded in memory (chunked).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int CHUNK = 5000;

    private final FloorsheetParser parser;
    private final TradeUpsertDao upsertDao;
    private final IngestionJobRepository jobs;
    private final IngestionRejectionRepository rejections;
    private final RawArchive archive;
    private final InstrumentDirectory instruments;
    private final ApplicationEventPublisher events;
    private final IngestionProperties props;

    IngestionService(FloorsheetParser parser, TradeUpsertDao upsertDao, IngestionJobRepository jobs,
                     IngestionRejectionRepository rejections, RawArchive archive, InstrumentDirectory instruments,
                     ApplicationEventPublisher events, IngestionProperties props) {
        this.parser = parser;
        this.upsertDao = upsertDao;
        this.jobs = jobs;
        this.rejections = rejections;
        this.archive = archive;
        this.instruments = instruments;
        this.events = events;
        this.props = props;
    }

    /** Archive raw content and create a QUEUED job. The filename is used only for the date + audit. */
    @Transactional
    public IngestionJob createJob(UUID batchId, LocalDate tradeDate, String sourceFilename, byte[] content) {
        String hash = sha256(content);
        String key = archive.store(tradeDate, hash, content);
        return jobs.save(new IngestionJob(batchId, tradeDate, sourceFilename, hash, key));
    }

    /** Create a QUEUED job that re-reads an already-archived file (reprocess from raw). */
    @Transactional
    public IngestionJob createJobFromArchive(UUID batchId, LocalDate tradeDate, String sourceFilename,
                                             String hash, String archiveKey) {
        return jobs.save(new IngestionJob(batchId, tradeDate, sourceFilename, hash, archiveKey));
    }

    /** Process a queued job: load archived bytes, ingest, update counts, and publish the event. */
    @Transactional
    public void processJob(UUID jobId, boolean suppressNotifications) {
        IngestionJob job = jobs.findById(jobId).orElseThrow();
        job.markRunning();
        jobs.save(job);
        try {
            Counts counts = ingest(job);
            job.markCompleted(counts.read, counts.accepted, counts.rejected, counts.duplicate);
            jobs.save(job);
            events.publishEvent(new TradesIngestedEvent(job.getTradeDate(), counts.accepted, suppressNotifications));
            log.info("Ingested {} for {}: read={} accepted={} rejected={} duplicate={}",
                    job.getSourceFilename(), job.getTradeDate(), counts.read, counts.accepted, counts.rejected,
                    counts.duplicate);
        } catch (RuntimeException e) {
            job.markFailed();
            jobs.save(job);
            log.error("Ingestion failed for job {} ({})", jobId, job.getTradeDate(), e);
            throw e;
        }
    }

    private Counts ingest(IngestionJob job) {
        Counts counts = new Counts();
        Set<String> symbols = new HashSet<>();
        List<FloorsheetTrade> buffer = new ArrayList<>(CHUNK);
        List<IngestionRejection> rejectBuffer = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(archive.open(job.getArchiveKey()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LineOutcome outcome = parser.parseLine(line, job.getTradeDate(), props.amountTolerance());
                switch (outcome) {
                    case LineOutcome.Ignored ignored -> { /* blank/header: not counted */ }
                    case LineOutcome.Rejected r -> {
                        counts.read++;
                        counts.rejected++;
                        rejectBuffer.add(new IngestionRejection(job.getId(), r.rawLine(), r.reason(), r.detail()));
                        if (rejectBuffer.size() >= CHUNK) {
                            rejections.saveAll(rejectBuffer);
                            rejectBuffer.clear();
                        }
                    }
                    case LineOutcome.Accepted a -> {
                        counts.read++;
                        symbols.add(a.trade().symbol());
                        buffer.add(withSource(a.trade(), job.getId()));
                        if (buffer.size() >= CHUNK) {
                            flush(job.getTradeDate(), buffer, counts);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed reading archived file " + job.getArchiveKey(), e);
        }
        flush(job.getTradeDate(), buffer, counts);
        if (!rejectBuffer.isEmpty()) {
            rejections.saveAll(rejectBuffer);
        }
        symbols.forEach(instruments::ensureProvisional);
        return counts;
    }

    private void flush(LocalDate date, List<FloorsheetTrade> buffer, Counts counts) {
        if (buffer.isEmpty()) {
            return;
        }
        List<String> ids = buffer.stream().map(FloorsheetTrade::contractId).toList();
        int duplicates = upsertDao.existingContractIds(date, ids).size();
        upsertDao.upsertAll(buffer);
        counts.duplicate += duplicates;
        counts.accepted += buffer.size() - duplicates;
        buffer.clear();
    }

    private static FloorsheetTrade withSource(FloorsheetTrade t, UUID sourceFileId) {
        return new FloorsheetTrade(t.contractId(), t.symbol(), t.buyerBroker(), t.sellerBroker(), t.quantity(),
                t.price(), t.amount(), t.tradeTime(), t.tradeDate(), sourceFileId);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static final class Counts {
        int read;
        int accepted;
        int rejected;
        int duplicate;
    }
}
