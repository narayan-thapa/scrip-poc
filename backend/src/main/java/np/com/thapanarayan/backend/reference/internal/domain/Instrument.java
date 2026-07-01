package np.com.thapanarayan.backend.reference.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A listed scrip (or the reserved {@code NEPSE} index). Symbol is the natural key. */
@Entity
@Table(name = "instrument")
public class Instrument {

    @Id
    private String symbol;

    @Column(nullable = false)
    private String name;

    private String sector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstrumentType type = InstrumentType.EQUITY;

    @Column(name = "listed_shares")
    private Long listedShares;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstrumentStatus status = InstrumentStatus.ACTIVE;

    @Column(name = "price_band")
    private BigDecimal priceBand;

    @Column(nullable = false)
    private boolean provisional;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Instrument() {
    }

    public Instrument(String symbol, String name, String sector, InstrumentType type) {
        this.symbol = symbol;
        this.name = name;
        this.sector = sector;
        this.type = type;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public InstrumentType getType() {
        return type;
    }

    public void setType(InstrumentType type) {
        this.type = type;
    }

    public Long getListedShares() {
        return listedShares;
    }

    public void setListedShares(Long listedShares) {
        this.listedShares = listedShares;
    }

    public InstrumentStatus getStatus() {
        return status;
    }

    public void setStatus(InstrumentStatus status) {
        this.status = status;
    }

    public BigDecimal getPriceBand() {
        return priceBand;
    }

    public void setPriceBand(BigDecimal priceBand) {
        this.priceBand = priceBand;
    }

    public boolean isProvisional() {
        return provisional;
    }

    public void setProvisional(boolean provisional) {
        this.provisional = provisional;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
