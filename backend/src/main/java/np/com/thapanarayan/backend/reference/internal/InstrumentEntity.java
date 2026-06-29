package np.com.thapanarayan.backend.reference.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.reference.api.InstrumentStatus;

@Entity
@Table(name = "instrument")
class InstrumentEntity {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "sector", length = 64)
    private String sector;

    @Column(name = "listed_shares")
    private Long listedShares;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private InstrumentStatus status = InstrumentStatus.ACTIVE;

    @Column(name = "price_band", length = 32)
    private String priceBand;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InstrumentEntity() {
    }

    String getSymbol() {
        return symbol;
    }

    void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getSector() {
        return sector;
    }

    void setSector(String sector) {
        this.sector = sector;
    }

    Long getListedShares() {
        return listedShares;
    }

    void setListedShares(Long listedShares) {
        this.listedShares = listedShares;
    }

    InstrumentStatus getStatus() {
        return status;
    }

    void setStatus(InstrumentStatus status) {
        this.status = status;
    }

    String getPriceBand() {
        return priceBand;
    }

    void setPriceBand(String priceBand) {
        this.priceBand = priceBand;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
