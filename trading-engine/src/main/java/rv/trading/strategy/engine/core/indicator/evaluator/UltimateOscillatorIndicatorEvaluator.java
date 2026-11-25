package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.UltimateOscillatorIndicator;
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
public final class UltimateOscillatorIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.ULTOSC.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        int period1 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_1, 7);
        int period2 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_2, 14);
        int period3 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_3, 28);
        IndicatorParams.require(IndicatorCode.ULTOSC, period1 < period2 && period2 < period3,
                "Ultimate oscillator periods " + IndicatorParamKeys.PERIOD_1 + ", " + IndicatorParamKeys.PERIOD_2
                        + " and " + IndicatorParamKeys.PERIOD_3 + " must be strictly increasing");
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.ULTOSC, params,
                IndicatorParamKeys.LOW_THRESHOLD, 30.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.ULTOSC, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 70.0);
        IndicatorParams.require(IndicatorCode.ULTOSC, lowThreshold <= highThreshold,
                IndicatorParamKeys.LOW_THRESHOLD + " must not exceed " + IndicatorParamKeys.HIGH_THRESHOLD);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.ULTOSC, params, IndicatorParamKeys.CONDITION_TYPE,
                IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);
        IndicatorParams.require(IndicatorCode.ULTOSC,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.THRESHOLD,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or THRESHOLD");
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int period1 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_1, 7);
        int period2 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_2, 14);
        int period3 = IndicatorParams.positiveInt(IndicatorCode.ULTOSC, params, IndicatorParamKeys.PERIOD_3, 28);
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.ULTOSC, params,
                IndicatorParamKeys.LOW_THRESHOLD, 30.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.ULTOSC, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 70.0);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.ULTOSC, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);
        int requiredBars = period3 + 2 + (role == IndicatorRole.TRIGGER ? 1 : 0);
        if (!IndicatorSupport.hasBars(series, requiredBars)) {
            return IndicatorSupport.empty(Map.of());
        }
        UltimateOscillatorIndicator indicator = new UltimateOscillatorIndicator(series, period1, period2, period3);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(indicator.getValue(index));
        double previous = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(indicator.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? current < lowThreshold
                : conditionType == IndicatorConditionType.CROSS ? CrossUtils.crossedUp(previous, current, lowThreshold, lowThreshold)
                        : current < lowThreshold;
        boolean down = role == IndicatorRole.FILTER ? current > highThreshold
                : conditionType == IndicatorConditionType.CROSS ? CrossUtils.crossedDown(previous, current, highThreshold, highThreshold)
                        : current > highThreshold;
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.ULTOSC, current));
    }
}