package rv.trading.strategy.engine.core.indicator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

@Component
public final class IndicatorRegistry {

    private final Map<String, IndicatorEvaluator> evaluators;

    public IndicatorRegistry(List<IndicatorEvaluator> evaluators) {
        this.evaluators = evaluators.stream().collect(Collectors.toUnmodifiableMap(
                IndicatorEvaluator::code, Function.identity()));
    }

    public IndicatorEvaluator getEvaluator(String code) {
        if (code == null) {
            throw new IndicatorConfigurationException(null, "Unknown indicator code: null");
        }
        IndicatorEvaluator evaluator = evaluators.get(code);
        if (evaluator == null) {
            throw new IndicatorConfigurationException(indicatorCode(code), "Unknown indicator code: " + code);
        }
        return evaluator;
    }

    public Optional<TrailingStopIndicator> getTrailingStopIndicator(String code) {
        return Optional.ofNullable(evaluators.get(code))
                .filter(TrailingStopIndicator.class::isInstance)
                .map(TrailingStopIndicator.class::cast);
    }

    private static IndicatorCode indicatorCode(String code) {
        try {
            return IndicatorCode.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
