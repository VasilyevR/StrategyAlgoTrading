package rv.trading.strategy.engine.core.indicator.ta4j;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

public final class StochasticRviIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> normalizedRvi;
    private final int length;

    public StochasticRviIndicator(BarSeries series, int length) {
        super(series);
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        this.length = length;
        Indicator<Num> rvi = new RelativeVigorIndexIndicator(series, length);
        Indicator<Num> highest = new HighestValueIndicator(rvi, length);
        Indicator<Num> lowest = new LowestValueIndicator(rvi, length);
        this.normalizedRvi = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                Num maximum = highest.getValue(index);
                Num minimum = lowest.getValue(index);
                Num range = maximum.minus(minimum);
                return range.isZero() ? series.numFactory().numOf(0.5)
                        : rvi.getValue(index).minus(minimum).dividedBy(range);
            }

            @Override
            public int getCountOfUnstableBars() {
                return rvi.getCountOfUnstableBars() + length - 1;
            }
        };
    }

    @Override
    protected Num calculate(int index) {
        Num weighted = getBarSeries().numFactory().zero();
        int[] weights = {4, 3, 2, 1};
        for (int offset = 0; offset < weights.length; offset++) {
            int currentIndex = index - offset;
            Num value = currentIndex < getBarSeries().getBeginIndex()
                    ? getBarSeries().numFactory().zero()
                    : normalizedRvi.getValue(currentIndex);
            weighted = weighted.plus(value.multipliedBy(getBarSeries().numFactory().numOf(weights[offset])));
        }
        return weighted.multipliedBy(getBarSeries().numFactory().hundred())
                .dividedBy(getBarSeries().numFactory().numOf(10));
    }

    @Override
    public int getCountOfUnstableBars() {
        return (length * 2) + 4;
    }
}
