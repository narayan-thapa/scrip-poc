package np.com.thapanarayan.backend.reference.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A NEPSE numbered broker referenced by buyer/seller IDs in the floorsheet. */
@Entity
@Table(name = "broker")
public class Broker {

    @Id
    @Column(name = "broker_id")
    private Integer brokerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerStatus status = BrokerStatus.ACTIVE;

    protected Broker() {
    }

    public Broker(Integer brokerId, String name) {
        this.brokerId = brokerId;
        this.name = name;
    }

    public Integer getBrokerId() {
        return brokerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BrokerStatus getStatus() {
        return status;
    }

    public void setStatus(BrokerStatus status) {
        this.status = status;
    }
}
