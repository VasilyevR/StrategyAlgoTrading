package rv.trading.strategy.engine.core.indicator.ta4j;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import rv.trading.strategy.engine.core.indicator.IndicatorTestSeries;

import static org.assertj.core.api.Assertions.assertThat;

class SwmaIndicatorTest {

    @Test
    void computesFourTapSymmetricWeightedAverageAndZeroBeforeWarmup() {
        var series = IndicatorTestSeries.fromCloses(1, 2, 3, 4, 5);
        Indicator<Num> indicator = new SwmaIndicator(new ClosePriceIndicator(series));

        assertThat(indicator.getValue(0).doubleValue()).isZero();
        assertThat(indicator.getValue(1).doubleValue()).isZero();
        assertThat(indicator.getValue(2).doubleValue()).isZero();
        assertThat(indicator.getValue(3).doubleValue()).isEqualTo(2.5);
        assertThat(indicator.getValue(4).doubleValue()).isEqualTo(3.5);
        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(3);
    }
}
