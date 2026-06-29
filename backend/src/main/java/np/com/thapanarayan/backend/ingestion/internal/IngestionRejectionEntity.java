package np.com.thapanarayan.backend.ingestion.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingestion_rejection")
class IngestionRejectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "raw_line")
    private String rawLine;

    @Column(name = "reason_code", length = 48, nullable = false)
    private String reasonCode;

    @Column(name = "detail")
    private String detail;

    protected IngestionRejectionEntity() {
    }

    IngestionRejectionEntity(Long jobId, String rawLine, String reasonCode, String detail) {
        this.jobId = jobId;
        this.rawLine = rawLine;
        this.reasonCode = reasonCode;
        this.detail = detail;
    }

    Long getId() {
        return id;
    }

    Long getJobId() {
        return jobId;
    }

    String getRawLine() {
        return rawLine;
    }

    String getReasonCode() {
        return reasonCode;
    }

    String getDetail() {
        return detail;
    }
}
