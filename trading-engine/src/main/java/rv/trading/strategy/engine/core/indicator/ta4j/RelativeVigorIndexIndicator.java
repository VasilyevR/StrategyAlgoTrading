package rv.trading.strategy.engine.core.indicator.ta4j;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

public final class RelativeVigorIndexIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> numerator;
    private final Indicator<Num> denominator;
    private final int length;

    public RelativeVigorIndexIndicator(BarSeries series, int length) {
        super(series);
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        this.length = length;
        Indicator<Num> closeMinusOpen = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                return series.getBar(index).getClosePrice().minus(series.getBar(index).getOpenPrice());
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        Indicator<Num> highMinusLow = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                return series.getBar(index).getHighPrice().minus(series.getBar(index).getLowPrice());
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        this.numerator = new SwmaIndicator(closeMinusOpen);
        this.denominator = new SwmaIndicator(highMinusLow);
    }

    @Override
    protected Num calculate(int index) {
        int start = Math.max(getBarSeries().getBeginIndex(), index - length + 1);
        Num numeratorSum = getBarSeries().numFactory().zero();
        Num denominatorSum = getBarSeries().numFactory().zero();
        for (int current = start; current <= index; current++) {
            numeratorSum = numeratorSum.plus(numerator.getValue(current));
            denominatorSum = denominatorSum.plus(denominator.getValue(current));
        }
        return denominatorSum.isZero() ? getBarSeries().numFactory().zero() : numeratorSum.dividedBy(denominatorSum);
    }

    @Override
    public int getCountOfUnstableBars() {
        return length + 2;
    }
}
