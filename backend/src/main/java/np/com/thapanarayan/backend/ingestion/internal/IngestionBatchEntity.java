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
@Table(name = "ingestion_batch")
class IngestionBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private IngestionStatus status;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestionBatchEntity() {
    }

    Long getId() {
        return id;
    }

    int getFileCount() {
        return fileCount;
    }

    void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    LocalDate getDateFrom() {
        return dateFrom;
    }

    void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    LocalDate getDateTo() {
        return dateTo;
    }

    void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    IngestionStatus getStatus() {
        return status;
    }

    void setStatus(IngestionStatus status) {
        this.status = status;
    }

    String getSubmittedBy() {
        return submittedBy;
    }

    void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    Instant getFinishedAt() {
        return finishedAt;
    }

    void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
