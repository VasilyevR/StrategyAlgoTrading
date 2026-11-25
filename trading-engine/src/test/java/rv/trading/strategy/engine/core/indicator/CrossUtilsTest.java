package rv.trading.strategy.engine.core.indicator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrossUtilsTest {

    @Test
    void detectsUpwardCrossIncludingEqualityAtPreviousBar() {
        assertThat(CrossUtils.crossedUp(1.0, 3.0, 2.0, 2.0)).isTrue();
        assertThat(CrossUtils.crossedUp(2.0, 2.1, 2.0, 2.0)).isTrue();
        assertThat(CrossUtils.crossedUp(3.0, 4.0, 2.0, 2.0)).isFalse();
        assertThat(CrossUtils.crossedUp(1.0, 2.0, 2.0, 2.0)).isFalse();
    }

    @Test
    void detectsDownwardCrossIncludingEqualityAtPreviousBar() {
        assertThat(CrossUtils.crossedDown(3.0, 1.0, 2.0, 2.0)).isTrue();
        assertThat(CrossUtils.crossedDown(2.0, 1.9, 2.0, 2.0)).isTrue();
        assertThat(CrossUtils.crossedDown(1.0, 0.0, 2.0, 2.0)).isFalse();
        assertThat(CrossUtils.crossedDown(3.0, 2.0, 2.0, 2.0)).isFalse();
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThat(CrossUtils.crossedUp(Double.NaN, 1.0, 0.0, 0.0)).isFalse();
        assertThat(CrossUtils.crossedDown(1.0, Double.NaN, 0.0, 0.0)).isFalse();
        assertThat(CrossUtils.crossedUp(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0)).isFalse();
        assertThat(CrossUtils.crossedDown(0.0, 0.0, Double.NEGATIVE_INFINITY, 0.0)).isFalse();
    }
}
