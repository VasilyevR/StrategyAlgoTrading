package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AwesomeOscillatorIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
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
public final class AoIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.AO.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.AO, params, IndicatorParamKeys.FAST_PERIOD, 5);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.AO, params, IndicatorParamKeys.SLOW_PERIOD, 34);
        IndicatorParams.require(IndicatorCode.AO, fastPeriod < slowPeriod,
                IndicatorParamKeys.FAST_PERIOD + " must be less than " + IndicatorParamKeys.SLOW_PERIOD);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.AO, params, IndicatorParamKeys.CONDITION_TYPE,
                IndicatorConditionType.class, IndicatorConditionType.CROSS);
        IndicatorParams.require(IndicatorCode.AO,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.THRESHOLD,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or THRESHOLD");
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.AO, params,
                IndicatorParamKeys.LOW_THRESHOLD, 0.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.AO, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 0.0);
        IndicatorParams.require(IndicatorCode.AO, lowThreshold <= highThreshold,
                IndicatorParamKeys.LOW_THRESHOLD + " must not exceed " + IndicatorParamKeys.HIGH_THRESHOLD);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.AO, params, IndicatorParamKeys.FAST_PERIOD, 5);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.AO, params, IndicatorParamKeys.SLOW_PERIOD, 34);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.AO, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.CROSS);
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.AO, params,
                IndicatorParamKeys.LOW_THRESHOLD, 0.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.AO, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 0.0);
        if (!IndicatorSupport.hasBars(series, slowPeriod + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        AwesomeOscillatorIndicator ao = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), fastPeriod,
                slowPeriod);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(ao.getValue(index));
        double previous = role == IndicatorRole.TRIGGER ? IndicatorSupport.value(ao.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? current > 0.0
                : conditionType == IndicatorConditionType.CROSS ? CrossUtils.crossedUp(previous, current, 0.0, 0.0)
                        : CrossUtils.crossedUp(previous, current, lowThreshold, lowThreshold);
        boolean down = role == IndicatorRole.FILTER ? current < 0.0
                : conditionType == IndicatorConditionType.CROSS ? CrossUtils.crossedDown(previous, current, 0.0, 0.0)
                        : CrossUtils.crossedDown(previous, current, highThreshold, highThreshold);
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.AO, current));
    }
}
