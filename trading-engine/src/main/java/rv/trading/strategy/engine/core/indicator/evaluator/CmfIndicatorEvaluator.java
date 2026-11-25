package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
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
public final class CmfIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.CMF.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.CMF, params, IndicatorParamKeys.PERIOD, 20);
        IndicatorParams.doubleValue(IndicatorCode.CMF, params, IndicatorParamKeys.BUY_THRESHOLD, 0.0);
        IndicatorParams.doubleValue(IndicatorCode.CMF, params, IndicatorParamKeys.SELL_THRESHOLD, 0.0);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.CMF, params, IndicatorParamKeys.CONDITION_TYPE,
                IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);
        IndicatorParams.require(IndicatorCode.CMF,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.THRESHOLD,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or THRESHOLD");
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int period = IndicatorParams.positiveInt(IndicatorCode.CMF, params, IndicatorParamKeys.PERIOD, 20);
        double buyThreshold = IndicatorParams.doubleValue(IndicatorCode.CMF, params, IndicatorParamKeys.BUY_THRESHOLD, 0.0);
        double sellThreshold = IndicatorParams.doubleValue(IndicatorCode.CMF, params, IndicatorParamKeys.SELL_THRESHOLD, 0.0);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.CMF, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.THRESHOLD);
        if (!IndicatorSupport.hasBars(series, period + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, period);
        int index = series.getEndIndex();
        double current = IndicatorSupport.value(cmf.getValue(index));
        double previous = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(cmf.getValue(index - 1)) : current;
        if (!IndicatorSupport.finite(current, previous)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? current > buyThreshold
                : CrossUtils.crossedUp(previous, current, buyThreshold, buyThreshold);
        boolean down = role == IndicatorRole.FILTER ? current < sellThreshold
                : CrossUtils.crossedDown(previous, current, sellThreshold, sellThreshold);
        if (conditionType == IndicatorConditionType.CROSS) {
            up = role == IndicatorRole.FILTER ? current > 0.0
                    : CrossUtils.crossedUp(previous, current, 0.0, 0.0);
            down = role == IndicatorRole.FILTER ? current < 0.0
                    : CrossUtils.crossedDown(previous, current, 0.0, 0.0);
        }
        return IndicatorSupport.evaluation(up, down, Map.of(IndicatorSnapshotKeys.CMF, current));
    }
}
