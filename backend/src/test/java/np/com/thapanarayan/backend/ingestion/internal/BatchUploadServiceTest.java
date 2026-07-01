package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.internal.BatchUploadService.UploadedFile;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchUploadServiceTest {

    @Mock IngestionService ingestionService;
    @Mock IngestionPipeline pipeline;
    @Mock IngestionBatchRepository batches;

    // Small caps to exercise the guardrails: max 3 files, 1 KB/file, 5 KB/batch.
    private final IngestionProperties props =
            new IngestionProperties(new BigDecimal("0.5"), 3, 1000L, 5000L, null);

    private BatchUploadService service;

    @BeforeEach
    void setUp() {
        service = new BatchUploadService(ingestionService, pipeline, batches, props);
    }

    private static UploadedFile file(String name, int bytes) {
        return new UploadedFile(name, new byte[bytes]);
    }

    private static UploadedFile csv(String name) {
        return new UploadedFile(name, "x".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMalformedFilename() {
        assertThatThrownBy(() -> service.submit(List.of(csv("June3.csv")), "admin"))
                .isInstanceOf(ApiException.class);
        verify(ingestionService, never()).createJob(any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateDatesWithinBatch() {
        assertThatThrownBy(() -> service.submit(
                List.of(csv("2026-06-03.csv"), csv("2026-06-03.csv")), "admin"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsTooManyFiles() {
        assertThatThrownBy(() -> service.submit(
                List.of(csv("2026-06-01.csv"), csv("2026-06-02.csv"), csv("2026-06-03.csv"), csv("2026-06-04.csv")),
                "admin")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsOversizedFile() {
        assertThatThrownBy(() -> service.submit(List.of(file("2026-06-03.csv", 2000)), "admin"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptsAndProcessesOldestFirst() {
        when(batches.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.submit(List.of(csv("2026-06-05.csv"), csv("2026-06-03.csv")), "admin");

        assertThat(result.files()).hasSize(2);
        // Intake is returned in ascending date order.
        assertThat(result.files().get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(result.files().get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        verify(ingestionService, times(2)).createJob(any(), any(), any(), any());
        verify(ingestionService).createJob(any(), eq(LocalDate.of(2026, 6, 3)), eq("2026-06-03.csv"), any());
        verify(pipeline).runBatchAsync(any());
    }
}
