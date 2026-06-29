package np.com.thapanarayan.backend.reference.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.reference.api.BrokerStatus;

@Entity
@Table(name = "broker")
class BrokerEntity {

    @Id
    @Column(name = "broker_id", nullable = false)
    private Integer brokerId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private BrokerStatus status = BrokerStatus.ACTIVE;

    protected BrokerEntity() {
    }

    Integer getBrokerId() {
        return brokerId;
    }

    void setBrokerId(Integer brokerId) {
        this.brokerId = brokerId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    BrokerStatus getStatus() {
        return status;
    }

    void setStatus(BrokerStatus status) {
        this.status = status;
    }
}
