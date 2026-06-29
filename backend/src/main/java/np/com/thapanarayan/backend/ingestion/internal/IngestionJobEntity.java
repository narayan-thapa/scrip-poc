package np.com.thapanarayan.backend.ingestion.internal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.ingestion.api.IngestionStatus;

@Entity
@Table(name = "ingestion_job")
class IngestionJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "source_filename", length = 255, nullable = false)
    private String sourceFilename;

    @Column(name = "file_hash", length = 64, nullable = false)
    private String fileHash;

    @Column(name = "rows_read", nullable = false)
    private int rowsRead;

    @Column(name = "rows_accepted", nullable = false)
    private int rowsAccepted;

    @Column(name = "rows_rejected", nullable = false)
    private int rowsRejected;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private IngestionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestionJobEntity() {
    }

    Long getId() {
        return id;
    }

    Long getBatchId() {
        return batchId;
    }

    void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    LocalDate getTradeDate() {
        return tradeDate;
    }

    void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    String getSourceFilename() {
        return sourceFilename;
    }

    void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    String getFileHash() {
        return fileHash;
    }

    void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    int getRowsRead() {
        return rowsRead;
    }

    void setRowsRead(int rowsRead) {
        this.rowsRead = rowsRead;
    }

    int getRowsAccepted() {
        return rowsAccepted;
    }

    void setRowsAccepted(int rowsAccepted) {
        this.rowsAccepted = rowsAccepted;
    }

    int getRowsRejected() {
        return rowsRejected;
    }

    void setRowsRejected(int rowsRejected) {
        this.rowsRejected = rowsRejected;
    }

    int getRowsDuplicate() {
        return rowsDuplicate;
    }

    void setRowsDuplicate(int rowsDuplicate) {
        this.rowsDuplicate = rowsDuplicate;
    }

    IngestionStatus getStatus() {
        return status;
    }

    void setStatus(IngestionStatus status) {
        this.status = status;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    Instant getFinishedAt() {
        return finishedAt;
    }

    void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
