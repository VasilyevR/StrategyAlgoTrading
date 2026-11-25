package rv.trading.strategy.engine.core.signal;

import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public final class StrategyConfigValidator {

    private final IndicatorRegistry indicatorRegistry;

    public StrategyConfigValidator(IndicatorRegistry indicatorRegistry) {
        this.indicatorRegistry = indicatorRegistry;
    }

    public void validate(AlgotradingRuntimeConfig config) {
        if (config == null) {
            throw invalidConfig("Runtime config must not be null");
        }
        if (isNonPositive(config.algotradingId())) {
            throw invalidConfig("algotradingId must be positive");
        }
        if (isNonPositive(config.brokerClientId())) {
            throw invalidConfig("brokerClientId must be positive");
        }
        if (isNonPositive(config.instrumentPairId())) {
            throw invalidConfig("instrumentPairId must be positive");
        }
        if (config.symbol() == null || config.symbol().isBlank()) {
            throw invalidConfig("symbol must not be blank");
        }
        if (config.timeframeCode() == null) {
            throw invalidConfig("timeframeCode must not be null");
        }
        if (config.directionMode() == null) {
            throw invalidConfig("directionMode must not be null");
        }
        if (config.exitMode() == null) {
            throw invalidConfig("exitMode must not be null");
        }
        if (config.sizingMode() == null) {
            throw invalidConfig("sizingMode must not be null");
        }
        requirePositive(config.orderVolume(), "orderVolume");
        requirePositive(config.tickSize(), "tickSize");
        requirePositive(config.stepSize(), "stepSize");

        validateIndicators(config.indicators());
        if (requiresTrailingStop(config.exitMode()) && !hasTrailingStopIndicator(config.indicators())) {
            throw invalidConfig("TRAILING_STOP or HYBRID exit mode requires a trailing-stop indicator");
        }
    }

    public void validateIndicators(List<StrategyIndicatorConfig> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            throw invalidConfig("At least one strategy indicator must be configured");
        }

        Set<String> aliases = new HashSet<>();
        boolean hasDirectionalIndicator = false;
        for (StrategyIndicatorConfig indicator : indicators) {
            if (indicator == null) {
                throw invalidConfig("Strategy indicator must not be null");
            }

            IndicatorCode code = indicator.indicatorCode();
            if (code == null) {
                throw invalidConfig("Indicator code must not be null");
            }
            String alias = indicator.alias();
            if (alias == null || alias.isBlank()) {
                throw new IndicatorConfigurationException(code, "Indicator alias must not be blank");
            }
            if (indicator.role() == null) {
                throw new IndicatorConfigurationException(code, "Indicator role must not be null");
            }
            if (isInvalidConditionGroup(indicator.conditionGroup())) {
                throw new IndicatorConfigurationException(code, "Indicator conditionGroup must be at least 1");
            }
            if (indicator.params() == null) {
                throw new IndicatorConfigurationException(code, "Indicator params must not be null");
            }
            if (!aliases.add(alias)) {
                throw new IndicatorConfigurationException(code, "Indicator aliases must be unique: " + alias);
            }

            IndicatorEvaluator evaluator = indicatorRegistry.getEvaluator(code.name());
            evaluator.validateParams(indicator.params());
            if (isDirectional(indicator.role())) {
                hasDirectionalIndicator = true;
            }
        }

        if (!hasDirectionalIndicator) {
            throw invalidConfig("At least one TRIGGER or FILTER indicator must be configured");
        }
    }

    private boolean hasTrailingStopIndicator(List<StrategyIndicatorConfig> indicators) {
        return indicators.stream()
                .filter(indicator -> indicator.role() == IndicatorRole.TRAILING_STOP)
                .anyMatch(indicator -> indicatorRegistry.getTrailingStopIndicator(indicator.indicatorCode().name())
                        .isPresent());
    }

    private static boolean requiresTrailingStop(ExitMode exitMode) {
        return exitMode == ExitMode.TRAILING_STOP || exitMode == ExitMode.HYBRID;
    }

    private static boolean isDirectional(IndicatorRole role) {
        return role == IndicatorRole.TRIGGER || role == IndicatorRole.FILTER;
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw invalidConfig(fieldName + " must be positive");
        }
    }

    private static boolean isNonPositive(long value) {
        return value <= 0;
    }

    private static boolean isInvalidConditionGroup(int conditionGroup) {
        return conditionGroup < 1;
    }

    private static IndicatorConfigurationException invalidConfig(String message) {
        return new IndicatorConfigurationException(null, message);
    }
}
