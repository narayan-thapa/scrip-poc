package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import np.com.thapanarayan.backend.TestcontainersConfiguration;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionJobRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * End-to-end ingest against a real Postgres: parse → validate → upsert → quarantine, with row counts,
 * and idempotent re-ingestion (re-running a date never duplicates rows). Tagged {@code integration}.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IngestionIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static final String CSV = String.join("\n",
            "BHCL, 41, 58, 2000, 600.8, 1201600, 2026-06-03 2:59:59:988 PM, 2026060301020260",
            "NABIL, 5, 9, 10, 1000, 10000, 2026-06-03 11:00:00:000 AM, 2026060301020261",
            "BADROW, 1, 2, -5, 10, 0, 2026-06-03 11:00:00:000 AM, 2026060301020262");

    @Autowired IngestionService ingestionService;
    @Autowired IngestionJobRepository jobs;
    @Autowired TradeUpsertDao dao;

    @Test
    void ingestsValidRowsQuarantinesBadOnesAndIsIdempotent() {
        IngestionJob job = ingestionService.createJob(null, DATE, "2026-06-03.csv", CSV.getBytes(StandardCharsets.UTF_8));

        ingestionService.processJob(job.getId(), true);
        IngestionJob done = jobs.findById(job.getId()).orElseThrow();
        assertThat(done.getRowsRead()).isEqualTo(3);
        assertThat(done.getRowsAccepted()).isEqualTo(2);
        assertThat(done.getRowsRejected()).isEqualTo(1);   // negative quantity quarantined
        assertThat(done.getRowsDuplicate()).isZero();
        assertThat(dao.countByTradeDate(DATE)).isEqualTo(2);

        // Re-ingesting the same archive must not duplicate rows.
        ingestionService.processJob(job.getId(), true);
        assertThat(dao.countByTradeDate(DATE)).isEqualTo(2);
        IngestionJob rerun = jobs.findById(job.getId()).orElseThrow();
        assertThat(rerun.getRowsDuplicate()).isEqualTo(2);
        assertThat(rerun.getRowsAccepted()).isZero();
    }
}
