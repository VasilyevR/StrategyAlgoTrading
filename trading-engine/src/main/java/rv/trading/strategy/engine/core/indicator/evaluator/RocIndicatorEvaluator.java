package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
public final class RocIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.ROC.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.ROC, params, IndicatorParamKeys.PERIOD, 12);
        double minThreshold = IndicatorParams.doubleValue(IndicatorCode.ROC, params,
                IndicatorParamKeys.MIN_THRESHOLD, -1000.0);
        double maxThreshold = IndicatorParams.doubleValue(IndicatorCode.ROC, params,
                IndicatorParamKeys.MAX_THRESHOLD, 1000.0);
        IndicatorParams.require(IndicatorCode.ROC, minThreshold <= maxThreshold,
                IndicatorParamKeys.MIN_THRESHOLD + " must not exceed " + IndicatorParamKeys.MAX_THRESHOLD);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.ROC, params, IndicatorParamKeys.CONDITION_TYPE,
                IndicatorConditionType.class, IndicatorConditionType.RANGE);
        IndicatorParams.require(IndicatorCode.ROC,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.RANGE,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or RANGE");
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int period = IndicatorParams.positiveInt(IndicatorCode.ROC, params, IndicatorParamKeys.PERIOD, 12);
        double minThreshold = IndicatorParams.doubleValue(IndicatorCode.ROC, params,
                IndicatorParamKeys.MIN_THRESHOLD, -1000.0);
        double maxThreshold = IndicatorParams.doubleValue(IndicatorCode.ROC, params,
                IndicatorParamKeys.MAX_THRESHOLD, 1000.0);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.ROC, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.RANGE);
        if (!IndicatorSupport.hasBars(series, period + 1 + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        ROCIndicator roc = new ROCIndicator(new ClosePriceIndicator(series), period);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(roc.getValue(index));
        double previous = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(roc.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up;
        boolean down;
        if (conditionType == IndicatorConditionType.CROSS) {
            up = role == IndicatorRole.FILTER ? current > 0.0 : CrossUtils.crossedUp(previous, current, 0.0, 0.0);
            down = role == IndicatorRole.FILTER ? current < 0.0 : CrossUtils.crossedDown(previous, current, 0.0, 0.0);
        } else if (role == IndicatorRole.FILTER) {
            up = current >= minThreshold;
            down = current < maxThreshold;
        } else {
            up = CrossUtils.crossedUp(previous, current, minThreshold, minThreshold);
            down = CrossUtils.crossedDown(previous, current, maxThreshold, maxThreshold);
        }
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.ROC, current));
    }
}
