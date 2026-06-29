package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import np.com.thapanarayan.backend.backtest.api.CostModelSpec;

/**
 * Request to run a backtest. {@code startingCapital} and {@code costModel} are
 * optional — they default to the configured portfolio capital and NEPSE cost model.
 * ISO {@code LocalDate}s ({@code yyyy-MM-dd}) are handled by the configured mapper.
 */
record BacktestRequest(
        @NotEmpty List<String> symbols,
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @Positive BigDecimal startingCapital,
        CostModelSpec costModel) {
}
