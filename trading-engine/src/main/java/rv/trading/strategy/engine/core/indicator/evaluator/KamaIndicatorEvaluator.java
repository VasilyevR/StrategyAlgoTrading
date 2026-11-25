package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.KAMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import rv.trading.strategy.contracts.enums.IndicatorCode;
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
public final class KamaIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.KAMA.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.ER_PERIOD, 10);
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.FAST_PERIOD, 2);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.SLOW_PERIOD, 30);
        IndicatorParams.require(IndicatorCode.KAMA, fastPeriod < slowPeriod,
                IndicatorParamKeys.FAST_PERIOD + " must be less than " + IndicatorParamKeys.SLOW_PERIOD);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int erPeriod = IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.ER_PERIOD, 10);
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.FAST_PERIOD, 2);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.KAMA, params, IndicatorParamKeys.SLOW_PERIOD, 30);
        if (!IndicatorSupport.hasBars(series, erPeriod + (role == IndicatorRole.TRIGGER ? 2 : 1))) {
            return IndicatorSupport.empty(Map.of());
        }
        Indicator<Num> close = new ClosePriceIndicator(series);
        KAMAIndicator kama = new KAMAIndicator(close, erPeriod, fastPeriod, slowPeriod);
        int index = series.getEndIndex();
        double closeNow = IndicatorSupport.value(close.getValue(index));
        double kamaNow = IndicatorSupport.value(kama.getValue(index));
        double closePrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(close.getValue(index - 1)) : closeNow;
        double kamaPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(kama.getValue(index - 1)) : kamaNow;
        if (!IndicatorSupport.finite(closeNow, kamaNow, closePrevious, kamaPrevious)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? closeNow > kamaNow
                : CrossUtils.crossedUp(closePrevious, closeNow, kamaPrevious, kamaNow);
        boolean down = role == IndicatorRole.FILTER ? closeNow < kamaNow
                : CrossUtils.crossedDown(closePrevious, closeNow, kamaPrevious, kamaNow);
        return IndicatorSupport.evaluation(up, down, Map.of(
                IndicatorSnapshotKeys.CLOSE, closeNow, IndicatorSnapshotKeys.KAMA, kamaNow));
    }
}
