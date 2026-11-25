package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
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
public final class StochasticIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.STOCH.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        IndicatorParams.positiveInt(IndicatorCode.STOCH, params, IndicatorParamKeys.K_PERIOD, 14);
        IndicatorParams.positiveInt(IndicatorCode.STOCH, params, IndicatorParamKeys.D_PERIOD, 3);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.STOCH, params, IndicatorParamKeys.OVERSOLD, 20.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.STOCH, params, IndicatorParamKeys.OVERBOUGHT, 80.0);
        IndicatorParams.require(IndicatorCode.STOCH, oversold < overbought,
                IndicatorParamKeys.OVERSOLD + " must be less than " + IndicatorParamKeys.OVERBOUGHT);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int kPeriod = IndicatorParams.positiveInt(IndicatorCode.STOCH, params, IndicatorParamKeys.K_PERIOD, 14);
        int dPeriod = IndicatorParams.positiveInt(IndicatorCode.STOCH, params, IndicatorParamKeys.D_PERIOD, 3);
        double oversold = IndicatorParams.doubleValue(IndicatorCode.STOCH, params, IndicatorParamKeys.OVERSOLD, 20.0);
        double overbought = IndicatorParams.doubleValue(IndicatorCode.STOCH, params, IndicatorParamKeys.OVERBOUGHT, 80.0);
        int requiredBars = kPeriod + dPeriod - 1 + (role == IndicatorRole.TRIGGER ? 1 : 0);
        if (!IndicatorSupport.hasBars(series, requiredBars)) {
            return IndicatorSupport.empty(Map.of());
        }
        StochasticOscillatorKIndicator k = new StochasticOscillatorKIndicator(series, kPeriod);
        Indicator<Num> d = new SMAIndicator(k, dPeriod);
        int index = series.getEndIndex();
        double kNow = IndicatorSupport.value(k.getValue(index));
        double dNow = IndicatorSupport.value(d.getValue(index));
        double kPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(k.getValue(index - 1)) : kNow;
        double dPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(d.getValue(index - 1)) : dNow;
        if (!IndicatorSupport.finite(kNow, dNow, kPrevious, dPrevious)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = role == IndicatorRole.FILTER ? kNow > dNow
                : kNow <= oversold && CrossUtils.crossedUp(kPrevious, kNow, dPrevious, dNow);
        boolean down = role == IndicatorRole.FILTER ? kNow < dNow
                : kNow >= overbought && CrossUtils.crossedDown(kPrevious, kNow, dPrevious, dNow);
        return IndicatorSupport.evaluation(up, down, Map.of(
                IndicatorSnapshotKeys.K, kNow, IndicatorSnapshotKeys.D, dNow));
    }
}
