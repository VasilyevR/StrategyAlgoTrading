package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorConditionType;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.MacdLine;
import rv.trading.strategy.engine.core.indicator.CrossUtils;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParams;
import rv.trading.strategy.engine.core.indicator.IndicatorSupport;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;

import java.util.Map;

@Component
public final class MacdIndicatorEvaluator implements IndicatorEvaluator {

    @Override
    public String code() {
        return IndicatorCode.MACD.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.FAST_PERIOD, 12);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.SLOW_PERIOD, 26);
        IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.SIGNAL_PERIOD, 9);
        IndicatorParams.require(IndicatorCode.MACD, fastPeriod < slowPeriod,
                IndicatorParamKeys.FAST_PERIOD + " must be less than " + IndicatorParamKeys.SLOW_PERIOD);
        IndicatorConditionType condition = IndicatorParams.enumValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.CROSS);
        IndicatorParams.require(IndicatorCode.MACD,
                condition == IndicatorConditionType.CROSS || condition == IndicatorConditionType.THRESHOLD,
                IndicatorParamKeys.CONDITION_TYPE + " must be CROSS or THRESHOLD");
        IndicatorParams.enumValue(IndicatorCode.MACD, params, IndicatorParamKeys.LINE,
                MacdLine.class, MacdLine.SIGNAL);
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.LOW_THRESHOLD, 0.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 0.0);
        IndicatorParams.require(IndicatorCode.MACD, condition != IndicatorConditionType.THRESHOLD
                        || lowThreshold <= highThreshold,
                IndicatorParamKeys.LOW_THRESHOLD + " must not exceed " + IndicatorParamKeys.HIGH_THRESHOLD);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        int fastPeriod = IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.FAST_PERIOD, 12);
        int slowPeriod = IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.SLOW_PERIOD, 26);
        int signalPeriod = IndicatorParams.positiveInt(IndicatorCode.MACD, params, IndicatorParamKeys.SIGNAL_PERIOD, 9);
        IndicatorConditionType conditionType = IndicatorParams.enumValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.CONDITION_TYPE, IndicatorConditionType.class, IndicatorConditionType.CROSS);
        MacdLine line = IndicatorParams.enumValue(IndicatorCode.MACD, params, IndicatorParamKeys.LINE,
                MacdLine.class, MacdLine.SIGNAL);
        double lowThreshold = IndicatorParams.doubleValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.LOW_THRESHOLD, 0.0);
        double highThreshold = IndicatorParams.doubleValue(IndicatorCode.MACD, params,
                IndicatorParamKeys.HIGH_THRESHOLD, 0.0);
        if (!IndicatorSupport.hasBars(series, slowPeriod + signalPeriod + (role == IndicatorRole.TRIGGER ? 1 : 0))) {
            return IndicatorSupport.empty(Map.of());
        }
        Indicator<Num> close = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, fastPeriod, slowPeriod);
        Indicator<Num> signal = new EMAIndicator(macd, signalPeriod);
        Indicator<Num> histogram = macd.getHistogram(signalPeriod);
        Indicator<Num> selected = switch (line) {
            case MACD -> macd;
            case HISTOGRAM -> histogram;
            default -> signal;
        };
        int index = series.getEndIndex();
        double macdNow = IndicatorSupport.value(macd.getValue(index));
        double signalNow = IndicatorSupport.value(signal.getValue(index));
        double selectedNow = IndicatorSupport.value(selected.getValue(index));
        double histogramNow = IndicatorSupport.value(histogram.getValue(index));
        double macdPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(macd.getValue(index - 1)) : macdNow;
        double signalPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(signal.getValue(index - 1)) : signalNow;
        double selectedPrevious = role == IndicatorRole.TRIGGER
                ? IndicatorSupport.value(selected.getValue(index - 1)) : selectedNow;
        if (!IndicatorSupport.finite(macdNow, signalNow, selectedNow, histogramNow, macdPrevious, signalPrevious, selectedPrevious)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up;
        boolean down;
        if (conditionType == IndicatorConditionType.CROSS) {
            up = role == IndicatorRole.FILTER ? macdNow > signalNow
                    : CrossUtils.crossedUp(macdPrevious, macdNow, signalPrevious, signalNow);
            down = role == IndicatorRole.FILTER ? macdNow < signalNow
                    : CrossUtils.crossedDown(macdPrevious, macdNow, signalPrevious, signalNow);
        } else {
            up = role == IndicatorRole.FILTER ? selectedNow < lowThreshold
                    : CrossUtils.crossedUp(selectedPrevious, selectedNow, lowThreshold, lowThreshold);
            down = role == IndicatorRole.FILTER ? selectedNow > highThreshold
                    : CrossUtils.crossedDown(selectedPrevious, selectedNow, highThreshold, highThreshold);
        }
        return IndicatorSupport.evaluation(up, down, Map.of(
                IndicatorSnapshotKeys.MACD, macdNow,
                IndicatorSnapshotKeys.SIGNAL, signalNow,
                IndicatorSnapshotKeys.HISTOGRAM, histogramNow));
    }
}
