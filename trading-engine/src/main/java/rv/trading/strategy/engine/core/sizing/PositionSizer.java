package rv.trading.strategy.engine.core.sizing;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PositionSizer {
    public static final String ZERO_QUANTITY_MESSAGE = "Calculated quantity must be greater than zero";
    public static final String INVALID_STOP_MESSAGE = "Trailing stop-loss distance is invalid";

    private final BrokerExecutionPort brokerExecutionPort;
    private final IndicatorRegistry indicatorRegistry;

    public PositionSizer(BrokerExecutionPort brokerExecutionPort, IndicatorRegistry indicatorRegistry) {
        this.brokerExecutionPort = brokerExecutionPort;
        this.indicatorRegistry = indicatorRegistry;
    }

    public BigDecimal calculateQuantity(AlgotradingRuntimeConfig config, BarSeries series, PositionSide side,
                                        BigDecimal entryPrice) {
        BigDecimal quantity;
        if (config.sizingMode() == SizingMode.FIXED_VOLUME) {
            quantity = floorToStep(config.orderVolume(), config.stepSize());
        } else if (config.sizingMode() == SizingMode.RISK_PERCENT) {
            StrategyIndicatorConfig stopConfig = config.indicators().stream()
                    .filter(indicator -> indicator.role() == IndicatorRole.TRAILING_STOP)
                    .findFirst()
                    .orElseThrow(() -> new OrderSizingException(INVALID_STOP_MESSAGE));
            TrailingStopIndicator stopIndicator = indicatorRegistry
                    .getTrailingStopIndicator(stopConfig.indicatorCode().name())
                    .orElseThrow(() -> new OrderSizingException(INVALID_STOP_MESSAGE));
            BigDecimal stopPrice = stopIndicator.calculateStopLoss(series, side, stopConfig.params())
                    .orElseThrow(() -> new OrderSizingException(INVALID_STOP_MESSAGE));
            if (side == PositionSide.LONG && stopPrice.compareTo(entryPrice) >= 0) {
                throw new OrderSizingException(INVALID_STOP_MESSAGE);
            }
            if (side == PositionSide.SHORT && stopPrice.compareTo(entryPrice) <= 0) {
                throw new OrderSizingException(INVALID_STOP_MESSAGE);
            }
            BigDecimal distance = entryPrice.subtract(stopPrice).abs();
            if (distance.signum() <= 0) {
                throw new OrderSizingException(INVALID_STOP_MESSAGE);
            }
            BigDecimal balance = brokerExecutionPort.getAvailableBalance(
                    config.brokerClientId(), config.instrumentPairId());
            if (balance == null || balance.signum() <= 0) {
                throw new OrderSizingException(ZERO_QUANTITY_MESSAGE, true);
            }
            BigDecimal riskAmount = balance.multiply(config.orderVolume()).divide(
                    BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            quantity = floorToStep(riskAmount.divide(distance, 12, RoundingMode.HALF_UP), config.stepSize());
        } else {
            throw new OrderSizingException(ZERO_QUANTITY_MESSAGE);
        }
        if (quantity.signum() <= 0) {
            throw new OrderSizingException(ZERO_QUANTITY_MESSAGE);
        }
        return quantity;
    }

    private static BigDecimal floorToStep(BigDecimal value, BigDecimal stepSize) {
        if (value == null || stepSize == null || stepSize.signum() <= 0) {
            throw new OrderSizingException(ZERO_QUANTITY_MESSAGE);
        }
        BigDecimal units = value.divide(stepSize, 0, RoundingMode.DOWN);
        BigDecimal quantity = units.multiply(stepSize);
        if (quantity.signum() <= 0) {
            throw new OrderSizingException(ZERO_QUANTITY_MESSAGE);
        }
        return quantity;
    }
}
