package rv.trading.strategy.engine.core.indicator;

import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorEvaluationException;

import java.util.Map;

public interface IndicatorEvaluator {

    String code();

    void validateParams(Map<String, Object> params);

    IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params);

    default IndicatorEvaluation evaluate(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        validateParams(params);
        try {
            return evaluateIndicator(series, role, params);
        } catch (IndicatorConfigurationException | IndicatorEvaluationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IndicatorEvaluationException(IndicatorCode.valueOf(code()), exception);
        }
    }
}
