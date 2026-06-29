package np.com.thapanarayan.backend.marketdata.internal;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite identity for {@link VolumeProfileEntity}: one profile per (symbol, date). */
class VolumeProfileId implements Serializable {

    private String symbol;
    private LocalDate tradeDate;

    VolumeProfileId() {
    }

    VolumeProfileId(String symbol, LocalDate tradeDate) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VolumeProfileId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol) && Objects.equals(tradeDate, that.tradeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, tradeDate);
    }
}
