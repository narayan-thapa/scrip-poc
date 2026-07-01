package np.com.thapanarayan.backend.ingestion.internal.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionRejection;

/** Response/request payloads for the ingestion admin API. */
final class IngestionDtos {

    private IngestionDtos() {
    }

    record BatchSummary(UUID id, String status, int fileCount, LocalDate dateFrom, LocalDate dateTo,
                        OffsetDateTime submittedAt, OffsetDateTime finishedAt) {
        static BatchSummary from(IngestionBatch b) {
            return new BatchSummary(b.getId(), b.getStatus().name(), b.getFileCount(), b.getDateFrom(),
                    b.getDateTo(), b.getSubmittedAt(), b.getFinishedAt());
        }
    }

    record JobSummary(UUID id, LocalDate tradeDate, String sourceFilename, String status,
                      int rowsRead, int rowsAccepted, int rowsRejected, int rowsDuplicate,
                      OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        static JobSummary from(IngestionJob j) {
            return new JobSummary(j.getId(), j.getTradeDate(), j.getSourceFilename(), j.getStatus().name(),
                    j.getRowsRead(), j.getRowsAccepted(), j.getRowsRejected(), j.getRowsDuplicate(),
                    j.getStartedAt(), j.getFinishedAt());
        }
    }

    record BatchDetail(BatchSummary batch, List<JobSummary> jobs) {}

    record RejectionDto(String rawLine, String reasonCode, String detail) {
        static RejectionDto from(IngestionRejection r) {
            return new RejectionDto(r.getRawLine(), r.getReasonCode().name(), r.getDetail());
        }
    }

    /** Accepts either a single {@code date} or a {@code from}/{@code to} range. */
    record DateRangeRequest(LocalDate date, LocalDate from, LocalDate to) {
        LocalDate effectiveFrom() {
            return from != null ? from : date;
        }

        LocalDate effectiveTo() {
            return to != null ? to : date;
        }
    }

    record WebhookRequest(LocalDate date) {}
}
