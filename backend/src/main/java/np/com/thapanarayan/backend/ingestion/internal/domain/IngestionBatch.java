package np.com.thapanarayan.backend.ingestion.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.BatchStatus;

/** A multi-file upload / backfill submission grouping its per-file {@link IngestionJob}s. */
@Entity
@Table(name = "ingestion_batch")
public class IngestionBatch {

    @Id
    private UUID id;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.QUEUED;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected IngestionBatch() {
    }

    public IngestionBatch(int fileCount, LocalDate dateFrom, LocalDate dateTo, String submittedBy) {
        this.id = UUID.randomUUID();
        this.fileCount = fileCount;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.submittedBy = submittedBy;
    }

    public UUID getId() {
        return id;
    }

    public int getFileCount() {
        return fileCount;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
