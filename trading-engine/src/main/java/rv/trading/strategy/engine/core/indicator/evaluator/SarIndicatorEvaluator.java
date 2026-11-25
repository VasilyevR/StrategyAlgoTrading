package rv.trading.strategy.engine.core.indicator.evaluator;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.num.Num;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SarMode;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParams;
import rv.trading.strategy.engine.core.indicator.IndicatorSupport;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public final class SarIndicatorEvaluator implements IndicatorEvaluator, TrailingStopIndicator {

    @Override
    public String code() {
        return IndicatorCode.SAR.name();
    }

    @Override
    public void validateParams(Map<String, Object> params) {
        positive(IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_START, 0.02), IndicatorParamKeys.ACCELERATION_START);
        positive(IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_INCREMENT, 0.02), IndicatorParamKeys.ACCELERATION_INCREMENT);
        double maximum = positive(IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.MAX_ACCELERATION, 0.2), IndicatorParamKeys.MAX_ACCELERATION);
        double start = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_START, 0.02);
        IndicatorParams.require(IndicatorCode.SAR, start <= maximum,
                "accelerationStart must not exceed maxAcceleration");
        IndicatorParams.enumValue(IndicatorCode.SAR, params, IndicatorParamKeys.MODE, SarMode.class, SarMode.TREND);
    }

    @Override
    public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role, Map<String, Object> params) {
        if (role == IndicatorRole.TRAILING_STOP) {
            return IndicatorSupport.empty(Map.of());
        }
        if (!IndicatorSupport.hasBars(series, 2)) {
            return IndicatorSupport.empty(Map.of());
        }
        double accelerationStart = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_START, 0.02);
        double accelerationIncrement = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_INCREMENT, 0.02);
        double maxAcceleration = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.MAX_ACCELERATION, 0.2);
        SarMode mode = IndicatorParams.enumValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.MODE, SarMode.class, SarMode.TREND);
        ParabolicSarIndicator sar = parabolicSar(series, accelerationStart, accelerationIncrement, maxAcceleration);
        int index = series.getEndIndex();
        double close = IndicatorSupport.value(series.getBar(index).getClosePrice());
        double currentSar = IndicatorSupport.value(sar.getValue(index));
        double high = IndicatorSupport.value(series.getBar(index).getHighPrice());
        double low = IndicatorSupport.value(series.getBar(index).getLowPrice());
        if (!IndicatorSupport.finite(close, currentSar, high, low)) {
            return IndicatorSupport.empty(Map.of());
        }
        boolean up = mode == SarMode.TREND ? close > currentSar : currentSar > high;
        boolean down = mode == SarMode.TREND ? close < currentSar : currentSar < low;
        return IndicatorSupport.evaluation(up, down, Map.of(
                IndicatorSnapshotKeys.SAR, currentSar, IndicatorSnapshotKeys.CLOSE, close));
    }

    @Override
    public Optional<BigDecimal> calculateStopLoss(BarSeries series, PositionSide side, Map<String, Object> params) {
        if (side == null) {
            throw new rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException(
                    IndicatorCode.SAR, "side must not be null");
        }
        validateParams(params);
        if (!IndicatorSupport.hasBars(series, 2)) {
            return Optional.empty();
        }
        double accelerationStart = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_START, 0.02);
        double accelerationIncrement = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.ACCELERATION_INCREMENT, 0.02);
        double maxAcceleration = IndicatorParams.doubleValue(IndicatorCode.SAR, params,
                IndicatorParamKeys.MAX_ACCELERATION, 0.2);
        Num stop = parabolicSar(series, accelerationStart, accelerationIncrement, maxAcceleration)
                .getValue(series.getEndIndex());
        if (!Num.isFinite(stop)) {
            return Optional.empty();
        }
        return Optional.of(stop.bigDecimalValue());
    }

    private static ParabolicSarIndicator parabolicSar(BarSeries series, double start, double increment, double maximum) {
        return new ParabolicSarIndicator(series, series.numFactory().numOf(start), series.numFactory().numOf(maximum),
                series.numFactory().numOf(increment));
    }

    private static double positive(double value, String key) {
        if (value <= 0.0) {
            throw new rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException(
                    IndicatorCode.SAR, "Parameter '" + key + "' must be positive");
        }
        return value;
    }
}
