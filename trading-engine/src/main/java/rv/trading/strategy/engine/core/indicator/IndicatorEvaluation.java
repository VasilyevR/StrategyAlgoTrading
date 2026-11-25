package rv.trading.strategy.engine.core.indicator;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;

public record IndicatorEvaluation(BitSet directionMask, Map<String, Object> snapshot) {

    public IndicatorEvaluation {
        directionMask = (BitSet) Objects.requireNonNull(directionMask, "directionMask").clone();
        snapshot = Map.copyOf(Objects.requireNonNull(snapshot, "snapshot"));
    }

    @Override
    public BitSet directionMask() {
        return (BitSet) directionMask.clone();
    }
}
