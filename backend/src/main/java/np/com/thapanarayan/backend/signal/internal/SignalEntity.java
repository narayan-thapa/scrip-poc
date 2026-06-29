package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.signal.api.SignalAction;

/**
 * The persisted daily signal for a (symbol, trade_date). The surrogate {@code id} is
 * the PK; {@code (symbol, trade_date)} is unique, so regeneration reuses the existing
 * row's id and replaces its contents idempotently. The full weighted vote vector
 * (with reasons) is the {@code votes} JSONB array.
 */
@Entity
@Table(name = "signal")
class SignalEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 8, nullable = false)
    private SignalAction action;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "bar_count", nullable = false)
    private int barCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "votes", nullable = false, columnDefinition = "jsonb")
    private List<StoredVote> votes;

    @Column(name = "narrative", nullable = false)
    private String narrative;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected SignalEntity() {
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getSymbol() {
        return symbol;
    }

    void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    LocalDate getTradeDate() {
        return tradeDate;
    }

    void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    SignalAction getAction() {
        return action;
    }

    void setAction(SignalAction action) {
        this.action = action;
    }

    BigDecimal getScore() {
        return score;
    }

    void setScore(BigDecimal score) {
        this.score = score;
    }

    int getBarCount() {
        return barCount;
    }

    void setBarCount(int barCount) {
        this.barCount = barCount;
    }

    List<StoredVote> getVotes() {
        return votes;
    }

    void setVotes(List<StoredVote> votes) {
        this.votes = votes;
    }

    String getNarrative() {
        return narrative;
    }

    void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    Instant getComputedAt() {
        return computedAt;
    }

    void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
