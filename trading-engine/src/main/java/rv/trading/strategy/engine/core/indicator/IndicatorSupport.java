package rv.trading.strategy.engine.core.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.util.BitSet;
import java.util.Map;

public final class IndicatorSupport {

    private IndicatorSupport() {
    }

    public static boolean hasBars(BarSeries series, int requiredBars) {
        return series.getEndIndex() >= series.getBeginIndex()
                && series.getEndIndex() - series.getBeginIndex() + 1 >= requiredBars;
    }

    public static double value(Num value) {
        if (!Num.isFinite(value)) {
            return Double.NaN;
        }
        double result = value.doubleValue();
        return Double.isFinite(result) ? result : Double.NaN;
    }

    public static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    public static IndicatorEvaluation evaluation(boolean up, boolean down, Map<String, Object> snapshot) {
        return new IndicatorEvaluation(DirectionBitMask.of(up, down), snapshot);
    }

    public static IndicatorEvaluation empty(Map<String, Object> snapshot) {
        return new IndicatorEvaluation(new BitSet(2), snapshot);
    }

}
