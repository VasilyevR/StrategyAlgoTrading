package rv.trading.strategy.engine.core.indicator.ta4j;

import org.junit.jupiter.api.Test;
import rv.trading.strategy.engine.core.indicator.IndicatorTestSeries;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeVigorIndexIndicatorTest {

    @Test
    void dividesSmoothedBodySumBySmoothedRangeSum() {
        var series = IndicatorTestSeries.of(5, index -> 0.0, index -> 10.0, index -> 0.0,
                index -> index + 1.0);
        var indicator = new RelativeVigorIndexIndicator(series, 1);

        assertThat(indicator.getValue(3).doubleValue()).isEqualTo(0.25);
        assertThat(indicator.getValue(4).doubleValue()).isEqualTo(0.35);
    }

    @Test
    void returnsZeroWhenEveryCandleHasZeroRange() {
        var series = IndicatorTestSeries.of(8, index -> 5.0, index -> 5.0, index -> 5.0, index -> 5.0);
        var indicator = new RelativeVigorIndexIndicator(series, 3);

        assertThat(indicator.getValue(series.getEndIndex()).doubleValue()).isZero();
    }
}
