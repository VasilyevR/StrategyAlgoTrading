package rv.trading.strategy.engine.core.indicator;

import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.enums.PositionSide;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public interface TrailingStopIndicator {

    Optional<BigDecimal> calculateStopLoss(BarSeries series, PositionSide side, Map<String, Object> params);
}
