package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.engine.core.indicator.CrossUtils;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParams;
import rv.trading.strategy.engine.core.indicator.IndicatorSupport;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;
import rv.trading.strategy.engine.core.indicator.ta4j.StochasticRviIndicator;

import java.util.Map;

@Component
public final class StochRviIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.STOCH_RVI.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.STOCH_RVI, params, IndicatorParamKeys.LENGTH, 10);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.STOCH_RVI, params,
                IndicatorParamKeys.OVERSOLD, 20.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.STOCH_RVI, params,
                IndicatorParamKeys.OVERBOUGHT, 80.0);
        IndicatorParams.require(IndicatorCode.STOCH_RVI, oversold < overbought,
                IndicatorParamKeys.OVERSOLD + " must be less than " + IndicatorParamKeys.OVERBOUGHT);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int length = IndicatorParams.positiveInt(IndicatorCode.STOCH_RVI, params, IndicatorParamKeys.LENGTH, 10);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.STOCH_RVI, params,
                IndicatorParamKeys.OVERSOLD, 20.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.STOCH_RVI, params,
                IndicatorParamKeys.OVERBOUGHT, 80.0);
        int requiredBars = (length * 2) + 5 + (role == IndicatorRole.TRIGGER ? 1 : 0);
        if (!IndicatorSupport.hasBars(series, requiredBars)) {
            return IndicatorSupport.empty(Map.of());
        }
        StochasticRviIndicator indicator = new StochasticRviIndicator(series, length);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(indicator.getValue(index));
        double previous = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(indicator.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? current < oversold
                : CrossUtils.crossedUp(previous, current, oversold, oversold);
        boolean down = role == IndicatorRole.FILTER ? current > overbought
                : CrossUtils.crossedDown(previous, current, overbought, overbought);
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.STOCH_RVI, current));
    }
}
