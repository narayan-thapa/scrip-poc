package np.com.thapanarayan.backend.indicator.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One (date, value) point of a computed indicator series. */
public record IndicatorPoint(LocalDate date, BigDecimal value) {
}
