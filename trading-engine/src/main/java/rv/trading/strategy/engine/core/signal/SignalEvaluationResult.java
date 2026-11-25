package rv.trading.strategy.engine.core.signal;

import rv.trading.strategy.contracts.enums.SignalType;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

public record SignalEvaluationResult(
        List<SignalType> actions,
        BitSet resolvedDirectionMask,
        Map<String, Object> indicatorSnapshot
) {
}
