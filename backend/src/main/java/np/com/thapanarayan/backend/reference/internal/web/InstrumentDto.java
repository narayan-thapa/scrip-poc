package np.com.thapanarayan.backend.reference.internal.web;

import java.math.BigDecimal;
import np.com.thapanarayan.backend.reference.internal.domain.Instrument;

public record InstrumentDto(
        String symbol,
        String name,
        String sector,
        String type,
        Long listedShares,
        String status,
        BigDecimal priceBand,
        boolean provisional) {

    static InstrumentDto from(Instrument i) {
        return new InstrumentDto(
                i.getSymbol(),
                i.getName(),
                i.getSector(),
                i.getType().name(),
                i.getListedShares(),
                i.getStatus().name(),
                i.getPriceBand(),
                i.isProvisional());
    }
}
