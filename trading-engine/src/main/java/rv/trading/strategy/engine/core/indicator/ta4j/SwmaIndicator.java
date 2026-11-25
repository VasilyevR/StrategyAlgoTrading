package rv.trading.strategy.engine.core.indicator.ta4j;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

public final class SwmaIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> source;

    public SwmaIndicator(Indicator<Num> source) {
        super(source);
        this.source = source;
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index - 3 < beginIndex) {
            return getBarSeries().numFactory().zero();
        }
        return source.getValue(index)
                .plus(source.getValue(index - 1).multipliedBy(getBarSeries().numFactory().numOf(2)))
                .plus(source.getValue(index - 2).multipliedBy(getBarSeries().numFactory().numOf(2)))
                .plus(source.getValue(index - 3))
                .dividedBy(getBarSeries().numFactory().numOf(6));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 3;
    }
}
