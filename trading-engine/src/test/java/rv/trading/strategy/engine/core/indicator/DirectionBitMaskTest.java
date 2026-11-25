package rv.trading.strategy.engine.core.indicator;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionBitMaskTest {

    @Test
    void directionBitsUseStablePositionsAndSetExpectedDirections() {
        assertThat(DirectionBitMask.BIT_UP).isZero();
        assertThat(DirectionBitMask.BIT_DOWN).isEqualTo(1);

        BitSet upOnly = DirectionBitMask.of(true, false);
        BitSet downOnly = DirectionBitMask.of(false, true);
        BitSet both = DirectionBitMask.of(true, true);

        assertThat(DirectionBitMask.hasUp(upOnly)).isTrue();
        assertThat(DirectionBitMask.hasDown(upOnly)).isFalse();
        assertThat(DirectionBitMask.hasUp(downOnly)).isFalse();
        assertThat(DirectionBitMask.hasDown(downOnly)).isTrue();
        assertThat(DirectionBitMask.hasUp(both)).isTrue();
        assertThat(DirectionBitMask.hasDown(both)).isTrue();
        assertThat(DirectionBitMask.empty().isEmpty()).isTrue();
    }
}
