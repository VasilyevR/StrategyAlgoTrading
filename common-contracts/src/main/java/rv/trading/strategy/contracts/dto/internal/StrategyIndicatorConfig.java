package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record StrategyIndicatorConfig(
        @NotNull IndicatorCode indicatorCode,
        @NotBlank String alias,
        @NotNull IndicatorRole role,
        @Positive int conditionGroup,
        @NotNull Map<String, Object> params
) {
}
