package rv.trading.strategy.engine.core.indicator.ta4j;

import org.junit.jupiter.api.Test;
import rv.trading.strategy.engine.core.indicator.IndicatorTestSeries;

import static org.assertj.core.api.Assertions.assertThat;

class StochasticRviIndicatorTest {

    @Test
    void flatRviWindowSmoothsToFiftyWithinTheBoundedRange() {
        var series = IndicatorTestSeries.of(20, index -> 100.0, index -> 110.0, index -> 100.0,
                index -> 101.0);
        var indicator = new StochasticRviIndicator(series, 1);

        for (int index = 3; index <= series.getEndIndex(); index++) {
            assertThat(indicator.getValue(index).doubleValue()).isBetween(0.0, 100.0);
        }
        assertThat(indicator.getValue(series.getEndIndex()).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void risingAndFallingRviWindowsApproachOppositeExtremes() {
        var rising = IndicatorTestSeries.of(40, index -> 0.0, index -> 10.0, index -> 0.0,
                index -> 1.0 + index * 0.1);
        var falling = IndicatorTestSeries.of(40, index -> 0.0, index -> 10.0, index -> 0.0,
                index -> 5.0 - index * 0.1);
        var risingIndicator = new StochasticRviIndicator(rising, 3);
        var fallingIndicator = new StochasticRviIndicator(falling, 3);

        assertThat(risingIndicator.getValue(rising.getEndIndex()).doubleValue()).isEqualTo(100.0);
        assertThat(fallingIndicator.getValue(falling.getEndIndex()).doubleValue()).isEqualTo(0.0);
    }
}
