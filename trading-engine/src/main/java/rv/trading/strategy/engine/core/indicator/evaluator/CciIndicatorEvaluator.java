package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CCIIndicator;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorConditionType;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.engine.core.indicator.CrossUtils;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParams;
import rv.trading.strategy.engine.core.indicator.IndicatorSupport;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;

import java.util.Map;

@Component
public final class CciIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.CCI.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.CCI, params, IndicatorParamKeys.PERIOD, 20);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.CCI, params, IndicatorParamKeys.OVERSOLD, -100.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.CCI, params, IndicatorParamKeys.OVERBOUGHT, 100.0);
        IndicatorParams.require(IndicatorCode.CCI, oversold < overbought,
                IndicatorParamKeys.OVERSOLD + " must be less than " + IndicatorParamKeys.OVERBOUGHT);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.CCI, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);
        IndicatorParams.require(IndicatorCode.CCI,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.THRESHOLD,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or THRESHOLD");
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int period = IndicatorParams.positiveInt(IndicatorCode.CCI, params, IndicatorParamKeys.PERIOD, 20);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.CCI, params, IndicatorParamKeys.OVERSOLD, -100.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.CCI, params, IndicatorParamKeys.OVERBOUGHT, 100.0);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.CCI, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);

        if (!IndicatorSupport.hasBars(series, period + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        CCIIndicator cci = new CCIIndicator(series, period);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(cci.getValue(index));
        double previous = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(cci.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }

        boolean up;
        boolean down;
        if (role == IndicatorRole.FILTER) {
            up = current > 0.0;
            down = current < 0.0;
        } else if (conditionType == IndicatorConditionType.CROSS) {
            up = CrossUtils.crossedUp(previous, current, overbought, overbought);
            down = CrossUtils.crossedDown(previous, current, oversold, oversold);
        } else {
            up = CrossUtils.crossedUp(previous, current, oversold, oversold);
            down = CrossUtils.crossedDown(previous, current, overbought, overbought);
        }
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.CCI, current));
    }
}
