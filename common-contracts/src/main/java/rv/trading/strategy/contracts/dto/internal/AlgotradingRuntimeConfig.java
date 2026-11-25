package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record AlgotradingRuntimeConfig(
        @Positive
        long algotradingId,
        @Positive
        long brokerClientId,
        @Positive
        long instrumentPairId,
        @NotBlank String symbol,
        @NotNull TimeframeCode timeframeCode,
        @NotNull DirectionMode directionMode,
        @NotNull ExitMode exitMode,
        @NotNull SizingMode sizingMode,
        @NotNull @Positive BigDecimal orderVolume,
        @NotNull @Positive BigDecimal tickSize,
        @NotNull @Positive BigDecimal stepSize,
        @NotNull @Valid List<StrategyIndicatorConfig> indicators
) {
}
