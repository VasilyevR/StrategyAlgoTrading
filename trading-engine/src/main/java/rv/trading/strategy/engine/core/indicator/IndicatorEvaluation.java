package rv.trading.strategy.engine.core.indicator;

import java.util.BitSet;
import java.util.Map;

public record IndicatorEvaluation(BitSet directionMask, Map<String, Object> snapshot) {
}
