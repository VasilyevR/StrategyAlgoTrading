package rv.trading.strategy.engine.core.signal;

import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.PositionSide;

import java.util.Optional;

public record SignalEvaluationContext(
        Optional<PositionSide> openPositionSide,
        Optional<OrderSide> lastSignalDirection
) {
}
