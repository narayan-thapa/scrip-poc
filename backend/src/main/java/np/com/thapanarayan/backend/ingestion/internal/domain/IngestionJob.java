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
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.JobStatus;

/** Per-file ingestion record with row counts. The source filename's date is authoritative. */
@Entity
@Table(name = "ingestion_job")
public class IngestionJob {

    @Id
    private UUID id;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "file_hash", nullable = false)
    private String fileHash;

    @Column(name = "archive_key")
    private String archiveKey;

    @Column(name = "rows_read", nullable = false)
    private int rowsRead;

    @Column(name = "rows_accepted", nullable = false)
    private int rowsAccepted;

    @Column(name = "rows_rejected", nullable = false)
    private int rowsRejected;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected IngestionJob() {
    }

    public IngestionJob(UUID batchId, LocalDate tradeDate, String sourceFilename, String fileHash, String archiveKey) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.tradeDate = tradeDate;
        this.sourceFilename = sourceFilename;
        this.fileHash = fileHash;
        this.archiveKey = archiveKey;
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.startedAt = OffsetDateTime.now();
    }

    public void markCompleted(int read, int accepted, int rejected, int duplicate) {
        this.rowsRead = read;
        this.rowsAccepted = accepted;
        this.rowsRejected = rejected;
        this.rowsDuplicate = duplicate;
        this.status = JobStatus.COMPLETED;
        this.finishedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = JobStatus.FAILED;
        this.finishedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getArchiveKey() {
        return archiveKey;
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getRowsAccepted() {
        return rowsAccepted;
    }

    public int getRowsRejected() {
        return rowsRejected;
    }

    public int getRowsDuplicate() {
        return rowsDuplicate;
    }

    public JobStatus getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
