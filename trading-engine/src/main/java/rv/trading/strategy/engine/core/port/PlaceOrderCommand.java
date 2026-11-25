package rv.trading.strategy.engine.core.port;

import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.TimeInForce;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        long brokerClientId,
        long instrumentPairId,
        String symbol,
        String clientOrderId,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal stopPrice,
        boolean reduceOnly
) {
}
