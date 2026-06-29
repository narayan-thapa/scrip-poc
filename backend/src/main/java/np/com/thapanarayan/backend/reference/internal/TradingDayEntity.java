package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trading_day")
class TradingDayEntity {

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "note", length = 128)
    private String note;

    protected TradingDayEntity() {
    }

    TradingDayEntity(LocalDate tradeDate, boolean open, String note) {
        this.tradeDate = tradeDate;
        this.open = open;
        this.note = note;
    }

    LocalDate getTradeDate() {
        return tradeDate;
    }

    boolean isOpen() {
        return open;
    }

    String getNote() {
        return note;
    }
}
