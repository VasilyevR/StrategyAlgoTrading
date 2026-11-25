package rv.trading.strategy.engine.core.indicator.evaluator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.engine.core.indicator.CrossUtils;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParams;
import rv.trading.strategy.engine.core.indicator.IndicatorSupport;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;

import java.util.Map;

@Component
public final class SmaCrossIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.SMA_CROSS.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.SMA_CROSS, params, IndicatorParamKeys.FAST_PERIOD, 9);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.SMA_CROSS, params, IndicatorParamKeys.SLOW_PERIOD, 21);
        IndicatorParams.require(IndicatorCode.SMA_CROSS, fastPeriod < slowPeriod,
                IndicatorParamKeys.FAST_PERIOD + " must be less than " + IndicatorParamKeys.SLOW_PERIOD);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.SMA_CROSS, params, IndicatorParamKeys.FAST_PERIOD, 9);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.SMA_CROSS, params, IndicatorParamKeys.SLOW_PERIOD, 21);
        if (!IndicatorSupport.hasBars(series, slowPeriod + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        Indicator<org.ta4j.core.num.Num> close = new ClosePriceIndicator(series);
        Indicator<org.ta4j.core.num.Num> fast = new SMAIndicator(close, fastPeriod);
        Indicator<org.ta4j.core.num.Num> slow = new SMAIndicator(close, slowPeriod);
        int index = series.getEndIndex();
        double fastNow = IndicatorSupport.value(fast.getValue(index));
        double slowNow = IndicatorSupport.value(slow.getValue(index));
        double fastPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(fast.getValue(index - 1)) : fastNow;
        double slowPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(slow.getValue(index - 1)) : slowNow;
        if (!IndicatorSupport.finite(fastNow, slowNow, fastPrevious, slowPrevious)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? fastNow > slowNow
                : CrossUtils.crossedUp(fastPrevious, fastNow, slowPrevious, slowNow);
        boolean down = role == IndicatorRole.FILTER ? fastNow < slowNow
                : CrossUtils.crossedDown(fastPrevious, fastNow, slowPrevious, slowNow);
        return IndicatorSupport.evaluation(up, down,
                Map.of(IndicatorSnapshotKeys.FAST, fastNow, IndicatorSnapshotKeys.SLOW, slowNow));
    }
}
