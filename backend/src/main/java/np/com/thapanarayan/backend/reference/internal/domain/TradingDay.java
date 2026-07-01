package np.com.thapanarayan.backend.reference.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One row per calendar date marking whether NEPSE traded. Drives all trading-day look-backs. */
@Entity
@Table(name = "trading_day")
public class TradingDay {

    @Id
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    private String note;

    protected TradingDay() {
    }

    public TradingDay(LocalDate tradeDate, boolean open, String note) {
        this.tradeDate = tradeDate;
        this.open = open;
        this.note = note;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public boolean isOpen() {
        return open;
    }

    public String getNote() {
        return note;
    }
}
