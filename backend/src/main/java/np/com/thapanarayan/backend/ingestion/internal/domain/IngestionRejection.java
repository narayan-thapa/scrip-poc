package np.com.thapanarayan.backend.ingestion.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A quarantined row: the raw line, a reason code, and human detail. */
@Entity
@Table(name = "ingestion_rejection")
public class IngestionRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "raw_line", columnDefinition = "text")
    private String rawLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private RejectionReason reasonCode;

    @Column(columnDefinition = "text")
    private String detail;

    protected IngestionRejection() {
    }

    public IngestionRejection(UUID jobId, String rawLine, RejectionReason reasonCode, String detail) {
        this.jobId = jobId;
        this.rawLine = rawLine;
        this.reasonCode = reasonCode;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getRawLine() {
        return rawLine;
    }

    public RejectionReason getReasonCode() {
        return reasonCode;
    }

    public String getDetail() {
        return detail;
    }
}
